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

package com.biglybt.android.widget;

import com.biglybt.android.util.TextViewFlipper;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * List {@link android.widget.TextSwitcher}, but flips text with only one TextView
 * Created by TuxPaper on 11/14/16.
 */
public class TextViewFlipperWidget
	extends android.support.v7.widget.AppCompatTextView
{
	private TextViewFlipper flipper;

	private TextViewFlipper.FlipValidator flipValidator;

	private boolean changing = true; // true so first time in, it's set immediately

	public TextViewFlipperWidget(Context context) {
		super(context);
	}

	public TextViewFlipperWidget(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public TextViewFlipperWidget(Context context, AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public void setFlipValidator(TextViewFlipper.FlipValidator flipValidator) {
		this.flipValidator = flipValidator;
	}

	@Override
	public void setText(CharSequence text, BufferType type) {
		if (changing) {
			changing = false;
			super.setText(text, type);
			return;
		}
		changing = true;
		if (flipper == null) {
			flipper = TextViewFlipper.create();
		}

		if (!flipper.changeText(this, text, true, flipValidator)) {
			changing = false;
		}
	}
}