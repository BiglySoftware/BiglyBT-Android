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

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.aelitis.azureus.util.JSONUtils;
import com.vuze.android.remote.*;
import com.vuze.android.remote.AndroidUtils.AlertDialogBuilder;
import com.vuze.android.remote.dialog.DialogFragmentGenericRemoteProfile.GenericRemoteProfileListener;

public class DialogFragmentVuzeRemoteProfile
	extends DialogFragment
{

	private GenericRemoteProfileListener mListener;

	private RemoteProfile remoteProfile;

	private EditText textNick;

	private EditText textAC;

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		Bundle arguments = getArguments();

		String remoteAsJSON = arguments == null ? null
				: arguments.getString("remote.json");
		if (remoteAsJSON != null) {
			try {
				remoteProfile = new RemoteProfile(JSONUtils.decodeJSON(remoteAsJSON));
			} catch (Exception e) {
				return null;
			}
		} else {
			return null;
		}

		AlertDialogBuilder alertDialogBuilder = AndroidUtils.createAlertDialogBuilder(
				getActivity(), R.layout.dialog_vuze_remote_preferences);

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
						DialogFragmentVuzeRemoteProfile.this.getDialog().cancel();
					}
				});

		final View view = alertDialogBuilder.view;

		textNick = (EditText) view.findViewById(R.id.profile_nick);
		textNick.setText(remoteProfile.getNick());
		textAC = (EditText) view.findViewById(R.id.profile_ac);
		textAC.setText(remoteProfile.getAC());

		return builder.create();
	}

	public void setGroupEnabled(ViewGroup viewGroup, boolean enabled) {
		for (int i = 0; i < viewGroup.getChildCount(); i++) {
			View view = viewGroup.getChildAt(i);
			view.setEnabled(enabled);
		}
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		if (activity instanceof GenericRemoteProfileListener) {
			mListener = (GenericRemoteProfileListener) activity;
		}
	}

	protected void saveAndClose() {

		remoteProfile.setNick(textNick.getText().toString());
		remoteProfile.setAC(textAC.getText().toString());

		AppPreferences appPreferences = VuzeRemoteApp.getAppPreferences();
		appPreferences.addRemoteProfile(remoteProfile);

		if (mListener != null) {
			mListener.profileEditDone(remoteProfile, remoteProfile);
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).activityStart(this, "VuzeProfileEdit");
	}

	@Override
	public void onStop() {
		super.onStop();
		VuzeEasyTracker.getInstance(this).activityStop(this);
	}
}
