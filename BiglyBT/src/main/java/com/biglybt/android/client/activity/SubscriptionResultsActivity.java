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

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;

import androidx.annotation.*;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.view.ActionMode.Callback;
import androidx.appcompat.widget.Toolbar;
import androidx.leanback.app.ProgressBarManager;
import androidx.recyclerview.widget.RecyclerView;

import com.biglybt.android.adapter.SortableRecyclerAdapter;
import com.biglybt.android.client.*;
import com.biglybt.android.client.adapter.MetaSearchEnginesInfo;
import com.biglybt.android.client.adapter.MetaSearchResultsAdapter;
import com.biglybt.android.client.adapter.MetaSearchResultsAdapterFilter;
import com.biglybt.android.client.dialog.DialogFragmentDateRange;
import com.biglybt.android.client.dialog.DialogFragmentSizeRange;
import com.biglybt.android.client.rpc.SubscriptionListReceivedListener;
import com.biglybt.android.client.session.*;
import com.biglybt.android.client.sidelist.SideActionSelectionListener;
import com.biglybt.android.client.sidelist.SideListActivity;
import com.biglybt.android.client.sidelist.SideListHelper;
import com.biglybt.android.client.spanbubbles.DrawableTag;
import com.biglybt.android.client.spanbubbles.SpanTags;
import com.biglybt.android.util.JSONUtils;
import com.biglybt.android.util.MapUtils;
import com.biglybt.android.widget.CustomToast;
import com.biglybt.android.widget.PreCachingLayoutManager;
import com.biglybt.android.widget.SwipeRefreshLayoutExtra;
import com.biglybt.android.widget.SwipeRefreshLayoutExtra.SwipeTextUpdater;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.Thunk;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;

import java.net.URI;
import java.util.*;

/**
 * Activity for one Subscription, displaying Subscription information and items
 * <p/>
 * Originally copied from {@link MetaSearchActivity}, since the result maps
 * are returned in the same format.
 * <p/>
 * Created by TuxPaper on 4/22/16.
 */
