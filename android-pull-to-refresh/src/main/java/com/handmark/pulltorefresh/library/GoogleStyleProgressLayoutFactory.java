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
import com.handmark.pulltorefresh.library.internal.DefaultGoogleStyleProgressLayout;
import com.handmark.pulltorefresh.library.GoogleStyleProgressLayout;
/**
 * Factory which creates google style progress layouts 
 * <br />Google style progress layouts must be listed in pulltorefresh.xml as "PullToRefresh/GoogleStyleProgressLayouts/layout" nodes
 * @author Wonjun Kim
 */
class GoogleStyleProgressLayoutFactory {

	private static final String LOG_TAG = GoogleStyleProgressLayoutFactory.class
			.getSimpleName();
	/**
	 * Create the class token matched by <b>{@code layoutCode}</b>
	 * @param layoutCode Google style progress layout code, which must be defined in pulltorefresh.xml 
	 * @return Class token which is matched by {@code layoutCode}, or the class token of {@code RotateGoogleStyleProgressLayout} instance if not
	 */
	public static Class<? extends GoogleStyleProgressLayout> createGoogleStyleProgressLayoutClazzByLayoutCode(String layoutCode) {
		String clazzName = PullToRefreshXmlConfiguration.getInstance().getGoogleStyleProgressLayoutClazzName(layoutCode);
		return createGoogleStyleProgressLayoutClazz(clazzName);
	}
	/**
	 * Create a {@code GoogleStyleProgressLayout} instance matched by <b>{@code clazz} token</b> 
	 * @param layoutCode Google style progress layout code, which must be defined in pulltorefresh.xml
	 * @param context 
	 * @return {@code GoogleStyleProgressLayout} instance if the class matched by {@code layoutCode} exists, or {@code RotateGoogleStyleProgressLayout} instance if not  
	 */
	@SuppressWarnings("unchecked")
	public static Class<? extends GoogleStyleProgressLayout> createGoogleStyleProgressLayoutClazz(
			String clazzName) {
		Class<? extends GoogleStyleProgressLayout> googleStyleProgressLayoutClazz = null;
		if ( clazzName == null ) {
			googleStyleProgressLayoutClazz = DefaultGoogleStyleProgressLayoutFactory.createGoogleStyleProgressLayoutClazz(clazzName);
			return googleStyleProgressLayoutClazz;
		}
		
		try {
			googleStyleProgressLayoutClazz = (Class<GoogleStyleProgressLayout>) Class.forName(clazzName);

		} catch (ClassNotFoundException e) {
			Log.e(LOG_TAG,"The google style progress layout you have chosen class has not been found.", e);
			googleStyleProgressLayoutClazz = DefaultGoogleStyleProgressLayoutFactory.createGoogleStyleProgressLayoutClazz(clazzName);
		} 

		return googleStyleProgressLayoutClazz;
	}
	/**
	 * Create a {@code GoogleStyleProgressLayout} instance matched by <b>{@code layoutCode}</b> 
	 * @param layoutCode Google style progress layout code, which must be defined in pulltorefresh.xml
	 * @param context 
	 * @return {@code GoogleStyleProgressLayout} instance if the class matched by {@code layoutCode} exists, or {@code DefaultGoogleStyleProgressLayout} instance if not  
	 */
	public static GoogleStyleProgressLayout createGoogleStyleProgressLayout(String layoutCode, Context context, TypedArray attrs) {
		Class<? extends GoogleStyleProgressLayout> clazz = createGoogleStyleProgressLayoutClazz(layoutCode);
		return createGoogleStyleProgressLayout(clazz, context, attrs);
	}
	/**
	 * Create a {@code GoogleStyleProgressLayout} instance matched by <b>{@code clazz} token</b> 
	 * @param layoutCode Google style progress layout code, which must be defined in pulltorefresh.xml
	 * @param context 
	 * @return {@code GoogleStyleProgressLayout} instance if the class matched by {@code layoutCode} exists, or {@code DefaultGoogleStyleProgressLayout} instance if not  
	 */
	public static GoogleStyleProgressLayout createGoogleStyleProgressLayout(
			Class<? extends GoogleStyleProgressLayout> clazz, Context context, TypedArray attrs) {
		GoogleStyleProgressLayout layout = null;
		// Prevent NullPointerException
		if ( clazz == null ) {
			Log.i(LOG_TAG, "The Class token of the GoogleStyleProgressLayout is missing. Default google style progress layout will be used.");
			clazz = DefaultGoogleStyleProgressLayoutFactory.createGoogleStyleProgressLayoutClazz("");
		}
		
		layout = tryNewInstance(clazz, context, attrs);

		// If trying to create new instance has failed,
		if (layout == null) {
			layout = DefaultGoogleStyleProgressLayoutFactory.createGoogleStyleProgressLayout(clazz, context, attrs);
		}

		layout.setVisibility(View.INVISIBLE);
		return layout;
	}
	private static GoogleStyleProgressLayout tryNewInstance(
			Class<? extends GoogleStyleProgressLayout> clazz, Context context, TypedArray attrs) {
		GoogleStyleProgressLayout layout = null;
		try {
			Constructor<? extends GoogleStyleProgressLayout> constructor = clazz
					.getConstructor(Context.class, TypedArray.class);
			layout = (GoogleStyleProgressLayout) constructor.newInstance(context, attrs);

		} catch (IllegalArgumentException e) {
			Log.e(LOG_TAG, "The google style progress layout has failed to be created. ", e);
		} catch (InvocationTargetException e) {
			Log.e(LOG_TAG, "The google style progress layout has failed to be created. ", e);
		} catch (SecurityException e) {
			Log.e(LOG_TAG, "The google style progress layout has failed to be created. ", e);
		} catch (NoSuchMethodException e) {
			Log.e(LOG_TAG, "The google style progress layout has failed to be created. ", e);
		} catch (InstantiationException e) {
			Log.e(LOG_TAG, "The google style progress layout has failed to be created. ", e);
		} catch (IllegalAccessException e) {
			Log.e(LOG_TAG, "The google style progress layout has failed to be created. ", e);
		} catch (NullPointerException e) {
			Log.e(LOG_TAG, "The google style progress layout has failed to be created. ", e);
		}
		return layout;
	}
	/**
	 * Factory which creates a default google style progress layout instance. This is used when {@code GoogleStyleProgressLayoutFactory} fails to create a instance
	 * @author Wonjun Kim
	 *
	 */
	private static class DefaultGoogleStyleProgressLayoutFactory {
		/**
		 * @param clazzName This class name is being ignored
		 * @return Class token of {@code DefaultGoogleStyleProgressLayout}
		 */
		public static Class<? extends GoogleStyleProgressLayout> createGoogleStyleProgressLayoutClazz(
				String clazzName) {
			return DefaultGoogleStyleProgressLayout.class;
		}
		/**
		 * @param clazz Class token is being ignored.
		 * @param context
		 * @return {@code DefaultGoogleStyleProgressLayout} instance
		 */
		public static GoogleStyleProgressLayout createGoogleStyleProgressLayout(
				Class<? extends GoogleStyleProgressLayout> clazz, Context context, TypedArray attrs) {

			return new DefaultGoogleStyleProgressLayout(context, attrs);
		}

	}

}