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
import com.handmark.pulltorefresh.library.internal.RotateLoadingLayout;
import com.handmark.pulltorefresh.library.internal.LoadingLayout;
/**
 * Factory which creates loading layouts 
 * <br />Loading layouts must be listed in pulltorefresh.xml as "PullToRefresh/LoadingLayouts/layout" nodes
 * @author Wonjun Kim
 */
class LoadingLayoutFactory {

	private static final String LOG_TAG = LoadingLayoutFactory.class
			.getSimpleName();
	/**
	 * Create the class token matched by <b>{@code layoutCode}</b>
	 * @param layoutCode Loading layout code, which must be defined in pulltorefresh.xml 
	 * @return Class token which is matched by {@code layoutCode}, or the class token of {@code RotateLoadingLayout} instance if not
	 */
	public static Class<? extends LoadingLayout> createLoadingLayoutClazzByLayoutCode(String layoutCode) {
		String clazzName = PullToRefreshXmlConfiguration.getInstance().getLoadingLayoutClazzName(layoutCode);
		return createLoadingLayoutClazz(clazzName);
	}
	/**
	 * Create a {@code LoadingLayout} instance matched by <b>{@code clazz} token</b> 
	 * @param layoutCode Loading layout code, which must be defined in pulltorefresh.xml
	 * @param context 
	 * @param mode 
	 * @return {@code LoadingLayout} instance if the class matched by {@code layoutCode} exists, or {@code RotateLoadingLayout} instance if not  
	 */
	@SuppressWarnings("unchecked")
	public static Class<? extends LoadingLayout> createLoadingLayoutClazz(
			String clazzName) {
		Class<? extends LoadingLayout> loadingLayoutClazz = null;
		if ( clazzName == null ) {
			loadingLayoutClazz = DefaultLoadingLayoutFactory.createLoadingLayoutClazz(clazzName);
			return loadingLayoutClazz;
		}
		
		try {
			loadingLayoutClazz = (Class<LoadingLayout>) Class.forName(clazzName);

		} catch (ClassNotFoundException e) {
			Log.e(LOG_TAG,"The loading layout you have chosen class has not been found.", e);
			loadingLayoutClazz = DefaultLoadingLayoutFactory.createLoadingLayoutClazz(clazzName);
		} 

		return loadingLayoutClazz;
	}
	/**
	 * Create a {@code LoadingLayout} instance matched by <b>{@code layoutCode}</b> 
	 * @param layoutCode Loading layout code, which must be defined in pulltorefresh.xml
	 * @param context 
	 * @param mode 
	 * @return {@code LoadingLayout} instance if the class matched by {@code layoutCode} exists, or {@code RotateLoadingLayout} instance if not  
	 */
	public static LoadingLayout createLoadingLayout(String layoutCode, Context context, Mode mode,
			Orientation orientation, TypedArray attrs) {
		Class<? extends LoadingLayout> clazz = createLoadingLayoutClazz(layoutCode);
		return createLoadingLayout(clazz, context, mode, orientation, attrs);
	}
	/**
	 * Create a {@code LoadingLayout} instance matched by <b>{@code clazz} token</b> 
	 * @param layoutCode Loading layout code, which must be defined in pulltorefresh.xml
	 * @param context 
	 * @param mode 
	 * @return {@code LoadingLayout} instance if the class matched by {@code layoutCode} exists, or {@code RotateLoadingLayout} instance if not  
	 */
	public static LoadingLayout createLoadingLayout(
			Class<? extends LoadingLayout> clazz, Context context, Mode mode,
			Orientation orientation, TypedArray attrs) {
		LoadingLayout layout = null;
		// Prevent NullPointerException
		if ( clazz == null ) {
			Log.i(LOG_TAG, "The Class token of the Loading Layout is missing. Default Loading Layout will be used.");
			clazz = DefaultLoadingLayoutFactory.createLoadingLayoutClazz("");
		}
		
		layout = tryNewInstance(clazz, context, mode, orientation, attrs);

		// If trying to create new instance has failed,
		if (layout == null) {
			layout = DefaultLoadingLayoutFactory.createLoadingLayout(clazz, context, mode, orientation, attrs);
		}

		layout.setVisibility(View.INVISIBLE);
		return layout;
	}
	private static LoadingLayout tryNewInstance(
			Class<? extends LoadingLayout> clazz, Context context, Mode mode,
			Orientation orientation, TypedArray attrs) {
		LoadingLayout layout = null;
		try {
			Constructor<? extends LoadingLayout> constructor = clazz
					.getConstructor(Context.class, Mode.class,
							Orientation.class, TypedArray.class);
			layout = (LoadingLayout) constructor.newInstance(context, mode,
					orientation, attrs);

		} catch (IllegalArgumentException e) {
			Log.e(LOG_TAG, "The loading layout has failed to be created. ", e);
		} catch (InvocationTargetException e) {
			Log.e(LOG_TAG, "The loading layout has failed to be created. ", e);
		} catch (SecurityException e) {
			Log.e(LOG_TAG, "The loading layout has failed to be created. ", e);
		} catch (NoSuchMethodException e) {
			Log.e(LOG_TAG, "The loading layout has failed to be created. ", e);
		} catch (InstantiationException e) {
			Log.e(LOG_TAG, "The loading layout has failed to be created. ", e);
		} catch (IllegalAccessException e) {
			Log.e(LOG_TAG, "The loading layout has failed to be created. ", e);
		} catch (NullPointerException e) {
			Log.e(LOG_TAG, "The loading layout has failed to be created. ", e);
		}
		return layout;
	}
	/**
	 * Factory which creates a default loading layout instance. This is used when {@code LoadingLayoutFactory} fails to create a instance
	 * @author Wonjun Kim
	 *
	 */
	private static class DefaultLoadingLayoutFactory {
		/**
		 * @param clazzName This class name is being ignored
		 * @return Class token of {@code RotateLoadingLayout}
		 */
		public static Class<? extends LoadingLayout> createLoadingLayoutClazz(
				String clazzName) {
			return RotateLoadingLayout.class;
		}
		/**
		 * @param clazz Class token is being ignored.
		 * @param context
		 * @param mode
		 * @return {@code RotateLoadingLayout} instance
		 */
		public static LoadingLayout createLoadingLayout(
				Class<? extends LoadingLayout> clazz, Context context,
				Mode mode, Orientation orientation, TypedArray attrs) {

			return new RotateLoadingLayout(context, mode, orientation, attrs);
		}

	}

}
