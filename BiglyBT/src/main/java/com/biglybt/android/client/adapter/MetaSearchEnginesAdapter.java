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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.biglybt.android.adapter.FlexibleRecyclerAdapter;
import com.biglybt.android.adapter.FlexibleRecyclerSelectionListener;
import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.BiglyBTApp;
import com.biglybt.android.client.R;
import com.biglybt.util.DisplayFormatters;
import com.squareup.picasso.Picasso;

/**
 * Created by TuxPaper on 4/22/16.
 */
public class MetaSearchEnginesAdapter
	extends
	FlexibleRecyclerAdapter<MetaSearchEnginesAdapter, MetaSearchEnginesHolder, MetaSearchEnginesInfo>
{
	private static final String TAG = "MetaSearchEnginesAdapter";

	public MetaSearchEnginesAdapter(
			FlexibleRecyclerSelectionListener<MetaSearchEnginesAdapter, MetaSearchEnginesHolder, MetaSearchEnginesInfo> rs) {
		super(TAG, rs);
		Picasso.with(BiglyBTApp.getContext()).setLoggingEnabled(true);
		setHasStableIds(true);
	}

	@Override
	public long getItemId(int position) {
		MetaSearchEnginesInfo item = getItem(position);
		if (item == null) {
			return -1;
		}
		return item.uid.hashCode();
	}

	@Override
	public void onBindFlexibleViewHolder(@NonNull MetaSearchEnginesHolder holder,
			int position) {
		MetaSearchEnginesInfo item = getItem(position);
		if (item == null) {
			return;
		}

		holder.tvName.setText(item.name);
		holder.tvCount.setText(item.count == 0 ? "" : item.count == -1 ? "Error"
				: DisplayFormatters.formatNumber(item.count));

		holder.pb.setVisibility(item.completed ? View.GONE : View.VISIBLE);
		holder.ivChecked.setVisibility(
				isItemChecked(position) ? View.VISIBLE : View.GONE);

		String url = "http://search.vuze.com/xsearch/imageproxy.php?url="
				+ item.iconURL;
		Picasso picassoInstance = BiglyBTApp.getPicassoInstance();
		picassoInstance.load(url).into(holder.iv);
	}

	@NonNull
	@Override
	public MetaSearchEnginesHolder onCreateFlexibleViewHolder(
			@NonNull ViewGroup parent, @NonNull LayoutInflater inflater,
			int viewType) {

		View rowView = AndroidUtilsUI.requireInflate(inflater,
				R.layout.row_ms_engine_sidelist, parent, false);

		return new MetaSearchEnginesHolder(this, rowView);
	}

	public void refreshItem(@NonNull String uid, boolean completed, int numAdded) {
		MetaSearchEnginesInfo info = new MetaSearchEnginesInfo(uid);
		if (info.completed == completed && numAdded == 0) {
			return;
		}
		final int position = getPositionForItem(info);
		if (position < 0) {
			return;
		}

		info = getItem(position);
		if (info == null) {
			return;
		}
		boolean changed = false;
		if (info.completed != completed) {
			info.completed = completed;
			changed = true;
		}
		int oldCount = info.count;
		if (numAdded < 0) {
			info.count = -1;
		} else {
			info.count += numAdded;
		}
		changed |= (info.count != oldCount);

		if (changed) {
			RecyclerView recyclerView = getRecyclerView();
			if (recyclerView != null) {
				recyclerView.post(() -> notifyItemChanged(position));
			}
		}
	}
}
