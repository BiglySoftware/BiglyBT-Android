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

package com.biglybt.android.client.adapter;

import com.biglybt.android.*;
import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.R;

import android.arch.lifecycle.Lifecycle;
import android.content.Context;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class SideSortAdapter
	extends
	FlexibleRecyclerAdapter<SideSortAdapter.SideSortHolder, SideSortAdapter.SideSortInfo>
{

	private static final String TAG = "SideSortAdapter";

	private SortDefinition currentSort = null;

	private boolean currentSortAsc;

	private int paddingLeft = 0;

	public static final class SideSortInfo
		implements Comparable<SideSortInfo>
	{
		public final String name;

		public final long id;

		public final @DrawableRes int resAscending;

		public final @DrawableRes int resDescending;

		public SideSortInfo(long id, String sortName, @DrawableRes int resAscending,
				@DrawableRes int resDescending) {
			this.id = id;
			name = sortName;
			this.resAscending = resAscending;
			this.resDescending = resDescending;
		}

		@Override
		public int compareTo(@NonNull SideSortInfo another) {
			return AndroidUtils.longCompare(id, another.id);
		}
	}

	static final public class SideSortHolder
		extends FlexibleRecyclerViewHolder
	{

		final TextView tvText;

		final ImageView iv;

		public SideSortHolder(RecyclerSelectorInternal selector, View rowView) {
			super(selector, rowView);

			tvText = rowView.findViewById(R.id.sidesort_row_text);
			iv = rowView.findViewById(R.id.sidesort_row_image);
		}
	}

	private int viewType;

	public SideSortAdapter(Lifecycle lifecycle,
			FlexibleRecyclerSelectionListener selector) {
		super(lifecycle, selector);
		setHasStableIds(true);
	}

	@Override
	public SideSortHolder onCreateFlexibleViewHolder(ViewGroup parent,
			int viewType) {
		final Context context = parent.getContext();
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);

		boolean isSmall = viewType == 1;
		View rowView = inflater.inflate(
				isSmall ? R.layout.row_sidesort_small : R.layout.row_sidesort, parent,
				false);

		return new SideSortHolder(this, rowView);
	}

	@Override
	public void onBindFlexibleViewHolder(SideSortHolder holder, int position) {
		SideSortInfo item = getItem(position);
		holder.tvText.setText(item.name);

		int sortImageID;
		String contentDescription;
		if (currentSort != null && currentSort.id == item.id) {
			if (currentSortAsc) {
				sortImageID = item.resAscending;
				contentDescription = holder.iv.getResources().getString(
						R.string.spoken_sorted_ascending);
			} else {
				sortImageID = item.resDescending;
				contentDescription = holder.iv.getResources().getString(
						R.string.spoken_sorted_descending);
			}
			holder.iv.setScaleType(currentSortAsc ? ImageView.ScaleType.FIT_START
					: ImageView.ScaleType.FIT_END);
		} else {
			sortImageID = 0;
			contentDescription = null;
		}
		holder.iv.setImageResource(sortImageID);
		holder.iv.setContentDescription(contentDescription);
		holder.tvText.setPadding(paddingLeft, 0, holder.tvText.getPaddingRight(),
				0);
	}

	@Override
	public long getItemId(int position) {
		SideSortInfo item = getItem(position);
		return item.id;
	}

	public void setCurrentSort(SortDefinition sortDefinition, boolean isAsc) {
		RecyclerView rv = getRecyclerView();
		boolean idChanged = currentSort == null
				|| currentSort.id != sortDefinition.id;

		if (idChanged && currentSort != null) {
			RecyclerView.ViewHolder oldVH = rv.findViewHolderForItemId(
					currentSort.id);
			if (oldVH != null) {
				int position = oldVH.getAdapterPosition();
				if (position >= 0) {
					notifyItemChanged(position);
				}
			}
		}
		this.currentSort = sortDefinition;
		this.currentSortAsc = isAsc;

		RecyclerView.ViewHolder newVH = rv.findViewHolderForItemId(currentSort.id);
		if (newVH != null) {
			int position = newVH.getAdapterPosition();
			if (position >= 0) {
				notifyItemChanged(position);
			}
		}
	}

	public void setPaddingLeft(int paddingLeft) {
		this.paddingLeft = paddingLeft;
		notifyDataSetInvalidated();
	}

	public SortDefinition getCurrentSort() {
		return currentSort;
	}

	public void setViewType(int viewType) {
		if (viewType == this.viewType) {
			return;
		}
		this.viewType = viewType;
		notifyDataSetInvalidated();
	}

	@Override
	public int getItemViewType(int position) {
		return viewType;
	}

}
