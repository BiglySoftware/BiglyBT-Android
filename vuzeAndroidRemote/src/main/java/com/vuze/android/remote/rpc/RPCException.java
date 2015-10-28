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

import org.apache.http.HttpResponse;

public class RPCException
	extends Exception
{
	private static final long serialVersionUID = -7786951414807258786L;
	private HttpResponse httpResponse;

	public RPCException(HttpResponse response, String string) {
		super(string);
		httpResponse = response;
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
	
	public int getResponseCode() {
		return httpResponse.getStatusLine().getStatusCode();
	}
	
	public HttpResponse getHttpResponse() {
		return httpResponse;
	}

}
