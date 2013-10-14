package com.vuze.android.remote;

import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import com.vuze.android.remote.AndroidUtils.AlertDialogBuilder;

public class DialogFragmentAbout
	extends DialogFragment
{

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		AlertDialogBuilder alertDialogBuilder = AndroidUtils.createAlertDialogBuilder(
				getActivity(), R.layout.about_window);

		Builder builder = alertDialogBuilder.builder;

		TextView tvThanksTo = (TextView) alertDialogBuilder.view.findViewById(R.id.about_thanksto);
		tvThanksTo.setMovementMethod(LinkMovementMethod.getInstance());
		tvThanksTo.setText(Html.fromHtml(getResources().getString(R.string.about_thanks)));

		// Add action buttons
		builder.setPositiveButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
					}
				});
		return builder.create();
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
