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

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.biglybt.android.adapter.*;
import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.R;

import java.util.List;

/**
 * FlexibleRecyclerAdapter for the Sort entries in the SideList
 */
public class SideSortAdapter
	extends
	FlexibleRecyclerAdapter<SideSortAdapter, SideSortAdapter.SideSortHolder, SideSortAdapter.SideSortInfo>
{

	private static final String TAG = "SideSortAdapter";

	private SortDefinition currentSort = null;

	private boolean currentSortAsc;

	private int paddingLeft = 0;

	public static final class SideSortInfo
	{
		public final String name;

		public final long id;

		public final @DrawableRes int resAscending;

		public final @DrawableRes int resDescending;

		public final long sortDefId;

		public SideSortInfo(long id, long sortDefId, String sortName,
				@DrawableRes int resAscending, @DrawableRes int resDescending) {
			this.id = id;
			this.sortDefId = sortDefId;
			name = sortName;
			this.resAscending = resAscending;
			this.resDescending = resDescending;
		}

		@Override
		public boolean equals(@Nullable Object obj) {
			return (obj instanceof SideSortInfo) && id == ((SideSortInfo) obj).id;
		}
	}

	static final public class SideSortHolder
		extends FlexibleRecyclerViewHolder
	{

		@NonNull
		final TextView tvText;

		@NonNull
		final ImageView iv;

		public SideSortHolder(RecyclerSelectorInternal selector,
				@NonNull View rowView) {
			super(selector, rowView);

			tvText = ViewCompat.requireViewById(rowView, R.id.sidesort_row_text);
			iv = ViewCompat.requireViewById(rowView, R.id.sidesort_row_image);
		}
	}

	private int viewType;

	public SideSortAdapter(
			FlexibleRecyclerSelectionListener<SideSortAdapter, SideSortAdapter.SideSortHolder, SideSortAdapter.SideSortInfo> selector) {
		super(TAG, selector);
		setHasStableIds(true);
	}

	@NonNull
	@Override
	public SideSortHolder onCreateFlexibleViewHolder(@NonNull ViewGroup parent,
			@NonNull LayoutInflater inflater, int viewType) {

		boolean isSmall = viewType == 1;
		View rowView = AndroidUtilsUI.requireInflate(inflater,
				isSmall ? R.layout.row_sidesort_small : R.layout.row_sidesort, parent,
				false);

		return new SideSortHolder(this, rowView);
	}

	@Override
	public void onBindFlexibleViewHolder(@NonNull SideSortHolder holder,
			int position) {
		SideSortInfo item = getItem(position);
		if (item == null) {
			return;
		}
		holder.tvText.setText(item.name);

		int sortImageID;
		String contentDescription;
		if (currentSort != null && currentSort.id == item.id) {
			Resources resources = AndroidUtils.requireResources(holder.itemView);
			if (currentSortAsc) {
				sortImageID = item.resAscending;
				contentDescription = resources.getString(
						R.string.spoken_sorted_ascending);
			} else {
				sortImageID = item.resDescending;
				contentDescription = resources.getString(
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
		return item == null ? -1 : item.sortDefId;
	}

	public void setCurrentSort(SortDefinition sortDefinition, boolean isAsc) {
		RecyclerView rv = getRecyclerView();
		boolean idChanged = currentSort == null
				|| currentSort.id != sortDefinition.id;

		List<SideSortInfo> allItems = getAllItems();
		if (idChanged && currentSort != null) {
			for (int i = 0; i < allItems.size(); i++) {
				SideSortInfo sideSortInfo = allItems.get(i);
				if (sideSortInfo.sortDefId == currentSort.id) {
					notifyItemChanged(i);
					break;
				}
			}
		}
		this.currentSort = sortDefinition;
		this.currentSortAsc = isAsc;

		if (currentSort == null) {
			return;
		}

		if (rv == null) {
			// rv has been recycled
			return;
		}

		for (int i = 0; i < allItems.size(); i++) {
			SideSortInfo sideSortInfo = allItems.get(i);
			if (sideSortInfo.sortDefId == currentSort.id) {
				notifyItemChanged(i);
				break;
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
