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

import java.util.Collection;

import com.biglybt.util.Thunk;

import android.util.Log;

import net.grandcentrix.tray.TrayPreferences;
import net.grandcentrix.tray.core.OnTrayPreferenceChangeListener;
import net.grandcentrix.tray.core.TrayItem;

/**
 * Created by TuxPaper on 4/5/16.
 */
public class CorePrefs
	implements OnTrayPreferenceChangeListener
{
	public static final boolean DEBUG_CORE = AndroidUtils.DEBUG;

	public static final String PREF_CORE_AUTOSTART = "core_autostart";

	public static final String PREF_CORE_ALLOWCELLDATA = "core_allowcelldata";

	public static final String PREF_CORE_DISABLESLEEP = "core_disablesleep";

	public static final String PREF_CORE_ONLYPLUGGEDIN = "core_onlypluggedin";

	public static final String PREF_CORE_ALLOWLANACCESS = "core_allowlanaccess";

	@Thunk
	static final String TAG = "BiglyBTCorePrefs";

	public interface CorePrefsChangedListener
	{
		void corePrefAutoStartChanged(boolean autoStart);

		void corePrefAllowCellDataChanged(boolean allowCellData);

		void corePrefDisableSleepChanged(boolean disableSleep);

		void corePrefOnlyPluggedInChanged(boolean onlyPluggedIn);

		void corePrefAllowLANAccess(boolean allowLANAccess);
	}

	private CorePrefsChangedListener changedListener;

	private Boolean prefAllowCellData = null;

	private Boolean prefDisableSleep = null;

	private Boolean prefAutoStart = null;

	private Boolean prefOnlyPluggedIn = null;

	private Boolean prefAllowLANAccess = null;

	public CorePrefs() {
		final TrayPreferences preferences = BiglyBTApp.getAppPreferences().preferences;
		preferences.registerOnTrayPreferenceChangeListener(this);
		loadPref(preferences, PREF_CORE_ALLOWCELLDATA);
		loadPref(preferences, PREF_CORE_ONLYPLUGGEDIN);
		loadPref(preferences, PREF_CORE_AUTOSTART);
		loadPref(preferences, PREF_CORE_DISABLESLEEP);
		loadPref(preferences, PREF_CORE_ALLOWLANACCESS);
	}

	public void setChangedListener(CorePrefsChangedListener changedListener) {
		this.changedListener = changedListener;
		if (changedListener != null) {
			changedListener.corePrefAllowCellDataChanged(prefAllowCellData);
			changedListener.corePrefOnlyPluggedInChanged(prefOnlyPluggedIn);
			changedListener.corePrefDisableSleepChanged(prefDisableSleep);
			changedListener.corePrefAutoStartChanged(prefAutoStart);
			changedListener.corePrefAllowLANAccess(prefAllowLANAccess);
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

	public Boolean getPrefAllowLANAccess() {
		return prefAllowLANAccess;
	}

	private void setOnlyPluggedIn(boolean b) {
		if (prefOnlyPluggedIn == null || b != prefOnlyPluggedIn) {
			prefOnlyPluggedIn = b;
			if (changedListener != null) {
				changedListener.corePrefOnlyPluggedInChanged(b);
			}
		}
	}

	private void setDisableSleep(boolean b) {
		if (prefDisableSleep == null || b != prefDisableSleep) {
			prefDisableSleep = b;
			if (changedListener != null) {
				changedListener.corePrefDisableSleepChanged(b);
			}
		}
	}

	private void setAutoStart(boolean b) {
		if (prefAutoStart == null || b != prefAutoStart) {
			prefAutoStart = b;
			if (changedListener != null) {
				changedListener.corePrefAutoStartChanged(b);
			}
		}
	}

	private void setAllowCellData(boolean b) {
		if (prefAllowCellData == null || b != prefAllowCellData) {
			prefAllowCellData = b;
			if (changedListener != null) {
				changedListener.corePrefAllowCellDataChanged(b);
			}
		}
	}

	public void setAllowLANAccess(Boolean b) {
		if (prefAllowLANAccess == null || b != prefAllowLANAccess) {
			prefAllowLANAccess = b;
			if (changedListener != null) {
				changedListener.corePrefAllowLANAccess(b);
			}
		}
	}

	private void loadPref(TrayPreferences preferences, String key) {
		if (CorePrefs.DEBUG_CORE) {
			Log.d(TAG, "loadPref: " + key + ": " + preferences.getPref(key));
		}

		if (key.equals(PREF_CORE_ALLOWCELLDATA)) {
			setAllowCellData(preferences.getBoolean(PREF_CORE_ALLOWCELLDATA, false));
		}
		if (key.equals(PREF_CORE_AUTOSTART)) {
			setAutoStart(preferences.getBoolean(PREF_CORE_AUTOSTART, true));
		}
		if (key.equals(PREF_CORE_DISABLESLEEP)) {
			setDisableSleep(preferences.getBoolean(PREF_CORE_DISABLESLEEP, true));
		}
		if (key.equals(PREF_CORE_ONLYPLUGGEDIN)) {
			setOnlyPluggedIn(preferences.getBoolean(PREF_CORE_ONLYPLUGGEDIN, false));
		}
		if (key.equals(PREF_CORE_ALLOWLANACCESS)) {
			setAllowLANAccess(
					preferences.getBoolean(PREF_CORE_ALLOWLANACCESS, false));
		}
	}

	@Override
	public void onTrayPreferenceChanged(Collection<TrayItem> items) {
		final TrayPreferences preferences = BiglyBTApp.getAppPreferences().preferences;
		for (TrayItem item : items) {
			loadPref(preferences, item.key());
		}
	}

}
