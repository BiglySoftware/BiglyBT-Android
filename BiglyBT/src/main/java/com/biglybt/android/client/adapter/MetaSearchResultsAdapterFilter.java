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

import java.util.List;
import java.util.Map;

import com.biglybt.android.adapter.*;
import com.biglybt.android.client.*;
import com.biglybt.android.client.activity.MetaSearchActivity;
import com.biglybt.android.client.activity.SubscriptionResultsActivity;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.util.MapUtils;

import android.os.Bundle;
import androidx.annotation.NonNull;
import android.util.Log;
import android.util.SparseArray;

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

	private final String ID_SORT_FILTER;

	private final MetaSearchResultsAdapter.MetaSearchSelectionListener rs;

	private final Object mLock;

	private final SessionAdapterFilterTalkback<String> adapterFilterTalkbalk;

	private List<String> engineIDs;

	private long sizeStart = -1;

	private long sizeEnd = -1;

	private long dateStart = -1;

	private long dateEnd = -1;

	private boolean filterOnlyUnseen;

	private int defaultSortID;

	MetaSearchResultsAdapterFilter(String ID_SORT_FILTER,
			SessionAdapterFilterTalkback<String> adapterFilterTalkbalk,
			MetaSearchResultsAdapter.MetaSearchSelectionListener rs, Object mLock) {
		super(adapterFilterTalkbalk);
		this.ID_SORT_FILTER = ID_SORT_FILTER;
		this.adapterFilterTalkbalk = adapterFilterTalkbalk;
		this.rs = rs;
		this.mLock = mLock;

		StoredSortByInfo sortByInfo = rs.getSession().getRemoteProfile().getSortByInfo(
				ID_SORT_FILTER);
		SortDefinition sortDefinition = SortDefinition.findSortDefinition(
				sortByInfo, getSortDefinitions(), defaultSortID);
		boolean isAsc = sortByInfo == null ? sortDefinition.isSortAsc()
				: sortByInfo.isAsc;

		ComparatorMapFields<String> sorter = new MetaSearchResultsSorter(rs,
				sortDefinition, isAsc);

		setSorter(sorter);
	}

	@SuppressWarnings("RedundantIfStatement")
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
	protected FilterResults performFiltering2(CharSequence _constraint) {

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

		doSort(searchResultList);

		results.values = searchResultList;
		results.count = searchResultList.size();

		return results;
	}

	@NonNull
	@Override
	public String getSectionName(int position) {
		return "";
	}

	@SuppressWarnings("unchecked")
	@Override
	protected boolean publishResults2(CharSequence constraint,
			FilterResults results) {
		// Now we have to inform the adapter about the new list filtered
		if (results.count == 0) {
			adapterFilterTalkbalk.removeAllItems();
			return true;
		} else {
			synchronized (mLock) {
				if (results.values instanceof List) {
					return adapterFilterTalkbalk.setItems((List<String>) results.values,
							null);
				}
			}
		}
		return true;
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
				"");
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

	@Override
	public void restoreFromBundle(Bundle savedInstanceState) {
		super.restoreFromBundle(savedInstanceState);
		if (savedInstanceState == null) {
			return;
		}

		String prefix = getClass().getName();
		sizeStart = savedInstanceState.getLong(prefix + ":sizeStart", sizeStart);
		sizeEnd = savedInstanceState.getLong(prefix + ":sizeEnd", sizeEnd);
		dateEnd = savedInstanceState.getLong(prefix + ":dateEnd", dateEnd);
		dateStart = savedInstanceState.getLong(prefix + ":dateStart", dateStart);
		refilter(false);
	}

	@Override
	public void saveToBundle(Bundle outBundle) {
		super.saveToBundle(outBundle);
		String prefix = getClass().getName();
		outBundle.putLong(prefix + ":sizeStart", sizeStart);
		outBundle.putLong(prefix + ":sizeEnd", sizeEnd);
		outBundle.putLong(prefix + ":dateEnd", dateEnd);
		outBundle.putLong(prefix + ":dateStart", dateStart);
	}

	@Override
	public boolean showLetterUI() {
		return rs.getSearchResultList().size() > 3;
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

	@NonNull
	@Override
	public SparseArray<SortDefinition> createSortDefinitions() {
		String[] sortNames = BiglyBTApp.getContext().getResources().getStringArray(
				R.array.sortby_ms_list);

		SparseArray<SortDefinition> sortDefinitions = new SparseArray<>(
				sortNames.length);
		int i = 0;

		//<item>Rank</item>
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_SEARCHRESULT_RANK
		}, SortDefinition.SORT_DESC));
		if (ID_SORT_FILTER.equals(MetaSearchActivity.ID_SORT_FILTER)) {
			defaultSortID = i;
		}

		i++; // <item>Name</item>
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_SEARCHRESULT_NAME
		}, new Boolean[] {
			SortDefinition.SORT_NATURAL
		}, true, SortDefinition.SORT_ASC));

		i++; // <item>Seeds</item>
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_SEARCHRESULT_SEEDS,
			TransmissionVars.FIELD_SEARCHRESULT_PEERS
		}, SortDefinition.SORT_DESC));

		i++; // <item>size</item>
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_SEARCHRESULT_SIZE
		}, SortDefinition.SORT_DESC));

		i++; // <item>PublishDate</item>
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_SEARCHRESULT_PUBLISHDATE
		}, SortDefinition.SORT_DESC));
		if (ID_SORT_FILTER.equals(SubscriptionResultsActivity.ID_SORT_FILTER)) {
			defaultSortID = i;
		}

		return sortDefinitions;
	}
}
