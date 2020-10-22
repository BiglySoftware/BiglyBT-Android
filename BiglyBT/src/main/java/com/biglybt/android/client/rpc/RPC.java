/*
 * Copyright (c) Azureus Software, Inc, All Rights Reserved.
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
 */

package com.biglybt.android.client.rpc;

import java.net.*;
import java.util.Collections;
import java.util.Map;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.session.RemoteProfile;

import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import androidx.annotation.Nullable;
import android.util.Log;

public class RPC
{
	public static final String PAIR_DOMAIN = "pair.biglybt.com";
	private static final String PAIR_DOMAIN_VUZE = "pair.vuze.com";

	private static final String URL_PAIR = "https://" + PAIR_DOMAIN
		+ "/pairing/remote";
	private static final String URL_PAIR_VUZE = "https://" + PAIR_DOMAIN_VUZE
		+ "/pairing/remote";

	private static final String TAG = "RPC";

	public static final int DEFAULT_RPC_PORT = 9091;

	public static final int LOCAL_VUZE_PORT = 9091;

	public static final int LOCAL_VUZE_REMOTE_PORT = 9092;

	public static final int LOCAL_BIGLYBT_PORT = 9093;

	@SuppressWarnings("rawtypes")
	public static Map getBindingInfo(RemoteProfile remoteProfile)
			throws RPCException {
		Map map = null;
		RPCException err = null;
		try {
			map = getBindingInfo(remoteProfile, URL_PAIR);
		} catch (RPCException e) {
			err = e;
		}

		if (map == null) {
			try {
				map = getBindingInfo(remoteProfile, URL_PAIR_VUZE);
			} catch (RPCException ignore) {
			}

			if (map != null) {
				return map;
			}
		}
			
		Map lastBindingInfo = remoteProfile.getLastBindingInfo();
		if (lastBindingInfo != null && lastBindingInfo.size() >= 3) {
			if (AndroidUtils.DEBUG_RPC) {
				Log.d(TAG, "getBindingInfo: using last bindingInfo");
			}
			return lastBindingInfo;
		}

		if (err != null) {
			throw err;
		}

		return Collections.EMPTY_MAP;
	}

	private static Map getBindingInfo(RemoteProfile remoteProfile, String URL_PAIR)
		throws RPCException {
		String url = URL_PAIR + "/getBinding?sid=xmwebui&ac=" + remoteProfile.getAC();

		RestJsonClient restJsonClient = RestJsonClient.getInstance(false, false);
		Object map = restJsonClient.connect(url);
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

		if (AndroidUtils.DEBUG_RPC) {
			Log.d(TAG, "getBindingInfo: empty or invalid reply from pair rpc");
		}

		return null;
	}

	public static boolean isLocalAvailable(int port) {
		Object oldThreadPolicy = null;
		try {
			// allow synchronous networking because we are only going to localhost
			// and it will return really fast (it better!)
			oldThreadPolicy = enableNasty();

			String url = "http://localhost:" + port + "/transmission/rpc?json="
					+ URLEncoder.encode("{\"method\":\"session-get\"}",
					AndroidUtils.UTF_8);

			HttpURLConnection cn = (HttpURLConnection) new URL(url).openConnection();
			cn.setRequestProperty("User-Agent", AndroidUtils.BIGLYBT_USERAGENT);
			cn.setConnectTimeout(200);
			cn.setReadTimeout(900);
			cn.connect();

			if (cn.getResponseCode() == 409) {
				// Must be RPC!
				return true;
			}

		} catch (ConnectException ignore) {
			// Connection to http://localhost:<port> refused
		} catch (Throwable e) {
			Log.e("RPC", "isLocalAvailable", e);
		} finally {
			revertNasty((ThreadPolicy) oldThreadPolicy);
		}
		return false;
	}

	private static Object enableNasty() {
		ThreadPolicy oldThreadPolicy = StrictMode.getThreadPolicy();
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitNetwork().build();
		StrictMode.setThreadPolicy(policy);
		return oldThreadPolicy;
	}

	private static void revertNasty(@Nullable ThreadPolicy oldPolicy) {
		if (oldPolicy == null) {
			return;
		}
		StrictMode.setThreadPolicy(oldPolicy);
	}
}
