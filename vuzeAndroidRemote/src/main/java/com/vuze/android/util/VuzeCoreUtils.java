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

package com.vuze.android.util;

import java.util.HashMap;
import java.util.Map;

import com.vuze.android.remote.*;
import com.vuze.android.remote.service.VuzeServiceInit;
import com.vuze.android.remote.service.VuzeServiceInitImpl;
import com.vuze.android.remote.session.RemoteProfile;
import com.vuze.android.remote.session.Session;
import com.vuze.android.remote.session.SessionManager;
import com.vuze.android.widget.CustomToast;
import com.vuze.util.RunnableWithObject;
import com.vuze.util.Thunk;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

/**
 * Created by TuxPaper on 1/30/17.
 */

public class VuzeCoreUtils
{
	private static final String TAG = "VuzeCoreUtils";

	@Thunk
	static VuzeServiceInit vuzeServiceInit = null;

	@Thunk
	static boolean vuzeCoreStarted = false;

	private static Boolean isCoreAllowed = null;

	public static void detachCore() {
		if (vuzeServiceInit == null) {
			return;
		}
		try {
			vuzeServiceInit.detachCore();
			vuzeServiceInit = null;
			vuzeCoreStarted = false;
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public static boolean isCoreAllowed() {
		if (isCoreAllowed == null) {
			try {
				@SuppressWarnings("UnusedAssignment")
				Class<?> claVuzeService = Class.forName(
						"com.vuze.android.remote.service.VuzeService");
				isCoreAllowed = true;
			} catch (ClassNotFoundException e) {
				isCoreAllowed = false;
			}
		}
		return isCoreAllowed;
	}

	public static boolean isCoreStarted() {
		return vuzeCoreStarted;
	}

	public static void shutdownCoreService() {
		if (vuzeServiceInit != null) {
			try {
				vuzeServiceInit.stopService();
			} catch (Throwable t) {
				Log.e(TAG, "stopService: ", t);
			}
		}
	}

	public static boolean startVuzeCoreService() {
		if (!isCoreAllowed()) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "initMainApp: Not starting core");
			}
			return false;
		}

		// Start VuzeCore
		try {
			if (AndroidUtils.DEBUG) {
				if (vuzeCoreStarted) {
					Log.d(TAG, "onCreate: Start VuzeService (already started)");
				} else {
					Log.d(TAG, "onCreate: Start VuzeService "
							+ AndroidUtils.getCompressedStackTrace());
				}
			}
			if (vuzeServiceInit == null) {
				vuzeServiceInit = createVuzeServiceInit();
			}

			try {
				vuzeServiceInit.powerUp();
			} catch (Throwable t) {
				Log.e(TAG, "powerUp: ", t);
				isCoreAllowed = false;
				vuzeServiceInit = null;
				return false;
			}
			return true;
		} catch (Throwable t) {
			Log.e(TAG, "createCore: ", t);
			vuzeServiceInit = null;
			isCoreAllowed = false;
		}
		return false;
	}

	private static VuzeServiceInit createVuzeServiceInit() {
		Map<String, Runnable> mapListeners = new HashMap<>();
		mapListeners.put("onAddedListener", new RunnableWithObject() {
			@Override
			public void run() {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "onAddedListener " + vuzeServiceInit);
				}
				if (!(object instanceof String)) {
					return;
				}

				String state = (String) object;
				if (state.equals("stopping")) {
					Session coreSession = SessionManager.findCoreSession();
					Activity activity = coreSession == null ? null
							: coreSession.getCurrentActivity();
					if (activity != null && !activity.isFinishing()) {
						AndroidUtilsUI.showConnectionError(activity,
								"Can't connect while Vuze Core is shutting down", false);
					} else {
						CustomToast.showText(
								"Can't connect while Vuze Core is shutting down",
								Toast.LENGTH_LONG);
						if (coreSession != null) {
							SessionManager.removeSession(
									coreSession.getRemoteProfile().getID());
						}
					}
				} else if (state.equals("ready-to-start")) {
					CustomToast.showText(R.string.toast_core_starting, Toast.LENGTH_LONG);
				}
			}
		});

		mapListeners.put("onCoreStarted", new Runnable() {
			@Override
			public void run() {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Core Started " + vuzeServiceInit);
				}
				if (vuzeServiceInit != null) {
					vuzeCoreStarted = true;
				}
			}
		});
		mapListeners.put("onCoreStopping", new Runnable() {
			@Override
			public void run() {
				// Core Stopped/Stopping
				Session currentVisibleSession = SessionManager.getCurrentVisibleSession();
				if (AndroidUtils.DEBUG) {
					Log.d(TAG,
							"Core Stopped, currentVisibleSession=" + currentVisibleSession);
				}
				vuzeCoreStarted = false;
				vuzeServiceInit = null;
				if (currentVisibleSession == null) {
					return;
				}
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Core Stopped, currentVisibleSession.currentActivity="
							+ currentVisibleSession.getCurrentActivity());
				}
				RemoteProfile remoteProfile = currentVisibleSession.getRemoteProfile();
				if (remoteProfile.getRemoteType() == RemoteProfile.TYPE_CORE) {
					SessionManager.removeSession(remoteProfile.getID());
				}
			}
		});
		mapListeners.put("onCoreRestarting", new Runnable() {
			@Override
			public void run() {
				// Core Restarting
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Core Restarting");
				}
				vuzeCoreStarted = false;
			}
		});
		return new VuzeServiceInitImpl(VuzeRemoteApp.getContext(), mapListeners);
	}

	// TODO: Tell users some status progress
	public static void waitForCore(final Activity activity, int maxMS) {
		if (AndroidUtilsUI.isUIThread()) {
			Log.e(TAG, "waitForCore: ON UI THREAD for waitForCore "
					+ AndroidUtils.getCompressedStackTrace());
		}
		if (!startVuzeCoreService()) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "waitForCore: No oVuzeService");
			}
			return;
		}

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "waitForCore ");
		}

		int maxCycles = maxMS / 100;
		int i = 0;
		while (!vuzeCoreStarted && i++ < maxCycles) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException ignore) {
			}
		}
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "waitForCore: Core started (" + i + " of " + maxCycles + ")");
		}
	}
}
