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
package com.handmark.pulltorefresh.library.internal;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.handmark.pulltorefresh.library.ILoadingLayout;
import com.handmark.pulltorefresh.library.PullToRefreshBase.Mode;
import com.handmark.pulltorefresh.library.PullToRefreshBase.Orientation;
import com.handmark.pulltorefresh.library.R;

@SuppressLint("ViewConstructor")
public abstract class LoadingLayout extends FrameLayout implements ILoadingLayout {

	static final String LOG_TAG = "PullToRefresh-LoadingLayout";

	static final Interpolator ANIMATION_INTERPOLATOR = new LinearInterpolator();

	protected FrameLayout mInnerLayout;

	protected ImageView mHeaderImage;
	protected ProgressBar mHeaderProgress;

	private boolean mUseIntrinsicAnimation;

	protected TextView mHeaderText;
	protected TextView mSubHeaderText;

	protected final Mode mMode;
	protected final Orientation mScrollDirection;

	private CharSequence mPullLabel;
	private CharSequence mRefreshingLabel;
	private CharSequence mReleaseLabel;

	private Drawable mImageDrawable;
	
	/**
	 * The constructor to customize layout, not public scope now.
	 * @param context
	 * @param mode
	 * @param scrollDirection
	 */
	protected LoadingLayout(Context context, final Mode mode, final Orientation scrollDirection, TypedArray attrs, int inflateId) {
		super(context);
		mMode = mode;
		mScrollDirection = scrollDirection;

		initInflate(context, inflateId);
		initComponents();
		initProperties(context, mode, attrs);

		if (null != mImageDrawable) {
			setLoadingDrawable(mImageDrawable);
			mImageDrawable = null;
		}

		reset();	
		
	}
	
	public LoadingLayout(Context context, final Mode mode, final Orientation scrollDirection, TypedArray attrs) {
		super(context);
		mMode = mode;
		mScrollDirection = scrollDirection;

		switch (scrollDirection) {
			case HORIZONTAL:
				initInflate(context, R.layout.pull_to_refresh_header_horizontal);
				break;
			case VERTICAL:
			default:
				initInflate(context, R.layout.pull_to_refresh_header_vertical);
				break;
		}

		initComponents();

		if (null != mInnerLayout) {
			FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mInnerLayout.getLayoutParams();

			switch (mode) {
				case PULL_FROM_END:
					lp.gravity = scrollDirection == Orientation.VERTICAL ? Gravity.TOP : Gravity.LEFT;
					break;

				case PULL_FROM_START:
				default:
					lp.gravity = scrollDirection == Orientation.VERTICAL ? Gravity.BOTTOM : Gravity.RIGHT;
					break;
			}			
		}

		initProperties(context, mode, attrs);

		// If we don't have a user defined drawable, load the default
		if (null == mImageDrawable) {
			mImageDrawable = context.getResources().getDrawable(getDefaultDrawableResId());
		}

		// Set Drawable, and save width/height
		setLoadingDrawable(mImageDrawable);
		mImageDrawable = null;

		reset();
	}

	protected void initComponents() {
		mInnerLayout = (FrameLayout) findViewById(R.id.fl_inner);
		mHeaderText = (TextView) mInnerLayout.findViewById(R.id.pull_to_refresh_text);
		mHeaderProgress = (ProgressBar) mInnerLayout.findViewById(R.id.pull_to_refresh_progress);
		mSubHeaderText = (TextView) mInnerLayout.findViewById(R.id.pull_to_refresh_sub_text);
		mHeaderImage = (ImageView) mInnerLayout.findViewById(R.id.pull_to_refresh_image);
	}

	private void initInflate(Context context, int inflateId) {
		LayoutInflater.from(context).inflate(inflateId, this);
	}

