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

import com.biglybt.android.adapter.FlexibleRecyclerAdapter;
import com.biglybt.android.adapter.FlexibleRecyclerViewHolder;
import com.biglybt.android.client.*;

import android.annotation.SuppressLint;
import android.arch.lifecycle.Lifecycle;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.view.menu.MenuBuilder;
import android.support.v7.view.menu.MenuItemImpl;
import android.support.v7.widget.RecyclerView;
import android.view.*;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Created by TuxPaper on 2/13/16.
 */
public class SideActionsAdapter
	extends
	FlexibleRecyclerAdapter<SideActionsAdapter, SideActionsAdapter.SideActionsHolder, SideActionsAdapter.SideActionsInfo>
{
	private static final String TAG = "SideActionsAdapter";

	private int[] restrictToMenuIDs;

	private final SideActionSelectionListener selector;

	private MenuBuilder menuBuilder;

	private boolean isSmall;

	public static final class SideActionsInfo
		implements Comparable<SideActionsInfo>
	{
		public final MenuItem menuItem;

		final int itemType;

		private boolean itemEnabled;

		private CharSequence title;

		SideActionsInfo(MenuItem item) {
			this(item, 0);
		}

		SideActionsInfo(MenuItem item, int itemType) {
			menuItem = item;
			this.itemType = itemType;
			itemEnabled = item.isEnabled();
			title = item.getTitle();
		}

		@Override
		public int compareTo(@NonNull SideActionsInfo another) {
			int itemId = menuItem.getItemId();
			if (itemId == 0) {
				itemId = menuItem.getTitle().toString().hashCode();
			}
			int itemIdAnother = another.menuItem.getItemId();
			if (itemIdAnother == 0) {
				itemIdAnother = another.menuItem.getTitle().toString().hashCode();
			}
			return AndroidUtils.integerCompare(itemId, itemIdAnother);
		}
	}

	static final class SideActionsHolder
		extends FlexibleRecyclerViewHolder<SideActionsHolder>
	{

		final TextView tvText;

		final ImageView iv;

		RotateAnimation rotateAnimation;

		SideActionsHolder(RecyclerSelectorInternal<SideActionsHolder> selector,
				View rowView) {
			super(selector, rowView);

			tvText = rowView.findViewById(R.id.sideaction_row_text);
			iv = rowView.findViewById(R.id.sideaction_row_image);
		}
	}

	public void setSmall(boolean small) {
		isSmall = small;
		if (getItemCount() > 0) {
			notifyDataSetInvalidated();
		}
	}

	@SuppressLint("RestrictedApi")
	public SideActionsAdapter(Lifecycle lifecycle,
			SideActionSelectionListener selector) {
		super(TAG, lifecycle, selector);
		this.restrictToMenuIDs = selector.getRestrictToMenuIDs();
		this.selector = selector;

		setHasStableIds(true);

		updateMenuItems();
	}

	@SuppressLint("RestrictedApi")
	private @Nullable MenuBuilder getMenuBuilder() {
		if (menuBuilder == null) {
			menuBuilder = selector.getMenuBuilder();
		}
		return menuBuilder;
	}

	public void updateMenuItems() {
		MenuBuilder menuBuilder = getMenuBuilder();
		selector.prepareActionMenus(menuBuilder);
		List<SideActionsInfo> list = new ArrayList<>();
		updateMenuItems(menuBuilder, list, true);

		if (AndroidUtils.DEBUG_MENU) {
			StringBuilder sb = new StringBuilder();
			boolean first = true;
			for (SideActionsInfo info : list) {
				if (first) {
					first = false;
				} else {
					sb.append(',');
				}
				sb.append(info.title);
			}
			log(TAG, sb.toString());
		}

		setItems(list, null, (oldItem, newItem) -> {
			if (!oldItem.equals(newItem)) {
				return false;
			}
			if (oldItem.itemEnabled != newItem.itemEnabled) {
				return false;
			}
			return newItem.title != null && newItem.title.equals(oldItem.title);
		});
	}

	@SuppressLint("RestrictedApi")
	private void updateMenuItems(MenuBuilder menuBuilder,
			List<SideActionsInfo> list, boolean goDeeper) {
		if (restrictToMenuIDs == null) {
			ArrayList<MenuItemImpl> actionItems = menuBuilder.getVisibleItems();
			for (MenuItem item : actionItems) {
				int itemType = goDeeper && item.hasSubMenu() ? 1 : 0;
				list.add(new SideActionsInfo(item, itemType));
				if (itemType == 1) {
					updateMenuItems((MenuBuilder) item.getSubMenu(), list, false);
				}
				if (item.getItemId() == R.id.action_refresh) {
					item.setEnabled(!selector.isRefreshing());
				}
			}
		} else {
			for (int id : restrictToMenuIDs) {
				MenuItem item = menuBuilder.findItem(id);
				if (item != null && item.isVisible()) {
					list.add(new SideActionsInfo(item));
					if (id == R.id.action_refresh) {
						item.setEnabled(!selector.isRefreshing());
					}
				}
			}
		}
	}

	@SuppressLint("RestrictedApi")
	@AnyThread
	public void updateRefreshButton() {
		if (AndroidUtilsUI.runIfNotUIThread(this::updateRefreshButton)) {
			return;
		}

		MenuBuilder menuBuilder = getMenuBuilder();
		if (menuBuilder == null) {
			return;
		}
		MenuItem menuItem = menuBuilder.findItem(R.id.action_refresh);
		if (menuItem == null) {
			return;
		}
		boolean enable = !selector.isRefreshing();
		if (enable == menuItem.isEnabled()) {
			return;
		}
		boolean refreshVisible = TorrentUtils.isAllowRefresh(selector.getSession());

		menuItem.setVisible(refreshVisible);
		menuItem.setEnabled(enable);

		RecyclerView recyclerView = getRecyclerView();
		if (recyclerView != null) {
			RecyclerView.ViewHolder vh = recyclerView.findViewHolderForItemId(
					R.id.action_refresh);
			if (vh != null) {
				int position = vh.getLayoutPosition();
				if (position >= 0) {
					notifyItemChanged(position);
				}
			}
		}
	}

	@Override
	public int getItemViewType(int position) {
		SideActionsInfo item = getItem(position);
		int i = item == null ? 0 : item.itemType;
		if (isSmall) {
			i |= 2;
		}
		return i;
	}

	@Override
	public SideActionsHolder onCreateFlexibleViewHolder(ViewGroup parent,
			int viewType) {
		final Context context = parent.getContext();
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);

		assert inflater != null;

		View rowView = inflater.inflate((viewType & 1) == 0 ? ((viewType & 2) == 2)
				? R.layout.row_sideaction_small : R.layout.row_sideaction
				: R.layout.row_sideaction_header, parent, false);

		return new SideActionsHolder(this, rowView);
	}

	@Override
	public void onBindFlexibleViewHolder(SideActionsHolder holder, int position) {
		SideActionsInfo item = getItem(position);
		item.title = item.menuItem.getTitle();
		holder.tvText.setText(item.title);
		if (holder.iv != null) {
			Drawable icon = item.menuItem.getIcon();
			holder.iv.setImageDrawable(icon);
		}

		if (item.menuItem.getItemId() == R.id.action_refresh) {
			if (selector.isRefreshing()) {
				if (holder.rotateAnimation == null) {
					holder.rotateAnimation = new RotateAnimation(0, 360,
							RotateAnimation.RELATIVE_TO_SELF, 0.5f,
							RotateAnimation.RELATIVE_TO_SELF, 0.5f);
					holder.rotateAnimation.setDuration(1000);
					holder.rotateAnimation.setStartOffset(500);
					holder.rotateAnimation.setInterpolator(new LinearInterpolator());
					holder.rotateAnimation.setRepeatCount(RotateAnimation.INFINITE);
					holder.iv.startAnimation(holder.rotateAnimation);
				}
			} else {
				holder.iv.clearAnimation();
				holder.rotateAnimation = null;
			}
		}

		item.itemEnabled = item.menuItem.isEnabled();
		holder.itemView.setEnabled(item.itemEnabled);
		if (holder.iv != null) {
			holder.iv.setAlpha(item.itemEnabled ? 0xFF : 0x80);
		}
	}

	@Override
	public long getItemId(int position) {
		SideActionsInfo item = getItem(position);
		int itemId = item.menuItem.getItemId();
		if (itemId == View.NO_ID || itemId == 0) {
			return item.menuItem.getTitle().toString().hashCode();
		}
		return itemId;
	}

	public void setRestrictToMenuIDs(int[] restrictToMenuIDs) {
		this.restrictToMenuIDs = restrictToMenuIDs;
		updateMenuItems();
	}

	public SideActionSelectionListener getSideActionSelectionListener() {
		return selector;
	}
}
