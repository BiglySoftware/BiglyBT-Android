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

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.DatePicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.OffThread;
import com.biglybt.android.client.R;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.util.Thunk;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.Calendar;

public class DialogFragmentDateRange
	extends DialogFragmentResized
{
	private static final String TAG = "DateRangeDialog";

	private static final String KEY_START = "start";

	private static final String KEY_END = "end";

	private static final String KEY_CALLBACK_ID = "callbackID";

	@Thunk
	DateRangeDialogListener mListener;

	public interface DateRangeDialogListener
	{
		@WorkerThread
		void onDateRangeChanged(@Nullable String callbackID, long start, long end);
	}

	@Thunk
	long start = 0;

	@Thunk
	long end = -1;

	private long initialStart;

	private long initialEnd;

	public DialogFragmentDateRange() {
		setDialogWidthRes(R.dimen.dlg_datepicker_width);
		setDialogHeightRes(R.dimen.dlg_datepicker_height);
	}

	public static void openDialog(FragmentManager fm, @Nullable String callbackID,
			String remoteProfileID, long start, long end) {
		DialogFragment dlg = new DialogFragmentDateRange();
		// Put things into Bundle instead of passing as a constructor, since
		// Android may regenerate this Dialog with no constructor.
		Bundle bundle = new Bundle();
		bundle.putString(SessionManager.BUNDLE_KEY, remoteProfileID);
		bundle.putLong(KEY_START, start);
		bundle.putLong(KEY_END, end);
		bundle.putString(KEY_CALLBACK_ID, callbackID);
		dlg.setArguments(bundle);
		AndroidUtilsUI.showDialog(dlg, fm, TAG);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		Dialog dialog = getDialog();
		if (dialog != null) {
			dialog.setCanceledOnTouchOutside(true);
		}
		return super.onCreateView(inflater, container, savedInstanceState);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Bundle arguments = getArguments();
		if (arguments != null) {
			initialStart = arguments.getLong(KEY_START);
			initialEnd = arguments.getLong(KEY_END);
		}
		final String callbackID = arguments == null ? null
				: arguments.getString(KEY_CALLBACK_ID);

		Context context = requireContext();
		AndroidUtilsUI.AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				context, R.layout.dialog_date_rangepicker);

		View view = alertDialogBuilder.view;
		AlertDialog.Builder builder = alertDialogBuilder.builder;

		TabLayout tabLayout = view.findViewById(R.id.tab_layout);
		if (tabLayout != null) {

			int[] layouts = new int[] {
				R.layout.dialog_date_rangepicker_startfrag,
				R.layout.dialog_date_rangepicker_endfrag
			};

			ViewPager2 viewPager = ViewCompat.requireViewById(view, R.id.view_pager);
			DateRangeAdapter adapter = new DateRangeAdapter(layouts);
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

		DatePicker pickerValue0 = view.findViewById(R.id.range0_picker_date);
		if (pickerValue0 != null) {
			setupPicker0(view, pickerValue0);
		}
		DatePicker pickerValue1 = view.findViewById(R.id.range1_picker_date);
		if (pickerValue1 != null) {
			setupPicker1(view, pickerValue1);
		}

		if (AndroidUtilsUI.getScreenHeightDp(context) >= 480
				|| AndroidUtilsUI.getScreenWidthDp(context) >= 540) {
			builder.setTitle(R.string.filterby_title);
		}

		Button btnClear = view.findViewById(R.id.range_clear);
		if (btnClear != null) {
			btnClear.setOnClickListener(v -> trigger(callbackID, -1, -1, true));
		}

		Button btnCancel = view.findViewById(R.id.range_cancel);
		if (btnCancel != null) {
			btnCancel.setOnClickListener(v -> dismissDialog());
		}

		Button btnSet = view.findViewById(R.id.range_set);
		if (btnSet != null) {
			btnSet.setOnClickListener(v -> trigger(callbackID, start, end, true));
		} else {

			// Add action buttons
			builder.setPositiveButton(R.string.action_filterby,
					(dialog, id) -> trigger(callbackID, start, end, false));
			builder.setNeutralButton(R.string.button_clear,
					(dialog, which) -> trigger(callbackID, -1, -1, true));
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

	private void trigger(String callbackID, long start, long end, boolean close) {
		if (mListener != null) {
			OffThread.runOffUIThread(
					() -> mListener.onDateRangeChanged(callbackID, start, end));
		}
		if (close) {
			dismissDialog();
		}
	}

	@Thunk
	void setupPicker0(@NonNull View view,
			@NonNull final DatePicker pickerValue0) {
		Calendar c;
		c = removeTimeFromDate(
				initialStart > 0 ? initialStart : System.currentTimeMillis());
		int year = c.get(Calendar.YEAR);
		int month = c.get(Calendar.MONTH);
		int day = c.get(Calendar.DAY_OF_MONTH);
		start = c.getTimeInMillis();

		pickerValue0.init(year, month, day,
				(view1, year1, monthOfYear, dayOfMonth) -> {
					Calendar c1 = Calendar.getInstance();
					c1.set(year1, monthOfYear, dayOfMonth, 0, 0, 0);
					start = c1.getTimeInMillis();
				});

		AndroidUtilsUI.changePickersBackground((ViewGroup) view);
	}

	@Thunk
	void setupPicker1(@NonNull View view,
			@NonNull final DatePicker pickerValue1) {

		final View range1Area = view.findViewById(R.id.range1_picker_area);
		final CompoundButton range1Switch = ViewCompat.requireViewById(view,
				R.id.range1_picker_switch);

		Calendar c = initialEnd > 0 ? removeTimeFromDate(initialEnd)
				: Calendar.getInstance();
		int year = c.get(Calendar.YEAR);
		int month = c.get(Calendar.MONTH);
		int day = c.get(Calendar.DAY_OF_MONTH);

		pickerValue1.init(year, month, day,
				(view12, year12, monthOfYear, dayOfMonth) -> {
					Calendar c12 = Calendar.getInstance();
					c12.set(year12, monthOfYear, dayOfMonth, 0, 0, 0);
					end = c12.getTimeInMillis();
				});

		AndroidUtilsUI.changePickersBackground((ViewGroup) view);

		range1Switch.setOnCheckedChangeListener((buttonView, isChecked) -> {
			if (range1Area == null) {
				pickerValue1.setVisibility(isChecked ? View.VISIBLE : View.INVISIBLE);
			} else {
				range1Area.setVisibility(isChecked ? View.VISIBLE : View.GONE);
			}

			if (isChecked) {
				Calendar c13 = Calendar.getInstance();
				c13.set(pickerValue1.getYear(), pickerValue1.getMonth(),
						pickerValue1.getDayOfMonth(), 0, 0, 0);
				end = c13.getTimeInMillis();
			} else {
				end = -1;
			}
		});

		boolean isChecked = initialEnd >= 0;
		if (range1Area == null) {
			pickerValue1.setVisibility(isChecked ? View.VISIBLE : View.INVISIBLE);
		} else {
			range1Area.setVisibility(isChecked ? View.VISIBLE : View.GONE);
		}
		range1Switch.setChecked(isChecked);
	}

	private static Calendar removeTimeFromDate(long date) {
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(date);
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);

		return c;
	}

	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);

		Fragment targetFragment = getTargetFragment();
		if (targetFragment instanceof DateRangeDialogListener) {
			mListener = (DateRangeDialogListener) targetFragment;
		} else if (context instanceof DateRangeDialogListener) {
			mListener = (DateRangeDialogListener) context;
		} else {
			Log.e(TAG, "No Target Fragment " + targetFragment);
		}
	}

	public class DateRangeAdapter
		extends RecyclerView.Adapter<RecyclerView.ViewHolder>
	{
		@NonNull
		private final int[] layouts;

		DateRangeAdapter(@NonNull int[] layouts) {
			super();
			this.layouts = layouts;
		}

		private class DateRangeViewHolder
			extends RecyclerView.ViewHolder
		{
			DateRangeViewHolder(@NonNull View view) {
				super(view);
			}
		}

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
				int viewType) {
			LayoutInflater inflater = LayoutInflater.from(parent.getContext());
			if (inflater == null) {
				throw new IllegalStateException("Inflater null");
			}
			View view = AndroidUtilsUI.requireInflate(inflater, viewType, parent,
					false);

			return new DateRangeViewHolder(view);
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder,
				int position) {
			if (position == 0) {
				DatePicker pickerValue0 = holder.itemView.findViewById(
						R.id.range0_picker_date);
				if (pickerValue0 != null) {
					setupPicker0(holder.itemView, pickerValue0);
				}
			} else {
				DatePicker pickerValue1 = holder.itemView.findViewById(
						R.id.range1_picker_date);
				if (pickerValue1 != null) {
					setupPicker1(holder.itemView, pickerValue1);
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
