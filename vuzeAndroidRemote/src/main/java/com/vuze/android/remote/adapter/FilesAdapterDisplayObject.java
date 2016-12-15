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

import java.util.Map;

import com.vuze.android.remote.session.Session;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public abstract class FilesAdapterDisplayObject
	implements Comparable<FilesAdapterDisplayObject>
{
	protected static final String KEY_IS_FOLDER = "isFolder";

	public final int level;

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

	public abstract Map<?, ?> getMap(@Nullable Session session,
			long torrentID);

	public int compareTo(@NonNull FilesAdapterDisplayObject another) {
		int i = path.compareTo(another.path);
		if (i == 0) {
			i = name.compareTo(another.name);
		}
		return i;
	}

}