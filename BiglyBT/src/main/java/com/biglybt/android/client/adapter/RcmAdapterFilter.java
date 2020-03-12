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

import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;

import com.biglybt.android.adapter.*;
import com.biglybt.android.client.*;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.util.MapUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Created by TuxPaper on 7/4/16.
 */
public class RcmAdapterFilter
	extends LetterFilter<String>
{
	private static final boolean DEBUG = AndroidUtils.DEBUG_ADAPTER;

	private static final String TAG = "RcmAdapterFilter";

	public static final String KEY_LAST_SEEN_START = TAG + ":lastSeenStart";

	public static final String KEY_LAST_SEEN_END = TAG + ":lastSeenEnd";

	public static final String KEY_PUBLISH_DATE_START = TAG + ":publishDateStart";

	public static final String KEY_PUBLISH_DATE_END = TAG + ":publishDateEnd";

	private static final String ID_SORT_FILTER = "-rcm";

	private static final String KEY_MIN_RANK = TAG + ":minRank";

	private static final String KEY_MIN_SEEDS = TAG + ":minSeeds";

	private static final String KEY_SIZE_START = TAG + ":sizeStart";

	private static final String KEY_SIZE_END = TAG + ":sizeEnd";

	private final SessionAdapterFilterTalkback adapterFilterTalkbalk;

	private final Object mLock;

	private final RcmAdapter.RcmSelectionListener rs;

	private long sizeStart = -1;

	private long sizeEnd = -1;

	private long publishdateStart = -1;

	private long publishdateEnd = -1;

	private long lastSeenStart = -1;

	private long lastSeenEnd = -1;

	private int minSeeds = -1;

	private int minRank = -1;

	private int defaultSortID;

	RcmAdapterFilter(SessionAdapterFilterTalkback adapterFilterTalkbalk,
			RcmAdapter.RcmSelectionListener rs, Object mLock) {
		super(adapterFilterTalkbalk);
		this.adapterFilterTalkbalk = adapterFilterTalkbalk;
		this.rs = rs;
		this.mLock = mLock;

		StoredSortByInfo sortByInfo = adapterFilterTalkbalk.getSession().getRemoteProfile().getSortByInfo(
				ID_SORT_FILTER);
		SortDefinition sortDefinition = SortDefinition.findSortDefinition(
				sortByInfo, getSortDefinitions(), defaultSortID);
		boolean isAsc = sortByInfo == null ? sortDefinition.isSortAsc()
				: sortByInfo.isAsc;

		ComparatorMapFields<String> sorter = new RcmAdapterSorter(rs,
				sortDefinition, isAsc);
		setSorter(sorter);
	}

	@Override
	protected String getStringToConstrain(String key) {
		Map<?, ?> map = rs.getSearchResultMap(key);
		if (map == null) {
			return null;
		}

		return MapUtils.getMapString(map, "title", "");
	}

	@Override
	public @NonNull String getSectionName(int position) {
		return "";
	}

	@Override
	protected FilterResults performFiltering2(CharSequence constraint) {
		FilterResults results = new FilterResults();

		final List<String> searchResultList = rs.getSearchResultList();
		int size = searchResultList.size();

		synchronized (mLock) {

			if (publishdateStart > 0 || publishdateEnd > 0 || sizeStart > 0
					|| sizeEnd > 0 || lastSeenEnd > 0 || lastSeenStart > 0 || minRank > 0
					|| minSeeds >= 0) {
				if (DEBUG) {
					Log.d(TAG, "filtering " + searchResultList.size());
				}

				HashSet<String> toRemove = new HashSet<>();
				for (int i = size - 1; i >= 0; i--) {
					String key = searchResultList.get(i);

					if (!filterCheck(key)) {
						toRemove.add(key);
					}
				}

				if (toRemove.size() > 0) {
					size -= toRemove.size();
					searchResultList.removeAll(toRemove);
				}

				if (DEBUG) {
					Log.d(TAG, "type filtered to " + size);
				}

			}

			performLetterFiltering(constraint, searchResultList);
		}

		doSort(searchResultList);

		results.values = searchResultList;
		results.count = searchResultList.size();

		return results;
	}

	@SuppressWarnings("RedundantIfStatement")
	private boolean filterCheck(String key) {
		Map<?, ?> map = rs.getSearchResultMap(key);
		if (map == null) {
			return false;
		}
		if (sizeStart > 0 || sizeEnd > 0) {
			long size = MapUtils.getMapLong(map, TransmissionVars.FIELD_RCM_SIZE, -1);
			boolean withinRange = size >= sizeStart
					&& (sizeEnd < 0 || size <= sizeEnd);
			if (!withinRange) {
				return false;
			}
		}
		if (publishdateStart > 0 || publishdateEnd > 0) {
			long date = MapUtils.getMapLong(map,
					TransmissionVars.FIELD_RCM_PUBLISHDATE, -1);
			boolean withinRange = date >= publishdateStart
					&& (publishdateEnd < 0 || date <= publishdateEnd);
			if (!withinRange) {
				return false;
			}
		}
		if (lastSeenStart > 0 || lastSeenEnd > 0) {
			long date = MapUtils.getMapLong(map, TransmissionVars.FIELD_RCM_CHANGEDON,
					-1);
			boolean withinRange = date >= lastSeenStart
					&& (lastSeenEnd < 0 || date <= lastSeenEnd);
			if (!withinRange) {
				return false;
			}
		}
		if (minSeeds > 0) {
			int val = MapUtils.getMapInt(map, TransmissionVars.FIELD_RCM_SEEDS, -1);
			if (val > 0 && val < minSeeds) {
				return false;
			}
		}
		if (minRank > 0) {
			int val = MapUtils.getMapInt(map, TransmissionVars.FIELD_RCM_RANK, -1);
			if (val > 0 && val < minRank) {
				return false;
			}
		}
		return true;
	}

	@Override
	protected boolean publishResults2(CharSequence constraint,
			FilterResults results) {
		// Now we have to inform the adapter about the new list filtered
		if (results.count == 0) {
			adapterFilterTalkbalk.removeAllItems();
		} else {
			synchronized (mLock) {
				if (results.values instanceof List) {
					//noinspection unchecked
					return adapterFilterTalkbalk.setItems((List<String>) results.values,
							null);
				}
			}
		}
		return true;
	}

	public void setFilterSizes(long start, long end) {
		this.sizeStart = start;
		this.sizeEnd = end;
	}

	public void setFilterPublishTimes(long start, long end) {
		this.publishdateStart = start;
		this.publishdateEnd = end;
	}

	public void setFilterLastSeenTimes(long start, long end) {
		this.lastSeenStart = start;
		this.lastSeenEnd = end;
	}

	public void setFilterMinSeeds(int val) {
		this.minSeeds = val;
	}

	public void setFilterMinRank(int val) {
		this.minRank = val;
	}

	public boolean hasPublishTimeFilter() {
		return this.publishdateStart > 0 || this.publishdateEnd > 0;
	}

	public boolean hasSizeFilter() {
		return sizeStart >= 0 || sizeEnd > 0;
	}

	public boolean hasLastSeenFilter() {
		return lastSeenStart > 0 || lastSeenEnd > 0;
	}

	public boolean hasMinSeedsFilter() {
		return minSeeds > 0;
	}

	public boolean hasMinRankFilter() {
		return minRank > 0;
	}

	@Override
	public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);

		minRank = savedInstanceState.getInt(KEY_MIN_RANK, minRank);
		minSeeds = savedInstanceState.getInt(KEY_MIN_SEEDS, minSeeds);
		sizeStart = savedInstanceState.getLong(KEY_SIZE_START, sizeStart);
		sizeEnd = savedInstanceState.getLong(KEY_SIZE_END, sizeEnd);
		publishdateEnd = savedInstanceState.getLong(KEY_PUBLISH_DATE_END,
				publishdateEnd);
		publishdateStart = savedInstanceState.getLong(KEY_PUBLISH_DATE_START,
				publishdateStart);
		lastSeenEnd = savedInstanceState.getLong(KEY_LAST_SEEN_END, lastSeenEnd);
		lastSeenStart = savedInstanceState.getLong(KEY_LAST_SEEN_START,
				lastSeenStart);
		refilter(false);
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outBundle) {
		super.onSaveInstanceState(outBundle);

		outBundle.putInt(KEY_MIN_RANK, minRank);
		outBundle.putInt(KEY_MIN_SEEDS, minSeeds);
		outBundle.putLong(KEY_SIZE_START, sizeStart);
		outBundle.putLong(KEY_SIZE_END, sizeEnd);
		outBundle.putLong(KEY_PUBLISH_DATE_END, publishdateEnd);
		outBundle.putLong(KEY_PUBLISH_DATE_START, publishdateStart);
		outBundle.putLong(KEY_LAST_SEEN_END, lastSeenEnd);
		outBundle.putLong(KEY_LAST_SEEN_START, lastSeenStart);
	}

	@NonNull
	public long[] getFilterSizes() {
		return new long[] {
			sizeStart,
			sizeEnd
		};
	}

	@NonNull
	public long[] getFilterPublishTimes() {
		return new long[] {
			publishdateStart,
			publishdateEnd
		};
	}

	@NonNull
	public long[] getFilterLastSeenTimes() {
		return new long[] {
			lastSeenStart,
			lastSeenEnd
		};
	}

	public int getFilterMinSeeds() {
		return minSeeds;
	}

	public int getFilterMinRank() {
		return minRank;
	}

	public void clearFilter() {
		setFilterMinSeeds(0);
		setFilterMinRank(0);
		setFilterLastSeenTimes(0, -1);
		setFilterPublishTimes(0, -1);
		setFilterSizes(-1, -1);
	}

	@Override
	public boolean showLetterUI() {
		return rs.getSearchResultList().size() > 3;
	}

	@NonNull
	@Override
	public SparseArray<SortDefinition> createSortDefinitions() {
		String[] sortNames = BiglyBTApp.getContext().getResources().getStringArray(
				R.array.sortby_rcm_list);

		SparseArray<SortDefinition> sortDefinitions = new SparseArray<>(
				sortNames.length);
		int i = 0;

		//<item>Rank</item>
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_RCM_RANK
		}, SortDefinition.SORT_DESC));

		i++; // <item>Name</item>
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_RCM_NAME
		}, new Boolean[] {
			true
		}, true, SortDefinition.SORT_ASC));
		defaultSortID = i;

		i++; // <item>Seeds</item>
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_RCM_SEEDS,
			TransmissionVars.FIELD_RCM_PEERS
		}, SortDefinition.SORT_DESC));

		i++; // <item>size</item>
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_RCM_SIZE
		}, SortDefinition.SORT_DESC));

		i++; // <item>PublishDate</item>
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_RCM_PUBLISHDATE
		}, SortDefinition.SORT_DESC));

		i++; // <item>Last Seen</item>
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_RCM_LAST_SEEN_SECS
		}, SortDefinition.SORT_DESC));
		return sortDefinitions;
	}

	@Override
	protected void saveSortDefinition(SortDefinition sortDefinition,
			boolean isAsc) {
		Session session = adapterFilterTalkbalk.getSession();
		if (session.getRemoteProfile().setSortBy(ID_SORT_FILTER, sortDefinition,
				isAsc)) {
			session.saveProfile();
		}
	}

}
