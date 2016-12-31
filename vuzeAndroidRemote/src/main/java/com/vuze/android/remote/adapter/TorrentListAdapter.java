/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 * <p/>
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

package com.vuze.android.remote.adapter;

import java.util.*;

import com.vuze.android.FlexibleRecyclerAdapter;
import com.vuze.android.FlexibleRecyclerSelectionListener;
import com.vuze.android.remote.*;
import com.vuze.android.remote.session.Session;
import com.vuze.android.util.TextViewFlipper.FlipValidator;
import com.vuze.util.ComparatorMapFields;
import com.vuze.util.MapUtils;
import com.vuze.util.Thunk;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.util.LongSparseArray;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filterable;

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
	extends FlexibleRecyclerAdapter<TorrentListViewHolder, Long>
	implements Filterable
{
	public final static int FILTERBY_ALL = 8;

	public final static int FILTERBY_ACTIVE = 4;

	public final static int FILTERBY_COMPLETE = 9;

	public final static int FILTERBY_INCOMPLETE = 1;

	public final static int FILTERBY_STOPPED = 2;

	@Thunk
	static final boolean DEBUG = AndroidUtils.DEBUG_ADAPTER;

	private static final String TAG = "TorrentListAdapter";

	@Thunk
	ComparatorMapFields sorter;

	private int viewType;

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

	public TorrentListAdapter(Context context,
			FlexibleRecyclerSelectionListener selector) {
		super(selector);
		this.context = context;

		torrentListRowFiller = new TorrentListRowFiller(context);

		sorter = new ComparatorMapFields() {

			public Throwable lastError;

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
				VuzeEasyTracker.getInstance(TorrentListAdapter.this.context).logError(
						t);
				return 0;
			}

			@Override
			public Map<?, ?> mapGetter(Object o) {
				return session.torrent.getCachedTorrent((Long) o);
			}

			@SuppressWarnings("rawtypes")
			@Override
			public Comparable modifyField(String fieldID, Map map, Comparable o) {
				if (fieldID.equals(TransmissionVars.FIELD_TORRENT_POSITION)) {
					return (MapUtils.getMapLong(map,
							TransmissionVars.FIELD_TORRENT_LEFT_UNTIL_DONE, 1) == 0
									? 0x1000000000000000L : 0)
							+ ((Number) o).longValue();
				}
				if (fieldID.equals(TransmissionVars.FIELD_TORRENT_ETA)) {
					if (((Number) o).longValue() < 0) {
						o = Long.MAX_VALUE;
					}
				}
				return o;
			}
		};
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
		extends LetterFilter<Long>
	{
		private long filterMode;

		public void setFilterMode(long filterMode) {
			this.filterMode = filterMode;
			refilter();
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

			if (session == null) {
				if (DEBUG) {
					Log.d(TAG, "performFiltering skipped: No session");
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
			ArrayList<Long> keys = new ArrayList<>(num);
			for (int i = 0; i < num; i++) {
				keys.add(torrentList.keyAt(i));
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
						setItems((List<Long>) results.values);
					}
				}
			}
		}

		@Nullable
		@Override
		protected String getStringToConstrain(Long torrentID) {
			Map<?, ?> map = session.torrent.getCachedTorrent(torrentID);
			if (map == null) {
				return null;
			}

			return MapUtils.getMapString(map, TransmissionVars.FIELD_TORRENT_NAME,
					"").toUpperCase(Locale.US);
		}
	}

	public void refreshDisplayList() {
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

	public void setSort(String[] fieldIDs, Boolean[] sortOrderAsc) {
		synchronized (mLock) {
			Boolean[] order;
			if (sortOrderAsc == null) {
				order = new Boolean[fieldIDs.length];
				Arrays.fill(order, Boolean.FALSE);
			} else if (sortOrderAsc.length != fieldIDs.length) {
				order = new Boolean[fieldIDs.length];
				Arrays.fill(order, Boolean.FALSE);
				System.arraycopy(sortOrderAsc, 0, order, 0, sortOrderAsc.length);
			} else {
				order = sortOrderAsc;
			}
			sorter.setSortFields(fieldIDs, order);
		}
		doSort();
	}

	public void setSort(Comparator<? super Map<?, ?>> comparator) {
		synchronized (mLock) {
			sorter.setComparator(comparator);
		}
		doSort();
	}

	private void doSort() {
		if (session == null) {
			if (DEBUG) {
				Log.d(TAG, "doSort skipped: No session");
			}
			return;
		}
		if (!sorter.isValid()) {
			if (DEBUG) {
				Log.d(TAG, "doSort skipped: no comparator and no sort");
			}
			return;
		}
		if (DEBUG) {
			Log.d(TAG, "sort: " + sorter.toDebugString());
		}

		sortItems(sorter);
	}

	public Map<?, ?> getTorrentItem(int position) {
		if (session == null) {
			return new HashMap<Object, Object>();
		}
		Long item = getItem(position);
		if (item == null) {
			return new HashMap<Object, Object>();
		}
		return session.torrent.getCachedTorrent(item);
	}

	public long getTorrentID(int position) {
		Long torrentID = getItem(position);
		return torrentID == null ? -1 : torrentID;
	}

	public void setViewType(int viewType) {
		this.viewType = viewType;
		notifyDataSetInvalidated();
	}

	@Override
	public int getItemViewType(int position) {
		return viewType;
	}

	@SuppressWarnings("unchecked")
	@Override
	public TorrentListViewHolder onCreateFlexibleViewHolder(ViewGroup parent,
			int viewType) {
		boolean isSmall = viewType == 1;
		int resourceID = isSmall ? R.layout.row_torrent_list_small
				: R.layout.row_torrent_list;
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);

		View rowView = inflater.inflate(resourceID, parent, false);
		TorrentListViewHolder holder = new TorrentListViewHolder(this, rowView,
				isSmall);

		rowView.setTag(holder);
		return holder;
	}

	@Override
	public void onBindFlexibleViewHolder(TorrentListViewHolder holder,
			int position) {
		Map<?, ?> item = getTorrentItem(position);
		torrentListRowFiller.fillHolder(holder, item, session);
	}

	@Override
	public long getItemId(int position) {
		return getTorrentID(position) << viewType;
	}
}