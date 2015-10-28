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

import java.util.List;
import java.util.Map;

import com.vuze.util.MapUtils;

public class TorrentUtils
{

	public static String getSaveLocation(Map<?, ?> mapTorrent) {
		String saveLocation = MapUtils.getMapString(mapTorrent,
				TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR, "dunno");

		// if simple torrent, download dir might have file name attached
		List<?> listFiles = MapUtils.getMapList(mapTorrent, "files", null);
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
				saveLocation = saveLocation.substring(0, saveLocation.length()
						- firstFileName.length());
			}
		}

		return saveLocation;
	}
}
