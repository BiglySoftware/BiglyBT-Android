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

package com.vuze.android.remote.adapter;

import java.util.List;
import java.util.Map;

import com.vuze.android.remote.AndroidUtils;
import com.vuze.android.remote.session.Session;
import com.vuze.android.remote.TransmissionVars;
import com.vuze.util.MapUtils;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class FilesAdapterDisplayFile
	extends FilesAdapterDisplayObject
{
	final int fileIndex;

	@SuppressWarnings({
		"unchecked",
		"rawtypes"
	})
	public FilesAdapterDisplayFile(int fileIndex, int level,
			@Nullable FilesAdapterDisplayFolder parent, Map mapFile, String path, String name) {
		super(level, parent, path, name);
		this.fileIndex = fileIndex;
		mapFile.put(KEY_IS_FOLDER, false);
	}

	@Nullable
	public Map<?, ?> getMap(Session session, long torrentID) {
		if (session == null) {
			return null;
		}
		Map<?, ?> mapTorrent = session.torrent
			.getCachedTorrent(torrentID);

		List<?> listFiles = MapUtils.getMapList(mapTorrent,
				TransmissionVars.FIELD_TORRENT_FILES, null);

		if (listFiles == null || fileIndex >= listFiles.size()) {
			return null;
		}
		return (Map<?, ?>) listFiles.get(fileIndex);
	}

	@Override
	public int compareTo(@NonNull FilesAdapterDisplayObject another) {
		if (!(another instanceof FilesAdapterDisplayFile)) {
			return super.compareTo(another);
		}
		return AndroidUtils.integerCompare(fileIndex,
				((FilesAdapterDisplayFile) another).fileIndex);
	}
}