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

import java.util.Map;

import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.AndroidUtilsUI.AlertDialogBuilder;
import com.biglybt.android.client.BiglyBTApp;
import com.biglybt.android.client.R;
import com.biglybt.android.client.rpc.ReplyMapReceivedListener;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.util.Thunk;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.*;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class DialogFragmentRcmAuth
	extends DialogFragmentResized
{
	private static final String TAG = "RcmAuth";

	@Thunk
	static boolean showingDialog = false;

	public interface DialogFragmentRcmAuthListener
	{
		void rcmEnabledChanged(boolean enable, boolean all);
	}

	public static void openDialog(FragmentActivity fragment, String profileID) {
		if (showingDialog) {
			return;
		}
		if (fragment.isFinishing()) {
			return;
		}
		DialogFragmentRcmAuth dlg = new DialogFragmentRcmAuth();
		Bundle bundle = new Bundle();
		bundle.putString(SessionManager.BUNDLE_KEY, profileID);
		dlg.setArguments(bundle);
		showingDialog = AndroidUtilsUI.showDialog(dlg,
				fragment.getSupportFragmentManager(), TAG);
	}

	@Thunk
	boolean all;

	@Thunk
	DialogFragmentRcmAuthListener mListener;

	public DialogFragmentRcmAuth() {
		setMinWidthPX(
				(int) (AndroidUtilsUI.getScreenWidthPx(BiglyBTApp.getContext()) * 0.9));
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		FragmentActivity activity = getActivity();
		AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				activity, R.layout.dialog_rcm_auth);

		View view = alertDialogBuilder.view;
		Builder builder = alertDialogBuilder.builder;

		AndroidUtilsUI.linkify(activity,
				(TextView) view.findViewById(R.id.rcm_line1), null,
				R.string.rcm_ftux_info);
		AndroidUtilsUI.linkify(activity,
				(TextView) view.findViewById(R.id.rcm_line2), null,
				R.string.rcm_ftux_info2);
		AndroidUtilsUI.linkify(activity,
				(TextView) view.findViewById(R.id.rcm_line3), null,
				R.string.rcm_ftux_smallprint);
		AndroidUtilsUI.linkify(activity,
				(RadioButton) view.findViewById(R.id.rcm_rb_all), null,
				R.string.rcm_ftux_option_all);
		AndroidUtilsUI.linkify(activity,
				(RadioButton) view.findViewById(R.id.rcm_rb_pre), null,
				R.string.rcm_ftux_option_preselect);

		// Add action buttons
		builder.setPositiveButton(R.string.accept, new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int id) {
				closeDialog(true);
			}
		});
		builder.setNegativeButton(R.string.decline, new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int id) {
				closeDialog(false);
				DialogFragmentRcmAuth.this.getDialog().cancel();
			}
		});
		builder.setCancelable(true);
		AlertDialog dlg = builder.create();
		dlg.setOnDismissListener(new OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialog) {
				showingDialog = false;
			}
		});
		return dlg;
	}

	@Override
	public void onCancel(DialogInterface dialog) {
		closeDialog(false);
		super.onCancel(dialog);
	}

	@Thunk
	void closeDialog(final boolean enable) {
		showingDialog = false;
		Bundle arguments = getArguments();
		if (arguments == null) {
			Log.e(TAG, "arguments null");
			return;
		}
		String profileID = arguments.getString(SessionManager.BUNDLE_KEY);
		if (profileID == null) {
			Log.e(TAG, "profileID null");
			return;
		}

		if (enable && all) {
			DialogFragmentRcmAuthAll.openDialog(getActivity(), profileID);
			return;
		}

		Session session = SessionManager.getSession(profileID, null, null);
		session.rcm.setEnabled(enable, all, new ReplyMapReceivedListener() {
			@Override
			public void rpcSuccess(String id, Map<?, ?> optionalMap) {
				if (mListener != null) {
					mListener.rcmEnabledChanged(enable, all);
				}
			}

			@Override
			public void rpcFailure(String id, String message) {
				if (mListener != null) {
					mListener.rcmEnabledChanged(false, false);
				}
			}

			@Override
			public void rpcError(String id, Exception e) {
				if (mListener != null) {
					mListener.rcmEnabledChanged(false, false);
				}
			}
		});
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);

		if (context instanceof DialogFragmentRcmAuthListener) {
			mListener = (DialogFragmentRcmAuthListener) context;
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		AlertDialog d = (AlertDialog) getDialog();
		if (d != null) {
			final Button positiveButton = d.getButton(Dialog.BUTTON_POSITIVE);
			final RadioButton rbPre = (RadioButton) d.findViewById(R.id.rcm_rb_pre);
			final RadioButton rbAll = (RadioButton) d.findViewById(R.id.rcm_rb_all);

			all = rbAll.isChecked();

			positiveButton.setEnabled(rbPre.isChecked() || rbAll.isChecked());

			OnCheckedChangeListener l = new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView,
						boolean isChecked) {
					positiveButton.setEnabled(rbPre.isChecked() || rbAll.isChecked());
					all = rbAll.isChecked();
				}
			};

			rbPre.setOnCheckedChangeListener(l);
			rbAll.setOnCheckedChangeListener(l);
		}
	}

	@Override
	public String getLogTag() {
		return TAG;
	}
}
