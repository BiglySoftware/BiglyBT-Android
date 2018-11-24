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
import com.biglybt.android.client.session.Session;
import com.biglybt.android.util.MapUtils;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.SparseArray;

/**
 * Created by TuxPaper on 10/19/16.
 */
public class SubscriptionListAdapterFilter
	extends LetterFilter<String>
{
	private static final String TAG = "SubscriptionListFilter";

	private static final String ID_SORT_FILTER = "-sl";

	private final SubscriptionListAdapter adapter;

	private final SubscriptionListAdapter.SubscriptionSelectionListener rs;

	private final Object mLock;

	private boolean filterShowSearchTemplates = false;

	private boolean filterOnlyUnseen = false;

	private int defaultSortID;

	SubscriptionListAdapterFilter(SubscriptionListAdapter adapter,
			SubscriptionListAdapter.SubscriptionSelectionListener rs, Object mLock) {
		super(rs);
		this.adapter = adapter;
		this.rs = rs;
		this.mLock = mLock;

		StoredSortByInfo sortByInfo = adapter.getSession().getRemoteProfile().getSortByInfo(
				ID_SORT_FILTER);
		SortDefinition sortDefinition = SortDefinition.findSortDefinition(
				sortByInfo, getSortDefinitions(), defaultSortID);
		boolean isAsc = sortByInfo == null ? sortDefinition.isSortAsc()
				: sortByInfo.isAsc;

		ComparatorMapFields<String> sorter = new SubscriptionListSorter(
				sortDefinition, isAsc, rs);
		setSorter(sorter);
	}

	@Override
	protected String getStringToConstrain(String key) {
		Map<?, ?> map = rs.getSubscriptionMap(key);
		if (map == null) {
			return null;
		}

		return MapUtils.getMapString(map, TransmissionVars.FIELD_SUBSCRIPTION_NAME,
				"");
	}

	@NonNull
	@Override
	public String getSectionName(int position) {
		return "";
	}

	@Override
	protected FilterResults performFiltering2(CharSequence _constraint) {

		FilterResults results = new FilterResults();

		final List<String> searchResultList = rs.getSubscriptionList();
		int size = searchResultList.size();

		synchronized (mLock) {

			if (!filterShowSearchTemplates || filterOnlyUnseen) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "filtering " + searchResultList.size());
				}

				for (int i = size - 1; i >= 0; i--) {
					String key = searchResultList.get(i);

					Map<?, ?> map = rs.getSubscriptionMap(key);
					if (map == null) {
						continue;
					}

					if (!filterShowSearchTemplates && MapUtils.getMapBoolean(map,
							TransmissionVars.FIELD_SUBSCRIPTION_IS_SEARCH_TEMPLATE, false)) {
						searchResultList.remove(i);
						size--;
					} else if (filterOnlyUnseen && MapUtils.getMapLong(map,
							TransmissionVars.FIELD_SUBSCRIPTION_NEWCOUNT, 1) == 0) {
						searchResultList.remove(i);
						size--;
					}
				}

				if (AndroidUtils.DEBUG) {
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

	@Override
	protected boolean publishResults2(CharSequence constraint,
			FilterResults results) {
		// Now we have to inform the adapter about the new list filtered
		if (results.count == 0) {
			adapter.removeAllItems();
		} else {
			synchronized (mLock) {
				if (results.values instanceof List) {
					//noinspection unchecked
					return adapter.setItems((List<String>) results.values, null);
				}
			}
		}
		return true;
	}

	@Override
	public void restoreFromBundle(Bundle savedInstanceState) {
		super.restoreFromBundle(savedInstanceState);
		if (savedInstanceState == null) {
			return;
		}

		String prefix = getClass().getName();
		filterShowSearchTemplates = savedInstanceState.getBoolean(
				prefix + ":showSearchTemplates",
				filterShowSearchTemplates);
		refilter(false);

	}

	@Override
	public void saveToBundle(Bundle outBundle) {
		super.saveToBundle(outBundle);
		String prefix = getClass().getName();
		outBundle.putBoolean(prefix + ":showSearchTemplates",
				filterShowSearchTemplates);
	}

	public boolean isFilterShowSearchTemplates() {
		return filterShowSearchTemplates;
	}

	public void setFilterShowSearchTemplates(boolean filterShowSearchTemplates) {
		if (this.filterShowSearchTemplates == filterShowSearchTemplates) {
			refilter(true);
		} else {
			this.filterShowSearchTemplates = filterShowSearchTemplates;
			refilter(false);
		}
	}

	public boolean isFilterOnlyUnseen() {
		return filterOnlyUnseen;
	}

	public void setFilterOnlyUnseen(boolean filterOnlyUnseen) {
		if (this.filterOnlyUnseen == filterOnlyUnseen) {
			refilter(true);
		} else {
			this.filterOnlyUnseen = filterOnlyUnseen;
			refilter(false);
		}
	}

	@Override
	public boolean showLetterUI() {
		return rs.getSubscriptionList().size() > 3;
	}

	@Override
	public SparseArray<SortDefinition> createSortDefinitions() {
		String[] sortNames = BiglyBTApp.getContext().getResources().getStringArray(
				R.array.sortby_sl_list);

		SparseArray<SortDefinition> sortDefinitions = new SparseArray<>(
				sortNames.length);
		int i = 0;

		//<item>Name</item>
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_SUBSCRIPTION_NAME
		}, new Boolean[] {
			false
		}, true));

		i++; // <item>Last Checked</item>
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_SUBSCRIPTION_ENGINE_LASTUPDATED
		}, SortDefinition.SORT_DESC));
		defaultSortID = i;

		i++; // <item># New</item>
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_SUBSCRIPTION_NEWCOUNT
		}, SortDefinition.SORT_DESC));

		return sortDefinitions;
	}

	@Override
	protected void saveSortDefinition(SortDefinition sortDefinition,
			boolean isAsc) {
		Session session = adapter.getSession();
		if (session.getRemoteProfile().setSortBy(ID_SORT_FILTER, sortDefinition,
				isAsc)) {
			session.saveProfile();
		}
	}
}
