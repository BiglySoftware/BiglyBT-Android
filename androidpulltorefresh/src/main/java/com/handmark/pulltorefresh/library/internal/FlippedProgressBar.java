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
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.ProgressBar;
/**
 * Right to left ProgressBar
 * @author Wonjun Kim
 *
 */
public class FlippedProgressBar extends ProgressBar {
	public FlippedProgressBar(Context context) {
		super(context);
	}

	public FlippedProgressBar(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public FlippedProgressBar(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		canvas.save();
		float centerX = this.getWidth() / 2.0f;
		float centerY = this.getHeight() / 2.0f;
		canvas.scale(-1, 1, centerX /* center of x */, centerY /* center of y */);
		super.onDraw(canvas);
		canvas.restore();
	}
	
}
