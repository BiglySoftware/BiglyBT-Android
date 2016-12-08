/*
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

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;

/**
 * {@link GridLayoutManager} extension which introduces workaround for focus finding bug when
 * navigating with dpad.
 *
 * @see <a href="http://stackoverflow.com/questions/31596801/recyclerview-focus-scrolling">http://stackoverflow.com/questions/31596801/recyclerview-focus-scrolling</a>
 */
public class GridLayoutManager
	extends android.support.v7.widget.GridLayoutManager
{

	public GridLayoutManager(Context context, AttributeSet attrs,
			int defStyleAttr,

			int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	public GridLayoutManager(Context context, int spanCount) {
		super(context, spanCount);
	}

	public GridLayoutManager(Context context, int spanCount, int orientation,
			boolean reverseLayout) {
		super(context, spanCount, orientation, reverseLayout);
	}

	@Override
	public View onFocusSearchFailed(View focused, int focusDirection,
			RecyclerView.Recycler recycler, RecyclerView.State state) {
		// Need to be called in order to layout new row/column
		View nextFocus = super.onFocusSearchFailed(focused, focusDirection,
				recycler, state);

		if (nextFocus == null) {
			return null;
		}

		int fromPos = getPosition(focused);
		int nextPos = getNextViewPos(fromPos, focusDirection);

		return findViewByPosition(nextPos);
	}

	/**
	 * Manually detect next view to focus.
	 *
	 * @param fromPos from what position start to seek.
	 * @param direction in what direction start to seek. Your regular {@code View.FOCUS_*}.
	 * @return adapter position of next view to focus. May be equal to {@code fromPos}.
	 */
	private int getNextViewPos(int fromPos, int direction) {
		int offset = calcOffsetToNextView(direction);

		if (hitBorder(fromPos, offset)) {
			return fromPos;
		}

		return fromPos + offset;
	}

	/**
	 * Calculates position offset.
	 *
	 * @param direction regular {@code View.FOCUS_*}.
	 * @return position offset according to {@code direction}.
	 */
	private int calcOffsetToNextView(int direction) {
		int spanCount = getSpanCount();
		int orientation = getOrientation();

		if (orientation == VERTICAL) {
			switch (direction) {
				case View.FOCUS_DOWN:
					return spanCount;
				case View.FOCUS_UP:
					return -spanCount;
				case View.FOCUS_RIGHT:
					return 1;
				case View.FOCUS_LEFT:
					return -1;
			}
		} else if (orientation == HORIZONTAL) {
			switch (direction) {
				case View.FOCUS_DOWN:
					return 1;
				case View.FOCUS_UP:
					return -1;
				case View.FOCUS_RIGHT:
					return spanCount;
				case View.FOCUS_LEFT:
					return -spanCount;
			}
		}

		return 0;
	}

	/**
	 * Checks if we hit borders.
	 *
	 * @param from from what position.
	 * @param offset offset to new position.
	 * @return {@code true} if we hit border.
	 */
	private boolean hitBorder(int from, int offset) {
		int spanCount = getSpanCount();

		if (Math.abs(offset) == 1) {
			int spanIndex = from % spanCount;
			int newSpanIndex = spanIndex + offset;
			return newSpanIndex < 0 || newSpanIndex >= spanCount;
		} else {
			int newPos = from + offset;
			return newPos < 0 && newPos >= spanCount;
		}
	}
}