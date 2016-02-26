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
import android.graphics.drawable.Drawable;
import android.support.v7.view.menu.MenuBuilder;
import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;

import com.vuze.android.FlexibleRecyclerAdapter;
import com.vuze.android.FlexibleRecyclerSelectionListener;
import com.vuze.android.FlexibleRecyclerViewHolder;
import com.vuze.android.remote.AndroidUtilsUI;
import com.vuze.android.remote.R;

/**
 * Created by TuxPaper on 2/13/16.
 */
public class SideActionsAdapter
	extends
	FlexibleRecyclerAdapter<SideActionsAdapter.SideActionsHolder, SideActionsAdapter.SideActionsInfo>
{

	private static final String TAG = "SideActionsAdapter";

	private final Context context;

	public static final class SideActionsInfo
	{
		public final MenuItem menuItem;

		public SideActionsInfo(MenuItem item) {
			this.menuItem = item;
		}
	}

	static final public class SideActionsHolder
		extends FlexibleRecyclerViewHolder
	{

		final TextView tvText;

		final TextView tvTextSmall;

		final ImageView iv;

		public SideActionsHolder(RecyclerSelectorInternal selector, View rowView) {
			super(selector, rowView);

			tvText = (TextView) rowView.findViewById(R.id.sideaction_row_text);
			tvTextSmall = (TextView) rowView.findViewById(
					R.id.sideaction_row_smalltext);
			iv = (ImageView) rowView.findViewById(R.id.sideaction_row_image);
		}
	}

	public SideActionsAdapter(Context context,
			FlexibleRecyclerSelectionListener selector) {
		super(selector);
		this.context = context;
		setHasStableIds(true);

		List<SideActionsInfo> list = new ArrayList<>();

		MenuBuilder menuBuilder = new MenuBuilder(context);
		new MenuInflater(context).inflate(R.menu.menu_torrent_list, menuBuilder);

		int[] ids = {
			R.id.action_refresh,
			R.id.action_add_torrent,
			R.id.action_search,
			R.id.action_start_all,
			R.id.action_stop_all,
			R.id.action_settings,
			R.id.action_social,
			R.id.action_logout,
		};

		for (int id : ids) {
			MenuItem item = menuBuilder.findItem(id);
			if (item != null) {
				list.add(new SideActionsInfo(item));
			}
		}

		setItems(list);
	}

	@Override
	public SideActionsHolder onCreateFlexibleViewHolder(ViewGroup parent,
			int viewType) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);

		View rowView = inflater.inflate(R.layout.row_sideaction, parent, false);

		SideActionsHolder vh = new SideActionsHolder(this, rowView);

		return vh;
	}

	@Override
	public void onBindFlexibleViewHolder(SideActionsHolder holder, int position) {
		SideActionsInfo item = getItem(position);
		CharSequence s = item.menuItem.getTitle();
		holder.tvText.setText(s);
		holder.tvTextSmall.setText(s);
		int width = getRecyclerView() == null ? 0 : getRecyclerView().getWidth();
		boolean isSmall = width != 0 && width <= AndroidUtilsUI.dpToPx(120);
		holder.tvTextSmall.setVisibility(isSmall ? View.VISIBLE : View.GONE);
		holder.tvText.setVisibility(isSmall ? View.GONE : View.VISIBLE);
		Drawable icon = item.menuItem.getIcon();
		holder.iv.setImageDrawable(icon);
	}

	@Override
	public long getItemId(int position) {
		SideActionsInfo item = getItem(position);
		return item.menuItem.getItemId();
	}
}
