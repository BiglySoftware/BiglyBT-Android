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

package com.vuze.android.remote.rpc;

import java.util.Collections;
import java.util.Map;

public class RPC
{
	private static final String URL_PAIR = "http://pair.vuze.com/pairing/remote";

	@SuppressWarnings("rawtypes")
	public Map getBindingInfo(String ac)
			throws RPCException {
		String url = URL_PAIR + "/getBinding?sid=xmwebui&ac=" + ac;
		Object map = RestJsonClient.connect(url);
		if (map instanceof Map) {
			//System.out.println("is map");
			Object result = ((Map) map).get("result");
			if (result instanceof Map) {
				//System.out.println("result is map");
				return (Map) result;
			} else {
				return (Map) map;
			}
		}
		return Collections.EMPTY_MAP;
	}

}
