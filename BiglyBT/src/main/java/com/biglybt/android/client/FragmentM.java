/*
 * Copyright (c) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.android.client;

import java.util.Arrays;

import static com.biglybt.android.client.AndroidUtils.DEBUG;
import static com.biglybt.android.client.AndroidUtils.DEBUG_LIFECYCLE;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

/**
 * Fragment with permission handing methods
 *
 * Created by TuxPaper on 3/18/16.
 * <p/>
 * Duplicate code in {@link AppCompatActivityM}
 */
public class FragmentM
	extends Fragment
{
	private int requestPermissionID = 0;

	private final LongSparseArray<Runnable[]> requestPermissionRunnables = new LongSparseArray<>();

	private String classSimpleName;

	private boolean isFragmentVisible;

	private class PermissionRequestResults
	{
		final String[] permissions;

		final int[] grantResults;

		public PermissionRequestResults(String[] permissions, int[] grantResults) {
			this.permissions = permissions;
			this.grantResults = grantResults;
		}
	}

	private LongSparseArray<PermissionRequestResults> requestPermissionResults = null;

	private boolean isPaused;

	public void requestPermissions(String[] permissions, Runnable runnableOnGrant,
			@Nullable Runnable runnableOnDeny) {

		// requestPermissions supposedly does checkSelfPermission for us, but
		// I get prompted anyway, and clicking Revoke (on an already granted perm):
		// I/ActivityManager: Killing xxxx:com.vuze.android.client/u0a24 (adj 1): permissions revoked
		// Also, requestPermissions assumes PERMISSION_REVOKED on unknown
		// permission strings (ex READ_EXTERNAL_STORAGE on API 7)
		boolean allGranted = true;
		if (permissions.length > 0) {
			PackageManager packageManager = getContext().getPackageManager();
			for (String permission : permissions) {
				try {
					packageManager.getPermissionInfo(permission, 0);
				} catch (PackageManager.NameNotFoundException e) {
					Log.d("Perms", "requestPermissions: Permission " + permission
							+ " doesn't exist.  Assuming granted.");
					continue;
				}
				if (ActivityCompat.checkSelfPermission(getContext(),
						permission) != PackageManager.PERMISSION_GRANTED) {
					allGranted = false;
					break;
				}
			}
		}

		if (allGranted) {
			if (DEBUG) {
				Log.d("Perms", "requestPermissions: allGranted ("
						+ Arrays.toString(permissions) + ", running " + runnableOnGrant);
			}
			if (runnableOnGrant != null) {
				runnableOnGrant.run();
			}
			return;
		}

		if (DEBUG) {
			Log.d("Perms", "requestPermissions: requesting "
					+ Arrays.toString(permissions) + " for " + runnableOnGrant);
		}
		requestPermissionRunnables.put(requestPermissionID, new Runnable[] {
			runnableOnGrant,
			runnableOnDeny
		});
		requestPermissions(permissions, requestPermissionID);
		requestPermissionID++;
	}

	@Override
	public void onPause() {
		isPaused = true;
		super.onPause();
	}

	@Override
	public void onResume() {
		isPaused = false;
		super.onResume();
		if (!isFragmentVisible) {
			isFragmentVisible = true;
			onShowFragment();
		}

		// https://code.google.com/p/android/issues/detail?id=190966
		if (requestPermissionResults != null
				&& requestPermissionRunnables.size() > 0) {
			synchronized (requestPermissionRunnables) {
				for (int i = 0; i < requestPermissionResults.size(); i++) {
					long requestCode = requestPermissionResults.keyAt(i);
					PermissionRequestResults results = requestPermissionResults.get(
							requestCode);
					if (results != null) {
						onRequestPermissionsResult((int) requestCode, results.permissions,
								results.grantResults);
					}
				}
				requestPermissionResults = null;
			}
		}
	}

	/**
	 * Fragment is no longer visible.
	 *
	 * @implSpec When in multi-window mode, triggered via onStop.  Otherwise
	 *           triggered via onPause
	 */
	protected void onHideFragment() {
		if (DEBUG_LIFECYCLE) {
			log("Lifecycle",
					"onHideFragment via " + AndroidUtils.getCompressedStackTrace(3));
		}
	}

	/**
	 * Fragment is now visible to user
	 */
	protected void onShowFragment() {
		if (DEBUG_LIFECYCLE) {
			log("Lifecycle", "onShowFragment");
		}
	}

	public boolean isFragmentVisible() {
		return isFragmentVisible;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
			@NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		Runnable[] runnables = requestPermissionRunnables.get(requestCode);
		if (runnables != null) {

			if (isPaused) {
				// https://code.google.com/p/android/issues/detail?id=190966
				// our onResume will call this function again, when it's safe for the
				// runnables to open dialogs if they want
				if (requestPermissionResults == null) {
					requestPermissionResults = new LongSparseArray<>();
				}
				requestPermissionResults.put(requestCode,
						new PermissionRequestResults(permissions, grantResults));
				return;
			}

			requestPermissionRunnables.remove(requestCode);

			boolean allGranted = grantResults.length > 0;
			if (allGranted) {
				for (int grantResult : grantResults) {

					if (grantResult != PackageManager.PERMISSION_GRANTED) {
						allGranted = false;
						break;
					}
				}
			}

			if (DEBUG) {
				Log.d("Perms",
						"onRequestPermissionsResult: " + Arrays.toString(permissions) + " "
								+ (allGranted ? "granted" : "revoked") + " for "
								+ runnables[0]);
			}

			if (allGranted && runnables[0] != null) {
				runnables[0].run();
				return;
			}

			if (!allGranted && runnables[1] != null) {
				runnables[1].run();
				return;
			}
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		AnalyticsTracker.getInstance(this).fragmentResume(this);
	}

	@Override
	public void onStop() {
		super.onStop();
		if (isFragmentVisible) {
			isFragmentVisible = false;
			onHideFragment();
		}
		AnalyticsTracker.getInstance(this).fragmentPause(this);
	}

	@SuppressLint("LogConditional")
	public void log(String TAG, String s) {
		if (classSimpleName == null) {
			classSimpleName = AndroidUtils.getSimpleName(getClass()) + "@"
					+ Integer.toHexString(hashCode());
		}
		Log.d(classSimpleName, TAG + ": " + s);
	}

	@SuppressLint("LogConditional")
	public void log(int prority, String TAG, String s) {
		if (classSimpleName == null) {
			classSimpleName = AndroidUtils.getSimpleName(getClass()) + "@"
					+ Integer.toHexString(hashCode());
		}
		Log.println(prority, classSimpleName, TAG + ": " + s);
	}
}
