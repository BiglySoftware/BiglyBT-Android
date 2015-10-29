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
import android.util.Log;
import android.view.View;

import com.handmark.pulltorefresh.configuration.xml.PullToRefreshXmlConfiguration;
import com.handmark.pulltorefresh.library.PullToRefreshBase.Mode;
import com.handmark.pulltorefresh.library.internal.IndicatorLayout;
import com.handmark.pulltorefresh.library.internal.DefaultIndicatorLayout;
import com.handmark.pulltorefresh.library.internal.Utils;
/**
 * Factory which creates indicator layouts 
 * <br />Indicator layouts must be listed in pulltorefresh.xml as "PullToRefresh/IndicatorLayouts/layout" nodes
 * @author Wonjun Kim
 */
public class IndicatorLayoutFactory {

	private static final String LOG_TAG = IndicatorLayoutFactory.class.getName();
	/**
	 * Create the class token matched by <b>{@code layoutCode}</b>
	 * @param layoutCode Indicator layout code, which must be defined in pulltorefresh.xml 
	 * @return Class token which is matched by {@code layoutCode}, or the class token of {@code DefaultIndicatorLayout} instance if not
	 */
	public static Class<? extends IndicatorLayout> createIndicatorLayoutClazzByLayoutCode(String layoutCode) {
		String clazzName = PullToRefreshXmlConfiguration.getInstance().getIndicatorLayoutClazzName(layoutCode);
		return createIndicatorLayoutClazz(clazzName);
	}
	/**
	 * Create the class token matched by <b>class name</b>
	 * @param clazzName Class name such as "com.handmark.pulltorefresh.library.internal.DefaultIndicatorLayout"
	 * @return Class token if the class matched by class name exists, or the class token of {@code DefaultIndicatorLayout} instance if not  
	 */
	@SuppressWarnings("unchecked")
	public static Class<? extends IndicatorLayout> createIndicatorLayoutClazz(String clazzName) {
		Class<? extends IndicatorLayout> clazz = null;
		if (clazzName == null) {
			clazz = DefaultIndicatorLayoutFactory.createIndicatorLayoutClazz(clazzName);
			return clazz;
		}
		
 		try {
			clazz = (Class<? extends IndicatorLayout> )Class.forName(clazzName);
			
		} catch (ClassNotFoundException e) {
			Log.e(LOG_TAG, "The indicator layout you have chosen class has not been found.", e);
			clazz = DefaultIndicatorLayoutFactory.createIndicatorLayoutClazz(clazzName);
			
		}
		
		return clazz;
	}
	/**
	 * Create a {@code IndicatorLayout} instance matched by <b>{@code layoutCode}</b> 
	 * @param layoutCode Indicator layout code, which must be defined in pulltorefresh.xml
	 * @param context 
	 * @param mode 
	 * @return {@code IndicatorLayout} instance if the class matched by {@code layoutCode} exists, or {@code DefaultIndicatorLayout} instance if not  
	 */
	public static IndicatorLayout createIndicatorLayout(String layoutCode, Context context, PullToRefreshBase.Mode mode) {
		Class<? extends IndicatorLayout> clazz = createIndicatorLayoutClazz(layoutCode);
		return createIndicatorLayout(clazz, context, mode);
	}
	/**
	 * Create a {@code IndicatorLayout} instance matched by <b>{@code clazz} token</b> 
	 * @param clazz Indicator layout class token, which must be defined in pulltorefresh.xml
	 * @param context 
	 * @param mode 
	 * @return {@code IndicatorLayout} instance if the class matched by {@code layoutCode} exists, or {@code DefaultIndicatorLayout} instance if not  
	 */
	public static IndicatorLayout createIndicatorLayout(
			Class<? extends IndicatorLayout> clazz, Context context, Mode mode) {
		IndicatorLayout layout = null;
		// Prevent NullPointerException 
		if ( clazz == null ) {
			Log.i(LOG_TAG, "The Class token of the Indicator Layout is missing. Default Indicator Layout will be used.");
			clazz = DefaultIndicatorLayoutFactory.createIndicatorLayoutClazz("");
		}
		
		layout = tryNewInstance(clazz, context, mode);
		
		// If trying to create new instance has failed,
		if (layout == null) {
			layout = DefaultIndicatorLayoutFactory.createIndicatorLayout(clazz, context, mode);
		}

		layout.setVisibility(View.INVISIBLE);
		return layout;
	}
	
	private static IndicatorLayout tryNewInstance(
			Class<? extends IndicatorLayout> clazz, Context context, Mode mode) {
		IndicatorLayout layout = null;
		try {
			Constructor<? extends IndicatorLayout> constructor = clazz
					.getConstructor(Context.class, Mode.class);
			layout = (IndicatorLayout) constructor.newInstance(context, mode);

		} catch (IllegalArgumentException e) {
			Log.e(LOG_TAG, "The indicator layout has failed to be created. ", e);
		} catch (InvocationTargetException e) {
			Log.e(LOG_TAG, "The indicator layout has failed to be created. ", e);
		} catch (SecurityException e) {
			Log.e(LOG_TAG, "The indicator layout has failed to be created. ", e);
		} catch (NoSuchMethodException e) {
			Log.e(LOG_TAG, "The indicator layout has failed to be created. ", e);
		} catch (InstantiationException e) {
			Log.e(LOG_TAG, "The indicator layout has failed to be created. ", e);
		} catch (IllegalAccessException e) {
			Log.e(LOG_TAG, "The indicator layout has failed to be created. ", e);
		} catch (NullPointerException e) {
			Log.e(LOG_TAG, "The indicator layout has failed to be created. ", e);
		}
		
		return layout;
	}	
	/**
	 * Factory which creates a default indicator layout instance. This is used when {@code IndicatorLayoutFactory} fails to create a instance
	 * @author Wonjun Kim
	 *
	 */
	private static class DefaultIndicatorLayoutFactory {
		/**
		 * @param clazzName This class name is being ignored
		 * @return Class token of {@code DefaultIndicatorLayout}
		 */
		public static Class<? extends IndicatorLayout> createIndicatorLayoutClazz(String clazzName) {
			return DefaultIndicatorLayout.class;
		}
		/**
		 * @param clazz Class token is being ignored.
		 * @param context
		 * @param mode
		 * @return {@code DefaultIndicatorLayout} instance
		 */
		public static IndicatorLayout createIndicatorLayout(Class<? extends IndicatorLayout> clazz, Context context, PullToRefreshBase.Mode mode) {
			return new DefaultIndicatorLayout(context, mode);
		}
	}
}
