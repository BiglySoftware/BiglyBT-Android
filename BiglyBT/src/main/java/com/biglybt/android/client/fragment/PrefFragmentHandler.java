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

import com.biglybt.android.client.R;
import com.biglybt.android.client.*;
import com.biglybt.android.client.activity.SessionActivity;
import com.biglybt.android.client.dialog.*;
import com.biglybt.android.client.session.*;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.Thunk;

import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.*;

/**
 * Created by TuxPaper on 10/22/17.
 */

public class PrefFragmentHandler
{

	private static final String KEY_SESSION_DOWNLOAD = "session_download";

	private static final String KEY_SESSION_UPLOAD = "session_upload";

	private static final String KEY_SESSION_DOWNLOAD_MANUAL = "session_download_manual";

	private static final String KEY_SESSION_UPLOAD_MANUAL = "session_upload_manual";

	private static final String KEY_SESSION_DOWNLOAD_LIMIT = "session_download_limit";

	private static final String KEY_SESSION_UPLOAD_LIMIT = "session_upload_limit";

	static final String KEY_SESSION_DOWNLOAD_PATH = "session_download_path";

	private static final String KEY_PROFILE_NICKNAME = "nickname";

	private static final String KEY_SHOW_OPEN_OPTIONS = "show_open_options";

	private static final String KEY_SMALL_LIST = "small_list";

	static final String KEY_SAVE_PATH = "save_path";

	private static final String KEY_PORT_SETTINGS = "port_settings";

	private static final String TAG = "PrefFragmentHandler";

	private static final String KEY_REFRESH_INTERVAL = "refresh_interval";

	private static final String KEY_REFRESH_INTERVAL_ENABLED = "refresh_interval_enabled";

	private static final String KEY_REFRESH_INTERVAL_MOBILE_ENABLED = "refresh_interval_mobile_enabled";

	private static final String KEY_REFRESH_INTERVAL_MOBILE_SEPARATE = "refresh_interval_mobile_separate";

	private static final String KEY_REFRESH_INTERVAL_MOBILE = "refresh_interval_mobile";

	private static final String KEY_REMOTE_CONNECTION = "remote_connection";

	private static final String KEY_THEME_DARK = "ui_theme";

	private static final String KEY_ACTION_ABOUT = "action_about";

	private static final String KEY_ACTION_GIVEBACK = "action_giveback";

	private static final String KEY_ACTION_RATE = "action_rate";

	private static final String KEY_ACTION_ISSUE = "action_issue";

	private static final String KEY_PEER_PORT_RANDOM = "peer_port_random";

	private static final String KEY_PEER_PORT = "peer_port";

	protected final SessionActivity activity;

	/** 
	 * dataStore is only stored in memory.  Persistence of config settings is
	 * done via RemoteProfile, SessionSettings, or AppPreferences setters
	 */
	PreferenceDataStoreMap ds;

	private PreferenceManager preferenceManager;

	private SessionSettingsChangedListener settingsChangedListener;

	protected PreferenceScreen preferenceScreen;

	public PrefFragmentHandler(SessionActivity activity) {
		this.activity = activity;
	}

	public void onResume() {
		Session session = activity.getSession();
		if (session == null) {
			return;
		}
		settingsChangedListener = new SessionSettingsChangedListener() {
			@Override
			public void sessionSettingsChanged(SessionSettings newSessionSettings) {
				fillDataStore();
				updateWidgets();
			}

			@Override
			public void speedChanged(long downloadSpeed, long uploadSpeed) {

			}
		};
		session.addSessionSettingsChangedListeners(settingsChangedListener);
	}

	public void onDestroy() {
		Session session = activity.getSession();
		if (session != null) {
			session.removeSessionSettingsChangedListeners(settingsChangedListener);
		}
	}

