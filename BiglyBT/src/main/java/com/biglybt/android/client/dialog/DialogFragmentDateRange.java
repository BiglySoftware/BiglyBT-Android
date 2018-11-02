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

import java.util.ArrayList;
import java.util.Calendar;

import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.R;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.util.Thunk;

import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.*;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.NumberPicker;

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
		void onDateRangeChanged(@Nullable String callbackID, long start, long end);
	}

	@Thunk
	long start = 0;

	@Thunk
	long end = -1;

	private long initialStart;

	private long initialEnd;

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
			Window window = dialog.getWindow();
			if (window != null) {
				window.requestFeature(Window.FEATURE_NO_TITLE);
			}
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

		AndroidUtilsUI.AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				getActivity(), R.layout.dialog_date_rangepicker);

		View view = alertDialogBuilder.view;
		AlertDialog.Builder builder = alertDialogBuilder.builder;

		DatePicker pickerValue0 = view.findViewById(R.id.range0_picker_date);
		DatePicker pickerValue1 = view.findViewById(R.id.range1_picker_date);
		if (pickerValue0 != null && pickerValue1 != null) {
			setupPickers(view, pickerValue0, pickerValue1);
		}

		builder.setTitle(R.string.filterby_title);

		Button btnClear = view.findViewById(R.id.range_clear);
		if (btnClear != null) {
			btnClear.setOnClickListener(v -> {
				if (mListener != null) {
					mListener.onDateRangeChanged(callbackID, -1, -1);
				}
				DialogFragmentDateRange.this.getDialog().dismiss();
			});
		}

		Button btnCancel = view.findViewById(R.id.range_cancel);
		if (btnCancel != null) {
			btnCancel.setOnClickListener(
					v -> DialogFragmentDateRange.this.getDialog().dismiss());
		}

		Button btnSet = view.findViewById(R.id.range_set);
		if (btnSet != null) {
			btnSet.setOnClickListener(v -> {
				if (mListener != null) {
					mListener.onDateRangeChanged(callbackID, start, end);
				}
				DialogFragmentDateRange.this.getDialog().dismiss();
			});
		} else {

			// Add action buttons
			builder.setPositiveButton(R.string.action_filterby, (dialog, id) -> {

				if (mListener != null) {
					mListener.onDateRangeChanged(callbackID, start, end);
				}
			});
			builder.setNeutralButton(R.string.button_clear, (dialog, which) -> {
				if (mListener != null) {
					mListener.onDateRangeChanged(callbackID, -1, -1);
				}
				DialogFragmentDateRange.this.getDialog().dismiss();
			});
			builder.setNegativeButton(android.R.string.cancel,
					(dialog, id) -> DialogFragmentDateRange.this.getDialog().cancel());
		}

		AlertDialog dialog = builder.create();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			setMinWidthPX(view.getMinimumWidth());
		}
		return dialog;
	}

	private void setupPickers(final View view, final DatePicker pickerValue0,
			final DatePicker pickerValue1) {

		final View range1Area = view.findViewById(R.id.range1_picker_area);
		final SwitchCompat range1Switch = view.findViewById(
				R.id.range1_picker_switch);

		Calendar c;
		c = initialStart > 0 ? removeTimeFromDate(initialStart)
				: Calendar.getInstance();
		int year = c.get(Calendar.YEAR);
		int month = c.get(Calendar.MONTH);
		int day = c.get(Calendar.DAY_OF_MONTH);

		pickerValue0.init(year, month, day,
				(view1, year1, monthOfYear, dayOfMonth) -> {
					Calendar c1 = Calendar.getInstance();
					c1.set(year1, monthOfYear, dayOfMonth, 0, 0, 0);
					start = c1.getTimeInMillis();
				});

		c = initialEnd > 0 ? removeTimeFromDate(initialEnd)
				: Calendar.getInstance();
		year = c.get(Calendar.YEAR);
		month = c.get(Calendar.MONTH);
		day = c.get(Calendar.DAY_OF_MONTH);

		pickerValue1.init(year, month, day,
				(view12, year12, monthOfYear, dayOfMonth) -> {
					Calendar c12 = Calendar.getInstance();
					c12.set(year12, monthOfYear, dayOfMonth, 0, 0, 0);
					end = c12.getTimeInMillis();
				});

		/*
		ViewGroup.OnHierarchyChangeListener onHierarchyChangeListener = new
		ViewGroup.OnHierarchyChangeListener() {
			@Override
			public void onChildViewAdded(View parent, View child) {
				ArrayList<View> list = new ArrayList<>();
				AndroidUtilsUI.findByClass((ViewGroup) view, NumberPicker.class, list);
				if (list.size() > 0) {
					View lastView = null;
					for (View v : list) {
						if (lastView != null) {
							v.setNextFocusLeftId(lastView.getId());
						}
						v.setBackgroundResource(R.drawable.list_selector_dark);
		//						v.setFocusable(true);
						if (lastView != null) {
							lastView.setNextFocusRightId(v.getId());
						}
						lastView = v;
					}
					if (list.size() == 6) {
						list.get(2).setNextFocusRightId(range1Switch.getId());
		
						pickerValue1.setNextFocusLeftId(list.get(2).getId());
					}
		
					range1Switch.setNextFocusLeftId(list.get(2).getId());
				}
			}
		
			@Override
			public void onChildViewRemoved(View parent, View child) {
		
			}
		};
		((ViewGroup) pickerValue0.getChildAt(0)).setOnHierarchyChangeListener(
				onHierarchyChangeListener);
		((ViewGroup) pickerValue1.getChildAt(
				pickerValue1.getChildCount() - 1)).setOnHierarchyChangeListener(
						onHierarchyChangeListener);
		
		range1Switch.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View vv, boolean hasFocus) {
				ArrayList<View> list = new ArrayList<>();
				AndroidUtilsUI.findByClass((ViewGroup) view, NumberPicker.class, list);
				if (list.size() > 0) {
					View lastView = null;
					for (View v : list) {
						if (lastView != null) {
							v.setNextFocusLeftId(lastView.getId());
						}
						v.setBackgroundResource(R.drawable.list_selector_dark);
		//						v.setFocusable(true);
						if (lastView != null) {
							lastView.setNextFocusRightId(v.getId());
						}
						lastView = v;
					}
					if (list.size() == 6) {
						list.get(2).setNextFocusRightId(range1Switch.getId());
		
						pickerValue1.setNextFocusLeftId(list.get(2).getId());
					}
		
					range1Switch.setNextFocusLeftId(list.get(2).getId());
				}
			}
		});
		*/

		try {
			Class.forName("android.widget.NumberPicker"); // Throws on API 7, maybe others
			ArrayList<View> list = new ArrayList<>(1);
			AndroidUtilsUI.findByClass((ViewGroup) view, NumberPicker.class, list);
			if (list.size() > 0) {
//			View lastView = null;
				for (View v : list) {
//				if (lastView != null) {
//					v.setNextFocusLeftId(lastView.getId());
//				}
					v.setBackgroundResource(R.drawable.list_selector_dark);
//				v.setFocusable(true);
//				if (lastView != null) {
//					lastView.setNextFocusRightId(v.getId());
//				}
//				lastView = v;
//			}
//			if (list.size() == 6) {
//				list.get(2).setNextFocusRightId(range1Switch.getId());
//
//				pickerValue1.setNextFocusLeftId(list.get(2).getId());
				}
//
//			range1Switch.setNextFocusLeftId(list.get(2).getId());

//			list.get(0).requestFocus();
			}
		} catch (ClassNotFoundException ignore) {
		}

		range1Switch.setOnCheckedChangeListener((buttonView, isChecked) -> {
			range1Area.setVisibility(isChecked ? View.VISIBLE : View.GONE);
			if (isChecked) {
				Calendar c13 = Calendar.getInstance();
				c13.set(pickerValue1.getYear(), pickerValue1.getMonth(),
						pickerValue1.getDayOfMonth(), 0, 0, 0);
				end = c13.getTimeInMillis();
			} else {
				end = -1;
			}
		});

		boolean range1Visible = initialEnd >= 0;
		range1Area.setVisibility(range1Visible ? View.VISIBLE : View.GONE);
		range1Switch.setChecked(range1Visible);
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
	public void onAttach(Context context) {
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
}
