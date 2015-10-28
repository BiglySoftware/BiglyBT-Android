package com.vuze.android.remote.rpc;

import java.util.Map;

public interface ReplyMapReceivedListener
{
	public void rpcSuccess(String id, Map<?, ?> optionalMap);

	public void rpcError(String id, Exception e);

	public void rpcFailure(String id, String message);
}