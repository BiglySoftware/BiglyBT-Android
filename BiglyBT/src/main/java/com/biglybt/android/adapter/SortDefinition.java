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

package com.biglybt.android.adapter;

import java.util.Arrays;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.R;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import android.util.Log;
import android.util.SparseArray;

/**
 * Created by TuxPaper on 2/16/16.
 */
public class SortDefinition
{
	public static final boolean SORT_ASC = true;

	public static final boolean SORT_DESC = false;

	public static final boolean SORT_REVERSED = false;

	public static final boolean SORT_NATURAL = true;

	public static final int SORTEVENT_ACTIVATING = 0;

	public static final int SORTEVENT_DEACTIVATING = 1;

	public final int id;

	public @DrawableRes int resAscending;

	public @DrawableRes int resDescending;

	public final String name;

	public final String[] sortFieldIDs;

	private final boolean defaultSortAsc;

	private boolean allowSortDirection = true;

	public final Boolean[] sortOrderNatural;

	public SortDefinition(int id, String name, String[] sortFieldIDs,
			Boolean[] sortOrderNatural, Boolean defaultSortAsc) {
		this(id, name, sortFieldIDs, sortOrderNatural, false, defaultSortAsc);
	}

	public SortDefinition(int id, String name, String[] sortFieldIDs,
			Boolean defaultSortAsc) {
		this(id, name, sortFieldIDs, null, false, defaultSortAsc);
	}

	public SortDefinition(int id, String name, String[] sortFieldIDs,
			Boolean[] sortOrderNatural, boolean isAlphabet, Boolean defaultSortAsc) {
		this.id = id;
		this.name = name;
		this.sortFieldIDs = sortFieldIDs;
		this.allowSortDirection = defaultSortAsc != null;
		this.defaultSortAsc = allowSortDirection ? defaultSortAsc : true;

		Boolean[] order;
		if (sortOrderNatural == null) {
			order = new Boolean[sortFieldIDs.length];
			Arrays.fill(order, SORT_NATURAL);
			this.sortOrderNatural = order;
		} else if (sortOrderNatural.length != sortFieldIDs.length) {
			order = new Boolean[sortFieldIDs.length];
			Arrays.fill(order, SORT_NATURAL);
			System.arraycopy(sortOrderNatural, 0, order, 0, sortOrderNatural.length);
			this.sortOrderNatural = order;
		} else {
			this.sortOrderNatural = sortOrderNatural;
		}

		updateResources(isAlphabet);
	}

	private void updateResources(boolean isAlphabet) {
		resAscending = isAlphabet ? R.drawable.ic_sort_alpha_asc
				: R.drawable.ic_arrow_upward_white_24dp;
		resDescending = isAlphabet ? R.drawable.ic_sort_alpha_desc
				: R.drawable.ic_arrow_downward_white_24dp;
	}

	@Override
	public boolean equals(Object obj) {
		return (obj instanceof SortDefinition) && ((SortDefinition) obj).id == id;
	}

	@Override
	public String toString() {
		return "SortDefinition {" + name + ", #" + id + ", "
				+ Arrays.toString(sortFieldIDs) + ", "
				+ Arrays.toString(sortOrderNatural) + "}";
	}

	public boolean isSortAsc() {
		return defaultSortAsc;
	}

	public void sortEventTriggered(int sortEventID) {
	}

	public boolean allowSortDirection() {
		return allowSortDirection;
	}

	public static SortDefinition findSortDefinition(
			@Nullable StoredSortByInfo sortByInfo,
			SparseArray<SortDefinition> sortDefinitions, int defaultSortID) {
		SortDefinition sortDefinition = sortDefinitions.get(
				sortByInfo == null ? defaultSortID : sortByInfo.id);
		if (sortByInfo != null && sortByInfo.oldSortByFields != null) {
			SortDefinition sortDefinition2 = findSortFromTorrentFields(
					(String[]) sortByInfo.oldSortByFields.toArray(new String[0]),
					sortDefinitions);
			if (sortDefinition2 != null) {
				if (AndroidUtils.DEBUG) {
					Log.d("SortDefinition", "sortByConfig: migrated old sort "
							+ sortByInfo.oldSortByFields + " to " + sortDefinition2.name);
				}
				sortDefinition = sortDefinition2;
			}
		}
		if (sortDefinition == null) {
			sortDefinition = sortDefinitions.get(defaultSortID);
		}

		return sortDefinition;
	}

	private static SortDefinition findSortFromTorrentFields(String[] fields,
			SparseArray<SortDefinition> sortDefinitions) {
		for (int i = 0, size = sortDefinitions.size(); i < size; i++) {
			SortDefinition sortDefinition = sortDefinitions.get(i);
			for (int j = 0; j < sortDefinition.sortFieldIDs.length; j++) {
				if (sortDefinition.sortFieldIDs[j].equals(fields[0])) {
					return sortDefinition;
				}
			}
		}

		return null;
	}

}
