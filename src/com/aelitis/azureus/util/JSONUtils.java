/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 */

package com.aelitis.azureus.util;

import java.io.UnsupportedEncodingException;
import java.util.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import android.annotation.SuppressLint;
import android.util.Base64;


/**
 * @author TuxPaper
 * @created Feb 14, 2007
 *
 */
@SuppressWarnings({
	"unchecked",
	"rawtypes"
})
public class JSONUtils
{
	/**
	 * decodes JSON formatted text into a map.
	 * 
	 * @return Map parsed from a JSON formatted string
	 * <p>
	 *  If the json text is not a map, a map with the key "value" will be returned.
	 *  the value of "value" will either be an List, String, Number, Boolean, or null
	 *  <p>
	 *  if the String is formatted badly, null is returned
	 */
	public static Map decodeJSON(String json) {
		try {
			Object object = JSONValue.parseWithException(json);
			if (object instanceof Map) {
				return (Map) object;
			}
			// could be : ArrayList, String, Number, Boolean
			Map map = new HashMap();
			map.put("value", object);
			return map;
		} catch (Throwable t) {
			return null;
		}
	}

	public static List decodeJSONList(String json) {
		try {
			Object object = JSONValue.parseWithException(json);
			if (object instanceof List) {
				return (List) object;
			}
			// could be : Map, String, Number, Boolean
			List list = new ArrayList();
			list.add(object);
			return list;
		} catch (Throwable t) {
			return null;
		}
	}

	/**
	 * encodes a map into a JSONObject.
	 * <P>
	 * It's recommended that you use {@link #encodeToJSON(Map)} instead
	 * 
	 * @param map
	 * @return
	 *
	 * @since 3.0.1.5
	 */
	@SuppressLint("NewApi")
	public static Map encodeToJSONObject(Map map) {
		Map newMap = new JSONObject();

		for (Iterator iter = map.keySet().iterator(); iter.hasNext();) {
			String key = (String) iter.next();
			Object value = map.get(key);

			if (value instanceof byte[]) {
				key += ".B64";
				value = Base64.encode((byte[]) value, Base64.DEFAULT);
			}

			value = coerce(value);

			newMap.put(key, value);
		}
		return newMap;
	}

	/**
	 * Encodes a map into a JSON formatted string.
	 * <p>
	 * Handles multiple layers of Maps and Lists.  Handls String, Number,
	 * Boolean, and null values.
	 * 
	 * @param map Map to change into a JSON formatted string
	 * @return JSON formatted string
	 *
	 * @since 3.0.1.5
	 */
	public static String encodeToJSON(Map map) {
		return encodeToJSONObject(map).toString();
	}

	public static String encodeToJSON(Collection list) {
		return encodeToJSONArray(list).toString();
	}

	private static Object coerce(Object value) {
		if (value instanceof Map) {
			value = encodeToJSONObject((Map) value);
		} else if (value instanceof List) {
			value = encodeToJSONArray((List) value);
		} else if (value instanceof Object[]) {
			Object[] array = (Object[]) value;
			value = encodeToJSONArray(Arrays.asList(array));
		} else if (value instanceof byte[]) {
			try {
				value = new String((byte[]) value, "utf-8");
			} catch (UnsupportedEncodingException e) {
			}
		}
		return value;
	}

	/**
	 * @param value
	 * @return
	 *
	 * @since 3.0.1.5
	 */
	private static List encodeToJSONArray(Collection list) {
		List newList = new JSONArray(list);

		for (int i = 0; i < newList.size(); i++) {
			Object value = newList.get(i);

			newList.set(i, coerce(value));
		}

		return newList;
	}
}
