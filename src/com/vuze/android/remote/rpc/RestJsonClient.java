package com.vuze.android.remote.rpc;

import java.io.*;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.json.simple.JSONValue;

public class RestJsonClient
{
	public static Object connect(String url) throws RPCException {
		System.out.println("Execute " + url);
		long now = System.currentTimeMillis();

		BasicHttpParams basicHttpParams = new BasicHttpParams();
		HttpProtocolParams.setUserAgent(basicHttpParams, "Tux");
		HttpClient httpclient = new DefaultHttpClient(basicHttpParams);

		// Prepare a request object
		HttpGet httpget = new HttpGet(url);

		// Execute the request
		HttpResponse response;


		Object json = null;

		
		String result = null;

		try {
			response = httpclient.execute(httpget);

			long then = System.currentTimeMillis();
			System.out.println("  conn ->" + (then - now) + "ms");

			now = then;

			HttpEntity entity = response.getEntity();

			if (entity != null) {

				// A Simple JSON Response Read
				InputStream instream = entity.getContent();
				result = convertStreamToString(instream, entity.getContentLength());

				then = System.currentTimeMillis();
				System.out.println("  readin ->" + (then - now) + "ms");
				now = then;
				
				//System.out.println("Resut is " + result);

				if (result.startsWith("(")) {
					if (result.endsWith(")\n")) {
						result = result.substring(1, result.length() - 2);
					} else if (result.endsWith(")"))  {
						result = result.substring(1, result.length() - 1);
					}
					//System.out.println("result trimmed to " + result);
				}
				json = JSONValue.parse(result);
				
				System.out.println("JSON Result: " + json);

				instream.close();
			}
			
		} catch (Error e) {

			System.err.println("result = " + result);
			
			if (e.getMessage().equals("Lexical Error: Unmatched Input.")) {
				if (result != null && result.contains("<html")) {
					// TODO: use android strings.xml
					throw new RPCException("Could not retrieve remote client location information.  The most common cause is being on a guest wifi that requires login before using the internet.", e);
				}
			}

			throw new RPCException(e);
		} catch (Throwable e) {
			System.err.println("result = " + result);
			throw new RPCException(e);
		}

		long then = System.currentTimeMillis();
		System.out.println("  parse ->" + (then - now) + "ms");
		return json;
	}

	/**
	 *
	 * @param is
	 * @return String
	 */
	public static String convertStreamToString(InputStream is, long len) {
		BufferedReader reader = new BufferedReader(new InputStreamReader(is),
				(int) (len > 4096 ? 4096 : (len < 1024) ? 1024 : len));
		StringBuilder sb = new StringBuilder();

		String line = null;
		try {
			while ((line = reader.readLine()) != null) {
				sb.append(line + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return sb.toString();
	}

}
