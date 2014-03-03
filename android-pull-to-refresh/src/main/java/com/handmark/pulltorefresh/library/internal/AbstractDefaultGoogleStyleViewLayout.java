/*******************************************************************************
 * Copyright 2014 Naver Business Platform Corp.
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

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.handmark.pulltorefresh.library.GoogleStyleViewLayout;
import com.handmark.pulltorefresh.library.R;
/**
 * Abstract class of Default Google style view layout <br />
 * Instance of UI Components are been getting as abstract method, if you override this method, abstract get...() methods must be implemented. These will be assigned to UI instance fields. 
 * @author Wonjun Kim
 *
 */
public abstract class AbstractDefaultGoogleStyleViewLayout extends GoogleStyleViewLayout {

	private FrameLayout mInnerLayout;
	private TextView mHeaderText;
	private TextView mSubHeaderText;
	
	private CharSequence mPullLabel;
	private CharSequence mRefreshingLabel;
	private CharSequence mReleaseLabel;
	
	public AbstractDefaultGoogleStyleViewLayout(Context context, TypedArray attrs) {
		super(context, attrs);

		initImpl(context, attrs);
		initComponents(context, attrs);
		initProperties(context, attrs);
		reset();
	}

	protected abstract void initImpl(Context context, TypedArray attrs);
	/**
	 * Assign get..() methods to fields
	 * @param context
	 * @param attrs
	 */
	private void initComponents(Context context, TypedArray attrs) {
		mInnerLayout = getInnerLayout(context, attrs);
		mHeaderText = getHeaderText(context, attrs);
		mSubHeaderText = geSubHeaderTextLayout(context, attrs);
	}
	/**
	 * Returns SubHeaderText Layout instance to be displayed
	 * @param context
	 * @param attrs
	 * @return
	 */
	protected abstract TextView geSubHeaderTextLayout(Context context, TypedArray attrs);
	/**
	 * Returns Inner Layout instance to be displayed
	 * @param context
	 * @param attrs
	 * @return
	 */
	protected abstract TextView getHeaderText(Context context, TypedArray attrs);
	/**
	 * Returns HeaderText Layout instance to be displayed
	 * @param context
	 * @param attrs
	 * @return
	 */
	protected abstract FrameLayout getInnerLayout(Context context, TypedArray attrs);

	@Override
	public final void setHeight(int height) {
		// set Inner layout's height too
		ViewGroup.LayoutParams lp = mInnerLayout.getLayoutParams();
		lp.height = height;
		ViewGroup.LayoutParams thisLp = getLayoutParams();
		if ( thisLp != null ) {
			thisLp.height = height;
		}
	
		requestLayout();
	}
	/**
	 * Initialize text color, sub text color , and text appearance. <br />
	 * If text color is not set, set default color by calling {@link #getDefaultTextColor(Context, TypedArray)}, or {@link #getDefaultSubTextColor(Context, TypedArray)}.<br />
	 * Also, if background color is not set, set default color by calling {@link #getDefaultBackgroundColor(Context, TypedArray)}
	 * @param context
	 * @param attrs
	 */
	protected void initProperties(Context context, TypedArray attrs) {
		// Load Loading Layout Labels
		loadLoadingLayoutLabels(context, attrs);
		if (attrs.hasValue(R.styleable.PullToRefresh_ptrHeaderBackground)) {
			Drawable background = attrs.getDrawable(R.styleable.PullToRefresh_ptrHeaderBackground);
			if ( null != background ) {
				ViewCompat.setBackground(this, background);		
			}
		} else {
			// Set background to white as default
			setBackgroundColor(getDefaultBackgroundColor(context, attrs));
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
		} else {
			// Set Text color to black as default 
			setTextColor(getDefaultTextColor(context, attrs));
		}

		if (attrs.hasValue(R.styleable.PullToRefresh_ptrHeaderSubTextColor)) {
			ColorStateList colors = attrs.getColorStateList(R.styleable.PullToRefresh_ptrHeaderSubTextColor);
			if (null != colors) {
				setSubTextColor(colors);
			}
		} else {
			// Set Text color to black as default 
			setSubTextColor(getDefaultSubTextColor(context, attrs));
		}
		
		initPropertiesImpl(context, attrs);

	}

	protected abstract void initPropertiesImpl(Context context, TypedArray attrs);
	/**
	 * Use {@code Color.White} color as default to set background color. <br />
	 * If you want to change this default, override this method. 
	 * @param context
	 * @param attrs
	 * @return Color value
	 */
	protected int getDefaultBackgroundColor(Context context, TypedArray attrs) {
		return Color.WHITE;
	}
	/**
	 * Use {@code Color.Black} color as default to set text color. <br />
	 * If you want to change this default, override this method. 
	 * @param context
	 * @param attrs
	 * @return Color value
	 */
	protected int getDefaultTextColor(Context context, TypedArray attrs) {
		return Color.BLACK;
	}
	/**
	 * Use {@code Color.Black} color as default to set sub text color. <br />
	 * If you want to change this default, override this method. 
	 * @param context
	 * @param attrs
	 * @return Color value
	 */
	protected int getDefaultSubTextColor(Context context, TypedArray attrs) {
		return Color.BLACK;
	}

