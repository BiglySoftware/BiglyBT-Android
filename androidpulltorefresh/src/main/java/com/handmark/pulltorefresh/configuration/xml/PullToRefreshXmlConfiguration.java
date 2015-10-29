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
package com.handmark.pulltorefresh.configuration.xml;

import java.io.IOException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import com.handmark.pulltorefresh.library.R;
import com.handmark.pulltorefresh.library.internal.Assert;
import com.handmark.pulltorefresh.library.internal.Utils;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

/**
 * {@code PullToRefreshXmlConfiguration} is an information set of PullToRefresh. It contains a list of indicator layouts and of loading layouts. An information set of PullToRefresh is converted from pulltorefresh.xml.<br />
 * This Class is Singleton, and you <b>MUST</b> call {@link #init(Context)} before using this class.  
 * @author Wonjun Kim
 */
public class PullToRefreshXmlConfiguration {
	
	private static final String LOG_TAG = PullToRefreshXmlConfiguration.class.getName();
	
	/**
	 * Singleton instance
	 * @author Wonjun Kim
	 */
	private static class InstanceHolder {
		private final static PullToRefreshXmlConfiguration instance = new PullToRefreshXmlConfiguration();
		
		private static PullToRefreshXmlConfiguration getInstance() {
			return instance;
		}
	} 
	/**
	 * Parsed information from pulltorefresh.xml 
	 */
	private PullToRefreshNode node = null; 
	/**
	 * Default pulltorefresh path id is got from R class.
	 */
	private static final int XML_PATH_ID = R.xml.pulltorefresh;
	/**
	 * flag whether it has called {@link #init(Context)} 
	 */
	private boolean initialized = false;
	/**
	 * Constructor <br />
	 * nothing to do
	 */
	private PullToRefreshXmlConfiguration() {}
	/**
	 * Get singleton instance
	 * @return {@code PullToRefreshXmlConfiguration} instance
	 */
	public static PullToRefreshXmlConfiguration getInstance() {
		return InstanceHolder.getInstance();
	}
	/**
	 * Initialize the instance before using. <br />
	 * Load 'res/xml/pulltorefresh.xml' in PullToRefresh library package and 'assets/pulltofresh.xml' in Android Project if it exists. <br />
	 * Combine information of 'res/xml/pulltorefresh.xml' and 'assets/pulltofresh.xml', and then save the information into the instance's fields.<br />
	 * <br />
	 * NOTE: This method <b>MUST</b> be called before using! 
	 * @param context Context instance and not null
	 */
	public void init(Context context) {
		// If an initialization was happened already, skip.
		if ( initialized == true ) {
			return;
		}
		Assert.notNull(context, "Context");
		// get resources
		Resources resources = context.getResources();
		// read the file
		XmlPullParser parser = resources.getXml(XML_PATH_ID);
		// parser the xml
		XmlPullParserWrapper wrapper = new XmlPullParserWrapper(parser);
		
		try {
			node = new PullToRefreshConfigXmlParser(wrapper).parse();

			// load extended xml 
			XmlPullParser extendedXmlParser = ExtendedConfigXmlParserFactory.createParser(context);
			if ( extendedXmlParser != null) {
				XmlPullParserWrapper extendedXmlWrapper = new XmlPullParserWrapper(extendedXmlParser);
				// NOTE : if some exception is thrown from PullToRefreshConfigXmlParser, Loading extended xml will be skipped.
				PullToRefreshNode extendedNode = new PullToRefreshConfigXmlParser(extendedXmlWrapper).parse();
				node.extendProperties(extendedNode);
			}
		} catch (XmlPullParserException e) {
			Log.d(LOG_TAG, "It has failed to parse the xmlpullparser xml.", e);
		} catch (IOException e) {
			Log.d(LOG_TAG, "It has failed to parse the xmlpullparser xml.\n ", e);
		}
		
		// Intialization can be done whether reading XML has failed or not! 
		initialized = true;
	}
	/**
	 * @param layoutCode Layout name
	 * @return Layout Class name ( ex: com.handmark.pulltorefresh.library.internal.FlipLoadingLayout )
	 */
	public String getLoadingLayoutClazzName(String layoutCode) {
		assertInitialized();
		if ( isNodeNull() ) {
			return null;
		}
		return node.getLoadingLayoutClazzName(layoutCode);
	}
	/**
	 * @param layoutCode Layout name
	 * @return Layout Class name ( ex: com.handmark.pulltorefresh.library.internal.DefaultLoadingLayout )
	 */
	public String getIndicatorLayoutClazzName(String layoutCode) {
		assertInitialized();
		if ( isNodeNull() ) {
			return null;
		}
		return node.getIndicatorLayoutClazzName(layoutCode);
	}
	/**
	 * @param layoutCode Layout name
	 * @return Layout Class name ( ex: com.handmark.pulltorefresh.library.internal.DefaultGoogleStyleViewLayout )
	 */
	public String getGoogleStyleViewLayoutClazzName(String layoutCode) {
		assertInitialized();
		if ( isNodeNull() ) {
			return null;
		}
		return node.getGoogleStyleViewLayoutClazzName(layoutCode);
	}
	/**
	 * @param layoutCode Layout name
	 * @return Layout Class name ( ex: com.handmark.pulltorefresh.library.internal.DefaultGoogleStyleProgressLayout )
	 */
	public String getGoogleStyleProgressLayoutClazzName(String layoutCode) {
		assertInitialized();
		if ( isNodeNull() ) {
			return null;
		}
		return node.getGoogleStyleProgressLayoutClazzName(layoutCode);
	}	
	/**
	 * @return true if {@code node} is null
	 */
	private boolean isNodeNull() {
		return node == null;
	}
	/**
	 * @return true if {@link #init(Context)} method has not been called  
	 */
	private boolean notInitialized() {
		return !initialized;
	}
	/**
	 * @return throw an exception if {@link #init(Context)} method has not been called  
	 */
	private void assertInitialized() {
		if ( notInitialized() ) {
			throw new IllegalStateException(PullToRefreshXmlConfiguration.class.getName()+" has not initialized. Call init() method first.");
		}
	}
}
