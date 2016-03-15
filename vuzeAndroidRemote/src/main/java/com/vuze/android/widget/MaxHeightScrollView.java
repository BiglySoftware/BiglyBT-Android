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

package com.vuze.android.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.ScrollView;

import com.vuze.android.remote.R;

public class MaxHeightScrollView
	extends ScrollView
{

	private int maxHeight;

	private final int defaultHeight = -1;

	public MaxHeightScrollView(Context context) {
		super(context);
	}

	public MaxHeightScrollView(Context context, AttributeSet attrs) {
		super(context, attrs);
		if (!isInEditMode()) {
			init(context, attrs);
		}
	}

	public MaxHeightScrollView(Context context, AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		if (!isInEditMode()) {
			init(context, attrs);
		}
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public MaxHeightScrollView(Context context, AttributeSet attrs,
			int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		if (!isInEditMode()) {
			init(context, attrs);
		}
	}

	private void init(Context context, AttributeSet attrs) {
		if (attrs != null) {
			TypedArray styledAttrs = context.obtainStyledAttributes(attrs,
					R.styleable.MaxHeightScrollView);
			maxHeight = styledAttrs.getDimensionPixelSize(
					R.styleable.MaxHeightScrollView_maxHeight, defaultHeight);

			styledAttrs.recycle();
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		if (maxHeight >= 0) {
			heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight,
					MeasureSpec.AT_MOST);
		}
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}
}