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

package com.biglybt.android.client;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;

import com.biglybt.android.util.OnClearFromRecentService;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

/**
 * Created by TuxPaper on 5/10/18.
 */
public class AppLifecycleCallbacks
	implements Application.ActivityLifecycleCallbacks
{
	private static final String TAG = "AppLifecycleCB";

	private int resumed;

	private int paused;

	private int started;

	private int stopped;

	private boolean isAppInited = false;

	private void appInit(Context context) {
		if (isAppInited) {
			return;
		}
		boolean canStart;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			// O doesn't let us startService when activity isn't visible
			// If the screensaver is on, and this activity is launched (behind it),
			// we get the following exception:
			// java.lang.IllegalStateException: Not allowed to start service Intent { cmp=com.biglybt.android.client/com.biglybt.android.util.OnClearFromRecentService }: app is in background uid ...
			// Solution is to try to startService on every Activity resume until
			// we have a visible one.
			ActivityManager.RunningAppProcessInfo appProcessInfo = new ActivityManager.RunningAppProcessInfo();
			ActivityManager.getMyMemoryState(appProcessInfo);
			canStart = (appProcessInfo.importance == IMPORTANCE_FOREGROUND
					|| appProcessInfo.importance == IMPORTANCE_VISIBLE);
			if (AndroidUtils.DEBUG_LIFECYCLE) {
				Log.d(TAG,
						"isVisible? " + canStart + "; i=" + appProcessInfo.importance);
			}
		} else {
			canStart = true;
		}
		if (canStart) {
			try {
				context.startService(new Intent(context.getApplicationContext(),
						OnClearFromRecentService.class));
				isAppInited = true;
			} catch (Throwable t) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, t.getMessage(), t);
				}
			}
		}
	}

	@Override
	public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			Log.d(TAG, "onActivityCreated " + activity);
		}
	}

	@Override
	public void onActivityDestroyed(Activity activity) {
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			Log.d(TAG, "onActivityDestroyed " + activity);
		}
	}

	@Override
	public void onActivityResumed(Activity activity) {
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			Log.d(TAG, "onActivityResumed " + activity);
		}
		appInit(activity);
		resumed++;
	}

	@Override
	public void onActivityPaused(Activity activity) {
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			Log.d(TAG, "onActivityPaused " + activity);
		}
		paused++;
	}

	@Override
	public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			Log.d(TAG, "onActivitySaveInstanceState " + activity);
		}
	}

	@Override
	public void onActivityStarted(Activity activity) {
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			Log.d(TAG, "onActivityStarted " + activity);
		}
		started++;
	}

	@Override
	public void onActivityStopped(Activity activity) {
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			Log.d(TAG, "onActivityStopped " + activity);
		}
		stopped++;
	}

	public boolean isApplicationVisible() {
		return started > stopped;
	}

	public boolean isApplicationInForeground() {
		return resumed > paused;
	}
}