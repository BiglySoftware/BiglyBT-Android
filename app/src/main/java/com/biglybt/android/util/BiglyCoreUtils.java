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

package com.biglybt.android.util;

import android.util.Log;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.biglybt.android.client.*;
import com.biglybt.android.client.service.BiglyBTServiceInit;
import com.biglybt.android.client.service.BiglyBTServiceInitImpl;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.android.widget.CustomToast;
import com.biglybt.util.RunnableWithObject;
import com.biglybt.util.Thunk;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by TuxPaper on 1/30/17.
 */

public class BiglyCoreUtils
{
	private static final String TAG = "BiglyCoreUtils";

	@Thunk
	static BiglyBTServiceInit biglyBTServiceInit = null;

	@Thunk
	static boolean biglyBTCoreStarted = false;

	private static Boolean isCoreAllowed = null;

	@Thunk
	static Map<String, Runnable> currentMapListeners;

	public static void detachCore() {
		if (biglyBTServiceInit == null) {
			return;
		}
		try {
			biglyBTServiceInit.detachCore();
			biglyBTServiceInit = null;
			biglyBTCoreStarted = false;
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public static boolean isCoreAllowed() {
		if (isCoreAllowed == null) {
			try {
				@SuppressWarnings({
						"UnusedAssignment",
						"unused"
				})
				Class<?> claBiglyBTService = Class.forName(
						"com.biglybt.android.client.service.BiglyBTService");
				isCoreAllowed = true;
			} catch (ClassNotFoundException e) {
				isCoreAllowed = false;
			}
		}
		return isCoreAllowed;
	}

	public static boolean isCoreStarted() {
		return biglyBTCoreStarted;
	}

	public static void shutdownCoreService() {
		if (biglyBTServiceInit != null && biglyBTCoreStarted) {
			try {
				biglyBTServiceInit.stopService();
			} catch (Throwable t) {
				Log.e(TAG, "stopService: ", t);
			}
		}
	}

	public static void restartCoreService() {
		if (biglyBTServiceInit != null && biglyBTCoreStarted) {
			try {
				biglyBTServiceInit.restartService();
			} catch (Throwable t) {
				Log.e(TAG, "restartService: ", t);
			}
		}
	}

	public static boolean startBiglyBTCoreService() {
		if (!isCoreAllowed()) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "initMainApp: Not starting core");
			}
			return false;
		}

		// Start Core
		try {
			if (AndroidUtils.DEBUG) {
				if (biglyBTCoreStarted) {
					Log.d(TAG, "onCreate: Start BiglyBTService (already started)");
				} else {
					Log.d(TAG, "onCreate: Start BiglyBTService "
							+ AndroidUtils.getCompressedStackTrace());
				}
			}
			if (biglyBTServiceInit == null) {
				biglyBTServiceInit = createBiglyBTServiceInit();
			}

			try {
				biglyBTServiceInit.powerUp();
			} catch (Throwable t) {
				Log.e(TAG, "powerUp: ", t);
				isCoreAllowed = false;
				biglyBTServiceInit = null;
				return false;
			}
			return true;
		} catch (Throwable t) {
			Log.e(TAG, "createCore: ", t);
			biglyBTServiceInit = null;
			isCoreAllowed = false;
		}
		return false;
	}

	private static BiglyBTServiceInit createBiglyBTServiceInit() {
		Map<String, Runnable> mapListeners = new HashMap<>();
		if (currentMapListeners != null) {
			currentMapListeners.clear();
		}
		currentMapListeners = mapListeners;
		mapListeners.put("onAddedListener", new RunnableWithObject() {
			@Override
			public void run() {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "onAddedListener " + biglyBTServiceInit + " " + object);
				}
				if (!(object instanceof String)) {
					return;
				}

				String state = (String) object;
				if ("stopping".equals(state)) {
					Session coreSession = SessionManager.findCoreSession();
					FragmentActivity activity = coreSession == null ? null
							: coreSession.getCurrentActivity();
					if (activity != null && !activity.isFinishing()) {
						AndroidUtilsUI.showConnectionError(activity,
								"Can't connect while BiglyBT Core is shutting down", false);
					} else {
						CustomToast.showText(
								"Can't connect while BiglyBT Core is shutting down",
								Toast.LENGTH_LONG);
						if (coreSession != null) {
							SessionManager.removeSession(
									coreSession.getRemoteProfile().getID(), true);
						}
					}
				} else if ("restarting".equals(state)) {
					CustomToast.showText(R.string.toast_core_restarting,
							Toast.LENGTH_LONG);
				} else if ("ready-to-start".equals(state)) {
					CustomToast.showText(R.string.toast_core_starting, Toast.LENGTH_LONG);
				}
			}
		});

		mapListeners.put("onCoreStarted", () -> {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "Core Started " + biglyBTServiceInit);
			}
			if (biglyBTServiceInit != null) {
				biglyBTCoreStarted = true;
			}
		});
		mapListeners.put("onCoreStopping", () -> {
			// Core Stopped/Stopping
			Session coreSession = SessionManager.findCoreSession();
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "Core Stopped, coreSession=" + coreSession);
			}
			biglyBTCoreStarted = false;
			if (coreSession == null) {
				return;
			}
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "Core Stopped, coreSession.currentActivity="
						+ coreSession.getCurrentActivity());
			}
			SessionManager.removeSession(coreSession.getRemoteProfile().getID(),
					true);
		});
		mapListeners.put("onCoreRestarting", () -> {
			// Core Restarting
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "Core Restarting " + biglyBTServiceInit);
			}
			CustomToast.showText(R.string.toast_core_restarting, Toast.LENGTH_LONG);
			biglyBTCoreStarted = false;
		});
		mapListeners.put("onServiceDestroyed", () -> {
			biglyBTCoreStarted = false;
			biglyBTServiceInit = null;
		});
		return new BiglyBTServiceInitImpl(BiglyBTApp.getContext(), mapListeners);
	}

	public static void waitForCore() {
		waitForCore(60000);
	}

	// TODO: Tell users some status progress (create a listener system so UI
	//       can listen and do UI stuff)
	public static void waitForCore(int maxMS) {
		if (AndroidUtilsUI.isUIThread()) {
			Log.e(TAG, "waitForCore: ON UI THREAD for waitForCore "
					+ AndroidUtils.getCompressedStackTrace());
		}
		if (!startBiglyBTCoreService()) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "waitForCore: No oBiglyBTService");
			}
			return;
		}

		if (biglyBTCoreStarted) {
			return;
		}

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "waitForCore " + AndroidUtils.getCompressedStackTrace());
		}

		int maxCycles = maxMS / 100;
		int i = 0;
		while (!biglyBTCoreStarted && i++ < maxCycles) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException ignore) {
			}
			if (biglyBTServiceInit == null) {
				startBiglyBTCoreService();
			}
		}
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "waitForCore: Core started? " + biglyBTCoreStarted + " ("
					+ (i * 100) + "ms)");
		}
	}

	public static BiglyBTServiceInit getBiglyBTService() {
		return biglyBTServiceInit;
	}
}
