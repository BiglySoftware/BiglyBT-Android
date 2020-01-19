/*
 * Copyright (c) Azureus Software, Inc, All Rights Reserved.
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
 */

package com.biglybt.android.client;

import java.util.List;
import java.util.Map;

import com.biglybt.android.client.session.*;
import com.biglybt.android.util.MapUtils;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

public class TorrentUtils
{
	public static final char[] ANYSLASH = new char[] { '/', '\\' };

	@NonNull
	public static String getSaveLocation(Session session, Map<?, ?> mapTorrent) {
		String saveLocation = MapUtils.getMapString(mapTorrent,
				TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR, null);

		if (saveLocation == null) {
			if (session == null) {
				saveLocation = "dunno"; //NON-NLS
			} else {
				SessionSettings sessionSettings = session.getSessionSettingsClone();
				if (sessionSettings == null) {
					saveLocation = "";
				} else {
					saveLocation = sessionSettings.getDownloadDir();
					if (saveLocation == null) {
						saveLocation = "";
					}
				}
			}
		}

		// if simple torrent, download dir might have file name attached
		List<?> listFiles = MapUtils.getMapList(mapTorrent,
				TransmissionVars.FIELD_TORRENT_FILES, null);
		if (listFiles == null) {
			// files map not filled yet -- try guessing with numFiles
			int numFiles = MapUtils.getMapInt(mapTorrent,
					TransmissionVars.FIELD_TORRENT_FILE_COUNT, 0);
			if (numFiles == 1) {
				int posDot = saveLocation.lastIndexOf('.');
				int posSlash = AndroidUtils.lastindexOfAny(saveLocation, ANYSLASH, -1);
				if (posDot >= 0 && posSlash >= 0) {
					// probably contains filename -- chop it off
					saveLocation = saveLocation.substring(0, posSlash);
				}
			}
		} else if (listFiles.size() == 1) {
			Map<?, ?> firstFile = (Map<?, ?>) listFiles.get(0);
			String firstFileName = MapUtils.getMapString(firstFile,
					TransmissionVars.FIELD_FILES_NAME, null);
			if (firstFileName != null && saveLocation.endsWith(firstFileName)) {
				saveLocation = saveLocation.substring(0,
						saveLocation.length() - firstFileName.length());
			}
		}

		return saveLocation;
	}

	/**
	 * A Simple Torrent (aka single-file) which is a torrent that has no parent directory.
	 * <p/>
	 * Note: A torrent can have 1 file and still NOT be a Simple Torrent.
	 */
	public static boolean isSimpleTorrent(Map mapTorrent) {
		boolean isSimpleTorrent = MapUtils.getMapInt(mapTorrent,
						TransmissionVars.FIELD_TORRENT_FILE_COUNT, 0) == 1;
		/* TODO: Handle case where torrent has one file but isn't a simple torrent
		 * Transmission RPC doesn't have a field to determine this (downloadDir is always present, and
		 * torrent's "length" field is never passed)
		 * 
		 * If the user has not modified the display name of the torrent, a non-simple torrent's name
		 * will be the same as the last pathname of downloadDir
		 */

		if (isSimpleTorrent) {
			String saveLocation = MapUtils.getMapString(mapTorrent,
							TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR, null);
			String name = MapUtils.getMapString(mapTorrent,
							TransmissionVars.FIELD_TORRENT_NAME, null);
			if (saveLocation != null && name != null && name.length() > 0 && saveLocation.endsWith(name)) {
				isSimpleTorrent = false;
			}
		}
		return isSimpleTorrent;
	}

	public static boolean isAllowRefresh(@Nullable Session session) {
		if (session == null) {
			return false;
		}
		boolean refreshVisible = false;
		long calcUpdateInterval = session.getRemoteProfile().calcUpdateInterval();
		if (calcUpdateInterval >= 45 || calcUpdateInterval == 0) {
			refreshVisible = true;
		}
		return refreshVisible;
	}

	public static boolean isMagnetTorrent(Map mapTorrent) {
		if (mapTorrent == null) {
			return false;
		}
		int fileCount = MapUtils.getMapInt(mapTorrent,
				TransmissionVars.FIELD_TORRENT_FILE_COUNT, 0);
		if (fileCount != 0) {
			return false;
		}
		//long size = MapUtils.getMapLong(mapTorrent, "sizeWhenDone", 0); // 16384
		String torrentName = MapUtils.getMapString(mapTorrent,
				TransmissionVars.FIELD_TORRENT_NAME, " ");
		return torrentName.startsWith("Magnet download for "); //NON-NLS
	}

	public static boolean canStop(Map<?, ?> torrent, Session session) {
		boolean isMagnet = TorrentUtils.isMagnetTorrent(torrent);
		if (isMagnet) {
			return false;
		}
		if (session != null) {
			Long tagUID_Stopped = session.tag.getDownloadStateUID(Session_Tag.STATEID_STOPPED);
			List<?> listTagUIDs = MapUtils.getMapList(torrent,
					TransmissionVars.FIELD_TORRENT_TAG_UIDS, null);
			if (listTagUIDs != null && tagUID_Stopped != null) {
				return !listTagUIDs.contains(tagUID_Stopped);
			}
		}
		
		long errorStat = MapUtils.getMapLong(torrent,
				TransmissionVars.FIELD_TORRENT_ERROR, TransmissionVars.TR_STAT_OK);
		if (errorStat == TransmissionVars.TR_STAT_LOCAL_ERROR) {
			return true;
		}
		int status = MapUtils.getMapInt(torrent,
				TransmissionVars.FIELD_TORRENT_STATUS,
				TransmissionVars.TR_STATUS_STOPPED);
		boolean stopped = status == TransmissionVars.TR_STATUS_STOPPED;
		return !stopped;
	}

	public static boolean canStart(Map<?, ?> torrent, Session session) {
		boolean isMagnet = TorrentUtils.isMagnetTorrent(torrent);
		if (isMagnet) {
			return false;
		}

		if (session != null) {
			Long tagUID_Stopped = session.tag.getDownloadStateUID(Session_Tag.STATEID_STOPPED);
			List<?> listTagUIDs = MapUtils.getMapList(torrent,
					TransmissionVars.FIELD_TORRENT_TAG_UIDS, null);
			if (listTagUIDs != null && tagUID_Stopped != null) {
				return listTagUIDs.contains(tagUID_Stopped);
			}
		}

		// Transmission doesn't have stopped tag, use FIELD_TORRENT_STATUS
		int status = MapUtils.getMapInt(torrent,
				TransmissionVars.FIELD_TORRENT_STATUS,
				TransmissionVars.TR_STATUS_STOPPED);
		return status == TransmissionVars.TR_STATUS_STOPPED;
	}
	
	public static long getTorrentID(@NonNull Activity activity) {
		Intent intent = activity.getIntent();

		final Bundle extras = intent.getExtras();
		if (extras == null || !extras.containsKey(Session_Torrent.EXTRA_TORRENT_ID)) {
			Log.e(activity.getClass().getSimpleName(), "No extras!");
			return -1;
		}
		
		return extras.getLong(Session_Torrent.EXTRA_TORRENT_ID);
	}
}
