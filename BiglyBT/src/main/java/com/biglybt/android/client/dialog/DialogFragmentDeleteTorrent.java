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

import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.AndroidUtilsUI.AlertDialogBuilder;
import com.biglybt.android.client.R;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.util.Thunk;

import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AlertDialog;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

public class DialogFragmentDeleteTorrent
	extends DialogFragmentBase
{

	private static final String TAG = "DeleteTorrentDialog";

	private static final String KEY_NAME = "name";

	private static final String KEY_TORRENT_ID = "id";

	private long torrentId;

	private CheckBox cbDeleteData;

	private String remoteProfileID;

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				getActivity(), R.layout.dialog_delete_torrent);

		View view = alertDialogBuilder.view;
		AlertDialog.Builder builder = alertDialogBuilder.builder;

		builder.setTitle(R.string.dialog_delete_title);

		// Add action buttons
		builder.setPositiveButton(R.string.dialog_delete_button_remove,
				(dialog, id) -> removeTorrent());
		builder.setNegativeButton(android.R.string.cancel,
				(dialog, id) -> DialogFragmentDeleteTorrent.this.getDialog().cancel());

		AlertDialog dialog = builder.create();
		setupVars(view);

		return dialog;

	}

	@Thunk
	void removeTorrent() {
		boolean deleteData = cbDeleteData.isChecked();
		Session session = SessionManager.getSession(remoteProfileID, null);
		RemoteProfile remoteProfile = session.getRemoteProfile();
		remoteProfile.setDeleteRemovesData(deleteData);
		session.saveProfile();

		session.torrent.removeTorrent(new long[] {
			torrentId
		}, deleteData, null);
	}

	private void setupVars(View view) {
		Bundle args = getArguments();
		assert args != null;
		String name = args.getString(KEY_NAME);
		torrentId = args.getLong(KEY_TORRENT_ID);

		remoteProfileID = args.getString(SessionManager.BUNDLE_KEY);

		cbDeleteData = view.findViewById(R.id.dialog_delete_datacheck);

		Session session = SessionManager.getSession(remoteProfileID, null);
		RemoteProfile remoteProfile = session.getRemoteProfile();
		cbDeleteData.setChecked(remoteProfile.isDeleteRemovesData());

		TextView tv = view.findViewById(R.id.dialog_delete_message);

		tv.setText(getResources().getString(R.string.dialog_delete_message, name));
	}

	public static void open(FragmentManager fragmentManager, Session session,
			String name, long torrentID) {
		DialogFragmentDeleteTorrent dlg = new DialogFragmentDeleteTorrent();
		Bundle bundle = new Bundle();
		bundle.putString(KEY_NAME, name);
		bundle.putLong(KEY_TORRENT_ID, torrentID);
		bundle.putString(SessionManager.BUNDLE_KEY,
				session.getRemoteProfile().getID());

		dlg.setArguments(bundle);
		AndroidUtilsUI.showDialog(dlg, fragmentManager, TAG);
	}

}
