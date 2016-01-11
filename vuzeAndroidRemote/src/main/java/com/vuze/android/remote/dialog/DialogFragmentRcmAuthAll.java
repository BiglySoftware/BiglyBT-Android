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

import java.util.HashMap;
import java.util.Map;

import android.app.*;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.*;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.vuze.android.remote.*;
import com.vuze.android.remote.AndroidUtils.AlertDialogBuilder;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.dialog.DialogFragmentRcmAuth.DialogFragmentRcmAuthListener;
import com.vuze.android.remote.rpc.ReplyMapReceivedListener;
import com.vuze.android.remote.rpc.TransmissionRPC;

public class DialogFragmentRcmAuthAll
	extends DialogFragment
{

	private static final String TAG = "RcmAuthAll";

	private DialogFragmentRcmAuthListener mListener;

	public static void openDialog(FragmentActivity fragment, String profileID) {
		DialogFragmentRcmAuthAll dlg = new DialogFragmentRcmAuthAll();
		Bundle bundle = new Bundle();
		bundle.putString(SessionInfoManager.BUNDLE_KEY, profileID);
		dlg.setArguments(bundle);
		AndroidUtils.showDialog(dlg, fragment.getSupportFragmentManager(), TAG);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		AlertDialogBuilder alertDialogBuilder = AndroidUtils.createAlertDialogBuilder(
				getActivity(), R.layout.dialog_rcm_auth_all);

		View view = alertDialogBuilder.view;
		Builder builder = alertDialogBuilder.builder;
		
		AndroidUtils.linkify(view, R.id.rcm_ftux2_line1);
		AndroidUtils.linkify(view, R.id.rcm_ftux2_line2);
		AndroidUtils.linkify(view, R.id.rcm_cb_all);

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
				DialogFragmentRcmAuthAll.this.getDialog().cancel();
			}
		});
		builder.setCancelable(true);
		return builder.create();
	}

	protected void closeDialog(final boolean enable) {
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
		String id = arguments.getString(SessionInfoManager.BUNDLE_KEY);
		if (id == null) {
			return;
		}
		SessionInfo sessionInfo = SessionInfoManager.getSessionInfo(id,
				getActivity());
		if (sessionInfo == null) {
			return;
		}
		sessionInfo.executeRpc(new RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				Map<String, Object> map = new HashMap<>(2, 1.0f);
				map.put("enable", enable);
				if (enable) {
					map.put("all-sources", true);
				}
				rpc.simpleRpcCall("rcm-set-enabled", map,
						new ReplyMapReceivedListener() {

							@Override
							public void rpcSuccess(String id, Map<?, ?> optionalMap) {
								if (mListener != null) {
									mListener.rcmEnabledChanged(enable, true);
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
		});
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		if (activity instanceof DialogFragmentRcmAuthListener) {
			mListener = (DialogFragmentRcmAuthListener) activity;
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		AlertDialog d = (AlertDialog) getDialog();
		if (d != null) {
			final Button positiveButton = d.getButton(Dialog.BUTTON_POSITIVE);
			final CheckBox cbYesAlready = (CheckBox) d.findViewById(R.id.rcm_cb_all);

			positiveButton.setEnabled(cbYesAlready.isChecked());

			OnCheckedChangeListener l = new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView,
						boolean isChecked) {
					positiveButton.setEnabled(cbYesAlready.isChecked());
				}
			};
			cbYesAlready.setOnCheckedChangeListener(l);
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).fragmentStart(this, TAG);
	}

	@Override
	public void onStop() {
		super.onStop();
		VuzeEasyTracker.getInstance(this).fragmentStop(this);
	}
}
