/*******************************************************************************
 * Copyright 2011, 2012 Chris Banes.
 * Copyright 2013 Naver Business Platform Corp.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.handmark.pulltorefresh.library;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.handmark.pulltorefresh.configuration.xml.PullToRefreshXmlConfiguration;
import com.handmark.pulltorefresh.library.internal.LoadingLayout;
import com.handmark.pulltorefresh.library.internal.Utils;
import com.handmark.pulltorefresh.library.internal.ViewCompat;

public abstract class PullToRefreshBase<T extends View> extends LinearLayout implements IPullToRefresh<T> {

	// ===========================================================
	// Constants
	// ===========================================================

	static final boolean DEBUG = false;

	static final boolean USE_HW_LAYERS = false;

	static final String LOG_TAG = "PullToRefresh";

	public static final float DEFAULT_FRICTION = 2.0f;
	public static final int DEFAULT_SMOOTH_SCROLL_DURATION_MS = 200;
	public static final int DEFAULT_SMOOTH_SCROLL_LONG_DURATION_MS = 325;

	static final int DEMO_SCROLL_INTERVAL = 225;

	static final String STATE_STATE = "ptr_state";
	static final String STATE_MODE = "ptr_mode";
	static final String STATE_CURRENT_MODE = "ptr_current_mode";
	static final String STATE_SCROLLING_REFRESHING_ENABLED = "ptr_disable_scrolling";
	static final String STATE_SHOW_REFRESHING_VIEW = "ptr_show_refreshing_view";
	static final String STATE_SUPER = "ptr_super";

	static final int REFRESHABLEVIEW_REFRESHING_BAR_VIEW_WHILE_REFRESHING_DURATION = 100;
	static final int REFRESHABLE_VIEW_HIDE_WHILE_REFRESHING_DURATION = 500;
	static final int GOOGLE_STYLE_VIEW_APPEAREANCE_DURATION = 200;
	static final int DFEAULT_REFRESHABLEVIEW_REFRESHING_BAR_SIZE = ViewGroup.LayoutParams.WRAP_CONTENT;

	static final int LAYER_TYPE_HARDWARE = 2;
	static final int LAYER_TYPE_NONE = 0;
	// ===========================================================
	// Fields
	// ===========================================================
	
	private int mTouchSlop;
	private float mLastMotionX, mLastMotionY;
	private float mInitialMotionX, mInitialMotionY;
	
	// needed properties while scrolling
	private float mFriction;
	private int mSmoothScrollDurationMs = 200;
	private int mSmoothScrollLongDurationMs = 325;
	
	private boolean mIsBeingDragged = false;
	private State mState = State.RESET;
	private Mode mMode = Mode.getDefault();

	private Mode mCurrentMode;
	T mRefreshableView;
	private FrameLayout mRefreshableViewWrapper;

	private boolean mShowViewWhileRefreshing = true;
	private boolean mScrollingWhileRefreshingEnabled = false;
	private boolean mFilterTouchEvents = true;
	private boolean mOverScrollEnabled = true;
	private boolean mLayoutVisibilityChangesEnabled = true;

	private Interpolator mScrollAnimationInterpolator;
	private Class<? extends LoadingLayout> mLoadingLayoutClazz = null;
	
	private LoadingLayout mHeaderLayout;
	private LoadingLayout mFooterLayout;

	/**
	 * Top DecorView for containing google style pull to refresh 
	 */
	private FrameLayout mTopActionbarLayout;
	/**
	 * Flag whether {@link #onAttachedToWindow()} event has been called
	 */	
	private boolean mWindowAttached = false;
	/**
	 * View Layout being shown over ActionBar
	 */	
	private GoogleStyleViewLayout mGoogleStyleViewLayout;
	/**
	 * Progress Bar being shown over ActionBar
	 */
	private GoogleStyleProgressLayout mGoogleStyleProgressLayout;	
	/**
	 * Progress bar ratating on center while Refreshing
	 */	
	private ProgressBar mRefreshableViewProgressBar;

	private OnRefreshListener<T> mOnRefreshListener;
	private OnRefreshListener2<T> mOnRefreshListener2;
	private OnPullEventListener<T> mOnPullEventListener;

	private SmoothScrollRunnable mCurrentSmoothScrollRunnable;

	private int mStatusBarHeight;
	/**
	 * Current actionbar size
	 */
	private int mActionBarHeight;
	/**
	 * Flag whether {@link #onRefreshing(boolean)} has been called
	 */
	private boolean mRefreshing;
	/**
	 * Flag whether Google style view layout appearance animation will be shown
	 */
	private boolean mShowGoogleStyleViewAnimationEnabled = true;
	/**
	 * Duration of Google style view layout appearance animation
	 */
	private int mShowGoogleStyleViewAnimationDuration = GOOGLE_STYLE_VIEW_APPEAREANCE_DURATION;
	/**
	 * Flag whether {@code mRefreshaleView} will be hidden while refreshing
	 */
	private boolean mRefeshableViewHideWhileRefreshingEnabled = true;
	/**
	 * {@code mRefreshableView}'s fade-out Duration
	 */
	private int mRefeshableViewHideWhileRefreshingDuration = REFRESHABLE_VIEW_HIDE_WHILE_REFRESHING_DURATION;
	/**
	 * Flag whether some {@code ProgressBar} will be shown while refreshing
	 */
	private boolean mRefeshableViewRefreshingBarViewWhileRefreshingEnabled = true;
	/**
	 * {@code mRefreshableViewRefreshingBar}'s fade-in Duration
	 */
	private int mRefeshableViewRefreshingBarViewWhileRefreshingDuration = REFRESHABLEVIEW_REFRESHING_BAR_VIEW_WHILE_REFRESHING_DURATION;
	/**
	 * Width of {@code mRefreshableViewRefreshingBar}
	 */
	private int mRefeshableViewRefreshingBarWidth = DFEAULT_REFRESHABLEVIEW_REFRESHING_BAR_SIZE;
	/**
	 * Height of {@code mRefreshableViewRefreshingBar}
	 */
	private int mRefeshableViewRefreshingBarHeight = DFEAULT_REFRESHABLEVIEW_REFRESHING_BAR_SIZE;	
	/**
	 * Flag whether Google style view layout's size is set to ActionBar's size 
	 * (Don't set to false as possible, it's hard to control height if this flag is false)
	 */
	private boolean mSetGoogleViewLayoutSizeToActionbarHeight = true;

	private int mYPositionOfGoogleStyleViewLayout;

	private int mYPositionOfGoogleStyleProgressLayout;
	// ===========================================================
	// Constructors
	// ===========================================================
	public PullToRefreshBase(Context context) {
		super(context);
		init(context, null);
	}

	public PullToRefreshBase(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context, attrs);
	}

	public PullToRefreshBase(Context context, Mode mode) {
		super(context);
		mMode = mode;
		init(context, null);
	}

	public PullToRefreshBase(Context context, Mode mode, Class<? extends LoadingLayout> loadingLayoutClazz) {
		super(context);
		mMode = mode;
		mLoadingLayoutClazz = loadingLayoutClazz;
		init(context, null);
	}
	
	@Override
	public void addView(View child, int index, ViewGroup.LayoutParams params) {
		if (DEBUG) {
			Log.d(LOG_TAG, "addView: " + child.getClass().getSimpleName());
		}

		final T refreshableView = getRefreshableView();

		if (refreshableView instanceof ViewGroup) {
			((ViewGroup) refreshableView).addView(child, index, params);
		} else {
			throw new UnsupportedOperationException("Refreshable View is not a ViewGroup so can't addView");
		}
	}
	
	@Deprecated
	@Override
	public final boolean demo() {
		if (mMode.showHeaderLoadingLayout() && isReadyForPullStart()) {
			smoothScrollToAndBack(-getHeaderSize() * 2);
			return true;
		} else if (mMode.showFooterLoadingLayout() && isReadyForPullEnd()) {
			smoothScrollToAndBack(getFooterSize() * 2);
			return true;
		}

		return false;
	}

	@Override
	public final Mode getCurrentMode() {
		return mCurrentMode;
	}

	@Override
	public final boolean getFilterTouchEvents() {
		return mFilterTouchEvents;
	}

	@Override
	public final ILoadingLayout getLoadingLayoutProxy() {
		return getLoadingLayoutProxy(true, true);
	}

	@Override
	public final ILoadingLayout getLoadingLayoutProxy(boolean includeStart, boolean includeEnd) {
		return createLoadingLayoutProxy(includeStart, includeEnd);
	}

	@Override
	public final Mode getMode() {
		return mMode;
	}

	@Override
	public final T getRefreshableView() {
		return mRefreshableView;
	}

	@Override
	public final boolean getShowViewWhileRefreshing() {
		return mShowViewWhileRefreshing;
	}

	@Override
	public final State getState() {
		return mState;
	}

	/**
	 * @deprecated See {@link #isScrollingWhileRefreshingEnabled()}.
	 */
	public final boolean isDisableScrollingWhileRefreshing() {
		return !isScrollingWhileRefreshingEnabled();
	}

	@Override
	public final boolean isPullToRefreshEnabled() {
		return mMode.permitsPullToRefresh();
	}

	@Override
	public final boolean isPullToRefreshOverScrollEnabled() {
		return VERSION.SDK_INT >= VERSION_CODES.GINGERBREAD && mOverScrollEnabled
				&& OverscrollHelper.isAndroidOverScrollEnabled(mRefreshableView);
	}

	@Override
	public final boolean isRefreshing() {
		return mState == State.REFRESHING || mState == State.MANUAL_REFRESHING;
	}

	@Override
	public final boolean isScrollingWhileRefreshingEnabled() {
		return mScrollingWhileRefreshingEnabled;
	}
	
	@Override
	public final boolean onInterceptTouchEvent(MotionEvent event) {

		if (!isPullToRefreshEnabled()) {
			return false;
		}

		final int action = event.getAction();

		if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
			mIsBeingDragged = false;
			return false;
		}

		if (action != MotionEvent.ACTION_DOWN && mIsBeingDragged) {
			return true;
		}

		switch (action) {
			case MotionEvent.ACTION_MOVE: {
				// If we're refreshing, and the flag is set. Eat all MOVE events
				if (!mScrollingWhileRefreshingEnabled && isRefreshing()) {
					return true;
				}

				if (isReadyForPull()) {
					final float y = event.getY(), x = event.getX();
					final float diff, oppositeDiff, absDiff;

					// We need to use the correct values, based on scroll
					// direction
					switch (getFilteredPullToRefreshScrollDirection()) {
						case HORIZONTAL:
							diff = x - mLastMotionX;
							oppositeDiff = y - mLastMotionY;
							break;
						case VERTICAL:
						default:
							diff = y - mLastMotionY;
							oppositeDiff = x - mLastMotionX;
							break;
					}
					absDiff = Math.abs(diff);

					if (absDiff > mTouchSlop && (!mFilterTouchEvents || absDiff > Math.abs(oppositeDiff))) {
						if ((mMode.showHeaderLoadingLayout() || mMode.showGoogleStyle()) && diff >= 1f && isReadyForPullStart()) {
							mLastMotionY = y;
							mLastMotionX = x;
							mIsBeingDragged = true;
							if (mMode == Mode.BOTH) {
								mCurrentMode = Mode.PULL_FROM_START;
							}
						} else if (mMode.showFooterLoadingLayout() && diff <= -1f && isReadyForPullEnd()) {
							mLastMotionY = y;
							mLastMotionX = x;
							mIsBeingDragged = true;
							if (mMode == Mode.BOTH) {
								mCurrentMode = Mode.PULL_FROM_END;
							}
						}
					}
				}
				break;
			}
			case MotionEvent.ACTION_DOWN: {
				if (isReadyForPull()) {
					mLastMotionY = mInitialMotionY = event.getY();
					mLastMotionX = mInitialMotionX = event.getX();
					mIsBeingDragged = false;
				}
				break;
			}
		}

		return mIsBeingDragged;
	}

	@Override
	public final void onRefreshComplete() {
		if (isRefreshing()) {
			setState(State.RESET);
		}
	}

	@Override
	public final boolean onTouchEvent(MotionEvent event) {

		if (!isPullToRefreshEnabled()) {
			return false;
		}

		// If we're refreshing, and the flag is set. Eat the event
		if (!mScrollingWhileRefreshingEnabled && isRefreshing()) {
			return true;
		}

		if (event.getAction() == MotionEvent.ACTION_DOWN && event.getEdgeFlags() != 0) {
			return false;
		}

		switch (event.getAction()) {
			case MotionEvent.ACTION_MOVE: {
				if (mIsBeingDragged) {
					mLastMotionY = event.getY();
					mLastMotionX = event.getX();
					pullEvent();
					return true;
				}
				break;
			}

			case MotionEvent.ACTION_DOWN: {
				if (isReadyForPull()) {
					mLastMotionY = mInitialMotionY = event.getY();
					mLastMotionX = mInitialMotionX = event.getX();
					return true;
				}
				break;
			}

			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP: {
				if (mIsBeingDragged) {
					mIsBeingDragged = false;

					if (mState == State.RELEASE_TO_REFRESH
							&& (null != mOnRefreshListener || null != mOnRefreshListener2)) {
						setState(State.REFRESHING, true);
						return true;
					}

					// If we're already refreshing, just scroll back to the top
					if (isRefreshing()) {
						smoothScrollTo(0);
						return true;
					}

					// If we haven't returned by here, then we're not in a state
					// to pull, so just reset
					setState(State.RESET);

					return true;
				}
				break;
			}
		}

		return false;
	}
	/**
	 * Set new friction
	 * @param friction New friction value. Must be float. The default value is {@value #DEFAULT_FRICTION}.
	 */
	public final void setFriction(float friction) {
		this.mFriction = friction;
	}
	/**
	 * Set new smooth scroll duration
	 * @param smoothScrollDurationMs Milliseconds. The default value is {@value #DEFAULT_SMOOTH_SCROLL_DURATION_MS}.
	 */
	public final void setSmoothScrollDuration(int smoothScrollDurationMs) {
		this.mSmoothScrollDurationMs = smoothScrollDurationMs;
	} 
	/**
	 * Set new smooth scroll <b>longer</b> duration. <br /> This duration is only used by calling {@link #smoothScrollToLonger(int)}. 
	 * @param smoothScrollLongDurationMs Milliseconds. The default value is {@value #DEFAULT_SMOOTH_SCROLL_LONG_DURATION_MS}.
	 */
	public final void setSmoothScrollLongDuration(int smoothScrollLongDurationMs) {
		this.mSmoothScrollLongDurationMs = smoothScrollLongDurationMs;
	} 
	/**
	 * 
	 */
	public final void setScrollingWhileRefreshingEnabled(boolean allowScrollingWhileRefreshing) {
		mScrollingWhileRefreshingEnabled = allowScrollingWhileRefreshing;
	}
	/**
	 * @deprecated See {@link #setScrollingWhileRefreshingEnabled(boolean)}
	 */
	public void setDisableScrollingWhileRefreshing(boolean disableScrollingWhileRefreshing) {
		setScrollingWhileRefreshingEnabled(!disableScrollingWhileRefreshing);
	}

	@Override
	public final void setFilterTouchEvents(boolean filterEvents) {
		mFilterTouchEvents = filterEvents;
	}

	/**
	 * @deprecated You should now call this method on the result of
	 *             {@link #getLoadingLayoutProxy()}.
	 */
	public void setLastUpdatedLabel(CharSequence label) {
		getLoadingLayoutProxy().setLastUpdatedLabel(label);
	}

	/**
	 * @deprecated You should now call this method on the result of
	 *             {@link #getLoadingLayoutProxy()}.
	 */
	public void setLoadingDrawable(Drawable drawable) {
		getLoadingLayoutProxy().setLoadingDrawable(drawable);
	}

	/**
	 * @deprecated You should now call this method on the result of
	 *             {@link #getLoadingLayoutProxy(boolean, boolean)}.
	 */
	public void setLoadingDrawable(Drawable drawable, Mode mode) {
		getLoadingLayoutProxy(mode.showHeaderLoadingLayout(), mode.showFooterLoadingLayout()).setLoadingDrawable(
				drawable);
	}

	@Override
	public void setLongClickable(boolean longClickable) {
		getRefreshableView().setLongClickable(longClickable);
	}

	@Override
	public final void setMode(Mode mode) {
		if (mode != mMode) {
			if (DEBUG) {
				Log.d(LOG_TAG, "Setting mode to: " + mode);
			}
			mMode = mode;
			updateUIForMode();
			updateUIForGoogleStyleMode();
		}
	}

	public void setOnPullEventListener(OnPullEventListener<T> listener) {
		mOnPullEventListener = listener;
	}

	@Override
	public final void setOnRefreshListener(OnRefreshListener<T> listener) {
		mOnRefreshListener = listener;
		mOnRefreshListener2 = null;
	}

	@Override
	public final void setOnRefreshListener(OnRefreshListener2<T> listener) {
		mOnRefreshListener2 = listener;
		mOnRefreshListener = null;
	}

	/**
	 * @deprecated You should now call this method on the result of
	 *             {@link #getLoadingLayoutProxy()}.
	 */
	public void setPullLabel(CharSequence pullLabel) {
		getLoadingLayoutProxy().setPullLabel(pullLabel);
	}

	/**
	 * @deprecated You should now call this method on the result of
	 *             {@link #getLoadingLayoutProxy(boolean, boolean)}.
	 */
	public void setPullLabel(CharSequence pullLabel, Mode mode) {
		getLoadingLayoutProxy(mode.showHeaderLoadingLayout(), mode.showFooterLoadingLayout()).setPullLabel(pullLabel);
	}

	/**
	 * @param enable Whether Pull-To-Refresh should be used
	 * @deprecated This simple calls setMode with an appropriate mode based on
	 *             the passed value.
	 */
	public final void setPullToRefreshEnabled(boolean enable) {
		setMode(enable ? Mode.getDefault() : Mode.DISABLED);
	}

	@Override
	public final void setPullToRefreshOverScrollEnabled(boolean enabled) {
		mOverScrollEnabled = enabled;
	}

	@Override
	public final void setRefreshing() {
		setRefreshing(true);
	}

	@Override
	public final void setRefreshing(boolean doScroll) {
		if (!isRefreshing()) {
			setState(State.MANUAL_REFRESHING, doScroll);
		}
	}

	/**
	 * @deprecated You should now call this method on the result of
	 *             {@link #getLoadingLayoutProxy()}.
	 */
	public void setRefreshingLabel(CharSequence refreshingLabel) {
		getLoadingLayoutProxy().setRefreshingLabel(refreshingLabel);
	}

	/**
	 * @deprecated You should now call this method on the result of
	 *             {@link #getLoadingLayoutProxy(boolean, boolean)}.
	 */
	public void setRefreshingLabel(CharSequence refreshingLabel, Mode mode) {
		getLoadingLayoutProxy(mode.showHeaderLoadingLayout(), mode.showFooterLoadingLayout()).setRefreshingLabel(
				refreshingLabel);
	}

	/**
	 * @deprecated You should now call this method on the result of
	 *             {@link #getLoadingLayoutProxy()}.
	 */
	public void setReleaseLabel(CharSequence releaseLabel) {
		setReleaseLabel(releaseLabel, Mode.BOTH);
	}

	/**
	 * @deprecated You should now call this method on the result of
	 *             {@link #getLoadingLayoutProxy(boolean, boolean)}.
	 */
	public void setReleaseLabel(CharSequence releaseLabel, Mode mode) {
		getLoadingLayoutProxy(mode.showHeaderLoadingLayout(), mode.showFooterLoadingLayout()).setReleaseLabel(
				releaseLabel);
	}

	public void setScrollAnimationInterpolator(Interpolator interpolator) {
		mScrollAnimationInterpolator = interpolator;
	}

	@Override
	public final void setShowViewWhileRefreshing(boolean showView) {
		mShowViewWhileRefreshing = showView;
	}

	/**
	 * @return Either {@link Orientation#VERTICAL} or
	 *         {@link Orientation#HORIZONTAL} depending on the scroll direction.
	 */
	public abstract Orientation getPullToRefreshScrollDirection();
	/**
	 * <p>
	 * Wrap {@link #getPullToRefreshScrollDirection()} method <br />
	 * Other methods Use this method instead of {@link #getPullToRefreshScrollDirection()} method, because an orientation must be VERTICAL when mode is google style
	 * </p>
	 * @return Oreintation.VERTICAL if mMode.showGoogleStyle() is true,<br />Return value of {@link #getPullToRefreshScrollDirection()} method if else 
	 */
	public final Orientation getFilteredPullToRefreshScrollDirection() {
		Orientation orientation = getPullToRefreshScrollDirection();
		if (mMode.showGoogleStyle() ) {
			orientation = Orientation.VERTICAL;
		}
		return orientation;
	}

	final void setState(State state, final boolean... params) {
		mState = state;
		if (DEBUG) {
			Log.d(LOG_TAG, "State: " + mState.name());
		}

		switch (mState) {
			case RESET:
				onReset();
				break;
			case PULL_TO_REFRESH:
				onPullToRefresh();
				break;
			case RELEASE_TO_REFRESH:
				onReleaseToRefresh();
				break;
			case REFRESHING:
			case MANUAL_REFRESHING:
				onRefreshing(params[0]);
				break;
			case OVERSCROLLING:
				// NO-OP
				break;
		}

		// Call OnPullEventListener
		if (null != mOnPullEventListener) {
			mOnPullEventListener.onPullEvent(this, mState, mCurrentMode);
		}
	}

	/**
	 * Used internally for adding view. Need because we override addView to
	 * pass-through to the Refreshable View
	 */
	protected final void addViewInternal(View child, int index, ViewGroup.LayoutParams params) {
		super.addView(child, index, params);
	}

	/**
	 * Used internally for adding view. Need because we override addView to
	 * pass-through to the Refreshable View
	 */
	protected final void addViewInternal(View child, ViewGroup.LayoutParams params) {
		super.addView(child, -1, params);
	}

	/**
	 * Create a new loading layout instance by using the class token {@link #mLoadingLayoutClazz}
	 * @param context
	 * @param mode
	 * @param attrs
	 * @return Loading layout instance which was created by using the class token {@link #mLoadingLayoutClazz}
	 */
	protected LoadingLayout createLoadingLayout(Context context, Mode mode, TypedArray attrs) {
		return LoadingLayoutFactory.createLoadingLayout(mLoadingLayoutClazz, context, mode, getFilteredPullToRefreshScrollDirection(), attrs);
	}
	/**
	 * Create a new google style view layout instance by using the class token 
	 * @param layoutCode Google style view layout code to be converted to some class token 
	 * @param context
	 * @param mode
	 * @param attrs
	 * @return Google style <b>view</b> layout instance which was created by using the class token
	 */
	private GoogleStyleViewLayout createGoogleStyleViewLayout(
			String layoutCode, Context context, TypedArray a) {
		Class<? extends GoogleStyleViewLayout> clazz = GoogleStyleViewLayoutFactory.createGoogleStyleViewLayoutClazzByLayoutCode(layoutCode);
		return GoogleStyleViewLayoutFactory.createGoogleStyleViewLayout(clazz, context, a);
	}	
	/**
	 * Create a new google style progress layout instance by using the class token 
	 * @param layoutCode google style progress layout code  to be converted to some class token 
	 * @param context
	 * @param mode
	 * @param attrs
	 * @return Google style <b>progress</b> layout instance which was created by using the class token
	 */
	private GoogleStyleProgressLayout createGoogleStyleProgressLayout(
			String layoutCode, Context context, TypedArray a) {
		Class<? extends GoogleStyleProgressLayout> clazz = GoogleStyleProgressLayoutFactory.createGoogleStyleProgressLayoutClazzByLayoutCode(layoutCode);
		return GoogleStyleProgressLayoutFactory.createGoogleStyleProgressLayout(clazz, context, a);
	}
	/**
	 * Used internally for {@link #getLoadingLayoutProxy(boolean, boolean)}.
	 * Allows derivative classes to include any extra LoadingLayouts.
	 */
	protected LoadingLayoutProxy createLoadingLayoutProxy(final boolean includeStart, final boolean includeEnd) {
		LoadingLayoutProxy proxy = new LoadingLayoutProxy();

		if (includeStart && mMode.showHeaderLoadingLayout()) {
			proxy.addLayout(mHeaderLayout);
		}
		if (includeEnd && mMode.showFooterLoadingLayout()) {
			proxy.addLayout(mFooterLayout);
		}

		return proxy;
	}

	/**
	 * This is implemented by derived classes to return the created View. If you
	 * need to use a custom View (such as a custom ListView), override this
	 * method and return an instance of your custom class.
	 * <p/>
	 * Be sure to set the ID of the view in this method, especially if you're
	 * using a ListActivity or ListFragment.
	 * 
	 * @param context Context to create view with
	 * @param attrs AttributeSet from wrapped class. Means that anything you
	 *            include in the XML layout declaration will be routed to the
	 *            created View
	 * @return New instance of the Refreshable View
	 */
	protected abstract T createRefreshableView(Context context, AttributeSet attrs);

	protected final void disableLoadingLayoutVisibilityChanges() {
		mLayoutVisibilityChangesEnabled = false;
	}

	protected final LoadingLayout getFooterLayout() {
		return mFooterLayout;
	}

	protected final int getFooterSize() {
		return mFooterLayout.getContentSize();
	}

	protected final LoadingLayout getHeaderLayout() {
		return mHeaderLayout;
	}

	protected final int getHeaderSize() {
		return mHeaderLayout.getContentSize();
	}

	protected final int getGoogleStyleViewSize() {
		return mGoogleStyleViewLayout.getContentSize();
	}

	protected int getPullToRefreshScrollDuration() {
		return mSmoothScrollDurationMs;
	}

	protected int getPullToRefreshScrollDurationLonger() {
		return mSmoothScrollLongDurationMs;
	}

	protected FrameLayout getRefreshableViewWrapper() {
		return mRefreshableViewWrapper;
	}

	/**
	 * Allows Derivative classes to handle the XML Attrs without creating a
	 * TypedArray themsevles
	 * 
	 * @param a - TypedArray of PullToRefresh Attributes
	 */
	protected void handleStyledAttributes(TypedArray a) {
	}

	/**
	 * Implemented by derived class to return whether the View is in a state
	 * where the user can Pull to Refresh by scrolling from the end.
	 * 
	 * @return true if the View is currently in the correct state (for example,
	 *         bottom of a ListView)
	 */
	protected abstract boolean isReadyForPullEnd();

	/**
	 * Implemented by derived class to return whether the View is in a state
	 * where the user can Pull to Refresh by scrolling from the start.
	 * 
	 * @return true if the View is currently the correct state (for example, top
	 *         of a ListView)
	 */
	protected abstract boolean isReadyForPullStart();

	/**
	 * Called by {@link #onRestoreInstanceState(Parcelable)} so that derivative
	 * classes can handle their saved instance state.
	 * 
	 * @param savedInstanceState - Bundle which contains saved instance state.
	 */
	protected void onPtrRestoreInstanceState(Bundle savedInstanceState) {
	}

	/**
	 * Called by {@link #onSaveInstanceState()} so that derivative classes can
	 * save their instance state.
	 * 
	 * @param saveState - Bundle to be updated with saved state.
	 */
	protected void onPtrSaveInstanceState(Bundle saveState) {
	}

	/**
	 * Called when the UI has been to be updated to be in the
	 * {@link State#PULL_TO_REFRESH} state.
	 */
	protected void onPullToRefresh() {
		switch (mCurrentMode) {
			case PULL_FROM_END:
				mFooterLayout.pullToRefresh();
				break;
			case GOOGLE_STYLE:
				showViewTopLayout();
				mGoogleStyleViewLayout.pullToRefresh();
				mGoogleStyleProgressLayout.pullToRefresh();
				break;
			case PULL_FROM_START:
				mHeaderLayout.pullToRefresh();
				break;
			default:
				// NO-OP
				break;
		}
	}

	/**
	 * Called when the UI has been to be updated to be in the
	 * {@link State#REFRESHING} or {@link State#MANUAL_REFRESHING} state.
	 * 
	 * @param doScroll - Whether the UI should scroll for this event.
	 */
	protected void onRefreshing(final boolean doScroll) {
		// Set the flag as below for fade-in animation of mRefreshableView when releasing 
		mRefreshing = true;

		if (mMode.showHeaderLoadingLayout()) {
			mHeaderLayout.refreshing();
		}
		if (mMode.showFooterLoadingLayout()) {
			mFooterLayout.refreshing();
		}
		if (mMode.showGoogleStyle()) {
			// Fade-out mRefreshableView
			if ( mRefeshableViewHideWhileRefreshingEnabled == true ) {
				AlphaAnimator.fadeout(mRefreshableView, mRefeshableViewHideWhileRefreshingDuration);	
			}
			// Fade-in refreshing bar on center
			if (mRefeshableViewRefreshingBarViewWhileRefreshingEnabled == true ) {
				mRefreshableViewProgressBar.setVisibility(View.VISIBLE);
				AlphaAnimator.fadein(mRefreshableViewProgressBar, mRefeshableViewRefreshingBarViewWhileRefreshingDuration);
			}

			mGoogleStyleViewLayout.refreshing();
			mGoogleStyleProgressLayout.refreshing();
		}

		if (doScroll) {
			if (mShowViewWhileRefreshing) {

				// Call Refresh Listener when the Scroll has finished
				OnSmoothScrollFinishedListener listener = new OnSmoothScrollFinishedListener() {
					@Override
					public void onSmoothScrollFinished() {
						callRefreshListener();
					}
				};

				switch (mCurrentMode) {
					case MANUAL_REFRESH_ONLY:
					case PULL_FROM_END:
						smoothScrollTo(getFooterSize(), listener);
						break;
					default:
					case PULL_FROM_START:
						smoothScrollTo(-getHeaderSize(), listener);
						break;
				}
			} else {
				smoothScrollTo(0);
			}
		} else {
			// We're not scrolling, so just call Refresh Listener now
			callRefreshListener();
		}
	}

	/**
	 * Called when the UI has been to be updated to be in the
	 * {@link State#RELEASE_TO_REFRESH} state.
	 */
	protected void onReleaseToRefresh() {
		switch (mCurrentMode) {
			case PULL_FROM_END:
				mFooterLayout.releaseToRefresh();
				break;
			case GOOGLE_STYLE:
				mGoogleStyleViewLayout.releaseToRefresh();
				mGoogleStyleProgressLayout.releaseToRefresh();
				break;
			case PULL_FROM_START:
				mHeaderLayout.releaseToRefresh();
				break;
			default:
				// NO-OP
				break;
		}
	}

	/**
	 * Called when the UI has been to be updated to be in the
	 * {@link State#RESET} state.
	 */
	protected void onReset() {
		mIsBeingDragged = false;
		mLayoutVisibilityChangesEnabled = true;

		// Always reset both layouts, just in case...
		mHeaderLayout.reset();
		mFooterLayout.reset();
		if (mMode.showGoogleStyle()) {
			mGoogleStyleViewLayout.reset();
			hideViewTopLayout();
			mGoogleStyleProgressLayout.reset();

			// Fade-in mRefreshableView
			if ( mRefreshing == true && mRefeshableViewHideWhileRefreshingEnabled == true ) {
				mRefreshableView.clearAnimation();
				AlphaAnimator.fadein(mRefreshableView, mRefeshableViewHideWhileRefreshingDuration);
			}
			// Fade-out refreshing bar on center
			if (mRefeshableViewRefreshingBarViewWhileRefreshingEnabled == true ) {
				AlphaAnimator.fadeout(mRefreshableViewProgressBar, mRefeshableViewRefreshingBarViewWhileRefreshingDuration, new AnimationListener(){
					@Override
					public void onAnimationEnd(Animation animation) {
						mRefreshableViewProgressBar.setVisibility(View.INVISIBLE);					
					}

					@Override
					public void onAnimationRepeat(Animation animation) {
						// do nothing
					}

					@Override
					public void onAnimationStart(Animation animation) {
						// do nothing
					}});	
			}
		}

		mRefreshing = false;
		smoothScrollTo(0);
	}

	@Override
	protected final void onRestoreInstanceState(Parcelable state) {
		if (state instanceof Bundle) {
			Bundle bundle = (Bundle) state;

			setMode(Mode.mapIntToValue(bundle.getInt(STATE_MODE, 0)));
			mCurrentMode = Mode.mapIntToValue(bundle.getInt(STATE_CURRENT_MODE, 0));

			mScrollingWhileRefreshingEnabled = bundle.getBoolean(STATE_SCROLLING_REFRESHING_ENABLED, false);
			mShowViewWhileRefreshing = bundle.getBoolean(STATE_SHOW_REFRESHING_VIEW, true);

			// Let super Restore Itself
			super.onRestoreInstanceState(bundle.getParcelable(STATE_SUPER));

			State viewState = State.mapIntToValue(bundle.getInt(STATE_STATE, 0));
			if (viewState == State.REFRESHING || viewState == State.MANUAL_REFRESHING) {
				setState(viewState, true);
			}

			// Now let derivative classes restore their state
			onPtrRestoreInstanceState(bundle);
			return;
		}

		super.onRestoreInstanceState(state);
	}

	@Override
	protected final Parcelable onSaveInstanceState() {
		Bundle bundle = new Bundle();

		// Let derivative classes get a chance to save state first, that way we
		// can make sure they don't overrite any of our values
		onPtrSaveInstanceState(bundle);

		bundle.putInt(STATE_STATE, mState.getIntValue());
		bundle.putInt(STATE_MODE, mMode.getIntValue());
		bundle.putInt(STATE_CURRENT_MODE, mCurrentMode.getIntValue());
		bundle.putBoolean(STATE_SCROLLING_REFRESHING_ENABLED, mScrollingWhileRefreshingEnabled);
		bundle.putBoolean(STATE_SHOW_REFRESHING_VIEW, mShowViewWhileRefreshing);
		bundle.putParcelable(STATE_SUPER, super.onSaveInstanceState());

		return bundle;
	}

	@Override
	protected final void onSizeChanged(int w, int h, int oldw, int oldh) {
		if (DEBUG) {
			Log.d(LOG_TAG, String.format("onSizeChanged. W: %d, H: %d", w, h));
		}

		super.onSizeChanged(w, h, oldw, oldh);

		// We need to update the header/footer when our size changes
		refreshLoadingViewsSize();

		// Update the Refreshable View layout
		refreshRefreshableViewSize(w, h);

		/**
		 * As we're currently in a Layout Pass, we need to schedule another one
		 * to layout any changes we've made here
		 */
		post(new Runnable() {
			@Override
			public void run() {
				requestLayout();
			}
		});
	}

	/**
	 * Re-measure the Loading Views height, and adjust internal padding as
	 * necessary
	 */
	protected final void refreshLoadingViewsSize() {
		final int maximumPullScroll = (int) (getMaximumPullScroll() * 1.2f);

		int pLeft = getPaddingLeft();
		int pTop = getPaddingTop();
		int pRight = getPaddingRight();
		int pBottom = getPaddingBottom();

		switch (getFilteredPullToRefreshScrollDirection()) {
			case HORIZONTAL:
				if (mMode.showHeaderLoadingLayout()) {
					mHeaderLayout.setWidth(maximumPullScroll);
					pLeft = -maximumPullScroll;
				} else {
					pLeft = 0;
				}

				if (mMode.showFooterLoadingLayout()) {
					mFooterLayout.setWidth(maximumPullScroll);
					pRight = -maximumPullScroll;
				} else {
					pRight = 0;
				}
				break;

			case VERTICAL:
				if (mMode.showHeaderLoadingLayout()) {
					mHeaderLayout.setHeight(maximumPullScroll);
					pTop = -maximumPullScroll;
				} else if (mMode.showGoogleStyle() && mWindowAttached == true ) {
					/**
					 * Set size of {@code GoogleStyleViewLayout} to ActionBar's size if {@code mSetGoogleViewLayoutSizeToActionbarHeight} is true
					 * This code is a default action, but if you want to use custom size of {@code GoogleStyleViewLayout}, set {@code ptrSetGoogleViewLayoutSizeToActionbarHeight} to false in layout xml (but not recommended).
					 */
					if (mSetGoogleViewLayoutSizeToActionbarHeight == true) {
						mGoogleStyleViewLayout.setHeight(mActionBarHeight);
					}

					pTop = 0;
				} else {
					pTop = 0;
				}

				if (mMode.showFooterLoadingLayout()) {
					mFooterLayout.setHeight(maximumPullScroll);
					pBottom = -maximumPullScroll;
				} else {
					pBottom = 0;
				}
				break;
		}

		if (DEBUG) {
			Log.d(LOG_TAG, String.format("Setting Padding. L: %d, T: %d, R: %d, B: %d", pLeft, pTop, pRight, pBottom));
		}
		setPadding(pLeft, pTop, pRight, pBottom);
	}

	protected final void refreshRefreshableViewSize(int width, int height) {
		// We need to set the Height of the Refreshable View to the same as
		// this layout
		LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mRefreshableViewWrapper.getLayoutParams();

		switch (getFilteredPullToRefreshScrollDirection()) {
			case HORIZONTAL:
				if (lp.width != width) {
					lp.width = width;
					mRefreshableViewWrapper.requestLayout();
				}
				break;
			case VERTICAL:
				if (lp.height != height) {
					lp.height = height;
					mRefreshableViewWrapper.requestLayout();
				}
				break;
		}
	}

	/**
	 * Helper method which just calls scrollTo() in the correct scrolling
	 * direction.
	 * 
	 * @param value - New Scroll value
	 */
	protected final void setHeaderScroll(int value) {
		if (DEBUG) {
			Log.d(LOG_TAG, "setHeaderScroll: " + value);
		}

		// Clamp value to with pull scroll range
		final int maximumPullScroll = getMaximumPullScroll();
		value = Math.min(maximumPullScroll, Math.max(-maximumPullScroll, value));

		if (mLayoutVisibilityChangesEnabled) {
			if (value < 0) {
				switch (mCurrentMode) {
					case GOOGLE_STYLE:
						mGoogleStyleViewLayout.setVisibility(View.VISIBLE);
						break;
					default:	
					case PULL_FROM_START:
						mHeaderLayout.setVisibility(View.VISIBLE);
						break;
				}
			} else if (value > 0) {
				mFooterLayout.setVisibility(View.VISIBLE);
			} else {
				mHeaderLayout.setVisibility(View.INVISIBLE);
				mFooterLayout.setVisibility(View.INVISIBLE);
			}
		}

		if (USE_HW_LAYERS) {
			/**
			 * Use a Hardware Layer on the Refreshable View if we've scrolled at
			 * all. We don't use them on the Header/Footer Views as they change
			 * often, which would negate any HW layer performance boost.
			 */
			ViewCompat.setLayerType(mRefreshableViewWrapper, value != 0 ? LAYER_TYPE_HARDWARE 
					: LAYER_TYPE_NONE /* View.LAYER_TYPE_NONE */);
		}

		// skip ScrollTo(...) 
		if (mMode.showGoogleStyle() ) {
			return;
		}

		switch (getFilteredPullToRefreshScrollDirection()) {
			case VERTICAL:
				scrollTo(0, value);
				break;
			case HORIZONTAL:
				scrollTo(value, 0);
				break;
		}
	}

	/**
	 * Smooth Scroll to position using the duration of
	 * {@link #mSmoothScrollDurationMs} ms.
	 * 
	 * @param scrollValue - Position to scroll to
	 */
	protected final void smoothScrollTo(int scrollValue) {
		smoothScrollTo(scrollValue, getPullToRefreshScrollDuration());
	}

	/**
	 * Smooth Scroll to position using the the duration of
	 * {@link #mSmoothScrollDurationMs} ms.
	 * 
	 * @param scrollValue - Position to scroll to
	 * @param listener - Listener for scroll
	 */
	protected final void smoothScrollTo(int scrollValue, OnSmoothScrollFinishedListener listener) {
		smoothScrollTo(scrollValue, getPullToRefreshScrollDuration(), 0, listener);
	}

	/**
	 * Smooth Scroll to position using the longer the duration of
	 * {@link #mSmoothScrollLongDurationMs} ms.
	 * 
	 * @param scrollValue - Position to scroll to
	 */
	protected final void smoothScrollToLonger(int scrollValue) {
		smoothScrollTo(scrollValue, getPullToRefreshScrollDurationLonger());
	}

	/**
	 * Updates the View State when the mode has been set. This does not do any
	 * checking that the mode is different to current state so always updates.
	 */
	protected void updateUIForMode() {
		// We need to use the correct LayoutParam values, based on scroll
		// direction
		final LinearLayout.LayoutParams lp = getLoadingLayoutLayoutParams();

		// Remove Header, and then add Header Loading View again if needed
		if (this == mHeaderLayout.getParent()) {
			removeView(mHeaderLayout);
		}
		if (mMode.showHeaderLoadingLayout()) {
			addViewInternal(mHeaderLayout, 0, lp);
		}

		// Remove Footer, and then add Footer Loading View again if needed
		if (this == mFooterLayout.getParent()) {
			removeView(mFooterLayout);
		}
		if (mMode.showFooterLoadingLayout()) {
			addViewInternal(mFooterLayout, lp);
		}

		// Hide Loading Views
		refreshLoadingViewsSize();

		// If we're not using Mode.BOTH, set mCurrentMode to mMode, otherwise
		// set it to pull down
		mCurrentMode = (mMode != Mode.BOTH) ? mMode : Mode.PULL_FROM_START;
	}
	/**
	 * Be called separately for google style mode when updating ui
	 */
	protected void updateUIForGoogleStyleMode() {
		if ( mWindowAttached == false ) {
			return;
		}

		if ( mMode.showGoogleStyle() == false ) {
            return;
        }

		// We need to use the correct LayoutParam values, based on scroll
		// direction
		//
		if ( mTopActionbarLayout == mGoogleStyleViewLayout.getParent()) {
			mTopActionbarLayout.removeView(mGoogleStyleViewLayout);
		}

		Log.d(LOG_TAG, "mViewOnTopLayout has been added." + mGoogleStyleViewLayout);
		mTopActionbarLayout.addView(mGoogleStyleViewLayout);
		/**
		 * Set size of {@code GoogleStyleViewLayout} to ActionBar's size if {@code mSetGoogleViewLayoutSizeToActionbarHeight} is true
		 * This code is a default action, but if you want to use custom size of {@code GoogleStyleViewLayout}, set {@code ptrSetGoogleViewLayoutSizeToActionbarHeight} to false in layout xml (but not recommended).
		 */
		if (mSetGoogleViewLayoutSizeToActionbarHeight == true) {
			// If it has called setHeight(...) method immediately after {@code view} has been added, the height isn't set correct
			post(new Runnable(){

				@Override
				public void run() {
					mGoogleStyleViewLayout.setHeight(mActionBarHeight);
				}});			
		}
		// Show Google style view layout to screen
		mGoogleStyleViewLayout.setVisibility(View.VISIBLE);
		
		// Hide Loading Views
		refreshLoadingViewsSize();

		// If we're not using Mode.BOTH, set mCurrentMode to mMode, otherwise
		// set it to pull down
		mCurrentMode = (mMode != Mode.BOTH) ? mMode : Mode.PULL_FROM_START;
	}

	private void addRefreshableView(Context context, T refreshableView) {
		mRefreshableViewWrapper = new FrameLayout(context);
		mRefreshableViewWrapper.addView(refreshableView, ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT);

		addViewInternal(mRefreshableViewWrapper, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
				LayoutParams.MATCH_PARENT));
	}

	private void callRefreshListener() {
		if (null != mOnRefreshListener) {
			mOnRefreshListener.onRefresh(this);
		} else if (null != mOnRefreshListener2) {
			if (mCurrentMode == Mode.PULL_FROM_START || mCurrentMode == Mode.GOOGLE_STYLE) {
				mOnRefreshListener2.onPullDownToRefresh(this);
			} else if (mCurrentMode == Mode.PULL_FROM_END) {
				mOnRefreshListener2.onPullUpToRefresh(this);
			}
		}
	}

	@SuppressWarnings("deprecation")
	private void init(Context context, AttributeSet attrs) {
		// PullToRefreshXmlConfiguration must be initialized.
		PullToRefreshXmlConfiguration.getInstance().init(context);
		/**
		 *  start initialization
		 */
		// Styleables from XML

		// Getting mMode is first, because It uses mMode in getFilteredPullToRefreshScrollDirection()
		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PullToRefresh);
		if (a.hasValue(R.styleable.PullToRefresh_ptrMode)) {
			mMode = Mode.mapIntToValue(a.getInteger(R.styleable.PullToRefresh_ptrMode, 0));
			filterModeForSDKVersion();
		}

		switch (getFilteredPullToRefreshScrollDirection()) {
			case HORIZONTAL:
				setOrientation(LinearLayout.HORIZONTAL);
				break;
			case VERTICAL:
			default:
				setOrientation(LinearLayout.VERTICAL);
				break;
		}

		ViewConfiguration config = ViewConfiguration.get(context);
		mTouchSlop = config.getScaledTouchSlop();

		// Default value of PTR View's gravity is center. So let the value be set center when the gravity is not set yet in XML.
		if (!Utils.existAttributeValue(attrs, "gravity")) {
			setGravity(Gravity.CENTER);
		}

		// Get a loading layout class token
		String loadingLayoutCode = null;
		if (a.hasValue(R.styleable.PullToRefresh_ptrAnimationStyle)) {
			loadingLayoutCode = a.getString(R.styleable.PullToRefresh_ptrAnimationStyle);
		} 
		
		mLoadingLayoutClazz = LoadingLayoutFactory.createLoadingLayoutClazzByLayoutCode(loadingLayoutCode);
		
		// Refreshable View
		// By passing the attrs, we can add ListView/GridView params via XML
		mRefreshableView = createRefreshableView(context, attrs);
		addRefreshableView(context, mRefreshableView);

		// We need to create now layouts now
		mHeaderLayout = createLoadingLayout(context, Mode.PULL_FROM_START, a);
		mFooterLayout = createLoadingLayout(context, Mode.PULL_FROM_END, a);

		/**
		 * Initialization for Google Style mode
		 */
		// Get a Google style view layout class token
		String googleStyleViewLayoutCode = null;
		if (a.hasValue(R.styleable.PullToRefresh_ptrGoogleViewStyle)) {
			googleStyleViewLayoutCode = a.getString(R.styleable.PullToRefresh_ptrGoogleViewStyle);
		} 
		// Get a Google style progress layout class token
		String googleStyleProgressLayoutCode = null;
		if (a.hasValue(R.styleable.PullToRefresh_ptrGoogleViewStyle)) {
			googleStyleProgressLayoutCode = a.getString(R.styleable.PullToRefresh_ptrGoogleProgressStyle);
		} 
		// Get a google style view layout
		mGoogleStyleViewLayout = createGoogleStyleViewLayout(googleStyleViewLayoutCode, context, a);
		// Get a google style progress layout 
		mGoogleStyleProgressLayout = createGoogleStyleProgressLayout(googleStyleProgressLayoutCode, context, a);
		// Get animation options for Google style mode
		if (a.hasValue(R.styleable.PullToRefresh_ptrShowGoogleStyleViewAnimationEnabled)) {
			mShowGoogleStyleViewAnimationEnabled = a.getBoolean(R.styleable.PullToRefresh_ptrShowGoogleStyleViewAnimationEnabled, true);
		}
		if (a.hasValue(R.styleable.PullToRefresh_ptrHideRefeshableViewWhileRefreshingEnabled)) {
			mRefeshableViewHideWhileRefreshingEnabled = a.getBoolean(R.styleable.PullToRefresh_ptrHideRefeshableViewWhileRefreshingEnabled, true);
		}
		if (a.hasValue(R.styleable.PullToRefresh_ptrViewRefeshableViewProgressBarOnCenterWhileRefreshingEnabled)) {
			mRefeshableViewRefreshingBarViewWhileRefreshingEnabled = a.getBoolean(R.styleable.PullToRefresh_ptrViewRefeshableViewProgressBarOnCenterWhileRefreshingEnabled, true);
		}
		if (a.hasValue(R.styleable.PullToRefresh_ptrShowGoogleStyleViewAnimationDuration)) {
			mShowGoogleStyleViewAnimationDuration = a.getInteger(R.styleable.PullToRefresh_ptrShowGoogleStyleViewAnimationDuration, GOOGLE_STYLE_VIEW_APPEAREANCE_DURATION);
		}
		if (a.hasValue(R.styleable.PullToRefresh_ptrHideRefeshableViewWhileRefreshingDuration)) {
			mRefeshableViewHideWhileRefreshingDuration = a.getInteger(R.styleable.PullToRefresh_ptrHideRefeshableViewWhileRefreshingDuration, REFRESHABLE_VIEW_HIDE_WHILE_REFRESHING_DURATION);
		}
		if (a.hasValue(R.styleable.PullToRefresh_ptrViewRefeshableViewProgressBarOnCenterWhileRefreshingDuration)) {
			mRefeshableViewRefreshingBarViewWhileRefreshingDuration = a.getInteger(R.styleable.PullToRefresh_ptrViewRefeshableViewProgressBarOnCenterWhileRefreshingDuration, REFRESHABLEVIEW_REFRESHING_BAR_VIEW_WHILE_REFRESHING_DURATION);
		}

		// Get a flag that decides Google View Layout's size is set to ActionBar's 
		if (a.hasValue(R.styleable.PullToRefresh_ptrSetGoogleViewLayoutSizeToActionbarHeight)) {
			mSetGoogleViewLayoutSizeToActionbarHeight = a.getBoolean(R.styleable.PullToRefresh_ptrSetGoogleViewLayoutSizeToActionbarHeight, true);
		}

		// Get width or height attr of refreshing bar 
		if (a.hasValue(R.styleable.PullToRefresh_ptrRefeshableViewProgressBarOnCenterWidth)) {
			mRefeshableViewRefreshingBarWidth = a.getInteger(R.styleable.PullToRefresh_ptrRefeshableViewProgressBarOnCenterWidth, DFEAULT_REFRESHABLEVIEW_REFRESHING_BAR_SIZE);
		}
		if (a.hasValue(R.styleable.PullToRefresh_ptrRefeshableViewProgressBarOnCenterHeight)) {
			mRefeshableViewRefreshingBarHeight = a.getInteger(R.styleable.PullToRefresh_ptrRefeshableViewProgressBarOnCenterHeight, DFEAULT_REFRESHABLEVIEW_REFRESHING_BAR_SIZE);
		}		
		/**
		 * Styleables from XML
		 */
		if (a.hasValue(R.styleable.PullToRefresh_ptrRefreshableViewBackground)) {
			Drawable background = a.getDrawable(R.styleable.PullToRefresh_ptrRefreshableViewBackground);
			if (null != background) {
				mRefreshableView.setBackgroundDrawable(background);
			}
		} else if (a.hasValue(R.styleable.PullToRefresh_ptrAdapterViewBackground)) {
			Utils.warnDeprecation("ptrAdapterViewBackground", "ptrRefreshableViewBackground");
			Drawable background = a.getDrawable(R.styleable.PullToRefresh_ptrAdapterViewBackground);
			if (null != background) {
				mRefreshableView.setBackgroundDrawable(background);
			}
		}

		if (a.hasValue(R.styleable.PullToRefresh_ptrOverScroll)) {
			mOverScrollEnabled = a.getBoolean(R.styleable.PullToRefresh_ptrOverScroll, true);
		}

		if (a.hasValue(R.styleable.PullToRefresh_ptrScrollingWhileRefreshingEnabled)) {
			mScrollingWhileRefreshingEnabled = a.getBoolean(
					R.styleable.PullToRefresh_ptrScrollingWhileRefreshingEnabled, false);
		}
		
		// set scroll properties from attributes 
		float friction = a.getFloat(R.styleable.PullToRefresh_ptrFriction, DEFAULT_FRICTION);
		int smoothScrollDuration = a.getInt(R.styleable.PullToRefresh_ptrSmoothScrollDuration, DEFAULT_SMOOTH_SCROLL_DURATION_MS);
		int smoothScrollLongDuration = a.getInt(R.styleable.PullToRefresh_ptrSmoothScrollLongDuration, DEFAULT_SMOOTH_SCROLL_LONG_DURATION_MS);
		
		setFriction(friction);
		setSmoothScrollDuration(smoothScrollDuration);
		setSmoothScrollLongDuration(smoothScrollLongDuration);
		
		// Let the derivative classes have a go at handling attributes, then
		// recycle them...
		handleStyledAttributes(a);
		a.recycle();

		// Get action bar height and status bar height 
		initActionBarSize(context);
		initStatusBarSize(context);

		determineYPositionOfGoogleStyleViewLayout();
		
		// Finally update the UI for the modes
		updateUIForMode();
		// updateUIForGoogleStyleMode() method will be called when onAttachedToWindow() event has been fired.
	}

	private void filterModeForSDKVersion() {
		// If SDK version is 2.x or lower, Let the mode not be google mode. 
		// Because google mode should not be supported in those versions.
		if ( VERSION.SDK_INT < VERSION_CODES.HONEYCOMB && mMode == Mode.GOOGLE_STYLE ) {
			mMode = Mode.PULL_FROM_START;
		}
	}

	private void determineYPositionOfGoogleStyleViewLayout() {
		if ( VERSION.SDK_INT < VERSION_CODES.ICE_CREAM_SANDWICH ) { 
			mYPositionOfGoogleStyleViewLayout = 0;
		} else {
			mYPositionOfGoogleStyleViewLayout = mStatusBarHeight;
		}
	}
	/**
	 * NOTE : This method must be called after initStatusBarSize() and initActionBarSize() have already been called. Also, mGoogleStyleProgressLayout should be initialized before calling this method. 
	 */
	private void determineYPositionOfGoogleStyleProgressLayout() {
		if ( VERSION.SDK_INT < VERSION_CODES.ICE_CREAM_SANDWICH ) { 
			mYPositionOfGoogleStyleProgressLayout = mStatusBarHeight + 1;
		} else {
			mYPositionOfGoogleStyleProgressLayout = mActionBarHeight + mGoogleStyleProgressLayout.getHeight() + 1;
		}		
	}
	/**
	 * Show google view layout and google progress layout when pulling
	 */
	private void showViewTopLayout() {
    	if (mMode.showGoogleStyle() == false ) {
    		return;
    	}

    	// Initialize Translate and Alpha animation
    	if ( mShowGoogleStyleViewAnimationEnabled == true ) {
        	AnimationSet set = new AnimationSet(true /* share interpolator */);
        	set.setDuration(mShowGoogleStyleViewAnimationDuration);
        	set.setFillAfter(true);
        	set.setAnimationListener(new AnimationListener(){

    			@Override
    			public void onAnimationEnd(Animation anim) {
    			}

    			@Override
    			public void onAnimationRepeat(Animation anim) {
    			}

    			@Override
    			public void onAnimationStart(Animation anim) {
    				mTopActionbarLayout.setVisibility(View.VISIBLE);
    			}});
        	
        	TranslateAnimation transAnimation = new TranslateAnimation(Animation.ABSOLUTE, 0,Animation.ABSOLUTE, 0, Animation.ABSOLUTE, -mActionBarHeight, Animation.ABSOLUTE, mYPositionOfGoogleStyleViewLayout);
        	AlphaAnimation alphaAnimation = new AlphaAnimation(0.0f, 1.0f);
        	set.addAnimation(transAnimation);
        	set.addAnimation(alphaAnimation);
        	// Start animation
        	mTopActionbarLayout.startAnimation(set);    		
    	} else {
       		// Show Google style view layout without animation
    		((FrameLayout.LayoutParams) mTopActionbarLayout.getLayoutParams()).topMargin = mYPositionOfGoogleStyleViewLayout;
    		mTopActionbarLayout.setVisibility(View.VISIBLE);   		
    	}

    	mGoogleStyleProgressLayout.setVisibility(View.VISIBLE);
    }

	/**
	 * Hide google view layout and google progress layout when releasing
	 */
	private void hideViewTopLayout() {
    	if (mMode.showGoogleStyle() == false ) {
    		return;
    	}

    	if ( mShowGoogleStyleViewAnimationEnabled == true ) {
        	// Initialize Translate and Alpha animation
    	   	AnimationSet set = new AnimationSet(true /* share interpolator */);
        	set.setDuration(mShowGoogleStyleViewAnimationDuration);
        	set.setFillAfter(true);
        	set.setAnimationListener(new AnimationListener(){

    			@Override
    			public void onAnimationEnd(Animation anim) {
    				mTopActionbarLayout.setVisibility(View.INVISIBLE);
    			}

    			@Override
    			public void onAnimationRepeat(Animation anim) {
    			}

    			@Override
    			public void onAnimationStart(Animation anim) {
    			}});
        	
        	TranslateAnimation transAnimation = new TranslateAnimation(Animation.ABSOLUTE, 0,Animation.ABSOLUTE, 0, Animation.ABSOLUTE, mTopActionbarLayout.getTop(), Animation.ABSOLUTE, -mStatusBarHeight);
        	AlphaAnimation alphaAnimation = new AlphaAnimation(1.0f, 0.0f);
        	set.addAnimation(transAnimation);
        	set.addAnimation(alphaAnimation);
        	// Start animation
        	mTopActionbarLayout.startAnimation(set);
    	} else {
      		// Hide Google style view layout without animation
    		((FrameLayout.LayoutParams) mTopActionbarLayout.getLayoutParams()).topMargin = -mActionBarHeight;
    		mTopActionbarLayout.setVisibility(View.INVISIBLE);
    	}

    	mGoogleStyleProgressLayout.setVisibility(View.INVISIBLE);
    }
    
	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		mWindowAttached = true;
		
		initTopViewGroup();
		updateUIForGoogleStyleMode();
	}

	/**
     * Initialize {@code mTopActionbarLayout} and add that into Top DecorView(this is the root view),
     * and initialize needed components
     */   
	private void initTopViewGroup() {

        if ( mMode.showGoogleStyle() == false ) {
            return;
        }

		View view = this.getRootView();
		ViewGroup topViewGroup = null;
		Context context = getContext();
		if (view instanceof ViewGroup == false) {
			Log.w(LOG_TAG, "Current root view is not ViewGroup type. Google Style Pull To Refresh mode will be disabled.");
			topViewGroup = new ViewGroup(context) {
				@Override
				protected void onLayout(boolean changed, int l, int t, int r, int b) {
					// do nothing
				}};

		} else {
			topViewGroup = (ViewGroup) view;
		}

		// Initialize Top Layout Layout
		FrameLayout layout = new FrameLayout(context);

		@SuppressWarnings("deprecation")
		int matchParent = ViewGroup.LayoutParams.FILL_PARENT;
		
		ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(matchParent, mActionBarHeight);
		
		topViewGroup.addView(layout, params);
		layout.setVisibility(View.INVISIBLE);

		// Initialize refreshing bar on center
        if (mMode.showGoogleStyle()) {
            mRefreshableViewProgressBar = generateCircleProgressBar(context);
            FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(mRefeshableViewRefreshingBarWidth, mRefeshableViewRefreshingBarHeight);
            barParams.gravity = Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL;
            mRefreshableViewProgressBar.setVisibility(View.INVISIBLE);
            mRefreshableViewWrapper.addView(mRefreshableViewProgressBar, -1, barParams);        	
        }
		// Initialize Google style progress layout
		topViewGroup.addView(mGoogleStyleProgressLayout, mGoogleStyleProgressLayout.createLayoutParams());
		mGoogleStyleProgressLayout.setVisibility(View.INVISIBLE);
		// Set height of Google style progress layout
		post(new Runnable(){

			@Override
			public void run() {
				determineYPositionOfGoogleStyleProgressLayout();
				mGoogleStyleProgressLayout.setTopMargin(mYPositionOfGoogleStyleProgressLayout);
				
			}});

		// Finally assign
		mTopActionbarLayout = layout;

	}
	/**
	 * Get an actionBar's size and save into a field
	 * @param context
	 */
	private void initActionBarSize(Context context) {
		mActionBarHeight = Utils.getActionBarSize(context);
	}
	/**
	 * Get an StatusBar's size and save into a field
	 * @param context
	 */
	private void initStatusBarSize(Context context) {
		mStatusBarHeight = Utils.getStatusBarSize(context);
	}
	/**
	 * Generate Progress bar UI Component on center
	 * @param context
	 * @return Generated ProgressBar instance
	 */
	private ProgressBar generateCircleProgressBar(Context context) {
        ProgressBar circleProgressBar = new ProgressBar(context);
        circleProgressBar.setScrollBarStyle(android.R.attr.progressBarStyle);
        circleProgressBar.setIndeterminate(true);
		
		return circleProgressBar;
	}

	private boolean isReadyForPull() {
		switch (mMode) {
			case PULL_FROM_START:
			case GOOGLE_STYLE:
				return isReadyForPullStart();
			case PULL_FROM_END:
				return isReadyForPullEnd();
			case BOTH:
				return isReadyForPullEnd() || isReadyForPullStart();
			default:
				return false;
		}
	}

	/**
	 * Actions a Pull Event
	 * 
	 * @return true if the Event has been handled, false if there has been no
	 *         change
	 */
	private void pullEvent() {
		final int newScrollValue;
		final int itemDimension;
		final float initialMotionValue, lastMotionValue;

		switch (getFilteredPullToRefreshScrollDirection()) {
			case HORIZONTAL:
				initialMotionValue = mInitialMotionX;
				lastMotionValue = mLastMotionX;
				break;
			case VERTICAL:
			default:
				initialMotionValue = mInitialMotionY;
				lastMotionValue = mLastMotionY;
				break;
		}

		switch (mCurrentMode) {
			case PULL_FROM_END:
				newScrollValue = Math.round(Math.max(initialMotionValue - lastMotionValue, 0) / mFriction);
				itemDimension = getFooterSize();
				break;
			case GOOGLE_STYLE:
				newScrollValue = Math.round(Math.min(initialMotionValue - lastMotionValue, 0) / mFriction);
				itemDimension = getGoogleStyleViewSize();
				break;
			case PULL_FROM_START:
			default:
				newScrollValue = Math.round(Math.min(initialMotionValue - lastMotionValue, 0) / mFriction);
				itemDimension = getHeaderSize();
				break;
		}

		setHeaderScroll(newScrollValue);

		if (newScrollValue != 0 && !isRefreshing()) {
			float scale = Math.abs(newScrollValue) / (float) itemDimension;
			switch (mCurrentMode) {
				case PULL_FROM_END:
					mFooterLayout.onPull(scale);
					break;
				case GOOGLE_STYLE:
					mGoogleStyleViewLayout.onPull(scale);
					mGoogleStyleProgressLayout.onPull(scale);
					break;
				case PULL_FROM_START:
				default:
					mHeaderLayout.onPull(scale);
					break;
			}

			if (mState != State.PULL_TO_REFRESH && itemDimension >= Math.abs(newScrollValue)) {
				setState(State.PULL_TO_REFRESH);
			} else if (mState == State.PULL_TO_REFRESH && itemDimension < Math.abs(newScrollValue)) {
				setState(State.RELEASE_TO_REFRESH);
			}
		}
	}

	@SuppressWarnings("deprecation")
	private LinearLayout.LayoutParams getLoadingLayoutLayoutParams() {
		switch (getFilteredPullToRefreshScrollDirection()) {
			case HORIZONTAL:
				return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
						LinearLayout.LayoutParams.FILL_PARENT);
			case VERTICAL:
			default:
				return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,
						LinearLayout.LayoutParams.WRAP_CONTENT);
		}
	}

	private int getMaximumPullScroll() {
		switch (getFilteredPullToRefreshScrollDirection()) {
			case HORIZONTAL:
				return Math.round(getWidth() / mFriction);
			case VERTICAL:
			default:
				return Math.round(getHeight() / mFriction);
		}
	}

	/**
	 * Smooth Scroll to position using the specific duration
	 * 
	 * @param scrollValue - Position to scroll to
	 * @param duration - Duration of animation in milliseconds
	 */
	private final void smoothScrollTo(int scrollValue, long duration) {
		smoothScrollTo(scrollValue, duration, 0, null);
	}

	private final void smoothScrollTo(int newScrollValue, long duration, long delayMillis,
			OnSmoothScrollFinishedListener listener) {
		if (null != mCurrentSmoothScrollRunnable) {
			mCurrentSmoothScrollRunnable.stop();
		}

		final int oldScrollValue;
		switch (getFilteredPullToRefreshScrollDirection()) {
			case HORIZONTAL:
				oldScrollValue = getScrollX();
				break;
			case VERTICAL:
			default:
				oldScrollValue = getScrollY();
				break;
		}

		if (oldScrollValue != newScrollValue) {
			if (null == mScrollAnimationInterpolator) {
				// Default interpolator is a Decelerate Interpolator
				mScrollAnimationInterpolator = new DecelerateInterpolator();
			}
			mCurrentSmoothScrollRunnable = new SmoothScrollRunnable(oldScrollValue, newScrollValue, duration, listener);

			if (delayMillis > 0) {
				postDelayed(mCurrentSmoothScrollRunnable, delayMillis);
			} else {
				post(mCurrentSmoothScrollRunnable);
			}
			
		} else if ( listener != null ) { 
			// Call listener immediately
			listener.onSmoothScrollFinished();
		}
	}

	private final void smoothScrollToAndBack(int y) {
		smoothScrollTo(y, mSmoothScrollDurationMs, 0, new OnSmoothScrollFinishedListener() {

			@Override
			public void onSmoothScrollFinished() {
				smoothScrollTo(0, mSmoothScrollDurationMs, DEMO_SCROLL_INTERVAL, null);
			}
		});
	}

	public static enum Mode {

		/**
		 * Disable all Pull-to-Refresh gesture and Refreshing handling
		 */
		DISABLED(0x0),

		/**
		 * Only allow the user to Pull from the start of the Refreshable View to
		 * refresh. The start is either the Top or Left, depending on the
		 * scrolling direction.
		 */
		PULL_FROM_START(0x1),

		/**
		 * Only allow the user to Pull from the end of the Refreshable View to
		 * refresh. The start is either the Bottom or Right, depending on the
		 * scrolling direction.
		 */
		PULL_FROM_END(0x2),

		/**
		 * Allow the user to both Pull from the start, from the end to refresh.
		 */
		BOTH(0x3),

		/**
		 * Disables Pull-to-Refresh gesture handling, but allows manually
		 * setting the Refresh state via
		 * {@link PullToRefreshBase#setRefreshing() setRefreshing()}.
		 */
		MANUAL_REFRESH_ONLY(0x4),

		/**
		 * Google style pull-to-refresh mode
		 *
		 */
		GOOGLE_STYLE(0x5);
		
		/**
		 * @deprecated Use {@link #PULL_FROM_START} from now on.
		 */
		public static Mode PULL_DOWN_TO_REFRESH = Mode.PULL_FROM_START;

		/**
		 * @deprecated Use {@link #PULL_FROM_END} from now on.
		 */
		public static Mode PULL_UP_TO_REFRESH = Mode.PULL_FROM_END;

		/**
		 * Maps an int to a specific mode. This is needed when saving state, or
		 * inflating the view from XML where the mode is given through a attr
		 * int.
		 * 
		 * @param modeInt - int to map a Mode to
		 * @return Mode that modeInt maps to, or PULL_FROM_START by default.
		 */
		static Mode mapIntToValue(final int modeInt) {
			for (Mode value : Mode.values()) {
				if (modeInt == value.getIntValue()) {
					return value;
				}
			}

			// If not, return default
			return getDefault();
		}

		static Mode getDefault() {
			return PULL_FROM_START;
		}

		private int mIntValue;

		// The modeInt values need to match those from attrs.xml
		Mode(int modeInt) {
			mIntValue = modeInt;
		}

		/**
		 * @return true if the mode permits Pull-to-Refresh
		 */
		boolean permitsPullToRefresh() {
			return !(this == DISABLED || this == MANUAL_REFRESH_ONLY);
		}

		/**
		 * @return true if this mode wants the Loading Layout Header to be shown
		 */
		public boolean showHeaderLoadingLayout() {
			return this == PULL_FROM_START || this == BOTH;
		}

		/**
		 * @return true if this mode wants the Loading Layout Footer to be shown
		 */
		public boolean showFooterLoadingLayout() {
			return this == PULL_FROM_END || this == BOTH || this == MANUAL_REFRESH_ONLY;
		}

		/**
		 * @return true if this mode wants the Loading Layout to be shown like Google style pull-to-refresh
		 */
		public boolean showGoogleStyle() {
			return this == GOOGLE_STYLE;
		}

		int getIntValue() {
			return mIntValue;
		}

	}

	// ===========================================================
	// Inner, Anonymous Classes, and Enumerations
	// ===========================================================

	/**
	 * Simple Listener that allows you to be notified when the user has scrolled
	 * to the end of the AdapterView. See (
	 * {@link PullToRefreshAdapterViewBase#setOnLastItemVisibleListener}.
	 * 
	 * @author Chris Banes
	 */
	public static interface OnLastItemVisibleListener {

		/**
		 * Called when the user has scrolled to the end of the list
		 */
		public void onLastItemVisible();

	}

	/**
	 * Listener that allows you to be notified when the user has started or
	 * finished a touch event. Useful when you want to append extra UI events
	 * (such as sounds). See (
	 * {@link PullToRefreshAdapterViewBase#setOnPullEventListener}.
	 * 
	 * @author Chris Banes
	 */
	public static interface OnPullEventListener<V extends View> {

		/**
		 * Called when the internal state has been changed, usually by the user
		 * pulling.
		 * 
		 * @param refreshView - View which has had it's state change.
		 * @param state - The new state of View.
		 * @param direction - One of {@link Mode#PULL_FROM_START} or
		 *            {@link Mode#PULL_FROM_END} depending on which direction
		 *            the user is pulling. Only useful when <var>state</var> is
		 *            {@link State#PULL_TO_REFRESH} or
		 *            {@link State#RELEASE_TO_REFRESH}.
		 */
		public void onPullEvent(final PullToRefreshBase<V> refreshView, State state, Mode direction);

	}

	/**
	 * Simple Listener to listen for any callbacks to Refresh.
	 * 
	 * @author Chris Banes
	 */
	public static interface OnRefreshListener<V extends View> {

		/**
		 * onRefresh will be called for both a Pull from start, and Pull from
		 * end
		 */
		public void onRefresh(final PullToRefreshBase<V> refreshView);

	}

	/**
	 * An advanced version of the Listener to listen for callbacks to Refresh.
	 * This listener is different as it allows you to differentiate between Pull
	 * Ups, and Pull Downs.
	 * 
	 * @author Chris Banes
	 */
	public static interface OnRefreshListener2<V extends View> {
		// TODO These methods need renaming to START/END rather than DOWN/UP

		/**
		 * onPullDownToRefresh will be called only when the user has Pulled from
		 * the start, and released.
		 */
		public void onPullDownToRefresh(final PullToRefreshBase<V> refreshView);

		/**
		 * onPullUpToRefresh will be called only when the user has Pulled from
		 * the end, and released.
		 */
		public void onPullUpToRefresh(final PullToRefreshBase<V> refreshView);

	}

	public static enum Orientation {
		VERTICAL, HORIZONTAL;
	}

	public static enum State {

		/**
		 * When the UI is in a state which means that user is not interacting
		 * with the Pull-to-Refresh function.
		 */
		RESET(0x0),

		/**
		 * When the UI is being pulled by the user, but has not been pulled far
		 * enough so that it refreshes when released.
		 */
		PULL_TO_REFRESH(0x1),

		/**
		 * When the UI is being pulled by the user, and <strong>has</strong>
		 * been pulled far enough so that it will refresh when released.
		 */
		RELEASE_TO_REFRESH(0x2),

		/**
		 * When the UI is currently refreshing, caused by a pull gesture.
		 */
		REFRESHING(0x8),

		/**
		 * When the UI is currently refreshing, caused by a call to
		 * {@link PullToRefreshBase#setRefreshing() setRefreshing()}.
		 */
		MANUAL_REFRESHING(0x9),

		/**
		 * When the UI is currently overscrolling, caused by a fling on the
		 * Refreshable View.
		 */
		OVERSCROLLING(0x10);

		/**
		 * Maps an int to a specific state. This is needed when saving state.
		 * 
		 * @param stateInt - int to map a State to
		 * @return State that stateInt maps to
		 */
		static State mapIntToValue(final int stateInt) {
			for (State value : State.values()) {
				if (stateInt == value.getIntValue()) {
					return value;
				}
			}

			// If not, return default
			return RESET;
		}

		private int mIntValue;

		State(int intValue) {
			mIntValue = intValue;
		}

		int getIntValue() {
			return mIntValue;
		}
	}

	final class SmoothScrollRunnable implements Runnable {
		private final Interpolator mInterpolator;
		private final int mScrollToY;
		private final int mScrollFromY;
		private final long mDuration;
		private OnSmoothScrollFinishedListener mListener;

		private boolean mContinueRunning = true;
		private long mStartTime = -1;
		private int mCurrentY = -1;

		public SmoothScrollRunnable(int fromY, int toY, long duration, OnSmoothScrollFinishedListener listener) {
			mScrollFromY = fromY;
			mScrollToY = toY;
			mInterpolator = mScrollAnimationInterpolator;
			mDuration = duration;
			mListener = listener;
		}

		@Override
		public void run() {

			/**
			 * Only set mStartTime if this is the first time we're starting,
			 * else actually calculate the Y delta
			 */
			if (mStartTime == -1) {
				mStartTime = System.currentTimeMillis();
			} else {

				/**
				 * We do do all calculations in long to reduce software float
				 * calculations. We use 1000 as it gives us good accuracy and
				 * small rounding errors
				 */
				long normalizedTime = (1000 * (System.currentTimeMillis() - mStartTime)) / mDuration;
				normalizedTime = Math.max(Math.min(normalizedTime, 1000), 0);

				final int deltaY = Math.round((mScrollFromY - mScrollToY)
						* mInterpolator.getInterpolation(normalizedTime / 1000f));
				mCurrentY = mScrollFromY - deltaY;
				setHeaderScroll(mCurrentY);
			}

			// If we're not at the target Y, keep going...
			if (mContinueRunning && mScrollToY != mCurrentY) {
				ViewCompat.postOnAnimation(PullToRefreshBase.this, this);
			} else {
				if (null != mListener) {
					mListener.onSmoothScrollFinished();
				}
			}
		}

		public void stop() {
			mContinueRunning = false;
			removeCallbacks(this);
		}
	}

	static interface OnSmoothScrollFinishedListener {
		void onSmoothScrollFinished();
	}

}
