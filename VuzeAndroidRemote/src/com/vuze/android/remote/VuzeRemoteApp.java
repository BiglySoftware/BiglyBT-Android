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

import android.annotation.TargetApi;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;

import com.google.analytics.tracking.android.ExceptionParser;
import com.google.analytics.tracking.android.ExceptionReporter;
import com.google.analytics.tracking.android.GAServiceManager;

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

		ExceptionReporter myHandler = new ExceptionReporter(
				VuzeEasyTracker.getInstance().getTracker(),
				GAServiceManager.getInstance(),
				Thread.getDefaultUncaughtExceptionHandler(), applicationContext);
		myHandler.setExceptionParser(new ExceptionParser() {
			@Override
			public String getDescription(String threadName, Throwable t) {
				String s = "*" + t.getClass().getSimpleName() + " "
						+ AndroidUtils.getCompressedStackTrace(t, 0, 9);
				return s;
			}
		});
		Thread.setDefaultUncaughtExceptionHandler(myHandler);

		appPreferences = AppPreferences.createAppPreferences(applicationContext);
		networkState = new NetworkState(applicationContext);

		if (AndroidUtils.DEBUG) {
			DisplayMetrics dm = getContext().getResources().getDisplayMetrics();

			System.out.println(dm.widthPixels + "px x " + dm.heightPixels + "px");
			System.out.println(pxToDp(dm.widthPixels) + "dp x "
					+ pxToDp(dm.heightPixels) + "dp");
		}

		appPreferences.setNumOpens(appPreferences.getNumOpens() + 1);

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

	public int pxToDp(int px) {
		DisplayMetrics dm = getContext().getResources().getDisplayMetrics();

		int dp = Math.round(px / (dm.xdpi / DisplayMetrics.DENSITY_DEFAULT));
		return dp;
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
