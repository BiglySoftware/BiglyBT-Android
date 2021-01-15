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

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.R;
import com.biglybt.android.client.activity.DirectoryChooserActivity;
import com.biglybt.android.widget.CustomToast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.rdrei.android.dirchooser.DirectoryChooserConfig;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
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
		intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
		intent.putExtra("android.content.extra.FANCY", true);
		intent.putExtra("android.content.extra.SHOW_FILESIZE", true);

		if (initialDir != null && VERSION.SDK_INT >= VERSION_CODES.O) {
			// Only works on >= 26 (Android O).  Not sure what format is acceptable
			DocumentFile file = DocumentFile.fromFile(new File(initialDir));
			intent.putExtra("android.provider.extra.INITIAL_URI", file.getUri());
		}
		return intent;
	}

	public static boolean canUseSAF(Context context) {
		return numOSFolderChoosers(context) > 0;
	}

	public static void openFolderChooser(@NonNull DialogFragment fragment,
			String initialDir, int requestCode) {
		Context context = fragment.requireContext();

		int numChoosers = numOSFolderChoosers(context);
		if (numChoosers > 0) {
			if (AndroidUtils.isTV(context)) {
				MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(
						context);
				builder.setTitle(R.string.saf_warning_title);
				builder.setMessage(R.string.saf_warning_text);
				builder.setPositiveButton(R.string.saf_warning_useOS,
						(dialog, which) -> {
							Activity fragActivity = fragment.getActivity();
							if (fragActivity == null || (fragActivity.isFinishing())) {
								return;
							}

							fragment.startActivityForResult(
									buildOSFolderChooserIntent(context, initialDir), requestCode);
						});
				builder.setNeutralButton(R.string.saf_warning_useInternal,
						(dialog, which) -> {
							Activity fragActivity = fragment.getActivity();
							if (fragActivity == null || (fragActivity.isFinishing())) {
								return;
							}

							DirectoryChooserConfig.Builder configBuilder = DirectoryChooserConfig.builder();
							configBuilder.newDirectoryName("BiglyBT");
							if (initialDir != null) {
								configBuilder.initialDirectory(initialDir);
							}
							configBuilder.allowReadOnlyDirectory(false);
							configBuilder.allowNewDirectoryNameModification(true);
							DirectoryChooserConfig config = configBuilder.build();

							fragment.startActivityForResult(
									new Intent(context, DirectoryChooserActivity.class).putExtra(
											DirectoryChooserActivity.EXTRA_CONFIG, config),
									requestCode);

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
				fragment.startActivityForResult(
						buildOSFolderChooserIntent(context, initialDir), requestCode);
			}
		} else {

			DirectoryChooserConfig.Builder configBuilder = DirectoryChooserConfig.builder();
			configBuilder.newDirectoryName("BiglyBT");
			if (initialDir != null) {
				configBuilder.initialDirectory(initialDir);
			}
			configBuilder.allowReadOnlyDirectory(false);
			configBuilder.allowNewDirectoryNameModification(true);
			DirectoryChooserConfig config = configBuilder.build();

			fragment.startActivityForResult(
					new Intent(context, DirectoryChooserActivity.class).putExtra(
							DirectoryChooserActivity.EXTRA_CONFIG, config),
					requestCode);
		}

	}

	public static int numOSFolderChoosers(Context context) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			return -1;
		}
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		PackageManager manager = context.getPackageManager();
		List<ResolveInfo> infos = manager.queryIntentActivities(intent, 0);
		// MiBox3 will have empty list
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

	// From http://
	public static void openFileChooser(@NonNull Activity activity,
			String mimeType, int requestCode) {

		openFileChooser(activity, null, mimeType, requestCode);
	}

	public static void openFileChooser(@NonNull Activity activity,
			Fragment forFragment, String mimeType, int requestCode) {

		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType(mimeType);
		intent.addCategory(Intent.CATEGORY_OPENABLE);

		// special intent for Samsung file manager
		Intent sIntent = new Intent("com.sec.android.app.myfiles.PICK_DATA");

		sIntent.putExtra("CONTENT_TYPE", mimeType);
		sIntent.addCategory(Intent.CATEGORY_DEFAULT);

		Intent chooserIntent;
		String title = activity.getString(R.string.open_file);
		PackageManager packageManager = activity.getPackageManager();

		if (packageManager != null
				&& packageManager.resolveActivity(sIntent, 0) != null) {
			chooserIntent = Intent.createChooser(sIntent, title);
			chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] {
				intent
			});
		} else {
			chooserIntent = Intent.createChooser(intent, title);

			if (packageManager != null) {
				List<Intent> targetedShareIntents = new ArrayList<>();

				boolean modified = false;
				List<ResolveInfo> infos = packageManager.queryIntentActivities(intent,
						PackageManager.MATCH_DEFAULT_ONLY);
				for (ResolveInfo info : infos) {

					ComponentInfo componentInfo = AndroidUtils.getComponentInfo(info);
					if (componentInfo != null && componentInfo.name != null
							&& componentInfo.name.toLowerCase().contains("stub")) {
						// com.google.android.tv.frameworkpackagestubs/.Stubs$DocumentsStub
						if (AndroidUtils.DEBUG) {
							Log.d(TAG, "openFileChooser: remove " + info.toString());
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

		}

		if (chooserIntent != null) {
			try {
				if (forFragment == null) {
					activity.startActivityForResult(chooserIntent, requestCode);
				} else {
					forFragment.startActivityForResult(chooserIntent, requestCode);
				}
				return;
			} catch (android.content.ActivityNotFoundException ignore) {
			}
		}
		CustomToast.showText(R.string.no_file_chooser, Toast.LENGTH_SHORT);
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
					result = cursor.getString(
							cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
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

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
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
		try {
			File tempFile = File.createTempFile("tmp", "B", f);
			tempFile.delete();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
}
