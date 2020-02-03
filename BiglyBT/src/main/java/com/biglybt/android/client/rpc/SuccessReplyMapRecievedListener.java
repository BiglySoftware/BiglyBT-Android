package com.biglybt.android.client.rpc;

import androidx.annotation.AnyThread;

/**
 * Created by TuxPaper on 10/13/18.
 */
@AnyThread
public interface SuccessReplyMapRecievedListener
	extends ReplyMapReceivedListener
{
	@Override
	default void rpcError(String requestID, Throwable e) {
	}

	@Override
	default void rpcFailure(String requestID, String message) {
	}
}
