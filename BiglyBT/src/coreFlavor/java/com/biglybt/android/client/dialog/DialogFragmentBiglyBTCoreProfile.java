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

import java.util.ArrayList;

import com.biglybt.android.client.*;
import com.biglybt.android.client.AndroidUtilsUI.AlertDialogBuilder;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.client.session.RemoteProfileFactory;
import com.biglybt.android.util.JSONUtils;
import com.biglybt.util.Thunk;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;

import net.grandcentrix.tray.TrayPreferences;

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

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		Bundle arguments = getArguments();

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

		AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				getActivity(), R.layout.dialog_biglybt_core_preferences);

		AlertDialog.Builder builder = alertDialogBuilder.builder;

		// Add action buttons
		builder.setPositiveButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						saveAndClose();
					}
				});
		builder.setNegativeButton(android.R.string.cancel,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						DialogFragmentBiglyBTCoreProfile.this.getDialog().cancel();
					}
				});

		final View view = alertDialogBuilder.view;

		textNick = view.findViewById(R.id.profile_nick);
		textNick.setText(remoteProfile.getNick());

		AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
		boolean alreadyExists = appPreferences.remoteExists(remoteProfile.getID());

		CorePrefs corePrefs = CorePrefs.getInstance();
		switchCoreStartup = view.findViewById(R.id.profile_core_startup);
		Boolean prefAutoStart = !alreadyExists ? true
				: corePrefs.getPrefAutoStart();
		switchCoreStartup.setChecked(prefAutoStart);

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
	public void onAttach(Context activity) {
		super.onAttach(activity);

		if (activity instanceof DialogFragmentGenericRemoteProfile.GenericRemoteProfileListener) {
			mListener = (DialogFragmentGenericRemoteProfile.GenericRemoteProfileListener) activity;
		}
	}

	@Thunk
	void saveAndClose() {

		remoteProfile.setNick(textNick.getText().toString());

		AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
		appPreferences.addRemoteProfile(remoteProfile);

		TrayPreferences prefs = appPreferences.getPreferences();

		ArrayList<String> permissionsNeeded = new ArrayList<>(4);
		permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

		if (switchCoreStartup.getVisibility() == View.VISIBLE) {
			boolean b = switchCoreStartup.isChecked();
			prefs.put(CorePrefs.PREF_CORE_AUTOSTART, b);
			if (b) {
				permissionsNeeded.add(Manifest.permission.RECEIVE_BOOT_COMPLETED);
			}
		}
		if (switchCoreDisableSleep.getVisibility() == View.VISIBLE) {
			boolean b = switchCoreDisableSleep.isChecked();
			prefs.put(CorePrefs.PREF_CORE_DISABLESLEEP, b);
			if (b) {
				permissionsNeeded.add(Manifest.permission.WAKE_LOCK);
			}
		}
		if (switchCoreAllowCellData.getVisibility() == View.VISIBLE) {
			boolean b = switchCoreAllowCellData.isChecked();
			prefs.put(CorePrefs.PREF_CORE_ALLOWCELLDATA, b);
		}
		if (switchCoreOnlyPluggedIn.getVisibility() == View.VISIBLE) {
			boolean b = switchCoreOnlyPluggedIn.isChecked();
			prefs.put(CorePrefs.PREF_CORE_ONLYPLUGGEDIN, b);
		}

		if (permissionsNeeded.size() > 0) {
			AndroidUtilsUI.requestPermissions(getActivity(),
					permissionsNeeded.toArray(new String[0]), null,
					null);
		}

		if (mListener != null) {
			mListener.profileEditDone(remoteProfile, remoteProfile);
		}
	}
}
