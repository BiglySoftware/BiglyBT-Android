/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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
 * 
 */

package com.vuze.android.remote;

import java.lang.reflect.Field;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ViewConfiguration;

public class VuzeRemoteApp
	extends Application
{
	private static final String TAG = "App";

	private static AppPreferences appPreferences;

	private static NetworkState networkState;

	private static Context applicationContext;

	@Override
	public void onCreate() {
		super.onCreate();

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "Application.onCreate");
		}
		applicationContext = getApplicationContext();

		// There was a bug in gms.analytics where creating an instance took forever
		// Putting first call on new thread didn't help much, but I'm leaving this
		// code here because it takes CPU cycles and block the app startup
		new Thread(new Runnable() {
			public void run() {
				VuzeEasyTracker.getInstance().registerExceptionReporter(
						applicationContext);
			}
		}, "VET Init").start();

		appPreferences = AppPreferences.createAppPreferences(applicationContext);
		networkState = new NetworkState(applicationContext);

		if (AndroidUtils.DEBUG) {
			DisplayMetrics dm = getContext().getResources().getDisplayMetrics();

			Log.d(TAG,
					"Display: " + dm.widthPixels + "px x " + dm.heightPixels + "px");
			Log.d(TAG, "Display: Using xdpi, " + pxToDpX(dm.widthPixels) + "dp x "
					+ pxToDpY(dm.heightPixels) + "dp");
			Log.d(TAG, "Display: Using dm.density, " + AndroidUtilsUI.pxToDp(
					dm.widthPixels) + "dp x "
					+ AndroidUtilsUI.pxToDp(dm.heightPixels) + "dp");
			Log.d(TAG, "Display: Using dm.densityDpi, " + convertPixelsToDp(dm.widthPixels) + "dp x "
					+ convertPixelsToDp(dm.heightPixels) + "dp");
		}

		appPreferences.setNumOpens(appPreferences.getNumOpens() + 1);

		// Common hack to always show overflow icon on actionbar if menu has overflow
		try {
			ViewConfiguration config = ViewConfiguration.get(this);
			Field menuKeyField = ViewConfiguration.class.getDeclaredField(
					"sHasPermanentMenuKey");

			if (menuKeyField != null) {
				menuKeyField.setAccessible(true);
				menuKeyField.setBoolean(config, false);
			}
		} catch (Exception e) {
			// presumably, not relevant
		}
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	@Override
	public void onTrimMemory(int level) {
		super.onTrimMemory(level);
		switch (level) {
			case TRIM_MEMORY_UI_HIDDEN: // not really a low memory event
				break;
			case TRIM_MEMORY_BACKGROUND: // app moved to background
				break;
			case TRIM_MEMORY_MODERATE:
				// app in middle of background list 
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "onTrimMemory Moderate");
				}
				SessionInfoManager.clearTorrentFilesCaches(true);
				break;
			case TRIM_MEMORY_COMPLETE:
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "onTrimMemory Complete");
				}
				// app next to be killed unless more mem found
				SessionInfoManager.clearTorrentCaches(false); // clear all
				break;
			case TRIM_MEMORY_RUNNING_MODERATE:
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "onTrimMemory RunningModerate");
				}
				SessionInfoManager.clearTorrentCaches(true); // clear all except current
				break;
			case TRIM_MEMORY_RUNNING_LOW: // Low memory
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "onTrimMemory RunningLow");
				}
				SessionInfoManager.clearTorrentCaches(true); // clear all except current
				SessionInfoManager.clearTorrentFilesCaches(true); // clear all except last file
				break;
			case TRIM_MEMORY_RUNNING_CRITICAL:
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "onTrimMemory RunningCritical");
				}
				SessionInfoManager.clearTorrentCaches(true); // clear all except current
				SessionInfoManager.clearTorrentFilesCaches(true); // clear all except last file
				break;
			default:
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "onTrimMemory " + level);
				}
		}
	}

	@Override
	public void onLowMemory() {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onLowMemory");
		}
		SessionInfoManager.clearTorrentCaches(false);
		super.onLowMemory();
	}

	public int pxToDpX(int px) {
		DisplayMetrics dm = getResources().getDisplayMetrics();

		int dp = Math.round(px / (dm.xdpi / DisplayMetrics.DENSITY_DEFAULT));
		return dp;
	}

	public int pxToDpY(int py) {
		DisplayMetrics dm = getResources().getDisplayMetrics();

		int dp = Math.round(py / (dm.ydpi / DisplayMetrics.DENSITY_DEFAULT));
		return dp;
	}

	public float convertPixelsToDp(float px){
		DisplayMetrics dm = getResources().getDisplayMetrics();
		float dp = px / (dm.densityDpi / 160f);
		return Math.round(dp);
	}

	@Override
	public void onTerminate() {
		// NOTE: This is never called except in emulation!
		networkState.dipose();

		super.onTerminate();
	}

	public static AppPreferences getAppPreferences() {
		return appPreferences;
	}

	public static NetworkState getNetworkState() {
		return networkState;
	}

	public static Context getContext() {
		return applicationContext;
	}
}
