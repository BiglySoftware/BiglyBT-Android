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
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.EditText;

import com.vuze.android.remote.*;
import com.vuze.android.remote.AndroidUtils.AlertDialogBuilder;
import com.vuze.android.remote.activity.TorrentViewActivity;
import com.vuze.android.remote.fragment.SessionInfoGetter;

public class DialogFragmentOpenTorrent
	extends DialogFragment
{

	private EditText mTextTorrent;

	public static void openOpenTorrentDialog(Fragment fragment, String profileID) {
		DialogFragmentOpenTorrent dlg = new DialogFragmentOpenTorrent();
		dlg.setTargetFragment(fragment, 0);
		Bundle bundle = new Bundle();
		bundle.putString(SessionInfoManager.BUNDLE_KEY, profileID);
		dlg.setArguments(bundle);
		dlg.show(fragment.getFragmentManager(), "OpenTorrentDialog");
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		AlertDialogBuilder alertDialogBuilder = AndroidUtils.createAlertDialogBuilder(
				getActivity(), R.layout.dialog_open_torrent);

		View view = alertDialogBuilder.view;
		Builder builder = alertDialogBuilder.builder;

		mTextTorrent = (EditText) view.findViewById(R.id.addtorrent_tb);

		// Add action buttons
		builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int id) {
				SessionInfo sessionInfo = getSessionInfo();
				if (sessionInfo == null) {
					return;
				}
				sessionInfo.openTorrent(getActivity(),
						mTextTorrent.getText().toString());
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

	private SessionInfo getSessionInfo() {
		FragmentActivity activity = getActivity();
		if (activity instanceof SessionInfoGetter) {
			SessionInfoGetter sig = (SessionInfoGetter) activity;
			return sig.getSessionInfo();
		}

		Bundle arguments = getArguments();
		if (arguments == null) {
			return null;
		}
		String profileID = arguments.getString(SessionInfoManager.BUNDLE_KEY);
		if (profileID == null) {
			return null;
		}
		return SessionInfoManager.getSessionInfo(profileID, activity);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		// This won't actually get called if this class is launched via DailogFragment.show()
		// It will be passed to parent (invoker's) activity
		if (AndroidUtils.DEBUG) {
			System.out.println("ActivityResult " + requestCode + "/" + resultCode);
		}
		if (requestCode == TorrentViewActivity.FILECHOOSER_RESULTCODE) {
			Uri result = intent == null || resultCode != Activity.RESULT_OK ? null
					: intent.getData();
			if (result == null) {
				return;
			}
			SessionInfo sessionInfo = getSessionInfo();
			if (sessionInfo == null) {
				return;
			}
			sessionInfo.openTorrent(getActivity(), result);
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).activityStart(this, "OpenTorrent");
	}

	@Override
	public void onStop() {
		super.onStop();
		VuzeEasyTracker.getInstance(this).activityStop(this);
	}
}
