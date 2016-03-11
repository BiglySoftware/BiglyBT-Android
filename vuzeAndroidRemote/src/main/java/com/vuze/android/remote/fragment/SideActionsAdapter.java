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
import android.support.v7.widget.RecyclerView;
import android.view.*;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import com.vuze.android.FlexibleRecyclerAdapter;
import com.vuze.android.FlexibleRecyclerSelectionListener;
import com.vuze.android.FlexibleRecyclerViewHolder;
import com.vuze.android.remote.*;
import com.vuze.android.remote.activity.TorrentViewActivity;

/**
 * Created by TuxPaper on 2/13/16.
 */
public class SideActionsAdapter
	extends
	FlexibleRecyclerAdapter<SideActionsAdapter.SideActionsHolder, SideActionsAdapter.SideActionsInfo>
{
	private static final String TAG = "SideActionsAdapter";

	private static final int menu_ids[] = new int[] {
		R.id.action_refresh,
		R.id.action_add_torrent,
		R.id.action_search,
		R.id.action_start_all,
		R.id.action_stop_all,
		R.id.action_settings,
		R.id.action_social,
		R.id.action_logout
	};

	private final Context context;

	private final SessionInfo sessionInfo;

	private final MenuBuilder menuBuilder;

	public static final class SideActionsInfo
		implements Comparable<SideActionsInfo>
	{
		public final MenuItem menuItem;

		public SideActionsInfo(MenuItem item) {
			this.menuItem = item;
		}

		@Override
		public int compareTo(SideActionsInfo another) {
			return AndroidUtils.integerCompare(menuItem.getItemId(),
					another.menuItem.getItemId());
		}
	}

	static final public class SideActionsHolder
		extends FlexibleRecyclerViewHolder
	{

		final TextView tvText;

		final TextView tvTextSmall;

		final ImageView iv;

		public RotateAnimation rotateAnimation;

		public SideActionsHolder(RecyclerSelectorInternal selector, View rowView) {
			super(selector, rowView);

			tvText = (TextView) rowView.findViewById(R.id.sideaction_row_text);
			tvTextSmall = (TextView) rowView.findViewById(
					R.id.sideaction_row_smalltext);
			iv = (ImageView) rowView.findViewById(R.id.sideaction_row_image);
		}
	}

	public SideActionsAdapter(Context context, SessionInfo sessionInfo,
			FlexibleRecyclerSelectionListener selector) {
		super(selector);
		this.context = context;
		this.sessionInfo = sessionInfo;
		setHasStableIds(true);

		menuBuilder = new MenuBuilder(context);
		new MenuInflater(context).inflate(R.menu.menu_torrent_list, menuBuilder);

		updateMenuItems();
	}

	public void updateMenuItems() {

		TorrentViewActivity.prepareGlobalMenu(menuBuilder, sessionInfo);

		List<SideActionsInfo> list = new ArrayList<>();
		for (int id : menu_ids) {
			MenuItem item = menuBuilder.findItem(id);
			if (item != null && item.isVisible()) {
				list.add(new SideActionsInfo(item));
			}
		}

		setItems(list);
	}

	public void updateRefreshButton() {
		RecyclerView.ViewHolder vh = getRecyclerView().findViewHolderForItemId(
				R.id.action_refresh);
		if (vh != null) {

			int position = vh.getLayoutPosition();
			if (position >= 0) {
				SideActionsInfo item = getItem(position);
				if (item != null) {
					item.menuItem.setEnabled(!sessionInfo.isRefreshingTorrentList());
					notifyItemChanged(position);
				}
			}
		}
	}

	@Override
	public SideActionsHolder onCreateFlexibleViewHolder(ViewGroup parent,
			int viewType) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);

		View rowView = inflater.inflate(R.layout.row_sideaction, parent, false);

		return new SideActionsHolder(this, rowView);
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

		if (item.menuItem.getItemId() == R.id.action_refresh) {
			if (sessionInfo.isRefreshingTorrentList()) {
				if (holder.rotateAnimation == null) {
					holder.rotateAnimation = new RotateAnimation(0, 360,
							RotateAnimation.RELATIVE_TO_SELF, 0.5f,
							RotateAnimation.RELATIVE_TO_SELF, 0.5f);
					holder.rotateAnimation.setDuration(1000);
					holder.rotateAnimation.setInterpolator(new LinearInterpolator());
					holder.rotateAnimation.setRepeatCount(RotateAnimation.INFINITE);
					holder.iv.startAnimation(holder.rotateAnimation);
				}
			} else {
				holder.iv.clearAnimation();
				holder.rotateAnimation = null;
			}
		}

		holder.itemView.setEnabled(item.menuItem.isEnabled());
		holder.iv.setAlpha(item.menuItem.isEnabled() ? 0xFF : 0x80);
	}

	@Override
	public long getItemId(int position) {
		SideActionsInfo item = getItem(position);
		return item.menuItem.getItemId();
	}
}
