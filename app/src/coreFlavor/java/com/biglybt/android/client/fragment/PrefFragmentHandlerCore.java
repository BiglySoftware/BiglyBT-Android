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

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.preference.*;

import com.biglybt.android.client.AppCompatActivityM.PermissionRequestResults;
import com.biglybt.android.client.AppCompatActivityM.PermissionResultHandler;
import com.biglybt.android.client.R;
import com.biglybt.android.client.*;
import com.biglybt.android.client.activity.SessionActivity;
import com.biglybt.android.client.dialog.DialogFragmentNumberPicker;
import com.biglybt.android.client.dialog.DialogFragmentRemoteAccessQR;
import com.biglybt.android.client.rpc.RPC;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionSettings;
import com.biglybt.android.util.BiglyCoreUtils;
import com.biglybt.android.util.NetworkState;
import com.biglybt.android.util.PathInfo;

import net.grandcentrix.tray.TrayPreferences;

import java.net.URLEncoder;

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

	private static final String KEY_CONN_ENCRYPT_SCREEN = "ps_conn_encryption";

	private static final String KEY_CONN_ENCRYPT_REQ = "core_conn_encrypt_req";

	private static final String KEY_CONN_ENCRYPT_MIN_LEVEL = "core_conn_encrypt_min_level";

	private static final String KEY_CONN_ENCRYPT_FB_INCOMING = "core_conn_encrypt_fallback_incoming";

	private static final String KEY_CONN_ENCRYPT_FB_OUTGOING = "core_conn_encrypt_fallback_outgoing";

	private static final String KEY_CONN_ENCRYPT_USE_CRYPTOPORT = "core_conn_encrypt_use_crypto_port";

	private static final String KEY_RACCESS_SCREEN = "ps_core_remote_access";

	private static final String KEY_RACCESS_REQPW = "core_remote_access_reqpw";

	private static final String KEY_RACCESS_USER = "core_remote_access_user";

	private static final String KEY_RACCESS_PW = "core_remote_access_pw";

	private static final String KEY_PREFSUMMARY_APPLIED_AFTER_CLOSING_PROXY = "prefsummary_applied_after_closing_proxy";

	private static final String KEY_PREFSUMMARY_APPLIED_AFTER_CLOSING_RA = "prefsummary_applied_after_closing_ra";

	private static final String KEY_RACCESS_SHOWQR = "action_remote_access_qr";

	private static final String TAG = "PrefFragHandlerCore";

	private String localIpAddress;

	public PrefFragmentHandlerCore(SessionActivity activity, Fragment fragment) {
		super(activity, fragment);
	}

	@Override
	@UiThread
	public boolean onPreferenceTreeClick(@NonNull final Preference preference) {
		final String key = preference.getKey();
		if (key == null || ds == null) {
			return false;
		}
		switch (key) {

			case KEY_ALLOW_MOBILE_DATA: {
				OffThread.runOffUIThread(() -> {
					TrayPreferences prefs = BiglyBTApp.getAppPreferences().getPreferences();
					prefs.put(CorePrefs.PREF_CORE_ALLOWCELLDATA,
							((TwoStatePreference) preference).isChecked());
				});
				return true;
			}

			case KEY_AUTO_START: {
				OffThread.runOffUIThread(() -> {
					TrayPreferences prefs = BiglyBTApp.getAppPreferences().getPreferences();
					boolean b = ((TwoStatePreference) preference).isChecked();
					prefs.put(CorePrefs.PREF_CORE_AUTOSTART, b);
					if (b) {
						// Note: In theory there's no need to ask for RECEIVE_BOOT_COMPLETED perms
						activity.requestPermissions(new String[] {
							android.Manifest.permission.RECEIVE_BOOT_COMPLETED
						}, new PermissionResultHandler() {
							@WorkerThread
							@Override
							public void onAllGranted() {
							}

							@WorkerThread
							@Override
							public void onSomeDenied(PermissionRequestResults results) {
								TrayPreferences prefs12 = BiglyBTApp.getAppPreferences().getPreferences();
								prefs12.put(CorePrefs.PREF_CORE_AUTOSTART, false);

								updateWidgets();
							}
						});
					}
				});
				return true;
			}

			case KEY_DISABLE_SLEEP: {
				OffThread.runOffUIThread(() -> {
					TrayPreferences prefs = BiglyBTApp.getAppPreferences().getPreferences();
					boolean b = ((TwoStatePreference) preference).isChecked();
					prefs.put(CorePrefs.PREF_CORE_DISABLESLEEP, b);

					if (b) {
						// Note: In theory there's no need to ask for WAKE_LOCK perms
						activity.requestPermissions(new String[] {
							android.Manifest.permission.WAKE_LOCK
						}, new PermissionResultHandler() {
							@WorkerThread
							@Override
							public void onAllGranted() {
							}

							@WorkerThread
							@Override
							public void onSomeDenied(PermissionRequestResults results) {
								TrayPreferences prefs1 = BiglyBTApp.getAppPreferences().getPreferences();
								prefs1.put(CorePrefs.PREF_CORE_DISABLESLEEP, false);

								updateWidgets();
							}
						});
					}
				});
				return true;
			}

			case KEY_ONLY_PLUGGEDIN: {
				OffThread.runOffUIThread(() -> {
					TrayPreferences prefs = BiglyBTApp.getAppPreferences().getPreferences();
					prefs.put(CorePrefs.PREF_CORE_ONLYPLUGGEDIN,
							((TwoStatePreference) preference).isChecked());
				});
				return true;
			}

			case KEY_ALLOW_LAN_ACCESS: {
				ds.putBoolean(KEY_ALLOW_LAN_ACCESS,
						((TwoStatePreference) preference).isChecked());
				updateWidgets();
				return true;
			}

			case KEY_RACCESS_REQPW: {
				ds.putBoolean(KEY_RACCESS_REQPW,
						((TwoStatePreference) preference).isChecked());
				updateWidgets();
				return true;
			}

			case KEY_RACCESS_PW: {
				AndroidUtilsUI.createTextBoxDialog(activity,
						activity.getString(R.string.preftitle_core_remote_access_password),
						null, null, "", EditorInfo.IME_ACTION_DONE,
						InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
						(dialog, which, editText) -> {
							ds.putString(KEY_RACCESS_PW, editText.getText().toString());
							updateWidgets();
						}).show();
				return true;
			}

			case KEY_RACCESS_USER: {
				AndroidUtilsUI.createTextBoxDialog(activity,
						R.string.preftitle_core_remote_access_user, View.NO_ID, View.NO_ID,
						ds.getString(KEY_RACCESS_USER, ""), EditorInfo.IME_ACTION_DONE,
						(dialog, which, editText) -> {
							ds.putString(KEY_RACCESS_USER, editText.getText().toString());
							updateWidgets();
						}).show();
				return true;
			}

			case KEY_RACCESS_SHOWQR: {
				OffThread.runOffUIThread(this::saveRemoteAccessPrefs);
				try {
					String url = "biglybt://remote/profile?h=" + localIpAddress + "&p="
							+ RPC.LOCAL_BIGLYBT_PORT;
					boolean reqPW = ds.getBoolean(KEY_RACCESS_REQPW, false);
					if (reqPW) {
						url += "&u="
								+ URLEncoder.encode(ds.getString(KEY_RACCESS_USER, ""), "utf8");
						if (!TextUtils.isEmpty(ds.getString(KEY_RACCESS_PW))) {
							url += "&reqPW=1";
						}
					}
					String urlWebUI = "http://" + localIpAddress + ":"
							+ RPC.LOCAL_BIGLYBT_PORT;
					DialogFragmentRemoteAccessQR.open(
							activity.getSupportFragmentManager(), url, urlWebUI);
				} catch (Throwable ignored) {
				}
				return true;
			}

			case KEY_PROXY_ENABLED_PEER:
			case KEY_PROXY_ENABLED_TRACKER: {
				ds.putBoolean(key, ((TwoStatePreference) preference).isChecked());
				updateWidgets();
				return true;
			}

			case KEY_PROXY_HOST: {
				AndroidUtilsUI.createTextBoxDialog(activity, R.string.proxy_host,
						View.NO_ID, View.NO_ID, ds.getString(key),
						EditorInfo.IME_ACTION_DONE, (dialog, which, editText) -> {
							ds.putString(key, editText.getText().toString());
							updateWidgets();
						}).show();
				return true;
			}

			case KEY_PROXY_PORT: {
				DialogFragmentNumberPicker.NumberPickerBuilder builder = new DialogFragmentNumberPicker.NumberPickerBuilder(
						AndroidUtilsUI.getSafeParentFragmentManager(fragment),
						KEY_PROXY_PORT, ds.getInt(key, 0)).setTitleId(
								R.string.proxy_port).setShowSpinner(false).setMin(1).setMax(
										65535).setTargetFragment(fragment);
				DialogFragmentNumberPicker.openDialog(builder);
				return true;
			}

			case KEY_PROXY_PW: {
				AndroidUtilsUI.createTextBoxDialog(activity,
						activity.getString(R.string.proxy_pw), null, null, "",
						EditorInfo.IME_ACTION_DONE,
						InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
						(dialog, which, editText) -> {
							ds.putString(key, editText.getText().toString());
							updateWidgets();
						}).show();
				return true;
			}

			case KEY_PROXY_USER: {
				AndroidUtilsUI.createTextBoxDialog(activity, R.string.proxy_user,
						View.NO_ID, View.NO_ID, ds.getString(key),
						EditorInfo.IME_ACTION_DONE, (dialog, which, editText) -> {
							ds.putString(key, editText.getText().toString());
							updateWidgets();
						}).show();
				return true;
			}

			case KEY_CONN_ENCRYPT_FB_INCOMING:
			case KEY_CONN_ENCRYPT_REQ: {
				ds.putBoolean(key, ((TwoStatePreference) preference).isChecked());
				updateConnEncryptWidgets();
			}

			case KEY_CONN_ENCRYPT_FB_OUTGOING:
			case KEY_CONN_ENCRYPT_USE_CRYPTOPORT: {
				ds.putBoolean(key, ((TwoStatePreference) preference).isChecked());
			}
		}
		return super.onPreferenceTreeClick(preference);
	}

	@Override
	public void corePrefAutoStartChanged(@NonNull CorePrefs corePrefs,
			boolean autoStart) {

	}

	@Override
	public void corePrefAllowCellDataChanged(@NonNull CorePrefs corePrefs,
			boolean allowCellData) {

	}

	@Override
	public void corePrefDisableSleepChanged(@NonNull CorePrefs corePrefs,
			boolean disableSleep) {

	}

	@Override
	public void corePrefOnlyPluggedInChanged(@NonNull CorePrefs corePrefs,
			boolean onlyPluggedIn) {

	}

	@Override
	public void corePrefRemAccessChanged(@NonNull CorePrefs corePrefs,
			CoreRemoteAccessPreferences prefRemoteAccess) {
		updateWidgets();
	}

	@WorkerThread
	@Override
	public void updateWidgetsOffUI() {
		if (ds == null) {
			return;
		}
		// Hack: Typically SessionActivity will restart core if it's lost, and
		//       we wait for isReadyForUI by addSessionListener.
		//       This hack just waits for core to be started, skipping all the 
		//       listener stuff
		BiglyCoreUtils.waitForCore();

		final String screenKey = preferenceScreen.getKey();
		if (KEY_RACCESS_SCREEN.equals(screenKey)) {
			localIpAddress = NetworkState.getLocalIpAddress();
		}

		final Preference prefSavePath = findPreference(KEY_SAVE_PATH);
		if (prefSavePath != null) {
			String sDir = ds.getString(KEY_SESSION_DOWNLOAD_PATH);
			if (sDir == null) {
				return;
			}
			Bundle extras = prefSavePath.getExtras();

			extras.putCharSequence("summary",
					PathInfo.buildPathInfo(sDir).getFriendlyName());
		}

		final Preference prefRemAccessScreen = findPreference(KEY_RACCESS_SCREEN);
		if (prefRemAccessScreen != null) {
			String s;

			CorePrefs corePrefs = CorePrefs.getInstance();
			CoreRemoteAccessPreferences raPrefs = corePrefs.getRemoteAccessPreferences();

			if (raPrefs != null && raPrefs.allowLANAccess) {
				String address = NetworkState.getLocalIpAddress() + ":"
						+ RPC.LOCAL_BIGLYBT_PORT;

				s = activity.getString(
						raPrefs.reqPW ? R.string.core_remote_access_summary_enabled_secure
								: R.string.core_remote_access_summary_enabled,
						address);
			} else {
				s = activity.getString(R.string.core_remote_access_summary_disabled);
			}
			Bundle extras = prefRemAccessScreen.getExtras();

			extras.putCharSequence("summary", s);
		}
	}

	@Override
	@UiThread
	public void updateWidgetsOnUI() {
		super.updateWidgetsOnUI();

		if (preferenceScreen == null || ds == null) {
			return;
		}

		final String screenKey = preferenceScreen.getKey();
		if (KEY_RACCESS_SCREEN.equals(screenKey)) {
			updateRemoteAccessWidgets();
			return;
		}
		if (KEY_PROXY_SCREEN.equals(screenKey)) {
			updateProxyWidgets();
			return;
		}
		if (KEY_CONN_ENCRYPT_SCREEN.equals(screenKey)) {
			updateConnEncryptWidgets();
		}

		Session session = activity.getSession();
		if (session == null) {
			return;
		}
		SessionSettings sessionSettings = session.getSessionSettingsClone();
		if (sessionSettings == null) {
			return;
		}

		TwoStatePreference prefAllowMobileData = (TwoStatePreference) findPreference(
				KEY_ALLOW_MOBILE_DATA);
		if (prefAllowMobileData != null) {
			if (BiglyBTApp.getNetworkState().hasMobileDataCapability()) {
				prefAllowMobileData.setVisible(true);
				prefAllowMobileData.setChecked(ds.getBoolean(KEY_ALLOW_MOBILE_DATA));
			} else {
				prefAllowMobileData.setVisible(false);
			}
		}

		TwoStatePreference prefAutoStart = (TwoStatePreference) findPreference(
				KEY_AUTO_START);
		if (prefAutoStart != null) {
			prefAutoStart.setChecked(ds.getBoolean(KEY_AUTO_START));
		}

		boolean canShowSleep = activity.getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_WIFI)
				&& AndroidUtils.hasPermisssion(BiglyBTApp.getContext(),
						android.Manifest.permission.WAKE_LOCK);
		TwoStatePreference prefDisableSleep = (TwoStatePreference) findPreference(
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
		TwoStatePreference prefOnlyPluggedIn = (TwoStatePreference) findPreference(
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
			String s = "";
			IBiglyCoreInterface coreInterface = BiglyCoreFlavorUtils.getCoreInterface();
			if (coreInterface != null) {
				try {
					boolean proxyTrackers = coreInterface.getParamBool(
							CoreParamKeys.BPARAM_PROXY_ENABLE_TRACKERS);
					boolean proxyOutgoingPeers = coreInterface.getParamBool(
							CoreParamKeys.BPARAM_PROXY_DATA_ENABLE);
					if (proxyTrackers || proxyOutgoingPeers) {
						String proxyType = coreInterface.getParamString(
								CoreParamKeys.SPARAM_PROXY_DATA_SOCKS_VER);
						boolean enableSocks = coreInterface.getParamBool(
								CoreParamKeys.BPARAM_PROXY_ENABLE_SOCKS);
						proxyType = proxyType.startsWith("V") && enableSocks
								? "SOCKS" + proxyType.substring(1) : "HTTP";
						s = activity.getString(R.string.pref_proxy_enabled, proxyType);
					} else {
						prefProxyScreen.setSummary(R.string.pref_proxy_disabled);
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				prefProxyScreen.setSummary(s);
			}
		}

		final Preference prefRemAccessScreen = findPreference(KEY_RACCESS_SCREEN);
		if (prefRemAccessScreen != null) {
			prefRemAccessScreen.setSummary(
					prefRemAccessScreen.getExtras().getCharSequence("summary"));
		}

		Preference prefEncryptScreen = findPreference(KEY_CONN_ENCRYPT_SCREEN);
		if (prefEncryptScreen != null) {
			String s = "";

			IBiglyCoreInterface coreInterface = BiglyCoreFlavorUtils.getCoreInterface();
			if (coreInterface != null) {
				try {
					boolean req = coreInterface.getParamBool(
							CoreParamKeys.BPARAM_CONN_ENCRYPT_REQ);
					@StringRes
					int id;
					if (req) {
						String minLevel = coreInterface.getParamString(
								CoreParamKeys.SPARAM_CONN_ENCRYPT_MIN_LEVEL);
						id = "RC4".equals(minLevel)
								? R.string.pref_conn_trans_encryption_RC4
								: R.string.pref_conn_trans_encryption_plain;
					} else {
						id = R.string.pref_conn_trans_encryption_none;
					}
					s = activity.getString(id);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
			prefEncryptScreen.setSummary(s);
		}

		final Preference prefSavePath = findPreference(KEY_SAVE_PATH);
		if (prefSavePath != null) {
			prefSavePath.setSummary(
					prefSavePath.getExtras().getCharSequence("summary"));
		}
	}

	private void updateConnEncryptWidgets() {
		if (ds == null) {
			return;
		}

		boolean req = ds.getBoolean(KEY_CONN_ENCRYPT_REQ);
		boolean allowIncoming = ds.getBoolean(KEY_CONN_ENCRYPT_FB_INCOMING);

		TwoStatePreference prefRequired = (TwoStatePreference) findPreference(
				KEY_CONN_ENCRYPT_REQ);
		if (prefRequired != null) {
			prefRequired.setChecked(req);
		}

		final ListPreference prefMinLevel = (ListPreference) findPreference(
				KEY_CONN_ENCRYPT_MIN_LEVEL);
		if (prefMinLevel != null) {
			prefMinLevel.setEnabled(req);
			prefMinLevel.setOnPreferenceChangeListener(this);

			String minLevel = ds.getString(KEY_CONN_ENCRYPT_MIN_LEVEL);

			prefMinLevel.setValue(minLevel);
			prefMinLevel.setSummary(prefMinLevel.getEntry());
		}

		TwoStatePreference prefEncIncoming = (TwoStatePreference) findPreference(
				KEY_CONN_ENCRYPT_FB_INCOMING);
		if (prefEncIncoming != null) {
			prefEncIncoming.setEnabled(req);
			prefEncIncoming.setChecked(allowIncoming);
		}

		TwoStatePreference prefEncOutgoing = (TwoStatePreference) findPreference(
				KEY_CONN_ENCRYPT_FB_OUTGOING);
		if (prefEncOutgoing != null) {
			prefEncOutgoing.setEnabled(req);
			boolean val = ds.getBoolean(KEY_CONN_ENCRYPT_FB_OUTGOING);
			prefEncOutgoing.setChecked(val);
		}

		TwoStatePreference prefUseCryptoport = (TwoStatePreference) findPreference(
				KEY_CONN_ENCRYPT_USE_CRYPTOPORT);
		if (prefUseCryptoport != null) {
			prefUseCryptoport.setEnabled(req && !allowIncoming);
			String title = prefUseCryptoport.getTitle().toString();
			int i = title.lastIndexOf('.');
			if (i > 0 && i < title.length() - 2) {
				String summary = title.substring(i + 2);
				prefUseCryptoport.setSummary(summary);
				prefUseCryptoport.setTitle(title.substring(0, i));
			}
			boolean val = ds.getBoolean(KEY_CONN_ENCRYPT_USE_CRYPTOPORT);
			prefUseCryptoport.setChecked(val);
		}
	}

	@UiThread
	private void updateRemoteAccessWidgets() {
		if (ds == null) {
			return;
		}

		boolean reqPW = ds.getBoolean(KEY_RACCESS_REQPW);
		boolean allowLANAccess = ds.getBoolean(KEY_ALLOW_LAN_ACCESS);

		final Preference prefSummaryLine = findPreference(
				KEY_PREFSUMMARY_APPLIED_AFTER_CLOSING_RA);
		if (prefSummaryLine != null) {
			prefSummaryLine.setSummary(
					activity.getString(R.string.prefsummary_applied_after_closing,
							activity.getString(R.string.ps_core_remote_access)));
		}

		TwoStatePreference prefAllowLANAccess = (TwoStatePreference) findPreference(
				KEY_ALLOW_LAN_ACCESS);
		if (prefAllowLANAccess != null) {
			prefAllowLANAccess.setChecked(allowLANAccess);
			prefAllowLANAccess.setSummaryOn(
					localIpAddress + ":" + RPC.LOCAL_BIGLYBT_PORT);
		}

		TwoStatePreference prefReqPW = (TwoStatePreference) findPreference(
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
		if (ds == null) {
			return;
		}

		final Preference prefSummaryLine = findPreference(
				KEY_PREFSUMMARY_APPLIED_AFTER_CLOSING_PROXY);
		if (prefSummaryLine != null) {
			prefSummaryLine.setSummary(
					activity.getString(R.string.prefsummary_applied_after_closing,
							activity.getString(R.string.preftitle_core_proxy_settings)));
		}

		TwoStatePreference prefProxyTrackers = (TwoStatePreference) findPreference(
				KEY_PROXY_ENABLED_TRACKER);
		if (prefProxyTrackers != null) {
			prefProxyTrackers.setChecked(ds.getBoolean(KEY_PROXY_ENABLED_TRACKER));
		}

		String proxyType = ds.getString(KEY_PROXY_TYPE);

		TwoStatePreference prefOutgoingPeers = (TwoStatePreference) findPreference(
				KEY_PROXY_ENABLED_PEER);
		if (prefOutgoingPeers != null) {
			prefOutgoingPeers.setChecked(ds.getBoolean(KEY_PROXY_ENABLED_PEER));
			prefOutgoingPeers.setEnabled(proxyType.startsWith("SOCKS"));
		}

		final ListPreference prefProxyType = (ListPreference) findPreference(
				KEY_PROXY_TYPE);
		if (prefProxyType != null) {
			prefProxyType.setOnPreferenceChangeListener(this);

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
		if (key == null || ds == null) {
			return false;
		}
		switch (key) {
			case KEY_CONN_ENCRYPT_MIN_LEVEL:
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
		if (ds != null && KEY_PROXY_PORT.equals(callbackID)) {
			ds.putInt(callbackID, val);
			updateWidgets();
			return;
		}
		super.onNumberPickerChange(callbackID, val);
	}

	@Override
	@UiThread
	public void onPreferenceScreenClosed(PreferenceScreen preferenceScreen) {
		CorePrefs corePrefs = CorePrefs.getInstance();
		corePrefs.removeChangedListener(this);

		String key = preferenceScreen.getKey();
		if (key == null) {
			return;
		}
		OffThread.runOffUIThread(() -> {
			switch (key) {
				case KEY_PROXY_SCREEN:
					saveProxyPrefs();
					break;
				case KEY_RACCESS_SCREEN:
					saveRemoteAccessPrefs();
					break;
				case KEY_CONN_ENCRYPT_SCREEN:
					saveEncryptionPrefs();
					break;
			}
		});

		super.onPreferenceScreenClosed(preferenceScreen);
	}

	@WorkerThread
	private void saveEncryptionPrefs() {
		if (ds == null || ds.size() == 0) {
			Log.e(TAG, "saveEncryptionPrefs: empty datastore "
					+ AndroidUtils.getCompressedStackTrace());
		}

		IBiglyCoreInterface coreInterface = BiglyCoreFlavorUtils.getCoreInterface();
		if (coreInterface == null) {
			return;
		}
		try {
			coreInterface.setParamBool(CoreParamKeys.BPARAM_CONN_ENCRYPT_REQ,
					ds.getBoolean(KEY_CONN_ENCRYPT_REQ));
			coreInterface.setParamString(CoreParamKeys.SPARAM_CONN_ENCRYPT_MIN_LEVEL,
					ds.getString(KEY_CONN_ENCRYPT_MIN_LEVEL));
			coreInterface.setParamBool(CoreParamKeys.BPARAM_CONN_ENCRYPT_FB_INCOMING,
					ds.getBoolean(KEY_CONN_ENCRYPT_FB_INCOMING));
			coreInterface.setParamBool(CoreParamKeys.BPARAM_CONN_ENCRYPT_FB_OUTGOING,
					ds.getBoolean(KEY_CONN_ENCRYPT_FB_OUTGOING));
			coreInterface.setParamBool(
					CoreParamKeys.BPARAM_CONN_ENCRYPT_USE_CRYPTOPORT,
					ds.getBoolean(KEY_CONN_ENCRYPT_USE_CRYPTOPORT));
		} catch (RemoteException e) {
			e.printStackTrace();
		}

	}

	@WorkerThread
	private void saveProxyPrefs() {
		if (ds == null || ds.size() == 0) {
			Log.e(TAG, "saveProxyPrefs: empty datastore "
					+ AndroidUtils.getCompressedStackTrace());
		}
		IBiglyCoreInterface coreInterface = BiglyCoreFlavorUtils.getCoreInterface();
		if (coreInterface == null) {
			return;
		}

		boolean changed = false;
		try {
			changed |= coreInterface.setParamBool(
					CoreParamKeys.BPARAM_PROXY_ENABLE_TRACKERS,
					ds.getBoolean(KEY_PROXY_ENABLED_TRACKER));
			String proxyType = ds.getString(KEY_PROXY_TYPE);
			boolean enableSOCKS = proxyType.startsWith("SOCKS");
			changed |= coreInterface.setParamBool(
					CoreParamKeys.BPARAM_PROXY_ENABLE_SOCKS, enableSOCKS);
			changed |= coreInterface.setParamString(CoreParamKeys.SPARAM_PROXY_HOST,
					ds.getString(KEY_PROXY_HOST));
			changed |= coreInterface.setParamString(CoreParamKeys.SPARAM_PROXY_PORT,
					Integer.toString(ds.getInt(KEY_PROXY_PORT, 0)));
			changed |= coreInterface.setParamString(CoreParamKeys.SPARAM_PROXY_USER,
					ds.getString(KEY_PROXY_USER));
			changed |= coreInterface.setParamString(CoreParamKeys.SPARAM_PROXY_PW,
					ds.getString(KEY_PROXY_PW));

			if (enableSOCKS) {
				changed |= coreInterface.setParamBool(
						CoreParamKeys.BPARAM_PROXY_DATA_ENABLE,
						ds.getBoolean(KEY_PROXY_ENABLED_PEER));
				String ver = "V" + proxyType.substring(5);
				changed |= coreInterface.setParamString(
						CoreParamKeys.SPARAM_PROXY_DATA_SOCKS_VER, ver);
				changed |= coreInterface.setParamBool(
						CoreParamKeys.BPARAM_PROXY_DATA_SAME, true);
			} else {
				// Technically, we can have outgoings peers on a proxy, but that requires
				// BPARAM_PROXY_DATA_SAME as false, and separate config param keys
				// just for data.
				// For now, that's a lot of confusing UI, so we limit using data proxy 
				// only when socks proxy for trackers is on
				changed |= coreInterface.setParamBool(
						CoreParamKeys.BPARAM_PROXY_DATA_ENABLE, false);
			}

			if (changed) {
				BiglyCoreUtils.restartCoreService();
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	@WorkerThread
	private void saveRemoteAccessPrefs() {
		if (ds == null || ds.size() == 0) {
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
		if (ds == null) {
			return;
		}

		final String screenKey = preferenceScreen.getKey();

		if (KEY_RACCESS_SCREEN.equals(screenKey)) {
			CorePrefs corePrefs = CorePrefs.getInstance();
			CoreRemoteAccessPreferences raPrefs = corePrefs.getRemoteAccessPreferences();
			if (raPrefs == null) {
				return;
			}

			ds.putBoolean(KEY_ALLOW_LAN_ACCESS, raPrefs.allowLANAccess);
			ds.putBoolean(KEY_RACCESS_REQPW, raPrefs.reqPW);
			ds.putString(KEY_RACCESS_USER, raPrefs.user);
			ds.putString(KEY_RACCESS_PW, raPrefs.pw);
		} else if (KEY_PROXY_SCREEN.equals(screenKey)) {
			IBiglyCoreInterface coreInterface = BiglyCoreFlavorUtils.getCoreInterface();
			if (coreInterface == null) {
				return;
			}
			try {
				ds.putBoolean(KEY_PROXY_ENABLED_TRACKER, coreInterface.getParamBool(
						CoreParamKeys.BPARAM_PROXY_ENABLE_TRACKERS));
				ds.putBoolean(KEY_PROXY_ENABLED_PEER,
						coreInterface.getParamBool(CoreParamKeys.BPARAM_PROXY_DATA_ENABLE));
				String proxyType = coreInterface.getParamString(
						CoreParamKeys.SPARAM_PROXY_DATA_SOCKS_VER); // V4, V4a, V5
				boolean enableSocks = coreInterface.getParamBool(
						CoreParamKeys.BPARAM_PROXY_ENABLE_SOCKS);
				proxyType = proxyType.startsWith("V") && enableSocks
						? "SOCKS" + proxyType.substring(1) : "HTTP";
				ds.putString(KEY_PROXY_TYPE, proxyType);
				ds.putString(KEY_PROXY_HOST,
						coreInterface.getParamString(CoreParamKeys.SPARAM_PROXY_HOST));
				ds.putString(KEY_PROXY_USER,
						coreInterface.getParamString(CoreParamKeys.SPARAM_PROXY_USER));
				ds.putString(KEY_PROXY_PW,
						coreInterface.getParamString(CoreParamKeys.SPARAM_PROXY_PW));
				String portString = coreInterface.getParamString(
						CoreParamKeys.SPARAM_PROXY_PORT);
				int port = 0;
				try {
					port = Integer.parseInt(portString);
				} catch (NumberFormatException ignore) {
				}
				ds.putInt(KEY_PROXY_HOST, port);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		} else if (KEY_CONN_ENCRYPT_SCREEN.equals(screenKey)) {
			IBiglyCoreInterface coreInterface = BiglyCoreFlavorUtils.getCoreInterface();
			if (coreInterface == null) {
				return;
			}
			try {
				ds.putBoolean(KEY_CONN_ENCRYPT_REQ,
						coreInterface.getParamBool(CoreParamKeys.BPARAM_CONN_ENCRYPT_REQ));
				ds.putString(KEY_CONN_ENCRYPT_MIN_LEVEL, coreInterface.getParamString(
						CoreParamKeys.SPARAM_CONN_ENCRYPT_MIN_LEVEL));
				ds.putBoolean(KEY_CONN_ENCRYPT_FB_INCOMING, coreInterface.getParamBool(
						CoreParamKeys.BPARAM_CONN_ENCRYPT_FB_INCOMING));
				ds.putBoolean(KEY_CONN_ENCRYPT_FB_OUTGOING, coreInterface.getParamBool(
						CoreParamKeys.BPARAM_CONN_ENCRYPT_FB_OUTGOING));
				ds.putBoolean(KEY_CONN_ENCRYPT_USE_CRYPTOPORT,
						coreInterface.getParamBool(
								CoreParamKeys.BPARAM_CONN_ENCRYPT_USE_CRYPTOPORT));
			} catch (RemoteException e) {
				e.printStackTrace();
			}
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
