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

import java.io.*;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import com.vuze.android.remote.AndroidUtils;

public class RestJsonClient
{
	public static Object connect(String url)
			throws RPCException {
		if (AndroidUtils.DEBUG) {
			System.out.println("Execute " + url);
		}
		long now = System.currentTimeMillis();

		BasicHttpParams basicHttpParams = new BasicHttpParams();
		HttpProtocolParams.setUserAgent(basicHttpParams, "Vuze Android Remote");
		HttpClient httpclient = new DefaultHttpClient(basicHttpParams);

		Object json = null;

		try {
			// Prepare a request object
			HttpGet httpget = new HttpGet(url); // IllegalArgumentException
			
			// Execute the request
			HttpResponse response;
			
			response = httpclient.execute(httpget);

			long then = System.currentTimeMillis();
			if (AndroidUtils.DEBUG) {
				System.out.println("  conn ->" + (then - now) + "ms");
			}

			now = then;

			HttpEntity entity = response.getEntity();

			if (entity != null) {

				// A Simple JSON Response Read
				InputStream instream = entity.getContent();
				InputStreamReader isr = new InputStreamReader(instream, "utf8");
				BufferedReader br = new BufferedReader(isr);
				br.mark(32767);

				then = System.currentTimeMillis();
				if (AndroidUtils.DEBUG) {
					System.out.println("  readin ->" + (then - now) + "ms");
				}
				now = then;

				try {
					json = JSONValue.parseWithException(br);

				} catch (ParseException pe) {

					br.reset();
					String line = br.readLine().trim();

					if (AndroidUtils.DEBUG) {
						System.out.println("line: " + line);
					}
					Header contentType = entity.getContentType();
					if (line.startsWith("<")
							|| line.contains("<html")
							|| (contentType != null && contentType.getValue().startsWith(
									"text/html"))) {
						// TODO: use android strings.xml
						throw new RPCException(
								"Could not retrieve remote client location information.  The most common cause is being on a guest wifi that requires login before using the internet.");
					}

					throw new RPCException(pe);
				} finally {
					br.close();
				}

				if (AndroidUtils.DEBUG) {
					System.out.println("JSON Result: " + json);
				}

			}
		} catch (RPCException e) {
			throw e;
		} catch (Throwable e) {
			throw new RPCException(e);
		}

		if (AndroidUtils.DEBUG) {
			long then = System.currentTimeMillis();
			System.out.println("  parse ->" + (then - now) + "ms");
		}
		return json;
	}

}
