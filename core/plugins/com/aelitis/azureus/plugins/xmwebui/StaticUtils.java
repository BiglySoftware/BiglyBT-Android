/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.aelitis.azureus.plugins.xmwebui;

import java.net.URL;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gudy.bouncycastle.util.encoders.Base64;

import com.biglybt.core.tag.*;
import com.biglybt.core.util.BEncoder;
import com.biglybt.core.util.UrlUtils;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.torrent.Torrent;

@SuppressWarnings({
	"rawtypes",
	"unchecked"
})
public class StaticUtils
{
	// @formatter:off
	private static final char[] encodingTable = {
			'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
			'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
			'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
			'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
			'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'
	};
	// @formatter:on

	public static String adjustURL(String host, URL url) {
		if (host == null || host.length() == 0) {

			return (url.toExternalForm());
		}

		int pos = host.indexOf(':');

		if (pos != -1) {

			host = host.substring(0, pos).trim();
		}

		return (UrlUtils.setHost(url, host).toExternalForm());
	}

	static boolean canAdd(String key, List<String> fields, boolean all) {
		return all || Collections.binarySearch(fields, key) >= 0;
	}

	static byte[] decodeBase64(String s) {
		String newLineCheck = s.substring(0, 90);
		boolean hasNewLine = newLineCheck.indexOf('\r') >= 0
				|| newLineCheck.indexOf('\n') >= 0;
		if (hasNewLine) {
			s = s.replaceAll("[\r\n]+", "");
		}
		return Base64.decode(s);
	}

	static String encodeNumber(long x) {
		char[] buf = new char[12];
		int p = buf.length;
		boolean isNeg = x < 0;
		if (isNeg) {
			x = x * -1;
		}
		do {
			int idx = (byte) ((x & 0xff) % 64);
			buf[--p] = encodingTable[idx];
			x >>>= 6;
		} while (x != 0);
		if (isNeg) {
			buf[--p] = '-';
		}
		return new String(buf, p, buf.length - p);
	}

	@SuppressWarnings("DuplicateStringLiteralInspection")
	public static String escapeXML(String str) {
		if (str == null) {
			return ("");
		}

		str = str.replaceAll("&", "&amp;");
		str = str.replaceAll(">", "&gt;");
		str = str.replaceAll("<", "&lt;");
		str = str.replaceAll("\"", "&quot;");
		str = str.replaceAll("--", "&#45;&#45;");

		return (str);
	}

	static List<String> fastSplit(String s, char charSplitter) {
		List<String> list = new ArrayList<>();

		int pos = 0;

		int len = s.length();
		while (pos < len) {
			int end = s.indexOf(charSplitter, pos);
			if (end == -1) {
				end = len;
			}
			String nextString = s.substring(pos, end);
			pos = end + 1; // Skip the delimiter.
			list.add(nextString);
		}

		return list;
	}

	protected static boolean getBoolean(Object o) {
		return getBoolean(o, false);
	}

	protected static Boolean getBoolean(Object o, Boolean defaultVal) {
		if (o instanceof Boolean) {

			return ((Boolean) o);

		} else if (o instanceof String) {

			return (((String) o).equalsIgnoreCase("true"));

		} else if (o instanceof Number) {

			return (((Number) o).intValue() != 0);

		} else {

			return (defaultVal);
		}
	}

	public static String getCausesMesssages(Throwable e) {
		try {
			StringBuilder sb = new StringBuilder();
			while (e != null) {
				if (sb.length() > 0) {
					sb.append(", ");
				}
				sb.append(e.getClass().getSimpleName());
				sb.append(": ");
				sb.append(e.getMessage());
				e = e.getCause();
			}

			return sb.toString();

		} catch (Throwable derp) {
			return "derp " + derp.getClass().getSimpleName(); //NON-NLS
		}
	}

