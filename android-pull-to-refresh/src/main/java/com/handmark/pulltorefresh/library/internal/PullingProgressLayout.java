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
import android.content.res.Resources;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
/**
 * Layout Class displaying specialized progress bar whose progress starts from center,
 * @author Wonjun Kim
 *
 */
public class PullingProgressLayout extends LinearLayout {

	private ProgressBar mPullingLeftBar;
	private ProgressBar mPullingRightBar;

	public PullingProgressLayout(Context context) {
		super(context);
		initializeLayout(context);
		
	}

	public PullingProgressLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		initializeLayout(context);
	}

	private void initializeLayout(Context context) {
		Resources res = context.getResources();
		
		LayoutInflater.from(context).inflate(R.layout.pulling_progress_layout, this);
		
		mPullingLeftBar = (ProgressBar) findViewById(R.id.pulling_left_progressbar);
		mPullingRightBar = (ProgressBar) findViewById(R.id.pulling_right_progressbar);
		
		mPullingLeftBar.setProgressDrawable(res.getDrawable(R.drawable.progress_horizontal_holo_light));
		mPullingRightBar.setProgressDrawable(res.getDrawable(R.drawable.progress_horizontal_holo_light));
		
		mPullingLeftBar.setMax(100);
		mPullingRightBar.setMax(100);
		
		mPullingLeftBar.setProgress(40);
		mPullingRightBar.setProgress(40);
	}
	/**
	 * Route percent value to internal bars 
	 * @param percent Positive integer value. max is 100. 
	 */
	public void setPercent(int percent) {
		if ( percent > 100 ) {
			percent = 100;
		}
		
		if ( percent < 0 ) {
			percent = 0;
		}
		
		mPullingLeftBar.setProgress(percent);
		mPullingRightBar.setProgress(percent);
	}
}