	@UiThread
	public boolean onPreferenceTreeClick(Preference preference) {
		// preference and datastore for switches will have the new value at this point

		final String key = preference.getKey();
		if (key == null) {
			return false;
		}
		switch (key) {
			case KEY_REFRESH_INTERVAL: {
				Session session = activity.getSession();
				if (session != null) {
					// will trigger sessionSettingsChanged if anything changed,
					// which will refresh datastore/widgets
					DialogFragmentRefreshInterval.openDialog(
							activity.getSupportFragmentManager(),
							session.getRemoteProfile().getID());
				}
				return true;
			}

			case KEY_REMOTE_CONNECTION: {
				Session session = activity.getSession();
				if (session != null) {
					RemoteUtils.editProfile(session.getRemoteProfile(),
							activity.getSupportFragmentManager(), false);
				}

				// TODO: Update nick if user changes it
				// Really TODO: Don't use edit profile dialog
				return true;
			}

			case KEY_SESSION_DOWNLOAD: {
				DialogFragmentNumberPicker.NumberPickerBuilder builder = new DialogFragmentNumberPicker.NumberPickerBuilder(
						activity.getSupportFragmentManager(), KEY_SESSION_DOWNLOAD,
						ds.getInt(KEY_SESSION_DOWNLOAD_LIMIT, 0)).setTitleId(
								R.string.rp_download_speed).setMin(0).setMax(99999).setSuffix(
										R.string.kbps).setClearButtonText(R.string.unlimited);
				// Results handled in onNumberPickerChange
				DialogFragmentNumberPicker.openDialog(builder);
				return true;
			}

			case KEY_SESSION_UPLOAD: {
				DialogFragmentNumberPicker.NumberPickerBuilder builder = new DialogFragmentNumberPicker.NumberPickerBuilder(
						activity.getSupportFragmentManager(), KEY_SESSION_UPLOAD,
						ds.getInt(KEY_SESSION_UPLOAD_LIMIT, 0)).setTitleId(
								R.string.rp_upload_speed).setMin(0).setMax(99999).setSuffix(
										R.string.kbps).setClearButtonText(R.string.unlimited);
				// Results handled in onNumberPickerChange
				DialogFragmentNumberPicker.openDialog(builder);
				return true;
			}

			case KEY_PROFILE_NICKNAME: {
				final Session session = activity.getSession();
				if (session != null) {
					AlertDialog dialog = AndroidUtilsUI.createTextBoxDialog(activity,
							R.string.profile_nickname, 0,
							(session.getRemoteProfile().getRemoteType() == RemoteProfile.TYPE_CORE)
									? R.string.profile_nick_explain
									: R.string.profile_localnick_explain,
							session.getRemoteProfile().getNick(), EditorInfo.IME_ACTION_DONE,
							(dialog1, which, editText) -> {
								final String newName = editText.getText().toString();

								session.getRemoteProfile().setNick(newName);
								session.triggerSessionSettingsChanged();
							});
					dialog.show();
				}
				return true;
			}

			case KEY_SMALL_LIST: {
				final Session session = activity.getSession();
				if (session != null) {
					session.getRemoteProfile().setUseSmallLists(
							((SwitchPreference) preference).isChecked());
					session.triggerSessionSettingsChanged();
				}
				return true;
			}

			case KEY_SAVE_PATH: {
				final Session session = activity.getSession();
				if (session != null) {
					DialogFragmentLocationPicker.openDialogChooser(
							session.getSessionSettingsClone().getDownloadDir(), session,
							activity.getSupportFragmentManager());
				}
				return true;
			}

			////////////// Network

			case KEY_PORT_SETTINGS: {
				final Session session = activity.getSession();
				if (session != null) {
					SessionSettings sessionSettings = session.getSessionSettingsClone();
					DialogFragmentNumberPicker.NumberPickerBuilder builder = new DialogFragmentNumberPicker.NumberPickerBuilder(
							activity.getSupportFragmentManager(), KEY_PORT_SETTINGS,
							sessionSettings.getPeerPort()).setTitleId(
									R.string.proxy_port).setShowSpinner(false).setMin(1).setMax(
											65535).setClearButtonText(
													R.string.pref_peerport_random_button);
					// Results handled in onNumberPickerChange
					DialogFragmentNumberPicker.openDialog(builder);
				}
				return true;
			}

			case KEY_SHOW_OPEN_OPTIONS: {
				final Session session = activity.getSession();
				if (session != null) {
					session.getRemoteProfile().setAddTorrentSilently(
							!((SwitchPreference) preference).isChecked());
					session.triggerSessionSettingsChanged();
				}
				return true;
			}

			////////////// UI

			case KEY_THEME_DARK: {
				boolean newIsDark = ((SwitchPreferenceCompat) preference).isChecked();

				final AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
				if (appPreferences.isThemeDark() != newIsDark) {
					appPreferences.setThemeDark(newIsDark);
					activity.recreate();
				}
				return true;
			}

			case KEY_ACTION_ABOUT: {
				DialogFragmentAbout dlg = new DialogFragmentAbout();
				AndroidUtilsUI.showDialog(dlg, activity.getSupportFragmentManager(),
						"About");
				return true;
			}

			case KEY_ACTION_GIVEBACK: {
				DialogFragmentGiveback.openDialog(activity,
						activity.getSupportFragmentManager(), true, TAG);
				return true;
			}

			case KEY_ACTION_RATE: {
				AndroidUtilsUI.openMarket(activity, activity.getPackageName());
				AnalyticsTracker.getInstance(activity).sendEvent(
						AnalyticsTracker.CAT_UI_ACTION, AnalyticsTracker.ACTION_RATING,
						"PrefClick", null);
				return true;
			}

			case KEY_ACTION_ISSUE: {
				String url = BiglyBTApp.URL_BUGS;
				Intent i = new Intent(Intent.ACTION_VIEW);
				i.setData(Uri.parse(url));
				activity.startActivity(i);
				return true;
			}

		}
		return false;
	}

