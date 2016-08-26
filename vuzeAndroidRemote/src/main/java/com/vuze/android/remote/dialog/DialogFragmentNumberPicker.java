/*
 * Copyright (c) Azureus Software, Inc, All Rights Reserved.
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
 */

package com.vuze.android.remote.dialog;

import com.vuze.android.remote.*;
import com.vuze.android.remote.fragment.SessionInfoGetter;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.*;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import eu.rekisoft.android.numberpicker.NumberPicker;

public class DialogFragmentNumberPicker
	extends DialogFragmentBase
{
	private static final String TAG = "NumberPickerDialog";

	/* @Thunk */ NumberPickerDialogListener mListener;

	public interface NumberPickerDialogListener
	{
		void onNumberPickerChange(String callbackID, int val);
	}

	/* @Thunk */ int val = 0;

	private int max;

	private int initialVal;

	private int min;

	public static void openDialog(FragmentManager fm, String callbackID,
			String remoteProfileID, int id_title, int currentVal, int min, int max) {
		DialogFragment dlg = new DialogFragmentNumberPicker();
		Bundle bundle = new Bundle();
		bundle.putString(SessionInfoManager.BUNDLE_KEY, remoteProfileID);
		bundle.putInt("min", min);
		bundle.putInt("max", max);
		bundle.putInt("val", currentVal);
		if (id_title > 0) {
			bundle.putInt("id_title", id_title);
		}
		bundle.putString("callbackID", callbackID);
		dlg.setArguments(bundle);
		AndroidUtilsUI.showDialog(dlg, fm, "SizeRangeDialog");
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		int id_title = R.string.filterby_title;

		Bundle arguments = getArguments();
		if (arguments != null) {
			max = arguments.getInt("max");
			min = arguments.getInt("min");
			initialVal = arguments.getInt("val");
			id_title = arguments.getInt("id_title");
		}
		final String callbackID = arguments == null ? null
				: arguments.getString("callbackID");

		if (max <= 0) {
			max = 1024;
		}

		val = Math.max(min, Math.min(max, initialVal));

		AndroidUtils.AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				getActivity(), AndroidUtils.isTV() ? R.layout.dialog_number_picker_tv
						: R.layout.dialog_number_picker);

		View view = alertDialogBuilder.view;
		AlertDialog.Builder builder = alertDialogBuilder.builder;

		final NumberPicker numberPicker = (NumberPicker) view.findViewById(
				R.id.number_picker);
		numberPicker.setMinValue(min);
		numberPicker.setMaxValue(max);
		numberPicker.setOnValueChangedListener(
				new NumberPicker.OnValueChangeListener() {
					@Override
					public void onValueChange(NumberPicker picker, int oldVal,
							int newVal) {
						val = numberPicker.getValue();
					}
				});

		numberPicker.setValue(val);

		Button btnSet = (Button) view.findViewById(R.id.range_set);
		if (btnSet != null) {
			btnSet.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (mListener != null) {
						mListener.onNumberPickerChange(callbackID, val);
					}
					DialogFragmentNumberPicker.this.getDialog().dismiss();
				}
			});
		}

		Button btnClear = (Button) view.findViewById(R.id.range_clear);
		if (btnClear != null) {
			btnClear.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (mListener != null) {
						mListener.onNumberPickerChange(callbackID, -1);
					}
					DialogFragmentNumberPicker.this.getDialog().dismiss();
				}
			});
		}

		builder.setTitle(id_title);

		if (btnSet == null) {
			// Add action buttons
			builder.setPositiveButton(R.string.action_filterby,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int id) {

							if (mListener != null) {
								mListener.onNumberPickerChange(callbackID, val);
							}
						}
					});
			builder.setNeutralButton(R.string.button_clear,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							if (mListener != null) {
								mListener.onNumberPickerChange(callbackID, -1);
							}
						}
					});
			builder.setNegativeButton(android.R.string.cancel,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int id) {
							DialogFragmentNumberPicker.this.getDialog().cancel();
						}
					});
		}

		AlertDialog dialog = builder.create();
		dialog.getWindow().setSoftInputMode(
				WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

		return dialog;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);

		Fragment targetFragment = getTargetFragment();
		if (targetFragment instanceof NumberPickerDialogListener) {
			mListener = (NumberPickerDialogListener) targetFragment;
		} else if (context instanceof NumberPickerDialogListener) {
			mListener = (NumberPickerDialogListener) context;
		} else {
			Log.e(TAG, "No Target Fragment " + targetFragment);
		}

	}

	@Override
	public String getLogTag() {
		return TAG;
	}
}
