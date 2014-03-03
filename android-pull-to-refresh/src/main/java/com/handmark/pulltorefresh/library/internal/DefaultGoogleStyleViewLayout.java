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

import com.handmark.pulltorefresh.library.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;
/**
 * Specific implemented layout of Google style view layout<br />
 * @author Wonjun Kim
 */
public class DefaultGoogleStyleViewLayout extends AbstractDefaultGoogleStyleViewLayout {

	public DefaultGoogleStyleViewLayout(Context context, TypedArray attrs) {
		super(context, attrs);
	}
	/**
	 * Inflate layout by {@code inflateId}
	 * @param context
	 * @param inflateId inflate id value like {@code R.layout...} 
	 */
	private void initInflate(Context context, int inflateId) {
		LayoutInflater.from(context).inflate(inflateId, this);
	}
	/**
	 * Initialize layout
	 */
	@Override
	protected void initImpl(Context context, TypedArray attrs) {
		initInflate(context, getLayoutInflateId());
	}
	/**
	 * Returns inflate id to be used when inflating
	 * If you want to change layout xml, you have to override this method and change a return value
	 * @return Inflate id
	 */
	protected int getLayoutInflateId() {
		return R.layout.pull_to_refresh_header_google_style;
	}
	/**
	 * Bind SubHeaderText layout Component to some field
	 */
	@Override
	protected TextView geSubHeaderTextLayout(Context context, TypedArray attrs) {
		return (TextView) findViewById(R.id.pull_to_refresh_sub_text);
	}
	/**
	 * Bind HeaderText layout Component to some field
	 */
	@Override
	protected TextView getHeaderText(Context context, TypedArray attrs) {
		return (TextView) findViewById(R.id.pull_to_refresh_text);
	}
	/**
	 * Bind Inner layout Component to some field
	 */
	@Override
	protected FrameLayout getInnerLayout(Context context, TypedArray attrs) {
		return (FrameLayout) findViewById(R.id.fl_inner_for_google_style);
	}

	@Override
	protected void initPropertiesImpl(Context context, TypedArray attrs) {
		// do nothing
	}

	@Override
	protected void pullToRefreshImpl() {
		// do nothing
	}

	@Override
	protected void releaseToRefreshImpl() {
		// do nothing		
	}

	@Override
	protected void refreshingImpl() {
		// do nothing		
	}

	@Override
	protected void resetImpl() {
		// do nothing		
	}

	@Override
	protected void onPullImpl(float scale) {
		// do nothing
	}
}
