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

import java.lang.reflect.Constructor;

import java.lang.reflect.InvocationTargetException;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.Log;
import android.view.View;

import com.handmark.pulltorefresh.configuration.xml.PullToRefreshXmlConfiguration;
import com.handmark.pulltorefresh.library.PullToRefreshBase.Mode;
import com.handmark.pulltorefresh.library.PullToRefreshBase.Orientation;
import com.handmark.pulltorefresh.library.internal.DefaultGoogleStyleViewLayout;
import com.handmark.pulltorefresh.library.GoogleStyleViewLayout;
/**
 * Factory which creates google style view layouts 
 * <br />Google style view layouts must be listed in pulltorefresh.xml as "PullToRefresh/GoogleStyleViewLayouts/layout" nodes
 * @author Wonjun Kim
 */
class GoogleStyleViewLayoutFactory {

	private static final String LOG_TAG = GoogleStyleViewLayoutFactory.class
			.getSimpleName();
	/**
	 * Create the class token matched by <b>{@code layoutCode}</b>
	 * @param layoutCode Google style view layout code, which must be defined in pulltorefresh.xml 
	 * @return Class token which is matched by {@code layoutCode}, or the class token of {@code RotateGoogleStyleViewLayout} instance if not
	 */
	public static Class<? extends GoogleStyleViewLayout> createGoogleStyleViewLayoutClazzByLayoutCode(String layoutCode) {
		String clazzName = PullToRefreshXmlConfiguration.getInstance().getGoogleStyleViewLayoutClazzName(layoutCode);
		return createGoogleStyleViewLayoutClazz(clazzName);
	}
	/**
	 * Create a {@code GoogleStyleViewLayout} instance matched by <b>{@code clazz} token</b> 
	 * @param layoutCode Google style view layout code, which must be defined in pulltorefresh.xml
	 * @param context 
	 * @return {@code GoogleStyleViewLayout} instance if the class matched by {@code layoutCode} exists, or {@code RotateGoogleStyleViewLayout} instance if not  
	 */
	@SuppressWarnings("unchecked")
	public static Class<? extends GoogleStyleViewLayout> createGoogleStyleViewLayoutClazz(
			String clazzName) {
		Class<? extends GoogleStyleViewLayout> googleStyleViewLayoutClazz = null;
		if ( clazzName == null ) {
			googleStyleViewLayoutClazz = DefaultGoogleStyleViewLayoutFactory.createGoogleStyleViewLayoutClazz(clazzName);
			return googleStyleViewLayoutClazz;
		}
		
		try {
			googleStyleViewLayoutClazz = (Class<GoogleStyleViewLayout>) Class.forName(clazzName);

		} catch (ClassNotFoundException e) {
			Log.e(LOG_TAG,"The google style view layout you have chosen class has not been found.", e);
			googleStyleViewLayoutClazz = DefaultGoogleStyleViewLayoutFactory.createGoogleStyleViewLayoutClazz(clazzName);
		} 

		return googleStyleViewLayoutClazz;
	}
	/**
	 * Create a {@code GoogleStyleViewLayout} instance matched by <b>{@code layoutCode}</b> 
	 * @param layoutCode Google style view layout code, which must be defined in pulltorefresh.xml
	 * @param context 
	 * @return {@code GoogleStyleViewLayout} instance if the class matched by {@code layoutCode} exists, or {@code DefaultGoogleStyleViewLayout} instance if not  
	 */
	public static GoogleStyleViewLayout createGoogleStyleViewLayout(String layoutCode, Context context, TypedArray attrs) {
		Class<? extends GoogleStyleViewLayout> clazz = createGoogleStyleViewLayoutClazz(layoutCode);
		return createGoogleStyleViewLayout(clazz, context, attrs);
	}
	/**
	 * Create a {@code GoogleStyleViewLayout} instance matched by <b>{@code clazz} token</b> 
	 * @param layoutCode Google style view layout code, which must be defined in pulltorefresh.xml
	 * @param context 
	 * @return {@code GoogleStyleViewLayout} instance if the class matched by {@code layoutCode} exists, or {@code DefaultGoogleStyleViewLayout} instance if not  
	 */
	public static GoogleStyleViewLayout createGoogleStyleViewLayout(
			Class<? extends GoogleStyleViewLayout> clazz, Context context, TypedArray attrs) {
		GoogleStyleViewLayout layout = null;
		// Prevent NullPointerException
		if ( clazz == null ) {
			Log.i(LOG_TAG, "The Class token of the GoogleStyleViewLayout is missing. Default google style view layout will be used.");
			clazz = DefaultGoogleStyleViewLayoutFactory.createGoogleStyleViewLayoutClazz("");
		}
		
		layout = tryNewInstance(clazz, context, attrs);

		// If trying to create new instance has failed,
		if (layout == null) {
			layout = DefaultGoogleStyleViewLayoutFactory.createGoogleStyleViewLayout(clazz, context, attrs);
		}

		layout.setVisibility(View.INVISIBLE);
		return layout;
	}
	private static GoogleStyleViewLayout tryNewInstance(
			Class<? extends GoogleStyleViewLayout> clazz, Context context, TypedArray attrs) {
		GoogleStyleViewLayout layout = null;
		try {
			Constructor<? extends GoogleStyleViewLayout> constructor = clazz
					.getConstructor(Context.class, TypedArray.class);
			layout = (GoogleStyleViewLayout) constructor.newInstance(context, attrs);

		} catch (IllegalArgumentException e) {
			Log.e(LOG_TAG, "The google style view layout has failed to be created. ", e);
		} catch (InvocationTargetException e) {
			Log.e(LOG_TAG, "The google style view layout has failed to be created. ", e);
		} catch (SecurityException e) {
			Log.e(LOG_TAG, "The google style view layout has failed to be created. ", e);
		} catch (NoSuchMethodException e) {
			Log.e(LOG_TAG, "The google style view layout has failed to be created. ", e);
		} catch (InstantiationException e) {
			Log.e(LOG_TAG, "The google style view layout has failed to be created. ", e);
		} catch (IllegalAccessException e) {
			Log.e(LOG_TAG, "The google style view layout has failed to be created. ", e);
		} catch (NullPointerException e) {
			Log.e(LOG_TAG, "The google style view layout has failed to be created. ", e);
		}
		return layout;
	}
	/**
	 * Factory which creates a default google style view layout instance. This is used when {@code GoogleStyleViewLayoutFactory} fails to create a instance
	 * @author Wonjun Kim
	 *
	 */
	private static class DefaultGoogleStyleViewLayoutFactory {
		/**
		 * @param clazzName This class name is being ignored
		 * @return Class token of {@code DefaultGoogleStyleViewLayout}
		 */
		public static Class<? extends GoogleStyleViewLayout> createGoogleStyleViewLayoutClazz(
				String clazzName) {
			return DefaultGoogleStyleViewLayout.class;
		}
		/**
		 * @param clazz Class token is being ignored.
		 * @param context
		 * @return {@code DefaultGoogleStyleViewLayout} instance
		 */
		public static GoogleStyleViewLayout createGoogleStyleViewLayout(
				Class<? extends GoogleStyleViewLayout> clazz, Context context, TypedArray attrs) {

			return new DefaultGoogleStyleViewLayout(context, attrs);
		}

	}

}
