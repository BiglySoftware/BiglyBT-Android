
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.view.View;

/**
 * From https://androiddevx.wordpress.com/2014/12/05/recycler-view-pre-cache-views/
 * or https://github.com/ovy9086/recyclerview-playground
 *
 * Created by ovidiu.latcu on 12/4/2014.
 */
public class PreCachingLayoutManager
	extends LinearLayoutManager
{
	private int DEFAULT_EXTRA_LAYOUT_SPACE = 600;

	private int extraLayoutSpace = -1;

	private final Context context;

	private int fixedVerticalHeight;

	public PreCachingLayoutManager(Context context) {
		super(context);
		this.context = context;
		setDefaultExtraLayoutSpace(context, getOrientation());
	}

	public PreCachingLayoutManager(Context context, int extraLayoutSpace) {
		super(context);
		this.context = context;
		this.extraLayoutSpace = extraLayoutSpace;
		setDefaultExtraLayoutSpace(context, getOrientation());
	}

	public PreCachingLayoutManager(Context context, int orientation,
			boolean reverseLayout) {
		super(context, orientation, reverseLayout);
		this.context = context;
		setDefaultExtraLayoutSpace(context, orientation);
	}

	public void setExtraLayoutSpace(int extraLayoutSpace) {
		this.extraLayoutSpace = extraLayoutSpace;
	}

	private void setDefaultExtraLayoutSpace(Context context, int orientation) {
		if (context == null) {
			// initializing, probably being called by setOrientation.  Will be called again
			return;
		}
		DisplayMetrics dm = context.getResources().getDisplayMetrics();
		DEFAULT_EXTRA_LAYOUT_SPACE = orientation == LinearLayoutManager.HORIZONTAL
				? dm.widthPixels : dm.heightPixels;
	}

	@Override
	public void setOrientation(int orientation) {
		setDefaultExtraLayoutSpace(context, orientation);
		super.setOrientation(orientation);
	}

	@Override
	protected int getExtraLayoutSpace(RecyclerView.State state) {
		if (extraLayoutSpace > 0) {
			return extraLayoutSpace;
		}
		return DEFAULT_EXTRA_LAYOUT_SPACE;
	}

	// FROM http://stackoverflow.com/a/33985508
	/**
	 * Disable predictive animations. There is a bug in RecyclerView which causes views that
	 * are being reloaded to pull invalid ViewHolders from the internal recycler stack if the
	 * adapter size has decreased since the ViewHolder was recycled.
	 */
	@Override
	public boolean supportsPredictiveItemAnimations() {
		return false;
	}

	@Override
	public boolean requestChildRectangleOnScreen(RecyclerView parent, View child,
			Rect rect, boolean immediate, boolean focusedChildVisible) {
		if (fixedVerticalHeight > 0) {
			rect.top -= fixedVerticalHeight;
			rect.bottom += fixedVerticalHeight;
		}
		return super.requestChildRectangleOnScreen(parent, child, rect, immediate,
				focusedChildVisible);
	}

	/**
	 * When scrolling up or down, show fixedVerticalHeight pixels of row
	 * above or below.
	 */
	public void setFixedVerticalHeight(int fixedVerticalHeight) {
		this.fixedVerticalHeight = fixedVerticalHeight;
	}

	public int getFixedVerticalHeight() {
		return fixedVerticalHeight;
	}

}