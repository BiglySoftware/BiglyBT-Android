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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.BiglyBTApp;
import com.biglybt.android.client.R;
import com.biglybt.android.core.az.AndroidFileHandler;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class PathInfo
{
	private static final String TAG = "PathInfo";

	@NonNull
	public final String fullPath;

	public final boolean isSAF;

	public String shortName;

	public boolean isRemovable;

	public File file;

	public String storagePath;

	public String storageVolumeName;

	public boolean isPrivateStorage;

	private Boolean isReadOnly;

	public Uri uri;

	public long freeBytes;

	public PathInfo(@NonNull String fullPath) {
		this.fullPath = fullPath;
		isSAF = fullPath.startsWith("content://");
	}

	@WorkerThread
	@NonNull
	public static PathInfo buildPathInfo(@NonNull String fileOrUri) {
		if (!fileOrUri.startsWith("content://")) {
			// file:// will typically have an extra / for root
			String fileName = fileOrUri.startsWith("file://")
					? Uri.decode(fileOrUri.substring(7)) : fileOrUri;
			if (fileName != null) {
				if (AndroidUtils.DEBUG && !fileName.startsWith("/")) {
					Log.w(TAG, "buildPathInfo: Weird Path '" + fileName + "' via "
							+ AndroidUtils.getCompressedStackTrace());
				}
				return buildPathInfo(new File(fileName));
			}
		}

		PathInfo pathInfo = new PathInfo(fileOrUri);
		pathInfo.uri = Uri.parse(fileOrUri);
		if (pathInfo.uri == null) {
			return pathInfo;
		}

		Context context = BiglyBTApp.getContext();
		/* pathInfo.file not used yet when PathInfo is SAF. Would be better
		  to only call when a file is needed (hide file var, implement getFile(), etc)
		String path = PaulBurkeFileUtils.getPath(context, pathInfo.uri, false);
		if (path != null) {
			pathInfo.file = new File(path);
		}
		*/

		if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
			if (DocumentsContract.isDocumentUri(context, pathInfo.uri)) {
				pathInfo.shortName = DocumentsContract.getDocumentId(pathInfo.uri);
			} else if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
				if (FileUtils.isTreeUri(pathInfo.uri)) {
					pathInfo.shortName = DocumentsContract.getTreeDocumentId(
							pathInfo.uri);
				}
			}
			if (pathInfo.shortName != null) {
				int i = pathInfo.shortName.indexOf(':');
				if (i > 0) {
					String beforeColon = pathInfo.shortName.substring(0, i);
					pathInfo.shortName = pathInfo.shortName.substring(i + 1);

					if (beforeColon.equals("raw")) {
						File f = new File(pathInfo.shortName);
						if (f.exists()) {
							pathInfo.shortName = null;
							fillPathInfo(pathInfo, f);
							// assume we always have write access to content uri's
							pathInfo.isReadOnly = false;
						}
					}
				}
			}
		}

		StorageManager sm = (StorageManager) context.getSystemService(
				Context.STORAGE_SERVICE);

		if (sm != null) {
			StorageVolume storageVolume = null;
			if (VERSION.SDK_INT >= VERSION_CODES.Q) {
				try {
					storageVolume = sm.getStorageVolume(pathInfo.uri);
				} catch (Throwable e) {
					// Used to throw IllegalArgumentException, but now throws
					// IllegalStateException("Unknown volume for " + uri)
				}
			}
			if (storageVolume == null) {
				String volumeID = FileUtils.getVolumeIdFromTreeUri(pathInfo.uri);
				if (volumeID != null) {
					if (VERSION.SDK_INT >= VERSION_CODES.N) {
						List<StorageVolume> storageVolumes = sm.getStorageVolumes();
						for (StorageVolume volume : storageVolumes) {
							String uuid = volume.getUuid();
							if (uuid != null && uuid.equals(volumeID)) {
								storageVolume = volume;
								break;
							}
						}
					} else {
						try {
							final Class<?> storageVolumeClazz = Class.forName(
									"android.os.storage.StorageVolume");
							final Method getUuid = storageVolumeClazz.getMethod("getUuid");

							Method getVolumeListMethod = StorageManager.class.getDeclaredMethod(
									"getVolumeList");
							StorageVolume[] sv = (StorageVolume[]) getVolumeListMethod.invoke(
									sm);
							for (StorageVolume volume : sv) {
								String uuid = (String) getUuid.invoke(volume);
								if (uuid != null && uuid.equals(volumeID)) {
									storageVolume = volume;

									try {
										Method getDescription = storageVolumeClazz.getMethod(
												"getDescription", Context.class);
										pathInfo.storageVolumeName = (String) getDescription.invoke(
												storageVolume, context);
									} catch (Throwable t) {
										Log.e(TAG, "buildPathInfo: ", t);
									}
									try {
										Method isRemovable = storageVolumeClazz.getMethod(
												"isRemovable");
										pathInfo.isRemovable = (boolean) isRemovable.invoke(
												storageVolume);
									} catch (Throwable t) {
										Log.e(TAG, "buildPathInfo: ", t);
									}

									break;
								}
							}

						} catch (Throwable t) {
							Log.e(TAG, "buildPathInfo: ", t);
						}
					}

				} else {
					if (VERSION.SDK_INT >= VERSION_CODES.N) {
						if (pathInfo.file == null) {
							String path = PaulBurkeFileUtils.getPath(context, pathInfo.uri,
									false);
							if (path != null) {
								pathInfo.file = new File(path);
							}
						}
						if (pathInfo.file != null) {
							storageVolume = sm.getStorageVolume(pathInfo.file);
						}
					}
				}
			}

			if (storageVolume != null) {
				if (VERSION.SDK_INT >= VERSION_CODES.O) {
					try {
						String uuid = storageVolume.getUuid();
						if (uuid != null) {
							pathInfo.freeBytes = sm.getAllocatableBytes(
									UUID.fromString(uuid));
						}
					} catch (IllegalArgumentException ignore) {
						// StorageVolume.getUuid can return a "XXXX-XXXX" value which
						// can not be converted to a UUID
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				Object oPath = null;
				Class<? extends StorageVolume> storageVolumeClass = storageVolume.getClass();

				try {
					Field mPath = storageVolumeClass.getDeclaredField("mPath");
					mPath.setAccessible(true);
					oPath = mPath.get(storageVolume).toString();
				} catch (Throwable ignore) {
				}

				if (oPath == null) {
					try {
						Method mGetPath = storageVolumeClass.getMethod("getPath");
						oPath = mGetPath.invoke(storageVolume);
					} catch (Throwable ignore) {
					}
				}

				if (oPath instanceof String) {
					pathInfo.storagePath = (String) oPath;
					if (pathInfo.freeBytes <= 0) {
						File file = new File(pathInfo.storagePath);
						if (file.exists()) {
							pathInfo.freeBytes = file.getFreeSpace();
						}
					}
				}

				if (VERSION.SDK_INT >= VERSION_CODES.N) {
					pathInfo.storageVolumeName = storageVolume.getDescription(context);
					pathInfo.isRemovable = storageVolume.isRemovable();
				}
			}

		}

		if (pathInfo.shortName == null) {
			Cursor cursor = context.getContentResolver().query(pathInfo.uri, null,
					null, null, null);
			try {
				if (cursor != null && cursor.moveToFirst()) {
					int columnIndex = cursor.getColumnIndex("_data");
					if (columnIndex >= 0) {
						String path = cursor.getString(columnIndex);
						if (path != null) {
							pathInfo.file = new File(path);
							if (pathInfo.storagePath != null
									&& path.startsWith(pathInfo.storagePath)) {
								pathInfo.shortName = path.substring(
										pathInfo.storagePath.length());
								if (pathInfo.shortName.startsWith("/")) {
									pathInfo.shortName = pathInfo.shortName.substring(1);
								}
							} else {
								pathInfo.shortName = path;
							}
						}
					}
				}
			} catch (Exception ignore) {
			} finally {
				if (cursor != null) {
					cursor.close();
				}
			}

			if (pathInfo.shortName == null) {
				pathInfo.shortName = FileUtils.getUriTitle(context, pathInfo.uri);
			}
		}

		if (pathInfo.freeBytes == 0 && pathInfo.file != null) {
			pathInfo.freeBytes = pathInfo.file.getFreeSpace();
		}

		return pathInfo;
	}

	@NonNull
	@WorkerThread
	public static PathInfo buildPathInfo(@NonNull File f) {
		String absolutePath = f.getAbsolutePath();
		PathInfo pathInfo = new PathInfo(absolutePath);
		return fillPathInfo(pathInfo, f);
	}

	private static PathInfo fillPathInfo(@NonNull PathInfo pathInfo,
			@NonNull File f) {
		Context context = BiglyBTApp.getContext();
		String absolutePath = f.getAbsolutePath();

		pathInfo.file = f;
		pathInfo.freeBytes = f.getFreeSpace();
		pathInfo.storagePath = f.getParent();
		pathInfo.isReadOnly = !FileUtils.canWrite(f);

		File[] externalFilesDirs = ContextCompat.getExternalFilesDirs(context,
				null);
		if (externalFilesDirs.length > 1 && externalFilesDirs[1] != null) {
			String sdPath = externalFilesDirs[1].getAbsolutePath();
			if (absolutePath.startsWith(sdPath)) {
				pathInfo.storageVolumeName = context.getString(
						R.string.private_external_storage);
				pathInfo.storagePath = sdPath;
				pathInfo.shortName = absolutePath.substring(sdPath.length());
				pathInfo.isRemovable = true;
			}
		}

		if (pathInfo.shortName == null && externalFilesDirs.length > 0
				&& externalFilesDirs[0] != null) {
			String internalPath = externalFilesDirs[0].getAbsolutePath();
			if (absolutePath.startsWith(internalPath)) {
				pathInfo.storageVolumeName = context.getString(
						R.string.private_internal_storage);
				pathInfo.isPrivateStorage = true;
				pathInfo.storagePath = internalPath;
				pathInfo.shortName = absolutePath.substring(internalPath.length());
				pathInfo.isRemovable = false;
			}
		}

		if (pathInfo.shortName == null) {
			String path = Environment.getExternalStorageDirectory().getAbsolutePath();
			if (absolutePath.startsWith(path)) {
				boolean isRemovable = Environment.isExternalStorageRemovable();
				pathInfo.shortName = absolutePath.substring(path.length());
				pathInfo.storagePath = path;
				pathInfo.isRemovable = isRemovable;
			}
		}

		if (pathInfo.shortName == null) {
			String secondaryStorage = System.getenv("SECONDARY_STORAGE");
			if (secondaryStorage != null) {
				String[] split = secondaryStorage.split(File.pathSeparator);
				for (String dir : split) {
					String path = new File(dir).getAbsolutePath();
					if (absolutePath.startsWith(path)) {
						boolean isRemovable = true;
						pathInfo.storagePath = path;
						pathInfo.shortName = absolutePath.substring(path.length());
						pathInfo.isRemovable = isRemovable;
					}
				}
			}
		}

		if (pathInfo.shortName == null && VERSION.SDK_INT >= VERSION_CODES.N) {
			StorageManager sm = (StorageManager) context.getSystemService(
					Context.STORAGE_SERVICE);

			try {
				assert sm != null;
				StorageVolume storageVolume = sm.getStorageVolume(f);

				Object oPath = null;

				Class<? extends StorageVolume> storageVolumeClass = storageVolume.getClass();
				try {
					Field mPath = storageVolumeClass.getDeclaredField("mPath");
					mPath.setAccessible(true);
					oPath = mPath.get(storageVolume).toString();
				} catch (Throwable ignore) {
				}

				if (oPath == null) {
					try {
						Method mGetPath = storageVolumeClass.getMethod("getPath");
						oPath = mGetPath.invoke(storageVolume);
					} catch (Throwable ignore) {
					}
				}

				if (oPath instanceof String) {
					String path = (String) oPath;
					if (absolutePath.startsWith(path)) {
						pathInfo.storageVolumeName = storageVolume.getDescription(context);
						pathInfo.storagePath = path;
						pathInfo.shortName = absolutePath.substring(path.length());
						pathInfo.isRemovable = storageVolume.isRemovable();
					}
				}
			} catch (Throwable ignore) {
			}

		}

		if (pathInfo.shortName == null) {
			pathInfo.shortName = f.toString();
		}
		if (pathInfo.shortName.startsWith("/")) {
			pathInfo.shortName = pathInfo.shortName.substring(1);
		}
		return pathInfo;
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

	@WorkerThread
	public boolean isReadOnly() {
		if (isReadOnly != null) {
			return isReadOnly;
		}
		File file = new AndroidFileHandler().newFile(fullPath);
		isReadOnly = !file.canWrite();
		return isReadOnly;
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (!(obj instanceof PathInfo)) {
			return false;
		}

		PathInfo other = (PathInfo) obj;
		if (file == null || other.file == null) {
			// R8 will desugar Objects.equals, so the minAPI 19 lint warning is wrong
			//noinspection NewApi
			return Objects.equals(fullPath, other.fullPath);
		}
		return file.equals(other.file);
	}
}
