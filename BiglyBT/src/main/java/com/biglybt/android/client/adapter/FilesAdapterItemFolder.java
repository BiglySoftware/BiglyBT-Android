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

import java.util.*;

import com.biglybt.android.client.TransmissionVars;
import com.biglybt.android.client.session.Session;

import androidx.annotation.Nullable;

public class FilesAdapterItemFolder
	extends FilesAdapterItem
{
	private static final String KEY_NAME = "name";

	final Map<String, Object> map = new HashMap<>(2);

	public boolean expand = true;

	public int numFilesWanted;

	public int numFilesFilteredWanted;

	private List<Integer> fileIndexes = new ArrayList<>();

	private List<Integer> filteredFileIndexes = new ArrayList<>();

	public long size;

	public long sizeWanted;

	public long sizeWantedFiltered;

	public final String folder;

	FilesAdapterItemFolder(String folder, @Nullable FilesAdapterItemFolder parent,
			String path, String name) {
		super(parent, path, name);
		this.folder = folder;
		map.put(KEY_NAME, folder);

		map.put(TransmissionVars.FIELD_FILES_INDEX, -1);
	}

	@Override
	public Map<?, ?> getMap(Session session, long torrentID) {
		return map;
	}

	public boolean parentsExpanded() {
		return parent == null || parent.expand && parent.parentsExpanded();
	}

	void summarizeFile(int index, long length, boolean wanted, boolean allowed) {
		size += length;
		fileIndexes.add(index);
		if (allowed) {
			filteredFileIndexes.add(index);
		}
		if (wanted) {
			sizeWanted += length;
			numFilesWanted++;
			if (allowed) {
				numFilesFilteredWanted++;
				sizeWantedFiltered += length;
			}
		}
		if (parent != null) {
			parent.summarizeFile(index, length, wanted, allowed);
		}
	}

	public int getNumFiles() {
		return fileIndexes.size();
	}

	public int[] getFileIndexes() {
		int[] indexesArray = new int[fileIndexes.size()];
		for (int i = 0; i < fileIndexes.size(); i++) {
			indexesArray[i] = fileIndexes.get(i);
		}
		return indexesArray;
	}

	public int getNumFilteredFiles() {
		return filteredFileIndexes.size();
	}

	public int[] getFilteredFileIndexes() {
		int[] indexesArray = new int[filteredFileIndexes.size()];
		for (int i = 0; i < filteredFileIndexes.size(); i++) {
			indexesArray[i] = filteredFileIndexes.get(i);
		}
		return indexesArray;
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		return (obj instanceof FilesAdapterItemFolder)
				&& folder.equals(((FilesAdapterItemFolder) obj).folder);
	}

	@Override
	public String toString() {
		return super.toString() + path + name;
	}
}