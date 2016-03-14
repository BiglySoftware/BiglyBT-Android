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

import java.util.HashMap;
import java.util.Map;

import com.vuze.android.remote.SessionInfo;
import com.vuze.android.remote.TransmissionVars;
import com.vuze.util.MapUtils;

public class FilesAdapterDisplayFolder
	extends FilesAdapterDisplayObject
{
	Map<String, Object> map = new HashMap<>(2);

	public boolean expand = true;

	public int numFiles;

	public int numFilesWanted;

	public long size;

	public long sizeWanted;

	public String folder;

	public FilesAdapterDisplayFolder(String folder, int level,
			FilesAdapterDisplayFolder parent, String path, String name) {
		super(level, parent, path, name);
		this.folder = folder;
		map.put("name", folder);

		map.put("isFolder", true);
		map.put(TransmissionVars.FIELD_FILES_INDEX, -1);
	}

	@Override
	public Map<?, ?> getMap(SessionInfo sessionInfo, long torrentID) {
		return map;
	}

	public boolean parentsExpanded() {
		return parent == null || parent.expand && parent.parentsExpanded();
	}

	public void clearSummary() {
		numFiles = numFilesWanted = 0;
		size = sizeWanted = 0;
	}

	public void summarize(Map<?, ?> mapFile) {
		long length = MapUtils.getMapLong(mapFile,
				TransmissionVars.FIELD_FILES_LENGTH, 0);
		boolean wanted = MapUtils.getMapBoolean(mapFile,
				TransmissionVars.FIELD_FILESTATS_WANTED, true);
		summarize(length, wanted);
	}

	private void summarize(long length, boolean wanted) {
		size += length;
		numFiles++;
		if (wanted) {
			sizeWanted += length;
			numFilesWanted++;
		}
		if (parent != null) {
			parent.summarize(length, wanted);
		}
	}

	@Override
	public int compareTo(FilesAdapterDisplayObject another) {
		if (!(another instanceof FilesAdapterDisplayFolder)) {
			return super.compareTo(another);
		}
		return folder.compareTo(((FilesAdapterDisplayFolder) another).folder);
	}

}