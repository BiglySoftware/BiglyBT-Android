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

package com.vuze.android.remote.fragment;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.vuze.android.FlexibleRecyclerAdapter;
import com.vuze.android.FlexibleRecyclerSelectionListener;
import com.vuze.android.FlexibleRecyclerViewHolder;
import com.vuze.android.remote.R;

/**
 * Created by TuxPaper on 2/9/16.
 */
public class SideFilterAdapter
	extends
	FlexibleRecyclerAdapter<SideFilterAdapter.SideFilterViewHolder, SideFilterAdapter.SideFilterInfo>
{

	private final Context context;

	public static final class SideFilterInfo
	{
		String letters;

		int count;

		public SideFilterInfo(String letters, int count) {
			this.letters = letters;
			this.count = count;
		}

		@Override
		public String toString() {
			return super.toString() + ";" + letters + ";" + count;
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

		View rowView = inflater.inflate(R.layout.row_sidefilter, parent, false);

		SideFilterViewHolder vh = new SideFilterViewHolder(this, rowView);

		return vh;
	}

	@Override
	public void onBindFlexibleViewHolder(SideFilterViewHolder holder,
			int position) {
		SideFilterInfo item = getItem(position);
		if (item.letters.equals(TorrentListFragment.LETTERS_BS)) {
			ImageSpan imageSpan = new ImageSpan(context,
					R.drawable.ic_backspace_white_24dp, DynamicDrawableSpan.ALIGN_BOTTOM);
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
		holder.tvCount.setText(item.count > 0 ? String.valueOf(item.count) : "");
	}

	@Override
	public long getItemId(int position) {
		SideFilterInfo item = getItem(position);
		if (item == null) {
			return RecyclerView.NO_ID;
		}
		return item.letters.hashCode();
	}
}
