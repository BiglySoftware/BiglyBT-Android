/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.android.client.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.biglybt.android.client.*;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.util.Thunk;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Created by TuxPaper on 10/27/17.
 */

public class DialogFragmentConnError
	extends DialogFragmentBase
{
	private static final String TAG = "DialogFragmentConnError";

	private static final String KEY_TITLE = "title";

	private static final String KEY_TEXT = "text";

	private static final String KEY_ALLOW_CONTINUE = "allowContinue";

	private static boolean hasAlertDialogOpen = false;

	private boolean allowContinue;

	@Thunk
	FragmentActivity activity;

	@UiThread
	public static void openDialog(@NonNull FragmentManager fm, String title,
			CharSequence text, boolean allowContinue) {
		if (hasAlertDialogOpen) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "Already have Alert Dialog Open ");
			}
			return;
		}

		if (fm.isDestroyed()) {
			Log.e(TAG, "openDialog: Fragment destroyed before displaying message ["
					+ title + "] " + text);
			RemoteUtils.openRemoteList(BiglyBTApp.getContext());
			return;
		}

		DialogFragmentConnError dlg = new DialogFragmentConnError();
		Bundle bundle = new Bundle();
		bundle.putString(KEY_TITLE, title);
		bundle.putCharSequence(KEY_TEXT, text);
		bundle.putBoolean(KEY_ALLOW_CONTINUE, allowContinue);
		dlg.setArguments(bundle);

		hasAlertDialogOpen = true;
		AndroidUtilsUI.showDialog(dlg, fm, TAG);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Bundle args = getArguments();
		assert args != null;

		CharSequence errMsg = args.getCharSequence(KEY_TEXT);
		allowContinue = args.getBoolean(KEY_ALLOW_CONTINUE);

		activity = getActivity();
		assert activity != null;

		AlertDialog.Builder builder = new MaterialAlertDialogBuilder(
				activity).setTitle(R.string.error_connecting).setMessage(
						errMsg).setCancelable(true).setNegativeButton(
								R.string.action_logout, (dialog, which) -> {
									String remoteProfileID = SessionManager.findRemoteProfileID(
											activity);
									if (remoteProfileID == null) {
										if (activity.isTaskRoot()) {
											RemoteUtils.openRemoteList(activity);
										}
										activity.finish();
									} else {
										SessionManager.removeSession(remoteProfileID, true);
									}
								});
		if (allowContinue) {
			builder.setPositiveButton(R.string.button_continue, (dialog, which) -> {
			});
		}

		return builder.create();
	}

	@Override
	public void onCancel(@NonNull DialogInterface dialog) {
		hasAlertDialogOpen = false;
		if (allowContinue) {
			return;
		}
		String remoteProfileID = SessionManager.findRemoteProfileID(activity);
		if (remoteProfileID == null) {
			if (activity.isTaskRoot()) {
				RemoteUtils.openRemoteList(activity);
			}
			activity.finish();
		} else {
			SessionManager.removeSession(remoteProfileID, true);
		}

		super.onCancel(dialog);
	}

	@Override
	public void onDismiss(@NonNull DialogInterface dialog) {
		hasAlertDialogOpen = false;
		super.onDismiss(dialog);
	}
}
