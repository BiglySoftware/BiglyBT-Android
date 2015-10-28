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

import java.lang.reflect.Field;

import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.internal.widget.DecorToolbar;
import android.support.v7.widget.ListPopupWindow;
import android.util.TypedValue;

public class AndroidUtilsUI
{

	/**
	 * Super Hack to set DropDownWidth of action bar's Navigation list
	 * android:dropDownWidth, android:minWidth, android:layout_width, do not work
	 */
	public static void setABSpinnerDropDownWidth(
			ActionBarActivity activity, int widthDIP) {
		int dip = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, widthDIP,
				activity.getResources().getDisplayMetrics());

		try {
			ActionBar actionBar = activity.getSupportActionBar();
			Field f;
			f = actionBar.getClass().getDeclaredField("mDecorToolbar");
			f.setAccessible(true);
			DecorToolbar decor = (DecorToolbar) f.get(actionBar);
			Field fSpinner = decor.getClass().getDeclaredField("mSpinner");
			fSpinner.setAccessible(true);
			Object oSpinnerCompat = fSpinner.get(decor);

			Field fPopup = oSpinnerCompat.getClass().getDeclaredField("mPopup");
			fPopup.setAccessible(true);
			Object o = fPopup.get(oSpinnerCompat);

			Field fWidth = ListPopupWindow.class.getDeclaredField("mDropDownWidth");
			//dumpVars(ListPopupWindow.class, o);
			fWidth.setAccessible(true);
			fWidth.set(o, dip);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

}
