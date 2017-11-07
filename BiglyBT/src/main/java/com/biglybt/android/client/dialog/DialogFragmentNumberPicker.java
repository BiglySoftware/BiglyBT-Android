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

import java.lang.reflect.Field;
import java.util.List;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.AndroidUtilsUI.AlertDialogBuilder;
import com.biglybt.android.client.R;
import com.biglybt.android.widget.NumberPickerLB;
import com.biglybt.util.Thunk;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.*;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.*;
import android.widget.*;

public class DialogFragmentNumberPicker
	extends DialogFragmentResized
{
	protected static final String TAG = "NumberPickerDialog";

	private static final String KEY_MIN = "min";

	private static final String KEY_MAX = "max";

	private static final String KEY_VAL = "val";

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

	private int numPadNumber = 0;

	private NumberPickerParams params;

	public static void openDialog(NumberPickerBuilder builder) {
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

		View view = alertDialogBuilder.view;
		AlertDialog.Builder builder = alertDialogBuilder.builder;

		final NumberPicker numberPicker = view.findViewById(R.id.number_picker);
		numberPicker.setMinValue(params.min);
		numberPicker.setMaxValue(params.max);
		numberPicker.setOnValueChangedListener(
				new NumberPicker.OnValueChangeListener() {
					@Override
					public void onValueChange(NumberPicker picker, int oldVal,
							int newVal) {
						numPadNumber = 0;
					}
				});
		numberPicker.setValue(val);

		if (params.id_suffix > 0) {
			TextView tvSuffix = view.findViewById(R.id.number_picker_suffix);
			if (tvSuffix != null) {
				tvSuffix.setText(params.id_suffix);
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

			View.OnKeyListener keyListener = new View.OnKeyListener() {
				@Override
				public boolean onKey(View v, int keyCode, KeyEvent event) {
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
						case KeyEvent.KEYCODE_BACK:
						case KeyEvent.KEYCODE_NUMPAD_DOT:
						case KeyEvent.KEYCODE_MEDIA_REWIND:
							if (numPadNumber == numberPicker.getMinValue()) {
								return false;
							}
							numPadNumber /= 10;
							numberPicker.setValue(
									Math.max(numberPicker.getMinValue(), numPadNumber));
							return true;
					}
					if (i >= 0) {
						numPadNumber = numPadNumber * 10 + i;
						numberPicker.setValue(
								Math.min(numberPicker.getMaxValue(), numPadNumber));
						return true;
					}
					return false;
				}
			};

			for (int i = 0; i < ids.length; i++) {
				@IdRes
				int id = ids[i];

				Object o = view.findViewById(id);

				if (o instanceof ImageButton) {
					((ImageButton) o).setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							numPadNumber /= 10;
							numberPicker.setValue(
									Math.max(numberPicker.getMinValue(), numPadNumber));
						}
					});
					((ImageButton) o).setOnKeyListener(keyListener);
				} else if (o instanceof Button) {
					Button btn = (Button) o;
					btn.setOnKeyListener(keyListener);
					final int finalI = i;
					btn.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							numPadNumber = numPadNumber * 10 + finalI;
							numberPicker.setValue(
									Math.min(numberPicker.getMaxValue(), numPadNumber));
						}
					});
				}
			}
		}

		View buttonArea = view.findViewById(R.id.number_picker_buttons);
		boolean useSystemButtons = true;

		if (buttonArea != null) {
			useSystemButtons = !AndroidUtils.isTV();
			if (useSystemButtons) {
				buttonArea.setVisibility(View.GONE);
			} else {
				Button btnSet = view.findViewById(R.id.range_set);
				if (btnSet != null) {
					btnSet.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							if (mListener != null) {
								mListener.onNumberPickerChange(params.callbackID,
										numberPicker.getValue());
							}
							DialogFragmentNumberPicker.this.getDialog().dismiss();
						}
					});
				}

				Button btnClear = view.findViewById(R.id.range_clear);
				if (btnClear != null) {
					if (params.id_button_clear > 0) {
						btnClear.setText(params.id_button_clear);
					}
					btnClear.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							if (mListener != null) {
								mListener.onNumberPickerChange(params.callbackID, -1);
							}
							DialogFragmentNumberPicker.this.getDialog().dismiss();
						}
					});
				}

				Button btn3 = view.findViewById(R.id.button_3);
				if (btn3 != null) {
					if (params.id_button_3 > 0) {
						btn3.setText(params.id_button_3);
						btn3.setVisibility(View.VISIBLE);
						btn3.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								if (mListener != null) {
									mListener.onNumberPickerChange(params.callbackID, -2);
								}
								DialogFragmentNumberPicker.this.getDialog().dismiss();
							}
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
			builder.setPositiveButton(R.string.button_set,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int id) {

							if (mListener != null) {
								mListener.onNumberPickerChange(params.callbackID,
										numberPicker.getValue());
							}
						}
					});
			builder.setNeutralButton(params.id_button_clear > 0
					? params.id_button_clear : R.string.button_clear,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							if (mListener != null) {
								mListener.onNumberPickerChange(params.callbackID, -1);
							}
						}
					});
			builder.setNegativeButton(
					params.id_button_3 > 0 ? params.id_button_3 : android.R.string.cancel,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int id) {
							if (params.id_button_3 > 0 && mListener != null) {
								mListener.onNumberPickerChange(params.callbackID, -2);
							}

							DialogFragmentNumberPicker.this.getDialog().cancel();
						}
					});
		}

		AlertDialog dialog = builder.create();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			setMinWidthPX(view.getMinimumWidth());
		}
		Window window = dialog.getWindow();
		if (window != null) {
			window.setSoftInputMode(
					WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		}

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
			// can't use targetFragment for non-appcompat Fragment -- need to
			// poke around for a fragment with a listener, or use some other
			// communication mechanism
			android.app.FragmentManager fragmentManager = getActivity().getFragmentManager();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				Object fragment = fragmentManager.getPrimaryNavigationFragment();
				if (fragment instanceof NumberPickerDialogListener) {
					mListener = (NumberPickerDialogListener) fragment;
				}
			} else {
				try {
					Field field = fragmentManager.getClass().getDeclaredField("mActive");
					field.setAccessible(true);
					@SuppressWarnings("unchecked")
					List<android.app.Fragment> listActive = (List<android.app.Fragment>) field.get(
							fragmentManager);
					for (android.app.Fragment fragment : listActive) {
						if (fragment instanceof NumberPickerDialogListener) {
							mListener = (NumberPickerDialogListener) fragment;
							break;
						}
					}
				} catch (Exception ignore) {
					ignore.printStackTrace();
				}
			}
			Log.e(TAG, "No Target Fragment " + targetFragment);
		}

	}

	@Override
	public String getLogTag() {
		return TAG;
	}

	private static class NumberPickerParams
	{
		private final String callbackID;

		private final int val;

		private final @StringRes int id_title;

		private final int min;

		private final int max;

		private final @StringRes int id_suffix;

		private final @StringRes int id_button_clear;

		private final @StringRes int id_button_3;

		public NumberPickerParams(Bundle arguments) {
			if (arguments == null) {
				arguments = new Bundle();
			}
			max = arguments.getInt(KEY_MAX);
			min = arguments.getInt(KEY_MIN);
			val = arguments.getInt(KEY_VAL);
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

		private @StringRes int id_suffix = -1;

		private Fragment targetFragment;

		private FragmentManager fm;

		private int id_button_clear = -1;

		private int id_button_3 = -1;

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

		private Bundle build() {
			Bundle bundle = new Bundle();
			bundle.putInt(KEY_MIN, min);
			bundle.putInt(KEY_MAX, max);
			bundle.putInt(KEY_VAL, val);
			if (id_title > 0) {
				bundle.putInt(KEY_ID_TITLE, id_title);
			}
			if (id_suffix > 0) {
				bundle.putInt(KEY_ID_SUFFIX, id_suffix);
			}
			if (id_button_clear > 0) {
				bundle.putInt(KEY_ID_BUTTON_CLEAR, id_button_clear);
			}
			if (id_button_3 > 0) {
				bundle.putInt(KEY_ID_BUTTON_3, id_button_3);
			}
			if (callbackID != null) {
				bundle.putString(KEY_CALLBACK_ID, callbackID);
			}
			return bundle;
		}
	}
}
