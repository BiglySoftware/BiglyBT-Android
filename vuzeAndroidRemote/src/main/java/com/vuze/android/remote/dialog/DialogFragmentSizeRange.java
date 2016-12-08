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
import com.vuze.android.remote.AndroidUtilsUI.AlertDialogBuilder;
import com.vuze.util.Thunk;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;

import eu.rekisoft.android.numberpicker.NumberPicker;

public class DialogFragmentSizeRange
	extends DialogFragmentBase
{
	private static final String TAG = "SizeRangeDialog";

	private static final String KEY_START = "start";

	private static final String KEY_CALLBACK_ID = "callbackID";

	@Thunk
	SizeRangeDialogListener mListener;

	public interface SizeRangeDialogListener
	{
		void onSizeRangeChanged(@Nullable String callbackID, long start, long end);
	}

	@Thunk
	long start = 0;

	@Thunk
	long end = -1;

	private long max;

	private long initialStart;

	private long initialEnd;

	private long initialEndRounded;

	public static void openDialog(FragmentManager fm, @Nullable String callbackID,
			String remoteProfileID, long max, long start, long end) {
		DialogFragment dlg = new DialogFragmentSizeRange();
		Bundle bundle = new Bundle();
		bundle.putString(SessionInfoManager.BUNDLE_KEY, remoteProfileID);
		bundle.putLong("max", max);
		bundle.putLong(KEY_START, start);
		bundle.putLong("end", end);
		bundle.putString(KEY_CALLBACK_ID, callbackID);
		dlg.setArguments(bundle);
		AndroidUtilsUI.showDialog(dlg, fm, TAG);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Bundle arguments = getArguments();
		if (arguments != null) {
			max = arguments.getLong("max");
			initialStart = arguments.getLong(KEY_START);
			initialEnd = arguments.getLong("end");
		}
		final String callbackID = arguments == null ? null
				: arguments.getString(KEY_CALLBACK_ID);

		if (max <= 0) {
			max = 1024;
		}
		if (initialEnd <= 0) {
			initialEnd = max;
		}

		int normalizeLevel = 2;
		long n = initialEnd / 1024 / 1024;
		while (n > 1024 * 2) {
			n = n / 1024;
			normalizeLevel++;
		}
		initialEndRounded = (n + 1) << (normalizeLevel * 10);

		start = initialStart;

		AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				getActivity(), AndroidUtils.isTV() ? R.layout.dialog_size_rangepicker_tv
						: R.layout.dialog_size_rangepicker);

		View view = alertDialogBuilder.view;
		AlertDialog.Builder builder = alertDialogBuilder.builder;

		NumberPicker pickerValue0 = (NumberPicker) view.findViewById(
				R.id.range0_picker_number);
		NumberPicker pickerUnit0 = (NumberPicker) view.findViewById(
				R.id.range0_picker_unit);
		NumberPicker pickerValue1 = (NumberPicker) view.findViewById(
				R.id.range1_picker_number);
		NumberPicker pickerUnit1 = (NumberPicker) view.findViewById(
				R.id.range1_picker_unit);
		if (pickerUnit0 != null && pickerUnit1 != null && pickerValue0 != null
				&& pickerValue1 != null) {
			setupPickers(view, pickerValue0, pickerUnit0, pickerValue1, pickerUnit1);
		}

		Button btnSet = (Button) view.findViewById(R.id.range_set);
		if (btnSet != null) {
			btnSet.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (mListener != null) {
						mListener.onSizeRangeChanged(callbackID, start, end);
					}
					DialogFragmentSizeRange.this.getDialog().dismiss();
				}
			});
		}

		Button btnClear = (Button) view.findViewById(R.id.range_clear);
		if (btnClear != null) {
			btnClear.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (mListener != null) {
						mListener.onSizeRangeChanged(callbackID, 0, -1);
					}
					DialogFragmentSizeRange.this.getDialog().dismiss();
				}
			});
		}
		Button btnCancel = (Button) view.findViewById(R.id.range_cancel);
		if (btnCancel != null) {
			btnCancel.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					DialogFragmentSizeRange.this.getDialog().dismiss();
				}
			});
		}

		builder.setTitle(R.string.filterby_title);

		if (btnSet == null) {
			// Add action buttons
			builder.setPositiveButton(R.string.action_filterby,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int id) {

							if (mListener != null) {
								mListener.onSizeRangeChanged(callbackID, start, end);
							}
						}
					});
			builder.setNeutralButton(R.string.button_clear,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							if (mListener != null) {
								mListener.onSizeRangeChanged(callbackID, 0, -1);
							}
						}
					});
			builder.setNegativeButton(android.R.string.cancel,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int id) {
							DialogFragmentSizeRange.this.getDialog().cancel();
						}
					});
		}

		AlertDialog dialog = builder.create();
		dialog.getWindow().setSoftInputMode(
				WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

		return dialog;
	}

	private void setupPickers(View view, final NumberPicker pickerValue0,
			final NumberPicker pickerUnit0, final NumberPicker pickerValue1,
			final NumberPicker pickerUnit1) {

		pickerValue0.setMinValue(0);
		pickerValue0.setMaxValue(1024);
		pickerValue0.setOnValueChangedListener(
				new NumberPicker.OnValueChangeListener() {
					@Override
					public void onValueChange(NumberPicker picker, int oldVal,
							int newVal) {
						start = AndroidUtils.mutiplyBy1024(pickerValue0.getValue(),
								pickerUnit0.getValue() + 2);
					}
				});

		pickerUnit0.setMinValue(0);
		pickerUnit0.setMaxValue(2);
		pickerUnit0.setDisplayedValues(new String[] {
			"MB",
			"GB",
			"TB"
		});
		pickerUnit0.setOnValueChangedListener(
				new NumberPicker.OnValueChangeListener() {
					@Override
					public void onValueChange(NumberPicker picker, int oldVal,
							int newVal) {
						start = AndroidUtils.mutiplyBy1024(pickerValue0.getValue(),
								pickerUnit0.getValue() + 2);
					}
				});

		int[] normalizedPickerValues = normalizePickerValue(initialStart);
		pickerValue0.setValue(normalizedPickerValues[0]);
		pickerUnit0.setValue(normalizedPickerValues[1]);

		final View range1Area = view.findViewById(R.id.range1_picker_area);
		SwitchCompat range1Switch = (SwitchCompat) view.findViewById(
				R.id.range1_picker_switch);

		range1Switch.setOnCheckedChangeListener(
				new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView,
							boolean isChecked) {
						range1Area.setVisibility(isChecked ? View.VISIBLE : View.GONE);
						if (isChecked) {
							end = AndroidUtils.mutiplyBy1024(pickerValue1.getValue(),
									pickerUnit1.getValue() + 2);
						} else {
							end = -1;
						}
					}
				});

		boolean range1Visible = initialEnd >= 0 && initialEnd < max;
		range1Area.setVisibility(range1Visible ? View.VISIBLE : View.GONE);
		range1Switch.setChecked(range1Visible);

		pickerValue1.setMinValue(0);
		pickerValue1.setMaxValue(1024);
		pickerValue1.setOnValueChangedListener(
				new NumberPicker.OnValueChangeListener() {
					@Override
					public void onValueChange(NumberPicker picker, int oldVal,
							int newVal) {
						end = AndroidUtils.mutiplyBy1024(pickerValue1.getValue(),
								pickerUnit1.getValue() + 2);
					}
				});

		pickerUnit1.setMinValue(0);
		pickerUnit1.setMaxValue(2);
		pickerUnit1.setDisplayedValues(new String[] {
			"MB",
			"GB",
			"TB"
		});
		normalizedPickerValues = normalizePickerValue(initialEndRounded);
		pickerValue1.setValue(normalizedPickerValues[0]);
		pickerUnit1.setValue(normalizedPickerValues[1]);
		pickerUnit1.setOnValueChangedListener(
				new NumberPicker.OnValueChangeListener() {
					@Override
					public void onValueChange(NumberPicker picker, int oldVal,
							int newVal) {
						end = AndroidUtils.mutiplyBy1024(pickerValue1.getValue(),
								pickerUnit1.getValue() + 2);
					}
				});
	}

	private int[] normalizePickerValue(long bytes) {
		if (bytes <= 0) {
			return new int[] {
				0,
				1
			};
		}

		long val = bytes / 1024 / 1024; // val
		int unit = 0;

		if (val % 1024 == 0) {
			val /= 1024;
			if (val % 1024 == 0) {
				val /= 1024;
				unit = 2;
			} else {
				unit = 1;
			}
		}

		return new int[] {
			(int) val,
			unit
		};
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);

		Fragment targetFragment = getTargetFragment();
		if (targetFragment instanceof SizeRangeDialogListener) {
			mListener = (SizeRangeDialogListener) targetFragment;
		} else if (context instanceof SizeRangeDialogListener) {
			mListener = (SizeRangeDialogListener) context;
		} else {
			Log.e(TAG, "No Target Fragment " + targetFragment);
		}

	}

	@Override
	public String getLogTag() {
		return TAG;
	}
}