	/**
	 * Load labels of pull, refresh, release, and assign into fields
	 * <br />Convert an each attribute value such as {@code ptrPullLabel}, {@code ptrRefreshLabel} or {@code ptrReleaseLabel} to each label field if each value exists.
	 * <br />Or if not, then the each label is assigned some string as default
	 * <br />
	 * NOTE : This method <b>Must</b> be modified if kinds of {@code Mode} are increased.
	 * @param attrs 
	 */
	private void loadLoadingLayoutLabels(Context context, TypedArray attrs) {
		mPullLabel = loadPullLabel(context, attrs);
		mRefreshingLabel = loadRefreshingLabel(context, attrs);
		mReleaseLabel = loadReleaseLabel(context, attrs);
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
	protected String loadPullLabel(Context context, TypedArray attrs) {
		// Pull Label
		if (attrs.hasValue(R.styleable.PullToRefresh_ptrPullLabel)) {
			return attrs.getString(R.styleable.PullToRefresh_ptrPullLabel);
		} 
		
		int stringId = R.string.pull_to_refresh_pull_label;
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
	 * @return String to be a refreshing label  
	 */
	protected String loadRefreshingLabel(Context context, TypedArray attrs) {
		// Refresh Label
		if (attrs.hasValue(R.styleable.PullToRefresh_ptrRefreshLabel)) {
			return attrs.getString(R.styleable.PullToRefresh_ptrRefreshLabel);
		} 
		
		int stringId = R.string.pull_to_refresh_refreshing_label;
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
	 * @return String to be a refreshing label  
	 */
	protected String loadReleaseLabel(Context context, TypedArray attrs) {
		// Release Label
		if (attrs.hasValue(R.styleable.PullToRefresh_ptrReleaseLabel)) {
			return attrs.getString(R.styleable.PullToRefresh_ptrReleaseLabel);
		} 
		
		int stringId = R.string.pull_to_refresh_release_label;
		return context.getString(stringId);
		
	}

	@Override
	public final void setWidth(int width) {
		ViewGroup.LayoutParams lp = (ViewGroup.LayoutParams) getLayoutParams();
		lp.width = width;
		requestLayout();
	}

	@Override
	public final int getContentSize() {
		return mInnerLayout.getHeight();
	}

	public final void hideAllViews() {
		hideHeaderText();
		hideSubHeaderText();
	}

	private void hideHeaderText() {
		if (null != mHeaderText && View.VISIBLE == mHeaderText.getVisibility()) {
			mHeaderText.setVisibility(View.INVISIBLE);
		}
	}

	private void hideSubHeaderText() {
		if (null != mHeaderText && View.VISIBLE == mSubHeaderText.getVisibility()) {
			mSubHeaderText.setVisibility(View.INVISIBLE);
		}
	}

	protected abstract void pullToRefreshImpl();
	protected abstract void releaseToRefreshImpl();
	protected abstract void refreshingImpl();
	protected abstract void resetImpl();
	protected abstract void onPullImpl(float scale);
	
	@Override
	public final void pullToRefresh() {
		if (null != mHeaderText) {
			mHeaderText.setText(mPullLabel);
		}
		pullToRefreshImpl();
	}
	
	@Override
	public final void refreshing() {
		if (null != mHeaderText) {
			mHeaderText.setText(mRefreshingLabel);
		}

		if (null != mSubHeaderText) {
			mSubHeaderText.setVisibility(View.GONE);
		}
		
		refreshingImpl();
	}

	@Override
	public final void releaseToRefresh() {
		if (null != mHeaderText) {
			mHeaderText.setText(mReleaseLabel);
		}
		
		releaseToRefreshImpl();
	}

	@Override
	public final void reset() {
		if (null != mHeaderText) {
			mHeaderText.setText(mPullLabel);
		}

		if (null != mSubHeaderText) {
			if (TextUtils.isEmpty(mSubHeaderText.getText())) {
				mSubHeaderText.setVisibility(View.GONE);
			} else {
				mSubHeaderText.setVisibility(View.VISIBLE);
			}
		}
		resetImpl();
	}

	public void setLastUpdatedLabel(CharSequence label) {
		setSubHeaderText(label);
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

	public void setTextTypeface(Typeface tf) {
		if (null != mHeaderText) {
			mHeaderText.setTypeface(tf);			
		}
	}

	public final void showInvisibleViews() {
		showHeaderText();
		showSubHeaderText();
	}

	private void showSubHeaderText() {
		if (null != mSubHeaderText && View.INVISIBLE == mSubHeaderText.getVisibility()) {
			mSubHeaderText.setVisibility(View.VISIBLE);
		}
	}

	private void showHeaderText() {
		if (null != mHeaderText && View.INVISIBLE == mHeaderText.getVisibility()) {
			mHeaderText.setVisibility(View.VISIBLE);
		}
	}

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

	private void setSubTextColor(int color) {
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

	private void setTextColor(int color) {
		if (null != mHeaderText) {
			mHeaderText.setTextColor(color);
		}
		if (null != mSubHeaderText) {
			mSubHeaderText.setTextColor(color);
		}
	}
	
	@Override
	public void onPull(float scale) {
		onPullImpl(scale);
	}
}
