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
import com.biglybt.android.client.session.*;
import com.biglybt.android.util.JSONUtils;
import com.biglybt.util.Thunk;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

public class DialogFragmentGenericRemoteProfile
	extends DialogFragmentBase
{

	public static final String TAG = "GenericProfileEdit";

	public interface GenericRemoteProfileListener
	{
		void profileEditDone(RemoteProfile oldProfile, RemoteProfile newProfile);
	}

	private GenericRemoteProfileListener mListener;

	private EditText textHost;

	private RemoteProfile remoteProfile;

	private EditText textNick;

	private EditText textPort;

	private EditText textPW;

	private EditText textUser;

	private CheckBox cbUseHttps;

	boolean reqPW;

	@SuppressLint("SetTextI18n")
	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				getActivity(), R.layout.dialog_generic_remote_preferences);

		AlertDialog.Builder builder = alertDialogBuilder.builder;

		// Add action buttons
		builder.setPositiveButton(android.R.string.ok, (dialog, id) -> {
			// onClick handled in onResume
		});
		builder.setNegativeButton(android.R.string.cancel, (dialog,
				id) -> DialogFragmentGenericRemoteProfile.this.getDialog().cancel());

		final View view = alertDialogBuilder.view;

		Bundle arguments = getArguments();

		if (arguments != null) {
			String remoteProfileID = arguments.getString(SessionManager.BUNDLE_KEY);

			if (remoteProfileID != null) {
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
					remoteProfile = RemoteProfileFactory.create(
							RemoteProfile.TYPE_NORMAL);
				}
			} else {
				remoteProfile = RemoteProfileFactory.create(RemoteProfile.TYPE_NORMAL);
			}
		}

		reqPW = arguments != null
				&& arguments.getBoolean(RemoteUtils.KEY_REQ_PW, false);

		textHost = view.findViewById(R.id.profile_host);
		textHost.setText(remoteProfile.getHost());
		textNick = view.findViewById(R.id.profile_nick);
		textNick.setText(remoteProfile.getNick());
		textPort = view.findViewById(R.id.profile_port);
		textPort.setText(Integer.toString(remoteProfile.getPort()));
		textPW = view.findViewById(R.id.profile_pw);
		textPW.setText(remoteProfile.getAC());
		textUser = view.findViewById(R.id.profile_user);
		textUser.setText(remoteProfile.getUser());
		cbUseHttps = view.findViewById(R.id.profile_use_https);
		cbUseHttps.setChecked(
				remoteProfile.getProtocol().equals(AndroidUtils.HTTPS));

		return builder.create();
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);

		if (context instanceof GenericRemoteProfileListener) {
			mListener = (GenericRemoteProfileListener) context;
		}
	}

	@Thunk
	void saveAndClose() {
		remoteProfile.setUser(textUser.getText().toString());
		remoteProfile.setAC(textPW.getText().toString());
		remoteProfile.setNick(textNick.getText().toString());
		remoteProfile.setPort(AndroidUtils.parseInt(textPort.getText().toString()));
		remoteProfile.setHost(textHost.getText().toString());
		remoteProfile.setProtocol(
				cbUseHttps.isChecked() ? AndroidUtils.HTTPS : AndroidUtils.HTTP);

		AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
		appPreferences.addRemoteProfile(remoteProfile);

		if (mListener != null) {
			mListener.profileEditDone(remoteProfile, remoteProfile);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		final AlertDialog d = (AlertDialog) getDialog();
		if (d != null) {
			Button positiveButton = d.getButton(Dialog.BUTTON_POSITIVE);
			positiveButton.setOnClickListener(v -> {
				if (reqPW && TextUtils.isEmpty(textPW.getText())) {
					textPW.setError(getString(R.string.password_is_required));
					textPW.requestFocus();
					return;
				}
				saveAndClose();
				d.dismiss();
			});
		}
	}
}
