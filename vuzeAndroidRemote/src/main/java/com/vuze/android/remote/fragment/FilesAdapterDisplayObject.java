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

import java.util.Map;

import android.support.annotation.NonNull;

import com.vuze.android.remote.SessionInfo;

public abstract class FilesAdapterDisplayObject
	implements Comparable<FilesAdapterDisplayObject>
{
	public int level;

	public final FilesAdapterDisplayFolder parent;

	public final String name;

	public final String path;

	public FilesAdapterDisplayObject(int level, FilesAdapterDisplayFolder parent,
			@NonNull String path, @NonNull String name) {
		this.level = level;
		this.parent = parent;
		this.path = path;
		this.name = name;
	}

	public abstract Map<?, ?> getMap(SessionInfo sessionInfo, long torrentID);

	public int compareTo(FilesAdapterDisplayObject another) {
		int i = path.compareTo(another.path);
		if (i == 0) {
			i = name.compareTo(another.name);
		}
		return i;
	}

}