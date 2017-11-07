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

import android.content.Context;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.util.AttributeSet;
import android.view.View;

@SuppressWarnings("unused")
public class DisableableAppBarLayoutBehavior
	extends AppBarLayout.Behavior
{
	private boolean mEnabled = true;

	public DisableableAppBarLayoutBehavior() {
		super();
	}

	public DisableableAppBarLayoutBehavior(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public void setEnabled(boolean enabled) {
		mEnabled = enabled;
	}

	@Override
	public boolean onStartNestedScroll(CoordinatorLayout parent,
			AppBarLayout child, View directTargetChild, View target,
			int nestedScrollAxes, int type) {
		return mEnabled && super.onStartNestedScroll(parent, child,
				directTargetChild, target, nestedScrollAxes, type);
	}

	public boolean isEnabled() {
		return mEnabled;
	}
}