	/** 
	 * Update any Preference titles/summaries/visibility based.
	 */
	final void updateWidgets() {
		if (AndroidUtilsUI.isUIThread()) {
			updateWidgetsOnUI();
		} else {
			activity.runOnUiThread(this::updateWidgetsOnUI);
		}
	}

	@UiThread
	public void updateWidgetsOnUI() {
		String s;
		Resources resources = activity.getResources();

		////////////////////////////////
		// Bandwidth
		////////////////////////////////

		final Preference prefDownload = findPreference(KEY_SESSION_DOWNLOAD);
		if (prefDownload != null) {
			if (ds.getBoolean(KEY_SESSION_DOWNLOAD_MANUAL)) {
				int dlSpeedK = ds.getInt(KEY_SESSION_DOWNLOAD_LIMIT, 0);
				s = resources.getString(R.string.setting_speed_on_summary,
						DisplayFormatters.formatByteCountToKiBEtcPerSec(dlSpeedK * 1024));
			} else {
				s = resources.getString(R.string.unlimited);
			}
			prefDownload.setSummary(s);
		}

		final Preference prefUpload = findPreference(KEY_SESSION_UPLOAD);
		if (prefUpload != null) {
			if (ds.getBoolean(KEY_SESSION_UPLOAD_MANUAL)) {
				int ulSpeedK = ds.getInt(KEY_SESSION_UPLOAD_LIMIT, 0);
				s = resources.getString(R.string.setting_speed_on_summary,
						DisplayFormatters.formatByteCountToKiBEtcPerSec(ulSpeedK * 1024));
			} else {
				s = resources.getString(R.string.unlimited);
			}
			prefUpload.setSummary(s);
		}

		////////////////////////////////
		// UI
		////////////////////////////////

		final Preference prefNickName = findPreference(KEY_PROFILE_NICKNAME);
		if (prefNickName != null) {
			prefNickName.setSummary(ds.getString(KEY_PROFILE_NICKNAME));
		}

		// Refresh Interval
		final Preference prefRefreshInterval = findPreference(KEY_REFRESH_INTERVAL);
		if (prefRefreshInterval != null) {
			boolean showIntervalMobile = BiglyBTApp.getNetworkState().hasMobileDataCapability();
			boolean updateIntervalEnabled = ds.getBoolean(
					KEY_REFRESH_INTERVAL_ENABLED);
			boolean updateIntervalMobileSeparate = ds.getBoolean(
					KEY_REFRESH_INTERVAL_MOBILE_SEPARATE);
			boolean updateIntervalMobileEnabled = ds.getBoolean(
					KEY_REFRESH_INTERVAL_MOBILE_ENABLED);
			if (updateIntervalEnabled) {

				if (showIntervalMobile) {
					if (updateIntervalMobileSeparate) {
						if (updateIntervalMobileEnabled) {
							// x refresh on non-mobile, separate mobile value
							String secs = formatTime(resources,
									ds.getInt(KEY_REFRESH_INTERVAL, 0));
							s = resources.getString(R.string.refresh_every_x_on_nonmobile,
									secs);
							s += "\n";
							secs = formatTime(resources,
									ds.getInt(KEY_REFRESH_INTERVAL_MOBILE, 0));
							s += resources.getString(R.string.refresh_every_x_on_mobile,
									secs);
						} else {
							// x refresh on non-mobile, manual on mobile
							String secs = formatTime(resources,
									ds.getInt(KEY_REFRESH_INTERVAL, 0));
							s = resources.getString(R.string.refresh_every_x_on_mobile, secs);
							s += "\n";
							s += resources.getString(R.string.refresh_manual_mobile);
						}

					} else {
						// x refresh on non-mobile
						// mobile same as non-mobile
						String secs = formatTime(resources,
								ds.getInt(KEY_REFRESH_INTERVAL, 0));
						s = resources.getString(R.string.refresh_every_x, secs);
					}
				} else {
					// x refresh on non-mobile
					// no mobile avail
					String secs = formatTime(resources,
							ds.getInt(KEY_REFRESH_INTERVAL, 0));
					s = resources.getString(R.string.refresh_every_x, secs);
				}

			} else {
				// Manual update on non-mobile

				if (showIntervalMobile) {
					if (updateIntervalMobileSeparate) {
						if (updateIntervalMobileEnabled) {
							// Manual update on non-mobile, separate mobile value
							s = resources.getString(R.string.refresh_manual_nonmobile);
							s += "\n";
							String secs = formatTime(resources,
									ds.getInt(KEY_REFRESH_INTERVAL_MOBILE, 0));
							s += resources.getString(R.string.refresh_every_x_on_mobile,
									secs);
						} else {
							// Manual update on both (both set to manual)
							s = resources.getString(R.string.manual_refresh);
						}

					} else {
						// Manual update on non-mobile
						// mobile same as non-mobile
						s = resources.getString(R.string.manual_refresh);
					}
				} else {
					// Manual update on non-mobile
					// no mobile avail
					s = resources.getString(R.string.manual_refresh);
				}
			}
			prefRefreshInterval.setSummary(s);
		}

		final SwitchPreference prefSmallList = (SwitchPreference) findPreference(
				KEY_SMALL_LIST);
		if (prefSmallList != null) {
			prefSmallList.setChecked(ds.getBoolean(KEY_SMALL_LIST));
		}

		final Preference prefSavePath = findPreference(KEY_SAVE_PATH);
		if (prefSavePath != null) {
			String sDir = ds.getString(KEY_SESSION_DOWNLOAD_PATH);
			prefSavePath.setSummary(sDir);
		}

		final SwitchPreference prefShowOpenOptions = (SwitchPreference) findPreference(
				KEY_SHOW_OPEN_OPTIONS);
		if (prefShowOpenOptions != null) {
			prefShowOpenOptions.setChecked(ds.getBoolean(KEY_SHOW_OPEN_OPTIONS));
		}

		final SwitchPreferenceCompat prefUITheme = (SwitchPreferenceCompat) findPreference(
				KEY_THEME_DARK);
		if (prefUITheme != null) {
			if (AndroidUtils.isTV(activity)) {
				prefUITheme.setVisible(false);
			} else {
				prefUITheme.setChecked(ds.getBoolean(KEY_THEME_DARK));
			}
		}

		////////////////////////////////
		// Network
		////////////////////////////////

		final Preference prefPortSettings = findPreference(KEY_PORT_SETTINGS);
		if (prefPortSettings != null) {
			int peerPort = ds.getInt(KEY_PEER_PORT, 0);
			boolean isRandom = ds.getBoolean(KEY_PEER_PORT_RANDOM);
			s = "";
			if (isRandom) {
				s = resources.getString(R.string.pref_peerport_random);
			}
			if (peerPort > 0) {
				if (s.length() > 0) {
					s += "\n";
				}
				s += resources.getString(R.string.pref_peerport_current, peerPort);
			}
			prefPortSettings.setSummary(s);
		}

		////////////////////////////////
		// Social
		////////////////////////////////

		final Preference prefIssue = findPreference(KEY_ACTION_ISSUE);
		if (prefIssue != null) {
			prefIssue.setVisible(!AndroidUtils.isTV(activity));
		}
	}

