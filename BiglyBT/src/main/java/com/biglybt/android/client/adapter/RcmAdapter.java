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

import android.content.Context;
import android.text.format.DateUtils;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.biglybt.android.adapter.*;
import com.biglybt.android.client.*;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.spanbubbles.SpanTags;
import com.biglybt.android.util.MapUtils;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.Thunk;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RcmAdapter
	extends SortableRecyclerAdapter<RcmAdapter, RcmAdapter.ViewHolder, String>
	implements SessionAdapterFilterTalkback<String>,
	FlexibleRecyclerAdapter.SetItemsCallBack<String>
{
	private static final String TAG = "RcmAdapter";

	public interface RcmSelectionListener
		extends FlexibleRecyclerSelectionListener<RcmAdapter, ViewHolder, String>
	{
		Map getSearchResultMap(String hash);

		List<String> getSearchResultList();

		void downloadResult(String id);
	}

	static class ViewHolder
		extends FlexibleRecyclerViewHolder<ViewHolder>
	{
		@NonNull
		TextView tvName;

		@NonNull
		TextView tvInfo;

		@NonNull
		TextView tvTags;

		@NonNull
		TextView tvSize;

		@NonNull
		ProgressBar pbRank;

		ImageButton ibDownload;

		ViewHolder(RecyclerSelectorInternal<ViewHolder> selector,
				@NonNull View rowView) {
			super(selector, rowView);
			tvName = ViewCompat.requireViewById(rowView, R.id.rcmrow_title);
			tvInfo = ViewCompat.requireViewById(rowView, R.id.rcmrow_info);
			tvTags = ViewCompat.requireViewById(rowView, R.id.rcmrow_tags);
			tvSize = ViewCompat.requireViewById(rowView, R.id.rcmrow_size);
			pbRank = ViewCompat.requireViewById(rowView, R.id.rcmrow_rank);
			pbRank.setMax(100);

			ibDownload = rowView.findViewById(R.id.rcmrow_dl_button);
		}
	}

	private final View.OnClickListener onDownloadClickedListener;

	private final int inflateID;

	@NonNull
	private final SessionGetter sessionGetter;

	@Thunk
	@NonNull
	final RcmSelectionListener rs;

	private final Object mLock = new Object();

	public RcmAdapter(@NonNull SessionGetter sessionGetter,
			@NonNull RcmSelectionListener rs) {
		super(TAG, rs);
		this.sessionGetter = sessionGetter;
		this.rs = rs;

		inflateID = AndroidUtils.usesNavigationControl()
				? R.layout.row_rcm_list_dpad : R.layout.row_rcm_list;

		onDownloadClickedListener = v -> {
			RecyclerView rv = getRecyclerView();
			if (rv == null) {
				return;
			}
			RecyclerView.ViewHolder viewHolder = rv.findContainingViewHolder(v);

			if (viewHolder == null) {
				return;
			}
			int position = viewHolder.getAdapterPosition();
			String id = getItem(position);

			RcmAdapter.this.rs.downloadResult(id);
		};

	}

	@NonNull
	@Override
	public ViewHolder onCreateFlexibleViewHolder(@NonNull ViewGroup parent,
			@NonNull LayoutInflater inflater, int viewType) {
		View rowView = AndroidUtilsUI.requireInflate(inflater, inflateID, parent,
				false);
		ViewHolder viewHolder = new ViewHolder(this, rowView);
		if (viewHolder.ibDownload != null) {
			viewHolder.ibDownload.setOnClickListener(onDownloadClickedListener);
		}

		return viewHolder;
	}

	@Override
	public void onBindFlexibleViewHolder(@NonNull ViewHolder holder,
			int position) {
		Map<?, ?> mapRCM = rs.getSearchResultMap(getItem(position));
		String s;

		s = MapUtils.getMapString(mapRCM, TransmissionVars.FIELD_RCM_NAME, "");
		holder.tvName.setText(AndroidUtils.lineBreaker(s));

		long size = MapUtils.getMapLong(mapRCM, TransmissionVars.FIELD_RCM_SIZE, 0);
		s = size <= 0 ? "" : DisplayFormatters.formatByteCountToKiBEtc(size, true);
		holder.tvSize.setText(s);

		Context context = holder.itemView.getContext();
		if (context == null) {
			return;
		}

		long rank = MapUtils.getMapLong(mapRCM, TransmissionVars.FIELD_RCM_RANK, 0);
		long numSeeds = MapUtils.getMapLong(mapRCM,
				TransmissionVars.FIELD_RCM_SEEDS, -1);
		long numPeers = MapUtils.getMapLong(mapRCM,
				TransmissionVars.FIELD_RCM_PEERS, -1);
		StringBuffer sb = new StringBuffer();

		//sb.append("Discovery Strength: " + rank);

		holder.pbRank.setProgress((int) rank);

		long pubDate = MapUtils.getMapLong(mapRCM,
				TransmissionVars.FIELD_RCM_PUBLISHDATE, 0);
		if (pubDate > 0) {
			if (sb.length() > 0) {
				sb.append('\n');
			}
			CharSequence ago = DateUtils.getRelativeDateTimeString(context, pubDate,
					DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS * 2, 0);
			if (ago != null) {
				sb.append(context.getString(R.string.published_x_ago, ago.toString()));
			}
		}

		long lastSeenSecs = MapUtils.getMapLong(mapRCM,
				TransmissionVars.FIELD_RCM_LAST_SEEN_SECS, 0);
		if (lastSeenSecs > 0) {
			if (sb.length() > 0) {
				sb.append('\n');
			}
			CharSequence relTime = DateUtils.getRelativeDateTimeString(context,
					lastSeenSecs * 1000, DateUtils.MINUTE_IN_MILLIS,
					DateUtils.WEEK_IN_MILLIS * 2, 0);
			if (relTime != null) {
				sb.append(context.getString(R.string.last_seen_x, relTime.toString()));
			}
		}

		if (numSeeds >= 0 || numPeers >= 0) {
			if (sb.length() > 0) {
				sb.append('\n');
			}

			if (numSeeds >= 0) {
				sb.append(context.getString(R.string.x_seeds,
						DisplayFormatters.formatNumber(numSeeds)));
			}
			if (numPeers >= 0) {
				if (numSeeds >= 0) {
					sb.append(" \u2022 ");
				}
				sb.append(context.getString(R.string.x_peers,
						DisplayFormatters.formatNumber(numPeers)));
			}
		}

		holder.tvInfo.setText(sb);

		List<?> listTags = MapUtils.getMapList(mapRCM,
				TransmissionVars.FIELD_RCM_TAGS, Collections.EMPTY_LIST);
		if (listTags.size() == 0) {
			holder.tvTags.setVisibility(View.GONE);
		} else {
			SpanTags spanTag = new SpanTags(holder.tvTags, null);
			spanTag.setLinkTags(false);
			spanTag.setShowIcon(false);
			//noinspection unchecked,CastCanBeRemovedNarrowingVariableType
			spanTag.addTagNames((List<String>) listTags);
			spanTag.updateTags();

			holder.tvTags.setVisibility(View.VISIBLE);
		}
	}

	@Override
	public boolean areContentsTheSame(String oldItem, String newItem) {
		Map mapRCM = rs.getSearchResultMap(oldItem);
		long lastSetItemsOn = getLastSetItemsOn();
		long lastUpdated = MapUtils.getMapLong(mapRCM,
				TransmissionVars.FIELD_RCM_CHANGEDON, 0);
		return lastUpdated > lastSetItemsOn;
	}

	@Override
	public LetterFilter<String> createFilter() {
		return new RcmAdapterFilter(this, rs, mLock);
	}

	@Override
	public @NonNull RcmAdapterFilter getFilter() {
		return (RcmAdapterFilter) super.getFilter();
	}

	@Override
	public boolean setItems(List<String> values,
			SparseIntArray countsByViewType) {
		return setItems(values, countsByViewType, this);
	}

	@Override
	public Session getSession() {
		return sessionGetter.getSession();
	}
}
