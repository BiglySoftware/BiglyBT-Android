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
 * Filter for MetaSearch Results Adapter
 * <p/>
 * Created by TuxPaper on 6/30/16.
 */
public class MetaSearchResultsAdapterFilter
	extends LetterFilter<String>
{
	private static final String TAG = "MetaSearchResultFilter";

	private static final boolean DEBUG = AndroidUtils.DEBUG;

	private final MetaSearchResultsAdapter.MetaSearchSelectionListener rs;

	private final Object mLock;

	private final AdapterFilterTalkbalk adapterFilterTalkbalk;

	private List<String> engineIDs;

	private long sizeStart = -1;

	private long sizeEnd = -1;

	private long dateStart = -1;

	private long dateEnd = -1;

	private boolean filterOnlyUnseen;

	public MetaSearchResultsAdapterFilter(
			AdapterFilterTalkbalk adapterFilterTalkbalk,
			MetaSearchResultsAdapter.MetaSearchSelectionListener rs, Object mLock) {

		this.adapterFilterTalkbalk = adapterFilterTalkbalk;
		this.rs = rs;
		this.mLock = mLock;
	}

	private boolean filterCheck(List<String> engines, String key,
			boolean hasEngines) {
		Map<?, ?> map = rs.getSearchResultMap(key);
		if (map == null) {
			return false;
		}

		if (filterOnlyUnseen && MapUtils.getMapBoolean(map,
				TransmissionVars.FIELD_SUBSCRIPTION_RESULT_ISREAD, true)) {
			return false;
		}

		if (hasEngines) {
			String engineID = MapUtils.getMapString(map,
					TransmissionVars.FIELD_SEARCHRESULT_ENGINE_ID, null);
			//Log.d(TAG, "filterCheck: engineID=" + engineID + "/" + engines + " for"
			//		+ " " + MapUtils.getMapString(map, "n", "??"));

			boolean engineMatches = engineID == null || engines.contains(engineID);

			if (!engineMatches) {
				List others = MapUtils.getMapList(map, "others", null);
				if (others != null) {
					for (Object other : others) {
						if (other instanceof Map) {
							engineID = MapUtils.getMapString((Map) other,
									TransmissionVars.FIELD_SEARCHRESULT_ENGINE_ID, null);
							engineMatches = engineID == null || engines.contains(engineID);
							if (engineMatches) {
								break;
							}
						}
					}
				}
				if (!engineMatches) {
					return false;
				}
			}
		}

		if (sizeStart > 0 || sizeEnd > 0) {
			long size = MapUtils.getMapLong(map,
					TransmissionVars.FIELD_SEARCHRESULT_SIZE, -1);
			boolean withinRange = size >= sizeStart
					&& (sizeEnd < 0 || size <= sizeEnd);
			if (!withinRange) {
				return false;
			}
		}
		if (dateStart > 0 || dateEnd > 0) {
			long date = MapUtils.getMapLong(map,
					TransmissionVars.FIELD_SEARCHRESULT_PUBLISHDATE, -1);
			boolean withinRange = date >= dateStart
					&& (dateEnd < 0 || date <= dateEnd);
			if (!withinRange) {
				return false;
			}
		}
		return true;
	}

	public boolean isFilterOnlyUnseen() {
		return filterOnlyUnseen;
	}

	@Override
	protected FilterResults performFiltering(CharSequence _constraint) {

		FilterResults results = new FilterResults();

		final List<String> searchResultList = rs.getSearchResultList();
		int size = searchResultList.size();

		synchronized (mLock) {

			boolean hasEngines = engineIDs != null && engineIDs.size() > 0;

			if (hasEngines || dateStart > 0 || dateEnd > 0 || sizeStart > 0
					|| sizeEnd > 0 || filterOnlyUnseen) {
				if (DEBUG) {
					Log.d(TAG, "filtering " + searchResultList.size());
				}

				for (int i = size - 1; i >= 0; i--) {
					String key = searchResultList.get(i);

					if (!filterCheck(engineIDs, key, hasEngines)) {
						searchResultList.remove(i);
						size--;
					}
				}

				if (DEBUG) {
					Log.d(TAG, "type filtered to " + size);
				}

			}

			performLetterFiltering(_constraint, searchResultList);
		}

		adapterFilterTalkbalk.doSort(searchResultList, false);

		results.values = searchResultList;
		results.count = searchResultList.size();

		return results;
	}

	@Override
	protected void lettersUpdated(HashMap<String, Integer> mapLetterCount) {
		adapterFilterTalkbalk.lettersUpdated(mapLetterCount);
	}

	@SuppressWarnings("unchecked")
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

	public void setEngines(List<String> engines) {
		if (engines.size() == 1 && engines.get(0).length() == 0) {
			this.engineIDs = null;
		} else {
			this.engineIDs = engines;
		}
	}

	public void setFilterOnlyUnseen(boolean filterOnlyUnseen) {
		this.filterOnlyUnseen = filterOnlyUnseen;
	}

	public void setFilterSizes(long start, long end) {
		this.sizeStart = start;
		this.sizeEnd = end;
	}

	public void setFilterTimes(long start, long end) {
		this.dateStart = start;
		this.dateEnd = end;
	}

	@Override
	protected String getStringToConstrain(String key) {
		Map<?, ?> map = rs.getSearchResultMap(key);
		if (map == null) {
			return null;
		}

		return MapUtils.getMapString(map, TransmissionVars.FIELD_SEARCHRESULT_NAME,
				"").toUpperCase(Locale.US);
	}

	public boolean hasPublishTimeFilter() {
		return this.dateStart > 0 || this.dateEnd > 0;
	}

	public boolean hasSizeFilter() {
		return sizeStart >= 0 || sizeEnd > 0;
	}

	public long[] getFilterTimes() {
		return new long[] {
			dateStart,
			dateEnd
		};
	}

	public long[] getFilterSizes() {
		return new long[] {
			sizeStart,
			sizeEnd
		};
	}

	public void restoreFromBundle(Bundle savedInstanceState) {
		if (savedInstanceState == null) {
			return;
		}

		String prefix = getClass().getName();
		sizeStart = savedInstanceState.getLong(prefix + ":sizeStart", sizeStart);
		sizeEnd = savedInstanceState.getLong(prefix + ":sizeEnd", sizeEnd);
		dateEnd = savedInstanceState.getLong(prefix + ":dateEnd", dateEnd);
		dateStart = savedInstanceState.getLong(prefix + ":dateStart", dateStart);
		refilter();
	}

	public void saveToBundle(Bundle outBundle) {
		String prefix = getClass().getName();
		outBundle.putLong(prefix + ":sizeStart", sizeStart);
		outBundle.putLong(prefix + ":sizeEnd", sizeEnd);
		outBundle.putLong(prefix + ":dateEnd", dateEnd);
		outBundle.putLong(prefix + ":dateStart", dateStart);
	}
}
