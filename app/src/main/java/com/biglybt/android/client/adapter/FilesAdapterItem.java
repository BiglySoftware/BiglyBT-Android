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

import java.util.Map;

import com.biglybt.android.client.session.Session;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class FilesAdapterItem
{
	public final int level;

	public final FilesAdapterItemFolder parent;

	@NonNull
	public final String name;

	/** Path excluding name **/
	@NonNull
	public final String path;

	FilesAdapterItem(FilesAdapterItemFolder parent, @NonNull String path,
			@NonNull String name) {
		this.level = parent == null ? 0 : parent.level + 1;
		this.parent = parent;
		this.path = path;
		this.name = name;
	}

	public abstract Map<?, ?> getMap(@Nullable Session session, long torrentID);

	@Override
	public boolean equals(@Nullable Object obj) {
		return (obj instanceof FilesAdapterItem)
				&& path.equals(((FilesAdapterItem) obj).path)
				&& name.equals(((FilesAdapterItem) obj).name);
	}

}