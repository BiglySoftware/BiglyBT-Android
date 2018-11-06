/*
 * Copyright (C) 2007-2008 OpenIntents.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.biglybt.android.util;

import java.io.*;

import com.biglybt.android.client.AndroidUtils;

import android.content.ContentUris;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

/** 
 * From https://github.com/iPaulPro/aFileChooser/blob/master/aFileChooser/src/com/ipaulpro/afilechooser/utils/FileUtils.java
 * Just the awseome methods to getPath
 */
public class PaulBurkeFileUtils
{
	public static final boolean DEBUG = AndroidUtils.DEBUG;

	/**
	 * Get a file path from a Uri. This will get the the path for Storage Access
	 * Framework Documents, as well as the _data field for the MediaStore and
	 * other file-based ContentProviders.<br>
	 * <br>
	 * Callers should check whether the path is local before assuming it
	 * represents a local file.
	 * 
	 * @param context The context.
	 * @param uri The Uri to query.
	 * @author paulburke
	 */
	public static String getPath(final Context context, final Uri uri) {

		if (DEBUG)
			Log.d("FileUtils", "Authority: " + uri.getAuthority() + ", Fragment: "
					+ uri.getFragment() + ", Port: " + uri.getPort() + ", Query: "
					+ uri.getQuery() + ", Scheme: " + uri.getScheme() + ", Host: "
					+ uri.getHost() + ", Segments: " + uri.getPathSegments().toString());

		// DocumentProvider
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			String docId = null;
			if (DocumentsContract.isDocumentUri(context, uri)) {
				docId = DocumentsContract.getDocumentId(uri);
			} else {
				boolean mightBeTreeUri = true;
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
						mightBeTreeUri = DocumentsContract.isTreeUri(uri);
					}
					try {
						if (mightBeTreeUri) {
							docId = DocumentsContract.getTreeDocumentId(uri);
						}
					} catch (Throwable ignore) {
					}
				}
			}

			if (docId != null) {
				// LocalStorageProvider
				if (isLocalStorageDocument(uri)) {
					// The path is the id
					return docId;
				}

				// ExternalStorageProvider
				if (isExternalStorageDocument(uri)) {
					final String[] split = docId.split(":");
					final String type = split[0];

					if ("primary".equalsIgnoreCase(type)) {
						return Environment.getExternalStorageDirectory() + "/" + split[1];
					}

					File[] externalFilesDirs = context.getExternalFilesDirs(null);
					int typeLength = type.length();
					for (File externalFilesDir : externalFilesDirs) {
						if (externalFilesDir == null) {
							continue;
						}
						String absolutePath = externalFilesDir.getAbsolutePath();
						int pathLength = absolutePath.length();
						int posType = absolutePath.indexOf("/" + type);
						if (posType >= 0 && (pathLength == posType + typeLength
								|| absolutePath.charAt(posType + typeLength + 1) == '/')) {
							String storagePath = absolutePath.substring(0,
									posType + typeLength + 1);
							return storagePath + "/" + split[1];
						}
					}

					// Not sure if all devices have /storage/xxx
					File f = new File("/storage/" + type);
					if (f.isDirectory()) {
						return f.getAbsolutePath() + "/" + split[1];
					}
					// TODO handle non-primary volumes
				}

				// DownloadsProvider
				if (isDownloadsDocument(uri)) {
					// From https://github.com/iPaulPro/aFileChooser/pull/96/file
					final String id = DocumentsContract.getDocumentId(uri);
					Uri contentUri;
					try {
						contentUri = ContentUris.withAppendedId(
								Uri.parse("content://downloads/public_downloads"),
								Long.valueOf(id));
					} catch (NumberFormatException e) {

						if (!TextUtils.isEmpty(id)) {
							if (id.startsWith("raw:")) {
								return id.replaceFirst("raw:", "");
							}
						}
						contentUri = ContentUris.withAppendedId(
								Uri.parse("content://downloads/public_downloads"),
								Long.valueOf(id));
					}

					return getDataColumn(context, contentUri, null, null);
				}

				// MediaProvider
				if (isMediaDocument(uri)) {
					final String[] split = docId.split(":");
					final String type = split[0];

					Uri contentUri = null;
					if ("image".equals(type)) {
						contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
					} else if ("video".equals(type)) {
						contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
					} else if ("audio".equals(type)) {
						contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
					}

					final String selection = "_id=?";
					final String[] selectionArgs = new String[] {
						split[1]
					};

					return getDataColumn(context, contentUri, selection, selectionArgs);
				}
			}
		}

		// MediaStore (and general)
		if ("content".equalsIgnoreCase(uri.getScheme())) {

			// Return the remote address
			if (isGooglePhotosUri(uri))
				return uri.getLastPathSegment();

			// From https://github.com/iPaulPro/aFileChooser/pull/97
			String filePath = getDataColumn(context, uri, null, null);
			if (filePath == null) {
				try {
					filePath = getDriveFilePath(context, uri);
				} catch (IOException e) {
					if (DEBUG) {
						Log.e("PBFU", "getPath", e);
					}
					return null;
				}
			}
			return filePath;
		}

		// File
		if ("file".equalsIgnoreCase(uri.getScheme())) {
			return uri.getPath();
		}

		return null;
	}

	private static String getDriveFilePath(Context context, Uri uri)
			throws IOException {
		ContextWrapper ctr = new ContextWrapper(context.getApplicationContext());
		// path to /data/data/yourapp/app_data/imageDir
		File directory = ctr.getDir("tempFilesDir", Context.MODE_PRIVATE);
		// Create imageDir
		File driveFile = new File(directory, "tempDriveFile");
		FileOutputStream fs = null;
		InputStream is = context.getContentResolver().openInputStream(uri);
		try {
			fs = new FileOutputStream(driveFile);
			int n;
			byte[] buffer = new byte[1024];
			if (is == null)
				throw new IOException();
			while ((n = is.read(buffer)) > -1) {
				fs.write(buffer, 0, n);
			}
			return driveFile.getAbsolutePath();
		} finally {
			if (fs != null) {
				fs.close();
			}
			is.close();
		}
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is LocalStorageProvider.
	 * @author paulburke
	 */
	public static boolean isLocalStorageDocument(Uri uri) {
		return false; // LocalStorageProvider.AUTHORITY.equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is ExternalStorageProvider.
	 * @author paulburke
	 */
	public static boolean isExternalStorageDocument(Uri uri) {
		return "com.android.externalstorage.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is DownloadsProvider.
	 * @author paulburke
	 */
	public static boolean isDownloadsDocument(Uri uri) {
		return "com.android.providers.downloads.documents".equals(
				uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is MediaProvider.
	 * @author paulburke
	 */
	public static boolean isMediaDocument(Uri uri) {
		return "com.android.providers.media.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is Google Photos.
	 */
	public static boolean isGooglePhotosUri(Uri uri) {
		return "com.google.android.apps.photos.content".equals(uri.getAuthority());
	}

	/**
	 * Get the value of the data column for this Uri. This is useful for
	 * MediaStore Uris, and other file-based ContentProviders.
	 *
	 * @param context The context.
	 * @param uri The Uri to query.
	 * @param selection (Optional) Filter used in the query.
	 * @param selectionArgs (Optional) Selection arguments used in the query.
	 * @return The value of the _data column, which is typically a file path.
	 * @author paulburke
	 */
	public static String getDataColumn(Context context, @Nullable Uri uri,
			@Nullable String selection, @Nullable String[] selectionArgs) {

		Cursor cursor = null;
		final String column = "_data";
		final String[] projection = {
			column
		};

		try {
			cursor = context.getContentResolver().query(uri, projection, selection,
					selectionArgs, null);
			if (cursor != null && cursor.moveToFirst()) {
				if (DEBUG)
					DatabaseUtils.dumpCursor(cursor);

				final int column_index = cursor.getColumnIndexOrThrow(column);
				return cursor.getString(column_index);
			}
		} catch (Exception e) {
			// SecurityException  in the wild
			return null;
		} finally {
			if (cursor != null)
				cursor.close();
		}
		return null;
	}

}
