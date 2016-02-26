package com.vuze.android.remote.rpc;

import java.util.Map;

public interface ReplyMapReceivedListener
{
	void rpcSuccess(String id, Map<?, ?> optionalMap);

	void rpcError(String id, Exception e);

	void rpcFailure(String id, String message);
}