	protected void initProperties(Context context, final Mode mode,
			TypedArray attrs) {
		// Load Loading Layout Labels
		loadLoadingLayoutLabels(context, attrs, mode);
		
		if (attrs.hasValue(R.styleable.PullToRefresh_ptrHeaderBackground)) {
			Drawable background = attrs.getDrawable(R.styleable.PullToRefresh_ptrHeaderBackground);
			if (null != background) {
				ViewCompat.setBackground(this, background);
			}
		}

		if (attrs.hasValue(R.styleable.PullToRefresh_ptrHeaderTextAppearance)) {
			TypedValue styleID = new TypedValue();
			attrs.getValue(R.styleable.PullToRefresh_ptrHeaderTextAppearance, styleID);
			setTextAppearance(styleID.data);
		}
		if (attrs.hasValue(R.styleable.PullToRefresh_ptrSubHeaderTextAppearance)) {
			TypedValue styleID = new TypedValue();
			attrs.getValue(R.styleable.PullToRefresh_ptrSubHeaderTextAppearance, styleID);
			setSubTextAppearance(styleID.data);
		}

		// Text Color attrs need to be set after TextAppearance attrs
		if (attrs.hasValue(R.styleable.PullToRefresh_ptrHeaderTextColor)) {
			ColorStateList colors = attrs.getColorStateList(R.styleable.PullToRefresh_ptrHeaderTextColor);
			if (null != colors) {
				setTextColor(colors);
			}
		}
		if (attrs.hasValue(R.styleable.PullToRefresh_ptrHeaderSubTextColor)) {
			ColorStateList colors = attrs.getColorStateList(R.styleable.PullToRefresh_ptrHeaderSubTextColor);
			if (null != colors) {
				setSubTextColor(colors);
			}
		}

		// Try and get defined drawable from Attrs
		if (attrs.hasValue(R.styleable.PullToRefresh_ptrDrawable)) {
			mImageDrawable = attrs.getDrawable(R.styleable.PullToRefresh_ptrDrawable);
		}

		// Check Specific Drawable from Attrs, these overrite the generic
		// drawable attr above
		switch (mode) {
			case PULL_FROM_START:
			default:
				if (attrs.hasValue(R.styleable.PullToRefresh_ptrDrawableStart)) {
					mImageDrawable = attrs.getDrawable(R.styleable.PullToRefresh_ptrDrawableStart);
				} else if (attrs.hasValue(R.styleable.PullToRefresh_ptrDrawableTop)) {
					Utils.warnDeprecation("ptrDrawableTop", "ptrDrawableStart");
					mImageDrawable = attrs.getDrawable(R.styleable.PullToRefresh_ptrDrawableTop);
				}
				break;

			case PULL_FROM_END:
				if (attrs.hasValue(R.styleable.PullToRefresh_ptrDrawableEnd)) {
					mImageDrawable = attrs.getDrawable(R.styleable.PullToRefresh_ptrDrawableEnd);
				} else if (attrs.hasValue(R.styleable.PullToRefresh_ptrDrawableBottom)) {
					Utils.warnDeprecation("ptrDrawableBottom", "ptrDrawableEnd");
					mImageDrawable = attrs.getDrawable(R.styleable.PullToRefresh_ptrDrawableBottom);
				}
				break;
		}

	}
	
	/**
	 * Load labels of pull, refresh, release, and assign into fields
	 * <br />Convert an each attribute value such as {@code ptrPullLabel}, {@code ptrRefreshLabel} or {@code ptrReleaseLabel} to each label field if each value exists.
	 * <br />Or if not, then the each label is assigned some string as default
	 * <br />
	 * NOTE : This method <b>Must</b> be modified if kinds of {@code Mode} are increased.
	 * @param attrs 
	 * @param mode Current mode
	 */
	private void loadLoadingLayoutLabels(Context context, TypedArray attrs, Mode mode) {
		mPullLabel = loadPullLabel(context, attrs, mode);
		mRefreshingLabel = loadRefreshingLabel(context, attrs, mode);
		mReleaseLabel = loadReleaseLabel(context, attrs, mode);
	}
	/**
	 * Load labels of pull
	 * <br />Convert an {@code ptrPullLabel} attribute value to {@code mPullLabel} field if each value exists.
	 * <br />Or if not, then the pull label is assigned some string as default
	 * <br />If you want to set some custom pull label at sub class, you have to override this method and implement.
	 * NOTE : This method <b>Must</b> be modified if kinds of {@code Mode} are increased.
	 * @param context
	 * @param attrs
	 * @param mode
	 * @return String to be a pull label  
	 */
	protected String loadPullLabel(Context context, TypedArray attrs, Mode mode) {
		// Pull Label
		if (attrs.hasValue(R.styleable.PullToRefresh_ptrPullLabel)) {
			return attrs.getString(R.styleable.PullToRefresh_ptrPullLabel);
		} 
		
		int stringId = (mode == Mode.PULL_FROM_END) ? R.string.pull_to_refresh_from_bottom_pull_label : R.string.pull_to_refresh_pull_label;
		return context.getString(stringId);
	}	
	/**
	 * Load labels of refreshing
	 * <br />Convert an {@code ptrRefreshLabel} attribute value to {@code mRefreshingLabel} field if each value exists.
	 * <br />Or if not, then the refreshing label is assigned some string as default
	 * <br />If you want to set some custom refreshing label at sub class, you have to override this method and implement.
	 * NOTE : This method <b>Must</b> be modified if kinds of {@code Mode} are increased.
	 * @param context
	 * @param attrs
	 * @param mode
	 * @return String to be a refreshing label  
	 */
	protected String loadRefreshingLabel(Context context, TypedArray attrs,
			Mode mode) {
		// Refresh Label
		if (attrs.hasValue(R.styleable.PullToRefresh_ptrRefreshLabel)) {
			return attrs.getString(R.styleable.PullToRefresh_ptrRefreshLabel);
		} 
		
		int stringId = (mode == Mode.PULL_FROM_END) ? R.string.pull_to_refresh_from_bottom_refreshing_label : R.string.pull_to_refresh_refreshing_label;
		return context.getString(stringId);
	}	
	/**
	 * Load labels of release
	 * <br />Convert an {@code ptrReleaseLabel} attribute value to {@code mReleaseLabel} field if each value exists.
	 * <br />Or if not, then the release label is assigned some string as default
	 * <br />If you want to set some custom release label at sub class, you have to override this method and implement.
	 * NOTE : This method <b>Must</b> be modified if kinds of {@code Mode} are increased.
	 * @param context
	 * @param attrs
	 * @param mode
	 * @return String to be a refreshing label  
	 */
	protected String loadReleaseLabel(Context context, TypedArray attrs, Mode mode) {
		// Release Label
		if (attrs.hasValue(R.styleable.PullToRefresh_ptrReleaseLabel)) {
			return attrs.getString(R.styleable.PullToRefresh_ptrReleaseLabel);
		} 
		
		int stringId = (mode == Mode.PULL_FROM_END) ? R.string.pull_to_refresh_from_bottom_release_label : R.string.pull_to_refresh_release_label;
		return context.getString(stringId);
		
	}

