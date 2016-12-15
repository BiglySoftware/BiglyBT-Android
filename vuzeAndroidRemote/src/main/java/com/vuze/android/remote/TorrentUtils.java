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
 */

package com.vuze.android.remote;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.vuze.android.remote.session.Session;
import com.vuze.android.remote.session.SessionSettings;
import com.vuze.util.MapUtils;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class TorrentUtils
{

	private static SortByFields[] sortByFields;

	@NonNull
	public static String getSaveLocation(Session session,
			Map<?, ?> mapTorrent) {
		String saveLocation = MapUtils.getMapString(mapTorrent,
				TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR, null);

		if (saveLocation == null) {
			if (session == null) {
				saveLocation = "dunno";
			} else {
				SessionSettings sessionSettings = session.getSessionSettings();
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
				int posSlash = AndroidUtils.lastindexOfAny(saveLocation, "\\/", -1);
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

		return saveLocation == null ? "" : saveLocation;
	}

	public static int findSordIdFromTorrentFields(String[] fields,
			SortByFields[] sortByFields) {
		for (int i = 0; i < sortByFields.length; i++) {
			if (Arrays.equals(sortByFields[i].sortFieldIDs, fields)) {
				return i;
			}
		}
		return -1;
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
		//long size = MapUtils.getMapLong(mapTorrent, "sizeWhenDone", 0); // 16384
		String torrentName = MapUtils.getMapString(mapTorrent, "name", " ");
		return torrentName.startsWith("Magnet download for ") && fileCount == 0;
	}
}
