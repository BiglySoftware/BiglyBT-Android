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

package com.biglybt.android;

import java.util.Arrays;

import com.biglybt.android.client.R;

import android.support.annotation.DrawableRes;

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

	public final Boolean[] sortOrderNatural;

	public SortDefinition(int id, String name, String[] sortFieldIDs,
			Boolean[] sortOrderNatural, boolean defaultSortAsc) {
		this(id, name, sortFieldIDs, sortOrderNatural, false, defaultSortAsc);
	}

	public SortDefinition(int id, String name, String[] sortFieldIDs,
			boolean defaultSortAsc) {
		this(id, name, sortFieldIDs, null, false, defaultSortAsc);
	}

	public SortDefinition(int id, String name, String[] sortFieldIDs,
			Boolean[] sortOrderNatural, boolean isAlphabet, boolean defaultSortAsc) {
		this.id = id;
		this.name = name;
		this.sortFieldIDs = sortFieldIDs;
		this.defaultSortAsc = defaultSortAsc;

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
}
