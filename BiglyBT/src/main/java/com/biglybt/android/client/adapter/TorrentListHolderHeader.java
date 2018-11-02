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

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.R;

import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

/**
 * Created by TuxPaper on 3/20/17.
 */

public class TorrentListHolderHeader
	extends TorrentListHolder
{
	private final ImageButton collapseButton;

	private final TextView tvTitle;

	private final TorrentListAdapter adapter;

	private final TextView tvCount;

	TorrentListHolderHeader(final TorrentListAdapter adapter,
			@Nullable RecyclerSelectorInternal selector, View rowView) {
		super(selector, rowView);
		this.adapter = adapter;

		tvTitle = itemView.findViewById(R.id.torrentList_headerText);
		tvCount = itemView.findViewById(R.id.torrentList_headerCount);
		collapseButton = itemView.findViewById(R.id.collapseButton);
		if (AndroidUtils.usesNavigationControl()) {
			rowView.setOnClickListener(
					v -> adapter.flipHeaderCollapse(getAdapterPosition()));
		}
		collapseButton.setOnClickListener(
				v -> adapter.flipHeaderCollapse(getAdapterPosition()));
	}

	public void bind(TorrentListAdapterHeaderItem item) {
		tvTitle.setText(item.title);

		if (adapter.showGroupCount(item.id)) {
			String s = tvCount.getResources().getQuantityString(
					R.plurals.torrent_count, item.count, item.count);
			tvCount.setText(s);
		} else {
			tvCount.setText("");
		}

		boolean sectionIsCollapsed = adapter.isGroupCollapsed(item.id);
		updateCollapseState(sectionIsCollapsed);
	}

	public void updateCollapseState(boolean sectionIsCollapsed) {
		@DrawableRes
		int id = sectionIsCollapsed ? R.drawable.ic_expand_more_black_24dp
				: R.drawable.ic_expand_less_black_24dp;
		Drawable d = VectorDrawableCompat.create(
				collapseButton.getContext().getResources(), id, null);
		if (d == null) {
			return;
		}
		d = DrawableCompat.wrap(d);
		collapseButton.setImageDrawable(d);
	}
}
