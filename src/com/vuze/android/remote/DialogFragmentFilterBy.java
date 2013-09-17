package com.vuze.android.remote;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

public class DialogFragmentFilterBy
	extends DialogFragment
{
	public interface FilterByDialogListner {
		void filterBy(String filterMode);
	}

	private FilterByDialogListner mListener;

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.filterby_title);
		builder.setItems(R.array.filterby_list,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						if (mListener == null) {
							return;
						}
						switch (which) {
							case 0: // <item>All</item>
								mListener.filterBy("Prefs._FilterAll");
								break;
							case 1: // <item>Active</item>
								mListener.filterBy("Prefs._FilterActive");
								break;
							case 2: // <item>Complete</item>
								mListener.filterBy("Prefs._FilterComplete");
								break;
							case 3: // <item>Incomplete</item>
								mListener.filterBy("Prefs._FilterIncomplete");
								break;
							case 4: // <item>Stopped</item>
								mListener.filterBy("Prefs._FilterStopped");
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
		
		if (activity instanceof FilterByDialogListner) {
			mListener = (FilterByDialogListner) activity;
		}
	}
}
