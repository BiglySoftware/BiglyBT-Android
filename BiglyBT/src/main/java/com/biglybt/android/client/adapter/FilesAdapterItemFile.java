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

package com.biglybt.android.client.adapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.biglybt.android.client.TransmissionVars;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.util.MapUtils;

import java.util.List;
import java.util.Map;

public class FilesAdapterItemFile
	extends FilesAdapterItem
{
	final int fileIndex;

	boolean want;

	final int priority;

	final long bytesComplete;

	final long length;

	@SuppressWarnings({
		"unchecked",
		"rawtypes"
	})
	FilesAdapterItemFile(int fileIndex, @Nullable FilesAdapterItemFolder parent,
			String path, String name, boolean want, Map<String, Object> mapFile) {
		super(parent, path, name);
		this.fileIndex = fileIndex;
		this.want = want;
		priority = MapUtils.getMapInt(mapFile,
				TransmissionVars.FIELD_TORRENT_FILES_PRIORITY,
				TransmissionVars.TR_PRI_NORMAL);
		bytesComplete = MapUtils.getMapLong(mapFile,
				TransmissionVars.FIELD_FILESTATS_BYTES_COMPLETED, 0);
		length = MapUtils.getMapLong(mapFile, TransmissionVars.FIELD_FILES_LENGTH,
				-1);
	}

	@Override
	@Nullable
	public Map<String, Object> getMap(Session session, long torrentID) {
		if (session == null) {
			return null;
		}
		Map<String, Object> mapTorrent = session.torrent.getCachedTorrent(
				torrentID);

		List<?> listFiles = MapUtils.getMapList(mapTorrent,
				TransmissionVars.FIELD_TORRENT_FILES, null);

		if (listFiles == null || fileIndex >= listFiles.size()) {
			return null;
		}
		//noinspection unchecked
		return (Map<String, Object>) listFiles.get(fileIndex);
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		return (obj instanceof FilesAdapterItemFile)
				&& fileIndex == ((FilesAdapterItemFile) obj).fileIndex;
	}

	@NonNull
	@Override
	public String toString() {
		return super.toString() + path + name;
	}
}