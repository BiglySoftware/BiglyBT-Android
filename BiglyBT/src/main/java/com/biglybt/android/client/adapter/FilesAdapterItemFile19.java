/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.android.client.adapter;

import java.util.Map;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Created by TuxPaper on 8/7/18.
 */
public class FilesAdapterItemFile19
	extends FilesAdapterItemFile
{
	FilesAdapterItemFile19(int fileIndex, @Nullable FilesAdapterItemFolder parent,
			String path, String name, boolean want, Map<String, Object> mapFile) {
		super(fileIndex, parent, path, name, want, mapFile);
	}

	@Override
	public int compareTo(@NonNull FilesAdapterItem another) {
		if (!(another instanceof FilesAdapterItemFile)) {
			return super.compareTo(another);
		}
		return Integer.compare(fileIndex,
				((FilesAdapterItemFile) another).fileIndex);
	}
}
