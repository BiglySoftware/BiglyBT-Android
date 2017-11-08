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
import com.biglybt.android.client.dialog.DialogFragmentRefreshInterval;
import com.biglybt.android.client.session.*;
import com.biglybt.util.DisplayFormatters;

import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

/**
 * Created by TuxPaper on 10/22/17.
 */

public class PrefFragmentHandler
{

	static final String KEY_SESSION_DOWNLOAD = "session_download";

	static final String KEY_SESSION_UPLOAD = "session_upload";

	static final String KEY_SESSION_DOWNLOAD_MANUAL = "session_download_maual";

	static final String KEY_SESSION_UPLOAD_MANUAL = "session_upload_manual";

	static final String KEY_SESSION_DOWNLOAD_LIMIT = "session_download_limit";

	static final String KEY_SESSION_UPLOAD_LIMIT = "session_upload_limit";

	static final String KEY_PROFILE_NICKNAME = "nickname";

	static final String KEY_SHOW_OPEN_OPTIONS = "show_open_options";

	static final String KEY_SMALL_LIST = "small_list";

	private static final String TAG = "PrefFragmentHandler";

	private static final String KEY_REFRESH_INTERVAL = "refresh_interval";

	private static final String KEY_REMOTE_CONNECTION = "remote_connection";

	private static final String KEY_THEME_DARK = "ui_theme";

	protected final SessionActivity activity;

	PreferenceDataStoreMap dataStore;

	private PreferenceManager preferenceManager;

	private SessionSettingsChangedListener settingsChangedListener;

	public PrefFragmentHandler(SessionActivity activity) {
		this.activity = activity;
	}

	public void onCreate(Bundle savedInstanceState,
			PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
		dataStore = new PreferenceDataStoreMap();

		Session session = activity.getSession();
		if (session == null) {
			return;
		}
		settingsChangedListener = new SessionSettingsChangedListener() {
			@Override
			public void sessionSettingsChanged(SessionSettings newSessionSettings) {
				fillDataStore();
			}

			@Override
			public void speedChanged(long downloadSpeed, long uploadSpeed) {

			}
		};
		session.addSessionSettingsChangedListeners(settingsChangedListener);

		preferenceManager.setPreferenceDataStore(dataStore);
	}

	public void onDestroy() {
		Session session = activity.getSession();
		if (session != null) {
			session.removeSessionSettingsChangedListeners(settingsChangedListener);
		}
	}

