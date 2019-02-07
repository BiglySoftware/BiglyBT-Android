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
import com.biglybt.android.client.session.*;
import com.biglybt.android.util.JSONUtils;
import com.biglybt.util.Thunk;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import android.view.View;
import android.widget.EditText;

import net.i2p.android.ui.I2PAndroidHelper;

public class DialogFragmentBiglyBTRemoteProfile
	extends DialogFragmentBase
{
	private GenericRemoteProfileListener mListener;

	private RemoteProfile remoteProfile;

	private EditText textNick;

	private EditText textAC;

	private SwitchCompat switchI2POnly;

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		Bundle arguments = getArguments();

		if (arguments != null) {
			String remoteProfileID = arguments.getString(SessionManager.BUNDLE_KEY);

			if (remoteProfileID != null
					&& SessionManager.hasSession(remoteProfileID)) {
				Session session = SessionManager.getSession(remoteProfileID, null,
						null);
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
					throw new IllegalStateException("No remote profile");
				}
			} else {
				throw new IllegalStateException("No remote.json");
			}
		}

		AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				getActivity(), R.layout.dialog_biglybt_remote_preferences);

		AlertDialog.Builder builder = alertDialogBuilder.builder;

		// Add action buttons
		builder.setPositiveButton(android.R.string.ok,
				(dialog, id) -> saveAndClose());
		builder.setNegativeButton(android.R.string.cancel, (dialog,
				id) -> DialogFragmentBiglyBTRemoteProfile.this.getDialog().cancel());

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
}
