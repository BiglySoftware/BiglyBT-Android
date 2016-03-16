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

package com.vuze.android.widget;

import android.content.Context;
import android.support.v7.widget.ActionMenuView;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * Simple Toolbar extension that fixes the width of ActionMenuView objects
 */
public class SplitToolbar
	extends Toolbar
{
	public SplitToolbar(Context context) {
		super(context);
		setContentInsetsAbsolute(0, 0);

	}

	public SplitToolbar(Context context, AttributeSet attrs) {
		super(context, attrs);
		setContentInsetsAbsolute(0,0);
	}

	public SplitToolbar(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		setContentInsetsAbsolute(0,0);
	}

	@Override
	public void addView(View child, ViewGroup.LayoutParams params) {
		if (child instanceof ActionMenuView) {
			params.width = LayoutParams.MATCH_PARENT;
		}
		super.addView(child, params);
	}
}