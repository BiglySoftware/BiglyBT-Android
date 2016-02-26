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
import com.vuze.android.remote.AndroidUtils;

/**
 * RecyclerView with FastScroll via FastScrollRecyclerView.
 * Also handles focusing on selected item when RecyclerView gets focus
 */
public class FlexibleRecyclerView
	extends FastScrollRecyclerView
{
	private static final String TAG = "FlexibleRecyclerView";

	public FlexibleRecyclerView(Context context) {
		this(context, null, 0);
	}

	public FlexibleRecyclerView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public FlexibleRecyclerView(Context context, AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);

		// we don't want a change animation (it pulsates the text)
		//((SimpleItemAnimator) getItemAnimator()).setSupportsChangeAnimations(
		//		false);
		// The above causes:
		/*
			 java.lang.IndexOutOfBoundsException: Inconsistency detected. Invalid item position {NUMBER1}(offset:-1).state:{NUMBER2(>NUMBER1)}
			 at android.support.v7.widget.RecyclerView$Recycler.getViewForPosition(RecyclerView.java:4405)
			 at android.support.v7.widget.RecyclerView$Recycler.getViewForPosition(RecyclerView.java:4363)
			 at android.support.v7.widget.LinearLayoutManager$LayoutState.next(LinearLayoutManager.java:1961)
			 at android.support.v7.widget.LinearLayoutManager.layoutChunk(LinearLayoutManager.java:1370)
			 at android.support.v7.widget.LinearLayoutManager.fill(LinearLayoutManager.java:1333)
			 at android.support.v7.widget.LinearLayoutManager.onLayoutChildren(LinearLayoutManager.java:562)
			 at android.support.v7.widget.RecyclerView.dispatchLayout(RecyclerView.java:2864)
			 at android.support.v7.widget.RecyclerView.onLayout(RecyclerView.java:3071)
		 */

		// The first one appears to fix the crashing, but not the pulsating text
		// (I'm actually guessing my FlipValidator is causing the pulsating)
		//getItemAnimator().setChangeDuration(0);
		//getItemAnimator().setAddDuration(0);
		//getItemAnimator().setRemoveDuration(0);
		//getItemAnimator().setMoveDuration(0);

		// Better safe than sorry, just kill the animator for now
		setItemAnimator(null);
	}

	@Override
	public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
		//Log.d(TAG, "requestFocus " + direction);

		// When the view gets the focus, make the selected item the focus
		Adapter adapter = getAdapter();
		if (adapter instanceof FlexibleRecyclerAdapter) {
			int selectedPosition = ((FlexibleRecyclerAdapter) adapter).getSelectedPosition();
			if (AndroidUtils.DEBUG_ADAPTER) {
				Log.d(TAG,
						"requestFocus FLEXIBLE. selectedPosition=" + selectedPosition);
			}
			if (selectedPosition < 0) {
				selectedPosition = 0;
			}
			ViewHolder viewHolder = findViewHolderForAdapterPosition(
					selectedPosition);
			if (AndroidUtils.DEBUG_ADAPTER) {
				Log.d(TAG, "requestFocus VH=" + viewHolder);
			}
			if (viewHolder != null && viewHolder.itemView != null) {
				if (AndroidUtils.DEBUG_ADAPTER) {
					Log.d(TAG, "requestFocus WE DID IT");
				}
				return viewHolder.itemView.requestFocus();
			}
		} else {
			if (AndroidUtils.DEBUG_ADAPTER) {
				Log.d(TAG, "requestFocus NOT FLEXIBLE " + adapter);
			}
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
