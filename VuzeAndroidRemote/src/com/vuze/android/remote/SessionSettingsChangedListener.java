package com.vuze.android.remote;

public interface SessionSettingsChangedListener
{
	public void sessionSettingsChanged(SessionSettings newSessionSettings);
	
	public void speedChanged(long downloadSpeed, long uploadSpeed);
}
