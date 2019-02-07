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

package com.biglybt.android.client.sidelist;

import com.biglybt.android.adapter.*;
import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.R;

import androidx.lifecycle.Lifecycle;
import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
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
public class SideTextFilterAdapter
	extends
	FlexibleRecyclerAdapter<SideTextFilterAdapter, SideTextFilterAdapter.SideFilterViewHolder, SideTextFilterAdapter.SideTextFilterInfo>
{

	private static final String TAG = "SideFilterAdapter";

	public static final class SideTextFilterInfo
		implements Comparable<SideTextFilterInfo>
	{
		public final String letters;

		public final int count;

		public SideTextFilterInfo(String letters, int count) {
			this.letters = letters;
			this.count = count;
		}

		@Override
		public String toString() {
			return super.toString() + ";" + letters + ";" + count;
		}

		@Override
		public int compareTo(@NonNull SideTextFilterInfo another) {
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

			tvText = rowView.findViewById(R.id.sidefilter_row_text);
			tvCount = rowView.findViewById(R.id.sidefilter_row_count);
		}
	}

	private int viewType;

	public SideTextFilterAdapter(Lifecycle lifecycle,
			FlexibleRecyclerSelectionListener selector) {
		super(TAG, lifecycle, selector);
		setHasStableIds(true);
	}

	@Override
	public SideFilterViewHolder onCreateFlexibleViewHolder(ViewGroup parent,
			int viewType) {

		Context context = parent.getContext();
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
		Context context = holder.tvText.getContext();
		SideTextFilterInfo item = getItem(position);
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
		SideTextFilterInfo item = getItem(position);
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
