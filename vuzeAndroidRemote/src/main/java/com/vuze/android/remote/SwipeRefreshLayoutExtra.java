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

package com.vuze.android.remote;

import android.content.Context;
import android.graphics.Canvas;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.widget.ImageView;

/**
 * A SwipeRefreshLayout with an extra view undernear the refresh circle
 */
public class SwipeRefreshLayoutExtra
	extends SwipeRefreshLayout
{
	ImageView mCircleView;

	private OnExtraViewVisibilityChangeListener listenerOnExtraViewVisiblityChange;

	private View mExtraView;

	public SwipeRefreshLayoutExtra(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public SwipeRefreshLayoutExtra(Context context) {
		super(context);
	}

	public void setExtraLayout(int layoutID) {
		LayoutInflater factory = LayoutInflater.from(getContext());
		mExtraView = factory.inflate(layoutID, this, false);
		mExtraView.setVisibility(View.GONE);
	}

	public View getExtraView() {
		return mExtraView;
	}

	private ImageView findCircleView() {
		if (mCircleView != null) {
			return mCircleView;
		}
		for (int i = 0; i < getChildCount(); i++) {
			View child = getChildAt(i);
			if (child instanceof ImageView) {
				mCircleView = (ImageView) child;
				break;
			}
		}
		if (mExtraView.getParent() == null) {
			addView(mExtraView, -1);
			mExtraView.bringToFront();
		}

		return mCircleView;
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		// Mostly needed for older APIs (8)
		ImageView circleView = findCircleView();
		if (circleView != null) {
			layoutExtra(circleView);
		}
		return super.onTouchEvent(ev);
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		if (mExtraView == null) {
			return;
		}

		ImageView circleView = findCircleView();
		if (circleView == null) {
			return;
		}

		layoutExtra(circleView);

		int tvTop = circleView.getTop() + circleView.getMeasuredHeight();

		int visibility = mExtraView.getVisibility();
		int newVisibility = tvTop <= 0 ? View.GONE : View.VISIBLE;

		if (visibility != newVisibility) {
			mExtraView.setVisibility(newVisibility);
			if (listenerOnExtraViewVisiblityChange != null) {
				listenerOnExtraViewVisiblityChange.onExtraViewVisibilityChange(
						mExtraView, newVisibility);
			}
		}
	}

	private void layoutExtra(ImageView circleView) {
		final int width = getMeasuredWidth();

		int tvHeight = mExtraView.getMeasuredHeight();

		int circleHeight = circleView.getMeasuredHeight();
		int offset = circleView.getTop();

		int tvTop = offset + circleHeight;
		mExtraView.layout(0, tvTop, width, tvTop + tvHeight);
	}

	@Override
	public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		if (mExtraView == null) {
			return;
		}
		if (mExtraView.getLayoutParams() == null) {
			return;
		}
		mExtraView.measure(
				getChildMeasureSpec(widthMeasureSpec, 0,
						mExtraView.getLayoutParams().width),
				getChildMeasureSpec(heightMeasureSpec, 0,
						mExtraView.getLayoutParams().height)

		);
	}

	public void setOnExtraViewVisibilityChange(
			OnExtraViewVisibilityChangeListener l) {
		listenerOnExtraViewVisiblityChange = l;
	}

	public interface OnExtraViewVisibilityChangeListener
	{
		void onExtraViewVisibilityChange(View view, int visibility);
	}

}
