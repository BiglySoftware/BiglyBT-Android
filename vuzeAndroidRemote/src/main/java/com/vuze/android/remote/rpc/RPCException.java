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

import java.util.HashMap;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpResponse;

import com.vuze.android.remote.VuzeRemoteApp;

import android.support.annotation.StringRes;

import okhttp3.Response;

public class RPCException
	extends Exception
{
	private static final long serialVersionUID = -7786951414807258786L;

	private Response response;

	private HttpResponse httpResponse;

	private int responseCode;

	private String httpResponseText;

	public RPCException(HttpResponse response, int responseCode, String string) {
		super(string);
		httpResponse = response;
		this.responseCode = responseCode;
	}

	public RPCException(HttpResponse response, int responseCode, String text,
			@StringRes int responseResID, Throwable cause) {
		super(VuzeRemoteApp.getContext().getResources().getString(responseResID),
				cause);
		this.responseCode = responseCode;
		httpResponse = response;
		httpResponseText = text;
	}

	public RPCException(HttpResponse response, int responseCode, String text,
			String errorString, Throwable cause) {
		super(errorString, cause);
		this.responseCode = responseCode;
		httpResponse = response;
		httpResponseText = text;
	}

	public RPCException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

	public RPCException(String detailMessage) {
		super(detailMessage);
	}

	public RPCException(Throwable throwable) {
		super(throwable);
	}

	public RPCException(Response response, int responseCode, String text) {
		this.response = response;
		this.responseCode = responseCode;
		httpResponseText = text;
	}

	public RPCException(Response response, int responseCode, String text,
			int responseResID, Exception cause) {
		super(VuzeRemoteApp.getContext().getResources().getString(responseResID),
				cause);
		this.response = response;
		this.responseCode = responseCode;
		httpResponseText = text;
	}

	public RPCException(Response response, int responseCode, String text,
			String errorString, Throwable cause) {
		super(errorString, cause);
		this.responseCode = responseCode;
		this.response = response;
		httpResponseText = text;
	}

	public Map<String, String> getFirstHeader(String key) {
		Map map = new HashMap(1);
		if (httpResponse != null) {
			Header firstHeader = httpResponse.getFirstHeader(key);
			map.put(firstHeader.getName(), firstHeader.getValue());
		}
		if (response != null) {
			String val = response.header(key);
			map.put(key, val);
		}
		return map;
	}

	public String getHttpResponseText() {
		return httpResponseText == null ? "" : httpResponseText;
	}

	public int getResponseCode() {
		return responseCode;
	}

	private HttpResponse getHttpResponse() {
		return httpResponse;
	}

}
