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

package com.vuze.android.remote.fragment;

import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Pattern;

import org.gudy.azureus2.core3.util.DisplayFormatters;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.util.TypedValue;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.*;
import android.widget.RelativeLayout.LayoutParams;

import com.aelitis.azureus.util.MapUtils;
import com.vuze.android.remote.*;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.TextViewFlipper.FlipValidator;
import com.vuze.android.remote.rpc.TransmissionRPC;

public class FilesTreeAdapter
	extends BaseAdapter
	implements Filterable, SectionIndexer
{
	private static final String TAG = "FilesTreeAdapter2";

	private static Pattern patternFolderSplit = Pattern.compile("[\\\\/]");

	static class ViewHolder
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
	}

	public static class ViewHolderFlipValidator
		implements FlipValidator
	{
		private ViewHolder holder;

		private int fileIndex = -1;

		private long torrentID;

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

	private Context context;

	private FileFilter filter;

	private List<FilesAdapterDisplayObject> displayList = new ArrayList<FilesAdapterDisplayObject>(
			0);

	private Map<String, FilesAdapterDisplayFolder> mapFolders = new HashMap<>();

	public Object mLock = new Object();

	private Comparator<? super Map<?, ?>> comparator;

	private Resources resources;

	private String[] sortFieldIDs = {
		"name",
		"index"
	};

	private Boolean[] sortOrderAsc = {
		true,
		true
	};

	private SessionInfo sessionInfo;

	private long torrentID;

	private TextViewFlipper flipper;

	private String[] sections;

	private List<Integer> sectionStarts;

	private int levelPaddingPx;

	private boolean inEditMode = false;

	private int levelPadding2Px;

	public FilesTreeAdapter(Context context) {
		this.context = context;
		resources = context.getResources();
		flipper = new TextViewFlipper(R.anim.anim_field_change);
		displayList = new ArrayList<FilesAdapterDisplayObject>();

		levelPaddingPx = (int) TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_DIP, 20, resources.getDisplayMetrics());
		levelPadding2Px = (int) TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_DIP, 5, resources.getDisplayMetrics());
	}

	public void setSessionInfo(SessionInfo sessionInfo) {
		this.sessionInfo = sessionInfo;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		return getView(position, convertView, parent, false);
	}

	public void refreshView(int position, View view, ListView listView) {
		getView(position, view, listView, true);
	}

	@SuppressWarnings("deprecation")
	public View getView(int position, View convertView, ViewGroup parent,
			boolean requireHolder) {
		Object oItem = getItem(position);
		boolean isFolder = (oItem instanceof FilesAdapterDisplayFolder);

		View rowView = convertView;
		if (rowView != null) {
			boolean isRowFolder = ((ViewHolder) rowView.getTag()).expando != null;
			if (isFolder != isRowFolder) {
				rowView = null;
			}
		}
		if (rowView == null) {
			if (requireHolder) {
				return null;
			}
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			rowView = inflater.inflate(isFolder ? R.layout.row_folder_selection
					: R.layout.row_file_selection, parent, false);
			ViewHolder viewHolder = new ViewHolder();

			viewHolder.tvName = (TextView) rowView.findViewById(R.id.filerow_name);

			viewHolder.tvProgress = (TextView) rowView.findViewById(R.id.filerow_progress_pct);
			viewHolder.pb = (ProgressBar) rowView.findViewById(R.id.filerow_progress);
			viewHolder.tvInfo = (TextView) rowView.findViewById(R.id.filerow_info);
			viewHolder.tvStatus = (TextView) rowView.findViewById(R.id.filerow_state);
			viewHolder.expando = (ImageButton) rowView.findViewById(R.id.filerow_expando);
			viewHolder.btnWant = (ImageButton) rowView.findViewById(R.id.filerow_btn_dl);
			viewHolder.strip = rowView.findViewById(R.id.filerow_indent);
			viewHolder.layout = (RelativeLayout) rowView.findViewById(R.id.filerow_layout);

			rowView.setTag(viewHolder);
		}

		ViewHolder holder = (ViewHolder) rowView.getTag();

		int level = ((FilesAdapterDisplayObject) oItem).level;
		int paddingX = levelPaddingPx * level;
		int parentWidth = parent.getWidth();
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
						levelPaddingPx * level, LayoutParams.FILL_PARENT));
			} else if (lp instanceof RelativeLayout.LayoutParams) {
				holder.strip.setLayoutParams(new RelativeLayout.LayoutParams(
						levelPaddingPx * level, LayoutParams.FILL_PARENT));
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
		return rowView;
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
			holder.expando.setImageResource(oFolder.expand
					? R.drawable.expander_ic_maximized : R.drawable.expander_ic_minimized);
			holder.expando.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					oFolder.expand = !oFolder.expand;
					refreshList();
				}
			});
		}
		if (holder.tvInfo != null) {
			String s = resources.getString(R.string.files_row_size,
					DisplayFormatters.formatByteCountToKiBEtc(oFolder.sizeWanted),
					DisplayFormatters.formatByteCountToKiBEtc(oFolder.size));
			s += ". " + oFolder.numFilesWanted + " of " + oFolder.numFiles;
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

	@SuppressWarnings({
		"unchecked",
		"rawtypes"
	})
	protected void flipWant(String folder) {
		Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
		if (torrent == null) {
			return;
		}
		final List<?> listFiles = MapUtils.getMapList(torrent, "files", null);
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
				int index = MapUtils.getMapInt(mapFile, "index", -1);
				if (index >= 0) {
					listIndexes.add(index);
				}
			}
		}

		final int[] fileIndexes = new int[listIndexes.size()];
		for (int i = 0; i < fileIndexes.length; i++) {
			fileIndexes[i] = listIndexes.get(i);
			Map map = (Map) listFiles.get(fileIndexes[i]);
			map.put("wanted", switchToWanted);
		}
		refreshList();
		final boolean wanted = switchToWanted;
		sessionInfo.executeRpc(new RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.setWantState("FolderWant", torrentID, fileIndexes, wanted, null);
			}
		});
	}

	private void buildView(final FilesAdapterDisplayFile oFile, ViewHolder holder) {
		Map<?, ?> item = getFileMap(oFile);
		final int fileIndex = MapUtils.getMapInt(item, "index", -2);
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
				holder.tvProgress.setVisibility(inEditMode ? View.GONE : wanted
						? View.VISIBLE : View.INVISIBLE);
				if (wanted && !inEditMode) {
					NumberFormat format = NumberFormat.getPercentInstance();
					format.setMaximumFractionDigits(1);
					String s = format.format(pctDone);
					flipper.changeText(holder.tvProgress, s, animateFlip, validator);
				}
			}
			if (holder.pb != null) {
				holder.pb.setVisibility(inEditMode ? View.GONE : wanted ? View.VISIBLE
						: View.INVISIBLE);
				if (wanted && !inEditMode) {
					holder.pb.setProgress((int) (pctDone * 10000));
				}
			}
		} else {
			if (holder.tvProgress != null) {
				holder.tvProgress.setVisibility(inEditMode ? View.GONE : View.INVISIBLE);
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
			holder.btnWant.setImageResource(wanted ? R.drawable.btn_want
					: R.drawable.btn_unwant);
			holder.btnWant.setOnClickListener(new OnClickListener() {
				@SuppressWarnings({
					"unchecked",
					"rawtypes"
				})
				@Override
				public void onClick(View v) {
					if (fileIndex < 0) {
						return;
					}
					Map map = getFileMap(oFile);
					if (map != null) {
						map.put("wanted", !wanted);
						refreshList();
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
			});
		}
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
				final List<?> listFiles = MapUtils.getMapList(torrent, "files", null);
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

				List<FilesAdapterDisplayObject> list = new ArrayList<FilesAdapterDisplayObject>();

				for (int i = 0; i < listFiles.size(); i++) {
					Map<?, ?> mapFile = (Map<?, ?>) listFiles.get(i);
					String name = MapUtils.getMapString(mapFile,
							TransmissionVars.FIELD_FILES_NAME, "");

					// Get the folder name and see if we added it yet
					int folderBreaksAt = AndroidUtils.lastindexOfAny(name, "/\\", -1);
					String folderWithSlash = folderBreaksAt <= 0 ? "" : name.substring(0,
							folderBreaksAt + 1);
					if (!mapFolders.containsKey(folderWithSlash)) {
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

					FilesAdapterDisplayFolder displayFolder = mapFolders.get(folderWithSlash);
					if (displayFolder == null) {
						// probably root
						list.add(new FilesAdapterDisplayFile(i, 0, null, mapFile, path,
								shortName));
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
				for (String key : mapFolders.keySet()) {
					FilesAdapterDisplayFolder filesAdapterDisplayFolder = mapFolders.get(key);
					if (filesAdapterDisplayFolder.parentsExpanded()) {
						list.add(filesAdapterDisplayFolder);
					}
				}

				doSort(list);

				Map map = new HashMap<>();
				map.put("list", list);
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
		protected void publishResults(CharSequence constraint, FilterResults results) {
			if (results.values instanceof Map) {
				Map map = (Map) results.values;
				synchronized (mLock) {
					displayList = (List<FilesAdapterDisplayObject>) map.get("list");

					sections = (String[]) map.get("sections");
					sectionStarts = (List<Integer>) map.get("sectionStarts");

					if (displayList == null) {
						displayList = new ArrayList<FilesAdapterDisplayObject>();
					}
				}
			}
			notifyDataSetChanged();
		}

	}

	public void refreshSections(List<FilesAdapterDisplayObject> displayList,
			Map map) {
		synchronized (mLock) {
			List<String> categories = new ArrayList<String>();
			List<Integer> categoriesStart = new ArrayList<Integer>();
			String lastFullCat = " ";
			Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
			List<?> listFiles = MapUtils.getMapList(torrent, "files", null);

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
						String[] split = patternFolderSplit.split(name, 3);
						String cat = "";
						int count = 0;
						int end = 0;
						for (int j = 0; j < split.length; j++) {
							if (j > 0) {
								end++;
							}

							String g = split[j];

							if (g.length() > 0) {
								cat += g.substring(0, 1);
								count++;
								if (count >= 2 || j == split.length - 1) {
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
			map.put("sections", categories.toArray(new String[0]));
			map.put("sectionStarts", categoriesStart);
		}
		if (AndroidUtils.DEBUG) {
			//Log.d(TAG, "Sections: " + Arrays.toString(sections));
			//Log.d(TAG, "SectionStarts: " + sectionStarts);
		}
	}

	public void setSort(String[] fieldIDs, Boolean[] sortOrderAsc) {
		synchronized (mLock) {
			sortFieldIDs = fieldIDs;
			Boolean[] order;
			if (sortOrderAsc == null) {
				order = new Boolean[sortFieldIDs.length];
				Arrays.fill(order, Boolean.FALSE);
			} else if (sortOrderAsc.length != sortFieldIDs.length) {
				order = new Boolean[sortFieldIDs.length];
				Arrays.fill(order, Boolean.FALSE);
				System.arraycopy(sortOrderAsc, 0, order, 0, sortOrderAsc.length);
			} else {
				order = sortOrderAsc;
			}
			this.sortOrderAsc = order;
			comparator = null;
		}
		getFilter().filter("");
	}

	public void setSort(Comparator<? super Map<?, ?>> comparator) {
		synchronized (mLock) {
			this.comparator = comparator;
		}
		getFilter().filter("");
	}

	private void doSort(List<FilesAdapterDisplayObject> list) {
		if (sessionInfo == null) {
			return;
		}
		if (comparator == null && sortFieldIDs == null) {
			return;
		}

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "Sort by " + Arrays.toString(sortFieldIDs));
		}

		ComparatorMapFields sorter = new ComparatorMapFields(sortFieldIDs,
				sortOrderAsc, comparator) {

			private Map<?, ?> torrent;

			private List<?> mapList;

			@Override
			public int reportError(Comparable<?> oLHS, Comparable<?> oRHS, Throwable t) {
				VuzeEasyTracker.getInstance(context).logError(t);
				return 0;
			}

			@Override
			public Map<?, ?> mapGetter(Object o) {
				if (torrent == null) {
					torrent = sessionInfo.getTorrent(torrentID);
					mapList = MapUtils.getMapList(torrent, "files", null);
				}
				return getFileMap(o, mapList);
			}

		};

		synchronized (mLock) {
			Collections.sort(list, sorter);
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

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getCount()
	 */
	@Override
	public int getCount() {
		synchronized (mLock) {
			return displayList == null ? 0 : displayList.size();
		}
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItem(int)
	 */
	@SuppressWarnings({
		"rawtypes"
	})
	@Override
	public Object getItem(int position) {
		synchronized (mLock) {
			if (position < 0 || position > displayList.size()) {
				return new HashMap();
			}
			return displayList.get(position);
		}
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
		FilesAdapterDisplayObject filesAdapterDisplayObject = displayList.get(position);
		if (filesAdapterDisplayObject instanceof FilesAdapterDisplayFile) {
			FilesAdapterDisplayFile dof = (FilesAdapterDisplayFile) filesAdapterDisplayObject;
			return dof.fileIndex;
		}
		return -position;
	}

	/* (non-Javadoc)
	 * @see android.widget.BaseAdapter#getViewTypeCount()
	 */
	@Override
	public int getViewTypeCount() {
		return 2;
	}

	/* (non-Javadoc)
	 * @see android.widget.BaseAdapter#getItemViewType(int)
	 */
	@Override
	public int getItemViewType(int position) {
		return super.getItemViewType(position);
	}

	public void clearList() {
		synchronized (mLock) {
			displayList.clear();
		}
		notifyDataSetChanged();
	}

	public void refreshList() {
		getFilter().filter("");
	}

	/* (non-Javadoc)
	 * @see android.widget.SectionIndexer#getSections()
	 */
	@Override
	public Object[] getSections() {
		Log.d(TAG, "GetSections " + (sections == null ? "NULL" : sections.length));
		return sections;
	}

	/* (non-Javadoc)
	 * @see android.widget.SectionIndexer#getPositionForSection(int)
	 */
	@Override
	public int getPositionForSection(int sectionIndex) {
		if (sectionIndex < 0 || sectionStarts == null
				|| sectionIndex >= sectionStarts.size()) {
			return 0;
		}
		return sectionStarts.get(sectionIndex);
	}

	/* (non-Javadoc)
	 * @see android.widget.SectionIndexer#getSectionForPosition(int)
	 */
	@Override
	public int getSectionForPosition(int position) {
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

	public boolean isInEditMode() {
		return inEditMode;
	}

	public void setInEditMode(boolean inEditMode) {
		this.inEditMode = inEditMode;
		refreshList();
	}
}