public class SubscriptionResultsActivity
	extends SideListActivity
	implements DialogFragmentSizeRange.SizeRangeDialogListener,
	DialogFragmentDateRange.DateRangeDialogListener,
	SubscriptionListReceivedListener
{
	@Thunk
	static final int FILTER_INDEX_AGE = 0;

	@Thunk
	static final int FILTER_INDEX_SIZE = 1;

	private static final String TAG = "Subscription";

	public static final String ID_SORT_FILTER = "-sub";

	private static final String SAVESTATE_LIST = "list";

	private static final String SAVESTATE_LIST_NAME = "listName";

	/**
	 * <HashString, Map of Fields>
	 */
	@Thunk
	final HashMap<String, Map> mapResults = new HashMap<>();

	@Thunk
	MetaSearchResultsAdapter subscriptionResultsAdapter;

	@Thunk
	TextView tvDrawerFilter;

	@Thunk
	String subscriptionID;

	@Thunk
	String listName;

	@Thunk
	long numNew;

	@Thunk
	TextView tvHeader;

	@Thunk
	ActionMode mActionMode;

	@Thunk
	SwipeRefreshLayoutExtra swipeRefresh;

	@Thunk
	boolean isRefreshing;

	private RecyclerView lvResults;

	private TextView tvFilterAgeCurrent;

	private TextView tvFilterSizeCurrent;

	private TextView tvFilterCurrent;

	private TextView tvFilterTop;

	private long maxSize;

	private SpanTags.SpanTagsListener listenerSpanTags;

	private Callback mActionModeCallback;

	@Thunk
	long lastUpdated;

	@Thunk
	CompoundButton switchAutoDL;

	private ProgressBarManager progressBarManager;

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_context_subscriptionlist, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	protected void onCreateWithSession(@Nullable Bundle savedInstanceState) {
		Intent intent = getIntent();

		subscriptionID = intent.getStringExtra("subscriptionID");
		listName = intent.getStringExtra("title");

		int SHOW_SIDELIST_MINWIDTH_PX = getResources().getDimensionPixelSize(
				R.dimen.sidelist_subscription_drawer_until_screen);

		setContentView(AndroidUtils.isTV(this) ? R.layout.activity_subscription_tv
				: AndroidUtilsUI.getScreenWidthPx(this) >= SHOW_SIDELIST_MINWIDTH_PX
						? R.layout.activity_subscription
						: R.layout.activity_subscription_drawer);
		setupActionBar();

		View progressBar = findViewById(R.id.progress_spinner);
		if (progressBar != null) {
			progressBarManager = new ProgressBarManager();
			progressBarManager.setProgressBarView(progressBar);
		}

		tvFilterTop = findViewById(R.id.ms_top_filterarea);
		if (tvFilterTop != null) {

			tvFilterTop.setMovementMethod(LinkMovementMethod.getInstance());

			listenerSpanTags = new SpanTags.SpanTagsListener() {

				@Override
				public int getTagState(int index, Map mapTag, String name) {
					boolean hasFilter = false;
					switch (index) {
						case FILTER_INDEX_AGE:
							hasFilter = subscriptionResultsAdapter.getFilter().hasPublishTimeFilter();
							break;
						case FILTER_INDEX_SIZE:
							hasFilter = subscriptionResultsAdapter.getFilter().hasSizeFilter();
							break;
					}

					return hasFilter ? SpanTags.TAG_STATE_SELECTED
							: SpanTags.TAG_STATE_UNSELECTED;
				}

				@Override
				public void tagClicked(int index, Map mapTag, String name) {
					switch (index) {
						case FILTER_INDEX_AGE:
							ageRow_clicked(null);
							break;
						case FILTER_INDEX_SIZE:
							fileSizeRow_clicked(null);
							break;
					}
				}
			};
		}
		tvDrawerFilter = findViewById(R.id.sidelist_topinfo);
		tvHeader = findViewById(R.id.subscription_header);

		if (tvHeader != null && listName != null) {
			tvHeader.setText(listName);
		}

		switchAutoDL = findViewById(R.id.subscription_autodl_switch);
		if (switchAutoDL != null) {
			switchAutoDL.setOnCheckedChangeListener((buttonView, isChecked) -> {
				Map<String, Object> map = new HashMap<>();
				map.put(TransmissionVars.FIELD_SUBSCRIPTION_AUTO_DOWNLOAD, isChecked);
				session.subscription.setField(subscriptionID, map);
			});
		}

		MetaSearchResultsAdapter.MetaSearchSelectionListener metaSearchSelectionListener = new MetaSearchResultsAdapter.MetaSearchSelectionListener() {
			@Override
			public Session getSession() {
				return SubscriptionResultsActivity.this.getSession();
			}

			@Override
			public void onItemCheckedChanged(MetaSearchResultsAdapter adapter,
					String item, boolean isChecked) {

				int checkedItemCount = adapter.getCheckedItemCount();
				if (checkedItemCount == 0) {
					finishActionMode();
				} else {
					showContextualActions();
				}

				if (adapter.isMultiCheckMode()) {
					updateActionModeText(mActionMode);
				} else if (checkedItemCount == 1
						&& AndroidUtils.usesNavigationControl()) {
					// touch users have their own download button
					// nav pad people have click to download, hold-click for menu
					downloadResult(item);
				}

				AndroidUtilsUI.invalidateOptionsMenuHC(SubscriptionResultsActivity.this,
						mActionMode);
			}

			@Override
			public void onItemClick(MetaSearchResultsAdapter adapter, int position) {
			}

			@Override
			public boolean onItemLongClick(MetaSearchResultsAdapter adapter,
					int position) {
				//noinspection SimplifiableIfStatement
				if (AndroidUtils.usesNavigationControl()) {
					return showContextMenu();
				}
				return false;
			}

			@Override
			public void onItemSelected(MetaSearchResultsAdapter adapter, int position,
					boolean isChecked) {

			}

			@Override
			public void downloadResult(String id) {
				SubscriptionResultsActivity.this.downloadResult(id);
			}

			@Override
			public MetaSearchEnginesInfo getSearchEngineMap(String engineID) {
				return null;
			}

			@Override
			public List<String> getSearchResultList() {
				return new ArrayList<>(mapResults.keySet());
			}

			@Override
			public Map getSearchResultMap(String id) {
				return mapResults.get(id);
			}

			@Override
			public void newButtonClicked(String id, boolean currentlyNew) {
				session.subscription.setResultRead(subscriptionID,
						Collections.singletonList(id), currentlyNew);
			}

		};
		subscriptionResultsAdapter = new MetaSearchResultsAdapter(
				metaSearchSelectionListener, R.layout.row_subscription_result,
				R.layout.row_subscription_result_dpad, ID_SORT_FILTER);
		subscriptionResultsAdapter.addOnSetItemsCompleteListener(
				adapter -> updateHeader());
		subscriptionResultsAdapter.setMultiCheckModeAllowed(
				!AndroidUtils.usesNavigationControl());
		lvResults = findViewById(R.id.ms_list_results);
		lvResults.setAdapter(subscriptionResultsAdapter);
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

		swipeRefresh = findViewById(R.id.swipe_container);
		if (swipeRefresh != null) {
			swipeRefresh.setExtraLayout(R.layout.swipe_layout_extra);

			swipeRefresh.setOnRefreshListener(
					() -> session.subscription.refreshResults(subscriptionID));
			swipeRefresh.setOnExtraViewVisibilityChange(
					new SwipeTextUpdater(getLifecycle(), (tv) -> {
						long sinceMS = System.currentTimeMillis() - lastUpdated;
						String since = DateUtils.getRelativeDateTimeString(
								SubscriptionResultsActivity.this, lastUpdated,
								DateUtils.SECOND_IN_MILLIS, DateUtils.WEEK_IN_MILLIS,
								0).toString();

						tv.setText(getResources().getString(R.string.last_updated, since));

						return sinceMS < DateUtils.MINUTE_IN_MILLIS
								? DateUtils.SECOND_IN_MILLIS : DateUtils.MINUTE_IN_MILLIS;
					}));
		}

		if (savedInstanceState != null) {
			String list = savedInstanceState.getString(SAVESTATE_LIST);
			if (list != null) {
				Map<String, Object> map = JSONUtils.decodeJSONnoException(list);

				if (map != null) {
					numNew = 0;
					for (String key : map.keySet()) {
						Object o = map.get(key);
						if (o instanceof Map) {
							mapResults.put(key, (Map) o);

							boolean isRead = MapUtils.getMapBoolean((Map) o,
									TransmissionVars.FIELD_SUBSCRIPTION_RESULT_ISREAD, false);
							if (!isRead) {
								numNew++;
							}

						}
					}
				}
				listName = savedInstanceState.getString(SAVESTATE_LIST_NAME);
			}

		}

	}

	@Override
	protected void onStart() {
		super.onStart();

		session.subscription.refreshResults(subscriptionID);
	}

	@WorkerThread
	@Override
	public void onDateRangeChanged(String callbackID, long start, long end) {
		if (subscriptionResultsAdapter == null) {
			return;
		}
		subscriptionResultsAdapter.getFilter().setFilterTimes(start, end);
		subscriptionResultsAdapter.getFilter().refilter(false);
		updateFilterTexts();
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

		if (itemId == R.id.action_mark_all_seen) {
			List<String> items = new ArrayList<>();
			for (Map sub : mapResults.values()) {
				boolean isSeen = MapUtils.getMapBoolean(sub,
						TransmissionVars.FIELD_SUBSCRIPTION_RESULT_ISREAD, false);
				if (!isSeen) {
					sub.put(TransmissionVars.FIELD_SUBSCRIPTION_RESULT_ISREAD, true);
					items.add(MapUtils.getMapString(sub,
							TransmissionVars.FIELD_SUBSCRIPTION_RESULT_ID, ""));
				}
			}
			subscriptionResultsAdapter.notifyDataSetInvalidated();

			session.subscription.setResultRead(subscriptionID, items, true);
			return true;
		}
		if (itemId == R.id.action_auto_download) {
			Map<String, Object> map = new HashMap<>();
			Map<String, Object> mapSubscription = session.subscription.getSubscription(
					subscriptionID);
			boolean autoDL = MapUtils.getMapBoolean(mapSubscription,
					TransmissionVars.FIELD_SUBSCRIPTION_AUTO_DOWNLOAD, false);
			map.put(TransmissionVars.FIELD_SUBSCRIPTION_AUTO_DOWNLOAD, !autoDL);
			session.subscription.setField(subscriptionID, map);
			return true;
		}
		if (itemId == R.id.action_sel_remove) {
			session.subscription.removeSubscription(this, new String[] {
				subscriptionID
			}, new Session_Subscription.SubscriptionsRemovedListener() {
				@Override
				public void subscriptionsRemoved(List<String> subscriptionIDs) {
					if (subscriptionIDs.contains(subscriptionID)) {
						finish();
					}
				}

				@Override
				public void subscriptionsRemovalError(
						Map<String, String> mapSubscriptionIDtoError) {

					// TODO: Pull name, i8n
					for (String subscriptionID : mapSubscriptionIDtoError.keySet()) {
						String error = mapSubscriptionIDtoError.get(subscriptionID);
						AndroidUtilsUI.showDialog(SubscriptionResultsActivity.this,
								R.string.remove_subscription, R.string.error_x, error);
					}
				}

				@Override
				public void subscriptionsRemovalException(Throwable t, String message) {
					if (t != null) {
						AndroidUtilsUI.showDialog(SubscriptionResultsActivity.this,
								R.string.remove_subscription, R.string.error_x, t.toString());
					} else {
						AndroidUtilsUI.showDialog(SubscriptionResultsActivity.this,
								R.string.remove_subscription, R.string.error_x, message);
					}
				}
			});
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onPause() {
		super.onPause();
		session.subscription.removeListReceivedListener(this);
		AnalyticsTracker.getInstance(this).activityPause(this);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuItem item = menu.findItem(R.id.action_auto_download);
		if (item != null) {
			Map<String, Object> mapSubscription = session.subscription.getSubscription(
					subscriptionID);
			boolean autoDlSupported = MapUtils.getMapBoolean(mapSubscription,
					TransmissionVars.FIELD_SUBSCRIPTION_AUTO_DL_SUPPORTED, false);
			boolean autoDL = MapUtils.getMapBoolean(mapSubscription,
					TransmissionVars.FIELD_SUBSCRIPTION_AUTO_DOWNLOAD, false);
			item.setEnabled(autoDlSupported);
			item.setChecked(autoDL);
		}

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		if (subscriptionResultsAdapter != null) {
			subscriptionResultsAdapter.onRestoreInstanceState(savedInstanceState);
		}
		updateFilterTexts();
	}

	@Override
	protected void onResume() {
		super.onResume();
		session.subscription.addListReceivedListener(this, lastUpdated);
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		if (subscriptionResultsAdapter != null) {
			subscriptionResultsAdapter.onSaveInstanceState(outState);
		}
		Bundle tmpBundle = new Bundle();
		tmpBundle.putString(SAVESTATE_LIST, JSONUtils.encodeToJSON(mapResults));
		tmpBundle.putString(SAVESTATE_LIST_NAME, listName);
		AndroidUtils.addToBundleIf(tmpBundle, outState, 1024 * 200L);
	}

	@Override
	public void onSizeRangeChanged(String callbackID, long start, long end) {
		if (subscriptionResultsAdapter == null) {
			return;
		}
		subscriptionResultsAdapter.getFilter().setFilterSizes(start, end);
		subscriptionResultsAdapter.getFilter().refilter(false);
		updateFilterTexts();
	}

	@SuppressWarnings("UnusedParameters")
	public void ageRow_clicked(@Nullable View view) {
		if (subscriptionResultsAdapter == null) {
			return;
		}

		MetaSearchResultsAdapterFilter filter = subscriptionResultsAdapter.getFilter();
		long[] timeRange = filter.getFilterTimes();

		DialogFragmentDateRange.openDialog(getSupportFragmentManager(), null,
				remoteProfileID, timeRange[0], timeRange[1]);
	}

	@Thunk
	void downloadResult(String id) {
		Map map = mapResults.get(id);
		if (map == null) {
			return;
		}

		Resources resources = getResources();

		final String name = MapUtils.getMapString(map,
				TransmissionVars.FIELD_SEARCHRESULT_NAME, "torrent");

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
				String from = url;
				try {
					from = URI.create(url).getHost();
				} catch (Exception ignore) {
				}

				s = resources.getString(R.string.download_source_item_from_url, from);
			}
			listNames.add(s);
			listURLs.add(url);
		}

		if (!gotHash) {
			String hash = MapUtils.getMapString(map,
					TransmissionVars.FIELD_SEARCHRESULT_HASH, null);

			if (hash != null && hash.length() > 0) {
				String s = resources.getString(R.string.download_source_item_from_hash);
				listNames.add(s);
				listURLs.add(hash);
			}
		}

		if (listNames.size() == 0) {
			CustomToast.showText("Error getting Search Result URL",
					Toast.LENGTH_SHORT);
		} else if (listNames.size() > 1) {
			String[] items = listNames.toArray(new String[0]);

			AlertDialog.Builder build = new MaterialAlertDialogBuilder(
					SubscriptionResultsActivity.this);
			build.setTitle(R.string.select_download_source);
			build.setItems(items, (dialog, which) -> {
				if (which >= 0 && which < listURLs.size()) {
					String url1 = listURLs.get(which);
					session.torrent.openTorrent(SubscriptionResultsActivity.this, url1,
							name);
				}

			});

			build.show();
		} else {
			session.torrent.openTorrent(SubscriptionResultsActivity.this,
					listURLs.get(0), name);
		}
	}

	@SuppressWarnings("UnusedParameters")
	public void fileSizeRow_clicked(@Nullable View view) {
		if (subscriptionResultsAdapter == null) {
			return;
		}

		MetaSearchResultsAdapterFilter filter = subscriptionResultsAdapter.getFilter();
		long[] sizeRange = filter.getFilterSizes();

		DialogFragmentSizeRange.openDialog(getSupportFragmentManager(), null, null,
				remoteProfileID, maxSize, sizeRange[0], sizeRange[1]);
	}

	@Thunk
	void finishActionMode() {
		if (mActionMode != null) {
			mActionMode.finish();
			mActionMode = null;
		}
	}

	private static Map<String, Object> fixupResultMap(
			Map<String, Object> mapResult) {
		final String[] IDS_LONG = {
			TransmissionVars.FIELD_SEARCHRESULT_PUBLISHDATE,
			TransmissionVars.FIELD_SEARCHRESULT_PEERS,
			TransmissionVars.FIELD_SEARCHRESULT_SIZE,
			TransmissionVars.FIELD_SEARCHRESULT_SEEDS,
		};
		final String[] IDS_FLOAT = {
			TransmissionVars.FIELD_SEARCHRESULT_RANK,
		};

		for (String id : IDS_LONG) {
			Object o = mapResult.get(id);
			if (o instanceof String) {
				try {
					Long l = Long.valueOf((String) o);
					mapResult.put(id, l);
				} catch (Throwable ignore) {
				}
			}
		}

		for (String id : IDS_FLOAT) {
			Object o = mapResult.get(id);
			if (o instanceof String) {
				try {
					Double d = Double.valueOf((String) o);
					mapResult.put(id, d);
				} catch (Throwable ignore) {
				}
			}
		}

		return mapResult;
	}

	private List<String> getChosenSubscriptionIDs() {
		List<String> list = new ArrayList<>();
		if (!subscriptionResultsAdapter.isMultiCheckMode()
				&& AndroidUtils.usesNavigationControl()) {
			list.add(subscriptionResultsAdapter.getSelectedItem());
			return list;
		}
		List<String> checkedItems = subscriptionResultsAdapter.getCheckedItems();
		list.addAll(checkedItems);
		return list;
	}

	private List<Map> getChosenSubscriptions() {
		List<Map> list = new ArrayList<>();
		if (!subscriptionResultsAdapter.isMultiCheckMode()
				&& AndroidUtils.usesNavigationControl()) {
			list.add(mapResults.get(subscriptionResultsAdapter.getSelectedItem()));
			return list;
		}
		List<String> checkedItems = subscriptionResultsAdapter.getCheckedItems();
		for (String id : checkedItems) {

			Map map = mapResults.get(id);
			if (map != null) {
				list.add(map);
			}
		}
		return list;
	}

	@SuppressWarnings("unchecked")
	@Thunk
	boolean handleMenu(@IdRes int itemId) {
		if (itemId == R.id.action_download) {
			List<String> checkedItems = getChosenSubscriptionIDs();
			for (String id : checkedItems) {
				downloadResult(id);
			}
			return true;
		}
		if (itemId == R.id.action_mark_seen) {
			List<Map> checkedSubscriptions = getChosenSubscriptions();
			for (Map sub : checkedSubscriptions) {
				sub.put(TransmissionVars.FIELD_SUBSCRIPTION_RESULT_ISREAD, true);
			}
			subscriptionResultsAdapter.notifyDataSetInvalidated();

			List<String> checkedItems = getChosenSubscriptionIDs();
			session.subscription.setResultRead(subscriptionID, checkedItems, true);
			return true;
		}
		if (itemId == R.id.action_mark_unseen) {
			List<Map> checkedSubscriptions = getChosenSubscriptions();
			for (Map sub : checkedSubscriptions) {
				sub.put(TransmissionVars.FIELD_SUBSCRIPTION_RESULT_ISREAD, false);
			}
			subscriptionResultsAdapter.notifyDataSetInvalidated();

			List<String> checkedItems = getChosenSubscriptionIDs();
			session.subscription.setResultRead(subscriptionID, checkedItems, false);
			return true;
		}
		return false;
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

	@Override
	public void rpcSubscriptionListError(String id, @NonNull Throwable e) {

	}

	@Override
	public void rpcSubscriptionListFailure(String id, @NonNull String message) {

	}

	@Override
	public void rpcSubscriptionListReceived(@NonNull List<String> subscriptions) {
		lastUpdated = System.currentTimeMillis();

		if (isFinishing()) {
			return;
		}

		final Map<String, Object> mapSubscription = session.subscription.getSubscription(
				subscriptionID);

		if (switchAutoDL != null) {
			runOnUiThread(() -> {
				if (isFinishing()) {
					return;
				}
				boolean autoDL = MapUtils.getMapBoolean(mapSubscription,
						TransmissionVars.FIELD_SUBSCRIPTION_AUTO_DOWNLOAD, false);
				boolean autoDlSupported = MapUtils.getMapBoolean(mapSubscription,
						TransmissionVars.FIELD_SUBSCRIPTION_AUTO_DL_SUPPORTED, false);
				switchAutoDL.setVisibility(autoDlSupported ? View.VISIBLE : View.GONE);
				switchAutoDL.setChecked(autoDL);
			});
		}

		listName = MapUtils.getMapString(mapSubscription,
				TransmissionVars.FIELD_SUBSCRIPTION_NAME, "");
		List listResults = MapUtils.getMapList(mapSubscription,
				TransmissionVars.FIELD_SUBSCRIPTION_RESULTS, null);

		if (listResults != null) {
			numNew = 0;
			for (Object oResult : listResults) {
				if (!(oResult instanceof Map)) {
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "rpcSubscriptionListReceived: NOT A MAP: " + oResult);
					}
					continue;
				}

				@SuppressWarnings("unchecked")
				Map<String, Object> mapResult = fixupResultMap(
						(Map<String, Object>) oResult);

				long size = MapUtils.getMapLong(mapResult,
						TransmissionVars.FIELD_SEARCHRESULT_SIZE, 0);
				if (size > maxSize) {
					maxSize = size;
				}

				String hash = MapUtils.getMapString(mapResult,
						TransmissionVars.FIELD_SUBSCRIPTION_RESULT_ID, null);
				if (hash != null) {
					mapResults.put(hash, mapResult);
				} else {
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "rpcSubscriptionListReceived: No hash for " + mapResult);
					}
				}

				boolean isRead = MapUtils.getMapBoolean(mapResult,
						TransmissionVars.FIELD_SUBSCRIPTION_RESULT_ISREAD, false);
				if (!isRead) {
					numNew++;
				}
			}

			runOnUiThread(
					() -> subscriptionResultsAdapter.getFilter().refilter(false));

		}

	}

	@Override
	public void rpcSubscriptionListRefreshing(boolean isRefreshing) {
		this.isRefreshing = isRefreshing;
		setRefreshVisible(isRefreshing);
	}

	private void setRefreshVisible(final boolean visible) {
		runOnUiThread(() -> {
			if (isFinishing()) {
				return;
			}
			if (swipeRefresh != null) {
				swipeRefresh.setRefreshing(visible);
			}
			if (progressBarManager != null) {
				if (visible) {
					progressBarManager.show();
				} else {
					progressBarManager.hide();
				}
			}
		});
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
		actionBar.setSubtitle(remoteProfile.getNick());

		mActionModeCallback = new ActionMode.Callback() {
			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
				return handleMenu(item.getItemId());
			}

			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu) {
				if (AndroidUtils.DEBUG_MENU) {
					Log.d(TAG, "onCreateActionMode");
				}

				if (subscriptionResultsAdapter.getSelectedPosition() < 0) {
					return false;
				}

				getMenuInflater().inflate(R.menu.menu_context_subscription, menu);

				return true;
			}

			@Override
			public void onDestroyActionMode(ActionMode mode) {
				if (AndroidUtils.DEBUG_MENU) {
					Log.d(TAG, "destroyActionMode");
				}
				if (mActionMode == null) {
					return;
				}
				mActionMode = null;

				subscriptionResultsAdapter.clearChecked();
			}

			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				AndroidUtils.fixupMenuAlpha(menu);
				return true;
			}
		};

		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeButtonEnabled(true);
	}

	@Override
	public SortableRecyclerAdapter getMainAdapter() {
		return subscriptionResultsAdapter;
	}

	@Override
	public SideActionSelectionListener getSideActionSelectionListener() {
		return null;
	}

	@Override
	public void onSideListHelperVisibleSetup(View view) {
		super.onSideListHelperVisibleSetup(view);
		tvFilterAgeCurrent = view.findViewById(R.id.ms_filter_age_current);
		tvFilterSizeCurrent = view.findViewById(R.id.ms_filter_size_current);
		tvFilterCurrent = view.findViewById(R.id.sidefilter_current);

		updateFilterTexts();
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

	// >>> Action Mode & Menu

	@Thunk
	boolean showContextMenu() {
		String s;
		List<Map> selectedSubscriptions = getChosenSubscriptions();
		int checkedItemCount = selectedSubscriptions.size();
		if (checkedItemCount == 0) {
			return false;
		}
		if (checkedItemCount == 1) {
			Map item = selectedSubscriptions.get(0);
			s = getResources().getString(R.string.subscription_actions_for,
					MapUtils.getMapString(item,
							TransmissionVars.FIELD_SUBSCRIPTION_RESULT_NAME, "???"));
		} else {
			s = getResources().getQuantityString(
					R.plurals.subscription_actions_for_multiple, checkedItemCount,
					checkedItemCount);
		}

		return AndroidUtilsUI.popupContextMenu(this, new Callback() {
			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
				return handleMenu(item.getItemId());
			}

			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu) {
				if (AndroidUtils.DEBUG_MENU) {
					Log.d(TAG, "onCreateActionMode");
				}

				if (subscriptionResultsAdapter.getSelectedPosition() < 0) {
					return false;
				}

				getMenuInflater().inflate(R.menu.menu_context_subscription, menu);

				if (AndroidUtils.usesNavigationControl()) {
					MenuItem add = menu.add(R.string.select_multiple_items);
					add.setCheckable(true);
					add.setChecked(subscriptionResultsAdapter.isMultiCheckMode());
					add.setOnMenuItemClickListener(item -> {
						boolean turnOn = !subscriptionResultsAdapter.isMultiCheckModeAllowed();

						subscriptionResultsAdapter.setMultiCheckModeAllowed(turnOn);
						if (turnOn) {
							subscriptionResultsAdapter.setMultiCheckMode(true);
							subscriptionResultsAdapter.setItemChecked(
									subscriptionResultsAdapter.getSelectedPosition(), true);
						}
						return true;
					});
				}

				return true;
			}

			@Override
			public void onDestroyActionMode(ActionMode mode) {
				if (AndroidUtils.DEBUG_MENU) {
					Log.d(TAG, "destroyActionMode");
				}
				if (mActionMode == null) {
					return;
				}
				mActionMode = null;

				subscriptionResultsAdapter.clearChecked();
			}

			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				if (!SessionManager.hasSession(remoteProfileID)) {
					return false;
				}
				AndroidUtils.fixupMenuAlpha(menu);
				return true;
			}
		}, s);
	}

	@Thunk
	boolean showContextualActions() {
		if (AndroidUtils.isTV(this)) {
			// TV doesn't get action bar changes, because it's impossible to get to
			// with remote control when you are on row 4000
			return false;
		}
		if (mActionMode != null) {
			if (AndroidUtils.DEBUG_MENU) {
				Log.d(TAG, "showContextualActions: invalidate existing");
			}
			List<Map> selectedSubscriptions = getChosenSubscriptions();
			Map map = selectedSubscriptions.size() > 0 ? selectedSubscriptions.get(0)
					: null;
			String name = MapUtils.getMapString(map,
					TransmissionVars.FIELD_SUBSCRIPTION_RESULT_NAME, null);
			mActionMode.setSubtitle(name);

			mActionMode.invalidate();
			return false;
		}

		// Start the CAB using the ActionMode.Callback defined above
		mActionMode = startSupportActionMode(mActionModeCallback);
		if (mActionMode == null) {
			Log.d(TAG,
					"showContextualActions: startSupportsActionMode returned null");
			return false;
		}

		mActionMode.setTitle(R.string.context_subscription_title);
		List<Map> selectedSubscriptions = getChosenSubscriptions();
		Map map = selectedSubscriptions.size() > 0 ? selectedSubscriptions.get(0)
				: null;
		String name = MapUtils.getMapString(map,
				TransmissionVars.FIELD_SUBSCRIPTION_RESULT_NAME, null);
		mActionMode.setSubtitle(name);
		return true;
	}

	public void showOnlyUnseen_clicked(View view) {
		boolean checked = ((Checkable) view).isChecked();
		subscriptionResultsAdapter.getFilter().setFilterOnlyUnseen(checked);
		subscriptionResultsAdapter.getFilter().refilter(false);
	}

	@Thunk
	void updateActionModeText(ActionMode mode) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "MULTI:CHECK CHANGE");
		}

		if (mode != null) {
			String subtitle = getResources().getString(
					R.string.context_torrent_subtitle_selected,
					subscriptionResultsAdapter.getCheckedItemCount());
			mode.setSubtitle(subtitle);
		}
	}

	@Thunk
	void updateFilterTexts() {
		if (!AndroidUtilsUI.isUIThread()) {
			runOnUiThread(this::updateFilterTexts);
			return;
		}

		if (subscriptionResultsAdapter == null) {
			return;
		}

		MetaSearchResultsAdapterFilter filter = subscriptionResultsAdapter.getFilter();

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

		if (filter.isFilterOnlyUnseen()) {
			if (sCombined.length() > 0) {
				sCombined += "\n";
			}
			sCombined += getResources().getString(R.string.only_unseen);
		}

		if (tvFilterCurrent != null) {
			tvFilterCurrent.setText(sCombined);
		}

		if (tvFilterTop != null) {
			SpanTags spanTag = new SpanTags(tvFilterTop, listenerSpanTags);
			spanTag.setLinkTags(false);
			spanTag.setShowIcon(false);
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

	@Thunk
	void updateHeader() {
		runOnUiThread(() -> {
			if (isFinishing()) {
				return;
			}

			ActionBar ab = getSupportActionBar();

			int count = subscriptionResultsAdapter.getItemCount();

			String s = getResources().getQuantityString(
					R.plurals.subscription_results_subheader, count,
					DisplayFormatters.formatNumber(count),
					DisplayFormatters.formatNumber(numNew));
			Spanned span = AndroidUtils.fromHTML(s);

			if (tvDrawerFilter != null) {
				tvDrawerFilter.setText(span);
			}

			if (tvHeader != null) {
				tvHeader.setText(listName);
			}
			if (ab != null) {
				ab.setSubtitle(span);

				s = getResources().getString(R.string.subscription_results_header,
						listName, session.getRemoteProfile().getNick());
				span = AndroidUtils.fromHTML(s);

				ab.setTitle(span);
			}
		});
	}

	// << Action Mode & Menu

}