	private static String formatTime(Resources res, int secs) {
		if (secs > 90) {
			return res.getQuantityString(R.plurals.minutes, secs / 60, secs / 60);
		} else {
			return res.getQuantityString(R.plurals.seconds, secs, secs);
		}
	}

	public void onNumberPickerChange(@Nullable String callbackID, int val) {
		Session session = activity.getSession();
		if (session == null) {
			return;
		}
		SessionSettings sessionSettings = session.getSessionSettingsClone();
		if (sessionSettings == null) {
			return;
		}

		if (KEY_SESSION_DOWNLOAD.equals(callbackID)) {
			sessionSettings.setDLIsManual(val > 0);
			if (val > 0) {
				sessionSettings.setManualDlSpeed(val);
			}
			session.updateSessionSettings(sessionSettings);
		}
		if (KEY_SESSION_UPLOAD.equals(callbackID)) {
			sessionSettings.setULIsManual(val > 0);
			if (val > 0) {
				sessionSettings.setManualUlSpeed(val);
			}
			session.updateSessionSettings(sessionSettings);
		}
		if (KEY_PORT_SETTINGS.equals(callbackID)) {
			boolean nowRandom = val <= 0;
			sessionSettings.setRandomPeerPort(nowRandom);
			if (!nowRandom) {
				sessionSettings.setPeerPort(val);
			}
			session.updateSessionSettings(sessionSettings);
		}
	}

