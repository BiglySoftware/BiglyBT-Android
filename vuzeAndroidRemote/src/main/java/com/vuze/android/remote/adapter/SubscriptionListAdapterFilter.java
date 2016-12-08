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
 * Created by TuxPaper on 10/19/16.
 */
public class SubscriptionListAdapterFilter
	extends LetterFilter<String>
{
	private static final String TAG = "SubscriptionListFilter";

	private final SubscriptionListAdapter adapter;

	private final SubscriptionListAdapter.SubscriptionSelectionListener rs;

	private final Object mLock;

	private boolean filterShowSearchTemplates = false;

	private boolean filterOnlyUnseen = false;

	public SubscriptionListAdapterFilter(SubscriptionListAdapter adapter,
			SubscriptionListAdapter.SubscriptionSelectionListener rs, Object mLock) {

		this.adapter = adapter;
		this.rs = rs;
		this.mLock = mLock;
	}

	@Override
	protected String getStringToConstrain(String key) {
		Map<?, ?> map = rs.getSubscriptionMap(key);
		if (map == null) {
			return null;
		}

		return MapUtils.getMapString(map, TransmissionVars.FIELD_SUBSCRIPTION_NAME,
				"").toUpperCase(Locale.US);
	}

	@Override
	protected void lettersUpdated(HashMap<String, Integer> mapLetterCount) {
		adapter.lettersUpdated(mapLetterCount);
	}

	@Override
	protected FilterResults performFiltering(CharSequence _constraint) {

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

		adapter.doSort(searchResultList, false);

		results.values = searchResultList;
		results.count = searchResultList.size();

		return results;
	}

	@Override
	protected void publishResults(CharSequence constraint,
			FilterResults results) {
		// Now we have to inform the adapter about the new list filtered
		if (results.count == 0) {
			adapter.removeAllItems();
		} else {
			synchronized (mLock) {
				if (results.values instanceof List) {
					adapter.setItems((List<String>) results.values);
				}
			}
		}
	}

	public void restoreFromBundle(Bundle savedInstanceState) {
		if (savedInstanceState == null) {
			return;
		}

		String prefix = getClass().getName();
		filterShowSearchTemplates = savedInstanceState.getBoolean(
				prefix + ":showSearchTemplates",

				filterShowSearchTemplates);
		refilter();

	}

	public void saveToBundle(Bundle outState) {
		String prefix = getClass().getName();
		outState.putBoolean(prefix + ":showSearchTemplates",
				filterShowSearchTemplates);
	}

	public boolean isFilterShowSearchTemplates() {
		return filterShowSearchTemplates;
	}

	public void setFilterShowSearchTemplates(boolean filterShowSearchTemplates) {
		this.filterShowSearchTemplates = filterShowSearchTemplates;
		refilter();
	}

	public boolean isFilterOnlyUnseen() {
		return filterOnlyUnseen;
	}

	public void setFilterOnlyUnseen(boolean filterOnlyUnseen) {
		this.filterOnlyUnseen = filterOnlyUnseen;
		refilter();
	}
}
