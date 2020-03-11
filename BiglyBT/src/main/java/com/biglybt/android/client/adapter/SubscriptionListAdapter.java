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

import android.content.res.Resources;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.biglybt.android.adapter.*;
import com.biglybt.android.client.*;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.util.MapUtils;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.Thunk;
import com.squareup.picasso.Picasso;

import java.util.List;
import java.util.Map;

/**
 * Results Adapter for MetaSearch
 *
 * Created by TuxPaper on 4/22/16.
 */
public class SubscriptionListAdapter
	extends
	SortableRecyclerAdapter<SubscriptionListAdapter, SubscriptionListResultsHolder, String>
	implements SessionAdapterFilterTalkback<String>,
	FlexibleRecyclerAdapter.SetItemsCallBack<String>
{
	private static final String TAG = "SubscriptionListAdapter";

	private final Object mLock = new Object();

	public interface SubscriptionSelectionListener
		extends
		FlexibleRecyclerSelectionListener<SubscriptionListAdapter, SubscriptionListResultsHolder, String>,
		DelayedFilter.PerformingFilteringListener
	{
		long getLastReceivedOn();

		Map getSubscriptionMap(String key);

		List<String> getSubscriptionList();
	}

	@NonNull
	private final SessionGetter sessionGetter;

	@Thunk
	@NonNull
	final SubscriptionSelectionListener rs;

	public SubscriptionListAdapter(@NonNull SessionGetter sessionGetter,
			@NonNull SubscriptionSelectionListener rs) {
		super(TAG, rs);
		this.sessionGetter = sessionGetter;
		this.rs = rs;
	}

	@Override
	public void onBindFlexibleViewHolder(
			@NonNull SubscriptionListResultsHolder holder, int position) {
		String item = getItem(position);

		Resources res = AndroidUtils.requireResources(holder.itemView);

		Map map = rs.getSubscriptionMap(item);
		Map mapEngine = MapUtils.getMapMap(map,
				TransmissionVars.FIELD_SUBSCRIPTION_ENGINE, null);
		String s;

		holder.tvName.setText(AndroidUtils.lineBreaker(MapUtils.getMapString(map,
				TransmissionVars.FIELD_SUBSCRIPTION_NAME, "")));
		holder.tvQueryInfo.setText(AndroidUtils.lineBreaker(MapUtils.getMapString(
				map, TransmissionVars.FIELD_SUBSCRIPTION_QUERY_KEY, "")));

		long updatedOn = MapUtils.getMapLong(mapEngine,
				TransmissionVars.FIELD_SUBSCRIPTION_ENGINE_LASTUPDATED, 0);
		if (updatedOn > 0) {
			long diff = System.currentTimeMillis() - updatedOn;
			s = DisplayFormatters.prettyFormatTimeDiff(res, diff / 1000);
			holder.tvLastUpdated.setText(s);
		} else {
			holder.tvLastUpdated.setText("");
		}

		int count = MapUtils.getMapInt(map,
				TransmissionVars.FIELD_SUBSCRIPTION_RESULTS_COUNT, 0);
		s = count <= 0 ? "" : res.getQuantityString(R.plurals.x_items, count,
				DisplayFormatters.formatNumber(count));
		holder.tvCount.setText(s);

		int newCount = MapUtils.getMapInt(map,
				TransmissionVars.FIELD_SUBSCRIPTION_NEWCOUNT, 0);
		holder.tvNewCount.setVisibility(newCount > 0 ? View.VISIBLE : View.GONE);
		s = newCount <= 0 ? "" : res.getQuantityString(R.plurals.x_new, newCount,
				DisplayFormatters.formatNumber(newCount));
		holder.tvNewCount.setText(s);

		Picasso picassoInstance = BiglyBTApp.getPicassoInstance();
		picassoInstance.cancelRequest(holder.iv);
		String iconURL = MapUtils.getMapString(mapEngine,
				TransmissionVars.FIELD_SUBSCRIPTION_FAVICON, null);
		if (iconURL != null) {
			holder.iv.setVisibility(View.VISIBLE);
			String url = "http://search.vuze.com/xsearch/imageproxy.php?url="
					+ iconURL;
			picassoInstance.load(url).into(holder.iv);
		} else {
			holder.iv.setVisibility(View.GONE);
		}

		holder.tvError.setText(MapUtils.getMapString(map, "error", ""));
	}

	@NonNull
	@Override
	public SubscriptionListResultsHolder onCreateFlexibleViewHolder(
			@NonNull ViewGroup parent, @NonNull LayoutInflater inflater,
			int viewType) {

		View rowView = AndroidUtilsUI.requireInflate(inflater,
				R.layout.row_subscriptionlist_result, parent, false);

		return new SubscriptionListResultsHolder(this, rowView);
	}

	@Override
	public boolean areContentsTheSame(String oldItem, String newItem) {
		return rs.getLastReceivedOn() <= getLastSetItemsOn();
	}

	@Override
	public boolean setItems(List<String> values,
			SparseIntArray countsByViewType) {
		return setItems(values, countsByViewType, this);
	}

	@Override
	public LetterFilter<String> createFilter() {
		return new SubscriptionListAdapterFilter(this, rs, mLock);
	}

	@Override
	public Session getSession() {
		return sessionGetter.getSession();
	}

	@NonNull
	@Override
	public SubscriptionListAdapterFilter getFilter() {
		return (SubscriptionListAdapterFilter) super.getFilter();
	}
}