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

package com.biglybt.android.core.az;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.biglybt.android.client.BiglyBTApp;
import com.biglybt.android.util.PaulBurkeFileUtils;
import com.biglybt.core.diskmanager.file.impl.FMFileAccess.FileAccessor;
import com.biglybt.core.util.FileHandler;

import java.io.*;

@Keep
public class AndroidFileHandler
	extends FileHandler
{

	@Override
	public File newFile(File parent, String... subDirs) {
		if (!(parent instanceof AndroidFile)) {
			return super.newFile(parent, subDirs);
		}
		if (subDirs == null || subDirs.length == 0) {
			return parent;
		}

		AndroidFile file = new AndroidFile((AndroidFile) parent, subDirs[0]);
		for (int i = 1, subDirsLength = subDirs.length; i < subDirsLength; i++) {
			file = new AndroidFile(file, subDirs[i]);
		}
		return file;
	}

	@Override
	public File newFile(String parent, String... subDirs) {
		boolean isContentURI = parent.startsWith("content://");
		if (Build.VERSION.SDK_INT < 21 || parent == null || !isContentURI) {
			if (Build.VERSION.SDK_INT < 21 && isContentURI) {
				return super.newFile(PaulBurkeFileUtils.getPath(BiglyBTApp.getContext(),
						Uri.parse(parent), false), subDirs);
			}
			return super.newFile(parent, subDirs);
		}
		AndroidFile file = new AndroidFile(parent);
		if (subDirs == null || subDirs.length == 0) {
			return file;
		}

		file = new AndroidFile(file, subDirs[0]);
		for (int i = 1, subDirsLength = subDirs.length; i < subDirsLength; i++) {
			file = new AndroidFile(file, subDirs[i]);
		}
		return file;
	}

	/**
	 * TODO: Check core for any <code>new [a-z]*OutputStream\(</code>File) and use FileUtil.newOutputStream
	 */
	@Override
	public FileOutputStream newFileOutputStream(@NonNull File file,
			boolean append)
			throws FileNotFoundException {
		if (!(file instanceof AndroidFile)) {
			return super.newFileOutputStream(file, append);
		}
		AndroidFile androidFile = (AndroidFile) file;
		androidFile.log("newFOS");
		Context context = BiglyBTApp.getContext();
		ContentResolver contentResolver = context.getContentResolver();

		// Same as
		// return contentResolver.openOutputStream(uri);
		// Except returns FileOutputStream
		if (!androidFile.docFile.exists()) {
			try {
				androidFile.createNewFile();
			} catch (IOException e) {
				FileNotFoundException ex = new FileNotFoundException(
						"Unable to create new file");
				ex.initCause(e);
				throw ex;
			}
		}

		AssetFileDescriptor fd = contentResolver.openAssetFileDescriptor(
				androidFile.uri, append ? "wa" : "w");
		if (fd == null) {
			return null;
		}
		try {
			return fd.createOutputStream();
		} catch (IOException e) {
			throw new FileNotFoundException("Unable to create stream");
		}
	}

	/**
	 * TODO: Check core for any "new [A-z]*(Input|Output)Stream\(.*\)
	 */
	@Override
	public FileInputStream newFileInputStream(@NonNull File from_file)
			throws FileNotFoundException {
		if (!(from_file instanceof AndroidFile)) {
			return super.newFileInputStream(from_file);
		}

		((AndroidFile) from_file).log("newFileInputStream");
		ContentResolver contentResolver = BiglyBTApp.getContext().getContentResolver();
//		if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
//			contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
//		}
		InputStream inputStream = contentResolver.openInputStream(
				((AndroidFile) from_file).uri);
		if (inputStream instanceof FileInputStream) {
			return (FileInputStream) inputStream;
		}
		throw new FileNotFoundException("Unable to create stream, "
				+ (inputStream == null ? "null" : inputStream.getClass())
				+ " not FileInputStream");
	}

	@Override
	public boolean containsPathSegment(@NonNull File f, @NonNull String path,
			boolean caseSensitive) {
		// TODO
		return super.containsPathSegment(f, path, caseSensitive);
	}

	@Override
	public String getRelativePath(@NonNull File parentDir, @NonNull File file) {
		// TODO
		return super.getRelativePath(parentDir, file);
	}

	@Override
	public File getCanonicalFileSafe(@NonNull File file) {
		if (file instanceof AndroidFile) {
			return file.getAbsoluteFile();
		}

		return super.getCanonicalFileSafe(file);
	}

	@NonNull
	@Override
	public String getCanonicalPathSafe(@NonNull File file) {
		try {
			if (file instanceof AndroidFile) {
				return file.getCanonicalPath();
			}
		} catch (Throwable e) {

			return (file.getAbsolutePath());
		}

		return super.getCanonicalPathSafe(file);
	}

	@Override
	public boolean isAncestorOf(@NonNull File _parent, @NonNull File _child) {
		if (_parent instanceof AndroidFile && _child instanceof AndroidFile) {
			return getRelativePath(_parent, _child) != null;
		}

		return super.isAncestorOf(_parent, _child);
	}

	@Override
	public FileAccessor newFileAccessor(File file, String access_mode)
			throws FileNotFoundException {
		return new AndroidFileAccessor(file, access_mode);
	}
}
