package com.vuze.android.remote.dialog;

import com.vuze.android.remote.R;
import com.vuze.android.remote.VuzeEasyTracker;
import com.vuze.android.remote.R.array;
import com.vuze.android.remote.R.string;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.widget.ArrayAdapter;

public class DialogFragmentFilterBy
	extends DialogFragment
{
	public interface FilterByDialogListener
	{
		void filterBy(String filterMode, String item, boolean save);
	}

	private FilterByDialogListener mListener;

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		ArrayAdapter<String> adapter;

		final String[] stringArray = getResources().getStringArray(
				R.array.filterby_list);

		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.filterby_title);
		builder.setItems(stringArray, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				if (mListener == null) {
					return;
				}
				String item = stringArray[which];
				String[] valuesArray = getResources().getStringArray(
						R.array.filterby_list_values);
				mListener.filterBy(valuesArray[which], item, true);
			}
		});
		return builder.create();
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		if (activity instanceof FilterByDialogListener) {
			mListener = (FilterByDialogListener) activity;
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
