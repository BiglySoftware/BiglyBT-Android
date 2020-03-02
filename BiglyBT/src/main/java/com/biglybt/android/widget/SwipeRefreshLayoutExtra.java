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

package com.biglybt.android.widget;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.R;
import com.biglybt.util.Thunk;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * A SwipeRefreshLayout with an extra view undernear the refresh circle.
 * <p/>
 * Sorta crappy.  Should just really take the SwipeRefreshLayout and add
 * to it instead of hacking
 */
public class SwipeRefreshLayoutExtra
	extends SwipeRefreshLayout
{
	private static final String TAG = "SwipeLayoutExtra";

	private static final boolean DEBUG_FOLLOW_THE_CIRLCE_HACK = false;

	private ImageView mCircleView;

	private OnExtraViewVisibilityChangeListener listenerOnExtraViewVisiblityChange;

	private View mExtraView;

	private int currentVisibility = View.GONE;

	public SwipeRefreshLayoutExtra(@NonNull Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public SwipeRefreshLayoutExtra(@NonNull Context context) {
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

	@Thunk
	ImageView findCircleView() {
		if (mExtraView == null) {
			return null;
		}
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
	public void invalidate() {
		super.invalidate();
		ImageView circleView = findCircleView();
		if (DEBUG_FOLLOW_THE_CIRLCE_HACK) {
			Log.d(TAG, "invalidate");
		}
		if (circleView != null) {
			layoutExtra(circleView);
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		boolean b = super.onTouchEvent(ev);

		if (DEBUG_FOLLOW_THE_CIRLCE_HACK) {
			Log.d(TAG, "onTouchEvent");
		}

		// Mostly needed for older APIs (8)
		ImageView circleView = findCircleView();
		if (circleView != null) {
			layoutExtra(circleView);
		}
		return b;
	}

	@Override
	public void onStopNestedScroll(View target) {
		super.onStopNestedScroll(target);

		// Mostly needed for older APIs (16).  Can't call layoutExtra, because circleView's top is still > 0
		postDelayed(() -> {
			ImageView circleView = findCircleView();
			if (circleView != null) {
				layoutExtra(circleView);
			}
		}, 300);
	}

	@Override
	public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
		super.onNestedPreScroll(target, dx, dy, consumed);

		if (DEBUG_FOLLOW_THE_CIRLCE_HACK) {
			Log.d(TAG, "onNestedPreScroll");
		}
		// Mostly needed for older APIs (8)
		ImageView circleView = findCircleView();
		if (circleView != null) {
			layoutExtra(circleView);
		}
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		if (mExtraView == null) {
			if (DEBUG_FOLLOW_THE_CIRLCE_HACK) {
				Log.d(TAG, "no extra");
			}
			return;
		}

		ImageView circleView = findCircleView();
		if (circleView == null) {
			if (DEBUG_FOLLOW_THE_CIRLCE_HACK) {
				Log.d(TAG, "no circle");
			}
			return;
		}

		layoutExtra(circleView);
	}

	@Thunk
	void layoutExtra(ImageView circleView) {
		if (mExtraView == null) {
			return;
		}
		final int width = getMeasuredWidth();

		int tvHeight = mExtraView.getMeasuredHeight();

		int circleHeight = circleView.getMeasuredHeight();
		int offset = circleView.getTop();

		int tvTop = offset + circleHeight;
		if (DEBUG_FOLLOW_THE_CIRLCE_HACK) {
			Log.d(TAG, "layoutExtra: " + tvTop + ";" + circleView.getVisibility());
		}
		mExtraView.layout(0, tvTop, width, tvTop + tvHeight);

		int visibility = mExtraView.getVisibility();
		int newVisibility = tvTop <= 0 || circleView.getVisibility() == View.GONE
				? View.GONE : View.VISIBLE;

		if (visibility != newVisibility) {
			if (DEBUG_FOLLOW_THE_CIRLCE_HACK) {
				Log.d(TAG, "layoutExtra: set " + (tvTop <= 0 ? "GONE" : "VISIBLE") + ";"
						+ AndroidUtils.getCompressedStackTrace());
			}
			mExtraView.setVisibility(newVisibility);
			tiggerVisibilityListener(newVisibility);
		}
	}

	private void tiggerVisibilityListener(int newVisibility) {
		if (newVisibility == currentVisibility) {
			return;
		}
		currentVisibility = newVisibility;
		if (listenerOnExtraViewVisiblityChange != null) {
			listenerOnExtraViewVisiblityChange.onExtraViewVisibilityChange(
				mExtraView, newVisibility);
		}
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

	@Override
	protected void onVisibilityChanged(@NonNull View changedView,
		int visibility) {
		super.onVisibilityChanged(changedView, visibility);
		// Usually when fragment or activity becomes hidden/shown
		if (visibility != View.VISIBLE) {
			tiggerVisibilityListener(visibility);
		}
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		tiggerVisibilityListener(View.INVISIBLE);
	}

	public interface OnExtraViewVisibilityChangeListener
	{
		void onExtraViewVisibilityChange(View view, int visibility);
	}

	public static class SwipeTextUpdater
		implements OnExtraViewVisibilityChangeListener
	{
		@NonNull
		final RunnableOnUpdateText runnableOnUpdateText;

		@Thunk
		Handler pullRefreshHandler;

		public interface RunnableOnUpdateText
		{
			long onUpdateText(TextView view);
		}

		public SwipeTextUpdater(@NonNull Lifecycle lifecycle,
				@NonNull RunnableOnUpdateText runnableOnUpdateText) {
			this.runnableOnUpdateText = runnableOnUpdateText;
			lifecycle.addObserver(new DefaultLifecycleObserver() {
				@Override
				public void onDestroy(@NonNull LifecycleOwner owner) {
					detroyHandler();
				}
			});
		}
		
		void detroyHandler() {
			if (pullRefreshHandler != null) {
				pullRefreshHandler.removeCallbacksAndMessages(null);
				pullRefreshHandler = null;
			}
		}

		@Override
		public void onExtraViewVisibilityChange(final View view, int visibility) {
			detroyHandler();

			if (visibility != VISIBLE) {
				return;
			}

			pullRefreshHandler = new Handler(Looper.getMainLooper());

			pullRefreshHandler.postDelayed(new Runnable() {
				@Override
				public void run() {

					TextView tvSwipeText = view.findViewById(R.id.swipe_text);

					long i = -1;
					if (tvSwipeText != null) {
						try {
							i = runnableOnUpdateText.onUpdateText(tvSwipeText);
						} catch (Throwable ignore) {
						}
					}

					if (pullRefreshHandler == null) {
						return;
					}
					if (i >= 0) {
						pullRefreshHandler.postDelayed(this, i);
					} else {
						pullRefreshHandler = null;
					}
				}
			}, 0);
		}
	}
}
