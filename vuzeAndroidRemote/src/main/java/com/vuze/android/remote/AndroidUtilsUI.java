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

import android.app.Activity;
import android.content.Context;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.*;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.*;

import com.vuze.android.remote.activity.DrawerActivity;

import java.util.ArrayList;

public class AndroidUtilsUI
{
	public static ArrayList findByClass(ViewGroup root, Class type,
			ArrayList list) {
		final int childCount = root.getChildCount();

		for (int i = 0; i < childCount; ++i) {
			final View child = root.getChildAt(i);
			if (type.isInstance(child)) {
				list.add(child);
			}

			if (child instanceof ViewGroup) {
				findByClass((ViewGroup) child, type, list);
			}
		}
		return list;
	}

	public static boolean handleCommonKeyDownEvents(Activity a, int keyCode,
			KeyEvent event) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_MEDIA_NEXT:
			case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: {
				ViewGroup vg = (ViewGroup) a.findViewById(android.R.id.content);
				ArrayList list = AndroidUtilsUI.findByClass(vg, ViewPager.class,
						new ArrayList(0));

				if (list.size() > 0) {
					ViewPager viewPager = (ViewPager) list.get(0);
					viewPager.arrowScroll(View.FOCUS_RIGHT);
				}
				break;
			}

			case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
			case KeyEvent.KEYCODE_MEDIA_REWIND: {
				ViewGroup vg = (ViewGroup) a.findViewById(android.R.id.content);
				ArrayList list = AndroidUtilsUI.findByClass(vg, ViewPager.class,
						new ArrayList(0));

				if (list.size() > 0) {
					ViewPager viewPager = (ViewPager) list.get(0);
					viewPager.arrowScroll(View.FOCUS_LEFT);
				}
				break;
			}

			case KeyEvent.KEYCODE_DPAD_LEFT: {
				if (a instanceof DrawerActivity) {
					DrawerActivity da = (DrawerActivity) a;
					DrawerLayout drawerLayout = da.getDrawerLayout();
					View viewFocus = a.getCurrentFocus();
					boolean canOpenDrawer = viewFocus != null
							&& "leftmost".equals(viewFocus.getTag());
					if (canOpenDrawer) {
						drawerLayout.openDrawer(Gravity.LEFT);
						drawerLayout.requestFocus();
						return true;
					}
				}
				break;
			}

		}

		return false;
	}
}
