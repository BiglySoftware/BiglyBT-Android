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

package com.vuze.android.remote.adapter;

import com.vuze.android.FlexibleRecyclerAdapter;
import com.vuze.android.FlexibleRecyclerSelectionListener;
import com.vuze.android.FlexibleRecyclerViewHolder;
import com.vuze.android.remote.AndroidUtilsUI;
import com.vuze.android.remote.FilterConstants;
import com.vuze.android.remote.R;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Created by TuxPaper on 2/9/16.
 */
public class SideFilterAdapter
	extends
	FlexibleRecyclerAdapter<SideFilterAdapter.SideFilterViewHolder, SideFilterAdapter.SideFilterInfo>
{

	private final Context context;

	public static final class SideFilterInfo
		implements Comparable<SideFilterInfo>
	{
		public final String letters;

		final int count;

		public SideFilterInfo(String letters, int count) {
			this.letters = letters;
			this.count = count;
		}

		@Override
		public String toString() {
			return super.toString() + ";" + letters + ";" + count;
		}

		@Override
		public int compareTo(@NonNull SideFilterInfo another) {
			return letters.compareTo(another.letters);
		}
	}

	static final public class SideFilterViewHolder
		extends FlexibleRecyclerViewHolder
	{

		final TextView tvText;

		final TextView tvCount;

		public SideFilterViewHolder(RecyclerSelectorInternal selector,
				View rowView) {
			super(selector, rowView);

			tvText = (TextView) rowView.findViewById(R.id.sidefilter_row_text);
			tvCount = (TextView) rowView.findViewById(R.id.sidefilter_row_count);
		}
	}

	private int viewType;

	public SideFilterAdapter(Context context,
			FlexibleRecyclerSelectionListener selector) {
		super(selector);
		this.context = context;
		setHasStableIds(true);
	}

	@Override
	public SideFilterViewHolder onCreateFlexibleViewHolder(ViewGroup parent,
			int viewType) {

		LayoutInflater inflater = (LayoutInflater) context.getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);

		boolean isSmall = viewType == 1;
		View rowView = inflater.inflate(isSmall ? R.layout.row_sidetextfilter_small
				: R.layout.row_sidetextfilter, parent, false);

		return new SideFilterViewHolder(this, rowView);
	}

	@Override
	public void onBindFlexibleViewHolder(SideFilterViewHolder holder,
			int position) {
		SideFilterInfo item = getItem(position);
		if (item.letters.equals(FilterConstants.LETTERS_BS)) {
			Drawable drawableCompat = AndroidUtilsUI.getDrawableWithBounds(context,
					R.drawable.ic_backspace_white_24dp);
			ImageSpan imageSpan = new ImageSpan(drawableCompat,
					DynamicDrawableSpan.ALIGN_BOTTOM);
			SpannableStringBuilder ss = new SpannableStringBuilder(",");
			ss.setSpan(imageSpan, 0, 1, 0);
			holder.tvText.setText(ss);
		} else {
			holder.tvText.setText(item.letters);

			int resID = item.letters.length() > 1
					? android.R.style.TextAppearance_Small
					: android.R.style.TextAppearance_Large;
			holder.tvText.setTextAppearance(context, resID);
			holder.tvText.setTextColor(
					ContextCompat.getColor(context, R.color.login_text_color));
		}
		if (holder.tvCount != null) {
			holder.tvCount.setText(item.count > 0 ? String.valueOf(item.count) : "");
		}
	}

	@Override
	public long getItemId(int position) {
		SideFilterInfo item = getItem(position);
		if (item == null) {
			return RecyclerView.NO_ID;
		}
		return item.letters.hashCode();
	}

	public void setViewType(int viewType) {
		if (viewType == this.viewType) {
			return;
		}
		this.viewType = viewType;
		if (getItemCount() > 0) {
			notifyDataSetInvalidated();
		}
	}

	@Override
	public int getItemViewType(int position) {
		return viewType;
	}
}
