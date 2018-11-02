/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.android.adapter;

import java.util.*;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.AndroidUtilsUI;

import android.support.annotation.NonNull;
import android.util.Log;

/**
 * Created by TuxPaper on 8/6/18.
 */
public abstract class FilterWithMapSorter<T>
	extends DelayedFilter
{
	private static final String TAG = "SortedFilter";

	private ComparatorMapFields<T> sorter;

	@NonNull
	private final Map<Comparable, Boolean> mapGroupIDCollapsed = new HashMap<>();

	FilterWithMapSorter(PerformingFilteringListener l) {
		super(l);
	}

	public ComparatorMapFields<T> getSorter() {
		return sorter;
	}

	protected void setSorter(ComparatorMapFields<T> sorter) {
		this.sorter = sorter;
	}

	protected void doSort(List<T> items) {
		if (sorter == null) {
			return;
		}

		if (!sorter.isValid()) {
			return;
		}

		setFilterState(FILTERSTATE_SORTING);

		if (AndroidUtils.DEBUG_ADAPTER) {
			log(TAG, "Sort by " + sorter.toDebugString());
		}

		if (AndroidUtilsUI.isUIThread()) {
			log(Log.WARN, TAG,
					"Sorting on UIThread! " + AndroidUtils.getCompressedStackTrace());
		}

		// java.lang.IllegalArgumentException: Comparison method violates its
		// general contract!
		try {
			Collections.sort(items, sorter);
		} catch (Throwable t) {
			log(TAG, "doSort: ", t);
		}

		// We could setFilterState back to original (FILTERSTATE_FILTERING),
		// but typically sort is done just before going to FILTERSTATE_PUBLISHING
	}

	public void setSortDefinition(SortDefinition sortDefinition, boolean isAsc) {
		if (sorter == null) {
			log(Log.ERROR, TAG,
					"no sorter for setSortDefinition(" + sortDefinition + ")");
			return;
		}
		if (!sortDefinition.equals(sorter.getSortDefinition())) {
			mapGroupIDCollapsed.clear();
		} else {
			if (isAsc == sorter.isAsc()) {
				return;
			}
		}
		sorter.setSortFields(sortDefinition);
		sorter.setAsc(isAsc);
		saveSortDefinition(sortDefinition, isAsc);
		refilter();
	}

	protected abstract void saveSortDefinition(SortDefinition sortDefinition,
			boolean isAsc);

	public boolean isGroupCollapsed(Comparable groupID) {
		Boolean isCollapsed = mapGroupIDCollapsed.get(groupID);
		return isCollapsed == null ? false : isCollapsed;
	}

	public void setGroupCollapsed(Comparable groupID, boolean isCollapsed) {
		if (AndroidUtils.DEBUG_ADAPTER) {
			log(TAG, "setSortDefinition: set  mapGroupIDCollapsed, groupID=" + groupID
					+ " to " + isCollapsed);
		}
		mapGroupIDCollapsed.put(groupID, isCollapsed);
	}

}
