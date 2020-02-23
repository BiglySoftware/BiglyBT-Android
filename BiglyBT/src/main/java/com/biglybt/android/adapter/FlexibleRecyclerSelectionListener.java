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

import androidx.annotation.UiThread;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Item Selection and Click listeners for {@link FlexibleRecyclerAdapter}
 * 
 * Created by TuxPaper on 1/25/16.
 */
public interface FlexibleRecyclerSelectionListener<ADAPTERTYPE extends RecyclerView.Adapter<VH>, VH extends RecyclerView.ViewHolder, T>
{
	@UiThread
	void onItemClick(ADAPTERTYPE adapter, int position);

	/**
	 * Triggered when item is long-clicked.  Item will already be selected.
	 *
	 * @param adapter Adapter that triggered the event
	 * @param position The position of the item in the list
	 * @return
	 * 	true - Long click handled. Item will not be checked after call.
	 * 	false - Long click was not handled. Item will be checked after call.
	 */
	@UiThread
	boolean onItemLongClick(ADAPTERTYPE adapter, int position);

	/**
	 * Triggered when item has been selected.  This is usually by focus change,
	 * either via tapping, or DPAD.
	 *
	 * Selected is not the same as checked. Tapping also results in a
	 * #onItemCheckedChanged, however, DPAD does not.
	 *
	 * @param adapter Adapter that triggered the event
	 * @param position The position of the item in the list
	 * @param isChecked The checked state of the item
	 */
	@UiThread
	void onItemSelected(ADAPTERTYPE adapter, int position, boolean isChecked);

	/**
	 * Triggered when an item has been checked or unchecked.  Typically, clicking
	 * on a row causes the item to be checked.
	 *
	 * @param adapter Adapter that triggered the event
	 * @param item The item in the list
	 * @param isChecked The new checked state of the item
	 */
	@UiThread
	void onItemCheckedChanged(ADAPTERTYPE adapter, T item, boolean isChecked);
}
