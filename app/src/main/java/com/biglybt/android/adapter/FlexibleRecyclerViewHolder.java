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

package com.biglybt.android.adapter;

import com.biglybt.android.client.AndroidUtils;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

/**
 * A ViewHolder that reports back clicks and focus changes
 * <p/>
 * <i>Copied from RecyclerView.ViewHolder</i>:<p/>
 * A ViewHolder describes an item view and metadata about its place within the RecyclerView.
 *
 * <p>{@link RecyclerView.Adapter} implementations should subclass ViewHolder and add fields for caching
 * potentially expensive {@link View#findViewById(int)} results.</p>
 *
 * <p>While {@link RecyclerView.LayoutParams} belong to the {@link RecyclerView.LayoutManager},
 * {@link RecyclerView.ViewHolder ViewHolders} belong to the adapter. Adapters should feel free to use
 * their own custom ViewHolder implementations to store data that makes binding view contents
 * easier. Implementations should assume that individual item views will hold strong references
 * to <code>ViewHolder</code> objects and that <code>RecyclerView</code> instances may hold
 * strong references to extra off-screen item views for caching purposes</p>
 */
public class FlexibleRecyclerViewHolder<VH extends RecyclerView.ViewHolder>
	extends RecyclerView.ViewHolder
	implements View.OnClickListener, View.OnLongClickListener,
	View.OnFocusChangeListener, View.OnTouchListener
{
	private static final String TAG = "FlexibleRecyclerViewH";

	@SuppressWarnings("unchecked")
	private final VH thisViewHolder = (VH) this;

	private final RecyclerSelectorInternal<VH> selector;

	private boolean mHasPerformedLongPress;

	public FlexibleRecyclerViewHolder(
			@Nullable RecyclerSelectorInternal<VH> selector, @NonNull View rowView) {
		super(rowView);
		this.selector = selector;
		//rowView.setFocusable(true);
		rowView.setOnClickListener(this);
		rowView.setOnLongClickListener(this);
		rowView.setOnFocusChangeListener(this);
		rowView.setOnTouchListener(this);
	}

	@SuppressLint("LogConditional")
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
		selector.onItemClick(thisViewHolder, v);
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
		return selector.onItemLongClick(thisViewHolder, v);
	}

	@Override
	public void onFocusChange(View v, boolean hasFocus) {
		if (AndroidUtils.DEBUG_ADAPTER && AndroidUtils.DEBUG_ANNOY) {
			log("onFocusChange " + v + ";" + hasFocus);
		}
		if (selector != null) {
			selector.onFocusChange(thisViewHolder, v, hasFocus);
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
		if (!v.post(v::performClick)) {
			v.performClick();
		}

		// We want View.onTouchEvent to still run and handle onFocusChanged, so
		// hopefully returning false is the right thing.
		return false;
	}

	@SuppressWarnings("SameReturnValue")
	protected interface RecyclerSelectorInternal<VH extends RecyclerView.ViewHolder>
	{
		void onItemClick(VH holder, View view);

		boolean onItemLongClick(VH holder, View view);

		void onFocusChange(VH holder, View v, boolean hasFocus);
	}

}
