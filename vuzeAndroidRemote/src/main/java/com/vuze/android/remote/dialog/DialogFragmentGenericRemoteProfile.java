/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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
 * 
 */

package com.vuze.android.remote.dialog;

import com.vuze.android.remote.*;
import com.vuze.android.remote.AndroidUtilsUI.AlertDialogBuilder;
import com.vuze.android.remote.session.RemoteProfile;
import com.vuze.util.JSONUtils;
import com.vuze.util.Thunk;

import android.annotation.SuppressLint;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
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

	@SuppressLint("SetTextI18n")
	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				getActivity(), R.layout.dialog_generic_remote_preferences);

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
						DialogFragmentGenericRemoteProfile.this.getDialog().cancel();
					}
				});

		final View view = alertDialogBuilder.view;

		Bundle arguments = getArguments();

		String remoteAsJSON = arguments == null ? null
				: arguments.getString(RemoteUtils.KEY_REMOTE_JSON);
		if (remoteAsJSON != null) {
			try {
				remoteProfile = new RemoteProfile(JSONUtils.decodeJSON(remoteAsJSON));
			} catch (Exception e) {
				remoteProfile = new RemoteProfile(RemoteProfile.TYPE_NORMAL);
			}
		} else {
			remoteProfile = new RemoteProfile(RemoteProfile.TYPE_NORMAL);
		}

		textHost = (EditText) view.findViewById(R.id.profile_host);
		textHost.setText(remoteProfile.getHost());
		textNick = (EditText) view.findViewById(R.id.profile_nick);
		textNick.setText(remoteProfile.getNick());
		textPort = (EditText) view.findViewById(R.id.profile_port);
		textPort.setText(Integer.toString(remoteProfile.getPort()));
		textPW = (EditText) view.findViewById(R.id.profile_pw);
		textPW.setText(remoteProfile.getAC());
		textUser = (EditText) view.findViewById(R.id.profile_user);
		textUser.setText(remoteProfile.getUser());
		cbUseHttps = (CheckBox) view.findViewById(R.id.profile_use_https);
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

		AppPreferences appPreferences = VuzeRemoteApp.getAppPreferences();
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
