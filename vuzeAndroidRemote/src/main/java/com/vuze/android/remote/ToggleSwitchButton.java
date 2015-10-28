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
 */

package com.vuze.android.remote;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.ToggleButton;

/**
 * Very simple and limited implementation of a Toggle button as a switch.
 * <p>
 * Moves the text left or right depending upon OFF/ON state.  Use padding!
 * <p>
 * Assumes:<BR>
 * - 9-Patch background for ON and OFF states
 * - No fancy measurements like max width
 */
public class ToggleSwitchButton
	extends ToggleButton
{
	float x1, x2;


	public ToggleSwitchButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		syncTextAlign();
	}

	public ToggleSwitchButton(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public ToggleSwitchButton(Context context) {
		super(context);
	}

	@Override
	public void setChecked(boolean checked) {
		super.setChecked(checked);

		syncTextAlign();
	}

	private void syncTextAlign() {
		boolean checked = isChecked();

		setGravity(Gravity.CENTER_VERTICAL
				| (checked ? Gravity.RIGHT : Gravity.LEFT));
	}

	/* (non-Javadoc)
	 * @see android.widget.TextView#onMeasure(int, int)
	 */
	@SuppressLint("DrawAllocation")
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		int width = getMeasuredWidth();
		if (widthMeasureSpec != MeasureSpec.EXACTLY) {
			Rect bounds = new Rect();
			Paint textPaint = getPaint();
			String textON = getTextOn().toString();
			textPaint.getTextBounds(textON, 0, textON.length(), bounds);
			int widthOn = bounds.width();
			String textOFF = getTextOff().toString();
			textPaint.getTextBounds(textOFF, 0, textOFF.length(), bounds);
			int widthOff = bounds.width();
			
			int biggest = Math.max(widthOn, widthOff);
			width = (biggest * 2) + getPaddingLeft() + getPaddingRight();
      width += getPaddingLeft() + getPaddingRight();

		}
		setMeasuredDimension(width, getMeasuredHeight());
	}

	public boolean onTouchEvent(MotionEvent touchevent) {
		if (super.onTouchEvent(touchevent)) {
			return true;
		}
		switch (touchevent.getAction()) {
		// when user first touches the screen we get x and y coordinate
			case MotionEvent.ACTION_DOWN: {
				x1 = touchevent.getX();
				break;
			}
			case MotionEvent.ACTION_UP: {
				x2 = touchevent.getX();
				
				//Log.e("action_up " + x1 + "/" + x2);

				if (x1 < x2) {
					// left to right -- turn on
					setChecked(true);
					return true;
				}

				if (x1 > x2) {
					// right to left
					setChecked(false);
					return true;
				}

				break;
			}
		}
		return false;
	}
}
