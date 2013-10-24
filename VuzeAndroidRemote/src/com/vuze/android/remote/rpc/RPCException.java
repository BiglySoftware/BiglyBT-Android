package com.vuze.android.remote.rpc;

public class RPCException
	extends Exception
{

	/**
	 * 
	 */
	private static final long serialVersionUID = -7786951414807258786L;

	public RPCException() {
		super();

	}

	public RPCException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

	public RPCException(String detailMessage) {
		super(detailMessage);
	}

	public RPCException(Throwable throwable) {
		super(throwable);
	}

}
