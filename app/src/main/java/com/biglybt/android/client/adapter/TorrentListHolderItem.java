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

import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.util.Log;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.RelativeLayout.LayoutParams;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.biglybt.android.adapter.FlexibleRecyclerAdapter;
import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.R;

class TorrentListHolderItem
	extends TorrentListHolder
{
	private static final boolean DEBUG = false;

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

	final Button btnAuth;

	boolean animateFlip;

	private final ViewGroup topRightArea;

	private final ViewGroup leftArea;

	private boolean areTagsIndented = true;

	private boolean isUnderRightArea = true;

	private boolean layoutQueued = false;

	TorrentListHolderItem(
			@Nullable RecyclerSelectorInternal<TorrentListHolder> selector,
			@NonNull View rowView, boolean isSmall) {
		super(selector, rowView);
		this.isSmall = isSmall;
		tvName = rowView.findViewById(R.id.torrentrow_name);
		tvProgress = rowView.findViewById(R.id.torrentrow_progress_pct);
		pb = rowView.findViewById(R.id.torrentrow_progress);
		tvInfo = rowView.findViewById(R.id.torrentrow_info);
		tvETA = rowView.findViewById(R.id.torrentrow_eta);
		tvUlRate = rowView.findViewById(R.id.torrentrow_upspeed);
		tvDlRate = rowView.findViewById(R.id.torrentrow_downspeed);
		tvStatus = rowView.findViewById(R.id.torrentrow_state);
		tvTags = rowView.findViewById(R.id.torrentrow_tags);
		tvTrackerError = rowView.findViewById(R.id.torrentrow_tracker_error);
		ivChecked = rowView.findViewById(R.id.torrentrow_checked);
		topRightArea = rowView.findViewById(R.id.torrentrow_topright);
		leftArea = rowView.findViewById(R.id.torrentrow_leftArea);
		btnAuth = rowView.findViewById(R.id.torrentrow_folderauth);

		if (tvTags != null && topRightArea != null && leftArea != null) {
			OnLayoutChangeListener onLayoutChangeListener = (v, left, top, right,
					bottom, oldLeft, oldTop, oldRight, oldBottom) -> recalcLayout(v);
			tvTags.addOnLayoutChangeListener(onLayoutChangeListener);
			// Must listen to the left/right areas in case one of them becomes the new
			// bottom
			topRightArea.addOnLayoutChangeListener(onLayoutChangeListener);
			leftArea.addOnLayoutChangeListener(onLayoutChangeListener);
		}

		if (VERSION.SDK_INT >= VERSION_CODES.N && pb != null) {
			// quirk seen: new rows with 0 progress have weirdly random progress visual display
			pb.setProgress(1, false);
		}
	}

	private void recalcLayout(View view) {
		if (tvTags.getText().length() == 0) {
			return;
		}

		int bottomOfRightArea = topRightArea.getBottom();
		int bottomOfLeftArea = leftArea.getBottom();
		int tagsWidth = tvTags.getWidth();
		int rightWidth = topRightArea.getWidth();

		boolean indentTags = tagsWidth < rightWidth
				|| bottomOfLeftArea - bottomOfRightArea > tvTags.getLineHeight() * 2;
		boolean underRightArea = indentTags || bottomOfRightArea > bottomOfLeftArea;

		if (DEBUG) {
			Log.d("TorrentListHolderItem",
					this + ":" + tvName.getText() + "] bottoms l/r=" + +bottomOfLeftArea
							+ "/" + bottomOfRightArea + "; tagsW=" + tagsWidth + "; rW="
							+ rightWidth + "; indent? " + areTagsIndented + "->" + indentTags
							+ ";" + (underRightArea ? "underR" : "underL") + "; Relayout? "
							+ (indentTags != areTagsIndented
									|| underRightArea != isUnderRightArea));
		}

		if (indentTags == areTagsIndented && underRightArea == isUnderRightArea) {
			return;
		}

		LayoutParams lp = (LayoutParams) tvTags.getLayoutParams();
		if (indentTags) {
			if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
				lp.addRule(RelativeLayout.END_OF, R.id.torrentrow_leftArea);
				lp.setMarginStart(0);
			} else {
				lp.leftMargin = 0;
			}
			lp.addRule(RelativeLayout.RIGHT_OF, R.id.torrentrow_leftArea);
			lp.addRule(RelativeLayout.BELOW, R.id.torrentrow_topright);
		} else {
			if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
				lp.addRule(RelativeLayout.END_OF, 0);
				lp.setMarginStart(AndroidUtilsUI.dpToPx(20));
			} else {
				lp.leftMargin = AndroidUtilsUI.dpToPx(20);
			}
			lp.addRule(RelativeLayout.RIGHT_OF, 0);
			lp.addRule(RelativeLayout.BELOW,
					underRightArea ? R.id.torrentrow_topright : R.id.torrentrow_leftArea);
		}
		if (layoutQueued) {
			// Only need one setTagLayout() call; it will use our current LayoutParams
			if (DEBUG) {
				Log.d("TorrentListHolderItem",
						this + ":" + tvName.getText() + "] skip setTagLayout, already Qd");
			}
			return;
		}

		if (VERSION.SDK_INT <= VERSION_CODES.M || view.isInLayout()) {
			final long currentTorrentId = torrentID;
			layoutQueued = true;
			tvTags.post(() -> {
				layoutQueued = false;
				if (torrentID == currentTorrentId) {
					if (DEBUG) {
						Log.d("TorrentListHolderItem",
								this + ":" + tvName.getText() + "] set setTagLayout");
					}
					setTagLayout(lp);
					isUnderRightArea = underRightArea;
					areTagsIndented = indentTags;
				} else {
					if (DEBUG) {
						Log.d("TorrentListHolderItem",
								this + ":" + tvName.getText()
										+ "] skip setTagLayout, torrent id changed "
										+ currentTorrentId + "->" + torrentID);
					}
				}
			});
		} else {
			setTagLayout(lp);
			isUnderRightArea = underRightArea;
			areTagsIndented = indentTags;
		}
	}

	private void setTagLayout(LayoutParams lp) {
		tvTags.setLayoutParams(lp);
		// Need to tell adapter we messed around with things outside of its onBindViewHolder
		Object bindingAdapter = getBindingAdapter();
		if (bindingAdapter instanceof FlexibleRecyclerAdapter) {
			//noinspection rawtypes
			((FlexibleRecyclerAdapter) bindingAdapter).safeNotifyItemChanged(
					getLayoutPosition());
		}
	}
}
