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

import android.util.Log;

import com.handmark.pulltorefresh.configuration.xml.XmlPullParserWrapper.DocumentState;
import com.handmark.pulltorefresh.library.internal.Assert;
/**
 * 
 * {@code XmlPullNode}-based Parser using XmlPullParser <br />
 * When you override this class, you must define parse strategies, and return the root node (in {@link #generateRootNode()} of {@code XmlPullNode}s containing each parse strategy, 
 * and return the result (in {@link #getResult()} for which provide that to client after parse.  
 * @author Wonjun Kim
 * @param <R> Result type 
 */
abstract class XmlPullNodeParser<R> {
	private final XmlPullParserWrapper parser;
	private final XmlPullNode rootNode;
	/**
	 * this flag prevents duplicate parse.
	 */
	private boolean isParsed = false;
	/**
	 * @param parser which has not been read yet. <br /> throw {@code NullPointerException} if {@code parser} is null
	 */
	public XmlPullNodeParser(XmlPullParserWrapper parser) {
		Assert.notNull(parser, "XmlPullParser");
		this.parser = parser;
		this.rootNode = generateRootNode();
	}
	/**
	 * @return entry point for {@code XmlPullNode}-based parse
	 */
	protected abstract XmlPullNode generateRootNode();
	/**
	 * Parse the data and return {@code <R>} type result <br />
	 * even if you call this methods several times, parsing happens once and no more duplicate parse
	 * @return parsed data
	 * @throws XmlPullParserException
	 * @throws IOException
	 */
	public final R parse() throws XmlPullParserException, IOException {

		// avoid duplicate parse 
		if ( isParsed == true ) {
			return getResult();
		}
		
		// First, Find the root node.
		DocumentState documentState;
		
		String rootName = rootNode.getName();
		documentState = parser.nextStartTagByName(rootName);
		
		if ( documentState.END.equals(documentState)) {
			throw new XmlPullParserException(rootName + " tag has not found.");
		}
		// deeply parsing
		parseInternal(rootNode);
		isParsed = true;
		return getResult();
	}
	/**
	 * Return a result after parse <br />Be called from {@link #parse()} method 
	 * @return result value to return after parsing is done
	 */
	protected abstract R getResult();
	/**
	 * Call {@link XmlPullNode.XmlPullNodeCallback#process(XmlPullParser)} and parse child nodes of current node
	 * 
	 * @param currentNode target of which call a callback and parse children
	 * @throws XmlPullParserException
	 * @throws IOException
	 */
	private void parseInternal(XmlPullNode currentNode) throws XmlPullParserException, IOException {
		// NOTE : too many permissions to node
		currentNode.getCallback().process(parser);
		
		while ( true ) {
	
			// if document is end during finding the end tag of this current node, it throws the exception below. 
			if ( parser.isEndDocument() ) {
				throw new XmlPullParserException("XML document is invalid.");
			}			
			
			// if the end tag is found, parsing this scope is being ended.
			if ( parser.isEndTag() && parser.matchCurrentTagName(currentNode.getName())) {
				break;
			}
			
			// get next tag
			parser.next();
			
			// when a child node is found, deeply parsing
			if ( parser.isStartTag() ) {
				
				String currentTagName = parser.getName();
				XmlPullNode childNode = currentNode.getChild(currentTagName);
				if ( childNode != null ) {
					// recursively!
					parseInternal(childNode);
				}
			}
		}
	} 
}
