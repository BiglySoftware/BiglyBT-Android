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

package com.vuze.android.remote;

import android.content.Context;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class SwipeTextRefreshLayout
	extends SwipeRefreshLayout
{
	TextView mTextView;

	ImageView mCircleView;

	private OnTextVisibilityChangeListener listenerOnTextVisiblityChange;

	public SwipeTextRefreshLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public SwipeTextRefreshLayout(Context context) {
		super(context);
		init();
	}

	private void init() {
		mTextView = new TextView(getContext());
		mTextView.setVisibility(View.GONE);
		mTextView.setGravity(Gravity.CENTER);
	}

	public TextView getTextView() {
		return mTextView;
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
		if (mTextView.getParent() == null) {
			addView(mTextView, -1);
			mTextView.bringToFront();
		}

		return mCircleView;
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		ImageView circleView = findCircleView();
		if (circleView == null) {
			return;
		}

		final int width = getMeasuredWidth();
		final int height = getMeasuredHeight();

//		final int childLeft = getPaddingLeft();
//		final int childTop = getPaddingTop();
//		final int childWidth = width - getPaddingLeft() - getPaddingRight();
//		final int childHeight = height - getPaddingTop() - getPaddingBottom();

		int tvWidth = mTextView.getMeasuredWidth();
		int tvHeight = mTextView.getMeasuredHeight();

		int circleWidth = circleView.getMeasuredWidth();
		int circleHeight = circleView.getMeasuredHeight();
		int offset = circleView.getTop();

		int tvTop = offset + circleHeight;
		mTextView.layout(0, tvTop, width, tvTop + tvHeight);

		int visibility = mTextView.getVisibility();
		int newVisibility = tvTop <= 0 ? View.GONE : View.VISIBLE;

		if (visibility != newVisibility) {
			mTextView.setVisibility(newVisibility);
			if (listenerOnTextVisiblityChange != null) {
				listenerOnTextVisiblityChange.onTextVisibilityChange(mTextView, newVisibility);
			}
		}

		//Log.d("STRL", "layout to " + (offset + circleHeight) + ";ofs=" + offset + ";ch=" + circleHeight + ";" + circleView.getVisibility());

//		mTextView.layout((width / 2) + circleWidth, offset,
//				(width / 2) + circleWidth + tvWidth, offset + tvHeight);
	}

	@Override
	public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		if (mTextView.getLayoutParams() == null) {
			return;
		}
		mTextView.measure(
				getChildMeasureSpec(widthMeasureSpec, 0,
						mTextView.getLayoutParams().width),
				getChildMeasureSpec(heightMeasureSpec, 0,
						mTextView.getLayoutParams().height)

		);
	}

	public void setOnTextVisibilityChange(OnTextVisibilityChangeListener l) {
		listenerOnTextVisiblityChange = l;
	}

	public interface OnTextVisibilityChangeListener {
		public void onTextVisibilityChange(TextView tv, int visibility);
	}

}
