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
import java.util.HashMap;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import com.handmark.pulltorefresh.library.internal.Assert;
/**
 * {@code PullToRefreshConfigXmlParser} does parsing pulltorefresh.xml as {@code XmlPullNode}s
 * @author Wonjun Kim
 */
final class PullToRefreshConfigXmlParser extends XmlPullNodeParser<PullToRefreshNode> {
	/**
	 * Repetition limit is only one
	 */ 
	private static final int ONLY_ONE_REPEAT = 1;
	/**
	 * Parsed data
	 */
	private PullToRefreshResult result;
	/**
	 * @param parser Must be not null 
	 */
	public PullToRefreshConfigXmlParser(XmlPullParserWrapper parser) {
		super(parser);
	}
	/**
	 * Generate a node tree matched by pulltorefresh.xml
	 * @return root node of a tree
	 */
	private XmlPullNode init() {
		// prepare a result instance
		result = new PullToRefreshResult();
		// make nodes 
		XmlPullNode root = new XmlPullNode("PullToRefresh");
		XmlPullNode loadingLayouts = new XmlPullNode("LoadingLayouts");
		XmlPullNode indicatorLayouts = new XmlPullNode("IndicatorLayouts");
		XmlPullNode googleStyleViewLayouts = new XmlPullNode("GoogleStyleViewLayouts");
		XmlPullNode googleStyleProgressLayouts = new XmlPullNode("GoogleStyleProgressLayouts");
		XmlPullNode loadingLayout = new XmlPullNode("layout",new LayoutNodeCallback(result.loadingLayoutClazzNameMap));
		XmlPullNode indicatorLayout = new XmlPullNode("layout",new LayoutNodeCallback(result.indicatorLayoutClazzNameMap));
		XmlPullNode googleStyleViewLayout = new XmlPullNode("layout",new LayoutNodeCallback(result.googleStyleViewLayoutClazzNameMap));
		XmlPullNode googleStyleProgressLayout = new XmlPullNode("layout",new LayoutNodeCallback(result.googleStyleProgressLayoutClazzNameMap));
		// make a tree structure
		root.addChildNode(loadingLayouts, ONLY_ONE_REPEAT);
		root.addChildNode(indicatorLayouts, ONLY_ONE_REPEAT);
		root.addChildNode(googleStyleViewLayouts, ONLY_ONE_REPEAT);
		root.addChildNode(googleStyleProgressLayouts, ONLY_ONE_REPEAT);
		loadingLayouts.addChildNode(loadingLayout);
		indicatorLayouts.addChildNode(indicatorLayout);
		googleStyleViewLayouts.addChildNode(googleStyleViewLayout);
		googleStyleProgressLayouts.addChildNode(googleStyleProgressLayout);
		// return root node
		return root;
	}
	/**
	 * @return root node of a tree
	 */
	@Override
	protected XmlPullNode generateRootNode() {
		return init();
	}
	/**
	 *  @return Parsed result data as {@code PullToRefreshNode} instance  
	 */
	@Override
	protected PullToRefreshNode getResult() {
		return new PullToRefreshNode(result.loadingLayoutClazzNameMap, result.indicatorLayoutClazzNameMap, result.googleStyleViewLayoutClazzNameMap, result.googleStyleProgressLayoutClazzNameMap);
	}
	/**
	 * Parsed Result to be sent to {@code PullToRefreshNode}
	 * @author Wonjun Kim
	 *
	 */
	private static class PullToRefreshResult {
		public final Map<String, String> loadingLayoutClazzNameMap = new HashMap<String, String>();
		public final Map<String, String> indicatorLayoutClazzNameMap = new HashMap<String, String>();
		public final Map<String, String> googleStyleViewLayoutClazzNameMap = new HashMap<String, String>();
		public final Map<String, String> googleStyleProgressLayoutClazzNameMap = new HashMap<String, String>();
	}
		
	/**
	 * Callback of the node 'PullToRefresh/IndicatorLayouts/layout' and 'PullToRefresh/LoadingLayouts/layout' 
	 * @author Wonjun Kim
	 *
	 */
	private static class LayoutNodeCallback implements XmlPullNode.XmlPullNodeCallback {
		/**
		 * {@code Map} storing Layouts' information 
		 */
		private Map<String, String> layoutClazzNameMap;
		/**
		 * @param layoutClazzNameMap Must not be null and be clean for which put new values
		 */
		public LayoutNodeCallback(Map<String, String> layoutClazzNameMap) {
			Assert.notNull(layoutClazzNameMap, "Class Map");
			this.layoutClazzNameMap = layoutClazzNameMap;
		}
		/**
		 * Be called when parser has found 'layout' node at a time
		 */
		@Override
		public void process(XmlPullParser parser) throws XmlPullParserException, IOException {

			int attributesCount = parser.getAttributeCount();
			String attributeName, attributeValue;
			// Iterate attributes!			
			for (int i = 0; i < attributesCount; ++i) {
				attributeName = parser.getAttributeName(i);
				attributeValue = parser.getAttributeValue(i);
				// The 'name' attribute is as a layout code of each layout's class  
				if ( "name".equals(attributeName)) {
					// Skip if attribute value is null or empty 
					if ( attributeValue == null || attributeValue.length() == 0 ) {
						continue;
					}
					
					// Get layout's class name
					String clazzName = parser.nextText();
					
					// Insert new class name 
					layoutClazzNameMap.put(attributeValue, clazzName);

					// Do 'break' because nextText() method has been called
					break;
				}
			}
		}
	}
}
