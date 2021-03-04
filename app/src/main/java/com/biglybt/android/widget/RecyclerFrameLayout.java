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
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.biglybt.android.adapter.FlexibleRecyclerView;
import com.biglybt.android.client.AndroidUtils;

/**
 * Hack fix for left/right keeping focus
 */
public class RecyclerFrameLayout
	extends FrameLayout
{
	public RecyclerFrameLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Nullable
	@Override
	public View focusSearch(int direction) {
		return FlexibleRecyclerView.handleVH_focusSearch(this,
				super.focusSearch(direction), direction);
	}

	@Override
	public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
		// When this layout is in a vertical list, gaining focus from the
		// left or the right is usually wrong.  ie. Android tries to guess
		// the row to the left/right of the currently focused view, and doesn't
		// send a requestFocus to the parent first (not even with
		// FOCUS_BEFORE_DESCENDANTS or parent)
		// Hack fix this by forcing parent to get focus
		if (AndroidUtils.DEBUG) {
			//Log.d("FSREC", "reqestFocus from " + direction);
		}
		if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
			return ((View) getParent()).requestFocus();
		}
		return super.requestFocus(direction, previouslyFocusedRect);
	}
}