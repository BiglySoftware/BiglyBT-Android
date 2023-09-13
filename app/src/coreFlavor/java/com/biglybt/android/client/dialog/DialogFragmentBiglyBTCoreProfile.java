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

package com.biglybt.android.client.dialog;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.biglybt.android.client.*;
import com.biglybt.android.client.AndroidUtilsUI.AlertDialogBuilder;
import com.biglybt.android.client.session.*;
import com.biglybt.android.util.JSONUtils;
import com.biglybt.util.Thunk;

import net.grandcentrix.tray.TrayPreferences;

import java.util.HashMap;
import java.util.Map;

public class DialogFragmentBiglyBTCoreProfile
	extends DialogFragmentBase
{

	private static final String TAG = "BiglyBTProfileEdit";

	private DialogFragmentGenericRemoteProfile.GenericRemoteProfileListener mListener;

	private RemoteProfile remoteProfile;

	private EditText textNick;

	private CompoundButton switchCoreStartup;

	private CompoundButton switchCoreAllowCellData;

	private CompoundButton switchCoreDisableSleep;

	private CompoundButton switchCoreOnlyPluggedIn;

	private CompoundButton switchUITheme;

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		FragmentActivity activity = getActivity();
		if (activity == null) {
			throw new IllegalStateException("No activity");
		}

		Bundle arguments = getArguments();

		if (arguments != null) {
			String remoteProfileID = arguments.getString(SessionManager.BUNDLE_KEY);

			if (remoteProfileID != null
					&& SessionManager.hasSession(remoteProfileID)) {
				Session session = SessionManager.getSession(remoteProfileID, null);
				if (session != null) {
					remoteProfile = session.getRemoteProfile();
				}
			}
		}

		if (remoteProfile == null) {
			String remoteAsJSON = arguments == null ? null
					: arguments.getString(RemoteUtils.KEY_REMOTE_JSON);
			if (remoteAsJSON != null) {
				try {
					remoteProfile = RemoteProfileFactory.create(
							JSONUtils.decodeJSON(remoteAsJSON));
				} catch (Exception e) {
					throw new IllegalStateException("No profile");
				}
			} else {
				throw new IllegalStateException("No remote.json");
			}
		}

		AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				activity, R.layout.dialog_biglybt_core_preferences);

		AlertDialog.Builder builder = alertDialogBuilder.builder;

		// Add action buttons
		builder.setPositiveButton(android.R.string.ok,
				(dialog, id) -> saveAndClose());
		builder.setNegativeButton(android.R.string.cancel, (dialog,
				id) -> DialogFragmentBiglyBTCoreProfile.this.getDialog().cancel());

		final View view = alertDialogBuilder.view;

		textNick = view.findViewById(R.id.profile_nick);
		textNick.setText(remoteProfile.getNick());

		AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
		boolean alreadyExists = appPreferences.remoteExists(remoteProfile.getID());

		CorePrefs corePrefs = CorePrefs.getInstance();
		switchCoreStartup = view.findViewById(R.id.profile_core_startup);
		boolean prefAutoStart = !alreadyExists || corePrefs.getPrefAutoStart();
		switchCoreStartup.setChecked(prefAutoStart);

		switchUITheme = view.findViewById(R.id.profile_theme);
		if (AndroidUtils.isTV(requireContext())) {
			switchUITheme.setVisibility(View.GONE);
		} else {
			boolean prefUITheme = appPreferences.isThemeDark();
			switchUITheme.setChecked(prefUITheme);
			switchUITheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
				if (appPreferences.isThemeDark() != isChecked) {
					appPreferences.setThemeDark(isChecked);
					activity.recreate();
				}
			});
			switchUITheme.setVisibility(View.VISIBLE);
		}

		switchCoreAllowCellData = view.findViewById(
				R.id.profile_core_allowcelldata);
		switchCoreAllowCellData.setVisibility(
				BiglyBTApp.getNetworkState().hasMobileDataCapability() ? View.VISIBLE
						: View.GONE);
		switchCoreAllowCellData.setChecked(corePrefs.getPrefAllowCellData());

		switchCoreDisableSleep = view.findViewById(R.id.profile_core_disablesleep);
		//noinspection ConstantConditions
		switchCoreDisableSleep.setVisibility(
				getContext().getPackageManager().hasSystemFeature(
						PackageManager.FEATURE_WIFI)
						&& AndroidUtils.hasPermisssion(BiglyBTApp.getContext(),
								Manifest.permission.WAKE_LOCK) ? View.VISIBLE : View.GONE);
		switchCoreDisableSleep.setChecked(corePrefs.getPrefDisableSleep());

		switchCoreOnlyPluggedIn = view.findViewById(
				R.id.profile_core_onlypluggedin);
		// Would be nice to have a way to detect a device that doesn't have a battery
		// Unfortunately, TV's return present=true for BatteryManager, and no
		// flag to indicate no-battery (plugged=BATTERY_PLUGGED_AC can be a
		// device temporarily attached to wall USB charger)
		switchCoreOnlyPluggedIn.setVisibility(
				AndroidUtils.isTV(getContext()) ? View.GONE : View.VISIBLE);
		switchCoreOnlyPluggedIn.setChecked(corePrefs.getPrefOnlyPluggedIn());

		return builder.create();
	}

	@Override
	public void onAttach(@NonNull Context activity) {
		super.onAttach(activity);

		if (activity instanceof DialogFragmentGenericRemoteProfile.GenericRemoteProfileListener) {
			mListener = (DialogFragmentGenericRemoteProfile.GenericRemoteProfileListener) activity;
		}
	}

	@UiThread
	@Thunk
	void saveAndClose() {

		remoteProfile.setNick(textNick.getText().toString());

		AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
		appPreferences.addRemoteProfile(remoteProfile);

		Map<String, Boolean> mapPrefsToChange = new HashMap<>();

		if (switchCoreStartup.getVisibility() == View.VISIBLE) {
			boolean b = switchCoreStartup.isChecked();
			mapPrefsToChange.put(CorePrefs.PREF_CORE_AUTOSTART, b);
		}
		if (switchCoreDisableSleep.getVisibility() == View.VISIBLE) {
			boolean b = switchCoreDisableSleep.isChecked();
			mapPrefsToChange.put(CorePrefs.PREF_CORE_DISABLESLEEP, b);
		}
		if (switchCoreAllowCellData.getVisibility() == View.VISIBLE) {
			boolean b = switchCoreAllowCellData.isChecked();
			mapPrefsToChange.put(CorePrefs.PREF_CORE_ALLOWCELLDATA, b);
		}
		if (switchCoreOnlyPluggedIn.getVisibility() == View.VISIBLE) {
			boolean b = switchCoreOnlyPluggedIn.isChecked();
			mapPrefsToChange.put(CorePrefs.PREF_CORE_ONLYPLUGGEDIN, b);
		}

		if (!mapPrefsToChange.isEmpty()) {
			OffThread.runOffUIThread(() -> {
				TrayPreferences prefs = appPreferences.getPreferences();
				for (String key : mapPrefsToChange.keySet()) {
					Boolean val = mapPrefsToChange.get(key);
					prefs.put(key, val);
				}
			});
		}

		if (mListener != null) {
			mListener.profileEditDone(remoteProfile, remoteProfile);
		}
	}
}
