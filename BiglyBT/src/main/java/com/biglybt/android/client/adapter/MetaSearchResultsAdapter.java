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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.biglybt.android.adapter.*;
import com.biglybt.android.client.*;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.spanbubbles.SpanTags;
import com.biglybt.android.util.MapUtils;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.Thunk;

import androidx.lifecycle.Lifecycle;
import android.content.Context;
import android.content.res.Resources;
import androidx.annotation.LayoutRes;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/**
 * Results Adapter for MetaSearch
 *
 * Created by TuxPaper on 4/22/16.
 */
public class MetaSearchResultsAdapter
	extends
	SortableRecyclerAdapter<MetaSearchResultsAdapter, MetaSearchResultsAdapter.MetaSearchViewResultsHolder, String>
	implements SessionAdapterFilterTalkback<String>,
	FlexibleRecyclerAdapter.SetItemsCallBack<String>
{
	private static final String TAG = "MetaSearchResultAdapter";

	private final Object mLock = new Object();

	class MetaSearchViewResultsHolder
		extends FlexibleRecyclerViewHolder<MetaSearchViewResultsHolder>
	{

		final TextView tvName;

		final TextView tvInfo;

		final ProgressBar pbRank;

		final TextView tvTags;

		final TextView tvTime;

		final TextView tvSize;

		final ImageButton ibDownload;

		final Button btnNew;

		MetaSearchViewResultsHolder(
				RecyclerSelectorInternal<MetaSearchViewResultsHolder> selector,
				View rowView) {
			super(selector, rowView);

			tvName = rowView.findViewById(R.id.ms_result_name);
			tvInfo = rowView.findViewById(R.id.ms_result_info);
			pbRank = rowView.findViewById(R.id.ms_result_rank);
			tvTags = rowView.findViewById(R.id.ms_result_tags);
			tvTime = rowView.findViewById(R.id.ms_result_time);
			tvSize = rowView.findViewById(R.id.ms_result_size);
			btnNew = rowView.findViewById(R.id.ms_new);
			if (btnNew != null) {
				btnNew.setOnClickListener(onNewClickedListener);
			}
			ibDownload = rowView.findViewById(R.id.ms_result_dl_button);
			if (ibDownload != null) {
				ibDownload.setOnClickListener(onDownloadClickedListener);
			}
			pbRank.setMax(1000);
		}
	}

	public interface MetaSearchSelectionListener
		extends
		FlexibleRecyclerSelectionListener<MetaSearchResultsAdapter, MetaSearchViewResultsHolder, String>,
		SessionGetter
	{
		Map getSearchResultMap(String hash);

		List<String> getSearchResultList();

		MetaSearchEnginesInfo getSearchEngineMap(String engineID);

		void downloadResult(String id);

		void newButtonClicked(String id, boolean currentlyNew);
	}

	@Thunk
	final MetaSearchSelectionListener rs;

	@Thunk
	final View.OnClickListener onDownloadClickedListener;

	@Thunk
	final View.OnClickListener onNewClickedListener;

	private final int rowLayoutRes;

	private final int rowLayoutRes_dpad;

	private final String ID_SORT_FILTER;

	public MetaSearchResultsAdapter(Lifecycle lifecycle,
			final MetaSearchSelectionListener rs, @LayoutRes int rowLayoutRes,
			@LayoutRes int rowLayoutRes_DPAD, String ID_SORT_FILTER) {
		super(TAG, lifecycle, rs);
		this.rs = rs;

		onDownloadClickedListener = v -> {
			ViewHolder viewHolder = getRecyclerView().findContainingViewHolder(v);

			if (viewHolder == null) {
				return;
			}
			int position = viewHolder.getAdapterPosition();
			String id = getItem(position);

			rs.downloadResult(id);
		};
		onNewClickedListener = v -> {
			ViewHolder viewHolder = getRecyclerView().findContainingViewHolder(v);

			if (viewHolder == null) {
				return;
			}
			int position = viewHolder.getAdapterPosition();
			String id = getItem(position);

			rs.newButtonClicked(id, v.getVisibility() != View.GONE);
		};
		this.rowLayoutRes = rowLayoutRes;
		rowLayoutRes_dpad = rowLayoutRes_DPAD;

		this.ID_SORT_FILTER = ID_SORT_FILTER;
	}

	@Override
	public void onBindFlexibleViewHolder(MetaSearchViewResultsHolder holder,
			int position) {
		String item = getItem(position);

		Resources res = holder.tvName.getResources();

		Map map = rs.getSearchResultMap(item);
		String s;

		holder.tvName.setText(AndroidUtils.lineBreaker(MapUtils.getMapString(map,
				TransmissionVars.FIELD_SEARCHRESULT_NAME, "")));

		long seeds = MapUtils.parseMapLong(map,
				TransmissionVars.FIELD_SEARCHRESULT_SEEDS, -1);
		long peers = MapUtils.parseMapLong(map,
				TransmissionVars.FIELD_SEARCHRESULT_PEERS, -1);
		long size = MapUtils.parseMapLong(map,
				TransmissionVars.FIELD_SEARCHRESULT_SIZE, 0);

		if (seeds < 0 && peers < 0) {
			holder.tvInfo.setText("");
		} else {
			s = res.getString(R.string.ms_results_info,
					seeds >= 0 ? DisplayFormatters.formatNumber(seeds) : "?",
					peers >= 0 ? DisplayFormatters.formatNumber(peers) : "??");

			holder.tvInfo.setText(s);
		}

		s = MapUtils.getMapString(map, TransmissionVars.FIELD_SEARCHRESULT_CATEGORY,
				"");
		if (s.length() == 0) {
			holder.tvTags.setText("");
		} else {
			SpanTags spanTags = new SpanTags();
			spanTags.init(holder.tvTags.getContext(), holder.tvTags, null);
			spanTags.addTagNames(Collections.singletonList(s));
			spanTags.setShowIcon(false);
			spanTags.updateTags();
		}

		s = buildPublishDateLine(res, map);

		List others = MapUtils.getMapList(map, "others", null);
		if (others != null) {
			for (Object other : others) {
				if (other instanceof Map) {
					s += "\n" + buildPublishDateLine(res, (Map) other);
					if (size <= 0) {
						size = MapUtils.parseMapLong((Map) other,
								TransmissionVars.FIELD_SEARCHRESULT_SIZE, 0);
					}
				}
			}
		}
		holder.tvTime.setText(s);

		if (size > 0) {
			holder.tvSize.setText(DisplayFormatters.formatByteCountToKiBEtc(size));
		} else {
			holder.tvSize.setText(R.string.ms_result_unknown_size);
		}

		double rank = MapUtils.parseMapDouble(map,
				TransmissionVars.FIELD_SEARCHRESULT_RANK, 0);
		holder.pbRank.setProgress((int) (rank * 1000));

		if (holder.btnNew != null) {
			holder.btnNew.setVisibility(MapUtils.getMapBoolean(map,
					TransmissionVars.FIELD_SUBSCRIPTION_RESULT_ISREAD, true)
							? View.INVISIBLE : View.VISIBLE);
		}
	}

	@Override
	public boolean areContentsTheSame(String oldItem, String newItem) {
		Map mapOld = rs.getSearchResultMap(oldItem);
		long lastUpdated = MapUtils.getMapLong(mapOld,
				TransmissionVars.FIELD_LAST_UPDATED, 0);
		long lastSetItemsOn = getLastSetItemsOn();
		return lastUpdated <= lastSetItemsOn;
	}

	private String buildPublishDateLine(Resources res, Map map) {
		String s;

		MetaSearchEnginesInfo engineInfo = rs.getSearchEngineMap(
				MapUtils.getMapString(map,
						TransmissionVars.FIELD_SEARCHRESULT_ENGINE_ID, null));

		long publishedOn = MapUtils.parseMapLong(map,
				TransmissionVars.FIELD_SEARCHRESULT_PUBLISHDATE, 0);
		if (publishedOn == 0) {
			s = res.getString(R.string.ms_result_unknown_age);
		} else {
			long diff = System.currentTimeMillis() - publishedOn;
			s = DisplayFormatters.prettyFormatTimeDiff(res, diff / 1000);
		}
		if (engineInfo == null) {
			return s;
		}
		return res.getString(R.string.ms_result_row_age, s, engineInfo.name);
	}

	@Override
	public MetaSearchViewResultsHolder onCreateFlexibleViewHolder(
			ViewGroup parent, int viewType) {

		LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);

		assert inflater != null;
		View rowView = inflater.inflate(
				AndroidUtils.usesNavigationControl() ? rowLayoutRes_dpad : rowLayoutRes,
				parent, false);

		return new MetaSearchViewResultsHolder(this, rowView);
	}

	@Override
	public LetterFilter<String> createFilter() {
		return new MetaSearchResultsAdapterFilter(ID_SORT_FILTER, this, rs, mLock);
	}

	@Override
	public boolean setItems(List<String> values,
			SparseIntArray countsByViewType) {
		return setItems(values, countsByViewType, this);
	}

	@Override
	public MetaSearchResultsAdapterFilter getFilter() {
		return (MetaSearchResultsAdapterFilter) super.getFilter();
	}

	@Override
	public Session getSession() {
		return rs.getSession();
	}
}
