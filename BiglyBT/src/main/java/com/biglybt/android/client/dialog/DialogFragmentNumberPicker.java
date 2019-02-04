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
import com.biglybt.android.widget.NumberPickerLB;
import com.biglybt.util.Thunk;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.*;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.view.*;
import android.widget.*;

public class DialogFragmentNumberPicker
	extends DialogFragmentResized
{
	protected static final String TAG = "NumberPickerDialog";

	private static final String KEY_MIN = "min";

	private static final String KEY_MAX = "max";

	private static final String KEY_VAL = "val";

	private static final String KEY_SHOW_SPINNER = "show_spinner";

	private static final String KEY_ID_TITLE = "id_title";

	private static final String KEY_ID_BUTTON_CLEAR = "id_button_clear";

	private static final String KEY_ID_BUTTON_3 = "id_button_3";

	private static final String KEY_ID_SUFFIX = "id_suffix";

	private static final String KEY_CALLBACK_ID = "callbackID";

	@Thunk
	NumberPickerDialogListener mListener;

	public interface NumberPickerDialogListener
	{
		void onNumberPickerChange(@Nullable String callbackID, int val);
	}

	@Thunk
	int numPadNumber = 0;

	@Thunk
	NumberPickerParams params;

	private TextView tvSuffix;

	public static void openDialog(@NonNull NumberPickerBuilder builder) {
		DialogFragment dlg = new DialogFragmentNumberPicker();
		if (builder.targetFragment != null) {
			dlg.setTargetFragment(builder.targetFragment, 0);
		}
		dlg.setArguments(builder.build());
		AndroidUtilsUI.showDialog(dlg, builder.fm, TAG);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		params = new NumberPickerParams(getArguments());

		int val = Math.max(params.min, Math.min(params.max, params.val));

		AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				getActivity(), R.layout.dialog_number_picker);

		final View view = alertDialogBuilder.view;
		AlertDialog.Builder builder = alertDialogBuilder.builder;

		final NumberPicker numberPicker = view.findViewById(R.id.number_picker);
		assert numberPicker != null;
		numberPicker.setVisibility(params.showSpinner ? View.VISIBLE : View.GONE);
		numberPicker.setMinValue(params.min);
		numberPicker.setMaxValue(params.max);
		numberPicker.setOnValueChangedListener(
				(picker, oldVal, newVal) -> numPadNumber = 0);
		numberPicker.setValue(val);

		tvSuffix = view.findViewById(R.id.number_picker_suffix);
		if (tvSuffix != null) {
			if (params.id_suffix != 0) {
				tvSuffix.setText(params.id_suffix);
			} else if (!params.showSpinner) {
				tvSuffix.setText("" + val);
			}
		}

		if (numberPicker instanceof NumberPickerLB) {
			EditText editText = ((NumberPickerLB) numberPicker).getEditText();
			if (editText != null) {
				editText.selectAll();
			}
		}

		if (view.findViewById(R.id.numpad_layout) != null) {
			@IdRes
			int[] ids = {
				R.id.numpad_0,
				R.id.numpad_1,
				R.id.numpad_2,
				R.id.numpad_3,
				R.id.numpad_4,
				R.id.numpad_5,
				R.id.numpad_6,
				R.id.numpad_7,
				R.id.numpad_8,
				R.id.numpad_9,
				R.id.numpad_BS,
			};

			View.OnKeyListener keyListener = (v, keyCode, event) -> {
				if (event.getAction() != KeyEvent.ACTION_UP) {
					return false;
				}
				int i = -1;
				switch (keyCode) {
					case KeyEvent.KEYCODE_0:
					case KeyEvent.KEYCODE_NUMPAD_0:
						i = 0;
						break;
					case KeyEvent.KEYCODE_1:
					case KeyEvent.KEYCODE_NUMPAD_1:
						i = 1;
						break;
					case KeyEvent.KEYCODE_NUMPAD_2:
					case KeyEvent.KEYCODE_2:
						i = 2;
						break;
					case KeyEvent.KEYCODE_3:
					case KeyEvent.KEYCODE_NUMPAD_3:
						i = 3;
						break;
					case KeyEvent.KEYCODE_4:
					case KeyEvent.KEYCODE_NUMPAD_4:
						i = 4;
						break;
					case KeyEvent.KEYCODE_5:
					case KeyEvent.KEYCODE_NUMPAD_5:
						i = 5;
						break;
					case KeyEvent.KEYCODE_6:
					case KeyEvent.KEYCODE_NUMPAD_6:
						i = 6;
						break;
					case KeyEvent.KEYCODE_7:
					case KeyEvent.KEYCODE_NUMPAD_7:
						i = 7;
						break;
					case KeyEvent.KEYCODE_8:
					case KeyEvent.KEYCODE_NUMPAD_8:
						i = 8;
						break;
					case KeyEvent.KEYCODE_9:
					case KeyEvent.KEYCODE_NUMPAD_9:
						i = 9;
						break;
					case KeyEvent.KEYCODE_DEL:
					case KeyEvent.KEYCODE_NUMPAD_DOT:
					case KeyEvent.KEYCODE_MEDIA_REWIND:
						if (numPadNumber == numberPicker.getMinValue()) {
							return false;
						}
						numPadNumber /= 10;
						updateNumberPicker(numberPicker);
						return true;
				}
				if (i >= 0) {
					numPadNumber = numPadNumber * 10 + i;
					updateNumberPicker(numberPicker);
					return true;
				}
				return false;
			};

			for (int i = 0; i < ids.length; i++) {
				@IdRes
				int id = ids[i];

				Object o = view.findViewById(id);

				if (o instanceof ImageButton) {
					((ImageButton) o).setOnClickListener(v -> {
						numPadNumber /= 10;
						updateNumberPicker(numberPicker);
					});
					((ImageButton) o).setOnKeyListener(keyListener);
				} else if (o instanceof Button) {
					Button btn = (Button) o;
					btn.setOnKeyListener(keyListener);
					final int finalI = i;
					btn.setOnClickListener(v -> {
						numPadNumber = numPadNumber * 10 + finalI;
						updateNumberPicker(numberPicker);
					});
				}
			}
		}

		View buttonArea = view.findViewById(R.id.number_picker_buttons);
		boolean useSystemButtons = true;

		if (buttonArea != null) {
			useSystemButtons = !AndroidUtils.isTV(getContext());
			if (useSystemButtons) {
				buttonArea.setVisibility(View.GONE);
			} else {
				Button btnSet = view.findViewById(R.id.range_set);
				if (btnSet != null) {
					btnSet.setOnClickListener(v -> {
						if (mListener != null) {
							mListener.onNumberPickerChange(params.callbackID,
									numberPicker.getValue());
						}
						DialogFragmentNumberPicker.this.getDialog().dismiss();
					});
				}

				Button btnClear = view.findViewById(R.id.range_clear);
				if (btnClear != null) {
					if (params.id_button_clear != 0) {
						btnClear.setText(params.id_button_clear);
					}
					btnClear.setOnClickListener(v -> {
						if (mListener != null) {
							mListener.onNumberPickerChange(params.callbackID, -1);
						}
						DialogFragmentNumberPicker.this.getDialog().dismiss();
					});
				}

				Button btn3 = view.findViewById(R.id.button_3);
				if (btn3 != null) {
					if (params.id_button_3 != 0) {
						btn3.setText(params.id_button_3);
						btn3.setVisibility(View.VISIBLE);
						btn3.setOnClickListener(v -> {
							if (mListener != null) {
								mListener.onNumberPickerChange(params.callbackID, -2);
							}
							DialogFragmentNumberPicker.this.getDialog().dismiss();
						});
					} else {
						btn3.setVisibility(View.GONE);
					}
				}
			}
		}

		builder.setTitle(params.id_title);
		if (useSystemButtons) {
			// Add action buttons
			builder.setPositiveButton(R.string.button_set, (dialog, id) -> {

				if (mListener != null) {
					mListener.onNumberPickerChange(params.callbackID,
							numberPicker.getValue());
				}
			});
			builder.setNeutralButton(params.id_button_clear != 0
					? params.id_button_clear : R.string.button_clear, (dialog, which) -> {
						if (mListener != null) {
							mListener.onNumberPickerChange(params.callbackID, -1);
						}
					});
			builder.setNegativeButton(params.id_button_3 != 0 ? params.id_button_3
					: android.R.string.cancel, (dialog, id) -> {
						if (params.id_button_3 != 0 && mListener != null) {
							mListener.onNumberPickerChange(params.callbackID, -2);
						}

						DialogFragmentNumberPicker.this.getDialog().cancel();
					});
		}

		AlertDialog dialog = builder.create();
		int orientation = getResources().getConfiguration().orientation;

		if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
			int minimumWidth = AndroidUtilsUI.dpToPx(
					AndroidUtils.isTV(requireContext()) ? 700 : Math.min(
							AndroidUtilsUI.getScreenWidthDp(requireContext()) - 32, 580));
			setMinWidthPX(
					minimumWidth + view.getPaddingRight() + view.getPaddingLeft());
		}

		Window window = dialog.getWindow();
		if (window != null) {
			window.setSoftInputMode(
					WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		}

		return dialog;
	}

	@Thunk
	void updateNumberPicker(@NonNull NumberPicker numberPicker) {
		numberPicker.setValue(numPadNumber);
		if (params != null && !params.showSpinner && tvSuffix != null) {
			tvSuffix.setText("" + Math.max(numberPicker.getMinValue(),
					Math.min(numberPicker.getMaxValue(), numPadNumber)));
		}
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);

		mListener = new TargetFragmentFinder<NumberPickerDialogListener>(
				NumberPickerDialogListener.class).findTarget(this, context);
	}

	private static class NumberPickerParams
	{
		@Thunk
		final String callbackID;

		@Thunk
		final int val;

		@Thunk
		final @StringRes int id_title;

		@Thunk
		final int min;

		@Thunk
		final int max;

		@Thunk
		final @StringRes int id_suffix;

		@Thunk
		final @StringRes int id_button_clear;

		@Thunk
		final @StringRes int id_button_3;

		@Thunk
		final boolean showSpinner;

		public NumberPickerParams(Bundle arguments) {
			if (arguments == null) {
				arguments = new Bundle();
			}
			max = arguments.getInt(KEY_MAX);
			min = arguments.getInt(KEY_MIN);
			val = arguments.getInt(KEY_VAL);
			showSpinner = arguments.getBoolean(KEY_SHOW_SPINNER);
			id_title = arguments.getInt(KEY_ID_TITLE);
			id_suffix = arguments.getInt(KEY_ID_SUFFIX);
			id_button_clear = arguments.getInt(KEY_ID_BUTTON_CLEAR);
			id_button_3 = arguments.getInt(KEY_ID_BUTTON_3);
			callbackID = arguments.getString(KEY_CALLBACK_ID);
		}
	}

	public static class NumberPickerBuilder
	{

		private final String callbackID;

		private final int val;

		private @StringRes int id_title = R.string.filterby_title;

		private int min = 0;

		private int max = 100;

		private @StringRes int id_suffix = 0;

		@Thunk
		Fragment targetFragment;

		@Thunk
		FragmentManager fm;

		private int id_button_clear = 0;

		private int id_button_3 = 0;

		private boolean show_spinner = true;

		public NumberPickerBuilder(FragmentManager fm, String callbackID, int val) {
			this.callbackID = callbackID;
			this.val = val;
			this.fm = fm;
		}

		public NumberPickerBuilder setClearButtonText(
				@StringRes int clearButtonText) {
			this.id_button_clear = clearButtonText;
			return this;
		}

		public NumberPickerBuilder set3rdButtonText(@StringRes int button3Text) {
			this.id_button_3 = button3Text;
			return this;
		}

		public NumberPickerBuilder setShowSpinner(boolean showSpinner) {
			this.show_spinner = showSpinner;
			return this;
		}

		public NumberPickerBuilder setSuffix(@StringRes int id_suffix) {
			this.id_suffix = id_suffix;
			return this;
		}

		public NumberPickerBuilder setMax(int max) {
			this.max = max;
			return this;
		}

		public NumberPickerBuilder setMin(int min) {
			this.min = min;
			return this;
		}

		public NumberPickerBuilder setTargetFragment(Fragment targetFragment) {
			this.targetFragment = targetFragment;
			return this;
		}

		public NumberPickerBuilder setFragmentManager(FragmentManager fm) {
			this.fm = fm;
			return this;
		}

		public NumberPickerBuilder setTitleId(@StringRes int id_title) {
			this.id_title = id_title;
			return this;
		}

		@Thunk
		Bundle build() {
			Bundle bundle = new Bundle();
			bundle.putInt(KEY_MIN, min);
			bundle.putInt(KEY_MAX, max);
			bundle.putInt(KEY_VAL, val);
			bundle.putBoolean(KEY_SHOW_SPINNER, show_spinner);
			if (id_title != 0) {
				bundle.putInt(KEY_ID_TITLE, id_title);
			}
			if (id_suffix != 0) {
				bundle.putInt(KEY_ID_SUFFIX, id_suffix);
			}
			if (id_button_clear != 0) {
				bundle.putInt(KEY_ID_BUTTON_CLEAR, id_button_clear);
			}
			if (id_button_3 != 0) {
				bundle.putInt(KEY_ID_BUTTON_3, id_button_3);
			}
			if (callbackID != null) {
				bundle.putString(KEY_CALLBACK_ID, callbackID);
			}
			return bundle;
		}
	}
}
