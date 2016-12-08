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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.vuze.android.FlexibleRecyclerAdapter;
import com.vuze.android.FlexibleRecyclerSelectionListener;
import com.vuze.android.FlexibleRecyclerViewHolder;
import com.vuze.android.remote.*;
import com.vuze.android.remote.spanbubbles.SpanTags;
import com.vuze.util.MapUtils;
import com.vuze.util.Thunk;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Created by TuxPaper on 2/13/16.
 */
public class SideTagAdapter
	extends
	FlexibleRecyclerAdapter<SideTagAdapter.SideTagHolder, SideTagAdapter.SideTagInfo>
{

	@Thunk
	final Context context;

	@Thunk
	final String remoteProfileID;

	private int paddingLeft;

	public static final class SideTagInfo
		implements Comparable<SideTagInfo>
	{
		public final long id;

		public final Map tag;

		public SideTagInfo(Map tag) {
			this.tag = tag;
			this.id = MapUtils.getMapLong(tag, "uid", 0);
		}

		@Override
		public int compareTo(@NonNull SideTagInfo another) {
			return AndroidUtils.longCompare(id, another.id);
		}
	}

	final public class SideTagHolder
		extends FlexibleRecyclerViewHolder
	{

		final TextView tvText;

		final SpanTags spanTag;

		public SideTagHolder(RecyclerSelectorInternal selector, View rowView) {
			super(selector, rowView);

			tvText = (TextView) rowView.findViewById(R.id.sidetag_row_text);
			SessionInfo sessionInfo = SessionInfoManager.getSessionInfo(
					remoteProfileID, null, null);
			spanTag = new SpanTags(context, sessionInfo, tvText, null);
			spanTag.setShowIcon(false);
		}
	}

	public SideTagAdapter(Context context, @Nullable String remoteProfileID,
			FlexibleRecyclerSelectionListener selector) {
		super(selector);
		this.context = context;
		this.remoteProfileID = remoteProfileID;
		setHasStableIds(true);
	}

	@Override
	public SideTagHolder onCreateFlexibleViewHolder(ViewGroup parent,
			int viewType) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);

		View rowView = inflater.inflate(R.layout.row_sidetag, parent, false);

		return new SideTagHolder(this, rowView);
	}

	@Override
	public void onBindFlexibleViewHolder(SideTagHolder holder, int position) {
		int width = getRecyclerView() == null ? 0 : getRecyclerView().getWidth();
		boolean isSmall = width != 0 && width <= AndroidUtilsUI.dpToPx(120);

		SideTagInfo item = getItem(position);
		List<Map<?, ?>> list = new ArrayList<>();
		list.add(item.tag);
		holder.spanTag.setDrawCount(!isSmall);
		holder.spanTag.setTagMaps(list);
		holder.spanTag.updateTags();

		holder.tvText.setPadding(paddingLeft, 0, 0, 0);
	}

	public void setPaddingLeft(int paddingLeft) {
		this.paddingLeft = paddingLeft;
		notifyDataSetInvalidated();
	}

	@Override
	public long getItemId(int position) {
		SideTagInfo item = getItem(position);
		return item.id;
	}
}
