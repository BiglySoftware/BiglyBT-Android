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
import com.biglybt.android.client.dialog.DialogFragmentNumberPicker;
import com.biglybt.android.client.rpc.RPC;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionSettings;

import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.support.annotation.Nullable;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.text.InputType;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import net.grandcentrix.tray.TrayPreferences;

/**
 * Created by TuxPaper on 10/23/17.
 */

public class PrefFragmentHandlerCore
	extends PrefFragmentHandler
	implements Preference.OnPreferenceChangeListener
{
	public final static String KEY_ALLOW_MOBILE_DATA = "core_data_transfer_over_mobile_data_plan";

	private static final String KEY_AUTO_START = "core_auto_start_on_boot";

	private static final String KEY_DISABLE_SLEEP = "core_keep_cpu_awake";

	private static final String KEY_ONLY_PLUGGEDIN = "core_only_transfer_data_when_plugged_in";

	public final static String KEY_ALLOW_LAN_ACCESS = "core_allow_lan_access";

	private static final String KEY_PROXY_ENABLED_TRACKER = "core_tracker_proxy_enabled";

	private static final String KEY_PROXY_ENABLED_PEER = "core_peer_proxy_enabled";

	private static final String KEY_PROXY_SCREEN = "ps_core_proxy";

	private static final String KEY_PROXY_TYPE = "core_proxy_type";

	private static final String KEY_PROXY_HOST = "core_proxy_host";

	private static final String KEY_PROXY_PORT = "core_proxy_port";

	private static final String KEY_PROXY_USER = "core_proxy_user";

	private static final String KEY_PROXY_PW = "core_proxy_pw";

	private CoreProxyPreferences proxyPrefs;

	public PrefFragmentHandlerCore(SessionActivity activity) {
		super(activity);
	}

	@Override
	public boolean onPreferenceTreeClick(final Preference preference) {
		final String key = preference.getKey();
		if (key == null) {
			return false;
		}
		switch (key) {

			case KEY_ALLOW_MOBILE_DATA: {
				TrayPreferences prefs = BiglyBTApp.getAppPreferences().getPreferences();
				prefs.put(CorePrefs.PREF_CORE_ALLOWCELLDATA,
						((SwitchPreference) preference).isChecked());
				return true;
			}

			case KEY_AUTO_START: {
				TrayPreferences prefs = BiglyBTApp.getAppPreferences().getPreferences();
				boolean b = ((SwitchPreference) preference).isChecked();
				prefs.put(CorePrefs.PREF_CORE_AUTOSTART, b);
				if (b) {
					AndroidUtilsUI.requestPermissions(activity, new String[] {
						android.Manifest.permission.RECEIVE_BOOT_COMPLETED
					}, null, new Runnable() {
						@Override
						public void run() {
							TrayPreferences prefs = BiglyBTApp.getAppPreferences().getPreferences();
							prefs.put(CorePrefs.PREF_CORE_AUTOSTART, false);

							fillDataStore();
						}
					});
				}
				return true;
			}

			case KEY_DISABLE_SLEEP: {
				TrayPreferences prefs = BiglyBTApp.getAppPreferences().getPreferences();
				boolean b = ((SwitchPreference) preference).isChecked();
				prefs.put(CorePrefs.PREF_CORE_DISABLESLEEP, b);

				if (b) {
					AndroidUtilsUI.requestPermissions(activity, new String[] {
						android.Manifest.permission.WAKE_LOCK
					}, null, new Runnable() {
						@Override
						public void run() {
							TrayPreferences prefs = BiglyBTApp.getAppPreferences().getPreferences();
							prefs.put(CorePrefs.PREF_CORE_DISABLESLEEP, false);

							fillDataStore();
						}
					});
				}
				return true;
			}

			case KEY_ONLY_PLUGGEDIN: {
				TrayPreferences prefs = BiglyBTApp.getAppPreferences().getPreferences();
				prefs.put(CorePrefs.PREF_CORE_ONLYPLUGGEDIN,
						((SwitchPreference) preference).isChecked());
				return true;
			}

			case KEY_ALLOW_LAN_ACCESS: {
				TrayPreferences prefs = BiglyBTApp.getAppPreferences().getPreferences();
				prefs.put(CorePrefs.PREF_CORE_ALLOWLANACCESS,
						((SwitchPreference) preference).isChecked());
				return true;
			}

			case KEY_PROXY_ENABLED_PEER: {
				proxyPrefs.proxyOutgoingPeers = ((SwitchPreference) preference).isChecked();
				fillProxyDataStore();
				return true;
			}

			case KEY_PROXY_ENABLED_TRACKER: {
				proxyPrefs.proxyTrackers = ((SwitchPreference) preference).isChecked();
				fillProxyDataStore();
				return true;
			}

			case KEY_PROXY_HOST: {
				AndroidUtilsUI.createTextBoxDialog(activity, R.string.proxy_host, 0,
						proxyPrefs.host, EditorInfo.IME_ACTION_DONE,
						new AndroidUtilsUI.OnTextBoxDialogClick() {
							@Override
							public void onClick(DialogInterface dialog, int which,
									EditText editText) {
								proxyPrefs.host = editText.getText().toString();
								fillDataStore();
							}
						}).show();
				return true;
			}

			case KEY_PROXY_PORT: {
				DialogFragmentNumberPicker.NumberPickerBuilder builder = new DialogFragmentNumberPicker.NumberPickerBuilder(
						activity.getSupportFragmentManager(), KEY_PROXY_PORT,
						proxyPrefs.port).setTitleId(R.string.proxy_port).setShowSpinner(
								false).setMin(1).setMax(65535);
				DialogFragmentNumberPicker.openDialog(builder);
				return true;
			}

			case KEY_PROXY_PW: {
				AndroidUtilsUI.createTextBoxDialog(activity, R.string.proxy_pw, 0,
						proxyPrefs.pw, EditorInfo.IME_ACTION_DONE,
						InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
						new AndroidUtilsUI.OnTextBoxDialogClick() {
							@Override
							public void onClick(DialogInterface dialog, int which,
									EditText editText) {
								proxyPrefs.pw = editText.getText().toString();
								fillDataStore();
							}
						}).show();
				return true;
			}

			case KEY_PROXY_USER: {
				AndroidUtilsUI.createTextBoxDialog(activity, R.string.proxy_user, 0,
						proxyPrefs.user, EditorInfo.IME_ACTION_DONE,
						new AndroidUtilsUI.OnTextBoxDialogClick() {
							@Override
							public void onClick(DialogInterface dialog, int which,
									EditText editText) {
								proxyPrefs.user = editText.getText().toString();
								fillDataStore();
							}
						}).show();
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

		CorePrefs corePrefs = CorePrefs.getInstance();

		if (preferenceScreen.getKey().equals(KEY_PROXY_SCREEN)) {
			if (proxyPrefs == null) {
				proxyPrefs = corePrefs.getProxyPreferences();
			}
			fillProxyDataStore();
		}

		SwitchPreference prefAllowMobileData = (SwitchPreference) findPreference(
				KEY_ALLOW_MOBILE_DATA);
		if (prefAllowMobileData != null) {
			if (BiglyBTApp.getNetworkState().hasMobileDataCapability()) {
				prefAllowMobileData.setVisible(true);
				Boolean allowCellData = corePrefs.getPrefAllowCellData();
				dataStore.putBoolean(KEY_ALLOW_MOBILE_DATA, allowCellData);
				prefAllowMobileData.setChecked(allowCellData);
			} else {
				prefAllowMobileData.setVisible(false);
			}
		}

		final SwitchPreference prefAutoStart = (SwitchPreference) findPreference(
				KEY_AUTO_START);
		if (prefAutoStart != null) {
			Boolean autoStart = corePrefs.getPrefAutoStart();
			dataStore.putBoolean(KEY_AUTO_START, autoStart);
			prefAutoStart.setChecked(autoStart);
		}

		boolean canShowSleep = activity.getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_WIFI)
				&& AndroidUtils.hasPermisssion(BiglyBTApp.getContext(),
						android.Manifest.permission.WAKE_LOCK);
		SwitchPreference prefDisableSleep = (SwitchPreference) findPreference(
				KEY_DISABLE_SLEEP);
		if (prefDisableSleep != null) {
			prefDisableSleep.setVisible(canShowSleep);
			if (canShowSleep) {
				Boolean disableSleep = corePrefs.getPrefDisableSleep();
				dataStore.putBoolean(KEY_DISABLE_SLEEP, disableSleep);
				prefDisableSleep.setChecked(disableSleep);
			}
		}

		// Would be nice to have a way to detect a device that doesn't have a battery
		// Unfortunately, TV's return present=true for BatteryManager, and no
		// flag to indicate no-battery (plugged=BATTERY_PLUGGED_AC can be a
		// device temporarily attached to wall USB charger)
		SwitchPreference prefOnlyPluggedIn = (SwitchPreference) findPreference(
				KEY_ONLY_PLUGGEDIN);
		if (prefOnlyPluggedIn != null) {
			boolean canShowPlug = !AndroidUtils.isTV();
			prefOnlyPluggedIn.setVisible(canShowPlug);
			if (canShowPlug) {
				Boolean onlyPluggedIn = corePrefs.getPrefOnlyPluggedIn();
				dataStore.putBoolean(KEY_ONLY_PLUGGEDIN, onlyPluggedIn);
				prefOnlyPluggedIn.setChecked(onlyPluggedIn);
			}
		}

		final SwitchPreference prefAllowLANAccess = (SwitchPreference) findPreference(
				KEY_ALLOW_LAN_ACCESS);
		if (prefAllowLANAccess != null) {
			Boolean allowLANAccess = corePrefs.getPrefAllowLANAccess();
			dataStore.putBoolean(KEY_ALLOW_LAN_ACCESS, allowLANAccess);
			prefAllowLANAccess.setChecked(allowLANAccess);
			prefAllowLANAccess.setSummaryOn(
					BiglyBTApp.getNetworkState().getLocalIpAddress() + ":"
							+ RPC.LOCAL_BIGLYBT_PORT);
		}

		final Preference prefProxyScreen = findPreference(KEY_PROXY_SCREEN);
		if (prefProxyScreen != null) {
			CoreProxyPreferences proxyPrefs = corePrefs.getProxyPreferences();

			if (proxyPrefs.proxyTrackers || proxyPrefs.proxyOutgoingPeers) {
				String s = activity.getString(R.string.pref_proxy_enabled,
						proxyPrefs.proxyType);
				prefProxyScreen.setSummary(s);
			} else {
				prefProxyScreen.setSummary(R.string.pref_proxy_disabled);
			}
		}
	}

	private void fillProxyDataStore() {
		final SwitchPreference prefProxyTrackers = (SwitchPreference) findPreference(
				KEY_PROXY_ENABLED_TRACKER);
		if (prefProxyTrackers != null) {
			dataStore.putBoolean(KEY_PROXY_ENABLED_TRACKER, proxyPrefs.proxyTrackers);
			prefProxyTrackers.setChecked(proxyPrefs.proxyTrackers);
		}

		final SwitchPreference prefOutgoingPeers = (SwitchPreference) findPreference(
				KEY_PROXY_ENABLED_PEER);
		if (prefOutgoingPeers != null) {
			dataStore.putBoolean(KEY_PROXY_ENABLED_PEER,
					proxyPrefs.proxyOutgoingPeers);
			prefOutgoingPeers.setChecked(proxyPrefs.proxyOutgoingPeers);
		}

		final ListPreference prefProxyType = (ListPreference) findPreference(
				KEY_PROXY_TYPE);
		if (prefProxyType != null) {
			prefProxyType.setOnPreferenceChangeListener(this);

			dataStore.putString(KEY_PROXY_TYPE, proxyPrefs.proxyType);
			prefProxyType.setValue(proxyPrefs.proxyType);
			prefProxyType.setSummary(proxyPrefs.proxyType);
		}

		final Preference prefProxyHost = findPreference(KEY_PROXY_HOST);
		if (prefProxyHost != null) {
			dataStore.putString(KEY_PROXY_HOST, proxyPrefs.host);
			prefProxyHost.setSummary(proxyPrefs.host);
		}

		final Preference prefProxyUser = findPreference(KEY_PROXY_USER);
		if (prefProxyUser != null) {
			dataStore.putString(KEY_PROXY_USER, proxyPrefs.user);
			prefProxyUser.setSummary(proxyPrefs.user);
		}

		final Preference prefProxyPW = findPreference(KEY_PROXY_PW);
		if (prefProxyPW != null) {
			dataStore.putString(KEY_PROXY_PW, proxyPrefs.pw);
			prefProxyPW.setSummary(TextUtils.isEmpty(proxyPrefs.pw)
					? "No Password Set" : "Password Set");
		}

		final Preference prefProxyPort = findPreference(KEY_PROXY_PORT);
		if (prefProxyPort != null) {
			dataStore.putInt(KEY_PROXY_PORT, proxyPrefs.port);
			if (proxyPrefs.port > 0) {
				prefProxyPort.setSummary("Port " + proxyPrefs.port);
			}
		}

		Preference[] proxyParamSet = {
			prefProxyHost,
			prefProxyPort,
			prefProxyPW,
			prefProxyType,
			prefProxyUser
		};
		boolean enableSet = proxyPrefs.proxyOutgoingPeers
				|| proxyPrefs.proxyTrackers;
		for (Preference pref : proxyParamSet) {
			if (pref != null) {
				pref.setEnabled(enableSet);
			}
		}

	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		final String key = preference.getKey();
		if (key == null) {
			return false;
		}
		switch (key) {
			case KEY_PROXY_TYPE: {
				proxyPrefs.proxyType = newValue.toString();
				fillDataStore();
				return true;
			}
		}
		return true;
	}

	@Override
	public void onNumberPickerChange(@Nullable String callbackID, int val) {
		if (callbackID.equals(KEY_PROXY_PORT) && proxyPrefs != null) {
			proxyPrefs.port = val;
			fillDataStore();
			return;
		}
		super.onNumberPickerChange(callbackID, val);
	}

	@Override
	public void onPreferenceScreenClosed(PreferenceScreen preferenceScreen) {
		if (preferenceScreen.getKey().equals(KEY_PROXY_SCREEN)
				&& proxyPrefs != null) {

			TrayPreferences prefs = BiglyBTApp.getAppPreferences().getPreferences();

			prefs.put(CorePrefs.PREF_CORE_PROXY_TRACKERS, proxyPrefs.proxyTrackers);
			prefs.put(CorePrefs.PREF_CORE_PROXY_DATA, proxyPrefs.proxyOutgoingPeers);
			prefs.put(CorePrefs.PREF_CORE_PROXY_TYPE, proxyPrefs.proxyType);
			prefs.put(CorePrefs.PREF_CORE_PROXY_HOST, proxyPrefs.host);
			prefs.put(CorePrefs.PREF_CORE_PROXY_PORT, proxyPrefs.port);
			prefs.put(CorePrefs.PREF_CORE_PROXY_USER, proxyPrefs.user);
			prefs.put(CorePrefs.PREF_CORE_PROXY_PW, proxyPrefs.pw);
		}
		super.onPreferenceScreenClosed(preferenceScreen);
	}
}
