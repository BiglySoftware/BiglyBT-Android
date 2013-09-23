package com.vuze.android.remote;

public interface JSInterfaceListener
{
	public void uiReady();

	public void selectionChanged(long selectionCount, boolean haveActive,
			boolean havePaused, boolean haveActiveSel, boolean havePausedSel);

	public void cancelGoBack(boolean cancel);
}
