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
import java.util.TreeMap;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import com.handmark.pulltorefresh.library.internal.Assert;
/**
 * {@code XmlPullNode} is used for parsing xml as a node tree.<br />
 * If you implement {@link XmlPullNode#XmlPullNodeCallback} and pass that on to this class,<br /> {@link XmlPullNodeParser} calls {@link XmlPullNode#XmlPullNodeCallback#process(XmlPullParser)} when it has found this current node during parse.<br />
 * When it has found a child node of current node' children, and if the child node has been added in current node as child XmlPullNode, {@link XmlPullNodeParser} continues parsing at the child.  
 * @author Wonjun Kim
 *
 */
class XmlPullNode {
	/**
	 * Unlimited repeat value <br />
	 * If you add a child (by calling {@link #addChildNode(XmlPullNode, int)} with setting repeat limit and the limit is {@code INFINITE}, the limit becomes meaningless.<br /> 
	 */
	public static final int INFINITE = -1;
	/**
	 * Map which store children<br /> 
	 * child's node name can have upper case or lower. all is the same whether that is upper or lower. 
	 */
	private final Map<String, XmlPullNodeContainer> children = new TreeMap<String, XmlPullNodeContainer>(
			String.CASE_INSENSITIVE_ORDER);
	/**
	 * Current node's name
	 */
	private final String tagName;
	/**
	 * Callback <br /> {@code XmlPullNodeCallback.process(XmlPullParser)} method is called when this node has found in xml.
	 */
	private final XmlPullNodeCallback callback;
	/**
	 * Default callback. This doesn't any kind of action.
	 */
	private static final XmlPullNodeCallback nullCallback = new XmlPullNodeCallback() {
		@Override
		public void process(XmlPullParser parser) {
			// do nothing
		}
	};
	/**
	 * Default Constructor (only set a node name)
	 * @param tagName Node name. this must not be null
	 */
	public XmlPullNode(String tagName) {
		this(tagName, null);
	}
	/**
	 * Constructor with {@code tagName} and {@code callback}
	 * @param tagName Node name. this must not be null
	 * @param callback  
	 */
	public XmlPullNode(String tagName, XmlPullNodeCallback callback) {
		Assert.notNull(tagName, "Tag Name");
		this.tagName = tagName;
		this.callback = (callback == null) ? nullCallback : callback;
	}
	/**
	 * @return Current node name
	 */
	public String getName() {
		return tagName;
	}
	/**
	 * Add a child node into this node without repeat limit<br />
	 * NOTE: When it has found a child node of current node' children, and if the child node has been added in current node as child XmlPullNode, {@link XmlPullNodeParser} continues parsing at the child.  
	 * @param node Child node to add
	 * @return true if it is successful to add
	 */
	public boolean addChildNode(XmlPullNode node) {
		return addChildNode(node, INFINITE);
	}
	/**
	 * Add a child node into this node with repeat limit<br />
	 * NOTE: When it has found a child node of current node' children, and if the child node has been added in current node as child XmlPullNode, {@link XmlPullNodeParser} continues parsing at the child.  
	 * @param node Child node to add
	 * @param repeatLimit Repeat limit. if a child repeats over the limit, an error occurs during parse. 
	 * @return true if it is successful to add
	 */
	public boolean addChildNode(XmlPullNode node, int repeatLimit) {
		XmlPullNodeContainer pullNodeContainer = children.get(node.getName());
		if (pullNodeContainer != null) {
			return false;
		}

		children.put(node.getName(),
				new XmlPullNodeContainer(node, repeatLimit));
		return true;
	}	
	/**
	 * Get a child of this node's chidren
	 * @param tagName Child node name to find
	 * @return Child node if the child has been found or null
	 * @throws XmlPullParserException
	 */
	public XmlPullNode getChild(String tagName) throws XmlPullParserException {
		XmlPullNodeContainer pullNodeContainer = children.get(tagName);
		if (pullNodeContainer == null) {
			return null;
		}
		return pullNodeContainer.takeXmlPullNode();

	}
	/**
	 * @return Callback instance
	 */
	public XmlPullNodeCallback getCallback() {
		return callback;
	}
	/**
	 * Callback to process some action for {@code XmlPullNode}. <br />
	 * 
	 * NOTE: If you implement {@link XmlPullNode#XmlPullNodeCallback} and pass that on to {@code XmlPullNode} class,<br /> {@link XmlPullNodeParser} calls {@link XmlPullNode#XmlPullNodeCallback#process(XmlPullParser)} when it has found this the node during parse.
	 * @author Wonjun Kim
	 *
	 */
	public static interface XmlPullNodeCallback {
		/**
		 * @param parser {@code XmlPullParser} instance which are parsing xml. WARNING: Must carefully use {@code parser}. using {@code parser} can affect to cause an parsing error. Because {@code parser} can easily go to some wrong position during usage.   
		 * @throws XmlPullParserException
		 * @throws IOException
		 */
		void process(XmlPullParser parser) throws XmlPullParserException, IOException;
	}
	
	/**
	 * {@XmlPullNodeContainer} is a container for saving {@code repeatLimit}. And check the limit to occur an error. <br />
	 * NOTE : Justly, this Class is not thread-safe. :)
	 * @author Wonjun Kim
	 *
	 */
	private static class XmlPullNodeContainer {
		/**
		 * Real {@code XmlPullnode} instance
		 */
		private XmlPullNode node;
		/**
		 * Repetition limit value decreasing one by which a node occurs at a time
		 */
		private int repeatLimit; 
		/**
		 * Default Constructor 
		 * @param node Infinite repeat {@code XmlPullNode} instance
		 */
		public XmlPullNodeContainer(XmlPullNode node) {
			this(node, INFINITE);
		}
		/**
		 * Constructor with {@code node} and {@code repeatLimit}
		 * @param node Finite repeat (only if you set positive {@code repeatLimit}) {@code XmlPullNode} instance
		 * @param repeatLimit Repeat limit value. This must be positive.
		 */
		public XmlPullNodeContainer(XmlPullNode node, int repeatLimit) {
			Assert.notNull(node, "XmlPullNode");
			this.node = node;
			this.repeatLimit = repeatLimit;
		}
		/**
		 * Return {@code XmlPullNode} instance or throw a limit error
		 * @return {@code XmlPullNode} instance if the node repeats under the limit 
		 * @throws XmlPullParserException if a number of repetitions reaches the limit 
		 */
		public XmlPullNode takeXmlPullNode() throws XmlPullParserException {
			if (repeatLimit > 0) {
				decreaseRepeatLimit();
				return node;
			}
			
			// throw an error
			if (repeatLimit == 0) {
				String tagName = node.getName();
				throw new XmlPullParserException("Tag '" + tagName
						+ "' should not have more " + repeatLimit + " nodes.");
			}

			// if infinite repeats,
			return node;

		}
		/**
		 * one step forward to the limit error!
		 */
		private void decreaseRepeatLimit() {
			--repeatLimit;
		}
	}
}
