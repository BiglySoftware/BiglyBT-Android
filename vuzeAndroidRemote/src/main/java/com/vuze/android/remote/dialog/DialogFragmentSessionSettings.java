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
import com.vuze.util.Thunk;

import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;

public class DialogFragmentSessionSettings
	extends DialogFragmentBase
{

	private static final String TAG = "SessionSettings";

	private EditText textUL;

	private EditText textDL;

	private EditText textRefresh;

	private CompoundButton chkUL;

	private CompoundButton chkDL;

	private CompoundButton chkRefresh;

	private CompoundButton chkUseSmalLists;

	private CompoundButton chkRefreshMobile;

	private CompoundButton chkRefreshMobileSeparate;

	private EditText textRefreshMobile;

	private CompoundButton chkShowOpenOptions;

	private String remoteProfileID;

	public static boolean openDialog(FragmentManager fm,
			SessionInfo sessionInfo) {
		if (sessionInfo == null || sessionInfo.getSessionSettings() == null) {
			return false;
		}
		DialogFragmentSessionSettings dlg = new DialogFragmentSessionSettings();
		Bundle bundle = new Bundle();
		String id = sessionInfo.getRemoteProfile().getID();
		bundle.putString(SessionInfoManager.BUNDLE_KEY, id);
		dlg.setArguments(bundle);
		AndroidUtilsUI.showDialog(dlg, fm, TAG);
		return true;
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Bundle arguments = getArguments();

		remoteProfileID = arguments.getString(SessionInfoManager.BUNDLE_KEY);
		SessionSettings originalSettings;
		if (remoteProfileID == null) {
			throw new IllegalStateException("No session info");
		}
		SessionInfo sessionInfo = SessionInfoManager.getSessionInfo(remoteProfileID,
				null, null);
		RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
		originalSettings = sessionInfo.getSessionSettings();
		if (originalSettings == null) {
			throw new IllegalStateException("No session info settings");
		}

		AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
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
		textRefreshMobile = (EditText) view.findViewById(
				R.id.rpUpdateIntervalMobile);
		textRefreshMobile.setText("" + remoteProfile.getUpdateIntervalMobile());

		boolean check;
		ViewGroup viewGroup;

		Resources resources = getResources();

		chkUL = (CompoundButton) view.findViewById(R.id.rp_chkUL);
		chkUL.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				ViewGroup viewGroup = (ViewGroup) view.findViewById(R.id.rp_ULArea);
				AndroidUtilsUI.setGroupEnabled(viewGroup, isChecked);
			}
		});
		check = originalSettings.isULAuto();
		viewGroup = (ViewGroup) view.findViewById(R.id.rp_ULArea);
		AndroidUtilsUI.setGroupEnabled(viewGroup, check);
		chkUL.setChecked(check);

		chkDL = (CompoundButton) view.findViewById(R.id.rp_chkDL);
		chkDL.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				ViewGroup viewGroup = (ViewGroup) view.findViewById(R.id.rp_DLArea);
				AndroidUtilsUI.setGroupEnabled(viewGroup, isChecked);
			}
		});
		check = originalSettings.isDLAuto();
		viewGroup = (ViewGroup) view.findViewById(R.id.rp_DLArea);
		AndroidUtilsUI.setGroupEnabled(viewGroup, check);
		chkDL.setChecked(check);

		chkRefresh = (CompoundButton) view.findViewById(R.id.rp_chkRefresh);
		chkRefresh.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				ViewGroup viewGroup = (ViewGroup) view.findViewById(
						R.id.rp_UpdateIntervalArea);
				AndroidUtilsUI.setGroupEnabled(viewGroup, isChecked);
			}
		});
		check = remoteProfile.isUpdateIntervalEnabled();
		viewGroup = (ViewGroup) view.findViewById(R.id.rp_UpdateIntervalArea);
		AndroidUtilsUI.setGroupEnabled(viewGroup, check);
		chkRefresh.setChecked(check);
		if (check) {
			chkRefresh.setText(R.string.rp_update_interval);
		} else {
			String s = resources.getString(R.string.rp_update_interval) + " ("
					+ resources.getString(R.string.manual_refresh) + ")";
			chkRefresh.setText(s);
		}

		chkRefreshMobileSeparate = (CompoundButton) view.findViewById(
				R.id.rp_chkRefreshMobileSeparate);
		chkRefreshMobileSeparate.setOnCheckedChangeListener(
				new OnCheckedChangeListener() {
					public void onCheckedChanged(CompoundButton buttonView,
							boolean isChecked) {
						ViewGroup viewGroup = (ViewGroup) view.findViewById(
								R.id.rp_RefreshMobileSeparateArea);
						AndroidUtilsUI.setGroupEnabled(viewGroup, isChecked);
					}
				});
		check = remoteProfile.isUpdateIntervalMobileSeparate();
		viewGroup = (ViewGroup) view.findViewById(
				R.id.rp_RefreshMobileSeparateArea);
		AndroidUtilsUI.setGroupEnabled(viewGroup, check);
		chkRefreshMobileSeparate.setChecked(check);

		chkRefreshMobile = (CompoundButton) view.findViewById(
				R.id.rp_chkRefreshMobile);
		chkRefreshMobile.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				ViewGroup viewGroup = (ViewGroup) view.findViewById(
						R.id.rp_UpdateIntervalMobileArea);
				AndroidUtilsUI.setGroupEnabled(viewGroup, isChecked);
			}
		});
		check = remoteProfile.isUpdateIntervalMobileEnabled()
				&& remoteProfile.isUpdateIntervalMobileSeparate();
		viewGroup = (ViewGroup) view.findViewById(R.id.rp_UpdateIntervalMobileArea);
		AndroidUtilsUI.setGroupEnabled(viewGroup, check);
		chkRefreshMobile.setChecked(check);
		if (check) {
			chkRefreshMobile.setText(R.string.rp_update_interval_mobile);
		} else {
			String s = resources.getString(R.string.rp_update_interval_mobile) + " ("
					+ resources.getString(R.string.manual_refresh) + ")";
			chkRefreshMobile.setText(s);
		}

		chkUseSmalLists = (CompoundButton) view.findViewById(
				R.id.rp_chkUseSmallLists);
		chkUseSmalLists.setChecked(remoteProfile.useSmallLists());

		chkShowOpenOptions = (CompoundButton) view.findViewById(
				R.id.rp_chkShowOpenOptionsDialog);
		chkShowOpenOptions.setChecked(!remoteProfile.isAddTorrentSilently());

		return builder.create();
	}

	@Thunk
	void saveAndClose() {
		SessionSettings newSettings = new SessionSettings();
		SessionInfo sessionInfo = SessionInfoManager.getSessionInfo(remoteProfileID,
				null, null);
		RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
		remoteProfile.setUpdateIntervalEnabled(chkRefresh.isChecked());
		remoteProfile.setUpdateIntervalEnabledSeparate(
				chkRefreshMobileSeparate.isChecked());
		remoteProfile.setUpdateIntervalMobileEnabled(chkRefreshMobile.isChecked());
		newSettings.setULIsAuto(chkUL.isChecked());
		newSettings.setDLIsAuto(chkDL.isChecked());
		newSettings.setDlSpeed(parseLong(textDL.getText().toString()));
		newSettings.setUlSpeed(parseLong(textUL.getText().toString()));
		try {
			remoteProfile.setUpdateInterval(
					parseLong(textRefresh.getText().toString()));
		} catch (Throwable t) {
			// lazy
		}
		try {
			remoteProfile.setUpdateIntervalMobile(
					parseLong(textRefreshMobile.getText().toString()));
		} catch (Throwable t) {
			// lazy
		}

		remoteProfile.setUseSmallLists(chkUseSmalLists.isChecked());
		remoteProfile.setAddTorrentSilently(!chkShowOpenOptions.isChecked());

		sessionInfo.updateSessionSettings(newSettings);
	}

	@Thunk long parseLong(String s) {
		try {
			return Long.parseLong(s);
		} catch (Exception ignore) {
		}
		return 0;
	}

	@Override
	public String getLogTag() {
		return TAG;
	}
}
