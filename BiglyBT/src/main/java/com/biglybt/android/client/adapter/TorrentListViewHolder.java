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

import com.biglybt.android.FlexibleRecyclerViewHolder;
import com.biglybt.android.client.R;

import android.support.annotation.Nullable;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class TorrentListViewHolder
	extends FlexibleRecyclerViewHolder
{
	private static final String TAG = "TLVH";

	public TorrentListViewHolder(@Nullable RecyclerSelectorInternal selector,
			View rowView, boolean isSmall) {
		super(selector, rowView);
		this.isSmall = isSmall;
		tvName = (TextView) rowView.findViewById(R.id.torrentrow_name);
		tvProgress = (TextView) rowView.findViewById(R.id.torrentrow_progress_pct);
		pb = (ProgressBar) rowView.findViewById(R.id.torrentrow_progress);
		tvInfo = (TextView) rowView.findViewById(R.id.torrentrow_info);
		tvETA = (TextView) rowView.findViewById(R.id.torrentrow_eta);
		tvUlRate = (TextView) rowView.findViewById(R.id.torrentrow_upspeed);
		tvDlRate = (TextView) rowView.findViewById(R.id.torrentrow_downspeed);
		tvStatus = (TextView) rowView.findViewById(R.id.torrentrow_state);
		tvTags = (TextView) rowView.findViewById(R.id.torrentrow_tags);
		tvTrackerError = (TextView) rowView.findViewById(
				R.id.torrentrow_tracker_error);
		ivChecked = (ImageView) rowView.findViewById(R.id.torrentrow_checked);
	}

	final boolean isSmall;

	long torrentID = -1;

	final TextView tvName;

	final TextView tvProgress;

	final ProgressBar pb;

	final TextView tvInfo;

	final TextView tvETA;

	final TextView tvUlRate;

	final TextView tvDlRate;

	final TextView tvStatus;

	final TextView tvTags;

	final TextView tvTrackerError;

	final ImageView ivChecked;

	boolean animateFlip;
}