	public static byte[] getHashFromMagnetURI(String magnetURI) {
		Pattern patXT = Pattern.compile("xt=urn:(?:btih|sha1):([^&]+)");
		Matcher matcher = patXT.matcher(magnetURI);
		if (matcher.find()) {
			return UrlUtils.decodeSHA1Hash(matcher.group(1));
		}
		return null;
	}

	protected static List getList(Object o) {
		if (o instanceof List) {
			return (List) o;
		} else {
			return new ArrayList();
		}
	}

	@SuppressWarnings("SameParameterValue")
	static List getMapList(Map args, String key, char charSplitter, List def) {
		Object oFilesHC = args.get(key);
		if (oFilesHC instanceof String) {
			return fastSplit((String) oFilesHC, charSplitter);
		}
		if (oFilesHC instanceof List) {
			return (List) oFilesHC;
		}
		return def;
	}

	static Number getNumber(Object val) {
		return getNumber(val, 0);
	}

	static Number getNumber(Object val, Number defaultNumber) {
		if (val instanceof Number) {
			return (Number) val;
		}
		if (val instanceof String) {
			NumberFormat format = NumberFormat.getInstance();
			try {
				return format.parse((String) val);
			} catch (ParseException e) {
				return defaultNumber;
			}
		}
		return defaultNumber;
	}

	static Tag getTagFromState(int state, boolean complete) {
		/*
		 	tag_initialising		= new MyTag( 0, "tag.type.ds.init",
			tag_downloading			= new MyTag( 1, "tag.type.ds.down",
			tag_seeding				= new MyTag( 2, "tag.type.ds.seed", 
			tag_queued_downloading	= new MyTag( 3, "tag.type.ds.qford"
			tag_queued_seeding		= new MyTag( 4, "tag.type.ds.qfors", 
			tag_stopped				= new MyTag( 5, "tag.type.ds.stop", 
			tag_error				= new MyTag( 6, "tag.type.ds.err", 
		*/
		int id = 0;

		switch (state) {
			case Download.ST_DOWNLOADING:
				id = 1;
				break;
			case Download.ST_SEEDING:
				id = 2;
				break;
			case Download.ST_QUEUED:
				id = complete ? 4 : 3;
				break;
			case Download.ST_STOPPED:
			case Download.ST_STOPPING:
				id = 5;
				break;
			case Download.ST_ERROR:
				id = 6;
				break;
		}
		TagManager tm = TagManagerFactory.getTagManager();
		return tm.getTagType(TagType.TT_DOWNLOAD_STATE).getTag(id);
	}

	// Copy of RelatedContentManager.getURLList, except with Torrent (not TOTorrent)
	public static List getURLList(Torrent torrent, String key) {
		Object obj = torrent.getAdditionalProperty(key);

		if (obj instanceof byte[]) {

			List l = new ArrayList();

			l.add(obj);

			return (l);

		} else if (obj instanceof List) {

			return (List) BEncoder.clone(obj);

		} else {

			return (new ArrayList());
		}
	}

	public static long hash(String string) {
		// Use simpler hashCode as Java caches it
		return string.hashCode();
// FROM http://stackoverflow.com/questions/1660501/what-is-a-good-64bit-hash-function-in-java-for-textual-strings
//adapted from String.hashCode()
//		long h = 1125899906842597L; // prime
//		int len = string.length();
//
//		for (int i = 0; i < len; i++) {
//			h = 31 * h + string.charAt(i);
//		}
//		return h;
	}

	static void hashAndAdd(SortedMap<String, Object> map, List<Map> addToList,
			List hcMatchList, int i) {
		long hashCode = longHashSimpleMap(map);
		// hex string shorter than long in json, even with quotes
		// Long.toString(hashCode) = Up to 19
		// Long.toHexString(hashCode) = Up to 16 + 2 = 18
		// Base64.encode(ByteFormatter.longToByteArray(hashCode)) = 8 bytes, 12 chars + 2 = 14
		// encodeNumber(hashCode) = up to 12
		String hc = encodeNumber(hashCode);

		boolean remove = hcMatchList != null && i < hcMatchList.size()
				&& hc.equals(hcMatchList.get(i));
		if (!remove) {
			map.put("hc", hc);
			addToList.add(map);
		}
	}

