package com.vuze.android.remote;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.vuze.android.remote.AndroidUtils.AlertDialogBuilder;

public class DialogFragmentGenericRemotePreferences
	extends DialogFragment
{

	public interface GenericRemoteProfileListener
	{
		public void profileEditDone(RemotePreferences profile);
	}

	private GenericRemoteProfileListener mListener;

	private EditText textHost;

	private RemotePreferences remotePrefs;

	private EditText textNick;

	private EditText textPort;

	private EditText textPW;

	private EditText textUser;

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Bundle arguments = getArguments();

		AlertDialogBuilder alertDialogBuilder = AndroidUtils.createAlertDialogBuilder(
				getActivity(), R.layout.dialog_generic_remote_preferences);

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
						DialogFragmentGenericRemotePreferences.this.getDialog().cancel();
					}
				});

		final View view = alertDialogBuilder.view;

		remotePrefs = new RemotePreferences("vuze", "");

		textHost = (EditText) view.findViewById(R.id.profile_host);
		textHost.setText(remotePrefs.getHost());
		textNick = (EditText) view.findViewById(R.id.profile_nick);
		textNick.setText(remotePrefs.getNick());
		textPort = (EditText) view.findViewById(R.id.profile_port);
		textPort.setText("" + remotePrefs.getPort());
		textPW = (EditText) view.findViewById(R.id.profile_pw);
		textPW.setText(remotePrefs.getAC());
		textUser = (EditText) view.findViewById(R.id.profile_user);
		textUser.setText(remotePrefs.getUser());

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

		if (activity instanceof GenericRemoteProfileListener) {
			mListener = (GenericRemoteProfileListener) activity;
		}
	}

	protected void saveAndClose() {
		RemotePreferences newPrefs = new RemotePreferences(
				textUser.getText().toString(), textPW.getText().toString());
		newPrefs.setNick(textNick.getText().toString());
		newPrefs.setPort(Integer.parseInt(textPort.getText().toString()));
		newPrefs.setHost(textHost.getText().toString());

		AppPreferences appPreferences = new AppPreferences(getActivity());
		appPreferences.addRemotePref(newPrefs);
		
		System.out.println("save it");

		if (mListener != null) {
			mListener.profileEditDone(newPrefs);
		}
	}

}
