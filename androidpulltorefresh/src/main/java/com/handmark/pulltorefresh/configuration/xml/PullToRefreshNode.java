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

import java.util.Map;

import com.handmark.pulltorefresh.library.internal.Assert;
/**
 * {@code PullToRefreshNode} has an information in contents of pulltorefresh.xml, <br /> such as loading layout and indicator layout class name
 * @author Wonjun Kim
 *
 */
class PullToRefreshNode {
	/**
	 * Map Storing LoadingLayout class names<br />
	 * Key : layout code (String) <br />
	 * Value : class name (String)
	 */
	private final Map<String, String> loadingLayoutClazzNameMap;
	/**
	 * Map Storing IndicatorLayout class names<br />
	 * Key : layout code (String) <br />
	 * Value : Class name (String)
	 */
	private final Map<String, String> indicatorLayoutClazzNameMap;
	/**
	 * Map Storing GoogleStyleViewLayout class names<br />
	 * Key : layout code (String) <br />
	 * Value : Class name (String)
	 */	
	private Map<String, String> googleStyleViewLayoutClazzNameMap;
	/**
	 * Map Storing GoogleStyleProgressLayout class names<br />
	 * Key : layout code (String) <br />
	 * Value : Class name (String)
	 */	
	private Map<String, String> googleStyleProgressLayoutClazzNameMap;	
	/**
	 * Constructor needs two class name {@code Map}s, which are LoadingLayout class name map and Indicator class name {@code map}.<br />
	 * NOTE: Parameters must go in order. First Parameter : loading layout / Second Parameter : indicator layout  
	 * @param loadingLayoutClazzNameMap LoadingLayout class names
	 * @param indicatorLayoutClazzNameMap
	 */
	public PullToRefreshNode(Map<String, String> loadingLayoutClazzNameMap,
			Map<String, String> indicatorLayoutClazzNameMap, Map<String, String> googleStyleViewLayoutClazzNameMap, Map<String, String> googleStyleProgressLayoutClazzNameMap) {
		Assert.notNull(loadingLayoutClazzNameMap, "LoadingLayout Class Name Map");
		Assert.notNull(indicatorLayoutClazzNameMap, "Loading Layout Class Name Map");
		this.loadingLayoutClazzNameMap = loadingLayoutClazzNameMap;
		this.indicatorLayoutClazzNameMap = indicatorLayoutClazzNameMap;
		this.googleStyleViewLayoutClazzNameMap = googleStyleViewLayoutClazzNameMap;
		this.googleStyleProgressLayoutClazzNameMap = googleStyleProgressLayoutClazzNameMap;
	}
	/**
	 * @param layoutCode LoadingLayout layout code
	 * @return LoadingLayout class name
	 */
	public String getIndicatorLayoutClazzName(String layoutCode) {
		return indicatorLayoutClazzNameMap.get(layoutCode);
	}
	/**
	 * @param layoutCode LoadingLayout layout code
	 * @return LoadingLayout class name
	 */
	public String getLoadingLayoutClazzName(String layoutCode) {
		return loadingLayoutClazzNameMap.get(layoutCode);
	}
	/**
	 * @param layoutCode GoogleStyleViewLayout layout code
	 * @return GoogleStyleViewLayout class name
	 */	
	public String getGoogleStyleViewLayoutClazzName(String layoutCode) {
		return googleStyleViewLayoutClazzNameMap.get(layoutCode);
	}
	/**
	 * @param layoutCode GoogleStyleProgressLayout layout code
	 * @return GoogleStyleProgressLayout class name
	 */	
	public String getGoogleStyleProgressLayoutClazzName(String layoutCode) {
		return googleStyleProgressLayoutClazzNameMap.get(layoutCode);
	}		
	/**
	 * Add an information from other {@code PullToRefreshNode} instance
	 * @param extendedNode Other {@code PullToRefresNode} to be combined
	 */
	public void extendProperties(PullToRefreshNode extendedNode) {
		Assert.notNull(extendedNode, "Extended Node");
		Map<String, String> indicatorMap = extendedNode.indicatorLayoutClazzNameMap;
		Map<String, String> loadingMap = extendedNode.loadingLayoutClazzNameMap;
		Map<String, String> googleStyleViewMap = extendedNode.googleStyleViewLayoutClazzNameMap;
		Map<String, String> googleStyleProgressMap = extendedNode.googleStyleProgressLayoutClazzNameMap;
		
		indicatorLayoutClazzNameMap.putAll(indicatorMap);
		loadingLayoutClazzNameMap.putAll(loadingMap);
		googleStyleViewLayoutClazzNameMap.putAll(googleStyleViewMap);
		googleStyleProgressLayoutClazzNameMap.putAll(googleStyleProgressMap);
	}

}