	public void setHeight(int height) {
		ViewGroup.LayoutParams lp = (ViewGroup.LayoutParams) getLayoutParams();
		lp.height = height;
		requestLayout();
	}

	public final void setWidth(int width) {
		ViewGroup.LayoutParams lp = (ViewGroup.LayoutParams) getLayoutParams();
		lp.width = width;
		requestLayout();
	}

	public final int getContentSize() {
		switch (mScrollDirection) {
			case HORIZONTAL:
				return mInnerLayout.getWidth();
			case VERTICAL:
			default:
				return mInnerLayout.getHeight();
		}
	}

	public final void hideAllViews() {
		hideHeaderText();
		hideHeaderProgress();
		hideHeaderImage();
		hideSubHeaderText();
	}

	private void hideHeaderText() {
		if (null != mHeaderText && View.VISIBLE == mHeaderText.getVisibility()) {
			mHeaderText.setVisibility(View.INVISIBLE);
		}
	}

	private void hideHeaderProgress() {
		if (null != mHeaderProgress && View.VISIBLE == mHeaderProgress.getVisibility()) {
			mHeaderProgress.setVisibility(View.INVISIBLE);
		}
	}

	private void hideSubHeaderText() {
		if (null != mHeaderText && View.VISIBLE == mSubHeaderText.getVisibility()) {
			mSubHeaderText.setVisibility(View.INVISIBLE);
		}
	}

	public final void onPull(float scaleOfLayout) {
		if (!mUseIntrinsicAnimation) {
			onPullImpl(scaleOfLayout);
		}
	}

	public final void pullToRefresh() {
		if (null != mHeaderText) {
			mHeaderText.setText(mPullLabel);
		}

		// Now call the callback
		pullToRefreshImpl();
	}

	public final void refreshing() {
		if (null != mHeaderText) {
			mHeaderText.setText(mRefreshingLabel);
		}

		if (null != mHeaderImage && mUseIntrinsicAnimation) {
			((AnimationDrawable) mHeaderImage.getDrawable()).start();
		} else {
			// Now call the callback
			refreshingImpl();
		}

		if (null != mSubHeaderText) {
			mSubHeaderText.setVisibility(View.GONE);
		}
	}

	public final void releaseToRefresh() {
		if (null != mHeaderText) {
			mHeaderText.setText(mReleaseLabel);
		}

		// Now call the callback
		releaseToRefreshImpl();
	}

