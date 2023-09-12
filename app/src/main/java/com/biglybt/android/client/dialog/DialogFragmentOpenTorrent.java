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

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;

import com.biglybt.android.client.*;
import com.biglybt.android.client.AndroidUtilsUI.AlertDialogBuilder;
import com.biglybt.android.client.activity.TorrentOpenOptionsActivity;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.android.util.FileUtils;
import com.biglybt.util.Thunk;

/**
 * This is the dialog box that asks the user for a URL/File/Hash.
 * <P>
 * After the torrent is loaded, the {@link TorrentOpenOptionsActivity} window
 * typically opens.
 */
public class DialogFragmentOpenTorrent
	extends DialogFragmentBase
{

	private static final String TAG = "OpenTorrentDialog";

	@Thunk
	EditText mTextTorrent;

	private ActivityResultLauncher<Intent> launcherOpenTorrent;

	public static void openOpenTorrentDialog(FragmentManager fm,
			String profileID) {
		DialogFragmentOpenTorrent dlg = new DialogFragmentOpenTorrent();
		Bundle bundle = new Bundle();
		bundle.putString(SessionManager.BUNDLE_KEY, profileID);
		dlg.setArguments(bundle);
		AndroidUtilsUI.showDialog(dlg, fm, TAG);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		launcherOpenTorrent = registerForActivityResult(
				new StartActivityForResult(), (result) -> {
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "launcherOpenTorrent: result=" + result);
					}
					if (result.getResultCode() != Activity.RESULT_OK) {
						return;
					}
					Intent intent = result.getData();
					if (intent == null) {
						return;
					}
					Uri uri = intent.getData();
					if (uri == null) {
						return;
					}
					Session session = SessionManager.findOrCreateSession(this, null);
					if (session == null) {
						return;
					}

					session.torrent.openTorrent((AppCompatActivityM) requireActivity(), uri);
					Dialog dialog = getDialog();
					if (dialog != null) {
						dialog.dismiss();
					}
				});

		super.onCreate(savedInstanceState);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		getDialog().getWindow().setSoftInputMode(
				WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				requireActivity(), R.layout.dialog_open_torrent);

		View view = alertDialogBuilder.view;
		AlertDialog.Builder builder = alertDialogBuilder.builder;

		mTextTorrent = view.findViewById(R.id.addtorrent_tb);

		// Add action buttons
		builder.setPositiveButton(android.R.string.ok, (dialog, id) -> {
			Session session = SessionManager.findOrCreateSession(
					DialogFragmentOpenTorrent.this, null);
			if (session == null) {
				return;
			}
			session.torrent.openTorrent(getActivity(),
					mTextTorrent.getText().toString(), null);
		});
		builder.setNegativeButton(android.R.string.cancel,
				(dialog, id) -> DialogFragmentOpenTorrent.this.getDialog().cancel());
		builder.setNeutralButton(R.string.button_browse, null);
		AlertDialog dialog = builder.create();

		// Prevent Neutral button from closing dialog, otherwise 
		// launcherOpenTorrent.ActivityResultCallback won't get called
		dialog.setOnShowListener(di -> {
			Button btnNeutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
			if (btnNeutral != null) {
				btnNeutral.setOnClickListener(v -> {
					FileUtils.launchFileChooser(requireContext(),
							FileUtils.getMimeTypeForExt("torrent"), launcherOpenTorrent);
				});
			}
		});
		return dialog;
	}
}
