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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.biglybt.android.adapter.FlexibleRecyclerAdapter;
import com.biglybt.android.adapter.FlexibleRecyclerSelectionListener;
import com.biglybt.android.adapter.FlexibleRecyclerViewHolder;
import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.R;
import com.biglybt.android.client.TransmissionVars;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.android.client.spanbubbles.SpanTags;
import com.biglybt.android.util.MapUtils;
import com.biglybt.util.Thunk;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;

/**
 * Created by TuxPaper on 2/13/16.
 */
public class SideTagAdapter
	extends
	FlexibleRecyclerAdapter<SideTagAdapter, SideTagAdapter.SideTagHolder, SideTagAdapter.SideTagInfo>
{
	private static final String TAG = "SideTagAdapter";

	private static final int VIEWTYPE_ITEM = 0;

	private static final int VIEWTYPE_HEADER = 1;

	@Thunk
	final String remoteProfileID;

	private int paddingLeft;

	public static abstract class SideTagInfo
	{
		public final long id;

		protected SideTagInfo(long id) {
			this.id = id;
		}

		@Override
		public boolean equals(@Nullable Object obj) {
			return (obj instanceof SideTagInfo) && id == ((SideTagInfo) obj).id;
		}
	}

	public static final class SideTagInfoHeader
		extends SideTagInfo
	{
		public final String groupName;

		public SideTagInfoHeader(@NonNull String groupName) {
			super(groupName.hashCode());
			this.groupName = groupName;
		}
	}

	public static final class SideTagInfoItem
		extends SideTagInfo
	{
		public SideTagInfoItem(Map tag) {
			super(MapUtils.getMapLong(tag, TransmissionVars.FIELD_TAG_UID, 0));
		}
	}

	final public class SideTagHolder
		extends FlexibleRecyclerViewHolder
	{

		final TextView tvText;

		final SpanTags spanTag;

		public SideTagHolder(RecyclerSelectorInternal selector, View rowView,
				boolean createSpan) {
			super(selector, rowView);

			tvText = rowView.findViewById(R.id.sidetag_row_text);
			if (createSpan) {
				spanTag = new SpanTags(tvText, null);
				spanTag.setShowIcon(false);
			} else {
				spanTag = null;
			}
		}
	}

	public SideTagAdapter(Lifecycle lifecycle, @Nullable String remoteProfileID,
			FlexibleRecyclerSelectionListener selector) {
		super(TAG, lifecycle, selector);
		this.remoteProfileID = remoteProfileID;
		setHasStableIds(true);
	}

	@Override
	public SideTagHolder onCreateFlexibleViewHolder(ViewGroup parent,
			int viewType) {
		LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);

		assert inflater != null;
		boolean isItemType = viewType == VIEWTYPE_ITEM;
		View rowView = inflater.inflate(
				isItemType ? R.layout.row_sidetag : R.layout.row_sidetag_header, parent,
				false);

		return new SideTagHolder(this, rowView, isItemType);
	}

	@Override
	public void onBindFlexibleViewHolder(SideTagHolder holder, int position) {
		Session session = SessionManager.getSession(remoteProfileID, null);
		if (session == null) {
			return;
		}

		int width = getRecyclerView() == null ? 0 : getRecyclerView().getWidth();
		boolean isSmall = width != 0 && width <= AndroidUtilsUI.dpToPx(120);

		SideTagInfo item = getItem(position);
		if (item instanceof SideTagInfoItem) {
			final Map<?, ?> tag = session.tag.getTag(((SideTagInfoItem) item).id);
			if (tag == null) {
				return;
			}
			List<Map<?, ?>> list = new ArrayList<>();
			list.add(tag);
			holder.spanTag.setDrawCount(!isSmall);
			holder.spanTag.setTagMaps(list);
			holder.spanTag.updateTags();

			holder.tvText.setPadding(paddingLeft, 0, 0, 0);
		} else if (item instanceof SideTagInfoHeader) {
			holder.tvText.setText(((SideTagInfoHeader) item).groupName);
		}
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

	@Override
	public int getItemViewType(int position) {
		return getItem(position) instanceof SideTagInfoItem ? VIEWTYPE_ITEM
				: VIEWTYPE_HEADER;
	}

	@Override
	public boolean isItemCheckable(int position) {
		return getItemViewType(position) == VIEWTYPE_ITEM;
	}
}
