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

import org.gudy.azureus2.core3.util.DisplayFormatters;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.aelitis.azureus.util.MapUtils;
import com.vuze.android.remote.*;
import com.vuze.android.remote.TextViewFlipper.FlipValidator;

public class FilesAdapter
	extends BaseAdapter
	implements Filterable, SectionIndexer
{
	private static final String TAG = "FilesAdapter";

	static class ViewHolder
	{
		TextView tvName;

		TextView tvProgress;

		ProgressBar pb;

		TextView tvInfo;

		TextView tvStatus;

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
			return holder.fileIndex == fileIndex && holder.torrentID == torrentID;
		}
	}

	private Context context;

	private FileFilter filter;

	/** List of they keys of all entries displayed, in the display order */
	private List<Integer> displayList = new ArrayList<Integer>(0);

	public Object mLock = new Object();

	private Comparator<? super Map<?, ?>> comparator;

	private Resources resources;

	private String[] sortFieldIDs = {
		"name"
	};

	private Boolean[] sortOrderAsc = {
		true
	};

	private SessionInfo sessionInfo;

	private long torrentID;

	private TextViewFlipper flipper;

	private String[] sections;

	private List<Integer> sectionStarts;

	public FilesAdapter(Context context) {
		this.context = context;
		resources = context.getResources();
		flipper = new TextViewFlipper(R.anim.anim_field_change);
		displayList = new ArrayList<Integer>();
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

	public View getView(int position, View convertView, ViewGroup parent,
			boolean requireHolder) {
		View rowView = convertView;
		if (rowView == null) {
			if (requireHolder) {
				return null;
			}
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			rowView = inflater.inflate(R.layout.row_file_list, parent, false);
			ViewHolder viewHolder = new ViewHolder();

			viewHolder.tvName = (TextView) rowView.findViewById(R.id.filerow_name);

			viewHolder.tvProgress = (TextView) rowView.findViewById(R.id.filerow_progress_pct);
			viewHolder.pb = (ProgressBar) rowView.findViewById(R.id.filerow_progress);
			viewHolder.tvInfo = (TextView) rowView.findViewById(R.id.filerow_info);
			viewHolder.tvStatus = (TextView) rowView.findViewById(R.id.filerow_state);

			rowView.setTag(viewHolder);
		}

		ViewHolder holder = (ViewHolder) rowView.getTag();
		Map<?, ?> item = getItem(position);

		int fileIndex = MapUtils.getMapInt(item, "index", -2);
		ViewHolderFlipValidator validator = new ViewHolderFlipValidator(holder,
				torrentID, fileIndex);
		boolean animateFlip = validator.isStillValid();
		holder.fileIndex = fileIndex;
		holder.torrentID = torrentID;

		boolean wanted = MapUtils.getMapBoolean(item, "wanted", true);

		if (holder.tvName != null) {
			flipper.changeText(holder.tvName,
					AndroidUtils.lineBreaker(MapUtils.getMapString(item, "name", " ")),
					animateFlip, validator);
		}
		long bytesCompleted = MapUtils.getMapLong(item, "bytesCompleted", 0);
		long length = MapUtils.getMapLong(item, "length", -1);
		if (length > 0) {
			float pctDone = (float) bytesCompleted / length;
			if (holder.tvProgress != null) {
				holder.tvProgress.setVisibility(wanted ? View.VISIBLE : View.INVISIBLE);
				if (wanted) {
					NumberFormat format = NumberFormat.getPercentInstance();
					format.setMaximumFractionDigits(1);
					String s = format.format(pctDone);
					flipper.changeText(holder.tvProgress, s, animateFlip, validator);
				}
			}
			if (holder.pb != null) {
				holder.pb.setVisibility(wanted ? View.VISIBLE : View.INVISIBLE);
				if (wanted) {
					holder.pb.setProgress((int) (pctDone * 10000));
				}
			}
		}
		if (holder.tvInfo != null) {
			String s = resources.getString(R.string.files_row_size,
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

		return rowView;
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
					return results;
				}
				final List<?> listFiles = MapUtils.getMapList(torrent, "files", null);
				if (listFiles == null) {
					return results;
				}
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "listFiles=" + listFiles.size());
				}
				List<Integer> listIndexes = new ArrayList<Integer>();
				for (int i = 0; i < listFiles.size(); i++) {
					listIndexes.add(i);
				}

				doSort(listIndexes);

				results.values = listIndexes;
				results.count = listIndexes.size();
			}

			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "performFIlter End");
			}
			return results;
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void publishResults(CharSequence constraint, FilterResults results) {
			if (results.values instanceof List) {
				synchronized (mLock) {
					displayList = (List<Integer>) results.values;

					if (displayList == null) {
						displayList = new ArrayList<Integer>();
					}
				}
			}
			notifyDataSetChanged();
		}

	}

	@Override
	public void notifyDataSetChanged() {
		if (sessionInfo == null) {
			super.notifyDataSetChanged();
			return;
		}
		synchronized (mLock) {
			List<String> categories = new ArrayList<String>();
			List<Integer> categoriesStart = new ArrayList<Integer>();
			String lastFullCat = " ";
			Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
			List<?> listFiles = MapUtils.getMapList(torrent, "files", null);

			if (listFiles != null) {
				for (int i = 0; i < displayList.size(); i++) {
					Integer index = displayList.get(i);
					Map<?, ?> mapFile = (Map<?, ?>) listFiles.get(index);

					String name = MapUtils.getMapString(mapFile, "name", "").toUpperCase(
							Locale.US);
					if (!name.startsWith(lastFullCat)) {
						String[] split = name.split("[\\\\/]", 3);
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
			sections = categories.toArray(new String[0]);
			sectionStarts = categoriesStart;
		}
		if (AndroidUtils.DEBUG) {
			//Log.d(TAG, "Sections: " + Arrays.toString(sections));
			//Log.d(TAG, "SectionStarts: " + sectionStarts);
		}

		super.notifyDataSetChanged();
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

	private void doSort(List<Integer> list) {
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
				VuzeEasyTracker.getInstance(context).logError(context, t);
				return 0;
			}

			@SuppressWarnings("rawtypes")
			@Override
			public Map<?, ?> mapGetter(Object o) {
				if (torrent == null) {
					torrent = sessionInfo.getTorrent(torrentID);
					mapList = MapUtils.getMapList(torrent, "files", null);
				}
				if (mapList == null) {
					return new HashMap();
				}
				Integer index = (Integer) o;
				return (Map<?, ?>) mapList.get(index);
			}
		};

		synchronized (mLock) {
			Collections.sort(list, sorter);
		}
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
		"rawtypes",
		"unchecked"
	})
	@Override
	public Map<?, ?> getItem(int position) {
		if (sessionInfo == null) {
			return new HashMap();
		}
		Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);

		Integer index;
		synchronized (mLock) {
			if (position < 0 || position > displayList.size()) {
				return new HashMap();
			}
			index = displayList.get(position);
		}
		List<?> listFiles = MapUtils.getMapList(torrent, "files", null);
		if (listFiles == null || index >= listFiles.size()) {
			return new HashMap();
		}
		List<?> listFileStats = MapUtils.getMapList(torrent, "fileStats", null);
		Map<?, ?> mapFile = (Map) listFiles.get(index);

		Map map = new HashMap(mapFile);
		if (listFileStats != null && index < listFileStats.size()) {
			map.putAll((Map) listFileStats.get(index));
		}
		map.put("index", index);
		return map;
	}

	public void setTorrentID(long torrentID) {
		// sync because we don't want notifyDataSetChanged to be processing
		synchronized (mLock) {
			this.torrentID = torrentID;
		}

		getFilter().filter("");
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItemId(int)
	 */
	@Override
	public long getItemId(int position) {
		Integer index = displayList.get(position);
		if (index == null) {
			return 0;
		}
		return index;
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
}
