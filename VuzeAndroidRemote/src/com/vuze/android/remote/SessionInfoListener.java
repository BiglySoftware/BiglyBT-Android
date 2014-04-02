package com.vuze.android.remote;

import com.vuze.android.remote.rpc.TransmissionRPC;

public interface SessionInfoListener
{
	public void transmissionRpcAvailable(SessionInfo sessionInfo);

	public void uiReady(TransmissionRPC rpc);
}