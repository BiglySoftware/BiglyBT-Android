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

import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;

import com.vuze.android.remote.*;
import com.vuze.android.remote.AndroidUtils.AlertDialogBuilder;

public class DialogFragmentSessionSettings
	extends DialogFragment
{

	private EditText textUL;

	private EditText textDL;

	private EditText textRefresh;

	private CompoundButton chkUL;

	private CompoundButton chkDL;

	private CompoundButton chkRefresh;

	private SessionSettings originalSettings;

	private SessionInfo sessionInfo;

	private RemoteProfile remoteProfile;

	public static boolean openDialog(FragmentManager fm, SessionInfo sessionInfo) {
		if (sessionInfo == null || sessionInfo.getSessionSettings() == null) {
			return false;
		}
		DialogFragmentSessionSettings dlg = new DialogFragmentSessionSettings();
		Bundle bundle = new Bundle();
		String id = sessionInfo.getRemoteProfile().getID();
		bundle.putString(SessionInfoManager.BUNDLE_KEY, id);
		dlg.setArguments(bundle);
		AndroidUtils.showDialog(dlg, fm, "SessionSettings");
		return true;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Bundle arguments = getArguments();

		String id = arguments.getString(SessionInfoManager.BUNDLE_KEY);
		if (id != null) {
			sessionInfo = SessionInfoManager.getSessionInfo(id, getActivity());
			if (sessionInfo == null) {
				Log.e(null, "No session info for " + id);
				return null;
			}
			originalSettings = sessionInfo.getSessionSettings();
			remoteProfile = sessionInfo.getRemoteProfile();
		} else {
			return null;
		}

		AlertDialogBuilder alertDialogBuilder = AndroidUtils.createAlertDialogBuilder(
				getActivity(), R.layout.dialog_session_settings);

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
						DialogFragmentSessionSettings.this.getDialog().cancel();
					}
				});

		final View view = alertDialogBuilder.view;

		textUL = (EditText) view.findViewById(R.id.rp_tvUL);
		textUL.setText("" + originalSettings.getUlSpeed());
		textDL = (EditText) view.findViewById(R.id.rp_tvDL);
		textDL.setText("" + originalSettings.getDlSpeed());
		textRefresh = (EditText) view.findViewById(R.id.rpUpdateInterval);
		textRefresh.setText("" + remoteProfile.getUpdateInterval());

		boolean check;
		ViewGroup viewGroup;

		chkUL = (CompoundButton) view.findViewById(R.id.rp_chkUL);
		chkUL.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				ViewGroup viewGroup = (ViewGroup) view.findViewById(R.id.rp_ULArea);
				setGroupEnabled(viewGroup, isChecked);
			}
		});
		check = originalSettings.isULAuto();
		viewGroup = (ViewGroup) view.findViewById(R.id.rp_ULArea);
		setGroupEnabled(viewGroup, check);
		chkUL.setChecked(check);

		chkDL = (CompoundButton) view.findViewById(R.id.rp_chkDL);
		chkDL.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				ViewGroup viewGroup = (ViewGroup) view.findViewById(R.id.rp_DLArea);
				setGroupEnabled(viewGroup, isChecked);
			}
		});
		check = originalSettings.isDLAuto();
		viewGroup = (ViewGroup) view.findViewById(R.id.rp_DLArea);
		setGroupEnabled(viewGroup, check);
		chkDL.setChecked(check);

		chkRefresh = (CompoundButton) view.findViewById(R.id.rp_chkRefresh);
		chkRefresh.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				ViewGroup viewGroup = (ViewGroup) view.findViewById(R.id.rp_UpdateIntervalArea);
				setGroupEnabled(viewGroup, isChecked);
			}
		});
		check = remoteProfile.isUpdateIntervalEnabled();
		viewGroup = (ViewGroup) view.findViewById(R.id.rp_UpdateIntervalArea);
		setGroupEnabled(viewGroup, check);
		chkRefresh.setChecked(check);

		return builder.create();
	}

	public void setGroupEnabled(ViewGroup viewGroup, boolean enabled) {
		for (int i = 0; i < viewGroup.getChildCount(); i++) {
			View view = viewGroup.getChildAt(i);
			view.setEnabled(enabled);
		}
	}

	protected void saveAndClose() {
		SessionSettings newSettings = new SessionSettings();
		remoteProfile.setUpdateIntervalEnabled(chkRefresh.isChecked());
		newSettings.setULIsAuto(chkUL.isChecked());
		newSettings.setDLIsAuto(chkDL.isChecked());
		newSettings.setDlSpeed(parseLong(textDL.getText().toString()));
		newSettings.setUlSpeed(parseLong(textUL.getText().toString()));
		remoteProfile.setUpdateInterval(parseLong(textRefresh.getText().toString()));

		sessionInfo.updateSessionSettings(newSettings);
	}

	long parseLong(String s) {
		try {
			return Long.parseLong(s);
		} catch (Exception e) {
		}
		return 0;
	}

	@Override
	public void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).fragmentStart(this, "SessionSettings");
	}

	@Override
	public void onStop() {
		super.onStop();
		VuzeEasyTracker.getInstance(this).fragmentStop(this);
	}
}
