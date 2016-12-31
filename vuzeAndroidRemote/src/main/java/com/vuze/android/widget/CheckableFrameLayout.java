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

package com.vuze.android.widget;

import com.vuze.android.FlexibleRecyclerView;
import com.vuze.android.remote.AndroidUtils;
import com.vuze.android.remote.VuzeEasyTracker;

import android.content.Context;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;
import android.widget.Checkable;
import android.widget.FrameLayout;

/**
 * FROM https://github.com/android/platform_packages_apps_music/blob/master/src/com/android/music/CheckableRelativeLayout.java
 * 
 * A special variation of RelativeLayout that can be used as a checkable object.
 * This allows it to be used as the top-level view of a list view item, which
 * also supports checking.  Otherwise, it works identically to a FrameLayou.
 *
 * Checkable stuff not needed for API >= 11 / HONEYCOMB
 * Hack fix for left/right keeping focus would need to be moved if dropping
 * support for API < 11
 */
public class CheckableFrameLayout
	extends FrameLayout
	implements Checkable
{
	private boolean mChecked;

	private static final int[] CHECKED_STATE_SET = {
		android.R.attr.state_checked
	};

	public CheckableFrameLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected int[] onCreateDrawableState(int extraSpace) {
		final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
		if (mChecked) {
			mergeDrawableStates(drawableState, CHECKED_STATE_SET);
		}
		return drawableState;
	}

	public void toggle() {
		setChecked(!mChecked);
	}

	@Override
	public boolean isChecked() {
		return mChecked;
	}

	public void setChecked(boolean checked) {
		if (mChecked != checked) {
			mChecked = checked;
			refreshDrawableState();
		}
	}

	@Nullable
	@Override
	public View focusSearch(int direction) {
		View view = super.focusSearch(direction);
		try {
			if (direction != FOCUS_DOWN) {
				return view;
			}
			ViewParent parent = getParent();
			// Moving down, but if we are moving to another item in the recycler, then it's ok
			if (!(parent instanceof FlexibleRecyclerView)) {
				return view;
			}
			FlexibleRecyclerView rv = (FlexibleRecyclerView) parent;
			int nextFocusDownId = rv.getNextFocusDownId();
			if (nextFocusDownId <= 0) {
				return view;
			}
			if (view == null || view.getParent() != parent) {
				// New view not within the same Recycler, ensure we are at the end of the list
				RecyclerView.ViewHolder viewHolder = rv.findContainingViewHolder(this);
				if (viewHolder != null) {
					if (viewHolder.getAdapterPosition() == rv.getAdapter().getItemCount()
							- 1) {
						// End of list, move to next focus down
						view = rv.getRootView().findViewById(nextFocusDownId);
					}
				}
			}
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				t.printStackTrace();
			}
			VuzeEasyTracker.getInstance().logError(t);
		}
		return view;
	}

	@Override
	public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
		// When this layout is in a vertical list, gaining focus from the
		// left or the right is usually wrong.  ie. Android tries to guess
		// the row to the left/right of the currently focused view, and doesn't
		// send a requestFocus to the parent first (not even with
		// FOCUS_BEFORE_DESCENDANTS or parent)
		// Hack fix this by forcing parent to get focus
		if (AndroidUtils.DEBUG) {
			Log.d("FSREC", "reqestFocus from " + direction);
		}
		if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
			return ((View) getParent()).requestFocus();
		}
		return super.requestFocus(direction, previouslyFocusedRect);
	}
}