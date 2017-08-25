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

import java.util.List;

/**
 * A Sort Definition for {@link FlexibleRecyclerView} that
 * can be grouped.
 */
public abstract class GroupedSortDefinition<ITEM, IDTYPE extends Comparable>
	extends SortDefinition
{
	private boolean showGroupCount = true;

	private int minCountBeforeGrouping = 1;

	public GroupedSortDefinition(int id, String name, String[] sortFieldIDs,
			Boolean[] sortOrderNatural, boolean defaultSortAsc) {
		super(id, name, sortFieldIDs, sortOrderNatural, defaultSortAsc);
	}

	public GroupedSortDefinition(int id, String name, String[] sortFieldIDs,
			boolean defaultSortAsc) {
		super(id, name, sortFieldIDs, defaultSortAsc);
	}

	public abstract IDTYPE getSectionID(ITEM o, boolean isAsc, List<ITEM> items);

	public abstract String getSectionName(IDTYPE sectionID, boolean isAsc);

	public final boolean showGroupCount() {
		return showGroupCount;
	}

	public final void setMinCountBeforeGrouping(int minCountBeforeGrouping) {
		this.minCountBeforeGrouping = minCountBeforeGrouping;
	}

	public final int getMinCountBeforeGrouping() {
		return minCountBeforeGrouping;
	}

	public final void setShowGroupCount(boolean showGroupCount) {
		this.showGroupCount = showGroupCount;
	}
}
