/*
 * *
 *  * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *  *
 *  * This program is free software; you can redistribute it and/or
 *  * modify it under the terms of the GNU General Public License
 *  * as published by the Free Software Foundation; either version 2
 *  * of the License, or (at your option) any later version.
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program; if not, write to the Free Software
 *  * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 *
 */

package com.vuze.android;

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.vuze.android.remote.AndroidUtils;

public class FlexibleRecyclerViewHolder
	extends RecyclerView.ViewHolder
	implements View.OnClickListener, View.OnLongClickListener,
	View.OnFocusChangeListener, View.OnTouchListener
{
	private static final String TAG = "FlexibleRecyclerViewH";

	private final RecyclerSelectorInternal selector;

	private boolean mHasPerformedLongPress;

	public FlexibleRecyclerViewHolder(RecyclerSelectorInternal selector,
			View rowView) {
		super(rowView);
		this.selector = selector;
		rowView.setFocusable(true);
		rowView.setOnClickListener(this);
		rowView.setOnLongClickListener(this);
		rowView.setOnFocusChangeListener(this);
		rowView.setOnTouchListener(this);
	}

	private void log(String s) {
		Log.d(TAG, getClass().getSimpleName() + "] " + s);
	}

	@Override
	public void onClick(View v) {
		if (selector == null) {
			return;
		}
		if (AndroidUtils.DEBUG_ADAPTER) {
			log("onClick " + AndroidUtils.getCompressedStackTrace());
		}
		selector.onItemClick(this, v);
	}

	@Override
	public boolean onLongClick(View v) {
		if (selector == null) {
			return false;
		}
		if (AndroidUtils.DEBUG_ADAPTER) {
			log("onLongClick " + AndroidUtils.getCompressedStackTrace());
		}
		mHasPerformedLongPress = true;
		return selector.onItemLongClick(this, v);
	}

	@Override
	public void onFocusChange(View v, boolean hasFocus) {
		if (AndroidUtils.DEBUG_ADAPTER) {
			log("onFocusChange " + v + ";" + hasFocus);
		}
		if (selector != null) {
			selector.onFocusChange(this, v, hasFocus);
		}
	}

	@Override
	public boolean onTouch(final View v, MotionEvent event) {
		int action = event.getAction();
		if (mHasPerformedLongPress && action == MotionEvent.ACTION_DOWN) {
			mHasPerformedLongPress = false;
			return false;
		}
		if (action != MotionEvent.ACTION_UP) {
			return false;
		}
		if (mHasPerformedLongPress) {
			return false;
		}

		// When focusable in touch mode, tapping the view will cause a focus change
		// and eat the click event.  This puts it back
		if (v.isFocused()) {
			// View.onTouchEvent will handle click
			return false;
		}
		if (!v.isFocusableInTouchMode()) {
			// View.onTouchEvent will handle click
			return false;
		}
		if (!v.isClickable() || !v.isEnabled()) {
			return false;
		}
		if (!v.post(new Runnable() {
			@Override
			public void run() {
				v.performClick();
			}
		})) {
			v.performClick();
		}

		// We want View.onTouchEvent to still run and handle onFocusChanged, so
		// hopefully returning false is the right thing.
		return false;
	}

	protected interface RecyclerSelectorInternal<VH extends RecyclerView.ViewHolder>
	{
		void onItemClick(VH holder, View view);

		boolean onItemLongClick(VH holder, View view);

		void onFocusChange(VH holder, View v, boolean hasFocus);
	}

}
