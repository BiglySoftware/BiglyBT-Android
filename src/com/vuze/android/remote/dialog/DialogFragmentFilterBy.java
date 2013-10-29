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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.vuze.android.remote.R;
import com.vuze.android.remote.VuzeEasyTracker;

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
		VuzeEasyTracker.getInstance(this).activityStart(this, "FilterBy");
	}

	@Override
	public void onStop() {
		super.onStop();
		VuzeEasyTracker.getInstance(this).activityStop(this);
	}
}
