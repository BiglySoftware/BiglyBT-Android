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

import com.handmark.pulltorefresh.library.IIndicatorLayout;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;
/**
 * @see IIndicatorLayout
 * @author Wonjun Kim
 */
public abstract class IndicatorLayout extends FrameLayout implements IIndicatorLayout {
	
	public IndicatorLayout(Context context) {
		super(context);
	}

	public IndicatorLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public IndicatorLayout(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}
	/**
	 * Create a specific {@code LayoutParams}.<br /> 
	 * Pull To Refresh will add this layout with applying this {@code LayoutParams} to the layout     
	 * @return {@code LayoutParams} which is applied if this indicator layout is a header of Pull To Refresh
	 */
	public abstract LayoutParams createApplicableHeaderLayoutParams();
	/**
	 * Create a specific {@code LayoutParams}.<br /> 
	 * Pull To Refresh will add this layout with applying this {@code LayoutParams} to the layout     
	 * @return {@code LayoutParams} which is applied if this indicator layout is a footer of Pull To Refresh 
	 */
	public abstract LayoutParams createApplicableFooterLayoutParams();

}
