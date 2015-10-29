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
package com.handmark.pulltorefresh.library.internal;

public class Assert {
	private static final String MESSAGE_FORMAT = "\"%s\" argument must be not null.";

	public static void notNull(Object object, String argName) {
		if (object == null) {
			// When a parameter is null,
			// it throws a NullPointerException instead of an IllegalArgumentException.
			// This rule is recommended by the item 62 on Effective Java 2nd edition as follows.
			// "If a caller passes null in some parameter for which null values are prohibited, 
			// convention dictates that NullPointerException be thrown rather than IllegalArgumentException."
			throw new NullPointerException(String.format(MESSAGE_FORMAT, argName));
		}
	}
}