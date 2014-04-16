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
import android.content.DialogInterface.OnDismissListener;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.*;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.vuze.android.remote.*;
import com.vuze.android.remote.AndroidUtils.AlertDialogBuilder;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.rpc.ReplyMapReceivedListener;
import com.vuze.android.remote.rpc.TransmissionRPC;

public class DialogFragmentRcmAuth
	extends DialogFragment
{

	private static final String TAG = "RcmAuth";
	
	private static boolean showingDialog = false;

	public interface DialogFragmentRcmAuthListener
	{
		public void rcmEnabledChanged(boolean enable, boolean all);
	}

	public static void openDialog(FragmentActivity fragment, String profileID) {
		if (showingDialog) {
			return;
		}
		DialogFragmentRcmAuth dlg = new DialogFragmentRcmAuth();
		Bundle bundle = new Bundle();
		bundle.putString(SessionInfoManager.BUNDLE_KEY, profileID);
		dlg.setArguments(bundle);
		showingDialog = true;
		dlg.show(fragment.getSupportFragmentManager(), TAG);
	}


	private boolean all;

	private DialogFragmentRcmAuthListener mListener;

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		AlertDialogBuilder alertDialogBuilder = AndroidUtils.createAlertDialogBuilder(
				getActivity(), R.layout.dialog_rcm_auth);

		View view = alertDialogBuilder.view;
		Builder builder = alertDialogBuilder.builder;

		AndroidUtils.linkify(view, R.id.rcm_line1);
		AndroidUtils.linkify(view, R.id.rcm_line2);
		AndroidUtils.linkify(view, R.id.rcm_line3);
		AndroidUtils.linkify(view, R.id.rcm_rb_all);
		AndroidUtils.linkify(view, R.id.rcm_rb_pre);

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

	protected void closeDialog(final boolean enable) {
		showingDialog = false;
		Bundle arguments = getArguments();
		if (arguments == null) {
			return;
		}
		String id = arguments.getString(SessionInfoManager.BUNDLE_KEY);
		if (id == null) {
			return;
		}

		if (enable && all) {
			DialogFragmentRcmAuthAll.openDialog(getActivity(), id);
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
					map.put("all-sources", all);
				}
				rpc.simpleRpcCall("rcm-set-enabled", map,
						new ReplyMapReceivedListener() {

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
	public void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).activityStart(this, TAG);
	}

	@Override
	public void onStop() {
		super.onStop();
		VuzeEasyTracker.getInstance(this).activityStop(this);
	}
}
