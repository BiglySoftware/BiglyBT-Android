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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.handmark.pulltorefresh.configuration.xml.PullToRefreshXmlConfiguration;

import android.annotation.TargetApi;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.util.Log;
import android.view.View;

@SuppressWarnings("deprecation")
public class ViewCompat {
	/**
	 * @author Wonjun Kim
	 */
	private static class Methods {
		private static final String LOG_TAG = Methods.class.getName();
		
		private static Method setLayerTypeMethod;
		private static Method postOnAnimationMethod;
		private static Method setBackgroundMethod;
		
		static {
			initializeMethods();
		}
		
		@SuppressWarnings("unchecked")
		private static void initializeMethods() {
			
			Class<? extends View> viewClazz = null;
			
			// Initialize android.view.View class token
			try {
				viewClazz = (Class<? extends View>) Class.forName("android.view.View");
			} catch (ClassNotFoundException e) {
				Log.e(LOG_TAG, "android.view.View class has not been found. Maybe Pull To Refresh might work not correctly.", e);
			} 
			
			// If viewClazz fails to initialize, skip creating methods
			if ( viewClazz == null ) {
				return;
			}
			
			// Initialize setLayerType()
			try {
				setLayerTypeMethod = viewClazz.getMethod("setLayerType", int.class, Paint.class);
			} catch (NoSuchMethodException e) {
				Log.e(LOG_TAG, "android.view.View.setLayerType() method has not been found. Maybe Pull To Refresh might work not correctly.", e);
			}
			
			// Initialize postOnAnimation()
			try {
				postOnAnimationMethod = viewClazz.getMethod("postOnAnimation", Runnable.class);
			} catch (NoSuchMethodException e) {
				Log.e(LOG_TAG, "android.view.View.postOnAnimation() method has not been found. Maybe Pull To Refresh might work not correctly.", e);
			}
			
			// Initialize setBackground() 
			try {
				setBackgroundMethod = viewClazz.getMethod("setBackground", Drawable.class);
			} catch (NoSuchMethodException e) {
				Log.e(LOG_TAG, "android.view.View.setBackground() method has not been found. Maybe Pull To Refresh might work not correctly.", e);
			}			
			
		}		
		private static void setLayerType(View view, int layerType) {
			Assert.notNull(view, "view");
			
			if ( setLayerTypeMethod == null ) {
				Log.e(LOG_TAG, "android.view.View.setLayerType() method token has not been initialized.");
			}
			try {
				setLayerTypeMethod.invoke(view, layerType, null /* android.graphics.Paint */);
			} catch (IllegalArgumentException e) {
				Log.e(LOG_TAG, "Some argument is illegal to call android.view.View.setLayerType().", e);
			} catch (IllegalAccessException e) {
				Log.e(LOG_TAG, "It has failed to call android.view.View.setLayerType().", e);
			} catch (InvocationTargetException e) {
				Log.e(LOG_TAG, "It has failed to call android.view.View.setLayerType().", e);
			}
		}
		
		private static void postOnAnimation(View view, Runnable runnable) {
			Assert.notNull(view, "view");
			
			if ( postOnAnimationMethod == null ) {
				Log.e(LOG_TAG, "android.view.View.postOnAnimation() method token has not been initialized.");
			}
			try {
				postOnAnimationMethod.invoke(view, runnable);
			} catch (IllegalArgumentException e) {
				Log.e(LOG_TAG, "Some argument is illegal to call android.view.View.postOnAnimation().", e);
			} catch (IllegalAccessException e) {
				Log.e(LOG_TAG, "It has failed to call android.view.View.postOnAnimation().", e);
			} catch (InvocationTargetException e) {
				Log.e(LOG_TAG, "It has failed to call android.view.View.postOnAnimation().", e);
			}
		}
		
		private static void setBackground(View view, Drawable background) {
			Assert.notNull(view, "view");
			
			if ( setBackgroundMethod == null ) {
				Log.e(LOG_TAG, "android.view.View.setBackground() method token has not been initialized.");
			}
			try {
				setBackgroundMethod.invoke(view, background);
			} catch (IllegalArgumentException e) {
				Log.e(LOG_TAG, "Some argument is illegal to call android.view.View.setBackground().", e);
			} catch (IllegalAccessException e) {
				Log.e(LOG_TAG, "It has failed to call android.view.View.setBackground().", e);
			} catch (InvocationTargetException e) {
				Log.e(LOG_TAG, "It has failed to call android.view.View.setBackground().", e);
			}
		}
	} 
	/*
	 * Copied from android.os.Build.VERSION_CODES 
	 */
	public static class VERSION_CODES {
		public static final int CUR_DEVELOPMENT = 10000;
		public static final int BASE = 1;
		public static final int BASE_1_1 = 2;
		public static final int CUPCAKE = 3;
		public static final int DONUT = 4;
		public static final int ECLAIR = 5;
		public static final int ECLAIR_0_1 = 6;
		public static final int ECLAIR_MR1 = 7;
		public static final int FROYO = 8;
		public static final int GINGERBREAD = 9;
		public static final int GINGERBREAD_MR1 = 10;
		public static final int HONEYCOMB = 11;
		public static final int HONEYCOMB_MR1 = 12;
		public static final int HONEYCOMB_MR2 = 13;
		public static final int ICE_CREAM_SANDWICH = 14;
		public static final int ICE_CREAM_SANDWICH_MR1 = 15;
		public static final int JELLY_BEAN = 16;
		public static final int JELLY_BEAN_MR1 = 17;
		public static final int JELLY_BEAN_MR2 = 18;
		public static final int KITKAT = 19;
	}

	public static void postOnAnimation(View view, Runnable runnable) {
		if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
			SDK16.postOnAnimation(view, runnable);
		} else {
			view.postDelayed(runnable, 16);
		}
	}

	public static void setBackground(View view, Drawable background) {
		if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
			SDK16.setBackground(view, background);
		} else {
			view.setBackgroundDrawable(background);
		}
	}

	public static void setLayerType(View view, int layerType) {
		if (VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB) {
			SDK11.setLayerType(view, layerType);
		}
	}

	@TargetApi(11)
	static class SDK11 {

		public static void setLayerType(View view, int layerType) {
			Methods.setLayerType(view, layerType);
		}
	}

	@TargetApi(16)
	static class SDK16 {

		public static void postOnAnimation(View view, Runnable runnable) {
			Methods.postOnAnimation(view, runnable);
		}

		public static void setBackground(View view, Drawable background) {
			Methods.setBackground(view, background);
		}

	}

}
