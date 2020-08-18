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

package com.biglybt.android.adapter;

import com.biglybt.android.client.AndroidUtils;
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import androidx.annotation.Nullable;

import android.util.AttributeSet;
import android.util.Log;
import android.view.*;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.OrientationHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

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
		ItemAnimator itemAnimator = getItemAnimator();
		if (itemAnimator instanceof SimpleItemAnimator) {
			((SimpleItemAnimator) itemAnimator).setSupportsChangeAnimations(false);
		}
		if (itemAnimator != null) {
			itemAnimator.setChangeDuration(0);
			//itemAnimator.setAddDuration(0);
			//itemAnimator.setRemoveDuration(0);
			//itemAnimator.setMoveDuration(0);
		}

		// API 15 with android:animateLayoutChanges will cause:
		//  W/RecyclerView: RecyclerView does not support scrolling to an absolute position. Use scrollToPosition instead
		// AND API 15, 22:
		//   java.lang.IllegalArgumentException: Scrapped or attached views may not be recycled. isScrap:false isAttached:true
		//			setLayoutTransition(null);

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
	@Override
	protected void onMeasure(int widthSpec, int heightSpec) {
		try {
			super.onMeasure(widthSpec, heightSpec);
		} catch (Throwable ignore) {
			// IndexOutOfBoundsException, Android 6.0, appcompat 26.1.0
		}
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

		/* Fix for Sony Bravia TV API 24 (and others)
		 * If the view is not visible, don't focus on its items
		 */
		if (getVisibility() != View.VISIBLE) {
			/* Some devices (correctly) don't even call requestFocus when visibility is not visible */
			// super.requestFocus(direction, previouslyFocusedRect) will focus a child
			ViewParent parent = getParent();
			if (parent instanceof ViewGroup) {
				ViewGroup viewGroup = (ViewGroup) parent;
				int childCount = viewGroup.getChildCount();
				int i = viewGroup.indexOfChild(this);
				if (AndroidUtils.DEBUG_ADAPTER) {
					log("requestFocus " + Integer.toHexString(direction) + " cur idx "
							+ i);
				}
				if (direction == View.FOCUS_UP) {
					i--;
				} else if (direction == View.FOCUS_DOWN) {
					i++;
				} else {
					// NOT HANDLED
					return super.requestFocus(direction, previouslyFocusedRect);
				}

				if (i >= 0 && i < childCount) {
					View childAt = viewGroup.getChildAt(i);
					if (AndroidUtils.DEBUG_ADAPTER) {
						log("requestFocus " + Integer.toHexString(direction) + " is now "
								+ childAt);
					}
					if (childAt != null) {
						return childAt.requestFocus();
					}
				} else {
					if (AndroidUtils.DEBUG_ADAPTER) {
						log("requestFocus " + Integer.toHexString(direction) + " idx " + i
								+ " OOB");
					}

				}
			}
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
			ViewHolder viewHolder;
			boolean ok = false;
			do {
				viewHolder = findViewHolderForAdapterPosition(selectedPosition);
				if (AndroidUtils.DEBUG_ADAPTER) {
					log("requestFocus VH=" + viewHolder);
				}
				if (viewHolder == null) {
					break;
				}
				ok = viewHolder.itemView.isFocusable();
				if (!ok) {
					if (AndroidUtils.DEBUG_ADAPTER) {
						log("requestFocus not focusable, trying next");
					}
					selectedPosition++;
					viewHolder = findViewHolderForAdapterPosition(selectedPosition);
				}
			} while (!ok);

			if (ok) {
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
				assert layoutManager != null;
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
				assert layoutManager != null;
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
		if (layoutManager == null) {
			return NO_POSITION;
		}
		final View child = findOneVisibleChild(0, layoutManager.getChildCount(),
				false, true);
		return child == null ? NO_POSITION : getChildAdapterPosition(child);
	}

	/**
	 * Returns the adapter position of the first fully visible view. This position does not include
	 * adapter changes that were dispatched after the last layout pass.
	 *
	 * @return The adapter position of the first fully visible item or
	 * {@link RecyclerView#NO_POSITION} if there aren't any visible items.
	 */
	@SuppressWarnings("unused")
	public int findFirstCompletelyVisibleItemPosition() {
		LayoutManager layoutManager = getLayoutManager();
		if (layoutManager == null) {
			return NO_POSITION;
		}
		final View child = findOneVisibleChild(0, layoutManager.getChildCount(),
				true, false);
		return child == null ? NO_POSITION : getChildAdapterPosition(child);
	}

	/**
	 * Returns the adapter position of the last visible view. This position does not include
	 * adapter changes that were dispatched after the last layout pass.
	 *
	 * @return The adapter position of the last visible view or {@link RecyclerView#NO_POSITION} if
	 * there aren't any visible items
	 */
	@SuppressWarnings("unused")
	public int findLastVisibleItemPosition() {
		LayoutManager layoutManager = getLayoutManager();
		if (layoutManager == null) {
			return NO_POSITION;
		}
		final View child = findOneVisibleChild(layoutManager.getChildCount() - 1,
				-1, false, true);
		return child == null ? NO_POSITION : getChildAdapterPosition(child);
	}

	/**
	 * Returns the adapter position of the last fully visible view. This position does not include
	 * adapter changes that were dispatched after the last layout pass.
	 *
	 * @return The adapter position of the last fully visible view or
	 * {@link RecyclerView#NO_POSITION} if there aren't any visible items.
	 */
	@SuppressWarnings("unused")
	public int findLastCompletelyVisibleItemPosition() {
		LayoutManager layoutManager = getLayoutManager();
		if (layoutManager == null) {
			return NO_POSITION;
		}
		final View child = findOneVisibleChild(layoutManager.getChildCount() - 1,
				-1, true, false);
		return child == null ? NO_POSITION : getChildAdapterPosition(child);
	}

	private View findOneVisibleChild(int fromIndex, int toIndex,
			boolean completelyVisible, boolean acceptPartiallyVisible) {
		OrientationHelper helper;
		LayoutManager layoutManager = getLayoutManager();
		if (layoutManager == null) {
			return null;
		}
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

	/*
	@Override
	protected void onScrollChanged(int l, int t, int oldl, int oldt) {
		Log.d(TAG, "onScrollChanged: l=" + l + ";t="+ t + ";" + AndroidUtils.getCompressedStackTrace());
		super.onScrollChanged(l, t, oldl, oldt);
	}
	
	@Override
	public void onScrolled(int dx, int dy) {
		Log.d(TAG, "onScrolled: " + dy + ";" + AndroidUtils.getCompressedStackTrace());
		super.onScrolled(dx, dy);
	}
	
	@Override
	public void smoothScrollToPosition(int position) {
		Log.d(TAG, "smoothScrollToPosition: " + position+ ";" + AndroidUtils.getCompressedStackTrace());
		super.smoothScrollToPosition(position);
	}
	
	@Override
	public void scrollToPosition(int position) {
		Log.d(TAG, "scrollToPosition: " +position + ";" + AndroidUtils.getCompressedStackTrace());
		super.scrollToPosition(position);
	}
	
	@Override
	public void scrollBy(int x, int y) {
		Log.d(TAG, "scrollBy: " + x + "," + y + ";" + AndroidUtils.getCompressedStackTrace());
		super.scrollBy(x, y);
	}
	
	/**/

	@Override
	public void scrollTo(int x, int y) {
		//Log.d(TAG, "scrollTo: " + x + "," + y + ";" + AndroidUtils.getCompressedStackTrace());
		try {
			super.scrollTo(x, y);
		} catch (Throwable t) {
			// Android 4.0.4/API 15 with android:animateLayoutChanges
			Log.e(TAG, "scrollTo; Exception ignored", t);
		}
	}

	@Override
	public void setAdapter(Adapter adapter) {
		if (adapter == getAdapter()) {
			return;
		}
		super.setAdapter(adapter);
	}

	@SuppressLint("ClickableViewAccessibility")
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

	@SuppressLint("LogConditional")
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
		//noinspection CatchMayIgnoreException
		try {
			super.onLayout(changed, l, t, r, b);
		} catch (NullPointerException | IndexOutOfBoundsException ignore) {
			Log.e(TAG, "onLayout: ", ignore);
			/* API 8:
			 05-10 12:38:09.499 2157-2157/com.vuze.android.client E/AndroidRuntime: FATAL EXCEPTION: main
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
			/* Found in wild:
			  java.lang.IndexOutOfBoundsException: Inconsistency detected. Invalid view holder adapter positionSideActionsHolder{a4cb7b7 position=12 id=2131361855, oldPos=1, pLpos:1 scrap [attachedScrap] tmpDetached no parent} com.biglybt.android.adapter.FlexibleRecyclerView{4baeede VFED..... ......ID 0,84-560,638 #7f0a0328 app:id/sideactions_list}, adapter:com.biglybt.android.client.sidelist.SideActionsAdapter@9e4b71e, layout:com.biglybt.android.widget.PreCachingLayoutManager@2191fe, context:com.biglybt.android.client.activity.TorrentViewActivity@666c074
        at androidx.recyclerview.widget.RecyclerView$Recycler.validateViewHolderForOffsetPosition(RecyclerView.java:6087)
        at androidx.recyclerview.widget.RecyclerView$Recycler.tryGetViewHolderForPositionByDeadline(RecyclerView.java:6270)
        at androidx.recyclerview.widget.RecyclerView$Recycler.getViewForPosition(RecyclerView.java:6231)
        at androidx.recyclerview.widget.RecyclerView$Recycler.getViewForPosition(RecyclerView.java:6227)
        at androidx.recyclerview.widget.LinearLayoutManager$LayoutState.next(LinearLayoutManager.java:2330)
        at androidx.recyclerview.widget.LinearLayoutManager.layoutChunk(LinearLayoutManager.java:1631)
        at androidx.recyclerview.widget.LinearLayoutManager.fill(LinearLayoutManager.java:1591)
        at androidx.recyclerview.widget.LinearLayoutManager.onLayoutChildren(LinearLayoutManager.java:668)
        at androidx.recyclerview.widget.RecyclerView.dispatchLayoutStep2(RecyclerView.java:4230)
        at androidx.recyclerview.widget.RecyclerView.dispatchLayout(RecyclerView.java:3947)
        at androidx.recyclerview.widget.RecyclerView.onLayout(RecyclerView.java:4499)
        at com.biglybt.android.adapter.FlexibleRecyclerView.onLayout(FlexibleRecyclerView.java:464)
			 */
		}
	}
}
