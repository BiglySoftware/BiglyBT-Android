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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
/**
 * Parser of assets/pulltorefresh.xml creation factory 
 * @author Wonjun Kim
 */
class ExtendedConfigXmlParserFactory {
	
	private static final String LOG_TAG = ExtendedConfigXmlParserFactory.class.getName();
	/**
	 * pulltorefresh.xml path in assets folder
	 */
	private static final String XML_PATH_IN_ASSETS = "pulltorefresh.xml";
	/**
	 * pulltorefresh.xml encoding type
	 */
	private static final String DEFAULT_ENCODING_TYPE = "utf-8";
	/**
	 * Create a parser of assets/pulltorefresh.xml 
	 * @param context
	 * @return {@code parser} if creating parser has done, or {@code null} if an error occured during creation
	 */
	public static XmlPullParser createParser(Context context) {
		AssetManager am = context.getAssets();
		XmlPullParser parser = null;
		try {
			InputStream is = am.open(XML_PATH_IN_ASSETS);
			parser = XmlPullParserFactory.newInstance().newPullParser();
			parser.setInput(is, DEFAULT_ENCODING_TYPE);
			
		} catch (FileNotFoundException e) {
			Log.d(LOG_TAG, "The configuration file 'assets/" + XML_PATH_IN_ASSETS + "' is missing. But the file is just an option. It is necessary only if you want to customize something in Pull To Refresh.");
			// explicitly assign null
			parser = null;
		} catch (XmlPullParserException e) {
			Log.w(LOG_TAG, "The error occurs below when generating parser.", e);
			// explicitly assign null
			parser = null;
		} catch (IOException e) {
			Log.w(LOG_TAG, "Loading "+XML_PATH_IN_ASSETS+" file has failed.", e);
			// explicitly assign null
			parser = null;
		}
		
		// NOTE: this code makes a fatal error. so omit the code.
		// am.close();
		
		return parser;
	}
}
