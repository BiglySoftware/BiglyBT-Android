package com.biglybt.android.client.rpc;

import android.support.annotation.AnyThread;

/**
 * Created by TuxPaper on 10/13/18.
 */
@AnyThread
public interface SuccessReplyMapRecievedListener
	extends ReplyMapReceivedListener
{
	@Override
	public default void rpcError(String requestID, Exception e) {
	}

	@Override
	public default void rpcFailure(String requestID, String message) {
	}
}
