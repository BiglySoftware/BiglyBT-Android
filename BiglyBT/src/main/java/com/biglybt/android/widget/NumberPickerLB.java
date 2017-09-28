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
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.Scroller;

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

	private Method meth_showSoftInput;

	private Field fld_mHasSelectorWheel;

	private Field fld_mWrapSelectorWheel;

	private Field fld_mLastHandledDownDpadKeyCode;

	private Method meth_removeAllCallbacks;

	private Field fld_mFlingScroller;

	private Method meth_setValueInternal;

	private Method meth_ensureScrollWheelAdjusted;

	private Method meth_changeValueByOne;

	private Field fld_mInputText;

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

	private void initReflection() {
		try {
			cla = getClass().getSuperclass();
			meth_showSoftInput = cla.getDeclaredMethod("showSoftInput");
			meth_showSoftInput.setAccessible(true);

			fld_mHasSelectorWheel = cla.getDeclaredField("mHasSelectorWheel");
			fld_mHasSelectorWheel.setAccessible(true);

			fld_mWrapSelectorWheel = cla.getDeclaredField("mWrapSelectorWheel");
			fld_mWrapSelectorWheel.setAccessible(true);

			fld_mLastHandledDownDpadKeyCode
					= cla.getDeclaredField("mLastHandledDownDpadKeyCode");
			fld_mLastHandledDownDpadKeyCode.setAccessible(true);

			meth_removeAllCallbacks = cla.getDeclaredMethod("removeAllCallbacks");
			meth_removeAllCallbacks.setAccessible(true);

			fld_mFlingScroller = cla.getDeclaredField("mFlingScroller");
			fld_mFlingScroller.setAccessible(true);

			meth_setValueInternal = cla.getDeclaredMethod("setValueInternal", int.class, boolean.class);
			meth_setValueInternal.setAccessible(true);

			meth_ensureScrollWheelAdjusted
					= cla.getDeclaredMethod("ensureScrollWheelAdjusted");
			meth_ensureScrollWheelAdjusted.setAccessible(true);

			meth_changeValueByOne
					= cla.getDeclaredMethod("changeValueByOne", boolean.class);
			meth_changeValueByOne.setAccessible(true);
			
			fld_mInputText = cla.getDeclaredField("mInputText");
			fld_mInputText.setAccessible(true);
			
			
			
		} catch (Throwable ignore) {
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
				//showSoftInput();
				meth_removeAllCallbacks.invoke(this);
				meth_showSoftInput.invoke(this);
				EditText et = (EditText) fld_mInputText.get(this);
				et.selectAll();
				return true;
			case KeyEvent.KEYCODE_DPAD_DOWN:
			case KeyEvent.KEYCODE_DPAD_UP:
				boolean mHasSelectorWheel = fld_mHasSelectorWheel.getBoolean(this);
				if (!mHasSelectorWheel) {
					break;
				}
				switch (event.getAction()) {
					case KeyEvent.ACTION_DOWN:
						boolean mWrapSelectorWheel = fld_mWrapSelectorWheel.getBoolean(this);
						if (mWrapSelectorWheel || ((keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
								? getValue() < getMaxValue() : getValue() > getMinValue())) {
							requestFocus();
							//mLastHandledDownDpadKeyCode = keyCode;
							fld_mLastHandledDownDpadKeyCode.setInt(this, keyCode);
							//removeAllCallbacks();
							meth_removeAllCallbacks.invoke(this);
							Scroller mFlingScroller = (Scroller) fld_mFlingScroller.get(this);
							if (mFlingScroller.isFinished()) {
								// go faster based on holding down.
								// Can't use repeat count, since value isn't
								// always increased on each repeat due to scroller
								long repeatCount = (event.getEventTime() - event.getDownTime()) / 300; //SNAP_SCROLL_DURATION;
								if (repeatCount >= 5) {
									//int step = (int) Math.min((repeatCount / 5) + 1, (mMaxValue - mMinValue) / 10);
									int step = (int) Math.min((repeatCount / 5) + 1, (getMaxValue() - getMinValue()) / 10);
									int v = getValue() + (keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? step : -step);
									//setValueInternal(v, true);
									meth_setValueInternal.invoke(this, v, true);

									//ensureScrollWheelAdjusted();
									meth_ensureScrollWheelAdjusted.invoke(this);
								}
								//changeValueByOne(keyCode == KeyEvent.KEYCODE_DPAD_DOWN);
								meth_changeValueByOne.invoke(this, keyCode == KeyEvent.KEYCODE_DPAD_DOWN);
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
}
