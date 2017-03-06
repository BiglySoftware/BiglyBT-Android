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

import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;
import com.vuze.android.remote.AndroidUtils;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v7.widget.*;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

/**
 * RecyclerView with FastScroll via FastScrollRecyclerView.
 * Also handles focusing on selected item when RecyclerView gets focus
 */
public class FlexibleRecyclerView
	extends FastScrollRecyclerView
{
	private static final String TAG = "FlexibleRecyclerView";

	private Integer columnWidth = null;

	private View.OnKeyListener keyListener;

	private int fixedVerticalHeight;

	public FlexibleRecyclerView(Context context) {
		this(context, null, 0);
	}

	public FlexibleRecyclerView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public FlexibleRecyclerView(Context context, @Nullable AttributeSet attrs,
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
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB_MR2) {
			ItemAnimator itemAnimator = getItemAnimator();
			if (itemAnimator instanceof SimpleItemAnimator) {
				((SimpleItemAnimator) itemAnimator).setSupportsChangeAnimations(false);
			}
			itemAnimator.setChangeDuration(0);
			//itemAnimator.setAddDuration(0);
			//itemAnimator.setRemoveDuration(0);
			//itemAnimator.setMoveDuration(0);
		} else {
			setItemAnimator(null);
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			// API 15 with android:animateLayoutChanges will cause:
			//  W/RecyclerView: RecyclerView does not support scrolling to an absolute position. Use scrollToPosition instead
			// AND API 15, 22:
			//   java.lang.IllegalArgumentException: Scrapped or attached views may not be recycled. isScrap:false isAttached:true
//			setLayoutTransition(null);
		}

		if (attrs != null) {
			int[] attrsArray = {
				android.R.attr.columnWidth
			};
			TypedArray array = context.obtainStyledAttributes(attrs, attrsArray);
			columnWidth = array.getDimensionPixelSize(0, -1);
			array.recycle();
		}

	}

	// from http://blog.sqisland.com/2014/12/recyclerview-autofit-grid.html
	protected void onMeasure(int widthSpec, int heightSpec) {
		super.onMeasure(widthSpec, heightSpec);
		if (columnWidth != null) {
			LayoutManager manager = getLayoutManager();
			if (manager instanceof GridLayoutManager) {
				int spanCount = Math.max(1, getMeasuredWidth() / columnWidth);
				((GridLayoutManager) manager).setSpanCount(spanCount);
			}
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

		LayoutManager layoutManager = getLayoutManager();
		if (layoutManager instanceof GridLayoutManager) {
			return super.requestFocus(direction, previouslyFocusedRect);
		}
		// When the view gets the focus, make the selected item the focus
		Adapter adapter = getAdapter();
		if (adapter instanceof FlexibleRecyclerAdapter) {
			int selectedPosition = ((FlexibleRecyclerAdapter) adapter).getSelectedPosition();
			if (AndroidUtils.DEBUG_ADAPTER) {
				log("requestFocus FLEXIBLE. selectedPosition=" + selectedPosition);
			}
			if (selectedPosition < 0) {
				selectedPosition = findFirstVisibleItemPosition();
			}
			ViewHolder viewHolder = findViewHolderForAdapterPosition(
					selectedPosition);
			if (AndroidUtils.DEBUG_ADAPTER) {
				log("requestFocus VH=" + viewHolder);
			}
			if (viewHolder != null && viewHolder.itemView != null) {
				boolean b = viewHolder.itemView.requestFocus();
				if (AndroidUtils.DEBUG_ADAPTER) {
					log("requestFocus WE DID IT? " + b);
				}
				return b;
			}
		} else {
			if (AndroidUtils.DEBUG_ADAPTER) {
				log("requestFocus NOT FLEXIBLE " + adapter);
			}
		}
		return super.requestFocus(direction, previouslyFocusedRect);
	}

	@Override
	public View focusSearch(int direction) {
		View view;
		if (getFocusedChild() == null) {
			if (direction == View.FOCUS_DOWN) {
				// This fixes a state where the recyclerview has the focus, but no
				// children do.  Pressing DPAD_DOWN will cause the bottom visible row
				// to be focused.  Fix this by choosing the first visible row.
				LayoutManager layoutManager = getLayoutManager();
				view = findOneVisibleChild(0, layoutManager.getChildCount(), true,
						false);
				//view = findChildViewUnder(0, 0);
				if (view != null) {
					return view;
				}
			}
			if (direction == View.FOCUS_UP) {
//				view = findChildViewUnder(0, getHeight() - 1);
				LayoutManager layoutManager = getLayoutManager();
				view = findOneVisibleChild(layoutManager.getChildCount() - 1, -1, false,
						true);

				if (view != null) {
					return view;
				}
			}
		}
		return super.focusSearch(direction);
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

	private View findOneVisibleChild(int fromIndex, int toIndex,
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

	@Override
	public boolean requestChildRectangleOnScreen(View child, Rect rect,
			boolean immediate) {
		if (fixedVerticalHeight > 0) {
			rect.top -= fixedVerticalHeight;
			rect.bottom += fixedVerticalHeight;
		}
		return super.requestChildRectangleOnScreen(child, rect, immediate);
	}

	/**
	 * When scrolling up or down, show fixedVerticalHeight pixels of row
	 * above or below.
	 * <p/>
	 * Excellent for {@link #setFadingEdgeLength(int)}
	 */
	public void setFixedVerticalHeight(int fixedVerticalHeight) {
		this.fixedVerticalHeight = fixedVerticalHeight;
	}

	public int getFixedVerticalHeight() {
		return fixedVerticalHeight;
	}

	private void log(String s) {
		Adapter adapter = getAdapter();
		String name;
		if (adapter == null) {
			name = this.toString();
		} else {
			name = adapter.getClass().getSimpleName();
			if (name.length() == 0) {
				name = adapter.getClass().getName();
			}
		}
		Log.d(TAG, name + "] " + s);
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		try {
			super.onLayout(changed, l, t, r, b);
		} catch (NullPointerException ignore) {
			Log.e(TAG, "onLayout: ", ignore);
			/** API 8:
			 05-10 12:38:09.499 2157-2157/com.vuze.android.remote E/AndroidRuntime: FATAL EXCEPTION: main
			 java.lang.NullPointerException
			 at android.text.style.DynamicDrawableSpan.getSize(DynamicDrawableSpan.java:81)
			 at android.text.Styled.drawUniformRun(Styled.java:151)
			 at android.text.Styled.drawDirectionalRun(Styled.java:298)
			 at android.text.Styled.measureText(Styled.java:430)
			 at android.text.BoringLayout.isBoring(BoringLayout.java:283)
			 at android.widget.TextView.onMeasure(TextView.java:5087)
			 at android.view.View.measure(View.java:8171)
			 at android.view.ViewGroup.measureChildWithMargins(ViewGroup.java:3132)
			 at android.widget.FrameLayout.onMeasure(FrameLayout.java:245)
			 at android.view.View.measure(View.java:8171)
			 at android.view.ViewGroup.measureChildWithMargins(ViewGroup.java:3132)
			 at android.widget.LinearLayout.measureChildBeforeLayout(LinearLayout.java:1012)
			 at android.widget.LinearLayout.measureHorizontal(LinearLayout.java:696)
			 at android.widget.LinearLayout.onMeasure(LinearLayout.java:306)
			 at android.view.View.measure(View.java:8171)
			 at android.support.v7.widget.RecyclerView$LayoutManager.measureChildWithMargins(RecyclerView.java:7487)
			 at android.support.v7.widget.LinearLayoutManager.layoutChunk(LinearLayoutManager.java:1416)
			 at android.support.v7.widget.LinearLayoutManager.fill(LinearLayoutManager.java:1353)
			 at android.support.v7.widget.LinearLayoutManager.onLayoutChildren(LinearLayoutManager.java:574)
			 at android.support.v7.widget.RecyclerView.dispatchLayoutStep2(RecyclerView.java:3028)
			 at android.support.v7.widget.RecyclerView.dispatchLayout(RecyclerView.java:2906)
			 at android.support.v7.widget.RecyclerView.onLayout(RecyclerView.java:3283)
			 at android.view.View.layout(View.java:7035)
			 at android.widget.LinearLayout.setChildFrame(LinearLayout.java:1249)
			 at android.widget.LinearLayout.layoutVertical(LinearLayout.java:1125)
			 at android.widget.LinearLayout.onLayout(LinearLayout.java:1042)
			 at android.view.View.layout(View.java:7035)
			 at android.widget.LinearLayout.setChildFrame(LinearLayout.java:1249)
			 at android.widget.LinearLayout.layoutHorizontal(LinearLayout.java:1238)
			 at android.widget.LinearLayout.onLayout(LinearLayout.java:1044)
			 at android.view.View.layout(View.java:7035)
			 at android.widget.FrameLayout.onLayout(FrameLayout.java:333)
			 at android.view.View.layout(View.java:7035)
			 at android.widget.FrameLayout.onLayout(FrameLayout.java:333)
			 at android.view.View.layout(View.java:7035)
			 at android.widget.FrameLayout.onLayout(FrameLayout.java:333)
			 at android.view.View.layout(View.java:7035)
			 at android.widget.FrameLayout.onLayout(FrameLayout.java:333)
			 at android.view.View.layout(View.java:7035)
			 at android.view.ViewRoot.performTraversals(ViewRoot.java:1049)
			 at android.view.ViewRoot.handleMessage(ViewRoot.java:1731)
			 at android.os.Handler.dispatchMessage(Handler.java:99)
			 at android.os.Looper.loop(Looper.java:123)
			 at android.app.ActivityThread.main(ActivityThread.java:4627)
			 at java.lang.reflect.Method.invokeNative(Native Method)
			 at java.lang.reflect.Method.invoke(Method.java:521)
			 at com.android.internal.os.ZygoteInit$MethodAndArgsCaller.run(ZygoteInit.java:858)
			 at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:616)
			 at dalvik.system.NativeStart.main(Native Method)
			 */
		}
	}
}
