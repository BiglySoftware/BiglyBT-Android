/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.android.widget;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.Scroller;

import androidx.annotation.RequiresApi;

import com.biglybt.android.client.AndroidUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Number Picker with DPAD_CENTER switchinig to editable, and holding down
 * DPAD_UP or DPAD_DOWN makes it go faster.
 * 
 * Created by TuxPaper on 9/27/17.
 */

public class NumberPickerLB
	extends NumberPicker
{
	private Class<?> cla;

	private Method meth_removeAllCallbacks;

	private Field fld_mFlingScroller;

	private Method meth_ensureScrollWheelAdjusted;

	private Field fld_mInputText;

	private Boolean mHasSelectorWheel;

	private OnValueChangeListener onValueChangedListener;

	public NumberPickerLB(Context context) {
		super(context);
		initReflection();
	}

	public NumberPickerLB(Context context, AttributeSet attrs) {
		super(context, attrs);
		initReflection();
	}

	public NumberPickerLB(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		initReflection();
	}

	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	public NumberPickerLB(Context context, AttributeSet attrs, int defStyleAttr,
			int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		initReflection();
	}

	@Override
	public void setOnValueChangedListener(
			OnValueChangeListener onValueChangedListener) {
		this.onValueChangedListener = onValueChangedListener;
		super.setOnValueChangedListener(onValueChangedListener);
	}

	private void initReflection() {
		mHasSelectorWheel = !willNotDraw();

		try {
			cla = getClass().getSuperclass();

			meth_removeAllCallbacks = cla.getDeclaredMethod("removeAllCallbacks");
			meth_removeAllCallbacks.setAccessible(true);
		} catch (Throwable ignore) {
		}

		try {
			fld_mFlingScroller = cla.getDeclaredField("mFlingScroller");
			fld_mFlingScroller.setAccessible(true);

		} catch (Throwable ignore) {
			if (AndroidUtils.DEBUG) {
				Log.e("NumberPickerLB", "initReflection", ignore);
			}
		}

		try {
			meth_ensureScrollWheelAdjusted = cla.getDeclaredMethod(
					"ensureScrollWheelAdjusted");
			meth_ensureScrollWheelAdjusted.setAccessible(true);
		} catch (Throwable ignore) {
		}

		try {
			fld_mInputText = cla.getDeclaredField("mInputText");
			fld_mInputText.setAccessible(true);
			if (AndroidUtils.DEBUG) {
				Log.d("NumberPickerLB", "Reflection2 Success");
			}
		} catch (Throwable ignore) {
			Log.e("NumberPickerLB", "initReflection", ignore);
		}

	}

	public EditText getEditText() {
		try {
			return (EditText) fld_mInputText.get(this);
		} catch (Throwable e) {
			return null;
		}
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		try {
			final int keyCode = event.getKeyCode();
			switch (keyCode) {
				case KeyEvent.KEYCODE_DPAD_CENTER:
				case KeyEvent.KEYCODE_ENTER:
					//removeAllCallbacks();
					super.dispatchKeyEvent(event); // calls removeAllCallbacks
					showSoftInput();
					if (fld_mInputText != null) {
						EditText et = (EditText) fld_mInputText.get(this);
						et.selectAll();
					}
					return true;
				case KeyEvent.KEYCODE_DPAD_DOWN:
				case KeyEvent.KEYCODE_DPAD_UP:
					if (mHasSelectorWheel == null || !mHasSelectorWheel) {
						break;
					}
					switch (event.getAction()) {
						case KeyEvent.ACTION_DOWN:
							if (getWrapSelectorWheel()
									|| ((keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
											? getValue() < getMaxValue()
											: getValue() > getMinValue())) {
								requestFocus();
								//mLastHandledDownDpadKeyCode = keyCode;
								//removeAllCallbacks();
								if (meth_removeAllCallbacks != null) {
									meth_removeAllCallbacks.invoke(this);
								}
								Scroller mFlingScroller = (Scroller) fld_mFlingScroller.get(
										this);
								if (mFlingScroller.isFinished()) {
									// go faster based on holding down.
									// Can't use repeat count, since value isn't
									// always increased on each repeat due to scroller
									long repeatCount = (event.getEventTime()
											- event.getDownTime()) / 300; //SNAP_SCROLL_DURATION;
									if (repeatCount >= 5) {
										//int step = (int) Math.min((repeatCount / 5) + 1, (mMaxValue - mMinValue) / 10);
										int step = (int) Math.min((repeatCount / 5) + 1,
												(getMaxValue() - getMinValue()) / 10);
										int old = getValue();
										int v = old + (keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? step
												: -step);
										//setValueInternal(v, true);
										setValue(v);
										if (onValueChangedListener != null) {
											onValueChangedListener.onValueChange(this, old, v);
										}

										//ensureScrollWheelAdjusted();
										if (meth_ensureScrollWheelAdjusted != null) {
											meth_ensureScrollWheelAdjusted.invoke(this);
										}
									} else {
										//changeValueByOne(keyCode == KeyEvent.KEYCODE_DPAD_DOWN);
										return super.dispatchKeyEvent(event);
									}
								}
								return true;
							}
							break;
					}
			}
		} catch (Throwable ignore) {
		}
		return super.dispatchKeyEvent(event);
	}

	/**
	 * Shows the soft input for its input text.
	 */
	private void showSoftInput() {
		if (fld_mInputText == null) {
			return;
		}
		InputMethodManager inputMethodManager = (InputMethodManager) getContext().getSystemService(
				Context.INPUT_METHOD_SERVICE);
		if (inputMethodManager != null) {
			EditText editText = getEditText();
			if (mHasSelectorWheel) {
				editText.setVisibility(View.VISIBLE);
			}
			editText.requestFocus();
			inputMethodManager.showSoftInput(editText, 0);
		}
	}
}
