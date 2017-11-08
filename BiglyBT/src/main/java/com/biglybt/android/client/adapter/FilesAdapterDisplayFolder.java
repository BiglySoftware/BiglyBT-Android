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

import java.util.HashMap;
import java.util.Map;

import com.biglybt.android.client.TransmissionVars;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.util.MapUtils;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class FilesAdapterDisplayFolder
	extends FilesAdapterDisplayObject
{
	private static final String KEY_NAME = "name";

	final Map<String, Object> map = new HashMap<>(2);

	public boolean expand = true;

	public int numFiles;

	public int numFilesWanted;

	public long size;

	public long sizeWanted;

	public final String folder;

	public FilesAdapterDisplayFolder(String folder, int level,
			@Nullable FilesAdapterDisplayFolder parent, String path, String name) {
		super(level, parent, path, name);
		this.folder = folder;
		map.put(KEY_NAME, folder);

		map.put(FilesAdapterDisplayFile.KEY_IS_FOLDER, true);
		map.put(TransmissionVars.FIELD_FILES_INDEX, -1);
	}

	@Override
	public Map<?, ?> getMap(Session session, long torrentID) {
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
	public int compareTo(@NonNull FilesAdapterDisplayObject another) {
		if (!(another instanceof FilesAdapterDisplayFolder)) {
			return super.compareTo(another);
		}
		return folder.compareTo(((FilesAdapterDisplayFolder) another).folder);
	}

}