/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.android.client.adapter;

import java.util.*;

import com.biglybt.android.adapter.*;
import com.biglybt.android.client.*;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.util.MapUtils;
import com.biglybt.util.Thunk;
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.widget.SectionIndexer;

import androidx.annotation.NonNull;

public class FilesTreeFilter
	extends LetterFilter<FilesAdapterItem>
	implements FastScrollRecyclerView.SectionedAdapter, SectionIndexer
{
	private static final String TAG = "FilesTreeFilter";

	private static final String RESULTFIELD_TOTAL_FILTERED_SIZE_WANTED = "totalSizeWantedFiltered";

	private static final String RESULTFIELD_TOTAL_FILTERED_NUM_FILES = "totalNumFilesFiltered";

	private static final String RESULTFIELD_TOTAL_FILTERED_NUM_FILES_WANTED = "totalNumFilesWantedFiltered";

	private static final String RESULTFIELD_TOTAL_SIZE_WANTED = "totalSizeWanted";

	private static final String RESULTFIELD_TOTAL_NUM_FILES_WANTED = "totalNumFilesWanted";

	private static final String RESULTFIELD_LIST = "list";

	private static final String RESULTFIELD_FOLDERS = "folders";

	private static final String RESULTFIELD_SECTIONS = "sections";

	private static final String RESULTFIELD_SECTION_STARTS = "sectionStarts";

	private static final String ID_SORT_FILTER = "-files";

	private static final long MAX_REFRESHSECTOINS_MS = 500;

	private static final int SORTID_TREE = 0;

	private static final int SORTID_NAME = 1;

	private static final int SORTID_SIZE = 2;

	private final SessionAdapterFilterTalkback<FilesAdapterItem> talkback;

	private final long torrentID;

	@Thunk
	Map<String, FilesAdapterItemFolder> mapFolders;

	@Thunk
	String[] sections;

	@Thunk
	List<Integer> sectionStarts;

	@Thunk
	final Object mLock = new Object();

	@Thunk
	final Object lockSections = new Object();

	long totalFilteredSizeWanted;

	int totalFilteredNumFilesWanted;

	private int totalFilteredNumFiles;

	long totalSizeWanted;

	int totalNumFilesWanted;

	@Thunk
	Map<String, Object>[] files = null;

	private long sizeStart = -1;

	private long sizeEnd = -1;

	private long maxSize;

	private boolean showOnlyWanted;

	private boolean showOnlyComplete;

	private int defaultSortID;

	FilesTreeFilter(long torrentID,
			SessionAdapterFilterTalkback<FilesAdapterItem> talkback) {
		super(talkback);
		this.torrentID = torrentID;
		this.talkback = talkback;

		StoredSortByInfo sortByInfo = talkback.getSession().getRemoteProfile().getSortByInfo(
				ID_SORT_FILTER);
		SortDefinition sortDefinition = SortDefinition.findSortDefinition(
				sortByInfo, getSortDefinitions(), defaultSortID);
		boolean isAsc = sortByInfo == null ? sortDefinition.isSortAsc()
				: sortByInfo.isAsc;

		ComparatorMapFields<FilesAdapterItem> sorter = new ComparatorMapFieldsErr<FilesAdapterItem>(
				sortDefinition, isAsc) {
			@Override
			public Map<?, ?> mapGetter(FilesAdapterItem o) {
				return getFileMap(o, files);
			}
		};
		setSorter(sorter);
	}

	@Thunk
	static Map<?, ?> getFileMap(FilesAdapterItem o, Object[] files) {
		if (o instanceof FilesAdapterItemFile) {
			if (files == null) {
				return Collections.EMPTY_MAP;
			}
			return (Map<?, ?>) files[((FilesAdapterItemFile) o).fileIndex];
		}
		if (o instanceof FilesAdapterItemFolder) {
			return ((FilesAdapterItemFolder) o).map;
		}
		return Collections.EMPTY_MAP;
	}

	@Override
	protected FilterResults performFiltering2(CharSequence constraint) {
		this.constraint = constraint;
		FilterResults results = new FilterResults();
		Map<String, Object> map = new HashMap<>();

		SortDefinition sortDefinition = getSorter().getSortDefinition();
		boolean useTree = sortDefinition.id == SORTID_TREE;

		Session session = talkback.getSession();
		Map<?, ?> torrent = session.torrent.getCachedTorrent(torrentID);
		if (torrent == null) {
			if (AndroidUtils.DEBUG_ADAPTER) {
				log(TAG, "No torrent for " + torrentID);
			}
			return results;
		}
		final List<?> listFiles = MapUtils.getMapList(torrent,
				TransmissionVars.FIELD_TORRENT_FILES, null);
		if (listFiles == null) {
			if (AndroidUtils.DEBUG_ADAPTER) {
				log(TAG, "No files");
			}
			return results;
		}
		if (AndroidUtils.DEBUG_ADAPTER) {
			log(TAG, "listFiles=" + listFiles.size());
		}

		if (files == null) {
			//noinspection unchecked,SuspiciousToArrayCall
			files = (Map<String, Object>[]) listFiles.toArray(new Map[0]);
		}

		List<FilesAdapterItem> list = useTree ? performTreeFiltering(map, listFiles)
				: performNonTreeFiltering(map, listFiles);

		doSort(list);

		map.put(RESULTFIELD_LIST, list);
		refreshSections(sortDefinition, torrent, list, map);

		results.values = map;
		results.count = list.size();

		return results;
	}

	private List<FilesAdapterItem> performNonTreeFiltering(
			Map<String, Object> map, List<?> listFiles) {
		long totalFilteredSizeWanted = 0;
		int totalFilteredNumFilesWanted = 0;
		long totalSizeWanted = 0;
		int totalNumFilesWanted = 0;
		int totalFilteredNumFiles = 0;
		List<FilesAdapterItem> list = new ArrayList<>();
		int listFilesSize = listFiles.size();

		HashSet<String> setLetters = null;
		HashMap<String, Integer> mapLetterCount = null;
		if (isBuildLetters()) {
			setLetters = new HashSet<>();
			mapLetterCount = new HashMap<>();
		}
		String constraintString = constraint == null ? "" : constraint.toString();

		for (int i = 0; i < listFilesSize; i++) {
			@SuppressWarnings("unchecked")
			Map<String, Object> mapFile = (Map<String, Object>) listFiles.get(i);

			boolean wanted = MapUtils.getMapBoolean(mapFile,
					TransmissionVars.FIELD_FILESTATS_WANTED, true);
			long length = MapUtils.getMapLong(mapFile,
					TransmissionVars.FIELD_FILES_LENGTH, 0);

			String shortName = MapUtils.getMapString(mapFile,
					TransmissionVars.FIELD_FILES_NAME, "");
			String path = "";

			boolean allowed = filterCheck(mapFile) && constraintCheck(
					constraintString, shortName, setLetters, mapLetterCount);

			if (allowed) {
				FilesAdapterItemFile f = Build.VERSION.SDK_INT >= 19
						? new FilesAdapterItemFile19(i, null, path, shortName, wanted,
								mapFile)
						: new FilesAdapterItemFile(i, null, path, shortName, wanted,
								mapFile);
				list.add(f);
				totalFilteredNumFiles++;
			}

			if (wanted) {
				if (allowed) {
					totalFilteredNumFilesWanted++;
					totalFilteredSizeWanted += length;
				} else {
					totalNumFilesWanted++;
					totalSizeWanted += length;
				}
			}

		}
		map.put(RESULTFIELD_TOTAL_FILTERED_SIZE_WANTED, totalFilteredSizeWanted);
		map.put(RESULTFIELD_TOTAL_FILTERED_NUM_FILES_WANTED,
				totalFilteredNumFilesWanted);
		map.put(RESULTFIELD_TOTAL_FILTERED_NUM_FILES, totalFilteredNumFiles);

		map.put(RESULTFIELD_TOTAL_SIZE_WANTED, totalSizeWanted);
		map.put(RESULTFIELD_TOTAL_NUM_FILES_WANTED, totalNumFilesWanted);

		if (mapLetterCount != null) {
			LettersUpdatedListener lettersUpdatedListener = getLettersUpdatedListener();
			if (lettersUpdatedListener != null) {
				lettersUpdatedListener.lettersUpdated(mapLetterCount);
			}
		}

		return list;
	}

	private List<FilesAdapterItem> performTreeFiltering(Map<String, Object> map,
			List<?> listFiles) {
		long totalSizeWanted = 0;
		int totalNumFilesWanted = 0;

		long totalFilteredSizeWanted = 0;
		int totalFilteredNumFilesWanted = 0;
		int totalFilteredNumFiles = 0;

		List<FilesAdapterItem> list = new ArrayList<>();
		int listFilesSize = listFiles.size();

		Map<String, FilesAdapterItemFolder> mapFoldersNew = new HashMap<>();

		HashSet<String> setLetters = null;
		HashMap<String, Integer> mapLetterCount = null;
		if (isBuildLetters()) {
			setLetters = new HashSet<>();
			mapLetterCount = new HashMap<>();
		}
		String constraintString = constraint == null ? "" : constraint.toString();

		for (int i = 0; i < listFilesSize; i++) {
			@SuppressWarnings("unchecked")
			Map<String, Object> mapFile = (Map<String, Object>) listFiles.get(i);

			boolean wanted = MapUtils.getMapBoolean(mapFile,
					TransmissionVars.FIELD_FILESTATS_WANTED, true);
			long length = MapUtils.getMapLong(mapFile,
					TransmissionVars.FIELD_FILES_LENGTH, 0);
			String name = MapUtils.getMapString(mapFile,
					TransmissionVars.FIELD_FILES_NAME, "");

			// Get the folder name and see if we added it yet
			int folderBreaksAt = AndroidUtils.lastindexOfAny(name,
					TorrentUtils.ANYSLASH, -1);
			String folderWithSlash = folderBreaksAt <= 0 ? ""
					: name.substring(0, folderBreaksAt + 1);
			FilesAdapterItemFolder folderItem = ensureParentFolders(folderWithSlash,
					mapFoldersNew, mapFolders, list);

			String shortName = name.substring(folderWithSlash.length(),
					name.length());

			boolean allowed = filterCheck(mapFile) && constraintCheck(
					constraintString, shortName, setLetters, mapLetterCount);

			boolean addFile = false;

			if (folderItem == null) {
				// probably root
				if (allowed) {
					addFile = true;
					totalFilteredNumFiles++;
				}
				if (folderWithSlash.length() == 0) {
					if (wanted) {
						if (allowed) {
							totalFilteredNumFilesWanted++;
							totalFilteredSizeWanted += length;
						} else {
							totalNumFilesWanted++;
							totalSizeWanted += length;
						}
					}
				}
			} else {
				folderItem.summarizeFile(i, length, wanted, allowed);
				addFile = allowed && folderItem.expand && folderItem.parentsExpanded();
			}

			if (addFile) {
				FilesAdapterItemFile f = Build.VERSION.SDK_INT >= 19
						? new FilesAdapterItemFile19(i, folderItem, folderWithSlash,
								shortName, wanted, mapFile)
						: new FilesAdapterItemFile(i, folderItem, folderWithSlash,
								shortName, wanted, mapFile);
				list.add(f);
			}
		}

		// calculate global totals
		// remove empty folders
		for (String key : mapFoldersNew.keySet()) {
			FilesAdapterItemFolder folderItem = mapFoldersNew.get(key);
			if (folderItem.level == 0) {
				totalFilteredSizeWanted += folderItem.sizeWantedFiltered;
				totalFilteredNumFilesWanted += folderItem.numFilesFilteredWanted;
				totalFilteredNumFiles += folderItem.getNumFilteredFiles();

				totalSizeWanted += folderItem.sizeWanted;
				totalNumFilesWanted += folderItem.numFilesWanted;
			}
			if (folderItem.getNumFilteredFiles() == 0) {
				list.remove(folderItem);
			}
		}

		map.put(RESULTFIELD_TOTAL_FILTERED_SIZE_WANTED, totalFilteredSizeWanted);
		map.put(RESULTFIELD_TOTAL_FILTERED_NUM_FILES_WANTED,
				totalFilteredNumFilesWanted);
		map.put(RESULTFIELD_TOTAL_FILTERED_NUM_FILES, totalFilteredNumFiles);

		map.put(RESULTFIELD_TOTAL_SIZE_WANTED, totalSizeWanted);
		map.put(RESULTFIELD_TOTAL_NUM_FILES_WANTED, totalNumFilesWanted);

		map.put(RESULTFIELD_FOLDERS, mapFoldersNew);

		if (mapLetterCount != null) {
			LettersUpdatedListener lettersUpdatedListener = getLettersUpdatedListener();
			if (lettersUpdatedListener != null) {
				lettersUpdatedListener.lettersUpdated(mapLetterCount);
			}
		}

		return list;
	}

	private static FilesAdapterItemFolder ensureParentFolders(
			String folderWithSlash, Map<String, FilesAdapterItemFolder> mapFoldersNew,
			Map<String, FilesAdapterItemFolder> mapFolders,
			List<FilesAdapterItem> list) {
		if (folderWithSlash.isEmpty()) {
			return null;
		}
		FilesAdapterItemFolder existing = mapFoldersNew.get(folderWithSlash);
		if (existing != null) {
			return existing;
		}

		// add folder and parents
		String[] folderSplit = FilesTreeAdapter.patternFolderSplit.split(
				folderWithSlash);
		int startAt = folderSplit[0].length() == 0 ? 1 : 0;
		int pos = startAt;
		FilesAdapterItemFolder last = null;
		for (int j = startAt; j < folderSplit.length; j++) {
			int oldPos = pos;
			pos += folderSplit[j].length() + 1;
			String folderWalk = folderWithSlash.substring(0, pos);

			existing = mapFoldersNew.get(folderWalk);
			if (existing == null) {
				String path = folderWithSlash.substring(0, oldPos);
				String folderName = folderSplit[j];

				FilesAdapterItemFolder displayFolder = new FilesAdapterItemFolder(
						folderWalk, last, path, folderName);
				last = displayFolder;
//						Log.e(TAG, i + "." + j + "] " + folderName + "] " + folderWalk
//								+ " for " + name);
				if (mapFolders != null) {
					FilesAdapterItemFolder oldFolder = mapFolders.get(folderWalk);
					if (oldFolder != null) {
						displayFolder.expand = oldFolder.expand;
					}
				}
				mapFoldersNew.put(folderWalk, displayFolder);
				if (displayFolder.getNumFiles() == 0
						&& displayFolder.parentsExpanded()) {
					list.add(displayFolder);
				}
			} else {
				last = existing;
			}
		}
		return last;
	}

	@SuppressWarnings({
		"RedundantIfStatement",
		"BooleanMethodIsAlwaysInverted"
	})
	private boolean filterCheck(Map<?, ?> mapFile) {
		long size = MapUtils.getMapLong(mapFile,
				TransmissionVars.FIELD_FILES_LENGTH, -1);

		if (size > maxSize) {
			maxSize = size;
		}

		if (sizeStart > 0 || sizeEnd > 0) {
			boolean withinRange = size >= sizeStart
					&& (sizeEnd < 0 || size <= sizeEnd);
			if (!withinRange) {
				return false;
			}
		}

		if (showOnlyComplete && MapUtils.getMapLong(mapFile,
				TransmissionVars.FIELD_FILESTATS_BYTES_COMPLETED, -1) != size) {
			return false;
		}
		if (showOnlyWanted && !MapUtils.getMapBoolean(mapFile,
				TransmissionVars.FIELD_FILESTATS_WANTED, true)) {
			return false;
		}

		return true;
	}

	@SuppressWarnings("unchecked")
	@Override
	protected boolean publishResults2(CharSequence constraint,
			FilterResults results) {
		// Now we have to inform the adapter about the new list filtered
		if (results.count == 0) {
			talkback.removeAllItems();
		} else {
			if (results.values instanceof Map) {
				Map map = (Map) results.values;
				List<FilesAdapterItem> displayList = (List<FilesAdapterItem>) map.get(
						RESULTFIELD_LIST);
				synchronized (lockSections) {
					sections = (String[]) map.get(RESULTFIELD_SECTIONS);
					sectionStarts = (List<Integer>) map.get(RESULTFIELD_SECTION_STARTS);
				}

				totalFilteredSizeWanted = MapUtils.getMapLong(map,
						RESULTFIELD_TOTAL_FILTERED_SIZE_WANTED, 0);
				totalFilteredNumFilesWanted = MapUtils.getMapInt(map,
						RESULTFIELD_TOTAL_FILTERED_NUM_FILES_WANTED, 0);
				totalFilteredNumFiles = MapUtils.getMapInt(map,
						RESULTFIELD_TOTAL_FILTERED_NUM_FILES, 0);
				totalSizeWanted = MapUtils.getMapLong(map,
						RESULTFIELD_TOTAL_SIZE_WANTED, 0);
				totalNumFilesWanted = MapUtils.getMapInt(map,
						RESULTFIELD_TOTAL_NUM_FILES_WANTED, 0);

				if (displayList == null) {
					displayList = new ArrayList<>();
				}

				mapFolders = (Map) map.get(RESULTFIELD_FOLDERS);

				return talkback.setItems(displayList, null);
			}
		}
		return true;
	}

	@Thunk
	void refreshSections(@NonNull SortDefinition sortDefinition, Map torrent,
			List<FilesAdapterItem> displayList, Map<String, Object> map) {

		if (sortDefinition.id == SORTID_SIZE) {
			return;
		}

		List<String> categories = new ArrayList<>();
		List<Integer> categoriesStart = new ArrayList<>();
		String lastFullCat = " ";
		List<?> listFiles = MapUtils.getMapList(torrent,
				TransmissionVars.FIELD_TORRENT_FILES, null);

		if (listFiles == null) {
			return;
		}

		long startedOn = System.currentTimeMillis();
		for (int i = 0, displayListSize = displayList.size(); i < displayListSize; i++) {
			if ((i % 10) == 9) {
				long timeDiff = System.currentTimeMillis() - startedOn;
				if (timeDiff > MAX_REFRESHSECTOINS_MS) {
					if (AndroidUtils.DEBUG_ADAPTER) {
						Log.d(TAG, "refreshSections: Over " + MAX_REFRESHSECTOINS_MS
								+ "ms. Processed " + i + " of " + displayList.size());
					}
					break;
				}
			}
			FilesAdapterItem displayObject = displayList.get(i);
			if (displayObject instanceof FilesAdapterItemFolder) {
				continue;
			}
			Map<?, ?> mapFile = FilesTreeAdapter.getFileMap(displayObject, listFiles);
			String name = MapUtils.getMapString(mapFile,
					TransmissionVars.FIELD_FILES_NAME, "");
			//.toUpperCase(Locale.US); adds a lot of time on large lists
			if (!name.startsWith(lastFullCat)) {
				final int MAX_CATS = 3;
				String[] split = FilesTreeAdapter.patternFolderSplit.split(name,
						MAX_CATS + 1);
				String cat = "";
				int count = 0;
				int end = 0;
				for (int j = 0; j < split.length; j++) {
					if (j > 0) {
						end++;
					}

					String g = split[j];

					if (g.length() > 0) {
						if (cat.length() > 0) {
							//noinspection StringConcatenationInLoop
							cat += "/";
						}
						//noinspection StringConcatenationInLoop
						cat += g.substring(0, 1);
						count++;
						if (count >= MAX_CATS || j == split.length - 1) {
							end++;
							break;
						} else {
							end += g.length();
						}
					}
				}
				lastFullCat = name.substring(0, end);
				//Log.d(TAG, lastFullCat);
				categories.add(cat);
				categoriesStart.add(i);
			}
		}

		// We could split larger gaps into two sections with the same name
		map.put(RESULTFIELD_SECTIONS, categories.toArray(new String[0]));
		map.put(RESULTFIELD_SECTION_STARTS, categoriesStart);

//		if (AndroidUtils.DEBUG_ADAPTER) {
//			Log.d(TAG,
//					"refreshSections: took " + (System.currentTimeMillis() - startedOn)
//							+ "ms for " + displayList.size());
//		}
		//if (AndroidUtils.DEBUG) {
		//Log.d(TAG, "Sections: " + Arrays.toString(sections));
		//Log.d(TAG, "SectionStarts: " + sectionStarts);
		//}
	}

	@Override
	public Object[] getSections() {
		if (AndroidUtils.DEBUG_ADAPTER) {
			Log.d(FilesTreeAdapter.TAG,
					"GetSections " + (sections == null ? "NULL" : sections.length));
		}
		return sections;
	}

	@Override
	public int getPositionForSection(int sectionIndex) {
		synchronized (lockSections) {
			if (sectionIndex < 0 || sectionStarts == null
					|| sectionIndex >= sectionStarts.size()) {
				return 0;
			}
			return sectionStarts.get(sectionIndex);
		}
	}

	@Override
	public int getSectionForPosition(int position) {
		synchronized (lockSections) {
			if (sectionStarts == null) {
				return 0;
			}
			int i = Collections.binarySearch(sectionStarts, position);
			if (i < 0) {
				i = (-1 * i) - 2;
			}
			if (i >= sections.length) {
				i = sections.length - 1;
			} else if (i < 0) {
				i = 0;
			}
			return i;
		}
	}

	@Override
	public @NonNull String getSectionName(int position) {
		synchronized (lockSections) {
			if (sections == null) {
				return "";
			}
			int sectionForPosition = getSectionForPosition(position);
			if (sectionForPosition != 0 || sections.length > 0) {
				return sections[sectionForPosition];
			}
			return "";
		}
	}

	@Override
	protected String getStringToConstrain(FilesAdapterItem key) {
		if (key instanceof FilesAdapterItemFolder) {
			return "";
		}
		return key.name;
	}

	@Override
	public boolean showLetterUI() {
		return files != null && files.length > 3;
	}

	@Override
	public void restoreFromBundle(Bundle savedInstanceState) {
		super.restoreFromBundle(savedInstanceState);
		if (savedInstanceState == null) {
			return;
		}

		String prefix = getClass().getName();
		sizeStart = savedInstanceState.getLong(prefix + ":sizeStart", sizeStart);
		sizeEnd = savedInstanceState.getLong(prefix + ":sizeEnd", sizeEnd);
	}

	@Override
	public void saveToBundle(Bundle outState) {
		super.saveToBundle(outState);
		String prefix = getClass().getName();

		outState.putLong(prefix + ":sizeStart", sizeStart);
		outState.putLong(prefix + ":sizeEnd", sizeEnd);
	}

	public void clearFilter() {
		setFilterSizes(-1, -1);
	}

	public long[] getFilterSizes() {
		return new long[] {
			sizeStart,
			sizeEnd
		};
	}

	public void setFilterSizes(long start, long end) {
		this.sizeStart = start;
		this.sizeEnd = end;
	}

	public long getMaxSize() {
		return maxSize;
	}

	public void setShowOnlyWanted(boolean showOnlyWanted) {
		this.showOnlyWanted = showOnlyWanted;
	}

	public boolean isShowOnlyComplete() {
		return showOnlyComplete;
	}

	public boolean isShowOnlyWanted() {
		return showOnlyWanted;
	}

	public void setShowOnlyComplete(boolean showOnlyComplete) {
		this.showOnlyComplete = showOnlyComplete;
	}

	@NonNull
	@Override
	public SparseArray<SortDefinition> createSortDefinitions() {
		String[] sortNames = BiglyBTApp.getContext().getResources().getStringArray(
				R.array.sortby_file_list);

		SparseArray<SortDefinition> sortDefinitions = new SparseArray<>(
				sortNames.length);

		int i = SORTID_TREE;
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_FILES_NAME,
			TransmissionVars.FIELD_FILES_INDEX
		}, new Boolean[] {
			true,
			true
		}, null));
		defaultSortID = i;

		i = SORTID_NAME;
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_FILES_NAME,
			TransmissionVars.FIELD_FILES_INDEX
		}, new Boolean[] {
			true,
			true
		}, true));

		i = SORTID_SIZE;
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_FILES_LENGTH
		}, SortDefinition.SORT_DESC));

		return sortDefinitions;
	}

	@Override
	protected void saveSortDefinition(SortDefinition sortDefinition,
			boolean isAsc) {
		Session session = talkback.getSession();
		if (session.getRemoteProfile().setSortBy(ID_SORT_FILTER, sortDefinition,
				isAsc)) {
			session.saveProfile();
		}
	}

	public int getFilteredFileCount() {
		return totalFilteredNumFiles;
	}

	public int getUnfilteredFileCount() {
		return files == null ? 0 : files.length;
	}

	@Override
	public void refilter(boolean skipIfFiltering) {
		if (getUnfilteredFileCount() == 0) {
			refilter(skipIfFiltering, 0);
			return;
		}
		super.refilter(skipIfFiltering);
	}
}
