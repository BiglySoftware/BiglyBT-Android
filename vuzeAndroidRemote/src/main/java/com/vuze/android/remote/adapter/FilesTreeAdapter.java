/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 */

package com.vuze.android.remote.adapter;

import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.TypedValue;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.*;
import android.widget.RelativeLayout.LayoutParams;

import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;
import com.vuze.android.FlexibleRecyclerAdapter;
import com.vuze.android.FlexibleRecyclerViewHolder;
import com.vuze.android.FlexibleRecyclerSelectionListener;
import com.vuze.android.remote.*;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.TextViewFlipper.FlipValidator;
import com.vuze.android.remote.rpc.TransmissionRPC;
import com.vuze.util.DisplayFormatters;
import com.vuze.util.MapUtils;

public class FilesTreeAdapter
	extends
	FlexibleRecyclerAdapter<FilesTreeAdapter.ViewHolder, FilesAdapterDisplayObject>
	implements Filterable, SectionIndexer, FastScrollRecyclerView.SectionedAdapter
{
	/* @Thunk */ static final String TAG = "FilesTreeAdapter2";

	private static final int TYPE_FOLDER = 0;

	private static final int TYPE_FILE = 1;

	/* @Thunk */ static final Pattern patternFolderSplit = Pattern.compile("[\\\\/]");

	static class ViewHolder
		extends FlexibleRecyclerViewHolder
	{
		TextView tvName;

		TextView tvProgress;

		ProgressBar pb;

		TextView tvInfo;

		TextView tvStatus;

		ImageButton expando;

		ImageButton btnWant;

		View strip;

		RelativeLayout layout;

		public int fileIndex = -1;

		public long torrentID = -1;

		public ViewHolder(RecyclerSelectorInternal selector, View rowView) {
			super(selector, rowView);
		}
	}

	public static class ViewHolderFlipValidator
		implements FlipValidator
	{
		private final ViewHolder holder;

		private int fileIndex = -1;

		private final long torrentID;

		public ViewHolderFlipValidator(ViewHolder holder, long torrentID,
				int fileIndex) {
			this.holder = holder;
			this.torrentID = torrentID;
			this.fileIndex = fileIndex;
		}

		@Override
		public boolean isStillValid() {
			return fileIndex >= 0 && holder.fileIndex == fileIndex
					&& holder.torrentID == torrentID;
		}
	}

	private final Context context;

	private FileFilter filter;

	/* @Thunk */ final Map<String, FilesAdapterDisplayFolder> mapFolders = new HashMap<>();

	public final Object mLock = new Object();

	private Comparator<? super Map<?, ?>> comparator;

	private final Resources resources;

	private final ComparatorMapFields sorter;

	/* @Thunk */ SessionInfo sessionInfo;

	/* @Thunk */ long torrentID;

	private final TextViewFlipper flipper;

	/* @Thunk */ String[] sections;

	/* @Thunk */ List<Integer> sectionStarts;

	private final int levelPaddingPx;

	private boolean inEditMode = false;

	private final int levelPadding2Px;

	/* @Thunk */ long totalSizeWanted;

	/* @Thunk */ long totalNumFilesWanted;

	/* @Thunk */ final Object lockSections = new Object();

	public FilesTreeAdapter(final Context context,
			FlexibleRecyclerSelectionListener selector) {
		super(selector);
		this.context = context;
		resources = context.getResources();
		flipper = new TextViewFlipper(R.anim.anim_field_change);

		levelPaddingPx = (int) TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_DIP, 20, resources.getDisplayMetrics());
		levelPadding2Px = (int) TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_DIP, 5, resources.getDisplayMetrics());

		String[] sortFieldIDs = {
			"name",
			"index"
		};

		Boolean[] sortOrderAsc = {
			true,
			true
		};

		sorter = new ComparatorMapFields(sortFieldIDs, sortOrderAsc) {

			private long mapListTorrentID = -1;

			private List<?> mapList;

			Throwable lastError = null;

			@Override
			public int reportError(Comparable<?> oLHS, Comparable<?> oRHS,
					Throwable t) {
				if (lastError != null) {
					if (t.getCause().equals(lastError.getCause())
							&& t.getMessage().equals(lastError.getMessage())) {
						return 0;
					}
				}
				lastError = t;
				Log.e(TAG, "Filesort", t);
				VuzeEasyTracker.getInstance(context).logError(t);
				return 0;
			}

			@Override
			public Map<?, ?> mapGetter(Object o) {
				if (mapListTorrentID != torrentID) {
					mapListTorrentID = torrentID;
					Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
					mapList = MapUtils.getMapList(torrent,
							TransmissionVars.FIELD_TORRENT_FILES, null);
				}
				return getFileMap(o, mapList);
			}

		};
	}

	public void setSessionInfo(SessionInfo sessionInfo) {
		this.sessionInfo = sessionInfo;
	}

	@Override
	public ViewHolder onCreateFlexibleViewHolder(ViewGroup parent, int viewType) {

		boolean isFolder = viewType == TYPE_FOLDER;
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);
		View rowView = inflater.inflate(
				isFolder ? R.layout.row_folder_selection : R.layout.row_file_selection,
				parent, false);
		ViewHolder viewHolder = new ViewHolder(this, rowView);

		viewHolder.tvName = (TextView) rowView.findViewById(R.id.filerow_name);

		viewHolder.tvProgress = (TextView) rowView.findViewById(
				R.id.filerow_progress_pct);
		viewHolder.pb = (ProgressBar) rowView.findViewById(R.id.filerow_progress);
		viewHolder.tvInfo = (TextView) rowView.findViewById(R.id.filerow_info);
		viewHolder.tvStatus = (TextView) rowView.findViewById(R.id.filerow_state);
		viewHolder.expando = (ImageButton) rowView.findViewById(
				R.id.filerow_expando);
		viewHolder.btnWant = (ImageButton) rowView.findViewById(
				R.id.filerow_btn_dl);
		viewHolder.strip = rowView.findViewById(R.id.filerow_indent);
		viewHolder.layout = (RelativeLayout) rowView.findViewById(
				R.id.filerow_layout);

		rowView.setTag(viewHolder);

		return viewHolder;
	}

	@Override
	public void onBindFlexibleViewHolder(ViewHolder holder, int position) {
		Object oItem = getItem(position);
		boolean isFolder = (oItem instanceof FilesAdapterDisplayFolder);

		int level = ((FilesAdapterDisplayObject) oItem).level;
		int paddingX = levelPaddingPx * level;
		int parentWidth = holder.itemView.getWidth();
		// if first 6 take up 1/3rd of the width, make levels over 6 use smaller width
		if (level > 6 && (levelPaddingPx * 6) > parentWidth / 4) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "Using smaller Padding.. from " + paddingX + " to "
						+ ((levelPaddingPx * 6) + (levelPadding2Px * (level - 6))));
			}
			paddingX = (levelPaddingPx * 6) + (levelPadding2Px * (level - 6));
		}
		if (holder.strip != null) {
			android.view.ViewGroup.LayoutParams lp = holder.strip.getLayoutParams();
			if (lp instanceof LinearLayout.LayoutParams) {
				holder.strip.setLayoutParams(new LinearLayout.LayoutParams(
						levelPaddingPx * level, LayoutParams.MATCH_PARENT));
			} else if (lp instanceof RelativeLayout.LayoutParams) {
				holder.strip.setLayoutParams(new RelativeLayout.LayoutParams(
						levelPaddingPx * level, LayoutParams.MATCH_PARENT));
			}
		} else if (holder.layout != null) {
			holder.layout.setPadding(paddingX, holder.layout.getPaddingTop(),
					holder.layout.getPaddingRight(), holder.layout.getPaddingBottom());
		}

		if (holder.btnWant != null) {
			holder.btnWant.setVisibility(inEditMode ? View.VISIBLE : View.GONE);
		}

		// There's common code in both buildViews that can be moved up here
		if (isFolder) {
			buildView((FilesAdapterDisplayFolder) oItem, holder);
		} else {
			buildView((FilesAdapterDisplayFile) oItem, holder);
		}
	}

	private void buildView(final FilesAdapterDisplayFolder oFolder,
			ViewHolder holder) {
		Map<?, ?> item = getFileMap(oFolder);

		ViewHolderFlipValidator validator = new ViewHolderFlipValidator(holder,
				torrentID, -3);
		boolean animateFlip = validator.isStillValid();
		holder.fileIndex = -3;
		holder.torrentID = torrentID;

		final String name = MapUtils.getMapString(item, "name", " ");

		if (holder.tvName != null) {
			int breakAt = AndroidUtils.lastindexOfAny(name, "\\/", name.length() - 2);
			String s = (breakAt > 0) ? name.substring(breakAt + 1) : name;
			flipper.changeText(holder.tvName, AndroidUtils.lineBreaker(s),
					animateFlip, validator);
		}
		if (holder.expando != null) {
			holder.expando.setImageResource(
					oFolder.expand ? R.drawable.expander_ic_maximized
							: R.drawable.expander_ic_minimized);
			holder.expando.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					oFolder.expand = !oFolder.expand;
					rebuildList();
				}
			});
		}
		if (holder.tvInfo != null) {
			String s = resources.getString(R.string.files_row_size,
					DisplayFormatters.formatByteCountToKiBEtc(oFolder.sizeWanted),
					DisplayFormatters.formatByteCountToKiBEtc(oFolder.size));
			s += ". " + DisplayFormatters.formatNumber(oFolder.numFilesWanted)
					+ " of " + DisplayFormatters.formatNumber(oFolder.numFiles);
			flipper.changeText(holder.tvInfo, s, animateFlip, validator);
		}
		if (holder.btnWant != null) {
			holder.btnWant.setImageResource(oFolder.numFiles == oFolder.numFilesWanted
					? R.drawable.btn_want : oFolder.numFilesWanted == 0
							? R.drawable.btn_unwant : R.drawable.ic_menu_want);
			holder.btnWant.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					flipWant(name);
				}
			});
		}
	}

	private void flipWant(FilesAdapterDisplayFolder oFolder) {
		Map<?, ?> item = getFileMap(oFolder);
		final String name = MapUtils.getMapString(item, "name", null);
		if (name == null) {
			return;
		}
		flipWant(name);
	}

	@SuppressWarnings({
		"unchecked",
		"rawtypes"
	})
	/* @Thunk */ void flipWant(String folder) {
		Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
		if (torrent == null) {
			return;
		}
		final List<?> listFiles = MapUtils.getMapList(torrent,
				TransmissionVars.FIELD_TORRENT_FILES, null);
		if (listFiles == null) {
			return;
		}

		boolean switchToWanted = false;
		List<Integer> listIndexes = new ArrayList<>();
		for (Object oFile : listFiles) {
			Map<?, ?> mapFile = (Map<?, ?>) oFile;
			String name = MapUtils.getMapString(mapFile, "name", "");
			if (name.startsWith(folder)) {
				boolean wanted = MapUtils.getMapBoolean(mapFile, "wanted", true);
				if (!wanted) {
					switchToWanted = true;
				}
				int index = MapUtils.getMapInt(mapFile,
						TransmissionVars.FIELD_FILES_INDEX, -1);
				// NO INDEX!?
				if (index >= 0) {
					listIndexes.add(index);
				}
			}
		}

		if (listIndexes.size() == 0) {
			// something went terribly wrong!
			return;
		}

		final int[] fileIndexes = new int[listIndexes.size()];
		for (int i = 0; i < fileIndexes.length; i++) {
			fileIndexes[i] = listIndexes.get(i);
			if (fileIndexes[i] < listFiles.size()) {
				Map map = (Map) listFiles.get(fileIndexes[i]);
				map.put("wanted", switchToWanted);
			}
		}
		rebuildList();
		final boolean wanted = switchToWanted;
		sessionInfo.executeRpc(new RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.setWantState("FolderWant", torrentID, fileIndexes, wanted, null);
			}
		});
	}

	private void buildView(final FilesAdapterDisplayFile oFile,
			ViewHolder holder) {
		Map<?, ?> item = getFileMap(oFile);
		final int fileIndex = MapUtils.getMapInt(item,
				TransmissionVars.FIELD_FILES_INDEX, -2);
		ViewHolderFlipValidator validator = new ViewHolderFlipValidator(holder,
				torrentID, fileIndex);
		boolean animateFlip = validator.isStillValid();
		holder.fileIndex = fileIndex;
		holder.torrentID = torrentID;

		final boolean wanted = MapUtils.getMapBoolean(item, "wanted", true);

		if (holder.tvName != null) {
			String s = MapUtils.getMapString(item, "name", " ");
			int breakAt = AndroidUtils.lastindexOfAny(s, "\\/", s.length());
			if (breakAt > 0) {
				s = s.substring(breakAt + 1);
			}
			flipper.changeText(holder.tvName, AndroidUtils.lineBreaker(s),
					animateFlip, validator);
		}
		long bytesCompleted = MapUtils.getMapLong(item, "bytesCompleted", 0);
		long length = MapUtils.getMapLong(item, "length", -1);
		if (length > 0) {
			float pctDone = (float) bytesCompleted / length;
			if (holder.tvProgress != null) {
				holder.tvProgress.setVisibility(
						inEditMode ? View.GONE : wanted ? View.VISIBLE : View.INVISIBLE);
				if (wanted && !inEditMode) {
					NumberFormat format = NumberFormat.getPercentInstance();
					format.setMaximumFractionDigits(1);
					String s = format.format(pctDone);
					flipper.changeText(holder.tvProgress, s, animateFlip, validator);
				}
			}
			if (holder.pb != null) {
				holder.pb.setVisibility(
						inEditMode ? View.GONE : wanted ? View.VISIBLE : View.INVISIBLE);
				if (wanted && !inEditMode) {
					holder.pb.setProgress((int) (pctDone * 10000));
				}
			}
		} else {
			if (holder.tvProgress != null) {
				holder.tvProgress.setVisibility(
						inEditMode ? View.GONE : View.INVISIBLE);
			}
			if (holder.pb != null) {
				holder.pb.setVisibility(inEditMode ? View.GONE : View.INVISIBLE);
			}
		}
		if (holder.tvInfo != null) {
			String s = inEditMode ? DisplayFormatters.formatByteCountToKiBEtc(length)
					: resources.getString(R.string.files_row_size,
							DisplayFormatters.formatByteCountToKiBEtc(bytesCompleted),
							DisplayFormatters.formatByteCountToKiBEtc(length));
			flipper.changeText(holder.tvInfo, s, animateFlip, validator);
		}
		if (holder.tvStatus != null) {
			int priority = MapUtils.getMapInt(item,
					TransmissionVars.FIELD_TORRENT_FILES_PRIORITY,
					TransmissionVars.TR_PRI_NORMAL);
			int id;
			switch (priority) {
				case TransmissionVars.TR_PRI_HIGH:
					id = R.string.torrent_file_priority_high;
					break;
				case TransmissionVars.TR_PRI_LOW:
					id = R.string.torrent_file_priority_low;
					break;
				default:
					id = R.string.torrent_file_priority_normal;
					break;
			}

			String s = resources.getString(id);
			flipper.changeText(holder.tvStatus, s, animateFlip, validator);
		}
		if (holder.btnWant != null) {
			holder.btnWant.setImageResource(
					wanted ? R.drawable.btn_want : R.drawable.btn_unwant);
			holder.btnWant.setOnClickListener(new OnClickListener() {
				@SuppressWarnings({
					"unchecked",
					"rawtypes"
				})
				@Override
				public void onClick(View v) {
					flipWant(oFile);
				}
			});
		}
	}

	public void flipWant(FilesAdapterDisplayObject o) {
		if (o instanceof FilesAdapterDisplayFile) {
			flipWant((FilesAdapterDisplayFile) o);
		} else if (o instanceof FilesAdapterDisplayFolder) {
			flipWant((FilesAdapterDisplayFolder) o);
		}
	}

	/* @Thunk */ void flipWant(FilesAdapterDisplayFile oFile) {
		final int fileIndex = oFile.fileIndex;
		if (fileIndex < 0) {
			return;
		}
		Map map = getFileMap(oFile);
		if (map == null) {
			return;
		}

		final boolean wanted = MapUtils.getMapBoolean(map, "wanted", true);
		map.put("wanted", !wanted);

		if (oFile.path == null || oFile.path.length() == 0) {
			long length = MapUtils.getMapLong(map,
					TransmissionVars.FIELD_FILES_LENGTH, 0);
			if (wanted) { // wanted -> unwanted
				totalNumFilesWanted--;
				totalSizeWanted -= length;
			} else {
				totalNumFilesWanted++;
				totalSizeWanted += length;
			}

			// notification will trigger fragment to update it's size ui
			notifyItemChanged(getPositionForItem(oFile));
		} else {
			rebuildList();
		}

		sessionInfo.executeRpc(new RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.setWantState("btnWant", torrentID, new int[] {
					fileIndex
				}, !wanted, null);
			}
		});
	}

	@Override
	public FileFilter getFilter() {
		if (filter == null) {
			filter = new FileFilter();
		}
		return filter;
	}

	public class FileFilter
		extends Filter
	{

		private CharSequence constraint;

		public void setFilterMode(int filterMode) {
			filter(constraint);
		}

		@Override
		protected FilterResults performFiltering(CharSequence constraint) {
			this.constraint = constraint;
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "performFIlter Start");
			}
			FilterResults results = new FilterResults();

			if (sessionInfo == null) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "noSessionInfo");
				}

				return results;
			}

			synchronized (mLock) {
				Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
				if (torrent == null) {
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "No torrent for " + torrentID);
					}
					return results;
				}
				final List<?> listFiles = MapUtils.getMapList(torrent,
						TransmissionVars.FIELD_TORRENT_FILES, null);
				if (listFiles == null) {
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "No files");
					}
					return results;
				}
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "listFiles=" + listFiles.size());
				}

				// Clear summaries for existing folders
				for (FilesAdapterDisplayFolder displayFolder : mapFolders.values()) {
					displayFolder.clearSummary();
				}

				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "cleared summary");
				}

				List<FilesAdapterDisplayObject> list = new ArrayList<>();
				long totalSizeWanted = 0;
				long totalNumFilesWanted = 0;

				for (int i = 0; i < listFiles.size(); i++) {
					Map<?, ?> mapFile = (Map<?, ?>) listFiles.get(i);
					String name = MapUtils.getMapString(mapFile,
							TransmissionVars.FIELD_FILES_NAME, "");

					// Get the folder name and see if we added it yet
					int folderBreaksAt = AndroidUtils.lastindexOfAny(name, "/\\", -1);
					String folderWithSlash = folderBreaksAt <= 0 ? ""
							: name.substring(0, folderBreaksAt + 1);
					if (folderWithSlash.length() > 0
							&& !mapFolders.containsKey(folderWithSlash)) {
						// add folder and parents
						String[] folderSplit = patternFolderSplit.split(folderWithSlash);
						int startAt = folderSplit[0].length() == 0 ? 1 : 0;
						int pos = startAt;
						FilesAdapterDisplayFolder last = null;
						for (int j = startAt; j < folderSplit.length; j++) {
							int oldPos = pos;
							pos += folderSplit[j].length() + 1;
							String folderWalk = folderWithSlash.substring(0, pos);

							FilesAdapterDisplayFolder existing = mapFolders.get(folderWalk);
							if (existing == null) {
								String path = folderWithSlash.substring(0, oldPos);
								// folderName == folderSplit[j], but substring will use same string
								String folderName = folderWithSlash.substring(oldPos, pos);
								FilesAdapterDisplayFolder displayFolder = new FilesAdapterDisplayFolder(
										folderWalk, j - startAt, last, path, folderName);
								last = displayFolder;
//								Log.e(TAG, i + "." + j + "] " + folderName + "] " + folderWalk
//										+ " for " + name);
								mapFolders.put(folderWalk, displayFolder);
							} else {
								last = existing;
							}
						}
					}
					String path = folderWithSlash;
					String shortName = name.substring(folderWithSlash.length(),
							name.length());

					FilesAdapterDisplayFolder displayFolder = mapFolders.get(
							folderWithSlash);
					if (displayFolder == null) {
						// probably root
						list.add(new FilesAdapterDisplayFile(i, 0, null, mapFile, path,
								shortName));
						if (path.length() == 0) {
							long length = MapUtils.getMapLong(mapFile,
									TransmissionVars.FIELD_FILES_LENGTH, 0);
							boolean wanted = MapUtils.getMapBoolean(mapFile,
									TransmissionVars.FIELD_FILESTATS_WANTED, true);
							if (wanted) {
								totalNumFilesWanted++;
								totalSizeWanted += length;
							}
						}
					} else {
						displayFolder.summarize(mapFile);
						if (displayFolder.expand && displayFolder.parentsExpanded()) {
							list.add(new FilesAdapterDisplayFile(i, displayFolder.level + 1,
									displayFolder, mapFile, path, shortName));
						}
					}
				}

				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "processed files");
				}

				// add all the folders to the end -- they will sort soon
				// calculate global totals
				for (String key : mapFolders.keySet()) {
					FilesAdapterDisplayFolder displayFolder = mapFolders.get(key);
					if (displayFolder.parentsExpanded()) {
						list.add(displayFolder);
					}
					if (displayFolder.level == 0) {
						totalSizeWanted += displayFolder.sizeWanted;
						totalNumFilesWanted += displayFolder.numFilesWanted;
					}
				}

				doSort(list);

				Map map = new HashMap<>();
				map.put("list", list);
				map.put("totalSizeWanted", totalSizeWanted);
				map.put("totalNumFilesWanted", totalNumFilesWanted);
				refreshSections(list, map);

				results.values = map;
				results.count = list.size();
			}

			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "performFIlter End");
			}
			return results;
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void publishResults(CharSequence constraint,
				FilterResults results) {
			// Now we have to inform the adapter about the new list filtered
			if (results.count == 0) {
				removeAllItems();
			} else {
				synchronized (mLock) {
					if (results.values instanceof Map) {
						Map map = (Map) results.values;
						List<FilesAdapterDisplayObject> displayList = (List<FilesAdapterDisplayObject>) map.get(
								"list");
						synchronized (lockSections) {
							sections = (String[]) map.get("sections");
							sectionStarts = (List<Integer>) map.get("sectionStarts");
						}

						totalSizeWanted = MapUtils.getMapLong(map, "totalSizeWanted", 0);
						totalNumFilesWanted = MapUtils.getMapLong(map,
								"totalNumFilesWanted", 0);

						if (displayList == null) {
							displayList = new ArrayList<>();
						}

						setItems(displayList);
					}
				}
			}
		}

	}

	public void refreshSections(List<FilesAdapterDisplayObject> displayList,
			Map map) {
		synchronized (mLock) {
			List<String> categories = new ArrayList<>();
			List<Integer> categoriesStart = new ArrayList<>();
			String lastFullCat = " ";
			Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
			List<?> listFiles = MapUtils.getMapList(torrent,
					TransmissionVars.FIELD_TORRENT_FILES, null);

			if (listFiles != null) {
				for (int i = 0; i < displayList.size(); i++) {
					FilesAdapterDisplayObject displayObject = displayList.get(i);
					if (displayObject instanceof FilesAdapterDisplayFolder) {
						continue;
					}
					Map<?, ?> mapFile = getFileMap(displayObject, listFiles);
					String name = MapUtils.getMapString(mapFile, "name", "").toUpperCase(
							Locale.US);
					if (!name.startsWith(lastFullCat)) {
						final int MAX_CATS = 3;
						String[] split = patternFolderSplit.split(name, MAX_CATS + 1);
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
									cat += "/";
								}
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
			}
			// We could split larger gaps into two sections with the same name
			map.put("sections", categories.toArray(new String[categories.size()]));
			map.put("sectionStarts", categoriesStart);
		}
		//if (AndroidUtils.DEBUG) {
			//Log.d(TAG, "Sections: " + Arrays.toString(sections));
			//Log.d(TAG, "SectionStarts: " + sectionStarts);
		//}
	}

	/* @Thunk */ void doSort(List<FilesAdapterDisplayObject> list) {
		if (sessionInfo == null) {
			return;
		}
		if (!sorter.isValid()) {
			return;
		}

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "Sort by " + sorter.toDebugString());
		}

		if (AndroidUtilsUI.isUIThread()) {
			Log.w(TAG,
					"Sorting on UIThread! " + AndroidUtils.getCompressedStackTrace());
		}

		synchronized (mLock) {
			doSort(list, sorter, false);
		}
	}

	@SuppressWarnings("rawtypes")
	protected Map<?, ?> getFileMap(Object o, List<?> mapList) {
		if (o instanceof FilesAdapterDisplayFile) {
			if (mapList == null) {
				return new HashMap();
			}
			FilesAdapterDisplayFile file = (FilesAdapterDisplayFile) o;
			return (Map<?, ?>) mapList.get(file.fileIndex);
		}
		if (o instanceof FilesAdapterDisplayFolder) {
			return ((FilesAdapterDisplayFolder) o).map;
		}
		return new HashMap();
	}

	@SuppressWarnings("rawtypes")
	private Map<?, ?> getFileMap(
			FilesAdapterDisplayObject filesAdapterDisplayObject) {

		if (sessionInfo == null) {
			return new HashMap();
		}

		return filesAdapterDisplayObject.getMap(sessionInfo, torrentID);
	}

	public void setTorrentID(long torrentID) {
		// sync because we don't want notifyDataSetChanged to be processing
		synchronized (mLock) {
			if (this.torrentID != -1 && this.torrentID != torrentID) {
				mapFolders.clear();
			}
			this.torrentID = torrentID;
		}

		getFilter().filter("");
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItemId(int)
	 */
	@Override
	public long getItemId(int position) {
		FilesAdapterDisplayObject filesAdapterDisplayObject = getItem(position);
		if (filesAdapterDisplayObject instanceof FilesAdapterDisplayFile) {
			FilesAdapterDisplayFile dof = (FilesAdapterDisplayFile) filesAdapterDisplayObject;
			return dof.fileIndex;
		}
		return -position;
	}

	@Override
	public int getItemViewType(int position) {
		int type = (getItem(position) instanceof FilesAdapterDisplayFolder)
				? TYPE_FOLDER : TYPE_FILE;
		return type;
	}

	/* @Thunk */ void rebuildList() {
		getFilter().filter("");
	}

	/* (non-Javadoc)
	 * @see android.widget.SectionIndexer#getSections()
	 */
	@Override
	public Object[] getSections() {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG,
					"GetSections " + (sections == null ? "NULL" : sections.length));
		}
		return sections;
	}

	/* (non-Javadoc)
	 * @see android.widget.SectionIndexer#getPositionForSection(int)
	 */
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

	/* (non-Javadoc)
	 * @see android.widget.SectionIndexer#getSectionForPosition(int)
	 */
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

	@NonNull
	@Override
	public String getSectionName(int position) {
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

	public boolean isInEditMode() {
		return inEditMode;
	}

	public void setInEditMode(boolean inEditMode) {
		this.inEditMode = inEditMode;
		notifyDataSetInvalidated();
	}

	public long getTotalSizeWanted() {
		return totalSizeWanted;
	}
}
