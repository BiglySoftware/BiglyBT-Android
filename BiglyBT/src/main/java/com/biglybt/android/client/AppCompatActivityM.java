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

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.collection.LongSparseArray;
import androidx.core.app.ActivityCompat;

/**
 * Activity with permission handing methods
 *
 * Created by TuxPaper on 3/18/16.
 *
 * Duplicate code in {@link FragmentM}
 */
@SuppressLint("Registered")
public class AppCompatActivityM
	extends AppCompatActivity
{
	private int requestPermissionID = 0;

	private final LongSparseArray<Runnable[]> requestPermissionRunnables = new LongSparseArray<>();

	private String classSimpleName;

	private boolean isActivityVisible;

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

	/**
	 * @return true if returned immediately
	 */
	public boolean requestPermissions(@NonNull String[] permissions,
			Runnable runnableOnGrant, @Nullable Runnable runnableOnDeny) {

		// requestPermissions supposedly does checkSelfPermission for us, but
		// I get prompted anyway, and clicking Revoke (on an already granted perm):
		// I/ActivityManager: Killing xxxx:com.vuze.android.client/u0a24 (adj 1): permissions revoked
		// Also, requestPermissions assumes PERMISSION_REVOKED on unknown
		// permission strings (ex READ_EXTERNAL_STORAGE on API 7)
		boolean allGranted = true;
		if (permissions.length > 0) {
			PackageManager packageManager = getPackageManager();
			for (String permission : permissions) {
				try {
					packageManager.getPermissionInfo(permission, 0);
				} catch (PackageManager.NameNotFoundException e) {
					log("Perms", "requestPermissions: Permission " + permission
							+ " doesn't exist.  Assuming granted.");
					continue;
				}
				if (ActivityCompat.checkSelfPermission(this,
						permission) != PackageManager.PERMISSION_GRANTED) {
					allGranted = false;
					break;
				}
			}
		}

		if (allGranted) {
			if (AndroidUtils.DEBUG) {
				log("Perms", "requestPermissions: allGranted ("
						+ Arrays.toString(permissions) + ", running " + runnableOnGrant);
			}
			if (runnableOnGrant != null) {
				runnableOnGrant.run();
			}
			return true;
		}

		if (AndroidUtils.DEBUG) {
			log("Perms", "requestPermissions: requesting "
					+ Arrays.toString(permissions) + " for " + runnableOnGrant);
		}
		requestPermissionRunnables.put(requestPermissionID, new Runnable[] {
			runnableOnGrant,
			runnableOnDeny
		});
		ActivityCompat.requestPermissions(this, permissions, requestPermissionID);
		requestPermissionID++;
		return false;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState,
			@Nullable PersistableBundle persistentState) {
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			log("Lifecycle", "onCreate2");
		}
		super.onCreate(savedInstanceState, persistentState);
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			log("Lifecycle", "onCreate1");
		}
		super.onCreate(savedInstanceState);
		/* For debugging focus
		getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
			log("FOCUS",   newFocus + "; was " + oldFocus);
		});
		/**/
	}

	@Override
	protected void onStop() {
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			log("Lifecycle", "onStop");
		}
		if (isActivityVisible) {
			isActivityVisible = false;
			onHideActivity();
		}
		super.onStop();
	}

	@Override
	protected void onPause() {
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			log("Lifecycle", "onPause");
		}
		isPaused = true;
		super.onPause();

		AnalyticsTracker.getInstance(this).activityPause(this);
	}

	/**
	 * Activity is no longer visible.
	 * 
	 * @implNote Split away from onStop to give a clearer method name
	 */
	protected void onHideActivity() {
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			log("Lifecycle",
					"onHideActivity via " + AndroidUtils.getCompressedStackTrace(3));
		}
	}

	/**
	 * Activity is now visible to user
	 *
	 * @implNote Split away from onResume to give a clearer method name
	 */
	protected void onShowActivity() {
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			log("Lifecycle", "onShowActivity");
		}
	}

	public boolean isActivityVisible() {
		return isActivityVisible;
	}

	@Override
	protected void onStart() {
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			log("Lifecycle", "onStart");
		}
		super.onStart();
	}

	@Override
	protected void onDestroy() {
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			log("Lifecycle", "onDestroy");
		}
		super.onDestroy();
	}

	@Override
	protected void onResume() {
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			log("Lifecycle", "onResume");
		}
		isPaused = false;
		super.onResume();
		if (!isActivityVisible) {
			isActivityVisible = true;
			onShowActivity();
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

		AnalyticsTracker.getInstance(this).activityResume(this);
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

			if (AndroidUtils.DEBUG) {
				log("Perms",
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

	public void log(String TAG, String s) {
		log(Log.DEBUG, TAG, s);
	}

	public void log(int priority, String TAG, String s) {
		if (AndroidUtils.DEBUG) {
			if (classSimpleName == null) {
				classSimpleName = AndroidUtils.getSimpleName(getClass()) + "@"
						+ Integer.toHexString(hashCode());
			}
			Log.println(priority, classSimpleName, TAG + ": " + s);
		}
	}
}
