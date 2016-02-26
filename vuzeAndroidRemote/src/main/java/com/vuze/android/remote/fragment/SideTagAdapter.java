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

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.vuze.android.FlexibleRecyclerAdapter;
import com.vuze.android.FlexibleRecyclerSelectionListener;
import com.vuze.android.FlexibleRecyclerViewHolder;
import com.vuze.android.remote.R;
import com.vuze.android.remote.SessionInfo;
import com.vuze.android.remote.SessionInfoListener;
import com.vuze.android.remote.rpc.TransmissionRPC;
import com.vuze.android.remote.spanbubbles.SpanTags;
import com.vuze.util.MapUtils;

/**
 * Created by TuxPaper on 2/13/16.
 */
public class SideTagAdapter
	extends
	FlexibleRecyclerAdapter<SideTagAdapter.SideTagHolder, SideTagAdapter.SideTagInfo>
{

	private final Context context;

	private final SessionInfo sessionInfo;


	public static final class SideTagInfo
	{
		public long id;

		public Map tag;

		public SideTagInfo(Map tag) {
			this.tag = tag;
			this.id = MapUtils.getMapLong(tag, "uid", 0);
		}
	}

	final public class SideTagHolder
		extends FlexibleRecyclerViewHolder
	{

		final TextView tvText;

		SpanTags spanTag;

		public SideTagHolder(RecyclerSelectorInternal selector, View rowView) {
			super(selector, rowView);

			tvText = (TextView) rowView.findViewById(R.id.sidetag_row_text);
			spanTag = new SpanTags(context, sessionInfo, tvText, null);
			spanTag.setShowIcon(false);
		}
	}

	public SideTagAdapter(Context context, final SessionInfo sessionInfo,
			FlexibleRecyclerSelectionListener selector) {
		super(selector);
		this.context = context;
		this.sessionInfo = sessionInfo;
		setHasStableIds(true);
	}

	@Override
	public SideTagHolder onCreateFlexibleViewHolder(ViewGroup parent,
			int viewType) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);

		View rowView = inflater.inflate(R.layout.row_sidetag, parent, false);

		SideTagHolder vh = new SideTagHolder(this, rowView);

		return vh;
	}

	@Override
	public void onBindFlexibleViewHolder(SideTagHolder holder, int position) {
		SideTagInfo item = getItem(position);
		List<Map<?, ?>> list = new ArrayList<>();
		list.add(item.tag);
		holder.spanTag.setTagMaps(list);
		holder.spanTag.updateTags();
	}

	@Override
	public long getItemId(int position) {
		SideTagInfo item = getItem(position);
		return item.id;
	}
}
