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

package com.vuze.android.remote.adapter;

import java.util.*;

import com.squareup.picasso.Picasso;
import com.vuze.android.FlexibleRecyclerAdapter;
import com.vuze.android.FlexibleRecyclerSelectionListener;
import com.vuze.android.FlexibleRecyclerViewHolder;
import com.vuze.android.remote.*;
import com.vuze.util.*;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Results Adapter for MetaSearch
 *
 * Created by TuxPaper on 4/22/16.
 */
public class SubscriptionListAdapter
	extends
	FlexibleRecyclerAdapter<SubscriptionListAdapter.SubscriptionListResultsHolder, String>
	implements Filterable, AdapterFilterTalkbalk<String>,
	FlexibleRecyclerAdapter.SetItemsCallBack<String>
{
	private static final String TAG = "SubscriptionListAdapter";

	private static final boolean DEBUG = AndroidUtils.DEBUG;

	private final Object mLock = new Object();

	class SubscriptionListResultsHolder
		extends FlexibleRecyclerViewHolder
	{

		final TextView tvName;

		final TextView tvQueryInfo;

		final TextView tvCount;

		final TextView tvNewCount;

		final TextView tvError;

		final TextView tvLastUpdated;

		final ImageView iv;

		public SubscriptionListResultsHolder(RecyclerSelectorInternal selector,
				View rowView) {
			super(selector, rowView);

			tvName = (TextView) rowView.findViewById(R.id.sl_name);
			tvQueryInfo = (TextView) rowView.findViewById(R.id.sl_queryInfo);
			tvCount = (TextView) rowView.findViewById(R.id.sl_count);
			tvNewCount = (TextView) rowView.findViewById(R.id.sl_new_count);
			tvError = (TextView) rowView.findViewById(R.id.sl_error);
			tvLastUpdated = (TextView) rowView.findViewById(R.id.sl_lastchecked);
			iv = (ImageView) rowView.findViewById(R.id.sl_image);
		}
	}

	public interface SubscriptionSelectionListener
		extends FlexibleRecyclerSelectionListener<SubscriptionListAdapter, String>
	{
		long getLastReceivedOn();

		Map getSubscriptionMap(String key);

		List<String> getSubscriptionList();
	}

	@Thunk
	final Context context;

	@Thunk
	final SubscriptionSelectionListener rs;

	private final ComparatorMapFields sorter;

	private final SubscriptionListAdapterFilter filter;

	public SubscriptionListAdapter(Context context,
			final SubscriptionSelectionListener rs) {
		super(rs);
		this.context = context;
		this.rs = rs;

		filter = new SubscriptionListAdapterFilter(this, rs, mLock);

		sorter = new ComparatorMapFields() {

			public Throwable lastError;

			@Override
			public int reportError(Comparable<?> oLHS, Comparable<?> oRHS,
					Throwable t) {
				if (lastError != null) {
					if (t.getCause().equals(lastError.getCause())
							&& t.getMessage().equals(lastError.getMessage())) {
						return 0;
					}
				}
				lastError = t;
				Log.e(TAG, "SubListSort", t);
				VuzeEasyTracker.getInstance(
						SubscriptionListAdapter.this.context).logError(t);
				return 0;
			}

			@Override
			public Comparable modifyField(String fieldID, Map<?, ?> map,
					Comparable o) {
				if (fieldID.equals(
						TransmissionVars.FIELD_SUBSCRIPTION_ENGINE_LASTUPDATED)) {
					Map mapEngine = (Map) map.get(
							TransmissionVars.FIELD_SUBSCRIPTION_ENGINE);
					return (Comparable) mapEngine.get(fieldID);
				}
				return o;
			}

			@Override
			public Map<?, ?> mapGetter(Object o) {
				return SubscriptionListAdapter.this.rs.getSubscriptionMap((String) o);
			}
		};
	}

	@Override
	public void onBindFlexibleViewHolder(SubscriptionListResultsHolder holder,
			int position) {
		String item = getItem(position);

		Resources res = context.getResources();

		Map map = rs.getSubscriptionMap(item);
		Map mapEngine = MapUtils.getMapMap(map,
				TransmissionVars.FIELD_SUBSCRIPTION_ENGINE, null);
		String s;

		holder.tvName.setText(AndroidUtils.lineBreaker(MapUtils.getMapString(map,
				TransmissionVars.FIELD_SUBSCRIPTION_NAME, "")));
		holder.tvQueryInfo.setText(AndroidUtils.lineBreaker(MapUtils.getMapString(
				map, TransmissionVars.FIELD_SUBSCRIPTION_QUERY_KEY, "")));

		if (holder.tvLastUpdated != null) {
			long updatedOn = MapUtils.getMapLong(mapEngine,
					TransmissionVars.FIELD_SUBSCRIPTION_ENGINE_LASTUPDATED, 0);
			if (updatedOn > 0) {
				long diff = System.currentTimeMillis() - updatedOn;
				s = DisplayFormatters.prettyFormatTimeDiff(res, diff / 1000);
				holder.tvLastUpdated.setText(s);
			} else {
				holder.tvLastUpdated.setText("");
			}
		}

		if (holder.tvCount != null) {
			long count = MapUtils.getMapLong(map,
					TransmissionVars.FIELD_SUBSCRIPTION_RESULTS_COUNT, 0);
			holder.tvCount.setText(
					count <= 0 ? "" : DisplayFormatters.formatNumber(count) + " items");
		}

		if (holder.tvNewCount != null) {
			long count = MapUtils.getMapLong(map,
					TransmissionVars.FIELD_SUBSCRIPTION_NEWCOUNT, 0);
			holder.tvNewCount.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
			holder.tvNewCount.setText(
					count <= 0 ? "" : DisplayFormatters.formatNumber(count) + " new");
		}

		if (holder.iv != null) {
			Picasso picassoInstance = VuzeRemoteApp.getPicassoInstance();
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
		}

		if (holder.tvError != null) {
			holder.tvError.setText(MapUtils.getMapString(map, "error", ""));
		}

	}

	@Override
	public SubscriptionListResultsHolder onCreateFlexibleViewHolder(
			ViewGroup parent, int viewType) {

		LayoutInflater inflater = (LayoutInflater) context.getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);

		View rowView = inflater.inflate(R.layout.row_subscriptionlist_result,
				parent, false);

		return new SubscriptionListResultsHolder(this, rowView);
	}

	@Override
	public boolean areContentsTheSame(String oldItem, String newItem) {
		return rs.getLastReceivedOn() <= getLastSetItemsOn();
	}

	public List<String> doSort(List<String> items, boolean createNewList) {
		return doSort(items, sorter, createNewList);
	}

	@Override
	public void setItems(List<String> values) {
		setItems(values, this);
	}

	@Override
	public SubscriptionListAdapterFilter getFilter() {
		return filter;
	}

	@Override
	public void lettersUpdated(HashMap<String, Integer> mapLetterCount) {
	}

	public void setSort(String[] fieldIDs, Boolean[] sortOrderAsc) {
		synchronized (mLock) {
			Boolean[] order;
			if (sortOrderAsc == null) {
				order = new Boolean[fieldIDs.length];
				Arrays.fill(order, Boolean.FALSE);
			} else if (sortOrderAsc.length != fieldIDs.length) {
				order = new Boolean[fieldIDs.length];
				Arrays.fill(order, Boolean.FALSE);
				System.arraycopy(sortOrderAsc, 0, order, 0, sortOrderAsc.length);
			} else {
				order = sortOrderAsc;
			}
			sorter.setSortFields(fieldIDs, order);
		}
		doSort();
	}

	public void setSort(Comparator<? super Map<?, ?>> comparator) {
		synchronized (mLock) {
			sorter.setComparator(comparator);
		}
		doSort();
	}

	private void doSort() {
		if (!sorter.isValid()) {
			if (DEBUG) {
				Log.d(TAG, "doSort skipped: no comparator and no sort");
			}
			return;
		}
		if (DEBUG) {
			Log.d(TAG, "sort: " + sorter.toDebugString());
		}

		sortItems(sorter, this);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (filter != null) {
			filter.saveToBundle(outState);
		}
	}

	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState,
			RecyclerView rv) {
		super.onRestoreInstanceState(savedInstanceState, rv);
		if (filter != null) {
			filter.restoreFromBundle(savedInstanceState);
		}
	}

}
