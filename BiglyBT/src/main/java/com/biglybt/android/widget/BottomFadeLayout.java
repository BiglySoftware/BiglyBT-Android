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

package com.biglybt.android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EdgeEffect;
import android.widget.FrameLayout;

public class BottomFadeLayout
	extends FrameLayout
{
	private EdgeEffect mEdgeGlowBottom;

	public BottomFadeLayout(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initScrollView();
	}

	public BottomFadeLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		initScrollView();
	}

	public BottomFadeLayout(Context context) {
		super(context);
		initScrollView();
	}

	private void initScrollView() {
		setWillNotDraw(false);
	}

	@Override
	public void setOverScrollMode(int mode) {
		if (mode != OVER_SCROLL_NEVER) {
			if (mEdgeGlowBottom == null) {
				Context context = getContext();
				mEdgeGlowBottom = new EdgeEffect(context);
			}
		} else {
			mEdgeGlowBottom = null;
		}
		super.setOverScrollMode(mode);
	}

	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		if (mEdgeGlowBottom != null) {
			int width = getWidth();
			int height = getHeight();
			int translateX = 0;
			int translateY = 0;

			final int restoreCount = canvas.save();
			mEdgeGlowBottom.setSize(width, height);
			canvas.rotate(180, width, 0);
			canvas.translate(-width + translateX,
					Math.max(getScrollRange(), getScrollY()) + height + translateY);
			mEdgeGlowBottom.draw(canvas);
			canvas.restoreToCount(restoreCount);
		}
	}

	private int getScrollRange() {
		int scrollRange = 0;
		if (getChildCount() > 0) {
			View child = getChildAt(0);
			scrollRange = Math.max(0, child.getHeight()
					- (getHeight() - getPaddingBottom() - getPaddingTop()));
		}
		return scrollRange;
	}

	@Override
	protected float getBottomFadingEdgeStrength() {
		if (getChildCount() == 0) {
			return 0.0f;
		}

		final int length = getVerticalFadingEdgeLength();
		final int bottomEdge = getHeight() - getPaddingBottom();
		final int span = getChildAt(0).getBottom() - getScrollY() - bottomEdge;
		if (span < length) {
			return span / (float) length;
		}

		return 1.0f;
	}

	/**
	 * <p>The scroll range of a scroll view is the overall height of all of its
	 * children.</p>
	 */
	@Override
	protected int computeVerticalScrollRange() {
		final int count = getChildCount();
		final int contentHeight = getHeight() - getPaddingBottom()
				- getPaddingTop();
		if (count == 0) {
			return contentHeight;
		}

		int scrollRange = getChildAt(0).getBottom();
		final int scrollY = getScrollY();
		final int overscrollBottom = Math.max(0, scrollRange - contentHeight);
		if (scrollY < 0) {
			scrollRange -= scrollY;
		} else if (scrollY > overscrollBottom) {
			scrollRange += scrollY - overscrollBottom;
		}

		return scrollRange;
	}

	@Override
	protected int computeVerticalScrollOffset() {
		return Math.max(0, super.computeVerticalScrollOffset());
	}

	@Override
	protected void measureChild(View child, int parentWidthMeasureSpec,
			int parentHeightMeasureSpec) {
		ViewGroup.LayoutParams lp = child.getLayoutParams();

		int childWidthMeasureSpec;
		int childHeightMeasureSpec;

		childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
				getPaddingLeft() + getPaddingRight(), lp.width);

		childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(0,
				MeasureSpec.UNSPECIFIED);

		child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
	}

	@Override
	protected void measureChildWithMargins(View child, int parentWidthMeasureSpec,
			int widthUsed, int parentHeightMeasureSpec, int heightUsed) {
		final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();

		final int childWidthMeasureSpec = getChildMeasureSpec(
				parentWidthMeasureSpec, getPaddingLeft() + getPaddingRight()
						+ lp.leftMargin + lp.rightMargin + widthUsed,
				lp.width);
		final int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
				lp.topMargin + lp.bottomMargin, MeasureSpec.UNSPECIFIED);

		child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
	}

}