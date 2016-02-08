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

package com.vuze.android;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;
import com.vuze.android.FlexibleRecyclerAdapter;

/**
 * RecyclerView with FastScroll via FastScrollRecyclerView.
 * Also handles focusing on selected item when RecyclerView gets focus
 */
public class FlexibleRecyclerView
	extends FastScrollRecyclerView
{
	private static final String TAG = "FSRecyclerView";

	public FlexibleRecyclerView(Context context) {
		super(context);
	}

	public FlexibleRecyclerView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public FlexibleRecyclerView(Context context, AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
		//Log.d(TAG, "requestFocus " + direction);

		// When the view gets the focus, make the selected item the focus
		Adapter adapter = getAdapter();
		if (adapter instanceof FlexibleRecyclerAdapter) {
			//Log.d(TAG, "requestFocus FLEXIBLE");
			int selectedPosition = ((FlexibleRecyclerAdapter) adapter).getSelectedPosition();
			if (selectedPosition >= 0) {
				//Log.d(TAG, "requestFocus selPos " + selectedPosition);
				ViewHolder viewHolder = findViewHolderForAdapterPosition(
						selectedPosition);
				//Log.d(TAG, "requestFocus VH=" + viewHolder);
				if (viewHolder != null && viewHolder.itemView != null) {
					//Log.d(TAG, "requestFocus WE DID IT");
					return viewHolder.itemView.requestFocus();
				}
			}
			//} else {
			//Log.d(TAG, "requestFocus NOT FLEXIBLE " + adapter);
		}
		return super.requestFocus(direction, previouslyFocusedRect);
	}

	@Override
	public View focusSearch(int direction) {
		View view;
		if (getFocusedChild() == null && direction == View.FOCUS_DOWN) {
			// This fixes a state where the recyclerview has the focus, but no
			// children do.  Pressing DPAD_DOWN will cause the bottom visible row
			// to be focused.  Fix this by choosing the first visible row.
			view = findChildViewUnder(0, 0);
		} else {
			view = super.focusSearch(direction);
		}
		return view;
	}

}
