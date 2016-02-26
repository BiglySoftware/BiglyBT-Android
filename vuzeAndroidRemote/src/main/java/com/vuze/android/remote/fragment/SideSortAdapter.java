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

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.vuze.android.FlexibleRecyclerAdapter;
import com.vuze.android.FlexibleRecyclerSelectionListener;
import com.vuze.android.FlexibleRecyclerViewHolder;
import com.vuze.android.remote.R;

/**
 * Created by TuxPaper on 2/13/16.
 */
public class SideSortAdapter
	extends
	FlexibleRecyclerAdapter<SideSortAdapter.SideSortHolder, SideSortAdapter.SideSortInfo>
{

	private static final String TAG = "SideSortAdapter";

	private final Context context;

	private int currentSortID = -1;

	private boolean currentSortOrderAsc;

	public static final class SideSortInfo
	{
		public String name;

		public long id;

		public SideSortInfo(long id, String sortName) {
			this.id = id;
			name = sortName;
		}
	}

	static final public class SideSortHolder
		extends FlexibleRecyclerViewHolder
	{

		final TextView tvText;

		final ImageView iv;

		public SideSortHolder(RecyclerSelectorInternal selector, View rowView) {
			super(selector, rowView);

			tvText = (TextView) rowView.findViewById(R.id.sidesort_row_text);
			iv = (ImageView) rowView.findViewById(R.id.sidesort_row_image);
		}
	}

	public SideSortAdapter(Context context,
			FlexibleRecyclerSelectionListener selector) {
		super(selector);
		this.context = context;
		setHasStableIds(true);

		String[] sortNames = context.getResources().getStringArray(
				R.array.sortby_list);
		// last on is "reverse".. so ignore it
		List<SideSortInfo> list = new ArrayList<>();
		for (int i = 0; i < sortNames.length - 1; i++) {
			list.add(new SideSortInfo(i, sortNames[i]));
		}
		setItems(list);
	}

	@Override
	public SideSortHolder onCreateFlexibleViewHolder(ViewGroup parent,
			int viewType) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);

		View rowView = inflater.inflate(R.layout.row_sidesort, parent, false);

		SideSortHolder vh = new SideSortHolder(this, rowView);

		return vh;
	}

	@Override
	public void onBindFlexibleViewHolder(SideSortHolder holder, int position) {
		SideSortInfo item = getItem(position);
		holder.tvText.setText(item.name);
		int leftID = currentSortID == item.id
				? currentSortOrderAsc ? R.drawable.ic_arrow_upward_white_24dp
						: R.drawable.ic_arrow_downward_white_24dp
				: 0;
		holder.iv.setScaleType(currentSortOrderAsc ? ImageView.ScaleType.FIT_START
				: ImageView.ScaleType.FIT_END);
		holder.iv.setImageResource(leftID);
	}

	@Override
	public long getItemId(int position) {
		SideSortInfo item = getItem(position);
		return item.id;
	}

	public void setCurrentSort(int id, boolean sortOrderAsc) {
		this.currentSortID = id;
		this.currentSortOrderAsc = sortOrderAsc;
		notifyDataSetInvalidated();
	}

	public int getCurrentSort() {
		return currentSortID;
	}
}
