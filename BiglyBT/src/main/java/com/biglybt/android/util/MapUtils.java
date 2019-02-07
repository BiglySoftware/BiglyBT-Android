/*
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

package com.biglybt.android.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import androidx.annotation.Nullable;

import com.biglybt.android.client.AndroidUtils;

/**
 * @author TuxPaper
 */
@SuppressWarnings("rawtypes")
public class MapUtils
{
	public static int getMapInt(@Nullable Map map, String key, int def) {
		if (map == null) {
			return def;
		}
		try {
			Number n = (Number) map.get(key);

			if (n == null) {

				return (def);
			}

			return n.intValue();
		} catch (Throwable e) {
			return def;
		}
	}

	public static long getMapLong(Map map, String key, long def) {
		if (map == null) {
			return def;
		}
		try {
			Number n = (Number) map.get(key);

			if (n == null) {

				return (def);
			}

			return n.longValue();
		} catch (Throwable e) {
			return def;
		}
	}

	public static long parseMapLong(Map map, String key, long def) {
		if (map == null) {
			return def;
		}
		try {
			Object o = map.get(key);
			if (o == null) {
				return def;
			}
			if (o instanceof Number) {
				Number n = (Number) o;
				return n.longValue();
			} else {
				return Long.parseLong(o.toString());
			}

		} catch (Throwable e) {
			return def;
		}
	}

	public static double parseMapDouble(Map map, String key, double def) {
		if (map == null) {
			return def;
		}
		try {
			Object o = map.get(key);
			if (o == null) {
				return def;
			}
			if (o instanceof Number) {
				Number n = (Number) o;
				return n.doubleValue();
			} else {
				return Double.parseDouble(o.toString());
			}

		} catch (Throwable e) {
			return def;
		}
	}

	public static String getMapString(@Nullable Map map, String key,
			@Nullable String def) {
		if (map == null) {
			return def;
		}
		try {
			Object o = map.get(key);
			if (o == null && !map.containsKey(key)) {
				return def;
			}
			// NOTE: The above returns def when map doesn't contain the key,
			//       which suggests below we would return the null when o is null.  
			//       But we don't! And now, some callers rely on this :(

			if (o instanceof String) {
				return (String) o;
			}
			if (o instanceof byte[]) {
				return new String((byte[]) o, AndroidUtils.UTF_8);
			}
			return def;
		} catch (Throwable t) {
			return def;
		}
	}

	@SuppressWarnings("unchecked")
	public static void setMapString(Map map, String key, String val) {
		if (map == null) {
			return;
		}
		try {
			if (val == null) {
				map.remove(key);
			} else {
				map.put(key, val.getBytes(AndroidUtils.UTF_8));
			}
		} catch (Throwable ignore) {
		}
	}

	public static Object getMapObject(@Nullable Map map, String key, Object def,
			Class cla) {
		if (map == null) {
			return def;
		}
		try {
			Object o = map.get(key);
			if (cla.isInstance(o)) {
				return o;
			} else {
				return def;
			}
		} catch (Throwable t) {
			return def;
		}
	}

	public static boolean getMapBoolean(@Nullable Map map, String key,
			boolean def) {
		if (map == null) {
			return def;
		}
		try {
			Object o = map.get(key);
			if (o instanceof Boolean) {
				return (Boolean) o;
			}

			if (o instanceof Long) {
				return (Long) o == 1;
			}

			return def;
		} catch (Throwable e) {
			return def;
		}
	}

	public static List getMapList(Map map, String key, @Nullable List def) {
		if (map == null) {
			return def;
		}
		try {
			Object o = map.get(key);
			if (o instanceof List) {
				return (List) o;
			}
			if (o == null) {
				return def;
			}
			if (o.getClass().isArray()) {
				return Arrays.asList((Object[]) o);
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return def;
	}

	public static Map getMapMap(Map map, String key, @Nullable Map def) {
		if (map == null) {
			return def;
		}
		try {
			Map valMap = (Map) map.get(key);
			if (valMap == null && !map.containsKey(key)) {
				return def;
			}
			return valMap;
		} catch (Throwable t) {
			return def;
		}
	}

	public static float getMapFloat(Map map, String key, float def) {
		if (map == null) {
			return def;
		}
		try {
			Number n = (Number) map.get(key);

			if (n == null) {

				return (def);
			}

			return n.floatValue();
		} catch (Throwable e) {
			return def;
		}
	}

}
