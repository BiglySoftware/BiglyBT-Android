package com.vuze.android.remote;

public class RPCException
	extends Exception
{

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
