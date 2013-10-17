package com.vuze.android.remote.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.vuze.android.remote.R;
import com.vuze.android.remote.VuzeEasyTracker;

public class DialogFragmentSortBy
	extends DialogFragment
{
	public interface SortByDialogListener {
		void flipSortOrder();

		void sortBy(String sortType, boolean save);
	}

	private SortByDialogListener mListener;

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.sortby_title);
		builder.setItems(R.array.sortby_list,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						if (mListener == null) {
							return;
						}
						switch (which) {
							case 0: // <item>Queue Order</item>
								mListener.sortBy("Prefs._SortByQueue", true);
								break;
							case 1: // <item>Activity</item>
								mListener.sortBy("Prefs._SortByActivity", true);
								break;
							case 2: // <item>Age</item>
								mListener.sortBy("Prefs._SortByAge", true);
								break;
							case 3: // <item>Progress</item>
								mListener.sortBy("Prefs._SortByProgress", true);
								break;
							case 4: // <item>Ratio</item>
								mListener.sortBy("Prefs._SortByRatio", true);
								break;
							case 5: // <item>Size</item>
								mListener.sortBy("Prefs._SortBySize", true);
								break;
							case 6: // <item>State</item>
								mListener.sortBy("Prefs._SortByState", true);
								break;
							case 7: // <item>Reverse Sort Order</item>
								mListener.flipSortOrder();
								break;
							default:
								break;
						}
					}
				});
		return builder.create();
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		
		if (activity instanceof SortByDialogListener) {
			mListener = (SortByDialogListener) activity;
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).activityStart(this, "SortBy");
	}
	
	@Override
	public void onStop() {
		super.onStop();
		VuzeEasyTracker.getInstance(this).activityStop(this);
	}
}
