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
import android.os.Build;
import android.support.v7.widget.OrientationHelper;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
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

	private OnKeyListener keyListener;

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

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			// API 15 with android:animateLayoutChanges will cause:
			//  W/RecyclerView: RecyclerView does not support scrolling to an absolute position. Use scrollToPosition instead
			// AND API 15, 22:
			//   java.lang.IllegalArgumentException: Scrapped or attached views may not be recycled. isScrap:false isAttached:true
			setLayoutTransition(null);
		}
	}

	@Override
	public void requestChildFocus(View child, View focused) {
		super.requestChildFocus(child, focused);

		// This hack is for FilesFragment, so I can capture DPAD_RIGHT before
		// PagerAdapter snatches it.
		if (keyListener != null) {
			child.setOnKeyListener(keyListener);
		}
	}

	@Override
	public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
		// When the view gets the focus, make the selected item the focus
		Adapter adapter = getAdapter();
		if (adapter instanceof FlexibleRecyclerAdapter) {
			int selectedPosition = ((FlexibleRecyclerAdapter) adapter).getSelectedPosition();
			if (AndroidUtils.DEBUG_ADAPTER) {
				Log.d(TAG,
						"requestFocus FLEXIBLE. selectedPosition=" + selectedPosition);
			}
			if (selectedPosition < 0) {
				selectedPosition = findFirstVisibleItemPosition();
			}
			ViewHolder viewHolder = findViewHolderForAdapterPosition(
					selectedPosition);
			if (AndroidUtils.DEBUG_ADAPTER) {
				Log.d(TAG, "requestFocus VH=" + viewHolder);
			}
			if (viewHolder != null && viewHolder.itemView != null) {
				boolean b = viewHolder.itemView.requestFocus();
				if (AndroidUtils.DEBUG_ADAPTER) {
					Log.d(TAG, "requestFocus WE DID IT? " + b);
				}
				return b;
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

	@Override
	public void setOnKeyListener(OnKeyListener l) {
		keyListener = l;
		super.setOnKeyListener(l);
	}

	//////////////////////////////
	// findFirst/Last methods from https://gist.github.com/mipreamble/b6d4b3d65b0b4775a22e
	//////////////////////////////

	/**
	 * Returns the adapter position of the first visible view. This position does not include
	 * adapter changes that were dispatched after the last layout pass.
	 *
	 * @return The adapter position of the first visible item or {@link RecyclerView#NO_POSITION} if
	 * there aren't any visible items.
	 */
	public int findFirstVisibleItemPosition() {
		LayoutManager layoutManager = getLayoutManager();
		final View child = findOneVisibleChild(0, layoutManager.getChildCount(),
				false, true);
		return child == null ? RecyclerView.NO_POSITION
				: getChildAdapterPosition(child);
	}

	/**
	 * Returns the adapter position of the first fully visible view. This position does not include
	 * adapter changes that were dispatched after the last layout pass.
	 *
	 * @return The adapter position of the first fully visible item or
	 * {@link RecyclerView#NO_POSITION} if there aren't any visible items.
	 */
	public int findFirstCompletelyVisibleItemPosition() {
		LayoutManager layoutManager = getLayoutManager();
		final View child = findOneVisibleChild(0, layoutManager.getChildCount(),
				true, false);
		return child == null ? RecyclerView.NO_POSITION
				: getChildAdapterPosition(child);
	}

	/**
	 * Returns the adapter position of the last visible view. This position does not include
	 * adapter changes that were dispatched after the last layout pass.
	 *
	 * @return The adapter position of the last visible view or {@link RecyclerView#NO_POSITION} if
	 * there aren't any visible items
	 */
	public int findLastVisibleItemPosition() {
		LayoutManager layoutManager = getLayoutManager();
		final View child = findOneVisibleChild(layoutManager.getChildCount() - 1,
				-1, false, true);
		return child == null ? RecyclerView.NO_POSITION
				: getChildAdapterPosition(child);
	}

	/**
	 * Returns the adapter position of the last fully visible view. This position does not include
	 * adapter changes that were dispatched after the last layout pass.
	 *
	 * @return The adapter position of the last fully visible view or
	 * {@link RecyclerView#NO_POSITION} if there aren't any visible items.
	 */
	public int findLastCompletelyVisibleItemPosition() {
		LayoutManager layoutManager = getLayoutManager();
		final View child = findOneVisibleChild(layoutManager.getChildCount() - 1,
				-1, true, false);
		return child == null ? RecyclerView.NO_POSITION
				: getChildAdapterPosition(child);
	}

	View findOneVisibleChild(int fromIndex, int toIndex,
			boolean completelyVisible, boolean acceptPartiallyVisible) {
		OrientationHelper helper;
		LayoutManager layoutManager = getLayoutManager();
		if (layoutManager.canScrollVertically()) {
			helper = OrientationHelper.createVerticalHelper(layoutManager);
		} else {
			helper = OrientationHelper.createHorizontalHelper(layoutManager);
		}

		final int start = helper.getStartAfterPadding();
		final int end = helper.getEndAfterPadding();
		final int next = toIndex > fromIndex ? 1 : -1;
		View partiallyVisible = null;
		for (int i = fromIndex; i != toIndex; i += next) {
			final View child = layoutManager.getChildAt(i);
			final int childStart = helper.getDecoratedStart(child);
			final int childEnd = helper.getDecoratedEnd(child);
			if (childStart < end && childEnd > start) {
				if (completelyVisible) {
					if (childStart >= start && childEnd <= end) {
						return child;
					} else if (acceptPartiallyVisible && partiallyVisible == null) {
						partiallyVisible = child;
					}
				} else {
					return child;
				}
			}
		}
		return partiallyVisible;
	}

	@Override
	public void scrollTo(int x, int y) {
		try {
			super.scrollTo(x, y);
		} catch (Throwable t) {
			// Android 4.0.4/API 15 with android:animateLayoutChanges
			Log.e(TAG, "scrollTo; Exception ignored", t);
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		try {
			return super.onTouchEvent(e);
		} catch (Throwable t) {
			// Android 4.0.4/API 15 with android:animateLayoutChanges
			Log.e(TAG, "onTouchEvent: ignoring", t);
		}
		return true;
	}
}
