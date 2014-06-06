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

import android.app.*;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import com.vuze.android.remote.*;
import com.vuze.android.remote.AndroidUtils.AlertDialogBuilder;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.rpc.TransmissionRPC;

public class DialogFragmentDeleteTorrent
	extends DialogFragment
{
	private AlertDialog dialog;

	private long torrentId;

	private CheckBox cbDeleteData;

	private SessionInfo sessionInfo;

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		AlertDialogBuilder alertDialogBuilder = AndroidUtils.createAlertDialogBuilder(
				getActivity(), R.layout.dialog_delete_torrent);

		View view = alertDialogBuilder.view;
		Builder builder = alertDialogBuilder.builder;

		builder.setTitle(R.string.dialog_delete_title);

		// Add action buttons
		builder.setPositiveButton(R.string.dialog_delete_button_remove,
				new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						if (sessionInfo == null) {
							return;
						}
						sessionInfo.executeRpc(new RpcExecuter() {
							@Override
							public void executeRpc(TransmissionRPC rpc) {
								rpc.removeTorrent(new long[] {
									torrentId
								}, cbDeleteData.isChecked(), null);
							}
						});
					}
				});
		builder.setNegativeButton(android.R.string.cancel, new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int id) {
				DialogFragmentDeleteTorrent.this.getDialog().cancel();
			}
		});

		dialog = builder.create();
		setupVars(view);

		return dialog;

	}

	private void setupVars(View view) {
		Bundle args = getArguments();
		String name = args.getString("name");
		torrentId = args.getLong("id");

		String remoteProfileID = args.getString(SessionInfoManager.BUNDLE_KEY);
		sessionInfo = SessionInfoManager.getSessionInfo(remoteProfileID, null);

		cbDeleteData = (CheckBox) view.findViewById(R.id.dialog_delete_datacheck);

		TextView tv = (TextView) view.findViewById(R.id.dialog_delete_message);

		tv.setText(getResources().getString(R.string.dialog_delete_message, name));
	}

	@Override
	public void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).fragmentStart(this, "DeleteTorrent");
	}

	@Override
	public void onStop() {
		super.onStop();
		VuzeEasyTracker.getInstance(this).fragmentStop(this);
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
		AndroidUtils.showDialog(dlg, fragmentManager, "DeleteTorrentDialog");
	}

}
