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
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.BiglyBTApp;
import com.biglybt.android.util.PaulBurkeFileUtils;
import com.biglybt.core.diskmanager.file.impl.FMFileAccess.FileAccessor;
import com.biglybt.core.diskmanager.file.impl.FMFileAccessController.FileAccessorRAF;
import com.biglybt.core.util.FileHandler;
import com.biglybt.core.util.FileUtil;

import java.io.*;
import java.lang.ref.WeakReference;
import java.util.*;

@Keep
public class AndroidFileHandler
	extends FileHandler
{
	public static final String TAG = "AndroidFile: Handler";

	private static final int ANDROID_MAX_FILENAME_BYTES = 255;

	/**
	 * Try to find subpath using findfile, which might result in a different path 
	 * than the string appended one.
	 * <br/>
	 * Example:<br/>
	 * <pre>
	 *   parent     = content://com.android.providers.downloads.documents/tree/downloads/document/downloads
	 *   subdirs = [ "test" ]
	 *   append path= content://com.android.providers.downloads.documents/tree/downloads/document/downloads%2Ftest"
	 *   real path  = content://com.android.providers.downloads.documents/tree/downloads/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2Ftest
	 * </pre>
	 * Note: findFile calls list() which can be incredibly slow on large folders
	 * or if caller is iterating through every file
	 */
	private static final boolean USE_FINDFILE_FOR_SUBPATHS = false;

	private static final boolean USE_CACHE = true;

	private static final long CLEANUP_THRESHOLD_MS = 1000L * 30;

	private static final Map<String, WeakReference<AndroidFile>> cache = new HashMap<>();

	private static long LAST_CLEANUP = 0;

	private static AndroidFile getCache(String path) {
		if (!USE_CACHE) {
			return null;
		}

		synchronized (cache) {
			WeakReference<AndroidFile> reference = cache.get(path);
			if (reference == null) {
				return null;
			}
			AndroidFile file = reference.get();
			if (file == null) {
				if (SystemClock.uptimeMillis() - LAST_CLEANUP > CLEANUP_THRESHOLD_MS) {
					int numRemoved = 0;
					String key = null;

					for (Iterator<String> iter = cache.keySet().iterator(); iter.hasNext();) {
						key = iter.next();
						WeakReference<AndroidFile> cleanupRef = cache.get(key);
						if (cleanupRef.get() == null) {
							iter.remove();
							numRemoved++;
						}
					}

					if (AndroidUtils.DEBUG) {
						Log.d(TAG,
								"getCache: cleaned up " + numRemoved + " (last: " + key + ")");
					}
					LAST_CLEANUP = SystemClock.uptimeMillis();
				} else {
					cache.remove(path);
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "getCache: cleaned up " + path);
					}
				}
			}
			return file;
		}
	}

	@RequiresApi(api = VERSION_CODES.LOLLIPOP)
	private static AndroidFile putCache(String key, AndroidFile file) {
		if (!USE_CACHE) {
			return file;
		}
		WeakReference<AndroidFile> old = cache.put(key, new WeakReference<>(file));
		if (!key.equals(file.path)) {
			cache.put(file.path, new WeakReference<>(file));
		}
		return old == null ? null : old.get();
	}

	@RequiresApi(api = VERSION_CODES.LOLLIPOP)
	static AndroidFile newFile(@NonNull DocumentFile documentFile) {
		Uri uri = documentFile.getUri();
		String path = uri.toString();

		AndroidFile file = getCache(path);
		if (file == null) {
			file = new AndroidFile(documentFile, uri, path);
			putCache(path, file);
		}
		return file;
	}

	@Override
	public File newFile(File parent, String... subDirs) {
		fixupFileName(subDirs);

		if (!(parent instanceof AndroidFile)
				|| VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) {
			return super.newFile(parent, subDirs);
		}
		if (subDirs == null || subDirs.length == 0) {
			((AndroidFile) parent).log("newFile(F)");
			return parent;
		}

		return newFile((AndroidFile) parent, subDirs);
	}

	@RequiresApi(api = VERSION_CODES.LOLLIPOP)
	private static File newFile(@NonNull AndroidFile parent,
			@NonNull String... subDirs) {
		fixupFileName(subDirs);

		AndroidFile file = parent;
		file.log("newFile(AF),subdirs=" + Arrays.toString(subDirs));
		for (String subDir : subDirs) {
			String[] moreSubDirs = subDir.split("/", 0);
			for (String subDir2 : moreSubDirs) {
				file = getAndroidFile(file, subDir2);
			}
		}
		return file;
	}

	@RequiresApi(api = VERSION_CODES.LOLLIPOP)
	@NonNull
	private static AndroidFile getAndroidFile(@NonNull AndroidFile file,
			String subDir) {
		// Best guess what path will be by String manipulation
		String path;
		try {
			// getCanonicalPath will convert special directories ("Downloads")
			// as well as ensuring path isDocumentUri (contains '/document/')
			path = file.getCanonicalPath();
		} catch (IOException e) {
			path = file.path;
		}
		String subpath = path + "%2F" + Uri.encode(subDir);

		AndroidFile subFile = getCache(subpath);
		if (subFile == null) {
			DocumentFile dfSubDir = USE_FINDFILE_FOR_SUBPATHS
					? file.getDocFile().findFile(subDir) : null;
			if (dfSubDir != null) {
				Uri subDirUri = dfSubDir.getUri();
				String subDirPath = subDirUri.toString();
				subFile = new AndroidFile(dfSubDir, subDirUri, subDirPath);
				if (!subDirPath.equals(subpath)) {
					// cache real path and String appended one
					putCache(subDirPath, subFile);
				}
				subFile.log("foundViaFind");
			} else {
				subFile = new AndroidFile(subpath);
			}
			putCache(subpath, subFile);
		}
		if (subDir.indexOf('/') < 0) {
			subFile.parentFile = file;
		}
		return subFile;
	}

	private static String fixupFileName(String name) {
		int numBytes = name.getBytes().length;
		if (numBytes <= ANDROID_MAX_FILENAME_BYTES) {
			return name;
		}

		String extension = FileUtil.getExtension(name);
		int len = name.length();

		int truncateAt;
		String hash = Integer.toHexString(name.hashCode()).toUpperCase();

		if (len == numBytes) {
			truncateAt = ANDROID_MAX_FILENAME_BYTES - extension.length()
					- hash.length() - 1;
		} else {
			// manually walk back & count, so we don't break multi-byte chars
			truncateAt = Math.min(len, ANDROID_MAX_FILENAME_BYTES);
			int extraBytes = extension.getBytes().length + hash.length() + 1;
			while (truncateAt > 1 && name.substring(0, truncateAt).getBytes().length
					+ extraBytes > ANDROID_MAX_FILENAME_BYTES) {
				truncateAt--;
			}
		}

		String shortName = name.substring(0, truncateAt) + " " + hash + extension;
		if (AndroidUtils.DEBUG) {
			Log.d(TAG,
					"name too long (" + len + "/" + numBytes + "b), shrunk to "
							+ shortName.length() + "/" + shortName.getBytes().length + "; "
							+ name + " via " + AndroidUtils.getCompressedStackTrace(1, 12));
			if (name.contains("/")) {
				Log.e(TAG, "fixupFileName: truncated name with / "
						+ AndroidUtils.getCompressedStackTrace());
			}
		}

		return shortName;
	}

	@Override
	public File newFile(String parent, String... subDirs) {
		fixupFileName(subDirs);

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
			file = getCache(parent);
			if (file == null) {
				file = new AndroidFile(parent);
				putCache(parent, file);
			}
		}

		if (subDirs == null || subDirs.length == 0) {
			file.log("newFile(S)");
			return file;
		}

		return newFile(file, subDirs);
	}

	private static void fixupFileName(String[] subDirs) {
		if (subDirs == null) {
			return;
		}

		for (int i = 0, subDirsLength = subDirs.length; i < subDirsLength; i++) {
			// Note: subDir might be "200charfoo/200charbar" and we may truncate it
			subDirs[i] = fixupFileName(subDirs[i]);
		}
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
		if (!androidFile.getDocFile().exists()) {
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
				androidFile.getUri(), append ? "wa" : "w");
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
				((AndroidFile) from_file).getUri());
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
		// TODO: Currently only used by BackupManagerImpl, but if anything else
		//       decides to use it, it probably always will return false
		return super.containsPathSegment(f, path, caseSensitive);
	}

	@Override
	public String getRelativePath(File parentDir, File file) {
		// TODO: This is a copy of super.getRelativePath, with %2F instead of File.separator
		//       Surely there's better code
		String parentPath = getCanonicalPathSafe(parentDir);

		if (parentDir instanceof AndroidFile) {
			if (!parentPath.endsWith("%2F")) {

				parentPath += "%2F";
			}
		} else {
			if (!parentPath.endsWith("/")) {

				parentPath += "/";
			}
		}

		String file_path = getCanonicalPathSafe(file);

		if (file_path.startsWith(parentPath)) {
			// need to replace %2F with / to allow caller to do something like:
			// FileUtil.newFile(somePath, getRelativePath(someParent, someChild);
			String relPath = file_path.substring(parentPath.length());
			if (file instanceof AndroidFile) {
				relPath = Uri.decode(relPath);
			}
			return relPath;
		}

		return FileUtil.areFilePathsIdentical(parentDir, file) ? "" : null;
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
		if (!(file instanceof AndroidFile)) {
			return new FileAccessorRAF(file, access_mode);
		}
		return new AndroidFileAccessor(file, access_mode);
	}
}
