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

package com.vuze.android.remote;

import android.support.annotation.DrawableRes;

/**
 * Created by TuxPaper on 2/16/16.
 */
public class SortByFields
{
	private final int id;

	public @DrawableRes final int resAscending;

	public @DrawableRes final int resDescending;

	public final String name;

	public final String[] sortFieldIDs;

	public final Boolean[] sortOrderAsc;

	public SortByFields(int id, String name, String[] sortFieldIDs,
			Boolean[] sortOrderAsc) {
		this(id, name, sortFieldIDs, sortOrderAsc, false);
	}

	public SortByFields(int id, String name, String[] sortFieldIDs,
			Boolean[] sortOrderAsc, boolean isAlphabet) {
		this.id = id;
		this.name = name;
		this.sortFieldIDs = sortFieldIDs;
		this.sortOrderAsc = sortOrderAsc;
		resAscending = isAlphabet ? R.drawable.ic_sort_alpha_asc
				: R.drawable.ic_arrow_upward_white_24dp;
		resDescending = isAlphabet ? R.drawable.ic_sort_alpha_desc
				: R.drawable.ic_arrow_downward_white_24dp;
	}

}
