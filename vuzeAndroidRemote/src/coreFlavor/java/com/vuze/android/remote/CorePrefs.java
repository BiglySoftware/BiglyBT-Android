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

import com.vuze.util.Thunk;

import android.content.SharedPreferences;
import android.util.Log;

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

	@Thunk
	static final String TAG = "VuzeCorePrefs";

	public interface CorePrefsChangedListener
	{
		void corePrefAutoStartChanged(boolean autoStart);

		void corePrefAllowCellDataChanged(boolean allowCellData);

		void corePrefDisableSleepChanged(boolean disableSleep);

		void corePrefOnlyPluggedInChanged(boolean onlyPluggedIn);
	}

	private CorePrefsChangedListener changedListener;

	private Boolean prefAllowCellData = null;

	private Boolean prefDisableSleep = null;

	private Boolean prefAutoStart = null;

	private Boolean prefOnlyPluggedIn = null;


	public CorePrefs() {
			SharedPreferences sharedPreferences = VuzeRemoteApp.getAppPreferences()
				.getSharedPreferences();
			sharedPreferences.registerOnSharedPreferenceChangeListener(this);
			loadPref(sharedPreferences, PREF_CORE_ALLOWCELLDATA);
			loadPref(sharedPreferences, PREF_CORE_ONLYPLUGGEDIN);
			loadPref(sharedPreferences, PREF_CORE_AUTOSTART);
			loadPref(sharedPreferences, PREF_CORE_DISABLESLEEP);
	}

	public void setChangedListener(CorePrefsChangedListener changedListener) {
		this.changedListener = changedListener;
		if (changedListener != null) {
			changedListener.corePrefAllowCellDataChanged(prefAllowCellData);
			changedListener.corePrefOnlyPluggedInChanged(prefOnlyPluggedIn);
			changedListener.corePrefDisableSleepChanged(prefDisableSleep);
			changedListener.corePrefAutoStartChanged(prefAutoStart);
		}
	}

	public Boolean getPrefAllowCellData() {
		return prefAllowCellData;
	}

	public Boolean getPrefDisableSleep() {
		return prefDisableSleep;
	}

	public Boolean getPrefOnlyPluggedIn() {
		return prefOnlyPluggedIn;
	}

	public Boolean getPrefAutoStart() {
		return prefAutoStart;
	}

	private void setOnlyPluggedIn(boolean b, boolean trigger) {
		if (prefOnlyPluggedIn == null || b != prefOnlyPluggedIn) {
			prefOnlyPluggedIn = b;
			if (changedListener != null) {
				changedListener.corePrefOnlyPluggedInChanged(b);
			}
		}
	}

	private void setDisableSleep(boolean b, boolean trigger) {
		if (prefDisableSleep == null || b != prefDisableSleep) {
			prefDisableSleep = b;
			if (changedListener != null) {
				changedListener.corePrefDisableSleepChanged(b);
			}
		}
	}

	private void setAutoStart(boolean b, boolean trigger) {
		if (prefAutoStart == null || b != prefAutoStart) {
			prefAutoStart = b;
			if (changedListener != null) {
				changedListener.corePrefAutoStartChanged(b);
			}
		}
	}

	private void setAllowCellData(boolean b, boolean trigger) {
		if (prefAllowCellData == null || b != prefAllowCellData) {
			prefAllowCellData = b;
			if (changedListener != null) {
				changedListener.corePrefAllowCellDataChanged(b);
			}
		}
	}

	private void loadPref(SharedPreferences sharedPreferences, String key) {
		if (CorePrefs.DEBUG_CORE) {
			Log.d(TAG, "loadPref: " + key);
		}

		if (key.equals(PREF_CORE_ALLOWCELLDATA)) {
			setAllowCellData(
				sharedPreferences.getBoolean(PREF_CORE_ALLOWCELLDATA, false),
				true);
		}
		if (key.equals(PREF_CORE_AUTOSTART)) {
			setAutoStart(sharedPreferences.getBoolean(PREF_CORE_AUTOSTART, true),
				true);
		}
		if (key.equals(PREF_CORE_DISABLESLEEP)) {
			setDisableSleep(
				sharedPreferences.getBoolean(PREF_CORE_DISABLESLEEP, true), true);
		}
		if (key.equals(PREF_CORE_ONLYPLUGGEDIN)) {
			setOnlyPluggedIn(
				sharedPreferences.getBoolean(PREF_CORE_ONLYPLUGGEDIN, false),
				true);
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
		String key) {
		loadPref(sharedPreferences, key);
	}


}
