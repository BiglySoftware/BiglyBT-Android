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

import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation.AnimationListener;
/**
 * AlphaAnimation Helper
 * Just supports fade-in and fade-out
 * @author Wonjun Kim
 *
 */
class AlphaAnimator {
	
	public static void fadein(View view, int duration) {
		fadein(view, duration, null);
	}
	public static void fadein(View view, int duration, AnimationListener listener) {
		animate(view, duration, listener, 0.0f, 1.0f);
	}
	
	public static void fadeout(View view, int duration) {
		fadeout(view, duration, null);
	}
	
	public static void fadeout(View view, int duration, AnimationListener listener) {
		animate(view, duration, listener, 1.0f, 0.0f);
	}
	
	private static void animate(View view, int duration, AnimationListener listener, float fromAlpha, float targetAlpha) {
		// Create new animation
		AlphaAnimation newAnimation = new AlphaAnimation(fromAlpha, targetAlpha);
		
		newAnimation.setDuration(duration);
		// Force fillAfter flag to be true
		newAnimation.setFillAfter(true);
		newAnimation.setAnimationListener(listener);
		// Start fading
		view.startAnimation(newAnimation);
		
	}
}
