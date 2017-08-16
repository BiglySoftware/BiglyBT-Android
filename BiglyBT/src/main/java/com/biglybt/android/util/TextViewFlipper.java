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

package com.biglybt.android.util;

import com.biglybt.util.Thunk;

import android.os.Build;
import android.text.SpannableString;
import android.widget.TextView;

/**
 * Flips text within a single TextView
 */
public abstract class TextViewFlipper
{
	@Thunk
	static final boolean DEBUG_FLIPPER = false;

	public interface FlipValidator
	{
		boolean isStillValid();
	}

	public abstract boolean changeText(TextView tv, CharSequence newText,
			boolean animate, FlipValidator validator);

	public abstract void changeText(TextView tv, SpannableString newText,
			boolean animate, FlipValidator validator);

	public static TextViewFlipper create() {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB
				? new TextViewFlipperV11() : new TextViewFlipperV7();
	}
}
