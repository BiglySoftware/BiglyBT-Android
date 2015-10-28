/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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
 * 
 */

package com.vuze.android.remote.fragment;

import android.support.v7.view.ActionMode;

/**
 * This is a real mess.
 * <p>
 * Used when there are multiple lists on the screen, each of which have an
 * actionmode.  Tries to handle restoring the previous action mode.
 * <p>
 * appcompat v21 made this code messier.  Will eventually replace with Toolbar logic
 */
public interface ActionModeBeingReplacedListener
{
	public void setActionModeBeingReplaced(ActionMode actionMode,
			boolean actionModeBeingReplaced);

	public void actionModeBeingReplacedDone();

	public void rebuildActionMode();

	public ActionMode getActionMode();
}