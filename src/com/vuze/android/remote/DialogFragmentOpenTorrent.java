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
import android.widget.Toast;

import com.vuze.android.remote.AndroidUtils.AlertDialogBuilder;

public class DialogFragmentOpenTorrent
	extends DialogFragment
{

	public interface OpenTorrentDialogListener
	{
		public void openTorrent(String s);

		public void openTorrent(Uri uri);
	}

	private static final int FILECHOOSER_RESULTCODE = 1;

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
		builder.setNeutralButton("Browse...", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				openFile("application/x-bittorrent");
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
		System.out.println("ActivityResult " + requestCode + "/" + resultCode);
		if (requestCode == FILECHOOSER_RESULTCODE) {
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


	public void openFile(String mimeType) {

		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType(mimeType);
		intent.addCategory(Intent.CATEGORY_OPENABLE);

		// special intent for Samsung file manager
		Intent sIntent = new Intent("com.sec.android.app.myfiles.PICK_DATA");
		// if you want any file type, you can skip next line 
		sIntent.putExtra("CONTENT_TYPE", mimeType);
		sIntent.addCategory(Intent.CATEGORY_DEFAULT);

		Intent chooserIntent;
		if (getActivity().getPackageManager().resolveActivity(sIntent, 0) != null) {
			// it is device with samsung file manager
			chooserIntent = Intent.createChooser(sIntent, "Open file");
			chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] {
				intent
			});
		} else {
			chooserIntent = Intent.createChooser(intent, "Open file");
		}

		try {
			startActivityForResult(chooserIntent, FILECHOOSER_RESULTCODE);
		} catch (android.content.ActivityNotFoundException ex) {
			Toast.makeText(getActivity().getApplicationContext(),
					"No suitable File Manager was found.", Toast.LENGTH_SHORT).show();
		}
		
	}

}
