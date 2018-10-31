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

package com.biglybt.android.client.rpc;

import java.io.IOException;

import okhttp3.*;
import okio.BufferedSink;
import okio.GzipSink;
import okio.Okio;

/** 
 * From https://github.com/square/okhttp/wiki/Interceptors#rewriting-requests
 * </p>
 * This interceptor compresses the HTTP request body. Many webservers can't
 * handle this!
 */
public class GzipRequestInterceptor
	implements Interceptor
{
	@Override
	public Response intercept(Interceptor.Chain chain)
			throws IOException {
		Request originalRequest = chain.request();
		if (originalRequest.body() == null
				|| originalRequest.header("Content-Encoding") != null) {
			return chain.proceed(originalRequest);
		}

		Request compressedRequest = originalRequest.newBuilder().header(
				"Content-Encoding", "gzip").method(originalRequest.method(),
						gzip(originalRequest.body())).build();
		return chain.proceed(compressedRequest);
	}

	private RequestBody gzip(final RequestBody body) {
		return new RequestBody() {
			@Override
			public MediaType contentType() {
				return body.contentType();
			}

			@Override
			public long contentLength() {
				return -1; // We don't know the compressed length in advance!
			}

			@Override
			public void writeTo(BufferedSink sink)
					throws IOException {
				BufferedSink gzipSink = Okio.buffer(new GzipSink(sink));
				body.writeTo(gzipSink);
				gzipSink.close();
			}
		};
	}
}
