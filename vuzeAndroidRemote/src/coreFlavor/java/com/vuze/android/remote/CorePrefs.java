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

import android.Manifest;
import android.content.*;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.aelitis.azureus.core.AzureusCore;
import com.vuze.android.remote.service.VuzeService;

/**
 * Created by TuxPaper on 4/5/16.
 */
public class CorePrefs
	implements SharedPreferences.OnSharedPreferenceChangeListener
{
	public static final boolean DEBUG_CORE = AndroidUtils.DEBUG;

	public static final String PREF_CORE_AUTOSTART = "core_autostart";

	public static final String PREF_CORE_ALLOWCELLDATA = "core_allowcelldata";

	public static final String PREF_CORE_DISABLESLEEP = "core_disablesleep";

	public static final String PREF_CORE_ONLYPLUGGEDIN = "core_onlypluggedin";

	static final String TAG = "VuzeCorePrefs";

	private static Boolean prefAllowCellData = null;

	private static Boolean prefDisableSleep = null;

	private static Boolean prefAutoStart = null;

	private static Boolean prefOnlyPluggedIn = null;

	private static final CorePrefs instance;

	private static WifiManager.WifiLock wifiLock;

	private static BroadcastReceiver batteryReceiver;

	static {
		instance = new CorePrefs();
		SharedPreferences sharedPreferences = VuzeRemoteApp.getAppPreferences().getSharedPreferences();
		sharedPreferences.registerOnSharedPreferenceChangeListener(instance);
		loadPref(sharedPreferences, PREF_CORE_ALLOWCELLDATA, true);
		loadPref(sharedPreferences, PREF_CORE_ONLYPLUGGEDIN, true);
		loadPref(sharedPreferences, PREF_CORE_AUTOSTART, true);
		loadPref(sharedPreferences, PREF_CORE_DISABLESLEEP, true);
	}

	public static Boolean getPrefAllowCellData() {
		return prefAllowCellData;
	}

	public static Boolean getPrefDisableSleep() {
		return prefDisableSleep;
	}

	public static Boolean getPrefOnlyPluggedIn() {
		return prefOnlyPluggedIn;
	}

	public static Boolean getPrefAutoStart() {
		return prefAutoStart;
	}

	public static void setOnlyPluggedIn(boolean b, boolean trigger) {
		if (prefOnlyPluggedIn == null || b != prefOnlyPluggedIn) {
			prefOnlyPluggedIn = b;
			if (trigger) {
				if (prefOnlyPluggedIn) {
					enableBatteryMonitoring(VuzeRemoteApp.getContext());
				} else {
					disableBatteryMonitoring(VuzeRemoteApp.getContext());
				}
			}
		}
	}

	public static void setDisableSleep(boolean b, boolean trigger) {
		if (prefDisableSleep == null || b != prefDisableSleep) {
			prefDisableSleep = b;
			if (trigger) {
				adjustPowerLock();
			}
		}
	}

	public static void setAutoStart(boolean b, boolean trigger) {
		if (prefAutoStart == null || b != prefAutoStart) {
			prefAutoStart = b;
			// no triggering needed, on boot event, we check the pref
		}
	}

	public static void setAllowCellData(boolean b, boolean trigger) {
		if (prefAllowCellData == null || b != prefAllowCellData) {
			prefAllowCellData = b;
			if (trigger) {
				VuzeService instance = VuzeService.getInstance();
				if (instance != null) {
					instance.restartService();
				}
			}
		}
	}

	private static void loadPref(SharedPreferences sharedPreferences, String key,
			boolean trigger) {
		if (CorePrefs.DEBUG_CORE) {
			Log.d(TAG, "loadPref: " + key);
		}

		if (key.equals(PREF_CORE_ALLOWCELLDATA)) {
			setAllowCellData(
					sharedPreferences.getBoolean(PREF_CORE_ALLOWCELLDATA, false),
					trigger);
		}
		if (key.equals(PREF_CORE_AUTOSTART)) {
			setAutoStart(sharedPreferences.getBoolean(PREF_CORE_AUTOSTART, true),
					trigger);
		}
		if (key.equals(PREF_CORE_DISABLESLEEP)) {
			setDisableSleep(
					sharedPreferences.getBoolean(PREF_CORE_DISABLESLEEP, true), trigger);
		}
		if (key.equals(PREF_CORE_ONLYPLUGGEDIN)) {
			setOnlyPluggedIn(
					sharedPreferences.getBoolean(PREF_CORE_ONLYPLUGGEDIN, false),
					trigger);
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		loadPref(sharedPreferences, key, true);
	}

	private static void acquirePowerLock() {
		if (wifiLock == null || !wifiLock.isHeld()) {
			if (!AndroidUtils.hasPermisssion(VuzeRemoteApp.getContext(),
					Manifest.permission.WAKE_LOCK)) {
				if (CorePrefs.DEBUG_CORE) {
					Log.d(TAG, "No Permissions to access wake lock");
				}
				return;
			}
			WifiManager wifiManager = (WifiManager) VuzeRemoteApp.getContext().getSystemService(
					Context.WIFI_SERVICE);
			wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL,
					"vuze power lock");
			wifiLock.acquire();
			if (CorePrefs.DEBUG_CORE) {
				Log.d(TAG, "Wifi lock acquired");
			}

		}
	}

	public static void releasePowerLock() {
		if (wifiLock != null && wifiLock.isHeld()) {
			wifiLock.release();
			if (CorePrefs.DEBUG_CORE) {
				Log.d(TAG, "Wifi lock released");
			}

		}
	}

	public static void adjustPowerLock() {
		if (getPrefDisableSleep()) {
			acquirePowerLock();
		} else {
			releasePowerLock();
		}
	}

	public static void disableBatteryMonitoring(Context context) {
		if (batteryReceiver != null) {
			context.unregisterReceiver(batteryReceiver);
			batteryReceiver = null;
			if (CorePrefs.DEBUG_CORE) {
				Log.d(TAG, "disableBatteryMonitoring: ");
			}

		}
	}

	public static void enableBatteryMonitoring(Context context) {
		if (batteryReceiver != null) {
			return;
		}
		IntentFilter intentFilterConnected = new IntentFilter(
				Intent.ACTION_POWER_CONNECTED);
		IntentFilter intentFilterDisconnected = new IntentFilter(
				Intent.ACTION_POWER_DISCONNECTED);

		batteryReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				boolean isConnected = intent.getAction().equals(
						Intent.ACTION_POWER_CONNECTED);
				if (CorePrefs.DEBUG_CORE) {
					Log.d(TAG, "Battery connected? " + isConnected);
				}
				AzureusCore core = VuzeService.getCore();
				if (core == null) {
					if (CorePrefs.DEBUG_CORE) {
						Log.d(TAG, "Battery changed, but core not initialized yet");
					}

					return;
				}
				if (getPrefOnlyPluggedIn()) {
					VuzeService instance = VuzeService.getInstance();
					if (instance != null) {
						instance.checkForSleepModeChange();
					}
				}
			}
		};
		context.registerReceiver(batteryReceiver, intentFilterConnected);
		context.registerReceiver(batteryReceiver, intentFilterDisconnected);
		if (CorePrefs.DEBUG_CORE) {
			Log.d(TAG, "enableBatteryMonitoring: ");
		}

	}
}
