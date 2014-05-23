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


package com.vuze.android.remote.fragment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aelitis.azureus.util.MapUtils;
import com.vuze.android.remote.SessionInfo;

public class FilesAdapterDisplayFile
	extends FilesAdapterDisplayObject
{
	int fileIndex;

	@SuppressWarnings({
		"unchecked",
		"rawtypes"
	})
	public FilesAdapterDisplayFile(int fileIndex, int level,
			FilesAdapterDisplayFolder parent, Map mapFile, String path, String name) {
		super(level, parent, path, name);
		this.fileIndex = fileIndex;
		mapFile.put("isFolder", false);
	}

	public Map<?, ?> getMap(SessionInfo sessionInfo, long torrentID) {
		if (sessionInfo == null) {
			return new HashMap<>();
		}
		Map<?, ?> mapTorrent = sessionInfo.getTorrent(torrentID);

		List<?> listFiles = MapUtils.getMapList(mapTorrent, "files", null);

		if (listFiles == null || fileIndex >= listFiles.size()) {
			return new HashMap<>();
		}
		Map<?, ?> map = (Map<?, ?>) listFiles.get(fileIndex);
		return map;
	}
}