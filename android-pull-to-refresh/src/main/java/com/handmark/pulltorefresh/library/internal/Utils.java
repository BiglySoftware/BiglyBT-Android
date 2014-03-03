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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;

public class Utils {

	static final String LOG_TAG = "PullToRefresh";
	/**
	 * Android namespace for Android attributes'-related util methods
	 */
	static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";
	/**
	 *  Invalid android attribute (temporarily defined, to check android attributes' values)
	 */
	static final int INVALID_INT_VALUE = -1;
	/**
	 * Delegate warn logs at where some deprecated method has been called
	 * @param depreacted Deprecated method name
	 * @param replacement Method name to be able to switch
	 */
	public static void warnDeprecation(String depreacted, String replacement) {
		Log.w(LOG_TAG, "You're using the deprecated " + depreacted + " attr, please switch over to " + replacement);
	}
	/**
	 * Try to close {@code InputStream} without any exceptions, and ignore if some exception occurs
	 * @param is {@code InputStream} instance to close
	 */
	public static void closeSilently(InputStream is) {
		// If the instance is null, do nothing and return
		if (is == null) {
			return;
		}
		
		try {
			// try to close
			is.close();
		} catch (IOException e) {
			// try to close once more
			try {
				is.close();
			} catch (IOException e1) {
				// do nothing
			}
		}
	}
	/**
	 * Try to close {@code Reader} without any exceptions, and ignore if some exception occurs
	 * @param br {@code Reader} instance to close
	 */
	public static void closeSilently(Reader br) {
		// If the instance is null, do nothing and return
		if (br == null) {
			return;
		}
		try {
			// try to close
			br.close();
		} catch (IOException e) {
			try {
				// try to close once more
				br.close();
			} catch (IOException e1) {
				// do nothing
			}
		}		
	}
	/**
	 * Check whether android {@code attribute} exists and is set in {@code attributeSet} 
	 * @param attrs {@code AttributeSet} where the {@code attribute} is included (if that is set)
	 * @return true if the {@code attribute} exists
	 */
	@Deprecated
	public static boolean existAttributeIntValue(AttributeSet attrs, String attribute) {
		return existAttributeIntValue(attrs, ANDROID_NAMESPACE, attribute);
	}
	/**
	 * Check whether android {@code attribute} exists and is set in {@code attributeSet} 
	 * @param attrs {@code AttributeSet} where the {@code attribute} is included (if that is set)
	 * @param namespace Namespace where the {@code attribute} is defined 
	 * @param attribute Attribute to be checked
	 * @return true if the {@code attribute} exists
	 */
	@Deprecated
	public static boolean existAttributeIntValue(AttributeSet attrs, String namespace, String attribute) {
		return existAttributeIntValue(attrs, namespace, attribute, INVALID_INT_VALUE);
	}
	/**
	 * Check whether android {@code attribute} exists and is set in {@code attributeSet} 
	 * @param attrs {@code AttributeSet} where the {@code attribute} is included (if that is set)
	 * @param namespace Namespace where the {@code attribute} is defined 
	 * @param attribute Attribute to be checked
	 * @param invalidValue The flag to check that the {@code attribute} is set 
	 * @return true if the {@code attribute} exists
	 */
	@Deprecated
	public static boolean existAttributeIntValue(AttributeSet attrs, String namespace, String attribute, int invalidValue) {
		// If attrs is null, assume the attribute is not set.
		if ( attrs == null ) {
			return false;
		}
		Assert.notNull(attrs, "namespace");
		Assert.notNull(attrs, "attribute");
		
		boolean isExist = true;
		int value = attrs.getAttributeIntValue(namespace, attribute, invalidValue);
		if ( value == invalidValue ) {
			isExist = false;
		}
		return isExist;
	}
	/**
	 * Check whether android {@code attribute} exists and is set in {@code attributeSet} 
	 * @param attrs {@code AttributeSet} where the {@code attribute} is included (if that is set)
	 * @param attribute Attribute to be checked
	 * @return true if the {@code attribute} exists
	 */
	public static boolean existAttributeValue(AttributeSet attrs, String attribute) {
		return existAttributeValue(attrs, ANDROID_NAMESPACE, attribute);
	}	
	/**
	 * Check whether android {@code attribute} exists and is set in {@code attributeSet} 
	 * @param attrs {@code AttributeSet} where the {@code attribute} is included (if that is set)
	 * @param namespace Namespace where the {@code attribute} is defined 
	 * @param attribute Attribute to be checked
	 * @return true if the {@code attribute} exists
	 */
	public static boolean existAttributeValue(AttributeSet attrs, String namespace, String attribute) {
		// If attrs is null, assume the attribute is not set.
		if ( attrs == null ) {
			return false;
		}
		Assert.notNull(attrs, "namespace");
		Assert.notNull(attrs, "attribute");
		
		boolean isExist = true;
		String value = attrs.getAttributeValue(namespace, attribute);
		// Assume that it doesn't exist only when value is null.
		// An empty value can be skipped.
		if ( value == null ) {
			isExist = false;
		}
		return isExist;
	}	
	/**
	 * Get an action bar size <br />
	 * {@link //stackoverflow.com/questions/7165830/what-is-the-size-of-actionbar-in-pixels}
	 * @param context
	 */	
	public static int getActionBarSize(Context context) {
		// Calculate ActionBar height
		int actionBarHeight = 0;
		TypedValue tv = new TypedValue();
		if (context.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
		    actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data,context.getResources().getDisplayMetrics());
		}
		
		return actionBarHeight;
	}
	/**
	 * Get a status bar size <br />
	 * @param context
	 */	
	public static int getStatusBarSize(Context context) {
	      int result = 0;
	      int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
	      if (resourceId > 0) {
	          result = context.getResources().getDimensionPixelSize(resourceId);
	      } 
	      
	     return result;
	}
}
