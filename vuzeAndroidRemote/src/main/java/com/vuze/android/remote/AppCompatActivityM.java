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

package com.vuze.android.remote;

import java.util.Arrays;

import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.util.LongSparseArray;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

/**
 * Created by TuxPaper on 3/18/16.
 *
 * Duplicate code in {@link FragmentM}
 */
public class AppCompatActivityM
	extends AppCompatActivity
{
	private int requestPermissionID = 0;

	LongSparseArray<Runnable[]> requestPermissionRunnables = new LongSparseArray<>();

	public void requestPermissions(String[] permissions, Runnable runnableOnGrant,
			Runnable runnableOnDeny) {

		// requestPermissions supposedly does checkSelfPermission for us, but
		// I get prompted anyway, and clicking Revoke (on an already granted perm):
		// I/ActivityManager: Killing xxxx:com.vuze.android.remote/u0a24 (adj 1): permissions revoked
		boolean allGranted = true;
		for (int i = 0; i < permissions.length; i++) {
			if (ActivityCompat.checkSelfPermission(this,
					permissions[i]) != PackageManager.PERMISSION_GRANTED) {
				allGranted = false;
				break;
			}
		}

		if (allGranted) {
			if (AndroidUtils.DEBUG) {
				Log.d("Perms",
						"requestPermissions: allGranted, running " + runnableOnGrant);
			}
			if (runnableOnGrant != null) {
				runnableOnGrant.run();
			}
			return;
		}

		if (AndroidUtils.DEBUG) {
			Log.d("Perms", "requestPermissions: requesting "
					+ Arrays.toString(permissions) + " for " + runnableOnGrant);
		}
		requestPermissionRunnables.put(requestPermissionID, new Runnable[] {
			runnableOnGrant,
			runnableOnDeny
		});
		ActivityCompat.requestPermissions(this, permissions, requestPermissionID);
		requestPermissionID++;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions,
			int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		Runnable[] runnables = requestPermissionRunnables.get(requestCode);
		if (runnables != null) {
			requestPermissionRunnables.remove(requestCode);

			boolean allGranted = grantResults.length > 0;
			if (allGranted) {
				for (int i = 0; i < grantResults.length; i++) {

					if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
						allGranted = false;
						break;
					}
				}
			}

			if (AndroidUtils.DEBUG) {
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
}
