package com.vuze.android.remote;

import java.util.Map;

public interface JSInterfaceListener
{
	public void uiReady();

	public void selectionChanged(long selectionCount, boolean haveActiveSel,
			boolean havePausedSel);

	public void cancelGoBack(boolean cancel);

	public void deleteTorrent(long torrentID);

	public void updateSpeed(long downSpeed, long upSpeed);

	public void updateTorrentCount(long total);

	public void sessionPropertiesUpdated(Map map);

	public void updateTorrentStates(boolean haveActive, boolean havePaused,
			boolean haveActiveSel, boolean havePausedSel);
}
