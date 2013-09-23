package com.vuze.android.remote.rpc;

import java.util.Collections;
import java.util.Map;

public class RPC
{
	private static final String URL_PAIR = "http://pair.vuze.com/pairing/remote";
	
	public Map getBindingInfo(String ac) throws RPCException {
		String url = URL_PAIR + "/getBinding?sid=xmwebui&jsoncallback=&ac=" + ac;
		Object map = RestJsonClient.connect(url);
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (map instanceof Map) {
			//System.out.println("is map");
			Object result = ((Map) map).get("result");
			if (result instanceof Map) {
				//System.out.println("result is map");
				return (Map)result;
			} else {
				return (Map) map;
			}
		}
		return Collections.EMPTY_MAP;
	}
}