	public final void reset() {
		if (null != mHeaderText) {
			mHeaderText.setText(mPullLabel);
		}

		showHeaderImage();

		if (null != mHeaderImage && mUseIntrinsicAnimation) {
			((AnimationDrawable) mHeaderImage.getDrawable()).stop();
		} else {
			// Now call the callback
			resetImpl();
		}		

		if (null != mSubHeaderText) {
			if (TextUtils.isEmpty(mSubHeaderText.getText())) {
				mSubHeaderText.setVisibility(View.GONE);
			} else {
				mSubHeaderText.setVisibility(View.VISIBLE);
			}
		}
	}

	private void showHeaderImage() {
		if (null != mHeaderImage && View.INVISIBLE == mHeaderImage.getVisibility()) {
			mHeaderImage.setVisibility(View.VISIBLE);	
		}
	}
	
	private void hideHeaderImage() {
		if (null != mHeaderImage && View.VISIBLE == mHeaderImage.getVisibility() ) {
			mHeaderImage.setVisibility(View.INVISIBLE);	
		}		
	}

	@Override
	public void setLastUpdatedLabel(CharSequence label) {
		setSubHeaderText(label);
	}

	public final void setLoadingDrawable(Drawable imageDrawable) {
		// Set Drawable
		if ( null != mHeaderImage ) {
			mHeaderImage.setImageDrawable(imageDrawable);
		}
		
		mUseIntrinsicAnimation = (imageDrawable instanceof AnimationDrawable);
		// Now call the callback
		onLoadingDrawableSet(imageDrawable);
	}

	public void setPullLabel(CharSequence pullLabel) {
		mPullLabel = pullLabel;
	}

	public void setRefreshingLabel(CharSequence refreshingLabel) {
		mRefreshingLabel = refreshingLabel;
	}

	public void setReleaseLabel(CharSequence releaseLabel) {
		mReleaseLabel = releaseLabel;
	}

	@Override
	public void setTextTypeface(Typeface tf) {
		if (null != mHeaderText) {
			mHeaderText.setTypeface(tf);			
		}
	}

	public final void showInvisibleViews() {
		showHeaderText();
		showHeaderProgress();
		showHeaderImage();
		showSubHeaderText();
	}

	private void showSubHeaderText() {
		if (null != mSubHeaderText && View.INVISIBLE == mSubHeaderText.getVisibility()) {
			mSubHeaderText.setVisibility(View.VISIBLE);
		}
	}

	private void showHeaderProgress() {
		if (null != mHeaderProgress && View.INVISIBLE == mHeaderProgress.getVisibility()) {
			mHeaderProgress.setVisibility(View.VISIBLE);
		}
	}

	private void showHeaderText() {
		if (null != mHeaderText && View.INVISIBLE == mHeaderText.getVisibility()) {
			mHeaderText.setVisibility(View.VISIBLE);
		}
	}

	/**
	 * Callbacks for derivative Layouts
	 */

	protected abstract int getDefaultDrawableResId();

	protected abstract void onLoadingDrawableSet(Drawable imageDrawable);

	protected abstract void onPullImpl(float scaleOfLayout);

	protected abstract void pullToRefreshImpl();

	protected abstract void refreshingImpl();

	protected abstract void releaseToRefreshImpl();

	protected abstract void resetImpl();

	private void setSubHeaderText(CharSequence label) {
		if (null != mSubHeaderText) {
			if (TextUtils.isEmpty(label)) {
				mSubHeaderText.setVisibility(View.GONE);
			} else {
				mSubHeaderText.setText(label);

				// Only set it to Visible if we're GONE, otherwise VISIBLE will
				// be set soon
				if (View.GONE == mSubHeaderText.getVisibility()) {
					mSubHeaderText.setVisibility(View.VISIBLE);
				}
			}
		}
	}

	private void setSubTextAppearance(int value) {
		if (null != mSubHeaderText) {
			mSubHeaderText.setTextAppearance(getContext(), value);
		}
	}

	private void setSubTextColor(ColorStateList color) {
		if (null != mSubHeaderText) {
			mSubHeaderText.setTextColor(color);
		}
	}

	private void setTextAppearance(int value) {
		if (null != mHeaderText) {
			mHeaderText.setTextAppearance(getContext(), value);
		}
		if (null != mSubHeaderText) {
			mSubHeaderText.setTextAppearance(getContext(), value);
		}
	}

	private void setTextColor(ColorStateList color) {
		if (null != mHeaderText) {
			mHeaderText.setTextColor(color);
		}
		if (null != mSubHeaderText) {
			mSubHeaderText.setTextColor(color);
		}
	}
}