	public boolean onPreferenceTreeClick(Preference preference) {
		switch (preference.getKey()) {
			case KEY_REFRESH_INTERVAL: {
				Session session = activity.getSession();
				if (session != null) {
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
							activity.getSupportFragmentManager());
				}

//					// TODO: Update nick if user changes it
//					// Really TODO: Don't use edit profile dialog
				return true;
			}

			case KEY_SESSION_DOWNLOAD: {
				DialogFragmentNumberPicker.NumberPickerBuilder builder = new DialogFragmentNumberPicker.NumberPickerBuilder(
						activity.getSupportFragmentManager(), KEY_SESSION_DOWNLOAD,
						dataStore.getInt(KEY_SESSION_DOWNLOAD_LIMIT, 0)).setTitleId(
								R.string.rp_download_speed).setMin(0).setMax(99999).setSuffix(
										R.string.kbps).setClearButtonText(R.string.unlimited);
				DialogFragmentNumberPicker.openDialog(builder);
				return true;
			}

			case KEY_SESSION_UPLOAD: {
				DialogFragmentNumberPicker.NumberPickerBuilder builder = new DialogFragmentNumberPicker.NumberPickerBuilder(
						activity.getSupportFragmentManager(), KEY_SESSION_UPLOAD,
						dataStore.getInt(KEY_SESSION_UPLOAD_LIMIT, 0)).setTitleId(
								R.string.rp_upload_speed).setMin(0).setMax(99999).setSuffix(
										R.string.kbps).setClearButtonText(R.string.unlimited);
				DialogFragmentNumberPicker.openDialog(builder);

				return true;
			}

			case KEY_PROFILE_NICKNAME: {
				final Session session = activity.getSession();
				if (session != null) {
					AlertDialog dialog = AndroidUtilsUI.createTextBoxDialog(activity,
							R.string.profile_nickname,
							(session.getRemoteProfile().getRemoteType() == RemoteProfile.TYPE_CORE)
									? R.string.profile_nick_explain
									: R.string.profile_localnick_explain,
							session.getRemoteProfile().getNick(), EditorInfo.IME_ACTION_DONE,
							new AndroidUtilsUI.OnTextBoxDialogClick() {

								@Override
								public void onClick(DialogInterface dialog, int which,
										EditText editText) {
									final String newName = editText.getText().toString();

									session.getRemoteProfile().setNick(newName);
									session.triggerSessionSettingsChanged();
								}
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

			case KEY_SHOW_OPEN_OPTIONS: {
				final Session session = activity.getSession();
				if (session != null) {
					session.getRemoteProfile().setAddTorrentSilently(
							!((SwitchPreference) preference).isChecked());
					session.triggerSessionSettingsChanged();
				}
				return true;
			}

			case KEY_THEME_DARK: {
				boolean newIsDark = ((SwitchPreference) preference).isChecked();

				final AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
				if (appPreferences.isThemeDark() != newIsDark) {
					appPreferences.setThemeDark(newIsDark);
					activity.recreate();
				}
			}
		}
		return false;
	}

	public void fillDataStore() {
		if (!AndroidUtilsUI.isUIThread()) {
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					fillDataStore();
				}
			});
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

		String s;
		Resources resources = activity.getResources();
		RemoteProfile profile = session.getRemoteProfile();

		boolean dlManual = sessionSettings.isDlManual();
		long dlSpeedK = sessionSettings.getManualDlSpeed();
		dataStore.putBoolean(PrefFragmentHandler.KEY_SESSION_DOWNLOAD_MANUAL,
				dlManual);
		dataStore.putLong(PrefFragmentHandler.KEY_SESSION_DOWNLOAD_LIMIT, dlSpeedK);
		if (dlManual) {
			s = resources.getString(R.string.setting_speed_on_summary,
					DisplayFormatters.formatByteCountToKiBEtcPerSec(dlSpeedK * 1024));
		} else {
			s = resources.getString(R.string.unlimited);
		}
		findPreference(PrefFragmentHandler.KEY_SESSION_DOWNLOAD).setSummary(s);

		boolean ulManual = sessionSettings.isUlManual();
		dataStore.putBoolean(PrefFragmentHandler.KEY_SESSION_UPLOAD_MANUAL,
				ulManual);
		dataStore.putBoolean(PrefFragmentHandler.KEY_SESSION_UPLOAD_LIMIT,
				ulManual);
		long ulSpeedK = sessionSettings.getManualUlSpeed();
		if (ulManual) {
			s = resources.getString(R.string.setting_speed_on_summary,
					DisplayFormatters.formatByteCountToKiBEtcPerSec(ulSpeedK * 1024));
		} else {
			s = resources.getString(R.string.unlimited);
		}
		findPreference(PrefFragmentHandler.KEY_SESSION_UPLOAD).setSummary(s);

		String nick = profile.getNick();
		dataStore.putString(PrefFragmentHandler.KEY_PROFILE_NICKNAME, nick);
		findPreference(PrefFragmentHandler.KEY_PROFILE_NICKNAME).setSummary(nick);

		// Refresh Interval... TODO
		boolean showIntervalMobile = BiglyBTApp.getNetworkState().hasMobileDataCapability();
		boolean updateIntervalEnabled = profile.isUpdateIntervalEnabled();
		boolean updateIntervalMobileSeparate = profile.isUpdateIntervalMobileSeparate();
		boolean updateIntervalMobileEnabled = profile.isUpdateIntervalMobileEnabled();
		if (updateIntervalEnabled) {

			if (showIntervalMobile) {
				if (updateIntervalMobileSeparate) {
					if (updateIntervalMobileEnabled) {
						// x refresh on non-mobile, separate mobile value
						String secs = formatTime(resources,
								(int) profile.getUpdateInterval());
						s = resources.getString(R.string.refresh_every_x_on_nonmobile,
								secs);
						s += "\n";
						secs = formatTime(resources,
								(int) profile.getUpdateIntervalMobile());
						s += resources.getString(R.string.refresh_every_x_on_mobile, secs);
					} else {
						// x refresh on non-mobile, manual on mobile
						String secs = formatTime(resources,
								(int) profile.getUpdateInterval());
						s = resources.getString(R.string.refresh_every_x_on_mobile, secs);
						s += "\n";
						s += resources.getString(R.string.refresh_manual_mobile);
					}

				} else {
					// x refresh on non-mobile
					// mobile same as non-mobile
					String secs = formatTime(resources,
							(int) profile.getUpdateInterval());
					s = resources.getString(R.string.refresh_every_x, secs);
				}
			} else {
				// x refresh on non-mobile
				// no mobile avail
				String secs = formatTime(resources, (int) profile.getUpdateInterval());
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
								(int) profile.getUpdateIntervalMobile());
						s += resources.getString(R.string.refresh_every_x_on_mobile, secs);
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
		findPreference(PrefFragmentHandler.KEY_REFRESH_INTERVAL).setSummary(s);

		boolean useSmallLists = profile.useSmallLists();
		dataStore.putBoolean(PrefFragmentHandler.KEY_SMALL_LIST, useSmallLists);
		((SwitchPreference) findPreference(
				PrefFragmentHandler.KEY_SMALL_LIST)).setChecked(useSmallLists);

		boolean addTorrentSilently = profile.isAddTorrentSilently();
		dataStore.putBoolean(PrefFragmentHandler.KEY_SHOW_OPEN_OPTIONS,
				!addTorrentSilently);
		((SwitchPreference) findPreference(
				PrefFragmentHandler.KEY_SHOW_OPEN_OPTIONS)).setChecked(
						!addTorrentSilently);

		final SwitchPreference prefUITheme = (SwitchPreference) findPreference(
				KEY_THEME_DARK);
		if (prefUITheme != null) {
			boolean themeDark = BiglyBTApp.getAppPreferences().isThemeDark();
			dataStore.putBoolean(KEY_THEME_DARK, themeDark);
			prefUITheme.setChecked(themeDark);
		}
	}

	private String formatTime(Resources res, int secs) {
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

		if (PrefFragmentHandler.KEY_SESSION_DOWNLOAD.equals(callbackID)) {
			sessionSettings.setDLIsManual(val > 0);
			if (val > 0) {
				sessionSettings.setManualDlSpeed(val);
			}
			session.updateSessionSettings(sessionSettings);
		}
		if (PrefFragmentHandler.KEY_SESSION_UPLOAD.equals(callbackID)) {
			sessionSettings.setULIsManual(val > 0);
			if (val > 0) {
				sessionSettings.setManualUlSpeed(val);
			}
			session.updateSessionSettings(sessionSettings);
		}
	}

	public Preference findPreference(String key) {
		return preferenceManager.findPreference(key);
	}
}
