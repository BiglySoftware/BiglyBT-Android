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

package com.biglybt.android.client.dialog;

import com.biglybt.android.TargetFragmentFinder;
import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.AndroidUtilsUI.AlertDialogBuilder;
import com.biglybt.android.client.R;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.Thunk;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.*;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.NumberPicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

public class DialogFragmentSizeRange
	extends DialogFragmentBase
{
	private static final String TAG = "SizeRangeDialog";

	private static final String KEY_START = "start";

	private static final String KEY_CALLBACK_ID = "callbackID";

	private static final String KEY_MAX = "max";

	private static final String KEY_END = "end";

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

	@Thunk
	int[] layouts = null;

	public static void openDialog(FragmentManager fm, Fragment target,
			@Nullable String callbackID, String remoteProfileID, long max, long start,
			long end) {
		DialogFragment dlg = new DialogFragmentSizeRange();
		Bundle bundle = new Bundle();
		bundle.putString(SessionManager.BUNDLE_KEY, remoteProfileID);
		bundle.putLong(KEY_MAX, max);
		bundle.putLong(KEY_START, start);
		bundle.putLong(KEY_END, end);
		bundle.putString(KEY_CALLBACK_ID, callbackID);
		dlg.setArguments(bundle);
		dlg.setTargetFragment(target, 0xDEADB0BB);
		AndroidUtilsUI.showDialog(dlg, fm, TAG);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Bundle arguments = getArguments();
		if (arguments != null) {
			max = arguments.getLong(KEY_MAX);
			initialStart = arguments.getLong(KEY_START);
			initialEnd = arguments.getLong(KEY_END);
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

		Context context = requireContext();
		start = initialStart;

		AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				context,
				AndroidUtils.isTV(getContext()) ? R.layout.dialog_size_rangepicker_tv
						: R.layout.dialog_size_rangepicker);

		View view = alertDialogBuilder.view;
		AlertDialog.Builder builder = alertDialogBuilder.builder;

		TabLayout tabLayout = view.findViewById(R.id.tab_layout);
		if (tabLayout != null) {

			layouts = new int[] {
				R.layout.dialog_size_rangepicker_startfrag,
				R.layout.dialog_size_rangepicker_endfrag
			};

			ViewPager2 viewPager = view.findViewById(R.id.view_pager);
			SizeRangeAdapter adapter = new SizeRangeAdapter();
			viewPager.setAdapter(adapter);
			//noinspection ConstantConditions
			CharSequence[] texts = new CharSequence[] {
				tabLayout.getTabAt(0).getText(),
				tabLayout.getTabAt(1).getText()
			};
			new TabLayoutMediator(tabLayout, viewPager,
					(tab, position) -> tab.setText(texts[position])).attach();
			viewPager.setOffscreenPageLimit(1);
		}

		NumberPicker pickerValue0 = view.findViewById(R.id.range0_picker_number);
		NumberPicker pickerUnit0 = view.findViewById(R.id.range0_picker_unit);
		if (pickerUnit0 != null && pickerValue0 != null) {
			setupPicker0(pickerValue0, pickerUnit0);
		}
		NumberPicker pickerValue1 = view.findViewById(R.id.range1_picker_number);
		NumberPicker pickerUnit1 = view.findViewById(R.id.range1_picker_unit);
		if (pickerUnit1 != null && pickerValue1 != null) {
			setupPicker1(view, pickerValue1, pickerUnit1);
		}

		Button btnSet = view.findViewById(R.id.range_set);
		if (btnSet != null) {
			btnSet.setOnClickListener(v -> {
				if (mListener != null) {
					mListener.onSizeRangeChanged(callbackID, start, end);
				}
				dismissDialog();
			});
		}

		Button btnClear = view.findViewById(R.id.range_clear);
		if (btnClear != null) {
			btnClear.setOnClickListener(v -> {
				if (mListener != null) {
					mListener.onSizeRangeChanged(callbackID, 0, -1);
				}
				dismissDialog();
			});
		}
		Button btnCancel = view.findViewById(R.id.range_cancel);
		if (btnCancel != null) {
			btnCancel.setOnClickListener(v -> dismissDialog());
		}

		if (AndroidUtilsUI.getScreenHeightDp(context) >= 480
				|| AndroidUtilsUI.getScreenWidthDp(context) >= 540) {
			builder.setTitle(R.string.filterby_title);
		}

		if (btnSet == null) {
			// Add action buttons
			builder.setPositiveButton(R.string.action_filterby, (dialog, id) -> {
				if (mListener != null) {
					mListener.onSizeRangeChanged(callbackID, start, end);
				}
			});
			builder.setNeutralButton(R.string.button_clear, (dialog, which) -> {
				if (mListener != null) {
					mListener.onSizeRangeChanged(callbackID, 0, -1);
				}
			});
			builder.setNegativeButton(android.R.string.cancel,
					(dialog, id) -> cancelDialog());
		}

		AlertDialog dialog = builder.create();
		Window window = dialog.getWindow();
		if (window != null) {
			window.setSoftInputMode(
					WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		}
		// Thanks https://stackoverflow.com/a/9118027
		// Dialog will set us not focusable if our edittext isn't visible initially,
		// so we must clear the flag in order to get soft keyboard to work (API 15)
		dialog.setOnShowListener(dialog1 -> dialog.getWindow().clearFlags(
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
						| WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM));

		return dialog;
	}

	@Thunk
	void setupPicker0(@NonNull final NumberPicker pickerValue0,
			@NonNull final NumberPicker pickerUnit0) {

		pickerValue0.setMinValue(0);
		pickerValue0.setMaxValue(1024);
		pickerValue0.setOnValueChangedListener(
				(picker, oldVal, newVal) -> start = AndroidUtils.mutiplyBy1024(
						pickerValue0.getValue(), pickerUnit0.getValue() + 2));

		pickerUnit0.setMinValue(0);
		pickerUnit0.setMaxValue(2);
		pickerUnit0.setDisplayedValues(new String[] {
			DisplayFormatters.getUnit(DisplayFormatters.UNIT_MB),
			DisplayFormatters.getUnit(DisplayFormatters.UNIT_GB),
			DisplayFormatters.getUnit(DisplayFormatters.UNIT_TB)
		});
		pickerUnit0.setOnValueChangedListener(
				(picker, oldVal, newVal) -> start = AndroidUtils.mutiplyBy1024(
						pickerValue0.getValue(), pickerUnit0.getValue() + 2));

		int[] normalizedPickerValues = normalizePickerValue(initialStart);
		pickerValue0.setValue(normalizedPickerValues[0]);
		pickerUnit0.setValue(normalizedPickerValues[1]);
	}

	@Thunk
	void setupPicker1(@NonNull View view,
			@NonNull final NumberPicker pickerValue1,
			@NonNull final NumberPicker pickerUnit1) {

		final View range1Area = view.findViewById(R.id.range1_picker_area);
		CompoundButton range1Switch = view.findViewById(R.id.range1_picker_switch);

		range1Switch.setOnCheckedChangeListener((buttonView, isChecked) -> {
			range1Area.setVisibility(isChecked ? View.VISIBLE : View.GONE);
			if (isChecked) {
				end = AndroidUtils.mutiplyBy1024(pickerValue1.getValue(),
						pickerUnit1.getValue() + 2);
			} else {
				end = -1;
			}
		});

		boolean range1Visible = initialEnd >= 0 && initialEnd < max;
		range1Area.setVisibility(range1Visible ? View.VISIBLE : View.GONE);
		range1Switch.setChecked(range1Visible);

		pickerValue1.setMinValue(0);
		pickerValue1.setMaxValue(1024);
		pickerValue1.setOnValueChangedListener(
				(picker, oldVal, newVal) -> end = AndroidUtils.mutiplyBy1024(
						pickerValue1.getValue(), pickerUnit1.getValue() + 2));

		pickerUnit1.setMinValue(0);
		pickerUnit1.setMaxValue(2);
		pickerUnit1.setDisplayedValues(new String[] {
			DisplayFormatters.getUnit(DisplayFormatters.UNIT_MB),
			DisplayFormatters.getUnit(DisplayFormatters.UNIT_GB),
			DisplayFormatters.getUnit(DisplayFormatters.UNIT_TB)
		});
		int[] normalizedPickerValues = normalizePickerValue(initialEndRounded);
		pickerValue1.setValue(normalizedPickerValues[0]);
		pickerUnit1.setValue(normalizedPickerValues[1]);
		pickerUnit1.setOnValueChangedListener(
				(picker, oldVal, newVal) -> end = AndroidUtils.mutiplyBy1024(
						pickerValue1.getValue(), pickerUnit1.getValue() + 2));
	}

	private static int[] normalizePickerValue(long bytes) {
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
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);

		mListener = new TargetFragmentFinder<DialogFragmentSizeRange.SizeRangeDialogListener>(
				DialogFragmentSizeRange.SizeRangeDialogListener.class).findTarget(this,
						context);
	}

	public class SizeRangeAdapter
		extends RecyclerView.Adapter<RecyclerView.ViewHolder>
	{

		private class SizeRangeViewHolder
			extends RecyclerView.ViewHolder
		{
			SizeRangeViewHolder(View view) {
				super(view);
			}
		}

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
				int viewType) {
			View view = LayoutInflater.from(parent.getContext()).inflate(viewType,
					parent, false);

			return new SizeRangeViewHolder(view);
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder,
				int position) {
			if (position == 0) {
				NumberPicker pickerValue0 = holder.itemView.findViewById(
						R.id.range0_picker_number);
				NumberPicker pickerUnit0 = holder.itemView.findViewById(
						R.id.range0_picker_unit);
				if (pickerValue0 != null && pickerUnit0 != null) {
					setupPicker0(pickerValue0, pickerUnit0);
				}
			} else {
				NumberPicker pickerValue1 = holder.itemView.findViewById(
						R.id.range1_picker_number);
				NumberPicker pickerUnit1 = holder.itemView.findViewById(
						R.id.range1_picker_unit);
				if (pickerValue1 != null && pickerUnit1 != null) {
					setupPicker1(holder.itemView, pickerValue1, pickerUnit1);
				}
			}
		}

		@Override
		public int getItemViewType(int position) {
			return layouts[position];
		}

		@Override
		public int getItemCount() {
			return layouts.length;
		}
	}
}
