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
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.rpc.TransmissionRPC;
import com.vuze.util.Thunk;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

public class DialogFragmentDeleteTorrent
	extends DialogFragmentBase
{

	private static final String TAG = "DeleteTorrent";

	private long torrentId;

	private CheckBox cbDeleteData;

	private String remoteProfileID;

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				getActivity(), R.layout.dialog_delete_torrent);

		View view = alertDialogBuilder.view;
		Builder builder = alertDialogBuilder.builder;

		builder.setTitle(R.string.dialog_delete_title);

		// Add action buttons
		builder.setPositiveButton(R.string.dialog_delete_button_remove,
				new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						removeTorrent();
					}
				});
		builder.setNegativeButton(android.R.string.cancel, new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int id) {
				DialogFragmentDeleteTorrent.this.getDialog().cancel();
			}
		});

		AlertDialog dialog = builder.create();
		setupVars(view);

		return dialog;

	}

	@Thunk
	void removeTorrent() {
		SessionInfo sessionInfo = SessionInfoManager.getSessionInfo(remoteProfileID,
				null, null);
		sessionInfo.executeRpc(new RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				removeTorrent_rpc(rpc);
			}
		});
	}

	@Thunk
	void removeTorrent_rpc(TransmissionRPC rpc) {
		boolean deleteData = cbDeleteData.isChecked();
		SessionInfo sessionInfo = SessionInfoManager.getSessionInfo(remoteProfileID,
				null, null);
		RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
		remoteProfile.setDeleteRemovesData(deleteData);
		sessionInfo.saveProfile();
		rpc.removeTorrent(new long[] {
			torrentId
		}, deleteData, null);
	}

	private void setupVars(View view) {
		Bundle args = getArguments();
		String name = args.getString("name");
		torrentId = args.getLong("id");

		remoteProfileID = args.getString(SessionInfoManager.BUNDLE_KEY);

		cbDeleteData = (CheckBox) view.findViewById(R.id.dialog_delete_datacheck);

		SessionInfo sessionInfo = SessionInfoManager.getSessionInfo(remoteProfileID,
				null, null);
		RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
		cbDeleteData.setChecked(remoteProfile.isDeleteRemovesData());

		TextView tv = (TextView) view.findViewById(R.id.dialog_delete_message);

		tv.setText(getResources().getString(R.string.dialog_delete_message, name));
	}

	@Override
	public String getLogTag() {
		return TAG;
	}

	public static void open(FragmentManager fragmentManager,
			SessionInfo sessionInfo, String name, long torrentID) {
		DialogFragmentDeleteTorrent dlg = new DialogFragmentDeleteTorrent();
		Bundle bundle = new Bundle();
		bundle.putString("name", name);
		bundle.putLong("id", torrentID);
		bundle.putString(SessionInfoManager.BUNDLE_KEY,
				sessionInfo.getRemoteProfile().getID());

		dlg.setArguments(bundle);
		AndroidUtilsUI.showDialog(dlg, fragmentManager, "DeleteTorrentDialog");
	}

}
