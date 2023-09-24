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

import android.app.Activity;
import android.content.*;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Parcelable;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import androidx.core.provider.DocumentsContractCompat;
import androidx.documentfile.provider.DocumentFile;

import com.biglybt.android.client.*;
import com.biglybt.android.client.activity.DirectoryChooserActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.rdrei.android.dirchooser.DirectoryChooserConfig;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by TuxPaper on 8/28/17.
 */

public class FileUtils
{
	private static final String TAG = "FileUtils";

	public static InputStream getInputStream(@NonNull Activity context,
			@NonNull Uri uri)
			throws FileNotFoundException {
		ContentResolver contentResolver = context.getContentResolver();
		uri = fixUri(uri);
		try {
			return contentResolver.openInputStream(uri);
		} catch (FileNotFoundException e) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
				String realPath = PaulBurkeFileUtils.getPath(context, uri);
				if (realPath != null) {
					String meh = realPath.startsWith("/") ? "file://" + realPath
							: realPath;
					return contentResolver.openInputStream(Uri.parse(meh));
				}
			}
			throw e;
		}
	}

	/** 
	 * Fix URIs containing incorrect '%'.  This happens with {@link Intent#getData()},
	 * but I'm not sure if it's the originating app's problem or the OS.
	 * <p/>
	 * For example, an URI of "http://google.com/the%!" can be passed to us
	 * from another app.  Uri will decode "%!" into hex "EFBFBD C080", when
	 * the other app really wanted "%!".  The other app should have used
	 * the url "http://google.com/the%25!", but we can't control that.
	 */
	@NonNull
	public static Uri fixUri(@NonNull Uri uri) {
		String uriString = uri.toString();
		int i = uriString.indexOf('%');
		if (i < 0) {
			return uri;
		}
		int length = uriString.length();
		StringBuilder sb = new StringBuilder(length);
		int lastPos = 0;
		while (i >= 0) {
			if (i == length - 1) {
				sb.append(uriString.substring(lastPos, i));
				sb.append("%25");
				lastPos = length;
				break;
			}
			if (i == length - 2) {
				sb.append(uriString.substring(lastPos, i));
				sb.append("%25");
				sb.append(uriString.charAt(length - 1));
				lastPos = length;
				break;
			}

			sb.append(uriString.substring(lastPos, i)); // up to %

			char nextChar = uriString.charAt(i + 1);
			char nextChar2 = uriString.charAt(i + 2);
			boolean ok = ((nextChar >= '0' && nextChar <= '9')
					|| (nextChar >= 'a' && nextChar <= 'f')
					|| (nextChar >= 'A' && nextChar <= 'F'))
					&& ((nextChar2 >= '0' && nextChar2 <= '9')
							|| (nextChar2 >= 'a' && nextChar2 <= 'f')
							|| (nextChar2 >= 'A' && nextChar2 <= 'F'));
			if (ok) {
				lastPos = i;
			} else {
				sb.append("%25");
				lastPos = i + 1; // % is processed
			}
			i = uriString.indexOf('%', i + 1);
		}
		if (lastPos < length) {
			sb.append(uriString.substring(lastPos));
		}
		Uri parse = Uri.parse(sb.toString());
		return parse == null ? uri : parse;
	}

	@NonNull
	private static Intent buildOSFolderChooserIntent(Context context,
			String initialDir) {

		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		intent.putExtra("android.provider.extra.SHOW_ADVANCED", true); // DocumentsContract.EXTRA_SHOW_ADVANCED
		intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
		intent.putExtra("android.content.extra.FANCY", true);
		intent.putExtra("android.content.extra.SHOW_FILESIZE", true);

		if (initialDir != null && VERSION.SDK_INT >= VERSION_CODES.O) {
			// EXTRA_INITIAL_URI only works on >= 26 (Android O)
			Uri uri = guessTreeUri(context, initialDir, false);

			if (uri != null) {
				uri = DocumentsContractCompat.buildDocumentUriUsingTree(uri,
						DocumentsContractCompat.getTreeDocumentId(uri));
				// EXTRA_INITIAL_URI needs Document Uri.  
				intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri);
			}
		}

		Intent chooserIntent = Intent.createChooser(intent, "Choose Folder");
		PackageManager pm = context.getPackageManager();
		if (pm != null) {
			// Build out own potential file browsing intents
			// Without this on AndroidTV, the system one may auto-launch without
			// ever asking the user.
			List<Intent> targetedShareIntents = new ArrayList<>();

			boolean modified = false;
			List<ResolveInfo> infos = pm.queryIntentActivities(intent,
					Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
							? PackageManager.MATCH_ALL : PackageManager.MATCH_DEFAULT_ONLY);
			for (ResolveInfo info : infos) {

				if (info.activityInfo.name.toLowerCase().contains("stub")) {
					// com.google.android.tv.frameworkpackagestubs/.Stubs$DocumentsStub
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "openFileChooser: remove " + info);
					}
					modified = true;
				} else {
					Intent targetedShareIntent = (Intent) intent.clone();
					targetedShareIntent.setPackage(info.activityInfo.packageName);
					targetedShareIntent.setClassName(info.activityInfo.packageName,
							info.activityInfo.name);
					targetedShareIntents.add(targetedShareIntent);
				}

			}

			if (modified && targetedShareIntents.size() > 0) {
				chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS,
						targetedShareIntents.toArray(new Parcelable[] {}));
			}

		}
		return chooserIntent;
	}

	/**
	 * Returns a clean TreeUri.
	 * <p></p>
	 * If the path contains a document, it is stripped.
	 * <br>
	 * If the path is native, a tree content uri will be built if SAF location
	 * is found. Otherwise, null will return.
	 *
	 * @param useDocumentId true : If the path has a document, return that as the tree uri
	 */
	@Nullable
	public static Uri guessTreeUri(@NonNull Context context, @NonNull String path,
			boolean useDocumentId) {
		if (isContentPath(path)) {
			Uri uri = Uri.parse(path);
			if (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) {
				return uri;
			}

			if (useDocumentId
					&& DocumentsContractCompat.isDocumentUri(context, uri)) {
				return DocumentsContract.buildTreeDocumentUri(uri.getAuthority(),
						DocumentsContract.getDocumentId(uri));
			}
			if (DocumentsContractCompat.isTreeUri(uri)) {
				return DocumentsContract.buildTreeDocumentUri(uri.getAuthority(),
						DocumentsContract.getTreeDocumentId(uri));
			}
			return uri;
		}

		StorageManager sm = (StorageManager) context.getSystemService(
				Context.STORAGE_SERVICE);
		StorageVolume storageVolume = findStorageVolume(sm, new File(path));
		String storageVolumePath = getStorageVolumePath(storageVolume);

		if (storageVolumePath != null && path.startsWith(storageVolumePath)) {
			String s = path.substring(storageVolumePath.length());
			if (s.startsWith("/")) {
				s = s.substring(1);
			}

			String uuid;
			if (VERSION.SDK_INT >= VERSION_CODES.N && storageVolume.isPrimary()) {
				// XXX is it always primary? getStorageVolumeUuid (usually) returns null
				uuid = "primary";
			} else {
				uuid = FileUtils.getStorageVolumeUuid(storageVolume);
				if (uuid == null) {
					uuid = "primary";
				}
			}
			return new Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(
					"com.android.externalstorage.documents").appendPath(
							"tree").appendPath(uuid + ":" + s).build();
		}

		return null;
	}

	public static boolean isContentPath(String s) {
		return s != null && s.startsWith("content://");
	}

	public static boolean canUseSAF(Context context) {
		return numOSFolderChoosers(context) > 0;
	}

	public static void launchFolderChooser(@NonNull Activity activity,
			String initialDir, @NonNull ActivityResultLauncher<Intent> launcher) {
		int numChoosers = numOSFolderChoosers(activity);
		if (numChoosers > 0) {
			if (AndroidUtils.isTV(activity)) {
				MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(
						activity);
				builder.setTitle(R.string.saf_warning_title);
				builder.setMessage(R.string.saf_warning_text);
				builder.setPositiveButton(R.string.saf_warning_useOS,
						(dialog, which) -> {
							if (activity.isFinishing()) {
								return;
							}

							launcher.launch(buildOSFolderChooserIntent(activity, initialDir));
						});
				builder.setNeutralButton(R.string.saf_warning_useInternal,
						(dialog, which) -> {
							if (activity.isFinishing()) {
								return;
							}

							launchInternalFolderChooser(activity, initialDir, launcher);
						});
				AlertDialog dialog = builder.create();
				dialog.setOnShowListener(di -> {
					final Button btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
					if (btn != null) {
						btn.requestFocus();
					}
				});

				dialog.show();
			} else {
				launcher.launch(buildOSFolderChooserIntent(activity, initialDir));
			}
		} else {
			launchInternalFolderChooser(activity, initialDir, launcher);
		}

	}

	private static void launchInternalFolderChooser(@NonNull Context context,
			String initialDir, @NonNull ActivityResultLauncher<Intent> launcher) {
		DirectoryChooserConfig.Builder configBuilder = DirectoryChooserConfig.builder();
		configBuilder.newDirectoryName("BiglyBT");
		if (initialDir != null) {
			if (FileUtils.isContentPath(initialDir)) {
				initialDir = PaulBurkeFileUtils.getPath(context, Uri.parse(initialDir),
						false);
			}
			configBuilder.initialDirectory(initialDir);
		}
		configBuilder.allowReadOnlyDirectory(false);
		configBuilder.allowNewDirectoryNameModification(true);
		DirectoryChooserConfig config = configBuilder.build();

		Intent intent = new Intent(context,
				DirectoryChooserActivity.class).putExtra(
						DirectoryChooserActivity.EXTRA_CONFIG, config);
		launcher.launch(intent);
	}

	public static int numOSFolderChoosers(Context context) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			// No SAF folder chooser until API 21
			return -1;
		}
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		PackageManager manager = context.getPackageManager();
		List<ResolveInfo> infos = manager.queryIntentActivities(intent, 0);
		// MiBox3 (API 28!) will have empty list
		if (infos.size() == 0) {
			return 0;
		}
		// nVidiaShield (API 26) returns 1, but DPAD broken and can't select.
		// Component name is "com.android.documentsui.picker.PickActivity", but 
		// sounds like a generic name so we can't just skip it in case it's a
		// working version.
		// Other Android TV manufacturers have stubs that pop up a message saying it's not supported
		int numFound = 0;
		for (ResolveInfo info : infos) {
			ComponentInfo componentInfo = AndroidUtils.getComponentInfo(info);
			if (componentInfo == null || componentInfo.name == null) {
				numFound++;
			} else {
				String lName = componentInfo.name.toLowerCase();
				if (!lName.contains("stub")) {
					numFound++;
				}
			}
		}

		return numFound;
	}

	public static class FileChooserItem
	{
		public final CharSequence text;

		public final Drawable icon;

		public final Intent intent;

		public final CharSequence subtext;

		FileChooserItem(CharSequence text, CharSequence subtext, Drawable icon,
				Intent intent) {
			this.text = text;
			this.icon = icon;
			this.intent = intent;
			this.subtext = subtext;
		}

		@NonNull
		@Override
		public String toString() {
			return text.toString();
		}
	}

	public static void launchFileChooser(@NonNull Context context,
			@NonNull String mimeType,
			@NonNull ActivityResultLauncher<Intent> launcher) {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType(mimeType);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
/*
		// special intent for Samsung file manager
		// might be needed for older Samsung Android OS 
		// (maybe queryIntentActivities doesn't return it or something.. need to check)
		Intent sIntent = new Intent("com.sec.android.app.myfiles.PICK_DATA");

		sIntent.putExtra("CONTENT_TYPE", mimeType);
		sIntent.addCategory(Intent.CATEGORY_DEFAULT);
		if (packageManager.resolveActivity(sIntent, 0) != null) {
		  //use it
		}  
*/
		String title = context.getString(R.string.open_file);
		PackageManager pm = context.getPackageManager();

		if (pm != null) {
			List<FileChooserItem> items = new ArrayList<>();

			List<ResolveInfo> infos = pm.queryIntentActivities(intent,
					android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
							? PackageManager.MATCH_ALL : PackageManager.MATCH_DEFAULT_ONLY);
			for (ResolveInfo info : infos) {

				if (info.activityInfo.name != null
						&& info.activityInfo.name.toLowerCase().contains("stub")) {
					// com.google.android.tv.frameworkpackagestubs/.Stubs$DocumentsStub
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "openFileChooser: remove " + info);
					}
				} else {
					Intent matchingIntent = (Intent) intent.clone();
					matchingIntent.setPackage(info.activityInfo.packageName);
					matchingIntent.setClassName(info.activityInfo.packageName,
							info.activityInfo.name);

					CharSequence appName = info.activityInfo.loadLabel(pm);
					Drawable icon = info.activityInfo.loadIcon(pm);
					items.add(new FileChooserItem(appName, info.activityInfo.name, icon,
							matchingIntent));
				}

			}

			if (items.size() > 1) {
				Set<CharSequence> names = new HashSet<>();
				final Set<CharSequence> dupNames = new HashSet<>();
				for (FileChooserItem item : items) {
					if (!names.add(item.text)) {
						dupNames.add(item.text);
					}
				}
				ListAdapter adapter = new ArrayAdapter<FileChooserItem>(context,
						R.layout.select_dialog_item_material, android.R.id.text1, items) {
					@NonNull
					@Override
					public View getView(int position, @Nullable View convertView,
							@NonNull ViewGroup parent) {
						View v = super.getView(position, convertView, parent);
						ImageView iv = (ImageView) v.findViewById(android.R.id.icon);
						TextView tv = (TextView) v.findViewById(android.R.id.text2);

						FileChooserItem item = items.get(position);

						iv.setImageDrawable(item.icon);
						if (tv != null) {
							boolean showSubText = dupNames.contains(item.text);
							tv.setVisibility(showSubText ? View.VISIBLE : View.GONE);
							tv.setText(showSubText ? item.subtext : "");
						}

						return v;
					}
				};
				AlertDialog.Builder builder = new MaterialAlertDialogBuilder(context);
				builder.setTitle(title);
				builder.setAdapter(adapter, (dialog, which) -> {
					if (which >= 0 && which < items.size()) {
						FileChooserItem item = items.get(which);
						launcher.launch(item.intent);
					}
					dialog.dismiss();
				});
				builder.create().show();
				return;
			}

		}

		launcher.launch(Intent.createChooser(intent, title));
	}

	@Nullable
	public static Intent createNewFileChooserIntent(@NonNull String mimeType,
			@Nullable Uri pickerInitialUri, @NonNull String filename) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
			return null;
		}

		Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);

		// 33 requires queryIntentActivities to define queries in AndroidManifest
		// Skip need ACTION_CREATE_DOCUMENT query perms and assume there is one
		if (Build.VERSION.SDK_INT < VERSION_CODES.TIRAMISU) {
			PackageManager pm = BiglyBTApp.getContext().getPackageManager();
			if (pm != null) {
				List<ResolveInfo> infos = pm.queryIntentActivities(intent,
						Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
								? PackageManager.MATCH_ALL : PackageManager.MATCH_DEFAULT_ONLY);
				boolean found = false;
				for (ResolveInfo info : infos) {
					if (!info.activityInfo.name.toLowerCase().contains("stub")) {
						found = true;
						break;
					}
				}

				if (!found) {
					return null;
				}
			}
		}

		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType(mimeType);
		intent.putExtra(Intent.EXTRA_TITLE, filename);

		if (pickerInitialUri != null && VERSION.SDK_INT >= VERSION_CODES.O) {
			intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri);
		}
		return intent;
	}

	public static @Nullable String getUriTitle(Context context, Uri uri) {
		String result = null;
		if (uri == null) {
			return null;
		}
		String scheme = uri.getScheme();
		if (scheme == null) {
			return uri.toString();
		}
		if ("content".equals(scheme)) {
			Cursor cursor = context.getContentResolver().query(uri, null, null, null,
					null);
			try {
				if (cursor != null && cursor.moveToFirst()) {
					int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
					if (columnIndex >= 0) {
						result = cursor.getString(columnIndex);
					}
				}
			} catch (Exception ignore) {
			} finally {
				if (cursor != null) {
					cursor.close();
				}
			}
		}
		if (result == null) {
			if (scheme.startsWith("http")) {
				return AndroidUtils.decodeURL(uri.toString());
			}
			result = uri.getPath();
			if (result == null) {
				return uri.toString();
			}
			int cut = result.lastIndexOf('/');
			if (cut != -1 && cut + 1 != result.length()) {
				result = result.substring(cut + 1);
			}
		}
		return result;
	}

	@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
	public static String getVolumeIdFromTreeUri(final Uri treeUri) {
		try {
			final String docId = DocumentsContract.getTreeDocumentId(treeUri);
			final String[] split = docId.split(":");
			if (split.length > 0) {
				return split[0];
			}
		} catch (IllegalArgumentException ignore) {
		}
		return null;
	}

	public static boolean isTreeUri(Uri uri) {
		if (VERSION.SDK_INT >= VERSION_CODES.N) {
			return DocumentsContract.isTreeUri(uri);
		}
		final List<String> paths = uri.getPathSegments();
		return (paths.size() >= 2 && "tree".equals(paths.get(0)));
	}

	public static void unzip(InputStream stream, File destination,
			boolean overwrite) {
		dirChecker(destination, "");
		byte[] buffer = new byte[10240];
		try {
			int numCopied = 0;
			ZipInputStream zin = new ZipInputStream(stream);
			ZipEntry ze = null;

			while ((ze = zin.getNextEntry()) != null) {

				if (ze.isDirectory()) {
					dirChecker(destination, ze.getName());
				} else {
					File f = new File(destination, ze.getName());
					if (overwrite || !f.exists() || f.length() != ze.getSize()
							|| f.lastModified() != ze.getTime()) {
						FileOutputStream fout = new FileOutputStream(f);
						int count;
						while ((count = zin.read(buffer)) != -1) {
							fout.write(buffer, 0, count);
						}
						zin.closeEntry();
						fout.close();
						f.setLastModified(ze.getTime());
						numCopied++;
					}
				}

			}
			zin.close();

			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "unzip: " + numCopied + " files copied");
			}
		} catch (Exception e) {
			Log.e(TAG, "unzip", e);
		}

	}

	private static void dirChecker(@NonNull File destination,
			@NonNull String dir) {
		File f = new File(destination, dir);

		if (!f.isDirectory()) {
			boolean success = f.mkdirs();
			if (!success) {
				Log.d(TAG, "Failed to create folder " + f.getName());
			}
		}
	}

	@WorkerThread
	public static boolean canWrite(File f) {
		if (AndroidUtilsUI.isUIThread()) {
			Log.w(TAG, "Calling canWrite on UIThread can be time consuming. "
					+ AndroidUtils.getCompressedStackTrace());
		}
		if (f == null || !f.canWrite()) {
			return false;
		}

		if (!f.isDirectory()) {
			return true;
		}

		try {
			File tempFile = new File(f, "BiglyBT-OkToDelete.tmp");
			// There are cases where createNewFile creates the file, but delete fails
			return tempFile.createNewFile() && tempFile.delete();
		} catch (IOException e) {
		}
		return false;
	}

	@NonNull
	public static String getMimeTypeForExt(String ext) {
		// Older Android doesn't support opening application/json mime type
		String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
		if (mimeType == null) {
			mimeType = "application/octet-stream";
		}
		return mimeType;
	}

	@Nullable
	public static String getStorageVolumePath(StorageVolume storageVolume) {
		if (storageVolume == null) {
			return null;
		}

		if (VERSION.SDK_INT >= VERSION_CODES.R) {
			return storageVolume.getDirectory().toString();
		}

		Object oPath = null;
		Class<? extends StorageVolume> storageVolumeClass = storageVolume.getClass();

		try {
			Field mPath = storageVolumeClass.getDeclaredField("mPath");
			mPath.setAccessible(true);
			oPath = mPath.get(storageVolume).toString();
		} catch (Throwable ignore) {
		}

		// getPath is hidden, but present since at API 15, and is hidden in 33
		// We could use getPathFile, but it's API 17, and is hidden in 33
		if (oPath == null) {
			try {
				Method mGetPath = storageVolumeClass.getMethod("getPath");
				oPath = mGetPath.invoke(storageVolume);
			} catch (Throwable ignore) {
			}
		}
		return (oPath instanceof String) ? (String) oPath : null;
	}

	public static boolean isStorageVolumeRemovable(StorageVolume storageVolume,
			boolean defaultVal) {
		if (VERSION.SDK_INT >= VERSION_CODES.N) {
			return storageVolume.isRemovable();
		}

		try {
			final Class<?> storageVolumeClazz = Class.forName(
					"android.os.storage.StorageVolume");
			Method isRemovable = storageVolumeClazz.getMethod("isRemovable");
			return (boolean) isRemovable.invoke(storageVolume);
		} catch (Throwable t) {
			Log.e(TAG, "isStorageVolumeRemovable: ", t);
		}

		return defaultVal;
	}

	public static String getStorageVolumeDescription(Context context,
			StorageVolume storageVolume, String defaultVal) {
		if (VERSION.SDK_INT >= VERSION_CODES.N) {
			return storageVolume.getDescription(context);
		}

		try {
			final Class<?> storageVolumeClazz = Class.forName(
					"android.os.storage.StorageVolume");
			Method getDescription = storageVolumeClazz.getMethod("getDescription",
					Context.class);
			return (String) getDescription.invoke(storageVolume, context);
		} catch (Throwable t) {
			Log.e(TAG, "getStorageVolumeDescription: ", t);
		}
		return defaultVal;
	}

	public static StorageVolume findStorageVolume(Context context,
			StorageManager sm, PathInfo pathInfo) {
		// StorageVolume has been around for forever (at least API 15)

		if (pathInfo.uri != null) {
			StorageVolume sv = findStorageVolume(sm, pathInfo.uri);
			if (sv != null) {
				return sv;
			}
		}

		if (pathInfo.file == null) {
			String path = PaulBurkeFileUtils.getPath(context, pathInfo.uri, false);
			if (path != null) {
				pathInfo.file = new File(path);
			}
		}

		return findStorageVolume(sm, pathInfo.file);
	}

	public static StorageVolume findStorageVolume(StorageManager sm, Uri uri) {
		if (uri == null) {
			return null;
		}

		if (VERSION.SDK_INT >= VERSION_CODES.Q) {
			try {
				return sm.getStorageVolume(uri);
			} catch (Throwable e) {
				// Used to throw IllegalArgumentException, but now throws
				// IllegalStateException("Unknown volume for " + uri)
			}
		}

		String volumeID = null;
		if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
			volumeID = getVolumeIdFromTreeUri(uri);
		}
		if (volumeID != null) {
			List<StorageVolume> storageVolumes = getStorageVolumes(sm);

			if (storageVolumes != null) {
				for (StorageVolume volume : storageVolumes) {
					String uuid = getStorageVolumeUuid(volume);
					if (uuid != null && uuid.equals(volumeID)) {
						return volume;
					}
				}
			}
		}

		return null;
	}

	@Nullable
	public static StorageVolume findStorageVolume(StorageManager sm, File file) {
		if (file == null) {
			return null;
		}

		if (VERSION.SDK_INT >= VERSION_CODES.N) {
			return sm.getStorageVolume(file);
		}

		// API 23 confirmed
		try {
			Method getStorageVolume = sm.getClass().getMethod("getStorageVolume",
					File.class);
			//noinspection NewApi
			return (StorageVolume) getStorageVolume.invoke(sm, file);
		} catch (Throwable t) {
		}

		// API 14 (at least)
		try {
			String canonicalPath = file.getCanonicalPath();

			Method getVolumeListMethod = StorageManager.class.getDeclaredMethod(
					"getVolumeList");
			StorageVolume[] sv = (StorageVolume[]) getVolumeListMethod.invoke(sm);

			final Class<?> storageVolumeClazz = Class.forName(
					"android.os.storage.StorageVolume");
			Method getPathFileMethod = storageVolumeClazz.getMethod("getPathFile");
			for (StorageVolume volume : sv) {
				String volumeCanonicalPath = ((File) getPathFileMethod.invoke(
						volume)).getCanonicalPath();
				if (pathContains(volumeCanonicalPath, canonicalPath)) {
					return volume;
				}
			}
		} catch (Throwable t) {
		}

		return null;
	}

	@Nullable
	public static String getStorageVolumeUuid(StorageVolume volume) {
		if (VERSION.SDK_INT >= VERSION_CODES.N) {
			return volume.getUuid();
		}

		try {
			final Class<?> storageVolumeClazz = Class.forName(
					"android.os.storage.StorageVolume");
			final Method getUuid = storageVolumeClazz.getMethod("getUuid");
			return (String) getUuid.invoke(volume);
		} catch (Throwable t) {
			Log.e(TAG, "getStorageVolumeUuid: ", t);
		}

		return null;
	}

	@Nullable
	public static List<StorageVolume> getStorageVolumes(StorageManager sm) {
		if (VERSION.SDK_INT >= VERSION_CODES.N) {
			return sm.getStorageVolumes();
		}

		try {
			// getVolumeList since API 13, hidden in 19 (K), replaced with getStorageVolumes in 24 (N)
			Method getVolumeListMethod = StorageManager.class.getDeclaredMethod(
					"getVolumeList");
			StorageVolume[] sv = (StorageVolume[]) getVolumeListMethod.invoke(sm);

			return sv == null ? null : Arrays.asList(sv);
		} catch (Throwable t) {
			Log.e(TAG, "findStorageVolume: ", t);
		}

		return null;

	}

	// From FileUtils (hidden on newer API)
	private static boolean pathContains(String dirPath, String filePath) {
		if (dirPath.equals(filePath)) {
			return true;
		}
		if (!dirPath.endsWith("/")) {
			dirPath += "/";
		}
		return filePath.startsWith(dirPath);
	}

	public static boolean hasFileAuth(ContentResolver contentResolver, Uri uri) {
		if (VERSION.SDK_INT < VERSION_CODES.KITKAT) {
			return true;
		}

		try {
			// Will do perms check, child check, and exists check
			contentResolver.canonicalize(uri);
			return true;
		} catch (IllegalArgumentException iae) {
			// likely "Failed to determine if xxx is child of yyy: java.io.FileNotFoundException: Missing file for zzz"

			try {
				DocumentFile testdocFile = DocumentFile.fromTreeUri(
						BiglyBTApp.getContext(), uri);
				if (!testdocFile.exists()) {
					Uri strippedUri = DocumentsContractCompat.buildTreeDocumentUri(
							uri.getAuthority(),
							DocumentsContractCompat.getTreeDocumentId(uri));
					return hasFileAuth(contentResolver, strippedUri);
				}
			} catch (Throwable t) {
				Log.e(TAG, "hasFileAuth: stripping " + uri, t);
			}

		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "hasFileAuth: " + uri + "\n" + t.getMessage());
				List<UriPermission> persistedUriPermissions = contentResolver.getPersistedUriPermissions();
				Log.d(TAG, "hasFileAuth: persisted " + persistedUriPermissions);
			}
		}

		return false;
	}

	public static void askForPathPerms(@NonNull Context context, @NonNull Uri uri,
			String names, @NonNull ActivityResultLauncher<Intent> launcher) {
		OffThread.runOffUIThread(() -> {
			PathInfo pathInfo = PathInfo.buildPathInfo(uri.toString());

			OffThread.runOnUIThread(() -> {
				MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(
						context);
				builder.setTitle(R.string.folder_permission_missing);
				boolean canHaveInitialDir = VERSION.SDK_INT >= VERSION_CODES.O;
				Resources resources = context.getResources();
				String s = resources.getString(R.string.ask_for_folder_auth,
						pathInfo.getFriendlyName(), names)
						+ "\n"
						+ resources.getString(
								canHaveInitialDir ? R.string.ask_for_folder_auth_new
										: R.string.ask_for_folder_auth_old,
								resources.getString(
										VERSION.SDK_INT >= 30 ? R.string.os_select_button_new
												: R.string.os_select_button_old));
				builder.setMessage(AndroidUtils.fromHTML(s));
				builder.setPositiveButton(R.string.authorize,
						(dialog, which) -> launcher.launch(
								buildOSFolderChooserIntent(context, pathInfo.fullPath)));
				AlertDialog dialog = builder.create();
				dialog.show();
			});
		});
	}
}
