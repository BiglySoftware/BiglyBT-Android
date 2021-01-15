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

package com.biglybt.android.client.sidelist;

import com.biglybt.android.adapter.FlexibleRecyclerSelectionListener;
import com.biglybt.android.client.SessionGetter;

import androidx.annotation.Nullable;
import androidx.appcompat.view.menu.MenuBuilder;
import android.view.Menu;

/**
 * Created by TuxPaper on 8/20/18.
 */
public interface SideActionSelectionListener
	extends
	FlexibleRecyclerSelectionListener<SideActionsAdapter, SideActionsAdapter.SideActionsHolder, SideActionsAdapter.SideActionsInfo>,
	SessionGetter
{
	boolean isRefreshing();

	/**
	 * Adjust menu item states (visibility, text, etc) here
	 */
	void prepareActionMenus(Menu menu);

	MenuBuilder getMenuBuilder();

	/**
	 * Filter out these menu id's when showing list
	 */
	@Nullable
	int[] getRestrictToMenuIDs();
}
