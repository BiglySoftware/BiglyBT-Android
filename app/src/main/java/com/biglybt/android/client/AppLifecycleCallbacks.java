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

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;

import com.biglybt.android.util.OnClearFromRecentService;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

/**
 * Created by TuxPaper on 5/10/18.
 */
class AppLifecycleCallbacks
	implements Application.ActivityLifecycleCallbacks
{
	private static final String TAG = "AppLifecycleCB";

	private int resumed;

	private int paused;

	private int started;

	private int stopped;

	private boolean isAppInited = false;

	private void appInit(@NonNull Context context) {
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
	public void onActivityCreated(@NonNull Activity activity,
			Bundle savedInstanceState) {
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			Log.d(TAG, "onActivityCreated " + activity);
		}
	}

	@Override
	public void onActivityDestroyed(@NonNull Activity activity) {
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			Log.d(TAG, "onActivityDestroyed " + activity);
		}
		if ("samsung".equals(Build.MANUFACTURER)) {

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
					&& Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
				try {
					Class<?> semEmergencyManagerClass = Class.forName(
							"com.samsung.android.emergencymode.SemEmergencyManager");
					Field sInstanceField = semEmergencyManagerClass.getDeclaredField(
							"sInstance");
					sInstanceField.setAccessible(true);
					Object sInstance = sInstanceField.get(null);
					Field mContextField = semEmergencyManagerClass.getDeclaredField(
							"mContext");
					mContextField.setAccessible(true);
					mContextField.set(sInstance, activity.getApplication());
				} catch (Throwable ignore) {
				}
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
					&& Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
				try {
					Object systemService = activity.getSystemService(Class.forName(
							"com.samsung.android.content.clipboard.SemClipboardManager"));
					Field mContext = systemService.getClass().getDeclaredField(
							"mContext");
					mContext.setAccessible(true);
					mContext.set(systemService, activity.getApplication());
				} catch (Throwable ignore) {
				}

				try {
					Object systemService = activity.getSystemService(
							Class.forName("com.samsung.android.knox.SemPersonaManager"));
					Field mContext = systemService.getClass().getDeclaredField(
							"mContext");
					mContext.setAccessible(true);
					mContext.set(systemService, activity.getApplication());
				} catch (Throwable ignore) {
				}
			}
		}

		removeActivityFromTransitionManager(activity);
	}

	@Override
	public void onActivityResumed(@NonNull Activity activity) {
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			Log.d(TAG, "onActivityResumed " + activity);
		}
		appInit(activity);
		resumed++;
	}

	@Override
	public void onActivityPaused(@NonNull Activity activity) {
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			Log.d(TAG, "onActivityPaused " + activity);
		}
		paused++;
	}

	@Override
	public void onActivitySaveInstanceState(@NonNull Activity activity,
			@NonNull Bundle outState) {
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			Log.d(TAG, "onActivitySaveInstanceState " + activity);
		}
	}

	@Override
	public void onActivityStarted(@NonNull Activity activity) {
		started++;
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			Log.d(TAG, "onActivityStarted " + activity + "; starts/stops=" + started
					+ "/" + stopped);
		}
	}

	@Override
	public void onActivityStopped(@NonNull Activity activity) {
		stopped++;
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			Log.d(TAG, "onActivityStopped " + activity + "; starts/stops=" + started
					+ "/" + stopped);
		}
	}

	public boolean isApplicationVisible() {
		return started > stopped;
	}

	public boolean isApplicationInForeground() {
		return resumed > paused;
	}

	private static void removeActivityFromTransitionManager(Activity activity) {
		if (Build.VERSION.SDK_INT < 21) {
			return;
		}
		try {
			Field runningTransitionsField = TransitionManager.class.getDeclaredField("sRunningTransitions");
			runningTransitionsField.setAccessible(true);
			//noinspection unchecked
			ThreadLocal<WeakReference<ArrayMap<ViewGroup, ArrayList<Transition>>>> runningTransitions
					= (ThreadLocal<WeakReference<ArrayMap<ViewGroup, ArrayList<Transition>>>>)
					runningTransitionsField.get(TransitionManager.class);
			if (runningTransitions.get() == null || runningTransitions.get().get() == null) {
				return;
			}
			ArrayMap<ViewGroup, ArrayList<Transition>> map = runningTransitions.get().get();
			View decorView = activity.getWindow().getDecorView();
			if (map.containsKey(decorView)) {
				map.remove(decorView);
			}
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
	}}