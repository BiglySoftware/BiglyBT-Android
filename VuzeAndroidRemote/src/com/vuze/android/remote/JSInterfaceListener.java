/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 */

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

	/**
	 * Show the delete confirmation dialog 
	 */
	public void openDeleteTorrentDialog(String name, long torrentID);
}
