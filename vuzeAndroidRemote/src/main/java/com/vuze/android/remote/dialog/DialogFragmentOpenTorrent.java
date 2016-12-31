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

import com.vuze.android.remote.AndroidUtils;
import com.vuze.android.remote.AndroidUtilsUI;
import com.vuze.android.remote.AndroidUtilsUI.AlertDialogBuilder;
import com.vuze.android.remote.R;
import com.vuze.android.remote.activity.TorrentOpenOptionsActivity;
import com.vuze.android.remote.activity.TorrentViewActivity;
import com.vuze.android.remote.session.Session;
import com.vuze.android.remote.session.SessionManager;
import com.vuze.util.Thunk;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

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

	public static void openOpenTorrentDialog(FragmentManager fm,
			String profileID) {
		DialogFragmentOpenTorrent dlg = new DialogFragmentOpenTorrent();
		Bundle bundle = new Bundle();
		bundle.putString(SessionManager.BUNDLE_KEY, profileID);
		dlg.setArguments(bundle);
		AndroidUtilsUI.showDialog(dlg, fm, TAG);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				getActivity(), R.layout.dialog_open_torrent);

		View view = alertDialogBuilder.view;
		Builder builder = alertDialogBuilder.builder;

		mTextTorrent = (EditText) view.findViewById(R.id.addtorrent_tb);

		// Add action buttons
		builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int id) {
				Session session = SessionManager.findOrCreateSession(
						DialogFragmentOpenTorrent.this, null);
				if (session == null) {
					return;
				}
				session.torrent.openTorrent(getActivity(),
						mTextTorrent.getText().toString(), null);
			}
		});
		builder.setNegativeButton(android.R.string.cancel, new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int id) {
				DialogFragmentOpenTorrent.this.getDialog().cancel();
			}
		});
		builder.setNeutralButton(R.string.button_browse, new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				AndroidUtils.openFileChooser(getActivity(), "application/x-bittorrent",
						TorrentViewActivity.FILECHOOSER_RESULTCODE);
			}
		});
		return builder.create();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		// This won't actually get called if this class is launched via DailogFragment.show()
		// It will be passed to parent (invoker's) activity
		if (AndroidUtils.DEBUG) {
			Log.e(TAG, "ActivityResult " + requestCode + "/" + resultCode);
		}
		if (requestCode == TorrentViewActivity.FILECHOOSER_RESULTCODE) {
			Uri result = intent == null || resultCode != Activity.RESULT_OK ? null
					: intent.getData();
			if (result == null) {
				return;
			}
			Session session = SessionManager.findOrCreateSession(this, null);
			if (session == null) {
				return;
			}
			session.torrent.openTorrent(getActivity(), result);
		}
	}

	@Override
	public String getLogTag() {
		return TAG;
	}
}
