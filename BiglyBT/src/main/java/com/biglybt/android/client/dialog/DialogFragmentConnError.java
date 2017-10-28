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

import static android.support.constraint.Constraints.TAG;

import com.biglybt.android.client.*;
import com.biglybt.android.client.session.SessionManager;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.util.Log;

/**
 * Created by TuxPaper on 10/27/17.
 */

public class DialogFragmentConnError
	extends DialogFragmentBase
{
	static boolean hasAlertDialogOpen = false;

	private static String tag;

	private boolean allowContinue;

	private FragmentActivity activity;

	public static void openDialog(FragmentManager fm, String tag, String title,
			CharSequence text, boolean allowContinue) {
		if (hasAlertDialogOpen) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "Already have Alert Dialog Open ");
			}
			return;
		}
		DialogFragmentConnError.tag = tag;
		DialogFragmentConnError dlg = new DialogFragmentConnError();
		Bundle bundle = new Bundle();
		bundle.putString("title", title);
		bundle.putCharSequence("text", text);
		bundle.putBoolean("allowContinue", allowContinue);
		dlg.setArguments(bundle);

		hasAlertDialogOpen = true;
		AndroidUtilsUI.showDialog(dlg, fm, TAG);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Bundle args = getArguments();

		CharSequence errMsg = args.getCharSequence("text");
		allowContinue = args.getBoolean("allowContinue");
		activity = getActivity();

		AlertDialog.Builder builder = new AlertDialog.Builder(activity).setTitle(
				R.string.error_connecting).setMessage(errMsg).setCancelable(
						true).setNegativeButton(R.string.action_logout,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int which) {
										String remoteProfileID = SessionManager.findRemoteProfileID(
												activity, TAG);
										if (remoteProfileID == null) {
											if (activity.isTaskRoot()) {
												RemoteUtils.openRemoteList(activity);
											}
											activity.finish();
										} else {
											SessionManager.removeSession(remoteProfileID);
										}
									}
								});
		if (allowContinue) {
			builder.setPositiveButton(R.string.button_continue,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
						}
					});
		}

		return builder.create();
	}

	@Override
	public void onCancel(DialogInterface dialog) {
		hasAlertDialogOpen = false;
		if (allowContinue) {
			return;
		}
		String remoteProfileID = SessionManager.findRemoteProfileID(
				activity, TAG);
		if (remoteProfileID == null) {
			if (activity.isTaskRoot()) {
				RemoteUtils.openRemoteList(activity);
			}
			activity.finish();
		} else {
			SessionManager.removeSession(remoteProfileID);
		}

		super.onCancel(dialog);
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		hasAlertDialogOpen = false;
		super.onDismiss(dialog);
	}

	@Override
	public String getLogTag() {
		return tag;
	}
}
