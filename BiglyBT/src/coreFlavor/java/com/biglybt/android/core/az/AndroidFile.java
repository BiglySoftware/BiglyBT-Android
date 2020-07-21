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

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.util.Log;

import androidx.annotation.*;
import androidx.documentfile.provider.DocumentFile;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.BiglyBTApp;
import com.biglybt.android.util.FileUtils;
import com.biglybt.android.util.FileUtils.PathInfo;
import com.biglybt.util.AssumeNoSideEffects;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @implNote DocumentFile doesn't like parent stuff.
 * <p/>
 * Base content URI:<br/>
 * <code>"content://" + HANDLER_ID + "/tree/" + BASE_LOCATION</code>
 * <br/>
 * Base content URI is what you receive from picker and request permissions on.
 * Can't walk up the tree from base. Any files and folders created withing have a URI of<br/>
 * <code>"content://" + HANDLER_ID + "/tree/" + BASE_LOCATION + "/document/" + BASE_LOCATION + (optional) SUBLOCATION</code>
 * <br/>
 * where SUBLOCATION is something like "%2Fsubpath%2Fsomefile"
 */
@SuppressWarnings("MethodDoesntCallSuperMethod")
@Keep
public class AndroidFile
	extends File
{
	private static final String QUOTED_FILE_SEP = Pattern.quote(File.separator);

	private static long uniqueNumber = 0;

	private static final String TAG = "AndroidFile";

	private static final boolean DEBUG_DETAILED = true;

	@NonNull
	private final String path;

	@NonNull
	DocumentFile docFile;

	@NonNull
	Uri uri;

	private DocumentFile parentFile;

	private PathInfo pathInfo;

	AndroidFile(@NonNull String path) {
		super("" + uniqueNumber++);

		docFile = DocumentFile.fromTreeUri(BiglyBTApp.getContext(),
				Uri.parse(fixDirName(path)));
		// make it a document uri (adds /document/* to path)
		uri = docFile.getUri();
		this.path = uri.toString();

		log("new");
	}

	private static String fixSubDirName(@NonNull String name) {
		// Note: separator '/' will be converted to %2F and will "just work"
		return Uri.encode(name);
	}

	private static String fixDirName(@NonNull String dir) {
		// All strings will start with "content://", but caller might have blindly
		// appended a File.separator.  
		// ie.  FileUtil.newFile(someFile.toString + File.separator + "foo")
		// which would result in something like
		// content://provider.id/tree/5555-5555%3Afolder/foo
		// TODO: Need to at least check for this case and warn dev
		// slashes after "folder" ARE valid, such as folder/children, folder/document, etc
		// so we can't blindly say a slash after "tree/" is invalid
		int firstEncodedSlash = dir.indexOf("%2F");
		if (firstEncodedSlash > 0 && dir.indexOf('/', firstEncodedSlash + 1) > 0) {
			Log.e(TAG, "fixDirName] dir has File.separatorChar! " + dir + "; "
					+ AndroidUtils.getCompressedStackTrace());
		}
		return dir;
	}

	AndroidFile(AndroidFile dir, @NonNull String name) {
		// TODO:
		// Not sure if appending a name to a DocumentFile with only a "/tree/" section
		// works, or if we have to build a "/document/<tree path>" + "%2F" + name
		this(dir.getPath() + "%2F" + fixSubDirName(name));
		if (name.indexOf('/') < 0) {
			parentFile = dir.docFile;
		}
	}

//	private AndroidFile(String dirPath, String name) {
//		this(new AndroidFile(fixDirName(dirPath)), name);
//	}

	private AndroidFile(DocumentFile documentFile) {
		super("" + uniqueNumber++);
		uri = documentFile.getUri();
		path = uri.toString();
		docFile = documentFile;
		log("new from docFile");
	}

	@NonNull
	@Override
	public String getName() {
		// Not sure if getName returns extension
		String name = docFile.getName();
		if (name == null) {

			int slashPos = path.lastIndexOf("%2F", path.length() - 3);
			int colonPos = path.lastIndexOf("%3A", path.length() - 3);
			if (slashPos >= 0 || colonPos >= 0) {
				name = Uri.decode(path.substring(Math.max(slashPos, colonPos) + 3));
			} else {
				name = "";
			}
		}
		log("getname=" + name);
		return name;
	}

	private DocumentFile getParentDocumentFile() {
		if (parentFile != null) {
			return parentFile;
		}
		DocumentFile docFileParent = docFile.getParentFile();
		if (docFileParent != null) {
			parentFile = docFileParent;
			return parentFile;
		}
		// There might be a better way with DocumentContracts or Uri.getPathSegments
		Pattern pattern = Pattern.compile("^content://[^/]+/tree/([^/]+)/?(.*)$");
		Matcher matcher = pattern.matcher(path);
		if (!matcher.matches()) {
			// If it's not a tree, we probably don't have rights to parent.
			// I suppose we could check by replacing "/document" with "/tree" and
			// subtract the last path segment
			log("Can't get parent of " + path);
			return null;
		}
		String docPath = matcher.group(2);
		if (docPath.isEmpty()) {
			// Removing path segments from tree is dangerous -- ie. we probably
			// don't have access to parent unless it was specifically allowed
			String treePath = matcher.group(1);
			int i = treePath.lastIndexOf("%2F");
			if (i > 0) {
				parentFile = DocumentFile.fromTreeUri(BiglyBTApp.getContext(),
						Uri.parse(path.substring(0, matcher.start(1) + i)));
			} else {
				log("docPath empty, can't get parent of " + path);
			}
		} else {
			int i = docPath.lastIndexOf("%2F");
			if (i > 0) {
				parentFile = DocumentFile.fromTreeUri(BiglyBTApp.getContext(),
						Uri.parse(path.substring(0, matcher.start(2) + i)));
			} else {
				log("docPath has no %2F, can't get parent of " + path);
			}
		}
		return parentFile;
	}

	@Nullable
	@Override
	public String getParent() {
		DocumentFile parentFile = getParentDocumentFile();
		String parent = parentFile == null ? null : parentFile.getUri().toString();
		log("getParent=" + parent);
		return parent;
	}

	@Nullable
	@Override
	public File getParentFile() {
		DocumentFile parentFile = getParentDocumentFile();
		log("getParentFile="
				+ (parentFile == null ? null : parentFile.getUri().toString()));
		return parentFile == null ? null : new AndroidFile(parentFile);
	}

	@NonNull
	@Override
	public String getPath() {
		log("getPath");
		return path;
	}

	@Override
	public boolean isAbsolute() {
		log("isAbsolute");
		return true;
	}

	@NonNull
	@Override
	public String getAbsolutePath() {
		log("getAbsolutePath");
		return path;
	}

	@NonNull
	@Override
	public File getAbsoluteFile() {
		log("getAbsoluteFile");
		return this;
	}

	@NonNull
	@Override
	public String getCanonicalPath()
			throws IOException {
		log("getCanonicalPath");
		return path;
	}

	@NonNull
	@Override
	public File getCanonicalFile()
			throws IOException {
		log("getCanonicalFile");
		return this;
	}

	@NonNull
	@Override
	public URL toURL()
			throws MalformedURLException {
		log("toURL");
		return new URL(path);
	}

	@NonNull
	@Override
	public URI toURI() {
		log("toURI");
		return URI.create(path);
	}

	@Override
	public boolean canRead() {
		log("canRead");
		return docFile.canRead();
	}

	@Override
	public boolean canWrite() {
		log("canWrite");
		return docFile.canWrite();
	}

	@Override
	public boolean exists() {
		boolean exists = docFile.exists();
		log("exists=" + exists);
		return exists;
	}

	@Override
	public boolean isDirectory() {
		boolean directory = docFile.isDirectory();
		log("isDir=" + directory);
		return directory;
	}

	@Override
	public boolean isFile() {
		log("isFile");
		return docFile.isFile();
	}

	@Override
	public boolean isHidden() {
		log("isHidden");
		return false;
	}

	@Override
	public long lastModified() {
		long lastModified = docFile.lastModified();
		log("lastModified=" + lastModified);
		return lastModified;
	}

	@Override
	public long length() {
		long length = docFile.length();
		log("length=" + length);
		return length;
	}

	@Override
	public boolean createNewFile()
			throws IOException {
		log("createNewFile");
		if (exists()) {
			return false;
		}
		DocumentFile parentFile = getParentDocumentFile();
		if (parentFile == null) {
			parentFile = DocumentFile.fromTreeUri(BiglyBTApp.getContext(),
					Uri.parse(getParent()));
		}
		log("createNewFile in " + parentFile.getUri());
		DocumentFile file = parentFile.createFile("application/octet-stream",
				getName());
		if (file != null) {
			docFile = file;
			uri = docFile.getUri();
		}
		return file != null;
	}

	@Override
	public boolean delete() {
		log("delete");
		return docFile.delete();
	}

	@Override
	public void deleteOnExit() {
		log("deleteOnExit");

		// TODO
		//super.deleteOnExit();
	}

	@Nullable
	@Override
	public String[] list() {
		log("list");
		if (!docFile.canRead()) {
			Log.w(TAG, "list: can't read " + path);
			return new String[0];
		}
		DocumentFile[] files = docFile.listFiles();
		String[] fileStrings = new String[files.length];
		for (int i = 0, filesLength = files.length; i < filesLength; i++) {
			fileStrings[i] = files[i].getUri().toString();
		}

		return fileStrings;
	}

	@Nullable
	@Override
	public String[] list(@Nullable FilenameFilter filter) {
		log("list");

		if (filter == null) {
			return list();
		}
		DocumentFile[] files = docFile.listFiles();
		List<String> list = new ArrayList<>();
		for (DocumentFile file : files) {
			AndroidFile f = new AndroidFile(file);
			if (filter.accept(f, file.getName())) {
				list.add(f.path);
			}
		}

		return list.toArray(new String[0]);
	}

	@Nullable
	@Override
	public File[] listFiles() {
		log("listFiles");

		DocumentFile[] files = docFile.listFiles();
		File[] javaFiles = new File[files.length];
		for (int i = 0, filesLength = files.length; i < filesLength; i++) {
			javaFiles[i] = new AndroidFile(files[i]);
		}

		return javaFiles;
	}

	@Nullable
	@Override
	public File[] listFiles(@Nullable FilenameFilter filter) {
		log("listFiles");

		if (filter == null) {
			return listFiles();
		}
		DocumentFile[] files = docFile.listFiles();
		List<File> list = new ArrayList<>();
		for (DocumentFile file : files) {
			AndroidFile f = new AndroidFile(file);
			if (filter.accept(f, file.getName())) {
				list.add(f);
			}
		}

		return list.toArray(new File[0]);
	}

	@Nullable
	@Override
	public File[] listFiles(@Nullable FileFilter filter) {
		log("listFiles");

		if (filter == null) {
			return listFiles();
		}
		DocumentFile[] files = docFile.listFiles();
		List<File> list = new ArrayList<>();
		for (DocumentFile file : files) {
			AndroidFile f = new AndroidFile(file);
			if (filter.accept(f)) {
				list.add(f);
			}
		}

		return list.toArray(new File[0]);
	}

	@Override
	public boolean mkdir() {
		log("mkdir");
		if (docFile.exists()) {
			return true;
		}
		DocumentFile parentFile = getParentDocumentFile();
		if (parentFile == null) {
			return false;
		}
		DocumentFile directory = parentFile.createDirectory(getName());
		return directory != null;
	}

	@Override
	public boolean mkdirs() {
		log("mkdirs");
		if (exists()) {
			return false;
		}
		if (mkdir()) {
			return true;
		}
		File parent = getParentFile();
		return (parent != null && (parent.mkdirs() || parent.exists()) && mkdir());
	}

	@Override
	public boolean renameTo(@NonNull File dest) {
		log("renameTo");
		Log.e(TAG, "renameTo: " + dest + " from " + this
				+ ". Only rename supported.  Move will bork");
		return docFile.renameTo(dest.getName());
	}

	@Override
	public boolean setLastModified(long time) {
		log("setLastMofieid");
		notImplemented();
		return false;
	}

	@Override
	public boolean setReadOnly() {
		log("setReadOnly");
		notImplemented();
		return false;
	}

	@Override
	public boolean setWritable(boolean writable, boolean ownerOnly) {
		log("setWritable");
		notImplemented();
		return false;
	}

	@Override
	public boolean setWritable(boolean writable) {
		log("setwritable");
		notImplemented();
		return false;
	}

	@Override
	public boolean setReadable(boolean readable, boolean ownerOnly) {
		log("setReadable");
		notImplemented();
		return false;
	}

	@Override
	public boolean setReadable(boolean readable) {
		log("setReadable");
		notImplemented();
		return false;
	}

	@Override
	public boolean setExecutable(boolean executable, boolean ownerOnly) {
		log("setExecutable");
		notImplemented();
		return false;
	}

	@Override
	public boolean setExecutable(boolean executable) {
		log("setExecutable");
		notImplemented();
		return false;
	}

	@Override
	public boolean canExecute() {
		log("canExecute");
		notImplemented();
		return false;
	}

	@Override
	public long getTotalSpace() {
		log("getTotalSpace");
		notImplemented();
		return super.getTotalSpace();
	}

	@Override
	public long getFreeSpace() {
		PathInfo pathInfo = getPathInfo();
		log("getFreeSpace=" + pathInfo.freeBytes);
		return pathInfo.freeBytes;
	}

	@Override
	public long getUsableSpace() {
		PathInfo pathInfo = getPathInfo();
		log("getUsableSpace=" + pathInfo.freeBytes);
		return pathInfo.freeBytes;
	}

	@NonNull
	public PathInfo getPathInfo() {
		if (pathInfo == null) {
			pathInfo = FileUtils.buildPathInfo(path);
		}
		return pathInfo;
	}

	@Override
	public int compareTo(@NonNull File pathname) {
		log("compareTo");
		if (pathname instanceof AndroidFile) {
			return (path.compareTo((((AndroidFile) pathname).path)));
		}
		return super.compareTo(pathname);
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		log("equals");
		if (obj instanceof AndroidFile) {
			// DocumentFile doesn't override equals.  uri does toString() comparison
			return (path.equals((((AndroidFile) obj).path)));
		}
		return super.equals(obj);
	}

	@Override
	public int hashCode() {
		return path.hashCode();
	}

	@NonNull
	@Override
	public String toString() {
		log("toString");

		return path;
	}

	@RequiresApi(api = VERSION_CODES.O)
	@NonNull
	@Override
	public Path toPath() {
		log("toPath");

		return Paths.get(URI.create(path));
	}

	private static void notImplemented() {
		Log.e(TAG, "not implemented. " + AndroidUtils.getCompressedStackTrace());
	}

	@SuppressLint("LogConditional")
	@AssumeNoSideEffects
	void log(String s) {
		if (!DEBUG_DETAILED) {
			return;
		}
		Log.d(TAG,
				s + "] " + path + " via " + AndroidUtils.getCompressedStackTrace());
	}

	@NonNull
	public Uri getUri() {
		return uri;
	}
}
