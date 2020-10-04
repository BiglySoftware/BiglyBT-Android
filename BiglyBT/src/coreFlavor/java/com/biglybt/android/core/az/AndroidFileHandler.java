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
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;

import com.biglybt.android.client.BiglyBTApp;
import com.biglybt.android.util.PaulBurkeFileUtils;
import com.biglybt.core.diskmanager.file.impl.FMFileAccess.FileAccessor;
import com.biglybt.core.util.FileHandler;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;

@Keep
public class AndroidFileHandler
	extends FileHandler
{

	private static final Map<String, AndroidFile> cache = new LinkedHashMap<String, AndroidFile>(
			64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, AndroidFile> eldest) {
			return size() > 1024;
		}
	};

	public static final String TAG = "AndroidFileHandler";

	@RequiresApi(api = VERSION_CODES.LOLLIPOP)
	static AndroidFile newFile(@NonNull DocumentFile documentFile) {
		Uri uri = documentFile.getUri();
		String path = uri.toString();
		AndroidFile file = cache.get(path);
		if (file == null) {
			file = new AndroidFile(documentFile, uri, path);
			cache.put(path, file);
		} else {
			//file.log("UsedCache (DocFile)");
		}
		return file;
	}

	@Override
	public File newFile(File parent, String... subDirs) {
		if (!(parent instanceof AndroidFile)
				|| VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) {
			return super.newFile(parent, subDirs);
		}
		if (subDirs == null || subDirs.length == 0) {
			return parent;
		}

		return newFile((AndroidFile) parent, subDirs);
	}

	@RequiresApi(api = VERSION_CODES.LOLLIPOP)
	private static File newFile(@NonNull AndroidFile parent,
			@NonNull String... subDirs) {

		AndroidFile file = parent;
		for (String subDir : subDirs) {
			String subpath = file.path + "%2F" + Uri.encode(subDir);
			AndroidFile subFile = cache.get(subpath);
			if (subFile == null) {
				subFile = new AndroidFile(subpath);
				cache.put(subpath, subFile);
			} else {
				subFile.log("UsedCache");
			}
			if (subDir.indexOf('/') < 0) {
				subFile.parentFile = file;
			}
			file = subFile;
		}
		return file;
	}

	@Override
	public File newFile(String parent, String... subDirs) {
		boolean isContentURI = parent != null && parent.startsWith("content://");
		if (Build.VERSION.SDK_INT < VERSION_CODES.LOLLIPOP || parent == null
				|| !isContentURI) {
			if (Build.VERSION.SDK_INT < VERSION_CODES.LOLLIPOP && isContentURI) {
				return super.newFile(PaulBurkeFileUtils.getPath(BiglyBTApp.getContext(),
						Uri.parse(parent), false), subDirs);
			}
			return super.newFile(parent, subDirs);
		}

		AndroidFile file;
		synchronized (cache) {
			file = cache.get(parent);
			if (file == null) {
				DocumentFile docFile = DocumentFile.fromTreeUri(BiglyBTApp.getContext(),
						Uri.parse(AndroidFile.fixDirName(parent)));
				// make it a document uri (adds /document/* to path)
				Uri uri = docFile.getUri();
				String path = uri.toString();

				file = cache.get(path);

				if (file == null) {
					file = new AndroidFile(docFile, uri, path);
					cache.put(path, file);
				} else {
					file.log("UsedCache2");
				}
			} else {
				file.log("UsedCache");
			}
		}
		if (subDirs == null || subDirs.length == 0) {
			return file;
		}

		return newFile(file, subDirs);
	}

	/**
	 * TODO: Check core for any <code>new [a-z]*OutputStream\(</code>File) and use FileUtil.newOutputStream
	 */
	@Override
	public FileOutputStream newFileOutputStream(File file, boolean append)
			throws FileNotFoundException {
		if (!(file instanceof AndroidFile)
				|| VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) {
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
	public FileInputStream newFileInputStream(File from_file)
			throws FileNotFoundException {
		if (!(from_file instanceof AndroidFile)
				|| VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) {
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
	public boolean containsPathSegment(@NonNull File f, String path,
			boolean caseSensitive) {
		// TODO
		return super.containsPathSegment(f, path, caseSensitive);
	}

	@Override
	public String getRelativePath(File parentDir, File file) {
		// TODO
		return super.getRelativePath(parentDir, file);
	}

	@Override
	public File getCanonicalFileSafe(File file) {
		if (file instanceof AndroidFile) {
			return file;
		}

		return super.getCanonicalFileSafe(file);
	}

	@NonNull
	@Override
	public String getCanonicalPathSafe(File file) {
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
	public boolean isAncestorOf(File _parent, File _child) {
		if (_parent instanceof AndroidFile && _child instanceof AndroidFile) {
			boolean isAncenstor = getRelativePath(_parent, _child) != null;
			if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
				((AndroidFile) _child).log(
						"isAncestor=" + isAncenstor + " of " + _parent);
			}
			return isAncenstor;
		}

		return super.isAncestorOf(_parent, _child);
	}

	@RequiresApi(api = VERSION_CODES.LOLLIPOP)
	@Override
	public FileAccessor newFileAccessor(File file, String access_mode)
			throws FileNotFoundException {
		return new AndroidFileAccessor(file, access_mode);
	}
}
