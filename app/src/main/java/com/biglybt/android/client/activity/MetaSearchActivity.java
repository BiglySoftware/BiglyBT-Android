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

package com.biglybt.android.client.activity;

import android.app.SearchManager;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.*;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;

import com.biglybt.android.adapter.FlexibleRecyclerAdapter;
import com.biglybt.android.adapter.FlexibleRecyclerSelectionListener;
import com.biglybt.android.adapter.SortableRecyclerAdapter;
import com.biglybt.android.client.*;
import com.biglybt.android.client.adapter.*;
import com.biglybt.android.client.dialog.DialogFragmentDateRange;
import com.biglybt.android.client.dialog.DialogFragmentSizeRange;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.Session_MetaSearch.MetaSearchEnginesInfo;
import com.biglybt.android.client.session.Session_MetaSearch.MetaSearchResultsListener;
import com.biglybt.android.client.session.Session_MetaSearch.SearchResult;
import com.biglybt.android.client.sidelist.SideActionSelectionListener;
import com.biglybt.android.client.sidelist.SideListActivity;
import com.biglybt.android.client.sidelist.SideListHelper;
import com.biglybt.android.client.spanbubbles.DrawableTag;
import com.biglybt.android.client.spanbubbles.SpanTags;
import com.biglybt.android.util.MapUtils;
import com.biglybt.android.widget.CustomToast;
import com.biglybt.android.widget.PreCachingLayoutManager;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.Thunk;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.BaseProgressIndicator;
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;

import java.util.*;

/**
 * Search Results from MetaSearch
 * <p/>
 * Created by TuxPaper on 4/22/16.
 */
