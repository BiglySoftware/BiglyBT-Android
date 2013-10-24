package com.vuze.android.remote;

import java.util.List;
import java.util.Map;

public interface JSInterfaceListener
{
	/**
	 * The Web UI is now ready for commands
	 */
	public void uiReady();

	/**
	 * Row Selection has changed
	 */
	@SuppressWarnings("rawtypes")
	public void selectionChanged(List<Map> selectedTorrentFields, boolean haveActiveSel,
			boolean havePausedSel);

	/**
	 * Indicates whether the Web UI handled the Go Back event
	 */
	public void cancelGoBack(boolean cancel);

	/**
	 * User definitely wants to delete the torrent.
	 * Do whatever it takes to delete that torrent (ie. call the WebUI)
	 */
	public void deleteTorrent(long torrentID);

	/**
	 * New upload/downlaod speeds reported
	 */
	public void updateSpeed(long downSpeed, long upSpeed);

	/**
	 * # of torrents in list has changed (filters affect torrent count)
	 */
	public void updateTorrentCount(long total);

	/**
	 * There are new session properties (might be the same values)
	 */
	public void sessionPropertiesUpdated(Map<?, ?> map);

	/**
	 * The Paused/Active states of the torrents have changed.  Only called
	 * on real change.
	 */
	public void updateTorrentStates(boolean haveActive, boolean havePaused,
			boolean haveActiveSel, boolean havePausedSel);
}
