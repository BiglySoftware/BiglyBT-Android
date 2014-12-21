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
import org.apache.http.params.HttpProtocolParams;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.util.Log;

public class RPC
{
	private static final String URL_PAIR = "https://pair.vuze.com/pairing/remote";

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
			HttpProtocolParams.setUserAgent(basicHttpParams, "Vuze Android Remote");
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
	private static void revertNasty(ThreadPolicy oldPolicy) {
		if (oldPolicy == null) {
			return;
		}
		StrictMode.setThreadPolicy(oldPolicy);
	}
}