	static boolean hashAndAddAsCollection(SortedMap<String, Object> map,
			List<Collection> addToList, List hcMatchList, int i) {
		long hashCode = longHashSimpleMap(map);
		String hc = encodeNumber(hashCode);

		boolean remove = hcMatchList != null && i < hcMatchList.size()
				&& hc.equals(hcMatchList.get(i));
		if (!remove) {
			map.put("hc", hc);
			addToList.add(new ArrayList(map.values()));
			return true;
		}
		return false;
	}

	static long longHashSimpleList(Iterable<?> list) {
		long hash = 0;
		for (Object value : list) {
			if (value instanceof String) {
				hash = (hash * 31) + hash((String) value);
			} else if (value instanceof Number) {
				// not sure about this
				hash = (hash * 31) + value.hashCode();
			} else if (value instanceof SortedMap) {
				hash = (hash * 31) + longHashSimpleMap((SortedMap) value);
			} else if (value instanceof Iterable) {
				hash = (hash * 31) + longHashSimpleList((Iterable) value);
			} else if (value instanceof Boolean) {
				hash = (hash * 31) + ((Boolean) value ? 1231 : 1237);
			} // else skip all other values since we can't be sure how they hash
		}
		return hash;
	}

	/**
	 * Very simple 64 bit hash of a map's keys (assumed String, esp on JSON map),
	 * and values (JSON types -- String, Number, Map (object), List (array), Boolean.
	 */
	private static long longHashSimpleMap(SortedMap<?, ?> map) {
		long hash = 0;

		Object hc = map.get("hc");
		if (hc instanceof String) {
			return Long.parseLong((String) hc, 16);
		}

		for (Object key : map.keySet()) {
			Object value = map.get(key);
			hash = (hash * 31) + hash(key.toString());
			if (value instanceof String) {
				hash = (hash * 31) + hash((String) value);
			} else if (value instanceof Number) {
				// not sure about this
				hash = (hash * 31) + value.hashCode();
			} else if (value instanceof SortedMap) {
				hash = (hash * 31) + longHashSimpleMap((SortedMap) value);
			} else if (value instanceof Iterable) {
				hash = (hash * 31) + longHashSimpleList((Iterable) value);
			} else if (value instanceof Boolean) {
				hash = (hash * 31) + ((Boolean) value ? 1231 : 1237);
			} else {
				// else skip all other values since we can't be sure how they hash
				//System.out.println("Warning: Unhashed Value. key '" + key + "' Value: " + value);
			}
		}
		return hash;
	}

	public static int[] htmlColorToRGB(String value) {
		int[] ints = htmlColorToRGBA(value);
		if (ints.length == 4) {
			// Could multiply RGB by A/255
			return new int[] {
				ints[0],
				ints[1],
				ints[2]
			};
		}
		return ints;
	}

	public static int[] htmlColorToRGBA(String value) {
		int[] colors = null;
		if (value.charAt(0) == '#') {
			// hex color string
			long l = Long.parseLong(value.substring(1), 16);
			if (value.length() == 9) {
				colors = new int[] {
					(int) ((l >> 24) & 255),
					(int) ((l >> 16) & 255),
					(int) ((l >> 8) & 255),
					(int) (l & 255)
				};
			} else if (value.length() == 4) {
				colors = new int[] {
					(int) ((l >> 8) & 15) << 4,
					(int) ((l >> 8) & 4) << 4,
					(int) (l & 15) << 4,
					(int) 255
				};
			} else {
				colors = new int[] {
					(int) ((l >> 16) & 255),
					(int) ((l >> 8) & 255),
					(int) (l & 255),
					(int) 255
				};
			}
		}
		return colors;
	}
}
