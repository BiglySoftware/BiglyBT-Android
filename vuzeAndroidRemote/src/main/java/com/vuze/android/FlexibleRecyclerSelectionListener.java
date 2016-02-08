/*
 * *
 *  * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *  *
 *  * This program is free software; you can redistribute it and/or
 *  * modify it under the terms of the GNU General Public License
 *  * as published by the Free Software Foundation; either version 2
 *  * of the License, or (at your option) any later version.
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program; if not, write to the Free Software
 *  * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 *
 */

package com.vuze.android;

/**
 * Created by Vuze on 1/25/16.
 */
public interface FlexibleRecyclerSelectionListener
{
	void onItemClick(int position);
	boolean onItemLongClick(int position);

	/**
	 * Triggered when item has been selected.  This is usually by focus change,
	 * either via tapping, or DPAD.
	 *
	 * Selected is not the same as checked. Tapping also results in a
	 * #onItemCheckedChanged, however, DPAD does not.
	 *
	 * @param position The position of the item in the list
	 * @param isChecked The checked state of the item
	 */
	void onItemSelected(int position, boolean isChecked);

	/**
	 * Triggered when an item has been checked or unchecked.  Typically, clicking
	 * on a row causes the item to be checked.
	 *
	 * @param position The position of the item in the list
	 * @param isChecked The new checked state of the item
	 */
	void onItemCheckedChanged(int position, boolean isChecked);
}
