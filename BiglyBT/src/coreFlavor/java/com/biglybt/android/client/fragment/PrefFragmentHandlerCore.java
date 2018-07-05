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

import java.net.URLEncoder;

import com.biglybt.android.client.*;
import com.biglybt.android.client.R;
import com.biglybt.android.client.activity.SessionActivity;
import com.biglybt.android.client.dialog.DialogFragmentNumberPicker;
import com.biglybt.android.client.dialog.DialogFragmentRemoteAccessQR;
import com.biglybt.android.client.rpc.RPC;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionSettings;

import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.*;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import net.grandcentrix.tray.TrayPreferences;

/**
 * Created by TuxPaper on 10/23/17.
 */

public class PrefFragmentHandlerCore
	extends PrefFragmentHandler
	implements Preference.OnPreferenceChangeListener,
	CorePrefs.CorePrefsChangedListener
{
	private final static String KEY_ALLOW_MOBILE_DATA = "core_data_transfer_over_mobile_data_plan";

	private static final String KEY_AUTO_START = "core_auto_start_on_boot";

	private static final String KEY_DISABLE_SLEEP = "core_keep_cpu_awake";

	private static final String KEY_ONLY_PLUGGEDIN = "core_only_transfer_data_when_plugged_in";

	private final static String KEY_ALLOW_LAN_ACCESS = "core_allow_lan_access";

	private static final String KEY_PROXY_ENABLED_TRACKER = "core_tracker_proxy_enabled";

	private static final String KEY_PROXY_ENABLED_PEER = "core_peer_proxy_enabled";

	private static final String KEY_PROXY_SCREEN = "ps_core_proxy";

	private static final String KEY_PROXY_TYPE = "core_proxy_type";

	private static final String KEY_PROXY_HOST = "core_proxy_host";

	private static final String KEY_PROXY_PORT = "core_proxy_port";

	private static final String KEY_PROXY_USER = "core_proxy_user";

	private static final String KEY_PROXY_PW = "core_proxy_pw";

	private static final String KEY_RACCESS_SCREEN = "ps_core_remote_access";

	private static final String KEY_RACCESS_REQPW = "core_remote_access_reqpw";

	private static final String KEY_RACCESS_USER = "core_remote_access_user";

	private static final String KEY_RACCESS_PW = "core_remote_access_pw";

	private static final String KEY_PREFSUMMARY_APPLIED_AFTER_CLOSING = "prefsummary_applied_after_closing";

	private static final String KEY_RACCESS_SHOWQR = "action_remote_access_qr";

	private static final String TAG = "PrefFragHandlerCore";

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

							updateWidgets();
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

							updateWidgets();
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
				ds.putBoolean(KEY_ALLOW_LAN_ACCESS,
						((SwitchPreference) preference).isChecked());
				updateWidgets();
				return true;
			}

			case KEY_RACCESS_REQPW: {
				ds.putBoolean(KEY_RACCESS_REQPW,
						((SwitchPreference) preference).isChecked());
				updateWidgets();
				return true;
			}

			case KEY_RACCESS_PW: {
				AndroidUtilsUI.createTextBoxDialog(activity,
						R.string.preftitle_core_remote_access_password, 0, "",
						EditorInfo.IME_ACTION_DONE,
						InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
						new AndroidUtilsUI.OnTextBoxDialogClick() {
							@Override
							public void onClick(DialogInterface dialog, int which,
									EditText editText) {
								ds.putString(KEY_RACCESS_PW, editText.getText().toString());
								updateWidgets();
							}
						}).show();
				return true;
			}

			case KEY_RACCESS_USER: {
				AndroidUtilsUI.createTextBoxDialog(activity,
						R.string.preftitle_core_remote_access_user, 0,
						ds.getString(KEY_RACCESS_USER, ""), EditorInfo.IME_ACTION_DONE,
						new AndroidUtilsUI.OnTextBoxDialogClick() {
							@Override
							public void onClick(DialogInterface dialog, int which,
									EditText editText) {
								ds.putString(KEY_RACCESS_USER, editText.getText().toString());
								updateWidgets();
							}
						}).show();
				return true;
			}

			case KEY_RACCESS_SHOWQR: {
				saveRemoteAccessPrefs();
				try {
					String url = "biglybt://remote/profile?h="
							+ BiglyBTApp.getNetworkState().getLocalIpAddress() + "&p="
							+ RPC.LOCAL_BIGLYBT_PORT;
					boolean reqPW = ds.getBoolean(KEY_RACCESS_REQPW, false);
					if (reqPW) {
						url += "&u="
								+ URLEncoder.encode(ds.getString(KEY_RACCESS_USER, ""), "utf8");
						if (!TextUtils.isEmpty(ds.getString(KEY_RACCESS_PW))) {
							url += "&reqPW=1";
						}
					}
					String urlWebUI = "http://"
							+ BiglyBTApp.getNetworkState().getLocalIpAddress() + ":"
							+ RPC.LOCAL_BIGLYBT_PORT;
					DialogFragmentRemoteAccessQR.open(
							activity.getSupportFragmentManager(), url, urlWebUI);
				} catch (Throwable ignored) {
				}
				return true;
			}

			case KEY_PROXY_ENABLED_PEER: {
				ds.putBoolean(key, ((SwitchPreference) preference).isChecked());
				updateWidgets();
				return true;
			}

			case KEY_PROXY_ENABLED_TRACKER: {
				ds.putBoolean(key, ((SwitchPreference) preference).isChecked());
				updateWidgets();
				return true;
			}

			case KEY_PROXY_HOST: {
				AndroidUtilsUI.createTextBoxDialog(activity, R.string.proxy_host, 0,
						ds.getString(key), EditorInfo.IME_ACTION_DONE,
						new AndroidUtilsUI.OnTextBoxDialogClick() {
							@Override
							public void onClick(DialogInterface dialog, int which,
									EditText editText) {
								ds.putString(key, editText.getText().toString());
								updateWidgets();
							}
						}).show();
				return true;
			}

			case KEY_PROXY_PORT: {
				DialogFragmentNumberPicker.NumberPickerBuilder builder = new DialogFragmentNumberPicker.NumberPickerBuilder(
						activity.getSupportFragmentManager(), KEY_PROXY_PORT,
						ds.getInt(key, 0)).setTitleId(R.string.proxy_port).setShowSpinner(
								false).setMin(1).setMax(65535);
				DialogFragmentNumberPicker.openDialog(builder);
				return true;
			}

			case KEY_PROXY_PW: {
				AndroidUtilsUI.createTextBoxDialog(activity, R.string.proxy_pw, 0, "",
						EditorInfo.IME_ACTION_DONE,
						InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
						new AndroidUtilsUI.OnTextBoxDialogClick() {
							@Override
							public void onClick(DialogInterface dialog, int which,
									EditText editText) {
								ds.putString(key, editText.getText().toString());
								updateWidgets();
							}
						}).show();
				return true;
			}

			case KEY_PROXY_USER: {
				AndroidUtilsUI.createTextBoxDialog(activity, R.string.proxy_user, 0,
						ds.getString(key), EditorInfo.IME_ACTION_DONE,
						new AndroidUtilsUI.OnTextBoxDialogClick() {
							@Override
							public void onClick(DialogInterface dialog, int which,
									EditText editText) {
								ds.putString(key, editText.getText().toString());
								updateWidgets();
							}
						}).show();
				return true;
			}

		}
		return super.onPreferenceTreeClick(preference);
	}

	@Override
	public void corePrefAutoStartChanged(boolean autoStart) {

	}

	@Override
	public void corePrefAllowCellDataChanged(boolean allowCellData) {

	}

	@Override
	public void corePrefDisableSleepChanged(boolean disableSleep) {

	}

	@Override
	public void corePrefOnlyPluggedInChanged(boolean onlyPluggedIn) {

	}

	@Override
	public void corePrefProxyChanged(CoreProxyPreferences prefProxy) {
		updateWidgets();
	}

	@Override
	public void corePrefRemAccessChanged(
			CoreRemoteAccessPreferences prefRemoteAccess) {
		updateWidgets();
	}

	@Override
	@UiThread
	public void updateWidgetsOnUI() {
		super.updateWidgetsOnUI();

		final String screenKey = preferenceScreen.getKey();
		if (KEY_RACCESS_SCREEN.equals(screenKey)) {
			updateRemoteAccessWidgets();
			return;
		}
		if (KEY_PROXY_SCREEN.equals(screenKey)) {
			updateProxyWidgets();
			return;
		}

		Session session = activity.getSession();
		if (session == null) {
			return;
		}
		SessionSettings sessionSettings = session.getSessionSettingsClone();
		if (sessionSettings == null) {
			return;
		}

		SwitchPreference prefAllowMobileData = (SwitchPreference) findPreference(
				KEY_ALLOW_MOBILE_DATA);
		if (prefAllowMobileData != null) {
			if (BiglyBTApp.getNetworkState().hasMobileDataCapability()) {
				prefAllowMobileData.setVisible(true);
				prefAllowMobileData.setChecked(ds.getBoolean(KEY_ALLOW_MOBILE_DATA));
			} else {
				prefAllowMobileData.setVisible(false);
			}
		}

		final SwitchPreference prefAutoStart = (SwitchPreference) findPreference(
				KEY_AUTO_START);
		if (prefAutoStart != null) {
			prefAutoStart.setChecked(ds.getBoolean(KEY_AUTO_START));
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
				prefDisableSleep.setChecked(ds.getBoolean(KEY_DISABLE_SLEEP));
			}
		}

		// Would be nice to have a way to detect a device that doesn't have a battery
		// Unfortunately, TV's return present=true for BatteryManager, and no
		// flag to indicate no-battery (plugged=BATTERY_PLUGGED_AC can be a
		// device temporarily attached to wall USB charger)
		SwitchPreference prefOnlyPluggedIn = (SwitchPreference) findPreference(
				KEY_ONLY_PLUGGEDIN);
		if (prefOnlyPluggedIn != null) {
			boolean canShowPlug = !AndroidUtils.isTV(activity);
			prefOnlyPluggedIn.setVisible(canShowPlug);
			if (canShowPlug) {
				prefOnlyPluggedIn.setChecked(ds.getBoolean(KEY_ONLY_PLUGGEDIN));
			}
		}

		final Preference prefProxyScreen = findPreference(KEY_PROXY_SCREEN);
		if (prefProxyScreen != null) {
			CorePrefs corePrefs = CorePrefs.getInstance();
			CoreProxyPreferences proxyPrefs = corePrefs.getProxyPreferences();

			if (proxyPrefs.proxyTrackers || proxyPrefs.proxyOutgoingPeers) {
				String s = activity.getString(R.string.pref_proxy_enabled,
						proxyPrefs.proxyType);
				prefProxyScreen.setSummary(s);
			} else {
				prefProxyScreen.setSummary(R.string.pref_proxy_disabled);
			}
		}

		final Preference prefRemAccessScreen = findPreference(KEY_RACCESS_SCREEN);
		if (prefRemAccessScreen != null) {
			String s;

			CorePrefs corePrefs = CorePrefs.getInstance();
			CoreRemoteAccessPreferences raPrefs = corePrefs.getRemoteAccessPreferences();

			if (raPrefs.allowLANAccess) {
				String address = BiglyBTApp.getNetworkState().getLocalIpAddress() + ":"
						+ RPC.LOCAL_BIGLYBT_PORT;

				s = activity.getString(
						raPrefs.reqPW ? R.string.core_remote_access_summary_enabled_secure
								: R.string.core_remote_access_summary_enabled,
						address);
			} else {
				s = activity.getString(R.string.core_remote_access_summary_disabled);
			}
			prefRemAccessScreen.setSummary(s);
		}

	}

	@UiThread
	private void updateRemoteAccessWidgets() {
		boolean reqPW = ds.getBoolean(KEY_RACCESS_REQPW);
		boolean allowLANAccess = ds.getBoolean(KEY_ALLOW_LAN_ACCESS);

		final Preference prefSummaryLine = findPreference(
				KEY_PREFSUMMARY_APPLIED_AFTER_CLOSING);
		if (prefSummaryLine != null) {
			prefSummaryLine.setSummary(
					activity.getString(R.string.prefsummary_applied_after_closing,
							activity.getString(R.string.ps_core_remote_access)));
		}

		final SwitchPreference prefAllowLANAccess = (SwitchPreference) findPreference(
				KEY_ALLOW_LAN_ACCESS);
		if (prefAllowLANAccess != null) {
			prefAllowLANAccess.setChecked(allowLANAccess);
			prefAllowLANAccess.setSummaryOn(
					BiglyBTApp.getNetworkState().getLocalIpAddress() + ":"
							+ RPC.LOCAL_BIGLYBT_PORT);
		}

		final SwitchPreference prefReqPW = (SwitchPreference) findPreference(
				KEY_RACCESS_REQPW);
		if (prefReqPW != null) {
			prefReqPW.setChecked(reqPW);
			prefReqPW.setEnabled(allowLANAccess);
		}

		final Preference prefUser = findPreference(KEY_RACCESS_USER);
		if (prefUser != null) {
			prefUser.setSummary(reqPW ? ds.getString(KEY_RACCESS_USER) : "");
			prefUser.setEnabled(allowLANAccess && reqPW);
		}

		final Preference prefPW = findPreference(KEY_RACCESS_PW);
		if (prefPW != null) {
			if (reqPW && allowLANAccess) {
				prefPW.setSummary(TextUtils.isEmpty(ds.getString(KEY_RACCESS_PW))
						? R.string.no_password_set : R.string.password_is_set);
			} else {
				prefPW.setSummary(null);
			}
			prefPW.setEnabled(reqPW && allowLANAccess);
		}

		final Preference prefShowQR = findPreference(KEY_RACCESS_SHOWQR);
		if (prefShowQR != null) {
			prefShowQR.setEnabled(allowLANAccess);
		}
	}

	@UiThread
	private void updateProxyWidgets() {
		final Preference prefSummaryLine = findPreference(
				KEY_PREFSUMMARY_APPLIED_AFTER_CLOSING);
		if (prefSummaryLine != null) {
			prefSummaryLine.setSummary(
					activity.getString(R.string.prefsummary_applied_after_closing,
							activity.getString(R.string.preftitle_core_proxy_settings)));
		}

		final SwitchPreference prefProxyTrackers = (SwitchPreference) findPreference(
				KEY_PROXY_ENABLED_TRACKER);
		if (prefProxyTrackers != null) {
			prefProxyTrackers.setChecked(ds.getBoolean(KEY_PROXY_ENABLED_TRACKER));
		}

		final SwitchPreference prefOutgoingPeers = (SwitchPreference) findPreference(
				KEY_PROXY_ENABLED_PEER);
		if (prefOutgoingPeers != null) {
			prefOutgoingPeers.setChecked(ds.getBoolean(KEY_PROXY_ENABLED_PEER));
		}

		final ListPreference prefProxyType = (ListPreference) findPreference(
				KEY_PROXY_TYPE);
		if (prefProxyType != null) {
			prefProxyType.setOnPreferenceChangeListener(this);

			String proxyType = ds.getString(KEY_PROXY_TYPE);

			prefProxyType.setValue(proxyType);
			prefProxyType.setSummary(proxyType);
		}

		final Preference prefProxyHost = findPreference(KEY_PROXY_HOST);
		if (prefProxyHost != null) {
			prefProxyHost.setSummary(ds.getString(KEY_PROXY_HOST));
		}

		final Preference prefProxyUser = findPreference(KEY_PROXY_USER);
		if (prefProxyUser != null) {
			prefProxyUser.setSummary(ds.getString(KEY_PROXY_USER));
		}

		final Preference prefProxyPW = findPreference(KEY_PROXY_PW);
		if (prefProxyPW != null) {
			prefProxyPW.setSummary(TextUtils.isEmpty(ds.getString(KEY_PROXY_PW))
					? R.string.no_password_set : R.string.password_is_set);
		}

		final Preference prefProxyPort = findPreference(KEY_PROXY_PORT);
		if (prefProxyPort != null) {
			int port = ds.getInt(KEY_PROXY_PORT, 0);
			if (port > 0) {
				prefProxyPort.setSummary(
						activity.getString(R.string.prefsummary_proxy_port, port));
			}
		}

		Preference[] proxyParamSet = {
			prefProxyHost,
			prefProxyPort,
			prefProxyPW,
			prefProxyType,
			prefProxyUser
		};
		boolean proxyOutgoingPeers = ds.getBoolean(KEY_PROXY_ENABLED_PEER);
		boolean proxyTrackers = ds.getBoolean(KEY_PROXY_ENABLED_TRACKER);
		boolean enableSet = proxyOutgoingPeers || proxyTrackers;
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
				ds.putString(key, newValue.toString());
				updateWidgets();
				return true;
			}
		}
		return true;
	}

	@Override
	public void onNumberPickerChange(@Nullable String callbackID, int val) {
		if (KEY_PROXY_PORT.equals(callbackID)) {
			ds.putInt(callbackID, val);
			updateWidgets();
			return;
		}
		super.onNumberPickerChange(callbackID, val);
	}

	@Override
	public void onPreferenceScreenClosed(PreferenceScreen preferenceScreen) {
		CorePrefs corePrefs = CorePrefs.getInstance();
		corePrefs.removeChangedListener(this);

		String key = preferenceScreen.getKey();
		if (key.equals(KEY_PROXY_SCREEN)) {
			saveProxyPrefs();
		} else if (key.equals(KEY_RACCESS_SCREEN)) {
			saveRemoteAccessPrefs();
		}

		super.onPreferenceScreenClosed(preferenceScreen);
	}

	private void saveProxyPrefs() {
		if (ds.size() == 0) {
			Log.e(TAG, "saveProxyPrefs: empty datastore "
					+ AndroidUtils.getCompressedStackTrace());
		}
		TrayPreferences prefs = BiglyBTApp.getAppPreferences().getPreferences();

		prefs.put(CorePrefs.PREF_CORE_PROXY_TRACKERS,
				ds.getBoolean(KEY_PROXY_ENABLED_TRACKER));
		prefs.put(CorePrefs.PREF_CORE_PROXY_DATA,
				ds.getBoolean(KEY_PROXY_ENABLED_PEER));
		prefs.put(CorePrefs.PREF_CORE_PROXY_TYPE, ds.getString(KEY_PROXY_TYPE));
		prefs.put(CorePrefs.PREF_CORE_PROXY_HOST, ds.getString(KEY_PROXY_HOST));
		prefs.put(CorePrefs.PREF_CORE_PROXY_PORT, ds.getInt(KEY_PROXY_PORT, 0));
		prefs.put(CorePrefs.PREF_CORE_PROXY_USER, ds.getString(KEY_PROXY_USER));
		prefs.put(CorePrefs.PREF_CORE_PROXY_PW, ds.getString(KEY_PROXY_PW));
	}

	private void saveRemoteAccessPrefs() {
		if (ds.size() == 0) {
			Log.e(TAG, "saveRemoteAccessPrefs: empty datastore "
					+ AndroidUtils.getCompressedStackTrace());
		}
		TrayPreferences prefs = BiglyBTApp.getAppPreferences().getPreferences();

		prefs.put(CorePrefs.PREF_CORE_ALLOWLANACCESS,
				ds.getBoolean(KEY_ALLOW_LAN_ACCESS));
		prefs.put(CorePrefs.PREF_CORE_RACCESS_REQPW,
				ds.getBoolean(KEY_RACCESS_REQPW));
		prefs.put(CorePrefs.PREF_CORE_RACCESS_USER, ds.getString(KEY_RACCESS_USER));
		prefs.put(CorePrefs.PREF_CORE_RACCESS_PW, ds.getString(KEY_RACCESS_PW));
	}

	@Override
	public void setPreferenceScreen(PreferenceManager preferenceManager,
			PreferenceScreen preferenceScreen) {
		super.setPreferenceScreen(preferenceManager, preferenceScreen);

		final String screenKey = preferenceScreen.getKey();

		if (KEY_RACCESS_SCREEN.equals(screenKey)) {
			CorePrefs corePrefs = CorePrefs.getInstance();
			CoreRemoteAccessPreferences raPrefs = corePrefs.getRemoteAccessPreferences();

			ds.putBoolean(KEY_ALLOW_LAN_ACCESS, raPrefs.allowLANAccess);
			ds.putBoolean(KEY_RACCESS_REQPW, raPrefs.reqPW);
			ds.putString(KEY_RACCESS_USER, raPrefs.user);
			ds.putString(KEY_RACCESS_PW, raPrefs.pw);
		} else if (KEY_PROXY_SCREEN.equals(screenKey)) {
			CorePrefs corePrefs = CorePrefs.getInstance();
			CoreProxyPreferences proxyPrefs = corePrefs.getProxyPreferences();

			ds.putBoolean(KEY_PROXY_ENABLED_TRACKER, proxyPrefs.proxyTrackers);
			ds.putBoolean(KEY_PROXY_ENABLED_PEER, proxyPrefs.proxyOutgoingPeers);
			ds.putString(KEY_PROXY_TYPE, proxyPrefs.proxyType);
			ds.putString(KEY_PROXY_HOST, proxyPrefs.host);
			ds.putString(KEY_PROXY_USER, proxyPrefs.user);
			ds.putString(KEY_PROXY_PW, proxyPrefs.pw);
			ds.putInt(KEY_PROXY_PORT, proxyPrefs.port);
		} else {
			CorePrefs corePrefs = CorePrefs.getInstance();
			ds.putBoolean(KEY_ONLY_PLUGGEDIN, corePrefs.getPrefOnlyPluggedIn());
			ds.putBoolean(KEY_ALLOW_MOBILE_DATA, corePrefs.getPrefAllowCellData());
			ds.putBoolean(KEY_AUTO_START, corePrefs.getPrefAutoStart());
			ds.putBoolean(KEY_DISABLE_SLEEP, corePrefs.getPrefDisableSleep());
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		CorePrefs corePrefs = CorePrefs.getInstance();
		corePrefs.addChangedListener(this, false);
	}
}
