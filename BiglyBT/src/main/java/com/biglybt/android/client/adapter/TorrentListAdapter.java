/*
 * Copyright (c) Azureus Software, Inc, All Rights Reserved.
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
 */

package com.biglybt.android.client.adapter;

import java.util.*;

import com.biglybt.android.*;
import com.biglybt.android.client.*;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.util.MapUtils;
import com.biglybt.android.util.TextViewFlipper.FlipValidator;
import com.biglybt.util.ComparatorMapFields;
import com.biglybt.util.Thunk;
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;

import android.arch.lifecycle.Lifecycle;
import android.content.Context;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.LongSparseArray;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filterable;
import android.widget.SectionIndexer;

/**
 * Checked == Activated according to google.  In google docs for View
 * .setActivated:
 * (Um, yeah, we are deeply sorry about the terminology here.)
 * <p/>
 * </p>
 * Other terms:
 * Focused: One focus per screen
 * Selected: highlighted item(s).  May not be activated
 * Checked: activated item(s)
 */
public class TorrentListAdapter
	extends
	FlexibleRecyclerAdapter<FlexibleRecyclerViewHolder, TorrentListAdapterItem>
	implements Filterable,
	FlexibleRecyclerAdapter.SetItemsCallBack<TorrentListAdapterItem>,
	SectionIndexer, FastScrollRecyclerView.SectionedAdapter, SortableAdapter
{
	public final static int FILTERBY_ALL = 8;

	public final static int FILTERBY_ACTIVE = 4;

	public final static int FILTERBY_COMPLETE = 9;

	public final static int FILTERBY_INCOMPLETE = 1;

	public final static int FILTERBY_STOPPED = 2;

	@Thunk
	static final boolean DEBUG = AndroidUtils.DEBUG_ADAPTER;

	private static final String TAG = "TorrentListAdapter";

	public static final int VIEWTYPE_TORRENT = 0;

	public static final int VIEWTYPE_HEADER = 2;

	public class TorrentListComparator
		extends ComparatorMapFields<TorrentListAdapterItem>
		implements FlexibleReyclerGroupDefiner<TorrentListAdapterItem>
	{

		public Throwable lastError;

		private Long tagUID_Active;

		public boolean showGroupCount() {
			SortDefinition sortDefinition = getSortDefinition();
			if (sortDefinition instanceof GroupedSortDefinition) {
				return ((GroupedSortDefinition) sortDefinition).showGroupCount();
			}
			return false;
		}

		public int getMinCountBeforeGrouping() {
			SortDefinition sortDefinition = getSortDefinition();
			if (sortDefinition instanceof GroupedSortDefinition) {
				return ((GroupedSortDefinition) sortDefinition).getMinCountBeforeGrouping();
			}
			return -1;
		}

		@Override
		public boolean supportsGrouping() {
			return getSortDefinition() instanceof GroupedSortDefinition;
		}

		@Override
		public Comparable getGroupID(TorrentListAdapterItem item, boolean isAsc,
				List<TorrentListAdapterItem> items) {
			SortDefinition sortDefinition = getSortDefinition();
			if (sortDefinition instanceof GroupedSortDefinition) {
				return ((GroupedSortDefinition) sortDefinition).getSectionID(item,
						isAsc, items);
			}
			return null;
		}

		@Override
		public String getGroupName(Comparable item, boolean isAsc) {
			SortDefinition sortDefinition = getSortDefinition();
			if (sortDefinition instanceof GroupedSortDefinition) {
				String sectionName = ((GroupedSortDefinition) sortDefinition).getSectionName(
						item, isAsc);
				if (sectionName != null) {
					return sectionName;
				}
			}
			return "";
		}

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
			Log.e(TAG, "TorrentSort", t);
			AnalyticsTracker.getInstance(TorrentListAdapter.this.context).logError(t);
			return 0;
		}

		@Override
		public Map<?, ?> mapGetter(TorrentListAdapterItem o) {
			if (o instanceof TorrentListAdapterTorrentItem) {
				return ((TorrentListAdapterTorrentItem) o).getTorrentMap(session);
			}
			return null;
		}

		@SuppressWarnings("rawtypes")
		@Override
		public Comparable modifyField(String fieldID, Map map, Comparable o) {
			if (fieldID.equals("ActiveSort")) {
				boolean active;
				List<?> listTagUIDs = MapUtils.getMapList(map,
						TransmissionVars.FIELD_TORRENT_TAG_UIDS, null);
				if (listTagUIDs != null && tagUID_Active == null) {
					tagUID_Active = session.tag.getDownloadStateUID(7);
				}
				if (listTagUIDs != null && tagUID_Active != null) {
					active = listTagUIDs.contains(tagUID_Active);
				} else {
					long rateDL = MapUtils.getMapLong(map,
							TransmissionVars.FIELD_TORRENT_RATE_DOWNLOAD, 0);
					long rateUL = MapUtils.getMapLong(map,
							TransmissionVars.FIELD_TORRENT_RATE_UPLOAD, 0);
					active = rateDL > 0 && rateUL > 0;
				}
				return active;
			}
			if (fieldID.equals(TransmissionVars.FIELD_TORRENT_ETA)) {
				if (((Number) o).longValue() < 0) {
					o = Long.MAX_VALUE;
				}
			}
			return o;
		}
	}

	@Thunk
	TorrentListComparator sorter;

	@Thunk
	String[] sections;

	@Thunk
	List<Integer> sectionStarts;

	@Thunk
	final Object lockSections = new Object();

	public static class ViewHolderFlipValidator
		implements FlipValidator
	{
		private final TorrentListViewHolder holder;

		private final long torrentID;

		public ViewHolderFlipValidator(TorrentListViewHolder holder,
				long torrentID) {
			this.holder = holder;
			this.torrentID = torrentID;
		}

		@Override
		public boolean isStillValid() {
			return holder.torrentID == torrentID;
		}
	}

	@Thunk
	Context context;

	private TorrentFilter filter;

	@Thunk
	final Object mLock = new Object();

	@Thunk
	Session session;

	private final TorrentListRowFiller torrentListRowFiller;

	@Thunk
	boolean isRefreshing;

	private List<Comparable> groupIDs;

	private final Map<Comparable, Boolean> mapGroupIDCollapsed = new HashMap<>();

	private final boolean smallView;

	public TorrentListAdapter(Context context, Lifecycle lifecycle,
			FlexibleRecyclerSelectionListener selector, boolean smallView) {
		super(lifecycle, selector);
		this.context = context;

		torrentListRowFiller = new TorrentListRowFiller(context);
		this.smallView = smallView;

		sorter = new TorrentListComparator();
		setHasStableIds(true);
	}

	public TorrentListComparator getSorter() {
		return sorter;
	}

	@Override
	public boolean areContentsTheSame(TorrentListAdapterItem oldItem,
			TorrentListAdapterItem newItem) {
		if (!(oldItem instanceof TorrentListAdapterTorrentItem)) {
			return true;
		}
		Map<?, ?> torrent = ((TorrentListAdapterTorrentItem) oldItem).getTorrentMap(
				session);
		long lastUpdated = MapUtils.getMapLong(torrent,
				TransmissionVars.FIELD_LAST_UPDATED, 0);
		long lastSetItemsOn = getLastSetItemsOn();
		return lastUpdated <= lastSetItemsOn;
	}

	public void lettersUpdated(HashMap<String, Integer> setLetters) {

	}

	public void setSession(Session session) {
		this.session = session;
	}

	@Override
	public TorrentFilter getFilter() {
		if (filter == null) {
			filter = new TorrentFilter();
		}
		return filter;
	}

	public class TorrentFilter
		extends LetterFilter<TorrentListAdapterItem>
	{
		private long filterMode;

		public void setFilterMode(long filterMode) {
			this.filterMode = filterMode;
			if (session.torrent.getLastListReceivedOn() > 0) {
				refilter();
			}
		}

		@Override
		protected void lettersUpdated(HashMap<String, Integer> mapLetterCount) {
			TorrentListAdapter.this.lettersUpdated(mapLetterCount);
		}

		@Override
		protected FilterResults performFiltering(CharSequence _constraint) {
			synchronized (mLock) {
				isRefreshing = false;
			}

			FilterResults results = new FilterResults();

			if (session == null || !isLifeCycleAtLeast(Lifecycle.State.CREATED)) {
				if (DEBUG) {
					Log.d(TAG,
							"performFiltering skipped: No session? " + (session == null));
				}
				return results;
			}

			LongSparseArray<Map<?, ?>> torrentList = session.torrent.getListAsSparseArray();
			int size = torrentList.size();

			if (DEBUG) {
				Log.d(TAG, "performFiltering: size=" + size + "/filter=" + filterMode);
			}

			if (size > 0 && filterMode > 0) {
				if (DEBUG) {
					Log.d(TAG, "filtering " + torrentList.size());
				}

				if (filterMode >= 0 && filterMode != FILTERBY_ALL) {
					synchronized (mLock) {
						for (int i = size - 1; i >= 0; i--) {
							long key = torrentList.keyAt(i);

							if (!filterCheck(filterMode, key)) {
								torrentList.removeAt(i);
								size--;
							}
						}
					}
				}

				if (DEBUG) {
					Log.d(TAG, "type filtered to " + size);
				}
			}
			int num = torrentList.size();
			ArrayList<TorrentListAdapterItem> keys = new ArrayList<>(num);
			for (int i = 0; i < num; i++) {
				keys.add(new TorrentListAdapterTorrentItem(torrentList.keyAt(i)));
			}

			performLetterFiltering(_constraint, keys);

			doSort(keys, sorter, false);

			results.values = keys;
			results.count = keys.size();

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
					if (results.values instanceof List) {
						setItems((List<TorrentListAdapterItem>) results.values,
								TorrentListAdapter.this);
					}
				}
			}
		}

		@Nullable
		@Override
		protected String getStringToConstrain(TorrentListAdapterItem item) {
			if (item instanceof TorrentListAdapterTorrentItem) {
				Map<?, ?> map = ((TorrentListAdapterTorrentItem) item).getTorrentMap(
						session);
				if (map == null) {
					return null;
				}

				return MapUtils.getMapString(map, TransmissionVars.FIELD_TORRENT_NAME,
						"").toUpperCase(Locale.US);
			} else {
				return null;
			}
		}
	}

	public void refreshDisplayList() {
		if (!session.isReadyForUI()) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "skipped refreshDisplayList. ui not ready");
			}
			return;
		}
		synchronized (mLock) {
			if (isRefreshing) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "skipped refreshDisplayList");
				}
				return;
			}
			isRefreshing = true;
		}
		getFilter().refilter();
	}

	@Thunk
	boolean filterCheck(long filterMode, long torrentID) {
		Map<?, ?> map = session.torrent.getCachedTorrent(torrentID);
		if (map == null) {
			return false;
		}

		if (filterMode > 10) {
			List<?> listTagUIDs = MapUtils.getMapList(map,
					TransmissionVars.FIELD_TORRENT_TAG_UIDS, null);
			return listTagUIDs != null && listTagUIDs.contains(filterMode);
		}

		switch ((int) filterMode) {
			case FILTERBY_ACTIVE:
				long dlRate = MapUtils.getMapLong(map,
						TransmissionVars.FIELD_TORRENT_RATE_DOWNLOAD, -1);
				long ulRate = MapUtils.getMapLong(map,
						TransmissionVars.FIELD_TORRENT_RATE_UPLOAD, -1);
				if (ulRate <= 0 && dlRate <= 0) {
					return false;
				}
				break;

			case FILTERBY_COMPLETE: {
				float pctDone = MapUtils.getMapFloat(map,
						TransmissionVars.FIELD_TORRENT_PERCENT_DONE, 0);
				if (pctDone < 1.0f) {
					return false;
				}
				break;
			}
			case FILTERBY_INCOMPLETE: {
				float pctDone = MapUtils.getMapFloat(map,
						TransmissionVars.FIELD_TORRENT_PERCENT_DONE, 0);
				if (pctDone >= 1.0f) {
					return false;
				}
				break;
			}
			case FILTERBY_STOPPED: {
				int status = MapUtils.getMapInt(map,
						TransmissionVars.FIELD_TORRENT_STATUS,
						TransmissionVars.TR_STATUS_STOPPED);
				if (status != TransmissionVars.TR_STATUS_STOPPED) {
					return false;
				}
				break;
			}
		}
		return true;
	}

	public void setSortDefinition(SortDefinition sortDefinition, boolean isAsc) {
		synchronized (mLock) {
			sorter.setSortFields(sortDefinition);
			sorter.setAsc(isAsc);
			if (!sortDefinition.equals(sorter.getSortDefinition())) {
				mapGroupIDCollapsed.clear();
			}
		}
		getFilter().refilter();
	}

	public SortDefinition getSortDefinition() {
		return sorter.getSortDefinition();
	}

	public Map<?, ?> getTorrentItem(int position) {
		if (session == null) {
			return new HashMap<>();
		}
		TorrentListAdapterItem item = getItem(position);
		if (!(item instanceof TorrentListAdapterTorrentItem)) {
			return new HashMap<>();
		}
		return ((TorrentListAdapterTorrentItem) item).getTorrentMap(session);
	}

	public long getTorrentID(int position) {
		TorrentListAdapterItem item = getItem(position);
		if (item instanceof TorrentListAdapterTorrentItem) {
			return ((TorrentListAdapterTorrentItem) item).torrentID;
		}
		if (item instanceof TorrentListAdapterHeaderItem) {
			int id = ((TorrentListAdapterHeaderItem) item).id.hashCode();
			return id == 0 ? Integer.MIN_VALUE : id < 0 ? id : -id;
		}
		return -1;
	}

	@Override
	public int getItemViewType(int position) {
		return getItem(position) instanceof TorrentListAdapterHeaderItem
				? VIEWTYPE_HEADER : VIEWTYPE_TORRENT;
	}

	@SuppressWarnings("unchecked")
	@Override
	public FlexibleRecyclerViewHolder onCreateFlexibleViewHolder(ViewGroup parent,
			int viewType) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);
		@LayoutRes
		int resourceID;

		boolean isHeader = viewType == VIEWTYPE_HEADER;
		if (isHeader) {
			resourceID = AndroidUtils.usesNavigationControl()
					? R.layout.row_torrent_list_header_dpad
					: R.layout.row_torrent_list_header;
			View rowView = inflater.inflate(resourceID, parent, false);
			TorrentListViewHeaderHolder holder = new TorrentListViewHeaderHolder(this,
					this, rowView);
			rowView.setTag(holder);
			return holder;
		} else {
			resourceID = smallView ? R.layout.row_torrent_list_small
					: R.layout.row_torrent_list;
			View rowView = inflater.inflate(resourceID, parent, false);
			TorrentListViewHolder holder = new TorrentListViewHolder(this, rowView,
					smallView);
			rowView.setTag(holder);
			return holder;
		}

	}

	@Override
	public void onBindFlexibleViewHolder(FlexibleRecyclerViewHolder holder,
			int position) {
		if (holder instanceof TorrentListViewHolder) {
			Map<?, ?> item = getTorrentItem(position);
			torrentListRowFiller.fillHolder((TorrentListViewHolder) holder, item,
					session);
		} else if (holder instanceof TorrentListViewHeaderHolder) {
			TorrentListAdapterItem item = getItem(position);
			if (item instanceof TorrentListAdapterHeaderItem) {
				((TorrentListViewHeaderHolder) holder).bind(
						(TorrentListAdapterHeaderItem) item);
			}
		}
	}

	@Override
	public long getItemId(int position) {
		return getTorrentID(position);
	}

	@Override
	protected void onItemListChanging(List<TorrentListAdapterItem> items,
			SparseIntArray countsFillMe) {
		if (sorter == null || !sorter.supportsGrouping()) {
			return;
		}

		if (items.size() < sorter.getMinCountBeforeGrouping()) {
			return;
		}
		List<Comparable> groupIDs = new ArrayList<>();
		List<String> groupNames = new ArrayList<>();
		List<Integer> groupStartPositions = new ArrayList<>();
		List<Integer> groupCounts = new ArrayList<>();

		ListIterator<TorrentListAdapterItem> iterator = items.listIterator();

		boolean isAsc = sorter.isAsc();
		Comparable lastID = null;
		int pos = -1;
		int numInGroup = 0;
		boolean collapsed = false;
		int countHeaders = 0;
		int countItems = items.size();
		while (iterator.hasNext()) {
			TorrentListAdapterItem item = iterator.next();
			if (!(item instanceof TorrentListAdapterTorrentItem)) {
				countItems--;
				iterator.remove();
				continue;
			}
			if (!collapsed) {
				pos++;
			}
			Comparable id = sorter.getGroupID(item, isAsc, items);
			if (id == null) {
				continue;
			}
			if (lastID == null) {
				// first item
				groupIDs.add(id);
				Boolean isCollapsed = mapGroupIDCollapsed.get(id);
				collapsed = isCollapsed != null && isCollapsed;
				groupNames.add(sorter.getGroupName(id, isAsc));
				groupStartPositions.add(pos);
				pos++;
				numInGroup = 1;
			} else if (!id.equals(lastID)) {
				groupIDs.add(id);
				Boolean isCollapsed = mapGroupIDCollapsed.get(id);
				collapsed = isCollapsed != null && isCollapsed;
				groupNames.add(sorter.getGroupName(id, isAsc));
				groupStartPositions.add(pos);
				pos++;
				groupCounts.add(numInGroup);
				numInGroup = 1;
			} else {
				numInGroup++;
			}

			lastID = id;
		}
		groupCounts.add(numInGroup);

		//		for (int i = groupNames.size() - 1; i >= 0; i--) {
		for (int i = 0, size = groupNames.size(); i < size; i++) {
			int position = groupStartPositions.get(i);
			numInGroup = groupCounts.get(i);
			Comparable groupID = groupIDs.get(i);
			items.add(position, new TorrentListAdapterHeaderItem(groupID,
					groupNames.get(i), numInGroup));
			countHeaders++;
			Boolean isCollapsed = mapGroupIDCollapsed.get(groupID);
			if (isCollapsed != null && isCollapsed) {
				for (int j = 0; j < numInGroup; j++) {
					items.remove(position + 1);
				}
				countItems -= numInGroup;
			}
		}

		countsFillMe.put(VIEWTYPE_HEADER, countHeaders);
		countsFillMe.put(VIEWTYPE_TORRENT, countItems);

		this.groupIDs = groupIDs;
		this.sections = groupNames.toArray(new String[groupNames.size()]);
		this.sectionStarts = groupStartPositions;
	}

	@Override
	public Object[] getSections() {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG,
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

	public void flipHeaderCollapse(int adapterPosition) {
		TorrentListAdapterItem item = getItem(adapterPosition);
		if (item instanceof TorrentListAdapterHeaderItem) {
			TorrentListAdapterHeaderItem headerItem = (TorrentListAdapterHeaderItem) item;
			Comparable comparableGroup = headerItem.id;
			boolean nowCollapsed = !isGroupCollapsed(comparableGroup);
			mapGroupIDCollapsed.put(comparableGroup, nowCollapsed);
			if (nowCollapsed) {
				int count = 0;
				TorrentListAdapterItem nextItem = getItem(adapterPosition + count + 1);
				while (nextItem != null
						&& !(nextItem instanceof TorrentListAdapterHeaderItem)) {
					count++;
					nextItem = getItem(adapterPosition + count + 1);
				}
				if (adapterPosition + count > getItemCount()) {
					count = getItemCount() - adapterPosition;
				}
				removeItems(adapterPosition + 1, count);
			} else {
				refreshDisplayList();
			}
			RecyclerView.ViewHolder holder = getRecyclerView().findViewHolderForAdapterPosition(
					adapterPosition);
			if (holder instanceof TorrentListViewHeaderHolder) {
				((TorrentListViewHeaderHolder) holder).updateCollapseState(
						nowCollapsed);
			}
		} // else we could walk up the item tree until we hit a header item
	}

	public boolean isGroupCollapsed(Comparable groupID) {
		Boolean isCollapsed = mapGroupIDCollapsed.get(groupID);
		return isCollapsed == null ? false : isCollapsed;
	}

	public boolean showGroupCount(Comparable id) {
		return sorter.showGroupCount();
	}

	@Override
	public boolean isItemCheckable(int position) {
		return getItem(position) instanceof TorrentListAdapterTorrentItem;
	}
}