	public void locationChanged(String location) {
		Session session = activity.getSession();
		if (session == null) {
			return;
		}

		SessionSettings sessionSettings = session.getSessionSettingsClone();
		if (sessionSettings == null) {
			return;
		}

		sessionSettings.setDownloadDir(location);
		session.updateSessionSettings(sessionSettings);
	}

	public Preference findPreference(String key) {
		return preferenceManager.findPreference(key);
	}

	public void setPreferenceScreen(PreferenceManager preferenceManager,
			PreferenceScreen preferenceScreen) {
		this.preferenceManager = preferenceManager;
		this.preferenceScreen = preferenceScreen;

		ds = new PreferenceDataStoreMap(null);
		fillDataStore();

		preferenceManager.setPreferenceDataStore(ds);
	}

	@Thunk
	void fillDataStore() {
		Session session = activity.getSession();
		if (session == null) {
			return;
		}
		SessionSettings sessionSettings = session.getSessionSettingsClone();
		if (sessionSettings == null) {
			return;
		}

		RemoteProfile profile = session.getRemoteProfile();

		boolean ulManual = sessionSettings.isUlManual();
		long ulSpeedK = sessionSettings.getManualUlSpeed();
		ds.putBoolean(KEY_SESSION_UPLOAD_MANUAL, ulManual);
		ds.putLong(KEY_SESSION_UPLOAD_LIMIT, ulSpeedK);

		boolean dlManual = sessionSettings.isDlManual();
		long dlSpeedK = sessionSettings.getManualDlSpeed();
		ds.putBoolean(KEY_SESSION_DOWNLOAD_MANUAL, dlManual);
		ds.putLong(KEY_SESSION_DOWNLOAD_LIMIT, dlSpeedK);

		String nick = profile.getNick();
		ds.putString(KEY_PROFILE_NICKNAME, nick);

		boolean themeDark = BiglyBTApp.getAppPreferences().isThemeDark();
		ds.putBoolean(KEY_THEME_DARK, themeDark);

		boolean addTorrentSilently = profile.isAddTorrentSilently();
		ds.putBoolean(KEY_SHOW_OPEN_OPTIONS, !addTorrentSilently);

		boolean useSmallLists = profile.useSmallLists();
		ds.putBoolean(KEY_SMALL_LIST, useSmallLists);

		String downloadDir = sessionSettings.getDownloadDir();
		ds.putString(KEY_SESSION_DOWNLOAD_PATH, downloadDir);

		ds.putLong(KEY_REFRESH_INTERVAL, profile.getUpdateInterval());
		ds.putLong(KEY_REFRESH_INTERVAL_MOBILE, profile.getUpdateIntervalMobile());
		ds.putBoolean(KEY_REFRESH_INTERVAL_ENABLED,
				profile.isUpdateIntervalEnabled());
		ds.putBoolean(KEY_REFRESH_INTERVAL_MOBILE_ENABLED,
				profile.isUpdateIntervalMobileEnabled());
		ds.putBoolean(KEY_REFRESH_INTERVAL_MOBILE_SEPARATE,
				profile.isUpdateIntervalMobileSeparate());

		ds.putBoolean(KEY_PEER_PORT_RANDOM, sessionSettings.isRandomPeerPort());
		ds.putInt(KEY_PEER_PORT, sessionSettings.getPeerPort());
	}

	public void onPreferenceScreenClosed(PreferenceScreen preferenceScreen) {
		Session session = activity.getSession();
		if (session != null) {
			session.saveProfile();
		}
	}
}
