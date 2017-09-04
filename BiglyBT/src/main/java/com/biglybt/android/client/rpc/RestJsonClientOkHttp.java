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

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.*;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.R;
import com.biglybt.android.util.JSONUtils;
import com.biglybt.util.Base64Encode;
import com.biglybt.util.Thunk;

import android.support.annotation.Nullable;
import android.util.Log;

import okhttp3.*;
import okhttp3.internal.http.HttpHeaders;
import okio.BufferedSink;
import okio.GzipSink;
import okio.Okio;

/**
 * Connects to URL, decodes JSON results
 */
public class RestJsonClientOkHttp
	extends RestJsonClient
{
	private static final String TAG = "RPC";

	private static final boolean DEBUG_DETAILED = AndroidUtils.DEBUG_RPC;

	// StringBuilder and JSON Reader parser are about the same speed, but SB probably uses more memory
	private static final boolean USE_STRINGBUILDER = false;

	private static final MediaType MEDIATYPE_JSON = MediaType.parse(
			"application/json; charset=utf-8");

	private OkHttpClient client = null;

	private boolean supportsSendingGzip = false;

	private boolean supportsSendingChunk = false;

	public void setSupportsSendingGzip(boolean supportsSendingGzip,
			boolean supportsSendingChunk) {
		if (supportsSendingGzip == this.supportsSendingGzip
				&& supportsSendingChunk == this.supportsSendingChunk) {
			return;
		}
		this.supportsSendingGzip = supportsSendingGzip;
		this.supportsSendingChunk = supportsSendingChunk;
		client = null;
	}

	@Override
	public Object connect(String url)
			throws RPCException {
		return connect("", url, null, null, null, null);
	}

	@Override
	public Map<?, ?> connect(String id, String url, @Nullable Map<?, ?> jsonPost,
			@Nullable Map<String, String> headers, @Nullable String username,
			@Nullable String password)
			throws RPCException {
		long readTime = 0;
		long connSetupTime = 0;
		long connTime = 0;
		int bytesRead = 0;
		if (DEBUG_DETAILED) {
			Log.d(TAG, id + "] Execute " + url);
		}
		long now = System.currentTimeMillis();
		long then;

		Map<?, ?> json = Collections.EMPTY_MAP;

		try {
			URI uri = new URI(url);

			if (client == null) {
				client = getUnsafeOkHttpClient(
						supportsSendingGzip && supportsSendingChunk);
			}

			OkHttpClient localClient = client;
			if (uri.getHost().endsWith(".i2p")) {
				localClient = client.newBuilder().proxy(new Proxy(Proxy.Type.HTTP,
						new InetSocketAddress("127.0.0.1", 4444))).build();
			}
			Request.Builder builder = new Request.Builder().url(url).header(
					"User-Agent", AndroidUtils.BIGLYBT_USERAGENT).header("Accept",
							"application/json");

			if (id != null) {
				builder.header("vr-id",
						id.length() < 50 ? id : (id.substring(0, 50) + "..."));
			}
			if (headers != null) {
				for (String key : headers.keySet()) {
					builder.header(key, headers.get(key));
				}
			}

			if (jsonPost != null) {
				String postString = JSONUtils.encodeToJSON(jsonPost);
				if (AndroidUtils.DEBUG_RPC) {
					Log.d(TAG, id + "]  Post: " + postString);
				}
				if (supportsSendingGzip && !supportsSendingChunk) {
					builder.addHeader("Content-Encoding", "gzip");
					byte[] compressed = compressString(postString);
					RequestBody requestBody = RequestBody.create(MEDIATYPE_JSON,
							compressed);
					builder.post(requestBody);
				} else {
					RequestBody requestBody = RequestBody.create(MEDIATYPE_JSON,
							postString.getBytes());
					builder.post(requestBody);
				}
			}

			if (username != null) {
				byte[] toEncode = (username + ":" + password).getBytes();
				String encoding = Base64Encode.encodeToString(toEncode, 0,
						toEncode.length);
				builder.header("Authorization", "Basic " + encoding);
			}

			Request request = builder.build();

			// Execute the response
			then = System.currentTimeMillis();
			if (AndroidUtils.DEBUG_RPC) {
				connSetupTime = (then - now);
				now = then;
			}

			Response response = localClient.newCall(request).execute();

			then = System.currentTimeMillis();
			if (AndroidUtils.DEBUG_RPC) {
				connTime = (then - now);
				now = then;
			}

			int statusCode = response.code();

			if (AndroidUtils.DEBUG_RPC && statusCode != 200) {
				Log.d(TAG, "StatusCode: " + statusCode);
			}

			if (statusCode == 401) {
				throw new RPCException(
						"Not Authorized.  It's possible that the remote client is in View-Only mode.");
			}

			ResponseBody body = response.body();
			long contentLength = body.contentLength();
			if (contentLength >= Integer.MAX_VALUE - 2) {
				throw new RPCException("JSON response too large");
			}

			Reader isr = null; // body.charStream();
			StringBuilder sb = null;
			BufferedReader br = null;
			try {
				if (USE_STRINGBUILDER) {
					isr = body.charStream();
					sb = new StringBuilder(
							contentLength > 512 ? (int) contentLength + 2 : 512);
					char c[] = new char[8192];
					while (true) {
						int read = isr.read(c);
						if (read < 0) {
							break;
						}
						sb.append(c, 0, read);
						//Log.d(TAG, id + "] Read " + read + ";size=" + sb.length());
					}

					if (AndroidUtils.DEBUG_RPC) {
						then = System.currentTimeMillis();
						if (DEBUG_DETAILED) {
							if (sb.length() > 2000) {
								Log.d(TAG, id + "] " + sb.substring(0, 2000) + "...");
							} else {
								Log.d(TAG, id + "] " + sb.toString());
							}
						}
						bytesRead = sb.length();
						readTime = (then - now);
						now = then;
					}

					json = JSONUtils.decodeJSON(sb.toString());
				} else {
					isr = body.charStream();
					br = new BufferedReader(isr, 8192);
					br.mark(32767);
					json = JSONUtils.decodeJSON(br);
					if (DEBUG_DETAILED) {
						String s = json.toString();
						if (s.length() > 2000) {
							Log.d(TAG, id + "] " + s.substring(0, 2000) + "...");
						} else {
							Log.d(TAG, id + "] " + s.toString());
						}
					}

					if (AndroidUtils.DEBUG_RPC) {
						bytesRead = (int) body.contentLength();
						if (bytesRead == -1) {
							bytesRead = (int) HttpHeaders.lastValidLengthForDebug;
						}
					}
				}

			} catch (Exception pe) {

				if (statusCode == 409) {
					throw new RPCException(response, statusCode, "409");
				}

				String line = null;
				try {
					if (USE_STRINGBUILDER) {
						line = sb.subSequence(0, Math.min(128, sb.length())).toString();
					} else if (br != null) {
						br.reset();
						line = br.readLine().trim();
					} else {
						line = "";
					}

					if (isr != null) {
						isr.close();
					}

					if (AndroidUtils.DEBUG_RPC) {
						Log.d(TAG, id + "]line: " + line);
					}
					MediaType contentType = body.contentType();
					if (line.startsWith("<") || line.contains("<html")
							|| (contentType != null
									&& contentType.type().startsWith("text/html"))) {
						if (AndroidUtils.DEBUG_RPC) {
							String msg = statusCode + ": " + response.message() + "\n"
									+ pe.getMessage();
							Log.d(TAG, "connect: " + msg);
						}

						// TODO: use android strings.xml
						throw new RPCException(response, statusCode,
								sb == null ? line : sb.toString(),
								R.string.rpcexception_HTMLnotJSON, pe);
					}
					if (line.matches("^d[0-9]+:.*$")) {
						// bencoded.  We don't have a bdecoder, so parse out failure reason if it's there
						Pattern pattern = Pattern.compile("failure reason[0-9]+:(.+)e");
						Matcher matcher = pattern.matcher(line);
						if (matcher.find()) {
							String reason = matcher.group(1);
							Map map = new HashMap();
							map.put("result", "error: " + reason);
							return map;
						}
					}
				} catch (IOException ignore) {

				}

				Log.e(TAG, id, pe);
				String msg = statusCode + ": " + response.message() + "\n"
						+ pe.getMessage();
				throw new RPCException(response, statusCode,
						sb == null ? line : sb.toString(), msg, pe);
			} finally {
				body.close();
			}

			//if (AndroidUtils.DEBUG_RPC) {
//					Log.d(TAG, id + "]JSON Result: " + json);
			//}

		} catch (RPCException e) {
			throw e;
		} catch (Throwable e) {
			Log.e(TAG, id, e);
			throw new RPCException(e);
		}

		if (AndroidUtils.DEBUG_RPC) {
			then = System.currentTimeMillis();
			Log.d(TAG,
					id + "] conn " + connSetupTime + "/" + connTime + "ms. Read "
							+ bytesRead + " in " + readTime + "ms, parsed in " + (then - now)
							+ "ms");
		}
		return json;
	}

	private static OkHttpClient getUnsafeOkHttpClient(boolean sendChunkedGzip) {
		try {
			// Create a trust manager that does not validate certificate chains
			final TrustManager[] trustAllCerts = new TrustManager[] {
				new X509TrustManager() {
					@Override
					public void checkClientTrusted(
							java.security.cert.X509Certificate[] chain, String authType)
							throws CertificateException {
					}

					@Override
					public void checkServerTrusted(
							java.security.cert.X509Certificate[] chain, String authType)
							throws CertificateException {
					}

					@Override
					public java.security.cert.X509Certificate[] getAcceptedIssuers() {
						return new java.security.cert.X509Certificate[] {};
					}
				}
			};

			// Install the all-trusting trust manager
			final SSLContext sslContext = SSLContext.getInstance("SSL");
			sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
			// Create an ssl socket factory with our all-trusting manager
			final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

			OkHttpClient.Builder builder = new OkHttpClient.Builder();
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "getUnsafeOkHttpClient: sendChunkedGZip=" + sendChunkedGzip);
			}
			if (sendChunkedGzip) {
				builder.addInterceptor(new GzipRequestInterceptor());
			}
			builder.sslSocketFactory(sslSocketFactory);
			builder.hostnameVerifier(new HostnameVerifier() {
				@Override
				public boolean verify(String hostname, SSLSession session) {
					return true;
				}
			});

			builder.retryOnConnectionFailure(true).connectTimeout(15,
					TimeUnit.SECONDS).readTimeout(120L, TimeUnit.SECONDS).writeTimeout(
							15L, TimeUnit.SECONDS);

			return builder.build();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * This interceptor compresses the HTTP request body. Many webservers can't
	 * handle this!
	 */
	@Thunk
	static class GzipRequestInterceptor
		implements Interceptor
	{
		@Override
		public Response intercept(Chain chain)
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

		private static RequestBody gzip(final RequestBody body) {
			return new RequestBody() {
				@Override
				public MediaType contentType() {
					return body.contentType();
				}

				@Override
				public long contentLength()
						throws IOException {
					return -1;
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

	private static byte[] compressString(String str)
			throws IOException {
		if (str == null || str.length() == 0) {
			return new byte[0];
		}

		ByteArrayOutputStream arr = new ByteArrayOutputStream(str.length());
		OutputStream zipper = new GZIPOutputStream(arr);
		zipper.write(str.getBytes());
		zipper.close();

		return arr.toByteArray();
	}
}
