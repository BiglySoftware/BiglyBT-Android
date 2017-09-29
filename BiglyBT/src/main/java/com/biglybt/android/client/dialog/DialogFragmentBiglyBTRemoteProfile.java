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

import com.biglybt.android.client.*;
import com.biglybt.android.client.AndroidUtilsUI.AlertDialogBuilder;
import com.biglybt.android.client.dialog.DialogFragmentGenericRemoteProfile.GenericRemoteProfileListener;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.util.JSONUtils;
import android.support.v7.widget.SwitchCompat;
import com.biglybt.util.Thunk;

import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.EditText;

import net.i2p.android.ui.I2PAndroidHelper;

public class DialogFragmentBiglyBTRemoteProfile
	extends DialogFragmentBase
{

	private static final String TAG = "BiglyBTProfileEdit";

	private GenericRemoteProfileListener mListener;

	private RemoteProfile remoteProfile;

	private EditText textNick;

	private EditText textAC;

	private SwitchCompat switchI2POnly;

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		Bundle arguments = getArguments();

		String remoteAsJSON = arguments == null ? null
				: arguments.getString(RemoteUtils.KEY_REMOTE_JSON);
		if (remoteAsJSON != null) {
			try {
				remoteProfile = new RemoteProfile(JSONUtils.decodeJSON(remoteAsJSON));
			} catch (Exception e) {
				throw new IllegalStateException("No remote profile");
			}
		} else {
			throw new IllegalStateException("No remote.json");
		}

		AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				getActivity(), R.layout.dialog_biglybt_remote_preferences);

		Builder builder = alertDialogBuilder.builder;

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
						DialogFragmentBiglyBTRemoteProfile.this.getDialog().cancel();
					}
				});

		final View view = alertDialogBuilder.view;

		textNick = view.findViewById(R.id.profile_nick);
		textNick.setText(remoteProfile.getNick());
		textAC = view.findViewById(R.id.profile_ac);
		textAC.setText(remoteProfile.getAC());
		switchI2POnly = view.findViewById(R.id.profile_only_i2p);
		switchI2POnly.setChecked(remoteProfile.getI2POnly());
		I2PAndroidHelper i2PAndroidHelper = new I2PAndroidHelper(getContext());
		switchI2POnly.setVisibility(
				i2PAndroidHelper.isI2PAndroidInstalled() ? View.VISIBLE : View.GONE);

		return builder.create();
	}

	@Override
	public void onAttach(Context activity) {
		super.onAttach(activity);

		if (activity instanceof GenericRemoteProfileListener) {
			mListener = (GenericRemoteProfileListener) activity;
		}
	}

	@Thunk
	void saveAndClose() {

		remoteProfile.setNick(textNick.getText().toString());
		remoteProfile.setAC(textAC.getText().toString());
		remoteProfile.setI2POnly(switchI2POnly.isChecked());

		AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
		appPreferences.addRemoteProfile(remoteProfile);

		if (mListener != null) {
			mListener.profileEditDone(remoteProfile, remoteProfile);
		}
	}

	@Override
	public String getLogTag() {
		return TAG;
	}
}
