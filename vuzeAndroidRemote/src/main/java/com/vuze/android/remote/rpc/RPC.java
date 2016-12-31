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

import java.net.URLEncoder;
import java.util.Collections;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpProtocolParams;

import com.vuze.android.remote.AndroidUtils;
import com.vuze.android.remote.session.RemoteProfile;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.support.annotation.Nullable;
import android.util.Log;

public class RPC
{
	private static final String URL_PAIR = "https://pair.vuze.com/pairing/remote";

	private static final String TAG = "RPC";

	@SuppressWarnings("rawtypes")
	public static Map getBindingInfo(String ac, RemoteProfile remoteProfile)
			throws RPCException {
		String url = URL_PAIR + "/getBinding?sid=xmwebui&ac=" + ac;
		try {
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

			if (remoteProfile != null) {
				Map lastBindingInfo = remoteProfile.getLastBindingInfo();
				if (lastBindingInfo != null && lastBindingInfo.size() >= 3) {
					if (AndroidUtils.DEBUG_RPC) {
						Log.d(TAG, "getBindingInfo: using last bindingInfo");
					}
					return lastBindingInfo;
				}
			}
		} catch (RPCException e) {
			if (remoteProfile != null) {
				Map lastBindingInfo = remoteProfile.getLastBindingInfo();
				if (lastBindingInfo != null && lastBindingInfo.size() >= 3) {
					if (AndroidUtils.DEBUG_RPC) {
						Log.d(TAG, "getBindingInfo: using last bindingInfo");
					}
					return lastBindingInfo;
				}
			}
			throw e;
		}
		return Collections.EMPTY_MAP;
	}

	public static boolean isLocalAvailable() {
		Object oldThreadPolicy = null;
		try {
			if (android.os.Build.VERSION.SDK_INT > 9) {
				// allow synchronous networking because we are only going to localhost
				// and it will return really fast (it better!)
				oldThreadPolicy = enableNasty();
			}

			String url = "http://localhost:9091/transmission/rpc?json="
					+ URLEncoder.encode("{\"method\":\"session-get\"}", "utf-8");

			BasicHttpParams basicHttpParams = new BasicHttpParams();
			HttpProtocolParams.setUserAgent(basicHttpParams,
				AndroidUtils.VUZE_REMOTE_USERAGENT);
			HttpConnectionParams.setConnectionTimeout(basicHttpParams, 200);
			HttpConnectionParams.setSoTimeout(basicHttpParams, 900);
			HttpClient httpclient = new DefaultHttpClient(basicHttpParams);

			// Prepare a request object
			HttpGet httpget = new HttpGet(url); // IllegalArgumentException

			// Execute the request
			HttpResponse response = httpclient.execute(httpget);

			if (response.getStatusLine().getStatusCode() == 409) {
				// Must be RPC!
				return true;
			}

		} catch (HttpHostConnectException ignore) {
			// Connection to http://localhost:9091 refused
		} catch (Throwable e) {
			Log.e("RPC", "isLocalAvailable", e);
		} finally {
			if (android.os.Build.VERSION.SDK_INT > 9) {
				revertNasty((ThreadPolicy) oldThreadPolicy);
			}
		}
		return false;
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	private static Object enableNasty() {
		ThreadPolicy oldThreadPolicy = StrictMode.getThreadPolicy();
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitNetwork().build();
		StrictMode.setThreadPolicy(policy);
		return oldThreadPolicy;
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	private static void revertNasty(@Nullable ThreadPolicy oldPolicy) {
		if (oldPolicy == null) {
			return;
		}
		StrictMode.setThreadPolicy(oldPolicy);
	}
}
