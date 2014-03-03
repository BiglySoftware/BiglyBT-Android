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
import java.io.InputStream;
import java.io.Reader;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import com.handmark.pulltorefresh.library.internal.Assert;
/**
 * <p>
 * {@code XmlPullParser} Wrapper <br />
 * This class provide util methods such as {@link #nextStartTag()}, {@link #isStartTag()} , and so on  
 * </p>
 * @author Wonjun Kim
 *
 */
class XmlPullParserWrapper implements XmlPullParser {
	/**	
	 * {@code XmlPullParserWrapper} call this {@code parser} as delegatee
	 */
	private final XmlPullParser parser;
	/**
	 * <p>
	 * Need {@code XmlPullParser} <br /> 
	 * If {@code parser} is null, It throws {@code NullPointerException}
	 * </p> 
	 * @param parser delegated {@link XmlPullParser}
	 */
	public XmlPullParserWrapper(XmlPullParser parser) {
		Assert.notNull(parser, "XmlPullParser");
		this.parser = parser;
	}
	/**
	 * <p>
	 * {@code DocumentState} represents current state (instead of event type) of a document which the parser reads.<br /> 
	 * </p>
	 * @author Wonjun Kim
	 *
	 */
	public static enum DocumentState {
		/**
		 * READ : Document is readable yet
		 */
		READ, 
		/**
		 * FOUNDTAG : Document is readable yet and current event is a tag type
		 */
		FOUNDTAG, 
		/**
		 * END : Document is completely read. There is no more token
		 */
		END;
	}
	/**
	 * @return true if current type is {@code START_TAG}
	 * @throws XmlPullParserException
	 */
	public boolean isStartTag() throws XmlPullParserException {
		return getEventType() == START_TAG;
	}
	/**
	 * @return true if current type is {@code END_TAG}
	 * @throws XmlPullParserException
	 */
	public boolean isEndTag() throws XmlPullParserException {
		return getEventType() == END_TAG;
	}
	/**
	 * @return true if current type is {@code TEXT}
	 * @throws XmlPullParserException
	 */
	public boolean isText() throws XmlPullParserException {
		return getEventType() == TEXT;
	}
	/**
	 * @return true if current type is {@code START_DOCUMENT}
	 * @throws XmlPullParserException
	 */
	public boolean isStartDocument() throws XmlPullParserException {
		return getEventType() == START_DOCUMENT;
	}
	/**
	 * @return true if current type is {@code END_DOCUMENT}
	 * @throws XmlPullParserException
	 */
	public boolean isEndDocument() throws XmlPullParserException {
		return getEventType() == END_DOCUMENT;
	}
	/**
	 * @return true if current type is {@code COMMENT}
	 * @throws XmlPullParserException
	 */
	public boolean isComment() throws XmlPullParserException {
		return getEventType() == COMMENT;
	}
	/**
	 * <p>
	 * Find next start tag by calling {@link XmlPullParser#next()} again and again 
	 * If the parser reaches the end of a document, this method will returns {@code DocumentState.END}
	 * </p>
	 * @return {@link DocumentState.FOUNDTAG} if start tag has been found 
	 * 		   {@link DocumentState.END} if the parser reaches the end of a document
	 * @throws XmlPullParserException
	 * @throws IOException
	 */
	public DocumentState nextStartTag() throws XmlPullParserException,
			IOException {
		while (true) {

			int evt = next();

			if (evt == XmlPullParser.START_TAG) {
				return DocumentState.FOUNDTAG;
			}

			if (evt == XmlPullParser.END_DOCUMENT) {
				return DocumentState.END;
			}
		}
	}
	/**
	 * <p>
	 * Find next start tag whose name is same as {@code tagName} by calling {@link XmlPullParser#next()} again and again 
	 * If the parser reaches the end of a document, this method will returns {@code DocumentState.END}
	 * </p>
	 * @param tagName Tag name you want to find
	 * @return {@link DocumentState.FOUNDTAG} if start tag has been found 
	 * 		   {@link DocumentState.END} if the parser reaches the end of a document
	 * @throws XmlPullParserException
	 * @throws IOException
	 */
	public DocumentState nextStartTagByName(String tagName)
			throws XmlPullParserException, IOException {
		while (true) {
			DocumentState documentState = nextStartTag();

			if (documentState.equals(DocumentState.END)) {
				return documentState;
			}
			
			if (matchCurrentTagName(tagName)) {
				break;
			}
		}

		return DocumentState.FOUNDTAG;
	}
	/**
	 * <p>
	 * Find next end tag by calling {@link XmlPullParser#next()} again and again 
	 * If the parser reaches the end of a document, this method will returns {@code DocumentState.END}
	 * </p>
	 * @return {@link DocumentState.FOUNDTAG} if end tag has been found 
	 * 		   {@link DocumentState.END} if the parser reaches the end of a document
	 * @throws XmlPullParserException
	 * @throws IOException
	 */
	public DocumentState nextEndTag() throws XmlPullParserException,
			IOException {
		while (true) {

			int evt = next();

			if (evt == XmlPullParser.END_TAG) {
				return DocumentState.FOUNDTAG;
			}

			if (evt == XmlPullParser.END_DOCUMENT) {
				return DocumentState.END;
			}
		}
	}
	/**
	 * <p>
	 * Find next end tag whose name is same as {@code tagName} by calling {@link XmlPullParser#next()} again and again 
	 * If the parser reaches the end of a document, this method will returns {@code DocumentState.END}
	 * </p>
	 * @param tagName Tag name you want to find
	 * @return {@link DocumentState.FOUNDTAG} if start tag has been found 
	 * 		   {@link DocumentState.END} if the parser reaches the end of a document
	 * @throws XmlPullParserException
	 * @throws IOException
	 */
	public DocumentState nextEndTagByName(String tagName)
			throws XmlPullParserException, IOException {
		while (true) {
			DocumentState documentState = nextEndTag();

			if (documentState.equals(DocumentState.END)) {
				return documentState;
			}
			
			if (matchCurrentTagName(tagName)) {
				break;
			}
		}

		return DocumentState.FOUNDTAG;
	}
	/**
	 * Check that current tag name is same as {@code tagName} 
	 * 
	 * @param tagName Tag name you want to check whether current tag is same as 
	 * @return true if current tag name is same 
	 * @throws XmlPullParserException
	 * @throws IOException
	 */
	public boolean matchCurrentTagName(String tagName) {
		Assert.notNull(tagName, "Tag Name");
		return getName().equals(tagName);
	}
	/** Wrapper method for {@link XmlPullParser#setFeature(String, boolean)} */
	@Override
	public void setFeature(String name, boolean state)
			throws XmlPullParserException {
		parser.setFeature(name, state);
	}
	/** Wrapper method for {@link XmlPullParser#getFeature(String)} */
	@Override
	public boolean getFeature(String name) {
		return parser.getFeature(name);
	}
	/** Wrapper method for {@link XmlPullParser#setProperty(String, Object)} */
	@Override
	public void setProperty(String name, Object value)
			throws XmlPullParserException {
		parser.setProperty(name, value);
	}
	/** Wrapper method for {@link XmlPullParser#getProperty(String)} */
	@Override
	public Object getProperty(String name) {
		return parser.getProperty(name);
	}
	/** Wrapper method for {@link XmlPullParser#setInput(Reader)} */
	@Override
	public void setInput(Reader in) throws XmlPullParserException {
		parser.setInput(in);
	}
	/** Wrapper method for {@link XmlPullParser#setInput(InputStream, String)} */
	@Override
	public void setInput(InputStream inputStream, String inputEncoding)
			throws XmlPullParserException {
		parser.setInput(inputStream, inputEncoding);
	}
	/** Wrapper method for {@link XmlPullParser#getInputEncoding()} */
	@Override
	public String getInputEncoding() {
		return parser.getInputEncoding();
	}
	/** Wrapper method for {@link XmlPullParser#defineEntityReplacementText(String, String)} */
	@Override
	public void defineEntityReplacementText(String entityName,
			String replacementText) throws XmlPullParserException {
		parser.defineEntityReplacementText(entityName, replacementText);
	}
	/** Wrapper method for {@link XmlPullParser#getNamespaceCount(int)} */
	@Override
	public int getNamespaceCount(int depth) throws XmlPullParserException {
		// TODO Auto-generated method stub
		return parser.getNamespaceCount(depth);
	}
	/** Wrapper method for {@link XmlPullParser#getNamespacePrefix(int)} */
	@Override
	public String getNamespacePrefix(int pos) throws XmlPullParserException {
		return parser.getNamespacePrefix(pos);
	}
	/** Wrapper method for {@link XmlPullParser#getNamespaceUri(int)} */
	@Override
	public String getNamespaceUri(int pos) throws XmlPullParserException {
		return parser.getNamespaceUri(pos);
	}
	/** Wrapper method for {@link XmlPullParser#getNamespace(String)} */
	@Override
	public String getNamespace(String prefix) {
		return parser.getNamespace(prefix);
	}
	/** Wrapper method for {@link XmlPullParser#getDepth()} */
	@Override
	public int getDepth() {
		return parser.getDepth();
	}
	/** Wrapper method for {@link XmlPullParser#getPositionDescription()} */
	@Override
	public String getPositionDescription() {
		return parser.getPositionDescription();
	}
	/** Wrapper method for {@link XmlPullParser#getLineNumber()} */
	@Override
	public int getLineNumber() {
		return parser.getLineNumber();
	}
	/** Wrapper method for {@link XmlPullParser#getColumnNumber()} */
	@Override
	public int getColumnNumber() {
		return parser.getColumnNumber();
	}
	/** Wrapper method for {@link XmlPullParser#isWhiteSpace()} */
	@Override
	public boolean isWhitespace() throws XmlPullParserException {
		return parser.isWhitespace();
	}
	/** Wrapper method for {@link XmlPullParser#getText()} */
	@Override
	public String getText() {
		return parser.getText();
	}
	/** Wrapper method for {@link XmlPullParser#getTextCharacters(int[])} */
	@Override
	public char[] getTextCharacters(int[] holderForStartAndLength) {
		return parser.getTextCharacters(holderForStartAndLength);
	}
	/** Wrapper method for {@link XmlPullParser#getNamespace()} */
	@Override
	public String getNamespace() {
		return parser.getNamespace();
	}
	/** Wrapper method for {@link XmlPullParser#getName()} */
	@Override
	public String getName() {
		return parser.getName();
	}
	/** Wrapper method for {@link XmlPullParser#getPrefix()} */
	@Override
	public String getPrefix() {
		return parser.getPrefix();
	}
	/** Wrapper method for {@link XmlPullParser#isEmptyElementTag()} */
	@Override
	public boolean isEmptyElementTag() throws XmlPullParserException {
		return parser.isEmptyElementTag();
	}
	/** Wrapper method for {@link XmlPullParser#getAttributeCount()} */
	@Override
	public int getAttributeCount() {
		return parser.getAttributeCount();
	}
	/** Wrapper method for {@link XmlPullParser#getAttributeNamespace(int)} */
	@Override
	public String getAttributeNamespace(int index) {
		return parser.getAttributeNamespace(index);
	}
	/** Wrapper method for {@link XmlPullParser#getAttributeName(int)} */
	@Override
	public String getAttributeName(int index) {
		return parser.getAttributeName(index);
	}
	/** Wrapper method for {@link XmlPullParser#getAttributePrefix(int)} */
	@Override
	public String getAttributePrefix(int index) {
		return parser.getAttributePrefix(index);
	}
	/** Wrapper method for {@link XmlPullParser#getAttributeType(int)} */
	@Override
	public String getAttributeType(int index) {
		return parser.getAttributeType(index);
	}
	/** Wrapper method for {@link XmlPullParser#isAttributeDefault(int)} */
	@Override
	public boolean isAttributeDefault(int index) {
		return parser.isAttributeDefault(index);
	}
	/** Wrapper method for {@link XmlPullParser#getAttributeValue(int)} */
	@Override
	public String getAttributeValue(int index) {
		return parser.getAttributeValue(index);
	}
	/** Wrapper method for {@link XmlPullParser#getAttributeValue(String, String)} */
	@Override
	public String getAttributeValue(String namespace, String name) {
		return parser.getAttributeValue(namespace, name);
	}
	/** Wrapper method for {@link XmlPullParser#getEventType()} */
	@Override
	public int getEventType() throws XmlPullParserException {
		return parser.getEventType();
	}
	/** Wrapper method for {@link XmlPullParser#next()} */
	@Override
	public int next() throws XmlPullParserException, IOException {
		return parser.next();
	}
	/** Wrapper method for {@link XmlPullParser#nextToken()} */
	@Override
	public int nextToken() throws XmlPullParserException, IOException {
		return parser.nextToken();
	}
	/** Wrapper method for {@link XmlPullParser#require(int, String, String)} */
	@Override
	public void require(int type, String namespace, String name)
			throws XmlPullParserException, IOException {
		parser.require(type, namespace, name);
	}
	/** Wrapper method for {@link XmlPullParser#nextTag()} */
	@Override
	public String nextText() throws XmlPullParserException, IOException {
		return parser.nextText();
	}
	/** Wrapper method for {@link XmlPullParser#nextTag()} */
	@Override
	public int nextTag() throws XmlPullParserException, IOException {
		return parser.nextTag();
	}
}
