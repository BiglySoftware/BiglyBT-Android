package com.vuze.android.remote.dialog;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.vuze.android.remote.*;
import com.vuze.android.remote.AndroidUtils.AlertDialogBuilder;
import com.vuze.android.remote.R.id;
import com.vuze.android.remote.R.layout;

public class DialogFragmentSessionSettings
	extends DialogFragment
{

	public interface SessionSettingsListener
	{
		public void sessionSettingsChanged(SessionSettings settings);
	}

	private SessionSettingsListener mListener;

	private EditText textUL;

	private EditText textDL;

	private EditText textRefresh;

	private CheckBox chkUL;

	private CheckBox chkDL;

	private CheckBox chkRefresh;

	private SessionSettings settings;

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Bundle arguments = getArguments();

		settings = (SessionSettings) arguments.getSerializable(SessionSettings.class.getName());

		AlertDialogBuilder alertDialogBuilder = AndroidUtils.createAlertDialogBuilder(
				getActivity(), R.layout.dialog_session_settings);

		Builder builder = alertDialogBuilder.builder;

		// Add action buttons
		builder.setPositiveButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						saveAndClose();
					}
				});
		builder.setNegativeButton(android.R.string.cancel,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						DialogFragmentSessionSettings.this.getDialog().cancel();
					}
				});

		final View view = alertDialogBuilder.view;

		textUL = (EditText) view.findViewById(R.id.rp_tvUL);
		textUL.setText("" + settings.getUlSpeed());
		textDL = (EditText) view.findViewById(R.id.rp_tvDL);
		textDL.setText("" + settings.getDlSpeed());
		textRefresh = (EditText) view.findViewById(R.id.rpUpdateInterval);
		textRefresh.setText("" + settings.getRefreshInterval());

		boolean check;
		ViewGroup viewGroup;
		
		chkUL = (CheckBox) view.findViewById(R.id.rp_chkUL);
		chkUL.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				ViewGroup viewGroup = (ViewGroup) view.findViewById(R.id.rp_ULArea);
				setGroupEnabled(viewGroup, isChecked);
			}
		});
		check = settings.isULAuto();
		viewGroup = (ViewGroup) view.findViewById(R.id.rp_ULArea);
		setGroupEnabled(viewGroup, check);
		chkUL.setChecked(check);

		chkDL = (CheckBox) view.findViewById(R.id.rp_chkDL);
		chkDL.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				ViewGroup viewGroup = (ViewGroup) view.findViewById(R.id.rp_DLArea);
				setGroupEnabled(viewGroup, isChecked);
			}
		});
		check = settings.isDLAuto();
		viewGroup = (ViewGroup) view.findViewById(R.id.rp_DLArea);
		setGroupEnabled(viewGroup, check);
		chkDL.setChecked(check);
		
		chkRefresh = (CheckBox) view.findViewById(R.id.rp_chkRefresh);
		chkRefresh.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				ViewGroup viewGroup = (ViewGroup) view.findViewById(R.id.rp_UpdateIntervalArea);
				setGroupEnabled(viewGroup, isChecked);
			}
		});
		check = settings.isRefreshIntervalIsAuto();
		viewGroup = (ViewGroup) view.findViewById(R.id.rp_UpdateIntervalArea);
		setGroupEnabled(viewGroup, check);
		chkRefresh.setChecked(check);

		return builder.create();
	}
	
	public void setGroupEnabled(ViewGroup viewGroup, boolean enabled) {
		for (int i = 0; i < viewGroup.getChildCount(); i++) {
			View view = viewGroup.getChildAt(i);
			view.setEnabled(enabled);
		}
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		if (activity instanceof SessionSettingsListener) {
			mListener = (SessionSettingsListener) activity;
		}
	}

	protected void saveAndClose() {
		if (mListener != null) {
			SessionSettings settings = new SessionSettings();
			settings.setRefreshIntervalIsAuto(chkRefresh.isChecked());
			settings.setULIsAuto(chkUL.isChecked());
			settings.setDLIsAuto(chkDL.isChecked());
			settings.setDlSpeed(Long.parseLong(textDL.getText().toString()));
			settings.setUlSpeed(Long.parseLong(textUL.getText().toString()));
			settings.setRefreshInterval(Long.parseLong(textRefresh.getText().toString()));
			mListener.sessionSettingsChanged(settings);
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).activityStart(this);
	}
	
	@Override
	public void onStop() {
		super.onStop();
		VuzeEasyTracker.getInstance(this).activityStop(this);
	}
}
