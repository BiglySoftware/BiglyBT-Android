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
package com.handmark.pulltorefresh.library;

import android.content.Context;
import android.content.res.TypedArray;
import android.view.ViewGroup;
import android.widget.FrameLayout;
/**
 * Abstract class of Google style progress layout
 * You can override this class and implement specific layout, for example, {@see DefaultGoogleStyleProgressLayout}.
 * 
 * @author Wonjun Kim
 *
 */
public abstract class GoogleStyleProgressLayout extends FrameLayout implements IGoogleStyleProgressLayout {
	public GoogleStyleProgressLayout(Context context, TypedArray attrs){
		super(context);
		
	}
	/**
	 * Set Y position of this layout by using margin property.<br /> 
	 * If you don't want to set default y position given by {@code PullToRefreshBase}, you have to this method and customize it.     
	 */
	public void setTopMargin(int height) {
		ViewGroup.LayoutParams params = getLayoutParams();
		if ( params instanceof FrameLayout.LayoutParams ) {
			((FrameLayout.LayoutParams) params).topMargin = height;
		}
	}
	/**
	 * Create a specific {@code LayoutParams}.<br /> 
	 * Pull To Refresh will add this layout with applying this {@code LayoutParams} to the layout     
	 * @return {@code LayoutParams} you implemented
	 */	
	public FrameLayout.LayoutParams createLayoutParams() {
		return GoogleStyleProgressLayout.createDefaultLayoutParams(getContext());
	}
	/**
	 * Create a default specific {@code LayoutParams}.<br /> 
	 * @param Context     
	 * @return Default {@code LayoutParams}  
	 */
	public static FrameLayout.LayoutParams createDefaultLayoutParams(Context context) {
		@SuppressWarnings("deprecation")
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		return params;
	}
}
