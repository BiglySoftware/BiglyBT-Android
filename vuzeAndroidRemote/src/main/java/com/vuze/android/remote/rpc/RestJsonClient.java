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

package com.vuze.android.remote.rpc;

import java.util.Map;

import android.os.Build;

/**
 * Created by TuxPaper on 11/24/16.
 */
public abstract class RestJsonClient
{
	private static RestJsonClient oldClient = null;

	private static RestJsonClient newClient = null;

	abstract Object connect(String url)
			throws RPCException;

	abstract void setSupportsSendingGzip(boolean supportsSendingGzip,
			boolean supportsSendingChunk);

	abstract Map<?, ?> connect(String id, String url, Map<?, ?> jsonPost,
			Map<String, String> headers, String username, String password)
			throws RPCException;

	public static RestJsonClient getInstance(boolean supportsSendingGZip,
			boolean supportsChunkedRequests) {

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
			if (oldClient == null) {
				oldClient = new RestJsonClientDeprecated();
			}
			oldClient.setSupportsSendingGzip(supportsSendingGZip,
					supportsChunkedRequests);
			return oldClient;
		}

		if (newClient == null) {
			newClient = new RestJsonClientOkHttp();
		}
		newClient.setSupportsSendingGzip(supportsSendingGZip,
				supportsChunkedRequests);
		return newClient;
	}
}
