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

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.vuze.android.FlexibleRecyclerAdapter;
import com.vuze.android.FlexibleRecyclerSelectionListener;
import com.vuze.android.FlexibleRecyclerViewHolder;
import com.vuze.android.remote.*;
import com.vuze.android.remote.spanbubbles.SpanTags;
import com.vuze.util.DisplayFormatters;
import com.vuze.util.MapUtils;

import java.util.*;

/**
 * Results Adapter for MetaSearch
 *
 * Created by TuxPaper on 4/22/16.
 */
public class MetaSearchResultsAdapter
	extends
	FlexibleRecyclerAdapter<MetaSearchResultsAdapter.MetaSearchViewResultsHolder, String>
	implements Filterable, AdapterFilterTalkbalk<String>
{
	static final String TAG = "MetaSearchResultAdapter";

	private static final boolean DEBUG = AndroidUtils.DEBUG;

	public final Object mLock = new Object();

	class MetaSearchViewResultsHolder
		extends FlexibleRecyclerViewHolder
	{

		final TextView tvName;

		final TextView tvInfo;

		final ProgressBar pbRank;

		final TextView tvTags;

		final TextView tvTime;

		final TextView tvSize;

		final ImageButton ibDownload;

		final View viewNew;

		public MetaSearchViewResultsHolder(RecyclerSelectorInternal selector,
				View rowView) {
			super(selector, rowView);

			tvName = (TextView) rowView.findViewById(R.id.ms_result_name);
			tvInfo = (TextView) rowView.findViewById(R.id.ms_result_info);
			pbRank = (ProgressBar) rowView.findViewById(R.id.ms_result_rank);
			tvTags = (TextView) rowView.findViewById(R.id.ms_result_tags);
			tvTime = (TextView) rowView.findViewById(R.id.ms_result_time);
			tvSize = (TextView) rowView.findViewById(R.id.ms_result_size);
			viewNew = rowView.findViewById(R.id.ms_new);
			ibDownload = (ImageButton) rowView.findViewById(R.id.ms_result_dl_button);
			if (ibDownload != null) {
				ibDownload.setOnClickListener(onDownloadClickedListener);
			}
			pbRank.setMax(1000);
		}
	}

	public interface MetaSearchSelectionListener
		extends FlexibleRecyclerSelectionListener<MetaSearchResultsAdapter, String>
	{
		Map getSearchResultMap(String hash);

		List<String> getSearchResultList();

		MetaSearchEnginesAdapter.MetaSearchEnginesInfo getSearchEngineMap(
				String engineID);

		void downloadResult(String id);
	}

	/* @Thunk */ final Context context;

	/* @Thunk */ final MetaSearchSelectionListener rs;

	private final ComparatorMapFields sorter;

	/* @Thunk */ final View.OnClickListener onDownloadClickedListener;

	private final int rowLayoutRes;

	private final int rowLayoutRes_dpad;

	private MetaSearchResultsAdapterFilter filter;

	public MetaSearchResultsAdapter(Context context,
			final MetaSearchSelectionListener rs, @LayoutRes int rowLayoutRes,
			@LayoutRes int rowLayoutRes_DPAD) {
		super(rs);
		this.context = context;
		this.rs = rs;

		onDownloadClickedListener = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				RecyclerView.ViewHolder viewHolder = getRecyclerView().findContainingViewHolder(
						v);

				if (viewHolder == null) {
					return;
				}
				int position = viewHolder.getAdapterPosition();
				String id = getItem(position);

				rs.downloadResult(id);
			}
		};
		this.rowLayoutRes = rowLayoutRes;
		rowLayoutRes_dpad = rowLayoutRes_DPAD;

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
				Log.e(TAG, "MetaSort", t);
				VuzeEasyTracker.getInstance(
						MetaSearchResultsAdapter.this.context).logError(t);
				return 0;
			}

			@Override
			public Map<?, ?> mapGetter(Object o) {
				return MetaSearchResultsAdapter.this.rs.getSearchResultMap((String) o);
			}
		};
	}

	@Override
	public void onBindFlexibleViewHolder(MetaSearchViewResultsHolder holder,
			int position) {
		String item = getItem(position);

		Resources res = context.getResources();

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
			spanTags.init(context, null, holder.tvTags, null);
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

		if (holder.viewNew != null) {
			holder.viewNew.setVisibility(
					MapUtils.getMapBoolean(map, "subs_is_read", true) ? View.INVISIBLE
							: View.VISIBLE);
		}
	}

	private String buildPublishDateLine(Resources res, Map map) {
		String s;

		MetaSearchEnginesAdapter.MetaSearchEnginesInfo engineInfo = rs.getSearchEngineMap(
				MapUtils.getMapString(map, "engine-id", null));

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

		LayoutInflater inflater = (LayoutInflater) context.getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);

		View rowView = inflater.inflate(
				AndroidUtils.usesNavigationControl() ? rowLayoutRes_dpad : rowLayoutRes,
				parent, false);

		return new MetaSearchViewResultsHolder(this, rowView);
	}

	@Override
	public MetaSearchResultsAdapterFilter getFilter() {
		if (filter == null) {
			// xxx java.lang.RuntimeException: Can't create handler inside thread
			// that has not called Looper.prepare()
			filter = new MetaSearchResultsAdapterFilter(this, rs, mLock);
		}
		return filter;
	}

	@Override
	public List<String> doSort(List<String> items, boolean createNewList) {
		return doSort(items, sorter, createNewList);
	}

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

	public void doSort() {
		if (!sorter.isValid()) {
			if (DEBUG) {
				Log.d(TAG, "doSort skipped: no comparator and no sort");
			}
			return;
		}
		if (DEBUG) {
			Log.d(TAG, "sort: " + sorter.toDebugString());
		}

		sortItems(sorter);
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
