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

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuItemImpl;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.biglybt.android.adapter.FlexibleRecyclerAdapter;
import com.biglybt.android.adapter.FlexibleRecyclerViewHolder;
import com.biglybt.android.client.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by TuxPaper on 2/13/16.
 */
public class SideActionsAdapter
	extends
	FlexibleRecyclerAdapter<SideActionsAdapter, SideActionsAdapter.SideActionsHolder, SideActionsAdapter.SideActionsInfo>
{
	private static final String TAG = "SideActionsAdapter";

	private int[] restrictToMenuIDs;

	@NonNull
	private final SideActionSelectionListener selector;

	private MenuBuilder menuBuilder;

	private boolean isSmall;

	public static final class SideActionsInfo
	{
		@NonNull
		public final MenuItem menuItem;

		final int itemType;

		boolean itemEnabled;

		CharSequence title;

		SideActionsInfo(@NonNull MenuItem item) {
			this(item, 0);
		}

		SideActionsInfo(@NonNull MenuItem item, int itemType) {
			menuItem = item;
			this.itemType = itemType;
			itemEnabled = item.isEnabled();
			title = item.getTitle();
		}

		@Override
		public boolean equals(@Nullable Object obj) {
			if (!(obj instanceof SideActionsInfo)) {
				return false;
			}
			SideActionsInfo another = (SideActionsInfo) obj;
			int itemId = menuItem.getItemId();
			if (itemId == 0) {
				itemId = menuItem.getTitle().toString().hashCode();
			}
			int itemIdAnother = another.menuItem.getItemId();
			if (itemIdAnother == 0) {
				itemIdAnother = another.menuItem.getTitle().toString().hashCode();
			}
			return itemId == itemIdAnother;
		}
	}

	static final class SideActionsHolder
		extends FlexibleRecyclerViewHolder<SideActionsHolder>
	{

		@NonNull
		final TextView tvText;

		final ImageView iv;

		RotateAnimation rotateAnimation;

		SideActionsHolder(RecyclerSelectorInternal<SideActionsHolder> selector,
				@NonNull View rowView) {
			super(selector, rowView);

			tvText = ViewCompat.requireViewById(rowView, R.id.sideaction_row_text);
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
	public SideActionsAdapter(@NonNull SideActionSelectionListener selector) {
		super(TAG, selector);
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
	private void updateMenuItems(@NonNull MenuBuilder menuBuilder,
			@NonNull List<SideActionsInfo> list, boolean goDeeper) {
		if (restrictToMenuIDs == null) {
			ArrayList<MenuItemImpl> actionItems = menuBuilder.getVisibleItems();
			for (MenuItem item : actionItems) {
				int itemType = goDeeper && item.hasSubMenu() ? 1 : 0;
				list.add(new SideActionsInfo(item, itemType));
				if (itemType == 1) {
					SubMenu subMenu = item.getSubMenu();
					if (subMenu instanceof MenuBuilder) {
						updateMenuItems((MenuBuilder) subMenu, list, false);
					}
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
			@SuppressLint("WrongThread") // Not wrong thread because AndroidUtilsUI.runIfNotUIThread
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

	@NonNull
	@Override
	public SideActionsHolder onCreateFlexibleViewHolder(@NonNull ViewGroup parent,
			@NonNull LayoutInflater inflater, int viewType) {

		View rowView = AndroidUtilsUI.requireInflate(inflater,
				(viewType & 1) == 0 ? ((viewType & 2) == 2)
						? R.layout.row_sideaction_small : R.layout.row_sideaction
						: R.layout.row_sideaction_header,
				parent, false);

		return new SideActionsHolder(this, rowView);
	}

	@Override
	public void onBindFlexibleViewHolder(@NonNull SideActionsHolder holder, int position) {
		SideActionsInfo item = getItem(position);
		if (item == null) {
			return;
		}
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
					if (holder.iv != null) {
						holder.iv.startAnimation(holder.rotateAnimation);
					}
				}
			} else {
				if (holder.iv != null) {
					holder.iv.clearAnimation();
				}
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
		if (item == null) {
			return -1;
		}
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
