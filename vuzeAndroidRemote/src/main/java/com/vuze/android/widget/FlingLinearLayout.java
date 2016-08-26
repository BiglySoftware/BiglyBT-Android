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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

/**
 * Created by TuxPaper on 6/20/16.
 */
public class FlingLinearLayout extends LinearLayout
{
	/** fling behavior threshold */
	/* @Thunk */ static final int FLING_THRESHOLD = 180;

	/** callback return value content : Left to Right */
	public static final int LEFT_TO_RIGHT = 1;

	/** callback return value content : Right to Left */
	public static final int RIGHT_TO_LEFT = -1;

	private GestureDetector mGestureDetector;

	/* @Thunk */ OnSwipeListener mOnSwipeListener;

	/**
	 * OnSwipeListener interface
	 */
	public interface OnSwipeListener
	{
		void onSwipe(View view, int direction);
	}

	private OnClickListener onClickListener;

	public void setOnSwipeListener(final OnSwipeListener listener) {
		mOnSwipeListener = listener;
	}




	public FlingLinearLayout(Context context) {
		super(context);
		init(context);
	}

	public FlingLinearLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public FlingLinearLayout(Context context, AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public FlingLinearLayout(Context context, AttributeSet attrs,
			int defStyleAttr,
			int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		init(context);
	}

	private void init(Context context) {
		mGestureDetector = new GestureDetector(context,
				new MySimpleOnGestureListener());
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// For the area that doesn't have any children (only ACTION_DOWN will be triggered in onInterceptTouchEvent)
		mGestureDetector.onTouchEvent(event);
		super.onTouchEvent(event);
		return true;
	}


	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		return mGestureDetector.onTouchEvent(ev);
	}


	/**
	 * GestureDetector.SimpleOnGestureListener
	 */
	/* @Thunk */ class MySimpleOnGestureListener
			extends GestureDetector.SimpleOnGestureListener
	{

		@Override
		public boolean onDown(MotionEvent e) {
			return false;
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {
			float sX = e1.getX();
			float sY = e1.getY();
			float eX = e2.getX();
			float eY = e2.getY();
			float deltaX = eX - sX;
			float deltaY = eY - sY;

			if (Math.abs(deltaX) > Math.abs(deltaY) &&
					Math.abs(deltaX) > FLING_THRESHOLD) {
				// Left to Right
				if (e1.getX() < e2.getX()) {
					if (mOnSwipeListener != null) {
						mOnSwipeListener.onSwipe(FlingLinearLayout.this, LEFT_TO_RIGHT);
						return true;
					}
				}
				// Right to Left
				if (e1.getX() > e2.getX()) {
					if (mOnSwipeListener != null) {
						mOnSwipeListener.onSwipe(FlingLinearLayout.this, RIGHT_TO_LEFT);
						return true;
					}
				}
			}
			return false;
		}
	}
}