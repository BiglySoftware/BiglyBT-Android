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
import com.biglybt.android.client.R;
import com.biglybt.android.client.dialog.DialogFragmentRcmAuth.DialogFragmentRcmAuthListener;
import com.biglybt.android.client.rpc.ReplyMapReceivedListener;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.util.Thunk;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.appcompat.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class DialogFragmentRcmAuthAll
	extends DialogFragmentBase
{

	private static final String TAG = "RcmAuthAll";

	@Thunk
	DialogFragmentRcmAuthListener mListener;

	public static void openDialog(@NonNull FragmentActivity fragment,
			String profileID) {
		DialogFragmentRcmAuthAll dlg = new DialogFragmentRcmAuthAll();
		Bundle bundle = new Bundle();
		bundle.putString(SessionManager.BUNDLE_KEY, profileID);
		dlg.setArguments(bundle);
		AndroidUtilsUI.showDialog(dlg, fragment.getSupportFragmentManager(), TAG);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		FragmentActivity activity = getActivity();

		AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				activity, R.layout.dialog_rcm_auth_all);

		View view = alertDialogBuilder.view;
		AlertDialog.Builder builder = alertDialogBuilder.builder;

		AndroidUtilsUI.linkify(activity, view.findViewById(R.id.rcm_ftux2_line1),
				null, R.string.rcm_ftux2_heading);
		AndroidUtilsUI.linkify(activity, view.findViewById(R.id.rcm_ftux2_line2),
				null, R.string.rcm_ftux2_info);
		AndroidUtilsUI.linkify(activity, view.findViewById(R.id.rcm_cb_all), null,
				R.string.rcm_ftux2_agree);

		// Add action buttons
		builder.setPositiveButton(R.string.accept,
				(dialog, id) -> closeDialog(true));
		builder.setNegativeButton(R.string.decline, (dialog, id) -> {
			closeDialog(false);
			Dialog d = DialogFragmentRcmAuthAll.this.getDialog();
			if (d != null) {
				d.cancel();
			}
		});
		builder.setCancelable(true);
		return builder.create();
	}

	@Thunk
	void closeDialog(final boolean enable) {
		if (!enable) {
			if (mListener != null) {
				mListener.rcmEnabledChanged(false, false);
			}
			return;
		}
		Bundle arguments = getArguments();
		if (arguments == null) {
			return;
		}
		String id = arguments.getString(SessionManager.BUNDLE_KEY);
		if (id == null) {
			return;
		}
		Session session = SessionManager.getSession(id, getActivity(), null);
		session.rcm.setEnabled(enable, true, new ReplyMapReceivedListener() {
			@Override
			public void rpcSuccess(String requestID, Map<?, ?> optionalMap) {
				if (mListener != null) {
					mListener.rcmEnabledChanged(enable, true);
				}
			}

			@Override
			public void rpcFailure(String requestID, String message) {
				if (mListener != null) {
					mListener.rcmEnabledChanged(false, false);
				}
			}

			@Override
			public void rpcError(String requestID, Exception e) {
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
		if (d == null) {
			return;
		}

		final CheckBox cbYesAlready = d.findViewById(R.id.rcm_cb_all);
		if (cbYesAlready == null) {
			return;
		}

		final Button positiveButton = d.getButton(Dialog.BUTTON_POSITIVE);
		if (positiveButton == null) {
			return;
		}

		positiveButton.setEnabled(cbYesAlready.isChecked());

		OnCheckedChangeListener l = (buttonView,
				isChecked) -> positiveButton.setEnabled(cbYesAlready.isChecked());
		cbYesAlready.setOnCheckedChangeListener(l);
	}
}