public class MetaSearchActivity
	extends SideListActivity
	implements MetaSearchResultsListener,
	DialogFragmentSizeRange.SizeRangeDialogListener,
	DialogFragmentDateRange.DateRangeDialogListener
{
	private static final String TAG = "MetaSearch";

	public static final String ID_SORT_FILTER = "-ms";

	@Thunk
	static final int FILTER_INDEX_AGE = 0;

	@Thunk
	static final int FILTER_INDEX_SIZE = 1;

	@Thunk
	String searchString;

	private RecyclerView lvEngines;

	private MetaSearchEnginesAdapter metaSearchEnginesAdapter;

	@Thunk
	MetaSearchResultsAdapter metaSearchResultsAdapter;

	private TextView tvFilterAgeCurrent;

	private TextView tvFilterSizeCurrent;

	private TextView tvFilterCurrent;

	private TextView tvFilterTop;

	@Thunk
	TextView tvDrawerFilter;

	private SpanTags.SpanTagsListener listenerSpanTags;

	@Thunk
	SearchResult searchResult;

	@Thunk
	TextView tvHeader;

	private BaseProgressIndicator progressBar;

	private static final Comparator<MetaSearchEnginesInfo> metaSearchEnginesInfoComparator = (
			lhs, rhs) -> {
		if (lhs.uid.length() == 0) {
			return -1;
		}
		if (rhs.uid.length() == 0) {
			return 1;
		}
		return lhs.name.compareTo(rhs.name);
	};

	@Override
	protected void onCreateWithSession(@Nullable Bundle savedInstanceState) {
		Intent intent = getIntent();
		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
			searchString = intent.getStringExtra(SearchManager.QUERY);
		}

		int SHOW_SIDELIST_MINWIDTH_PX = getResources().getDimensionPixelSize(
				R.dimen.sidelist_search_drawer_until_screen);

		setContentView(AndroidUtils.isTV(this) ? R.layout.activity_metasearch_tv
				: AndroidUtilsUI.getScreenWidthPx(this) >= SHOW_SIDELIST_MINWIDTH_PX
						? R.layout.activity_metasearch_sb
						: R.layout.activity_metasearch_sb_drawer);
		setupActionBar();

		progressBar = findViewById(R.id.progress_spinner);

		tvFilterTop = findViewById(R.id.ms_top_filterarea);
		if (tvFilterTop != null) {

			tvFilterTop.setMovementMethod(LinkMovementMethod.getInstance());

			listenerSpanTags = new SpanTags.SpanTagsListener() {

				@Override
				public void tagClicked(int index, Map mapTag, String name) {
					{
						switch (index) {
							case FILTER_INDEX_AGE:
								ageRow_clicked(null);
								break;
							case FILTER_INDEX_SIZE:
								fileSizeRow_clicked(null);
								break;
						}
					}
				}

				@Override
				public int getTagState(int index, Map mapTag, String name) {
					boolean hasFilter = false;
					switch (index) {
						case FILTER_INDEX_AGE:
							hasFilter = metaSearchResultsAdapter.getFilter().hasPublishTimeFilter();
							break;
						case FILTER_INDEX_SIZE:
							hasFilter = metaSearchResultsAdapter.getFilter().hasSizeFilter();
							break;
					}

					return hasFilter ? SpanTags.TAG_STATE_SELECTED
							: SpanTags.TAG_STATE_UNSELECTED;
				}
			};
		}
		tvDrawerFilter = findViewById(R.id.sidelist_topinfo);
		tvHeader = findViewById(R.id.ms_header);

		View viewFileSizeRow = findViewById(R.id.sidefilter_filesize);
		if (viewFileSizeRow != null) {
			viewFileSizeRow.setOnClickListener(this::fileSizeRow_clicked);
		}

		View viewAgeRow = findViewById(R.id.sidefilter_age_row);
		if (viewAgeRow != null) {
			viewAgeRow.setOnClickListener(this::ageRow_clicked);
		}

		MetaSearchResultsAdapter.MetaSearchSelectionListener metaSearchSelectionListener = new MetaSearchResultsAdapter.MetaSearchSelectionListener() {

			@Override
			public Session getSession() {
				return MetaSearchActivity.this.getSession();
			}

			@Override
			public void onItemClick(MetaSearchResultsAdapter adapter, int position) {
				if (!AndroidUtils.usesNavigationControl()) {
					// touch users have their own download button
					// nav pad people have click to download, hold-click for menu
					return;
				}

				String id = adapter.getItem(position);
				downloadResult(id);
			}

			@Override
			public void newButtonClicked(String id, boolean currentlyNew) {
			}

			@Override
			public boolean onItemLongClick(MetaSearchResultsAdapter adapter,
					int position) {
				return false;
			}

			@Override
			public void onItemSelected(MetaSearchResultsAdapter adapter, int position,
					boolean isChecked) {

			}

			@Override
			public void onItemCheckedChanged(MetaSearchResultsAdapter adapter,
					String item, boolean isChecked) {

			}

			@Override
			public Map getSearchResultMap(String id) {
				return searchResult == null ? null : searchResult.mapResults.get(id);
			}

			@Override
			public List<String> getSearchResultList() {
				return searchResult == null ? new ArrayList<>()
						: new ArrayList<>(searchResult.mapResults.keySet());
			}

			@Override
			public MetaSearchEnginesInfo getSearchEngineMap(String engineID) {
				return searchResult == null ? null
						: searchResult.mapEngines.get(engineID);
			}

			@Override
			public void downloadResult(String id) {
				Map map = getSearchResultMap(id);
				if (map == null) {
					return;
				}

				Resources resources = getResources();

				final String name = MapUtils.getMapString(map,
						TransmissionVars.FIELD_SEARCHRESULT_NAME, "torrent");

				String engineID = MapUtils.getMapString(map,
						TransmissionVars.FIELD_SEARCHRESULT_ENGINE_ID, null);

				MetaSearchEnginesInfo engineInfo = searchResult.mapEngines.get(
						engineID);
				String engineName = engineInfo == null ? "default" : engineInfo.name;

				final List<String> listNames = new ArrayList<>();
				final List<String> listURLs = new ArrayList<>();
				boolean gotHash = false;

				String url = MapUtils.getMapString(map,
						TransmissionVars.FIELD_SEARCHRESULT_URL, null);
				if (url != null && url.length() > 0) {
					String s;
					if (url.startsWith("magnet:")) {
						s = resources.getString(R.string.download_source_item_from_hash);
						gotHash = true;
					} else {
						s = resources.getString(R.string.download_source_item_from_url,
								engineName);
					}
					listNames.add(s);
					listURLs.add(url);
				}

				if (!gotHash) {
					String hash = MapUtils.getMapString(map,
							TransmissionVars.FIELD_SEARCHRESULT_HASH, null);

					if (hash != null && hash.length() > 0) {
						String s = resources.getString(
								R.string.download_source_item_from_hash);
						listNames.add(s);
						listURLs.add(hash);
						gotHash = true;
					}
				}

				List<Map<String, Object>> others = MapUtils.getMapList(map, "others",
						null);

				if (others != null && others.size() > 0) {
					for (Map<String, Object> other : others) {
						engineID = MapUtils.getMapString(other,
								TransmissionVars.FIELD_SEARCHRESULT_ENGINE_ID, null);

						engineInfo = searchResult.mapEngines.get(engineID);
						engineName = engineInfo == null ? "default" : engineInfo.name;

						url = MapUtils.getMapString(other,
								TransmissionVars.FIELD_SEARCHRESULT_URL, null);
						if (url != null && url.length() > 0) {
							String s = resources.getString(
									R.string.download_source_item_from_url, engineName);
							listNames.add(s);
							listURLs.add(url);
						}

						if (!gotHash) {
							String hash = MapUtils.getMapString(other,
									TransmissionVars.FIELD_SEARCHRESULT_HASH, null);

							if (hash != null && hash.length() > 0) {
								String s = resources.getString(
										R.string.download_source_item_from_hash);
								listNames.add(s);
								listURLs.add(hash);
								gotHash = true;
							}
						}
					}
				}

				if (listNames.size() == 0) {
					CustomToast.showText("Error getting Search Result URL",
							Toast.LENGTH_SHORT);
				} else if (listNames.size() > 1) {
					String[] items = listNames.toArray(new String[0]);

					AlertDialog.Builder build = new MaterialAlertDialogBuilder(
							MetaSearchActivity.this);
					build.setTitle(R.string.select_download_source);
					build.setItems(items, (dialog, which) -> {
						if (which >= 0 && which < listURLs.size()) {
							String url1 = listURLs.get(which);
							session.torrent.openTorrent(MetaSearchActivity.this, url1, name);
						}

					});

					build.show();
				} else {
					session.torrent.openTorrent(MetaSearchActivity.this, listURLs.get(0),
							name);
				}
			}

		};
		metaSearchResultsAdapter = new MetaSearchResultsAdapter(
				metaSearchSelectionListener, R.layout.row_ms_result,
				R.layout.row_ms_result_dpad, ID_SORT_FILTER);
		metaSearchResultsAdapter.addOnSetItemsCompleteListener(
				adapter -> updateHeader());
		metaSearchResultsAdapter.setMultiCheckModeAllowed(false);
		metaSearchResultsAdapter.setCheckOnSelectedAfterMS(50);
		RecyclerView lvResults = findViewById(R.id.ms_list_results);
		lvResults.setAdapter(metaSearchResultsAdapter);
		PreCachingLayoutManager layoutManager = new PreCachingLayoutManager(this);
		lvResults.setLayoutManager(layoutManager);

		if (AndroidUtils.isTV(this)) {
			if (lvResults instanceof FastScrollRecyclerView) {
				((FastScrollRecyclerView) lvResults).setFastScrollEnabled(false);
			}
			layoutManager.setFixedVerticalHeight(AndroidUtilsUI.dpToPx(48));
			lvResults.setVerticalFadingEdgeEnabled(true);
			lvResults.setFadingEdgeLength(AndroidUtilsUI.dpToPx((int) (48 * 1.5)));
		}
	}

	@Override
	protected void onPause() {
		session.metasearch.removeListener(searchString, this);

		if (metaSearchResultsAdapter != null && metaSearchEnginesAdapter != null
				&& searchResult != null) {
			Bundle bundle = new Bundle();
			metaSearchResultsAdapter.onSaveInstanceState(bundle);
			metaSearchEnginesAdapter.onSaveInstanceState(bundle);
			searchResult.mapExtras.put("InstanceState", bundle);
			searchResult.touch();
		}

		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();

		session.metasearch.search(searchString, this);
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		if (metaSearchResultsAdapter != null) {
			metaSearchResultsAdapter.onSaveInstanceState(outState);
		}
		if (metaSearchEnginesAdapter != null) {
			metaSearchEnginesAdapter.onSaveInstanceState(outState);
		}
	}

	@Override
	public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		if (metaSearchResultsAdapter != null) {
			metaSearchResultsAdapter.onRestoreInstanceState(savedInstanceState);
		}
		if (metaSearchEnginesAdapter != null) {
			metaSearchEnginesAdapter.onRestoreInstanceState(savedInstanceState);
		}
		updateFilterTexts();
	}

	private void setupActionBar() {
		Toolbar toolBar = findViewById(R.id.actionbar);
		if (toolBar != null) {
			if (AndroidUtils.isTV(this)) {
				toolBar.setVisibility(View.GONE);
				return;
			}
			setSupportActionBar(toolBar);
		}

		// enable ActionBar app icon to behave as action to toggle nav drawer
		ActionBar actionBar = getSupportActionBar();
		if (actionBar == null) {
			return;
		}

		RemoteProfile remoteProfile = session.getRemoteProfile();
		actionBar.setTitle(remoteProfile.getNick());
		actionBar.setSubtitle(searchString);

		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeButtonEnabled(true);
	}

	@Override
	public void onDrawerOpened(@NonNull View view) {
		super.onDrawerOpened(view);
		updateFilterTexts();
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		if (onOptionsItemSelected_drawer(item)) {
			return true;
		}
		int itemId = item.getItemId();
		if (itemId == android.R.id.home) {
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onMetaSearchGotResults(SearchResult searchResult) {
		OffThread.runOnUIThread(this, false, (a) -> {
			if (progressBar != null) {
				if (searchResult.complete) {
					AndroidUtilsUI.hideProgressBar(progressBar);
				} else {
					progressBar.show();
				}
			}
			ProgressBar enginesPB = findViewById(R.id.metasearch_engines_spinner);
			if (enginesPB != null) {
				enginesPB.setVisibility(
						searchResult.complete ? View.GONE : View.VISIBLE);
			}

			if (metaSearchEnginesAdapter != null) {
				metaSearchEnginesAdapter.notifyDataSetInvalidated();
			}

			metaSearchResultsAdapter.getFilter().refilter(false);
		});
	}

	@Thunk
	void updateHeader() {
		runOnUiThread(() -> {
			if (isFinishing()) {
				return;
			}

			ActionBar ab = getSupportActionBar();

			int filteredCount = metaSearchResultsAdapter.getItemCount();
			int count = searchResult == null ? 0 : searchResult.mapResults.size();
			String countString = DisplayFormatters.formatNumber(count);

			String sResultsCount;
			if (count == filteredCount) {
				sResultsCount = getResources().getQuantityString(
						R.plurals.ms_results_header, count, countString, searchString);
			} else {
				sResultsCount = getResources().getQuantityString(
						R.plurals.ms_filtered_results_header, count,
						DisplayFormatters.formatNumber(filteredCount), countString,
						searchString);
			}

			Spanned span = AndroidUtils.fromHTML(sResultsCount);

			if (tvDrawerFilter != null) {
				tvDrawerFilter.setText(span);
			}
			if (tvHeader != null) {
				tvHeader.setText(span);
			}

			if (ab != null) {
				ab.setSubtitle(span);
			}
		});
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_PROG_GREEN) {
			lvEngines.requestFocus();
		} else if (keyCode == KeyEvent.KEYCODE_PROG_RED) {
			Log.d(TAG, "onKeyUp: Engine: " + getCurrentFocus());
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public void onMetaSearchGotEngines(SearchResult searchResult) {
		if (isFinishing()) {
			session.metasearch.removeListener(searchString, this);
		}
		this.searchResult = searchResult;

		updateEngineList();

		if (metaSearchResultsAdapter != null && metaSearchEnginesAdapter != null) {
			Object instanceState = searchResult.mapExtras.get("InstanceState");
			if (instanceState instanceof Bundle) {
				// TODO: Restore sort order
				metaSearchResultsAdapter.onRestoreInstanceState((Bundle) instanceState);
				metaSearchEnginesAdapter.onRestoreInstanceState((Bundle) instanceState);
			}
		}
	}

	private void updateEngineList() {
		if (searchResult == null) {
			if (metaSearchEnginesAdapter != null) {
				metaSearchEnginesAdapter.removeAllItems();
			}
			return;
		}

		Collection<MetaSearchEnginesInfo> itemsCollection = searchResult.mapEngines.values();
		MetaSearchEnginesInfo[] items = itemsCollection.toArray(
				new MetaSearchEnginesInfo[0]);
		List<MetaSearchEnginesInfo> enginesList = Arrays.asList(items);
		Arrays.sort(items, metaSearchEnginesInfoComparator);

		if (metaSearchEnginesAdapter != null) {
			metaSearchEnginesAdapter.setItems(enginesList, null,
					(oldItem, newItem) -> {
						// MetaSearchEnginesAdapter.refreshItem handles notifyItemChanged
						return false;
					});
		}
	}

	@Override
	public SortableRecyclerAdapter getMainAdapter() {
		return metaSearchResultsAdapter;
	}

	@Override
	public SideActionSelectionListener getSideActionSelectionListener() {
		return null;
	}

	@Override
	public void onSideListHelperCreated(SideListHelper sideListHelper) {
		super.onSideListHelperCreated(sideListHelper);
		sideListHelper.addEntry("engine", AndroidUtilsUI.requireContentView(this),
				R.id.sideengine_header, R.id.sideengine_list);
	}

	@Override
	public boolean showFilterEntry() {
		return true;
	}

	private boolean setupSideEngines() {
		if (lvEngines != null) {
			return false;
		}
		lvEngines = findViewById(R.id.sideengine_list);

		if (lvEngines == null) {
			return false;
		}

		metaSearchEnginesAdapter = new MetaSearchEnginesAdapter(
				new FlexibleRecyclerSelectionListener<MetaSearchEnginesAdapter, MetaSearchEnginesHolder, MetaSearchEnginesInfo>() {
					@Override
					public void onItemClick(MetaSearchEnginesAdapter adapter,
							int position) {

					}

					@Override
					public boolean onItemLongClick(MetaSearchEnginesAdapter adapter,
							int position) {
						return false;
					}

					@Override
					public void onItemSelected(MetaSearchEnginesAdapter adapter,
							int position, boolean isChecked) {

					}

					@Override
					public void onItemCheckedChanged(MetaSearchEnginesAdapter adapter,
							MetaSearchEnginesInfo item, boolean isChecked) {

						if (isChecked) {
							if (item.uid.length() == 0) {
								if (adapter.getCheckedItemCount() > 1) {
									adapter.clearChecked();
									adapter.setItemChecked(item, true);
									return;
								}
							} else if (adapter.isItemChecked(0)) {
								adapter.setItemChecked(0, false);
								return;
							}
						}

						MetaSearchResultsAdapterFilter filter = metaSearchResultsAdapter.getFilter();

						List<MetaSearchEnginesInfo> checkedItems = adapter.getCheckedItems();

						List<String> engineIDs = new ArrayList<>();
						for (MetaSearchEnginesInfo info : checkedItems) {
							engineIDs.add(info.uid);
						}

						filter.setEngines(engineIDs);
						filter.refilter(false);
					}
				});

		metaSearchEnginesAdapter.setMultiCheckModeAllowed(true);
		metaSearchEnginesAdapter.setMultiCheckMode(true);
		metaSearchEnginesAdapter.setAlwaysMultiSelectMode(true);
		metaSearchEnginesAdapter.setCheckOnSelectedAfterMS(
				FlexibleRecyclerAdapter.NO_CHECK_ON_SELECTED);
		lvEngines.setAdapter(metaSearchEnginesAdapter);

		updateEngineList();

		return true;
	}

	@Override
	public void onSideListHelperVisibleSetup(View view) {
		super.onSideListHelperVisibleSetup(view);
		if (setupSideEngines()) {
			lvEngines.setLayoutManager(new PreCachingLayoutManager(this));
		}

		tvFilterAgeCurrent = view.findViewById(R.id.ms_filter_age_current);
		tvFilterSizeCurrent = view.findViewById(R.id.ms_filter_size_current);
		tvFilterCurrent = view.findViewById(R.id.sidefilter_current);

		updateFilterTexts();
	}

	@Thunk
	void updateFilterTexts() {
		OffThread.runOnUIThread(this::ui_updateFilterTexts);
	}

	@UiThread
	private void ui_updateFilterTexts() {
		if (metaSearchResultsAdapter == null) {
			return;
		}

		MetaSearchResultsAdapterFilter filter = metaSearchResultsAdapter.getFilter();

		long[] timeRange = filter.getFilterTimes();
		String sCombined = "";

		Resources resources = getResources();

		String filterTimeText;
		String filterSizeText;

		if (timeRange[0] <= 0 && timeRange[1] <= 0) {
			filterTimeText = resources.getString(R.string.filter_time_none);
		} else {
			if (timeRange[1] > 0 && timeRange[0] > 0) {
				filterTimeText = DateUtils.formatDateRange(this, timeRange[0],
						timeRange[1],
						DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_ABBREV_MONTH);
			} else if (timeRange[0] > 0) {
				filterTimeText = resources.getString(R.string.filter_date_starting,
						DateUtils.getRelativeTimeSpanString(this, timeRange[0], true));
			} else {
				filterTimeText = resources.getString(R.string.filter_date_until,
						DateUtils.getRelativeTimeSpanString(timeRange[1],
								System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS));
			}
			sCombined += filterTimeText;
		}

		if (tvFilterAgeCurrent != null) {
			tvFilterAgeCurrent.setText(filterTimeText);
		}

		long[] sizeRange = filter.getFilterSizes();
		if (sizeRange[0] <= 0 && sizeRange[1] <= 0) {
			filterSizeText = resources.getString(R.string.filter_size_none);
		} else {
			if (sizeRange[0] > 0 && sizeRange[1] > 0) {
				filterSizeText = resources.getString(R.string.filter_size,
						DisplayFormatters.formatByteCountToKiBEtc(sizeRange[0], true),
						DisplayFormatters.formatByteCountToKiBEtc(sizeRange[1], true));
			} else if (sizeRange[1] > 0) {
				filterSizeText = resources.getString(R.string.filter_size_upto,
						DisplayFormatters.formatByteCountToKiBEtc(sizeRange[1], true));
			} else {
				filterSizeText = resources.getString(R.string.filter_size_atleast,
						DisplayFormatters.formatByteCountToKiBEtc(sizeRange[0], true));
			}
			if (sCombined.length() > 0) {
				sCombined += "\n";
			}
			sCombined += filterSizeText;
		}

		if (tvFilterSizeCurrent != null) {
			tvFilterSizeCurrent.setText(filterSizeText);
		}

		if (tvFilterCurrent != null) {
			tvFilterCurrent.setText(sCombined);
		}

		if (tvFilterTop != null) {
			SpanTags spanTag = new SpanTags(tvFilterTop, listenerSpanTags);
			spanTag.setShowIcon(false);
			spanTag.setLineSpaceExtra(AndroidUtilsUI.dpToPx(8));
			List<Map<?, ?>> listFilters = new ArrayList<>();
			listFilters.add(makeFilterListMap(FILTER_INDEX_AGE, filterTimeText,
					filter.hasPublishTimeFilter()));
			listFilters.add(makeFilterListMap(FILTER_INDEX_SIZE, filterSizeText,
					filter.hasSizeFilter()));
			spanTag.setTagMaps(listFilters);
			spanTag.updateTags();
		}

		updateHeader();
	}

	private static HashMap<Object, Object> makeFilterListMap(long uid,
			String name, boolean enabled) {
		HashMap<Object, Object> map = new HashMap<>();
		map.put(TransmissionVars.FIELD_TAG_UID, uid);
		map.put(TransmissionVars.FIELD_TAG_NAME, name);
		map.put(DrawableTag.KEY_ROUNDED, true);
		map.put(TransmissionVars.FIELD_TAG_COLOR,
				enabled ? 0xFF000000 : 0x80000000);
		map.put(DrawableTag.KEY_FILL_COLOR, enabled ? 0xFF80ffff : 0x4080ffff);
		return map;
	}

	@SuppressWarnings("UnusedParameters")
	@Thunk
	void fileSizeRow_clicked(@Nullable View view) {
		if (metaSearchResultsAdapter == null) {
			return;
		}

		MetaSearchResultsAdapterFilter filter = metaSearchResultsAdapter.getFilter();
		long[] sizeRange = filter.getFilterSizes();

		DialogFragmentSizeRange.openDialog(getSupportFragmentManager(), null, null,
				getRemoteProfileID(), searchResult == null ? -1 : searchResult.maxSize,
				sizeRange[0], sizeRange[1]);
	}

	@SuppressWarnings("UnusedParameters")
	@Thunk
	void ageRow_clicked(@Nullable View view) {
		if (metaSearchResultsAdapter == null) {
			return;
		}

		MetaSearchResultsAdapterFilter filter = metaSearchResultsAdapter.getFilter();
		long[] timeRange = filter.getFilterTimes();

		DialogFragmentDateRange.openDialog(getSupportFragmentManager(), null,
				getRemoteProfileID(), timeRange[0], timeRange[1]);
	}

	@Override
	public void onSizeRangeChanged(String callbackID, long start, long end) {
		if (metaSearchResultsAdapter == null) {
			return;
		}
		MetaSearchResultsAdapterFilter filter = metaSearchResultsAdapter.getFilter();
		filter.setFilterSizes(start, end);
		filter.refilter(false);
		updateFilterTexts();
	}

	@WorkerThread
	@Override
	public void onDateRangeChanged(String callbackID, long start, long end) {
		if (metaSearchResultsAdapter == null) {
			return;
		}
		MetaSearchResultsAdapterFilter filter = metaSearchResultsAdapter.getFilter();
		filter.setFilterTimes(start, end);
		filter.refilter(false);
		updateFilterTexts();
	}
}
