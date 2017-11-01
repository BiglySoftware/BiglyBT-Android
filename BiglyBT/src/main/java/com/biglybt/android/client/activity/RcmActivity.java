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

import java.util.*;

import com.biglybt.android.SortDefinition;
import com.biglybt.android.client.*;
import com.biglybt.android.client.adapter.RcmAdapter;
import com.biglybt.android.client.adapter.RcmAdapterFilter;
import com.biglybt.android.client.adapter.SortableAdapter;
import com.biglybt.android.client.dialog.*;
import com.biglybt.android.client.dialog.DialogFragmentNumberPicker.NumberPickerBuilder;
import com.biglybt.android.client.rpc.RPCSupports;
import com.biglybt.android.client.session.RefreshTriggerListener;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.client.session.Session_RCM;
import com.biglybt.android.client.spanbubbles.DrawableTag;
import com.biglybt.android.client.spanbubbles.SpanBubbles;
import com.biglybt.android.client.spanbubbles.SpanTags;
import com.biglybt.android.widget.PreCachingLayoutManager;
import com.biglybt.android.widget.SwipeRefreshLayoutExtra;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.Thunk;
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;

import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

/**
 * Swarm Discoveries activity.
 */
public class RcmActivity
	extends DrawerActivity
	implements RefreshTriggerListener,
	DialogFragmentRcmAuth.DialogFragmentRcmAuthListener,
	SwipeRefreshLayoutExtra.OnExtraViewVisibilityChangeListener,
	SideListHelper.SideSortAPI, DialogFragmentDateRange.DateRangeDialogListener,
	DialogFragmentSizeRange.SizeRangeDialogListener,
	DialogFragmentNumberPicker.NumberPickerDialogListener
{

	private static final String TAG = "RCM";

	private static final String ID_SORT_FILTER = "-rcm";

	private static final String FILTER_PREF_LAST_SEEN = ID_SORT_FILTER
			+ "-lastSeen";

	private static final String FILTER_PREF_MINRANK = ID_SORT_FILTER + "-minRank";

	private static final String FILTER_PREF_MINSEEDS = ID_SORT_FILTER
			+ "-minSeeds";

	@Thunk
	static final int FILTER_INDEX_AGE = 0;

	@Thunk
	static final int FILTER_INDEX_SIZE = 1;

	@Thunk
	static final int FILTER_INDEX_LAST_SEEN = 2;

	@Thunk
	static final int FILTER_INDEX_RANK = 3;

	@Thunk
	static final int FILTER_INDEX_SEEDS = 4;

	private static final String SAVESTATE_RCM_GOT_UNTIL = "rcmGotUntil";

	private static final String SAVESTATE_LIST = "list";

	private SparseArray<SortDefinition> sortDefinitions;

	private RecyclerView listview;

	@Thunk
	long lastUpdated;

	@Thunk
	RcmAdapter adapter;

	@Thunk
	long rcmGotUntil;

	@Thunk
	boolean enabled;

	private boolean supportsRCM;

	@Thunk
	SwipeRefreshLayoutExtra swipeRefresh;

	@Thunk
	Handler pullRefreshHandler;

	@Thunk
	Map<String, Map<?, ?>> mapResults = new HashMap<>();

	@Thunk
	SideListHelper sideListHelper;

	private final Object mLock = new Object();

	private TextView tvFilterAgeCurrent;

	private TextView tvFilterSizeCurrent;

	private TextView tvFilterLastSeenCurrent;

	private TextView tvFilterMinRankCurrent;

	private TextView tvFilterMinSeedsCurrent;

	private TextView tvFilterCurrent;

	private TextView tvFilterTop;

	private long maxSize;

	private TextView tvDrawerFilter;

	private SpanTags.SpanTagsListener listenerSpanTags = null;

	private TextView tvHeader;

	private int defaultSortID;

	@Override
	protected String getTag() {
		return TAG;
	}

	@Override
	protected void onCreateWithSession(final Bundle savedInstanceState) {
		supportsRCM = session.getSupports(RPCSupports.SUPPORTS_RCM);
		int SHOW_SIDELIST_MINWIDTH_PX = getResources().getDimensionPixelSize(
				R.dimen.sidelist_rcm_drawer_until_screen);

		int contentViewID = supportsRCM
				? (AndroidUtils.isTV() ? R.layout.activity_rcm_tv
						: AndroidUtilsUI.getScreenWidthPx(this) >= SHOW_SIDELIST_MINWIDTH_PX
								? R.layout.activity_rcm : R.layout.activity_rcm_drawer)
				: R.layout.activity_rcm_na;
		setContentView(contentViewID);

		if (supportsRCM) {
			rpcRefreshingChanged(true);
			updateFirstLoadText(R.string.checking_rcm);
			session.rcm.checkEnabled(new Session_RCM.RcmCheckListener() {
				@Override
				public void rcmCheckEnabled(boolean enabled) {
					rpcRefreshingChanged(false);
					RcmActivity.this.enabled = enabled;

					if (enabled) {
						if (savedInstanceState == null
								|| savedInstanceState.getString(SAVESTATE_LIST) == null) {
							triggerRefresh();
						}
						AnalyticsTracker.getInstance().sendEvent("RCM", "Show", null, null);
					} else {
						if (isFinishing()) {
							// Hopefully fix IllegalStateException in v2.1
							return;
						}
						DialogFragmentRcmAuth.openDialog(RcmActivity.this, remoteProfileID);
					}
				}

				@Override
				public void rcmCheckEnabledError(Exception e, String message) {
					rpcRefreshingChanged(false);
					if (message != null) {
						updateFirstLoadText(R.string.first_load_error, message);
					} else {
						updateFirstLoadText(R.string.first_load_error,
								AndroidUtils.getCausesMesssages(e));
					}

				}
			});
		}

		setupActionBar();

		if (supportsRCM) {
			buildSortDefinitions();
			CollapsingToolbarLayout collapsingToolbarLayout = findViewById(
					R.id.collapsing_toolbar);

			if (collapsingToolbarLayout != null) {
				AppBarLayout.LayoutParams params = (AppBarLayout.LayoutParams) collapsingToolbarLayout.getLayoutParams();
				if (AndroidUtilsUI.getScreenHeightDp(this) >= 1000) {
					params.setScrollFlags(0);
				} else {
					params.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
							| AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED);
				}
			}

			setupListView();

			onCreate_setupDrawer();

			setupRCMViews();

		} else {
			TextView tvNA = findViewById(R.id.rcm_na);

			String text = getResources().getString(R.string.rcm_na,
					getResources().getString(R.string.title_activity_rcm));

			SpanBubbles.setSpanBubbles(tvNA, text, "|",
					AndroidUtilsUI.getStyleColor(this, R.attr.login_text_color),
					AndroidUtilsUI.getStyleColor(this, R.attr.login_textbubble_color),
					AndroidUtilsUI.getStyleColor(this, R.attr.login_text_color), null);
		}
	}

	private void setupRCMViews() {

		tvHeader = findViewById(R.id.rcm_header);
		if (tvHeader != null) {
			tvHeader.setText(R.string.title_activity_rcm);
		}

		tvFilterTop = findViewById(R.id.rcm_top_filterarea);

		if (tvFilterTop != null) {

			tvFilterTop.setMovementMethod(LinkMovementMethod.getInstance());

			listenerSpanTags = new SpanTags.SpanTagsListener() {

				@Override
				public void tagClicked(int index, Map mapTag, String name) {
					{
						Object uid = mapTag.get("uid");
						if (uid == null) {
							return;
						}
						int whichFilter = ((Number) uid).intValue();
						switch (whichFilter) {
							case FILTER_INDEX_AGE:
								ageRow_clicked(null);
								break;
							case FILTER_INDEX_LAST_SEEN:
								lastSeenRow_clicked(null);
								break;
							case FILTER_INDEX_RANK:
								minRankRow_clicked(null);
								break;
							case FILTER_INDEX_SEEDS:
								minSeedsRow_clicked(null);
								break;
							case FILTER_INDEX_SIZE:
								fileSizeRow_clicked(null);
								break;
						}
					}
				}

				@Override
				public int getTagState(int index, Map mapTag, String name) {
					return SpanTags.TAG_STATE_SELECTED;
				}
			};
		}
		tvDrawerFilter = findViewById(R.id.sidelist_topinfo);

		View viewFileSizeRow = findViewById(R.id.sidefilter_filesize);
		if (viewFileSizeRow != null) {
			viewFileSizeRow.setOnKeyListener(new View.OnKeyListener() {
				@Override
				public boolean onKey(View v, int keyCode, KeyEvent event) {
					return handleFileSizeRowKeyListener(keyCode, event);
				}
			});
		}

		View viewAgeRow = findViewById(R.id.sidefilter_age_row);
		if (viewAgeRow != null) {
			viewAgeRow.setOnKeyListener(new View.OnKeyListener() {
				@Override
				public boolean onKey(View v, int keyCode, KeyEvent event) {
					return handleAgeRowKeyListener(keyCode, event);
				}
			});
		}

		View viewLastSeenRow = findViewById(R.id.sidefilter_lastseen_row);
		if (viewLastSeenRow != null) {
			viewLastSeenRow.setOnKeyListener(new View.OnKeyListener() {
				@Override
				public boolean onKey(View v, int keyCode, KeyEvent event) {
					return handleLastSeenRowKeyListener(keyCode, event);
				}
			});
		}

		View viewMinSeedsRow = findViewById(R.id.sidefilter_minseeds_row);
		if (viewMinSeedsRow != null) {
			viewMinSeedsRow.setOnKeyListener(new View.OnKeyListener() {
				@Override
				public boolean onKey(View v, int keyCode, KeyEvent event) {
					return handleMinSeedRowKeyListener(keyCode, event);
				}
			});
		}
	}

	@Thunk
	boolean handleMinSeedRowKeyListener(int keyCode, KeyEvent event) {
		if (event.getAction() != KeyEvent.ACTION_DOWN) {
			return false;
		}
		if (adapter == null) {
			return false;
		}

		if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP
				|| keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
			RcmAdapterFilter filter = adapter.getFilter();
			int filterVal = filter.getFilterMinSeeds();

			if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP) {
				filterVal++;
			}
			if (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
				filterVal--;
			}

			filter.setFilterMinSeeds(filterVal);
			filter.refilter();
			updateFilterTexts();
			return true;
		}
		return false;
	}

	@Thunk
	boolean handleLastSeenRowKeyListener(int keyCode, KeyEvent event) {
		if (event.getAction() != KeyEvent.ACTION_DOWN) {
			return false;
		}
		if (adapter == null) {
			return false;
		}

		if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP
				|| keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
			long[] filter = adapter.getFilter().getFilterLastSeenTimes();

			if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP) {
				if (filter[0] <= 0) {
					filter[0] = AndroidUtils.getTodayMS();
				} else {
					filter[0] -= DateUtils.DAY_IN_MILLIS;
				}
			}
			if (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
				filter[0] += DateUtils.DAY_IN_MILLIS;
				if (filter[0] > AndroidUtils.getTodayMS()) {
					filter[0] = -1;
				}
			}

			adapter.getFilter().setFilterLastSeenTimes(filter[0], filter[1]);
			adapter.getFilter().refilter();
			updateFilterTexts();
			return true;
		}
		return false;
	}

	@Thunk
	boolean handleAgeRowKeyListener(int keyCode, KeyEvent event) {
		if (event.getAction() != KeyEvent.ACTION_DOWN) {
			return false;
		}
		if (adapter == null) {
			return false;
		}

		if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP
				|| keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
			long[] filter = adapter.getFilter().getFilterPublishTimes();

			if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP) {
				if (filter[0] <= 0) {
					filter[0] = AndroidUtils.getTodayMS();
				} else {
					filter[0] -= DateUtils.DAY_IN_MILLIS;
				}
			}
			if (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
				if (filter == null) {
					return true;
				}
				filter[0] += DateUtils.DAY_IN_MILLIS;
				if (filter[0] > AndroidUtils.getTodayMS()) {
					filter[0] = -1;
				}
			}

			adapter.getFilter().setFilterPublishTimes(filter[0], filter[1]);
			adapter.getFilter().refilter();
			updateFilterTexts();
		}
		return false;
	}

	@Thunk
	boolean handleFileSizeRowKeyListener(int keyCode, KeyEvent event) {
		if (event.getAction() != KeyEvent.ACTION_DOWN) {
			return false;
		}
		if (adapter == null) {
			return false;
		}

		if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP
				|| keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
			long[] filter = adapter.getFilter().getFilterSizes();

			if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP) {
				filter[0] += 1024 * 1024L * 100; // 100M
			}
			if (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
				filter[0] -= 1024 * 1024L * 100; // 100M
				if (filter[0] < 0) {
					filter[0] = 0;
				}
			}

			adapter.getFilter().setFilterSizes(filter[0], filter[1]);
			adapter.getFilter().refilter();
			updateFilterTexts();
			return true;
		}
		return false;
	}

	private void setupListView() {

		TextView tvEmptyList = findViewById(R.id.tv_empty);

		tvEmptyList.setText(R.string.rcm_list_empty);

		RcmAdapter.RcmSelectionListener selectionListener = new RcmAdapter.RcmSelectionListener() {

			@Override
			public Map getSearchResultMap(String id) {
				return mapResults.get(id);
			}

			@Override
			public List<String> getSearchResultList() {
				return new ArrayList<>(mapResults.keySet());
			}

			@Override
			public void onItemClick(RcmAdapter adapter, int position) {
				if (!AndroidUtils.usesNavigationControl()) {
					// touch users have their own download button
					// nav pad people have click to download, hold-click for menu
					return;
				}

				String id = adapter.getItem(position);
				downloadResult(id);
			}

			@Override
			public void downloadResult(String id) {
				RcmActivity.this.downloadResult(id);
			}

			@Override
			public boolean onItemLongClick(RcmAdapter adapter, int position) {
				return false;
			}

			@Override
			public void onItemSelected(RcmAdapter adapter, int position,
					boolean isChecked) {
			}

			@Override
			public void onItemCheckedChanged(RcmAdapter adapter, String item,
					boolean isChecked) {
				AndroidUtilsUI.invalidateOptionsMenuHC(RcmActivity.this);
			}
		};

		adapter = new RcmAdapter(getLifecycle(), selectionListener) {
			@Override
			public void lettersUpdated(HashMap<String, Integer> mapLetters) {
				sideListHelper.lettersUpdated(mapLetters);
			}

			@Override
			public void setItems(List<String> items,
					SetItemsCallBack<String> callback) {
				super.setItems(items, callback);
				updateFilterTexts();
			}
		};
		adapter.setMultiCheckModeAllowed(false);
		adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
			@Override
			public void onChanged() {
				updateFilterTexts();
			}

			@Override
			public void onItemRangeInserted(int positionStart, int itemCount) {
				updateFilterTexts();
			}

			@Override
			public void onItemRangeRemoved(int positionStart, int itemCount) {
				updateFilterTexts();
			}
		});
		adapter.setEmptyView(findViewById(R.id.first_list),
				findViewById(R.id.empty_list));

		listview = findViewById(R.id.rcm_list);
		PreCachingLayoutManager layoutManager = new PreCachingLayoutManager(this);
		listview.setLayoutManager(layoutManager);
		listview.setAdapter(adapter);

		if (AndroidUtils.isTV()) {
			listview.setVerticalScrollbarPosition(View.SCROLLBAR_POSITION_LEFT);
			((FastScrollRecyclerView) listview).setEnableFastScrolling(false);
			layoutManager.setFixedVerticalHeight(AndroidUtilsUI.dpToPx(48));
			listview.setVerticalFadingEdgeEnabled(true);
			listview.setFadingEdgeLength(AndroidUtilsUI.dpToPx((int) (48 * 1.5)));
		}

		swipeRefresh = findViewById(R.id.swipe_container);
		if (swipeRefresh != null) {
			swipeRefresh.setExtraLayout(R.layout.swipe_layout_extra);

			swipeRefresh.setOnRefreshListener(
					new SwipeRefreshLayout.OnRefreshListener() {
						@Override
						public void onRefresh() {
							triggerRefresh();
						}
					});
			swipeRefresh.setOnExtraViewVisibilityChange(this);
		}

		RemoteProfile remoteProfile = session.getRemoteProfile();
		setupSideListArea(this.getWindow().getDecorView());

		sideListHelper.sortByConfig(remoteProfile, ID_SORT_FILTER, defaultSortID,
				sortDefinitions);
	}

	@Thunk
	void downloadResult(String id) {
		Map<?, ?> map = mapResults.get(id);
		String hash = com.biglybt.android.util.MapUtils.getMapString(map,
				TransmissionVars.FIELD_RCM_HASH, null);
		String name = com.biglybt.android.util.MapUtils.getMapString(map,
				TransmissionVars.FIELD_RCM_NAME, null);
		if (hash != null) {
			// TODO: When opening torrent, directory is "dunno" from here!!
			session.torrent.openTorrent(RcmActivity.this, hash, name);
		}
	}

	private void setupSideListArea(View view) {
		if (sideListHelper == null || !sideListHelper.isValid()) {
			sideListHelper = new SideListHelper(this, view, R.id.sidelist_layout, 0,
					0, 0, 0, 500, adapter);
			if (!sideListHelper.isValid()) {
				return;
			}

			sideListHelper.addEntry(view, R.id.sidesort_header, R.id.sidesort_list);
			sideListHelper.addEntry(view, R.id.sidefilter_header,
					R.id.sidefilter_list);
			sideListHelper.addEntry(view, R.id.sidetextfilter_header,
					R.id.sidetextfilter_list);
		}

		View sideListArea = view.findViewById(R.id.sidelist_layout);

		if (sideListArea != null && sideListArea.getVisibility() == View.VISIBLE) {
			sideListHelper.setupSideTextFilter(view, R.id.sidetextfilter_list,
					R.id.sidefilter_text, listview, adapter.getFilter());

			setupSideFilters(view);

			sideListHelper.setupSideSort(view, R.id.sidesort_list,
					R.id.rcm_sort_current, this);

			sideListHelper.expandedStateChanging(sideListHelper.isExpanded());
			sideListHelper.expandedStateChanged(sideListHelper.isExpanded());
		}

		if (sideListHelper.hasSideTextFilterArea()) {
			adapter.getFilter().setBuildLetters(true);
		}
	}

	private void setupSideFilters(View view) {
		tvFilterAgeCurrent = view.findViewById(R.id.rcm_filter_age_current);
		tvFilterSizeCurrent = view.findViewById(R.id.rcm_filter_size_current);
		tvFilterLastSeenCurrent = view.findViewById(
				R.id.rcm_filter_lastseen_current);
		tvFilterMinSeedsCurrent = view.findViewById(R.id.rcm_filter_min_seeds);
		tvFilterMinRankCurrent = view.findViewById(R.id.rcm_filter_min_rank);
		tvFilterCurrent = view.findViewById(R.id.rcm_filter_current);

		updateFilterTexts();
	}

	private void buildSortDefinitions() {
		if (sortDefinitions != null) {
			return;
		}
		String[] sortNames = getResources().getStringArray(R.array.sortby_rcm_list);

		sortDefinitions = new SparseArray<>(sortNames.length);
		int i = 0;

		//<item>Rank</item>
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_RCM_RANK
		}, SortDefinition.SORT_DESC));

		i++; // <item>Name</item>
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_RCM_NAME
		}, new Boolean[] {
			true
		}, true, SortDefinition.SORT_ASC));
		defaultSortID = i;

		i++; // <item>Seeds</item>
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_RCM_SEEDS,
			TransmissionVars.FIELD_RCM_PEERS
		}, SortDefinition.SORT_DESC));

		i++; // <item>size</item>
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_RCM_SIZE
		}, SortDefinition.SORT_DESC));

		i++; // <item>PublishDate</item>
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_RCM_PUBLISHDATE
		}, SortDefinition.SORT_DESC));

		i++; // <item>Last Seen</item>
		sortDefinitions.put(i, new SortDefinition(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_RCM_LAST_SEEN_SECS
		}, SortDefinition.SORT_DESC));
	}

	@Override
	protected void onPause() {
		super.onPause();
		AnalyticsTracker.getInstance(this).activityPause(this);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (adapter != null) {
			adapter.onSaveInstanceState(outState);
		}
		if (sideListHelper != null) {
			sideListHelper.onSaveInstanceState(outState);
		}
		outState.putLong(SAVESTATE_RCM_GOT_UNTIL, rcmGotUntil);
		outState.putString(SAVESTATE_LIST,
				com.biglybt.android.util.JSONUtils.encodeToJSON(mapResults));
	}

	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		if (adapter != null) {
			adapter.onRestoreInstanceState(savedInstanceState, listview);
		}
		if (sideListHelper != null) {
			sideListHelper.onRestoreInstanceState(savedInstanceState);
		}
		updateFilterTexts();

		rcmGotUntil = savedInstanceState.getLong(SAVESTATE_RCM_GOT_UNTIL, 0);
		if (rcmGotUntil > 0) {
			String list = savedInstanceState.getString(SAVESTATE_LIST);
			if (list != null) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "onRestoreInstanceState: using stored list");
				}
				Map<String, Object> map = com.biglybt.android.util.JSONUtils.decodeJSONnoException(
						list);

				if (map != null) {
					for (String key : map.keySet()) {
						Object o = map.get(key);
						if (o instanceof Map) {
							mapResults.put(key, (Map) o);
						}
					}

					adapter.getFilter().refilter();
				}
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		session.addRefreshTriggerListener(this);
		if (sideListHelper != null) {
			sideListHelper.onResume();
		}
		if (supportsRCM) {
			AnalyticsTracker.getInstance(this).activityResume(this, TAG);
		} else {
			AnalyticsTracker.getInstance(this).activityResume(this, TAG + ":NA");
		}
	}

	private void setupActionBar() {
		Toolbar toolBar = findViewById(R.id.actionbar);
		if (toolBar != null) {
			setSupportActionBar(toolBar);
		}

		// enable ActionBar app icon to behave as action to toggle nav drawer
		ActionBar actionBar = getSupportActionBar();
		if (actionBar == null) {
			System.err.println("actionBar is null");
			return;
		}

		RemoteProfile remoteProfile = session.getRemoteProfile();
		actionBar.setTitle(remoteProfile.getNick());
		// Text usually too long for phone ui
		//			actionBar.setTitle(
		//					getResources().getString(R.string.title_with_profile_name, getTitle(),
		//							remoteProfile.getNick()));

		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeButtonEnabled(true);
	}

	public boolean onOptionsItemSelected(MenuItem item) {
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

	@Thunk
	void updateFirstLoadText(@StringRes final int taskResId,
			final Object... args) {
		if (adapter != null && !adapter.isNeverSetItems()) {
			return;
		}
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (isFinishing()) {
					return;
				}
				TextView tvFirstList = findViewById(R.id.tv_first_list);
				if (tvFirstList != null) {
					String s = getResources().getString(taskResId, args);
					tvFirstList.setText(s);
				}
			}
		});
	}

	@Thunk
	void rpcRefreshingChanged(final boolean refreshing) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (isFinishing()) {
					return;
				}
				View view = findViewById(R.id.progress_spinner);
				if (view != null) {
					view.setVisibility(refreshing ? View.VISIBLE : View.GONE);
				}
			}
		});
	}

	@Override
	public void triggerRefresh() {
		if (!enabled) {
			return;
		}
		rpcRefreshingChanged(true);
		updateFirstLoadText(R.string.retrieving_items);
		session.rcm.getList(rcmGotUntil, new Session_RCM.RcmGetListListener() {
			@Override
			public void rcmListReceived(final long until, final List listRCM) {
				lastUpdated = System.currentTimeMillis();
				runOnUiThread(new Runnable() {

					@Override
					public void run() {
						if (isFinishing()) {
							return;
						}
						if (swipeRefresh != null) {
							swipeRefresh.setRefreshing(false);
						}

						updateList(listRCM);
						rcmGotUntil = until + 1;
					}
				});
				rpcRefreshingChanged(false);

			}

			@Override
			public void rcmListReceivedError(Exception e, String message) {
				rpcRefreshingChanged(false);
				if (message != null) {
					updateFirstLoadText(R.string.first_load_error, message);
				} else {
					updateFirstLoadText(R.string.first_load_error,
							AndroidUtils.getCausesMesssages(e));
				}
			}
		});
	}

	@Thunk
	void updateList(List<?> listRCMs) {
		if (listRCMs == null || listRCMs.isEmpty()) {
			if (mapResults.size() == 0 && adapter != null) {
				// triggers display of "empty"
				adapter.notifyDataSetInvalidated();
			}
			return;
		}
		synchronized (mLock) {
			for (Object object : listRCMs) {
				Map<?, ?> mapRCM = (Map<?, ?>) object;
				String hash = com.biglybt.android.util.MapUtils.getMapString(mapRCM,
						TransmissionVars.FIELD_RCM_HASH, null);

				Map<?, ?> old = mapResults.put(hash, mapRCM);
				if (old == null) {
					//adapter.addItem(hash);

					long size = com.biglybt.android.util.MapUtils.getMapLong(mapRCM,
							TransmissionVars.FIELD_RCM_SIZE, 0);
					if (size > maxSize) {
						maxSize = size;
					}

				}
				List listTags = com.biglybt.android.util.MapUtils.getMapList(mapRCM,
						TransmissionVars.FIELD_RCM_TAGS, null);
				if (listTags != null && listTags.size() > 0) {
					Iterator iterator = listTags.iterator();
					while (iterator.hasNext()) {
						Object o = iterator.next();

						if ("_i2p_".equals(o)) {
							iterator.remove();
							break;
						}
					}
				}
			}
		}

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				adapter.getFilter().refilter();
			}
		});
	}

	@Override
	public void onDrawerOpened(View view) {
		setupSideListArea(view);
		updateFilterTexts();
	}

	@Override
	public void rcmEnabledChanged(boolean enable, boolean all) {
		this.enabled = enable;
		if (enabled) {
			triggerRefresh();
		} else {
			finish();
		}
	}

	@Override
	public void onExtraViewVisibilityChange(final View view, int visibility) {
		if (visibility != View.VISIBLE) {
			if (pullRefreshHandler != null) {
				pullRefreshHandler.removeCallbacksAndMessages(null);
				pullRefreshHandler = null;
			}
			return;
		}

		if (pullRefreshHandler != null) {
			pullRefreshHandler.removeCallbacks(null);
			pullRefreshHandler = null;
		}
		pullRefreshHandler = new Handler(Looper.getMainLooper());

		pullRefreshHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				if (isFinishing()) {
					return;
				}

				long sinceMS = System.currentTimeMillis() - lastUpdated;
				String since = DateUtils.getRelativeDateTimeString(RcmActivity.this,
						lastUpdated, DateUtils.SECOND_IN_MILLIS, DateUtils.WEEK_IN_MILLIS,
						0).toString();
				String s = getResources().getString(R.string.last_updated, since);

				TextView tvSwipeText = view.findViewById(R.id.swipe_text);
				tvSwipeText.setText(s);

				if (pullRefreshHandler == null) {
					return;
				}
				pullRefreshHandler.postDelayed(this,
						sinceMS < DateUtils.MINUTE_IN_MILLIS ? DateUtils.SECOND_IN_MILLIS
								: sinceMS < DateUtils.HOUR_IN_MILLIS
										? DateUtils.MINUTE_IN_MILLIS : DateUtils.HOUR_IN_MILLIS);
			}
		}, 0);
	}

	@Override
	public SortableAdapter getSortableAdapter() {
		return adapter;
	}

	@Override
	public SparseArray<SortDefinition> getSortDefinitions() {
		return sortDefinitions;
	}

	@Override
	public SortDefinition getSortDefinition(int id) {
		return sortDefinitions.get(id);
	}

	@Override
	public String getSortFilterID() {
		return ID_SORT_FILTER;
	}

	@SuppressWarnings("UnusedParameters")
	public void fileSizeRow_clicked(@Nullable View view) {
		if (adapter == null) {
			return;
		}
		long[] sizeRange = adapter.getFilter().getFilterSizes();

		DialogFragmentSizeRange.openDialog(getSupportFragmentManager(),
				ID_SORT_FILTER, remoteProfileID, maxSize, sizeRange[0], sizeRange[1]);
	}

	@SuppressWarnings("UnusedParameters")
	public void ageRow_clicked(@Nullable View view) {
		if (adapter == null) {
			return;
		}
		long[] timeRange = adapter.getFilter().getFilterPublishTimes();

		DialogFragmentDateRange.openDialog(getSupportFragmentManager(),
				ID_SORT_FILTER, remoteProfileID, timeRange[0], timeRange[1]);
	}

	@SuppressWarnings("UnusedParameters")
	public void lastSeenRow_clicked(@Nullable View view) {
		if (adapter == null) {
			return;
		}
		long[] timeRange = adapter.getFilter().getFilterLastSeenTimes();

		DialogFragmentDateRange.openDialog(getSupportFragmentManager(),
				FILTER_PREF_LAST_SEEN, remoteProfileID, timeRange[0], timeRange[1]);
	}

	@SuppressWarnings("UnusedParameters")
	public void minRankRow_clicked(@Nullable View view) {
		if (adapter == null) {
			return;
		}
		int val = adapter.getFilter().getFilterMinRank();
		NumberPickerBuilder builder = new NumberPickerBuilder(
				getSupportFragmentManager(), FILTER_PREF_MINRANK, val).setTitleId(
						R.string.filterby_header_minimum_rank).setMin(0).setMax(100);
		DialogFragmentNumberPicker.openDialog(builder);
	}

	@SuppressWarnings("UnusedParameters")
	public void minSeedsRow_clicked(@Nullable View view) {
		if (adapter == null) {
			return;
		}
		int val = adapter.getFilter().getFilterMinSeeds();
		NumberPickerBuilder builder = new NumberPickerBuilder(
				getSupportFragmentManager(), FILTER_PREF_MINSEEDS, val).setTitleId(
						R.string.filterby_header_minimum_seeds).setMin(0).setMax(99);
		DialogFragmentNumberPicker.openDialog(builder);
	}

	@Override
	public void onSizeRangeChanged(String callbackID, long start, long end) {
		if (adapter == null) {
			return;
		}
		adapter.getFilter().setFilterSizes(start, end);
		adapter.getFilter().refilter();
		updateFilterTexts();
	}

	@Override
	public void onDateRangeChanged(String callbackID, long start, long end) {
		if (adapter == null) {
			return;
		}
		RcmAdapterFilter filter = adapter.getFilter();
		if (ID_SORT_FILTER.equals(callbackID)) {
			filter.setFilterPublishTimes(start, end);
		} else {
			filter.setFilterLastSeenTimes(start, end);
		}
		filter.refilter();
		updateFilterTexts();
	}

	@Override
	public void onNumberPickerChange(String callbackID, int val) {
		if (adapter == null) {
			return;
		}
		if (FILTER_PREF_MINSEEDS.equals(callbackID)) {
			adapter.getFilter().setFilterMinSeeds(val);
		} else if (FILTER_PREF_MINRANK.equals(callbackID)) {
			adapter.getFilter().setFilterMinRank(val);
		}
		adapter.getFilter().refilter();
		updateFilterTexts();
	}

	public void clearFilters_clicked(View view) {

		RcmAdapterFilter filter = adapter.getFilter();

		filter.clearFilter();
		filter.refilter();
		updateFilterTexts();
	}

	@Thunk
	void updateFilterTexts() {

		if (!AndroidUtilsUI.isUIThread()) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					updateFilterTexts();
				}
			});
			return;
		}

		if (adapter == null) {
			return;
		}

		String sCombined = "";

		Resources resources = getResources();

		String filterTimeText;
		String filterSizeText;
		String filterLastSeenText;
		String filterMinRankText;
		String filterMinSeedsText;

		RcmAdapterFilter filter = adapter.getFilter();

		long[] timeRange = filter.getFilterPublishTimes();
		if (timeRange[0] <= 0 && timeRange[1] <= 0) {
			filterTimeText = resources.getString(R.string.filter_age_none);
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
			if (sizeRange[1] > 0 && sizeRange[0] > 0) {
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

		long[] lastSeenRange = filter.getFilterLastSeenTimes();
		if (lastSeenRange[0] <= 0 && lastSeenRange[1] <= 0) {
			filterLastSeenText = resources.getString(R.string.filter_lastseen_none);
		} else {
			if (lastSeenRange[1] > 0 && lastSeenRange[0] > 0) {
				filterLastSeenText = DateUtils.formatDateRange(this, lastSeenRange[0],
						lastSeenRange[1],
						DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_ABBREV_MONTH);
			} else if (lastSeenRange[0] > 0) {
				CharSequence s = DateUtils.getRelativeTimeSpanString(lastSeenRange[0],
						System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
						DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
				filterLastSeenText = resources.getString(R.string.filter_date_starting,
						s);
			} else {
				CharSequence s = DateUtils.getRelativeTimeSpanString(lastSeenRange[0],
						System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
						DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
				filterLastSeenText = resources.getString(R.string.filter_date_until, s);
			}
			if (sCombined.length() > 0) {
				sCombined += "\n";
			}
			sCombined += filterLastSeenText;
		}

		if (tvFilterLastSeenCurrent != null) {
			tvFilterLastSeenCurrent.setText(filterLastSeenText);
		}

		int minSeeds = filter.getFilterMinSeeds();
		if (minSeeds <= 0) {
			filterMinSeedsText = resources.getString(R.string.filter_seeds_none);
		} else {
			filterMinSeedsText = resources.getQuantityString(R.plurals.filter_seeds,
					minSeeds, minSeeds);
			if (sCombined.length() > 0) {
				sCombined += "\n";
			}
			sCombined += filterMinSeedsText;
		}
		if (tvFilterMinSeedsCurrent != null) {
			tvFilterMinSeedsCurrent.setText(filterMinSeedsText);
		}

		int minRank = filter.getFilterMinRank();
		if (minRank <= 0) {
			filterMinRankText = resources.getString(R.string.filter_rank_none);
		} else {
			filterMinRankText = resources.getString(R.string.filter_rank, minRank);
			if (sCombined.length() > 0) {
				sCombined += "\n";
			}
			sCombined += filterMinRankText;
		}
		if (tvFilterMinRankCurrent != null) {
			tvFilterMinRankCurrent.setText(filterMinRankText);
		}

		if (tvFilterCurrent != null) {
			tvFilterCurrent.setText(sCombined);
		}

		if (tvFilterTop != null) {
			SpanTags spanTag = new SpanTags(this, session, tvFilterTop,
					listenerSpanTags);
			spanTag.setLinkTags(false);
			spanTag.setShowIcon(false);
			List<Map<?, ?>> listFilters = new ArrayList<>();
			listFilters.add(makeFilterListMap(FILTER_INDEX_AGE, filterTimeText,
					filter.hasPublishTimeFilter()));
			listFilters.add(makeFilterListMap(FILTER_INDEX_SIZE, filterSizeText,
					filter.hasSizeFilter()));
			listFilters.add(makeFilterListMap(FILTER_INDEX_RANK, filterMinRankText,
					filter.hasMinRankFilter()));
			listFilters.add(makeFilterListMap(FILTER_INDEX_LAST_SEEN,
					filterLastSeenText, filter.hasLastSeenFilter()));
			listFilters.add(makeFilterListMap(FILTER_INDEX_SEEDS, filterMinSeedsText,
					filter.hasMinSeedsFilter()));
			spanTag.setTagMaps(listFilters);
			spanTag.setLineSpaceExtra(AndroidUtilsUI.dpToPx(8));
			spanTag.updateTags();
		}

		int count = mapResults == null ? 0 : mapResults.size();
		int filteredCount = adapter.getItemCount();
		String countString = DisplayFormatters.formatNumber(count);
		ActionBar actionBar = getSupportActionBar();
		String sResultsCount;
		if (count == filteredCount) {
			if (count == 0) {
				sResultsCount = getResources().getString(R.string.title_activity_rcm);
			} else {
				sResultsCount = getResources().getQuantityString(
						R.plurals.rcm_results_count, count, countString);
			}
		} else {
			sResultsCount = getResources().getQuantityString(
					R.plurals.rcm_filtered_results_count, count,
					DisplayFormatters.formatNumber(filteredCount), countString);
		}
		if (tvDrawerFilter != null) {
			tvDrawerFilter.setText(sResultsCount);
		}
		if (actionBar != null) {
			actionBar.setSubtitle(sResultsCount);
		}
		if (tvHeader != null) {
			tvHeader.setText(sResultsCount);
		}

	}

	private static HashMap<Object, Object> makeFilterListMap(long uid,
			String name, boolean enabled) {
		HashMap<Object, Object> map = new HashMap<>();
		map.put("uid", uid);
		map.put("name", name);
		map.put(DrawableTag.KEY_ROUNDED, true);
		map.put(TransmissionVars.FIELD_TAG_COLOR,
				enabled ? 0xFF000000 : 0xA0000000);
		map.put(DrawableTag.KEY_FILL_COLOR, enabled ? 0xFF80ffff : 0x4080ffff);
		return map;
	}

}
