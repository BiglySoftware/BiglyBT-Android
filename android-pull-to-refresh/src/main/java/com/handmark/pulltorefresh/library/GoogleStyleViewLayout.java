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
 * Abstract class of Google style view layout
 * You can override this class and implement specific layout like {@link LoadingLayout} style.
 * @author Wonjun Kim
 *
 */
public abstract class GoogleStyleViewLayout extends FrameLayout implements IGoogleStyleViewLayout {

	public GoogleStyleViewLayout(Context context, TypedArray attrs) {
		super(context);
	}
	
	public void setHeight(int height) {
		ViewGroup.LayoutParams lp = (ViewGroup.LayoutParams) getLayoutParams();
		lp.height = height;
		requestLayout();
	}

	public void setWidth(int width) {
		ViewGroup.LayoutParams lp = (ViewGroup.LayoutParams) getLayoutParams();
		lp.width = width;
		requestLayout();
	}

	public int getContentSize() {
		return getHeight();
	}
}
