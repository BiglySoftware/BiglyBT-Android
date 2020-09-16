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

package com.biglybt.android.util;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.biglybt.android.core.az.AndroidFileHandler;
import com.biglybt.util.StringCompareUtils;

import java.io.File;

public class PathInfo
{
	@NonNull
	public final String fullPath;

	public final boolean isSAF;

	public String shortName;

	public boolean isRemovable;

	public File file;

	public String storagePath;

	public String storageVolumeName;

	public boolean isPrivateStorage;

	public boolean isReadOnly;

	public Uri uri;

	public long freeBytes;

	public PathInfo(@NonNull String fullPath) {
		this.fullPath = fullPath;
		isSAF = fullPath.startsWith("content://");
	}

	public CharSequence getFriendlyName() {
		// TODO: i18n
		if (shortName == null) {
			return fullPath;
		}
		CharSequence s = (storageVolumeName == null
				? ((isRemovable ? "External" : "Internal") + " Storage")
				: storageVolumeName)
				+ (shortName.length() == 0 ? "" : ", " + shortName);
		return s;
	}

	@WorkerThread
	public boolean exists() {
		if (file != null) {
			return file.exists();
		}
		if (uri != null) {
			return new AndroidFileHandler().newFile(fullPath).exists();
		}
		return false;
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (!(obj instanceof PathInfo)) {
			return false;
		}

		PathInfo other = (PathInfo) obj;
		if (file == null || other.file == null) {
			return StringCompareUtils.equals(fullPath, other.fullPath);
		}
		return file.equals(other.file);
	}
}
