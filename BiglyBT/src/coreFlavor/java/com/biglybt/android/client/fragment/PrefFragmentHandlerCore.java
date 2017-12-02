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

package com.biglybt.android.client.fragment;

import com.biglybt.android.client.*;
import com.biglybt.android.client.activity.SessionActivity;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionSettings;

import android.content.pm.PackageManager;
import android.support.annotation.Nullable;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;

import net.grandcentrix.tray.TrayPreferences;

/**
 * Created by TuxPaper on 10/23/17.
 */

public class PrefFragmentHandlerCore
	extends PrefFragmentHandler
{
	public final static String KEY_ALLOW_MOBILE_DATA = "core_data_transfer_over_mobile_data_plan";

	private static final String KEY_AUTO_START = "core_auto_start_on_boot";

	private static final String KEY_DISABLE_SLEEP = "core_keep_cpu_awake";

	private static final String KEY_ONLY_PLUGGEDIN = "core_only_transfer_data_when_plugged_in";

	public final static String KEY_ALLOW_LAN_ACCESS = "core_allow_lan_access";

	public PrefFragmentHandlerCore(SessionActivity activity) {
		super(activity);
	}

	@Override
	public boolean onPreferenceTreeClick(final Preference preference) {
		switch (preference.getKey()) {

			case KEY_ALLOW_MOBILE_DATA: {
				AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
				final TrayPreferences preferences = appPreferences.getPreferences();
				preferences.put(CorePrefs.PREF_CORE_ALLOWCELLDATA,
						((SwitchPreference) preference).isChecked());
				return true;
			}

			case KEY_AUTO_START: {
				AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
				final TrayPreferences preferences = appPreferences.getPreferences();
				boolean b = ((SwitchPreference) preference).isChecked();
				preferences.put(CorePrefs.PREF_CORE_AUTOSTART, b);
				if (b) {
					AndroidUtilsUI.requestPermissions(activity, new String[] {
						android.Manifest.permission.RECEIVE_BOOT_COMPLETED
					}, null, new Runnable() {
						@Override
						public void run() {
							AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
							final TrayPreferences preferences = appPreferences.getPreferences();
							preferences.put(CorePrefs.PREF_CORE_AUTOSTART, false);

							fillDataStore();
						}
					});
				}
				return true;
			}

			case KEY_DISABLE_SLEEP: {
				AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
				final TrayPreferences preferences = appPreferences.getPreferences();
				boolean b = ((SwitchPreference) preference).isChecked();
				preferences.put(CorePrefs.PREF_CORE_DISABLESLEEP, b);

				if (b) {
					AndroidUtilsUI.requestPermissions(activity, new String[] {
						android.Manifest.permission.WAKE_LOCK
					}, null, new Runnable() {
						@Override
						public void run() {
							AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
							final TrayPreferences preferences = appPreferences.getPreferences();
							preferences.put(CorePrefs.PREF_CORE_DISABLESLEEP, false);

							fillDataStore();
						}
					});
				}
				return true;
			}

			case KEY_ONLY_PLUGGEDIN: {
				AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
				final TrayPreferences preferences = appPreferences.getPreferences();
				preferences.put(CorePrefs.PREF_CORE_ONLYPLUGGEDIN,
						((SwitchPreference) preference).isChecked());
				return true;
			}

			case KEY_ALLOW_LAN_ACCESS: {
				AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
				final TrayPreferences preferences = appPreferences.getPreferences();
				preferences.put(CorePrefs.PREF_CORE_ALLOWLANACCESS,
						((SwitchPreference) preference).isChecked());
				return true;
			}
		}
		return super.onPreferenceTreeClick(preference);
	}

	@Override
	public void fillDataStore() {
		super.fillDataStore();

		Session session = activity.getSession();
		if (session == null) {
			return;
		}
		SessionSettings sessionSettings = session.getSessionSettingsClone();
		if (sessionSettings == null) {
			return;
		}

		CorePrefs corePrefs = new CorePrefs();

		SwitchPreference preference;

		preference = (SwitchPreference) findPreference(KEY_ALLOW_MOBILE_DATA);
		if (BiglyBTApp.getNetworkState().hasMobileDataCapability()) {
			preference.setVisible(true);
			Boolean allowCellData = corePrefs.getPrefAllowCellData();
			preference.setChecked(allowCellData);
		} else {
			preference.setVisible(false);
		}

		Boolean autoStart = corePrefs.getPrefAutoStart();
		((SwitchPreference) findPreference(KEY_AUTO_START)).setChecked(autoStart);

		boolean canShowSleep = activity.getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_WIFI)
				&& AndroidUtils.hasPermisssion(BiglyBTApp.getContext(),
						android.Manifest.permission.WAKE_LOCK);
		preference = (SwitchPreference) findPreference(KEY_DISABLE_SLEEP);
		preference.setVisible(canShowSleep);
		if (canShowSleep) {
			Boolean disableSleep = corePrefs.getPrefDisableSleep();
			preference.setChecked(disableSleep);
		}

		// Would be nice to have a way to detect a device that doesn't have a battery
		// Unfortunately, TV's return present=true for BatteryManager, and no
		// flag to indicate no-battery (plugged=BATTERY_PLUGGED_AC can be a
		// device temporarily attached to wall USB charger)
		boolean canShowPlug = !AndroidUtils.isTV();
		preference = (SwitchPreference) findPreference(KEY_ONLY_PLUGGEDIN);
		preference.setVisible(canShowPlug);
		if (canShowPlug) {
			Boolean onlyPluggedIn = corePrefs.getPrefOnlyPluggedIn();
			preference.setChecked(onlyPluggedIn);
		}

		Boolean allowLANAccess = corePrefs.getPrefAllowLANAccess();
		((SwitchPreference) findPreference(KEY_ALLOW_LAN_ACCESS)).setChecked(
				allowLANAccess);
	}

	@Override
	public void onNumberPickerChange(@Nullable String callbackID, int val) {
		super.onNumberPickerChange(callbackID, val);
	}

	@Override
	public Preference findPreference(String key) {
		return super.findPreference(key);
	}
}
