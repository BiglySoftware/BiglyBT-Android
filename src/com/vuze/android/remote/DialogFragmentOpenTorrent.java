package com.vuze.android.remote;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.View;
import android.widget.EditText;

import com.vuze.android.remote.AndroidUtils.AlertDialogBuilder;
import com.vuze.android.remote.activity.EmbeddedWebRemote;

public class DialogFragmentOpenTorrent
	extends DialogFragment
{

	public interface OpenTorrentDialogListener
	{
		public void openTorrent(String s);

		public void openTorrent(Uri uri);
	}

	private OpenTorrentDialogListener mListener;

	private EditText mTextTorrent;

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
				if (mListener != null) {
					mListener.openTorrent(mTextTorrent.getText().toString());
				}
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
						EmbeddedWebRemote.FILECHOOSER_RESULTCODE);
			}
		});
		return builder.create();
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		if (activity instanceof OpenTorrentDialogListener) {
			mListener = (OpenTorrentDialogListener) activity;
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		// This won't actually get called if this class is launched via DailogFragment.show()
		// It will be passed to parent (invoker's) activity
		if (AndroidUtils.DEBUG) {
			System.out.println("ActivityResult " + requestCode + "/" + resultCode);
		}
		if (requestCode == EmbeddedWebRemote.FILECHOOSER_RESULTCODE) {
			Uri result = intent == null || resultCode != Activity.RESULT_OK ? null
					: intent.getData();
			if (result == null) {
				return;
			}
			if (mListener != null) {
				mListener.openTorrent(result);
			}
		}
	}

}
