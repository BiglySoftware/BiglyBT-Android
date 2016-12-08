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

package com.vuze.android.remote.adapter;

import java.util.*;

import com.vuze.android.remote.AndroidUtils;
import com.vuze.android.remote.TransmissionVars;
import com.vuze.util.MapUtils;

import android.os.Bundle;
import android.util.Log;

/**
 * Created by TuxPaper on 7/4/16.
 */
public class RcmAdapterFilter
	extends LetterFilter<String>
{
	private static final boolean DEBUG = AndroidUtils.DEBUG_ADAPTER;

	private static final String TAG = "RcmAdapterFilter";

	private final AdapterFilterTalkbalk adapterFilterTalkbalk;

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

	public RcmAdapterFilter(AdapterFilterTalkbalk adapterFilterTalkbalk,
			RcmAdapter.RcmSelectionListener rs, Object mLock) {
		this.adapterFilterTalkbalk = adapterFilterTalkbalk;
		this.rs = rs;
		this.mLock = mLock;
	}

	@Override
	protected String getStringToConstrain(String key) {
		Map<?, ?> map = rs.getSearchResultMap(key);
		if (map == null) {
			return null;
		}

		return MapUtils.getMapString(map, "title", "").toUpperCase(Locale.US);
	}

	@Override
	protected void lettersUpdated(HashMap<String, Integer> mapLetterCount) {
		adapterFilterTalkbalk.lettersUpdated(mapLetterCount);
	}

	@Override
	protected FilterResults performFiltering(CharSequence constraint) {
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

				for (int i = size - 1; i >= 0; i--) {
					String key = searchResultList.get(i);

					if (!filterCheck(key)) {
						searchResultList.remove(i);
						size--;
					}
				}

				if (DEBUG) {
					Log.d(TAG, "type filtered to " + size);
				}

			}

			performLetterFiltering(constraint, searchResultList);
		}

		adapterFilterTalkbalk.doSort(searchResultList, false);

		results.values = searchResultList;
		results.count = searchResultList.size();

		return results;
	}

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
	protected void publishResults(CharSequence constraint,
			FilterResults results) {
		// Now we have to inform the adapter about the new list filtered
		if (results.count == 0) {
			adapterFilterTalkbalk.removeAllItems();
		} else {
			synchronized (mLock) {
				if (results.values instanceof List) {
					adapterFilterTalkbalk.setItems((List<String>) results.values);
				}
			}
		}
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

	public void restoreFromBundle(Bundle savedInstanceState) {
		if (savedInstanceState == null) {
			return;
		}

		String prefix = getClass().getName();
		minRank = savedInstanceState.getInt(prefix + ":minRank", minRank);
		minSeeds = savedInstanceState.getInt(prefix + ":minSeeds", minSeeds);
		sizeStart = savedInstanceState.getLong(prefix + ":sizeStart", sizeStart);
		sizeEnd = savedInstanceState.getLong(prefix + ":sizeEnd", sizeEnd);
		publishdateEnd = savedInstanceState.getLong(prefix + ":publishDateEnd",
				publishdateEnd);
		publishdateStart = savedInstanceState.getLong(prefix + ":publishDateStart",
				publishdateStart);
		lastSeenEnd = savedInstanceState.getLong(prefix + ":lastSeenEnd",
				lastSeenEnd);
		lastSeenStart = savedInstanceState.getLong(prefix + ":lastSeenStart",
				lastSeenStart);
		refilter();
	}

	public void saveToBundle(Bundle outBundle) {
		String prefix = getClass().getName();
		outBundle.putInt(prefix + ":minRank", minRank);
		outBundle.putInt(prefix + ":minSeeds", minSeeds);
		outBundle.putLong(prefix + ":sizeStart", sizeStart);
		outBundle.putLong(prefix + ":sizeEnd", sizeEnd);
		outBundle.putLong(prefix + ":publishDateEnd", publishdateEnd);
		outBundle.putLong(prefix + ":publishDateStart", publishdateStart);
		outBundle.putLong(prefix + ":lastSeenEnd", lastSeenEnd);
		outBundle.putLong(prefix + ":lastSeenStart", lastSeenStart);
	}

	public long[] getFilterSizes() {
		return new long[] {
			sizeStart,
			sizeEnd
		};
	}

	public long[] getFilterPublishTimes() {
		return new long[] {
			publishdateStart,
			publishdateEnd
		};
	}

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
}
