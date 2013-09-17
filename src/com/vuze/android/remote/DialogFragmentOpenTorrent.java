package com.vuze.android.remote;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

public class DialogFragmentOpenTorrent
	extends DialogFragment
{
	
	public interface OpenTorrentDialogListener {
		public void openTorrent(String s);
	}
	
	private OpenTorrentDialogListener mListener;
	private EditText mTextTorrent;

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		// Get the layout inflater
		LayoutInflater inflater = getActivity().getLayoutInflater();

		// Inflate and set the layout for the dialog
		// Pass null as the parent view because its going in the dialog layout
		View view = inflater.inflate(R.layout.open_torrent_dialog, null);
		builder.setView(view);
		
		mTextTorrent = (EditText) view.findViewById(R.id.addtorrent_tb);

		
		// Add action buttons
		builder.setPositiveButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						if (mListener != null) {
							mListener.openTorrent(mTextTorrent.getText().toString());
						}
					}
				});
		builder.setNegativeButton(android.R.string.cancel,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						DialogFragmentOpenTorrent.this.getDialog().cancel();
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
}
