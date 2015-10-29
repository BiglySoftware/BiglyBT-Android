/*******************************************************************************
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

import com.handmark.pulltorefresh.library.internal.IndicatorLayout;

/**
 * Indicator layout which react pulling or releasing of Pull To Refresh
 * <br />
 * NOTE : To make new Indicator layout, You have to override {@link IndicatorLayout} instead of IIndicatorLayout. 
 * <br /> Because {@link PullToRefreshAdapterViewBase} uses indicator layouts as a kind of View component. {@link IndicatorLayout} is a descendant class of {@code View}.
 * 
 * @author Wonjun Kim
 */
public interface IIndicatorLayout {
	/**
	 * Show the Indicator Layout 
	 */
	public void show();
	/**
	 * Hide the Indicator Layout 
	 */
	public void hide();
	/**
	 * Check whether this indicator layout is being shown or not
	 * @return true if the indicator layout is visible now 
	 */
	public boolean isVisible();
	/**
	 * Make a Action when "PullToRefresh" event has been fired
	 */
	public void pullToRefresh();
	/**
	 * Make a Action when "ReleaseToRefresh" event has been fired
	 */
	public void releaseToRefresh();
}
