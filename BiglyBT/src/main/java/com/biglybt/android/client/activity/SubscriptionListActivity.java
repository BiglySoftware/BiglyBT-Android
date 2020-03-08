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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.*;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.*;
import android.widget.Checkable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.Toolbar;
import androidx.leanback.app.ProgressBarManager;
import androidx.recyclerview.widget.RecyclerView;

import com.biglybt.android.adapter.DelayedFilter;
import com.biglybt.android.adapter.SortableRecyclerAdapter;
import com.biglybt.android.client.*;
import com.biglybt.android.client.adapter.SubscriptionListAdapter;
import com.biglybt.android.client.adapter.SubscriptionListAdapterFilter;
import com.biglybt.android.client.rpc.RPCSupports;
import com.biglybt.android.client.rpc.SubscriptionListReceivedListener;
import com.biglybt.android.client.session.*;
import com.biglybt.android.client.sidelist.SideActionSelectionListener;
import com.biglybt.android.client.sidelist.SideActionsAdapter;
import com.biglybt.android.client.sidelist.SideListActivity;
import com.biglybt.android.client.spanbubbles.SpanBubbles;
import com.biglybt.android.util.MapUtils;
import com.biglybt.android.widget.PreCachingLayoutManager;
import com.biglybt.android.widget.SwipeRefreshLayoutExtra;
import com.biglybt.android.widget.SwipeRefreshLayoutExtra.SwipeTextUpdater;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.Thunk;
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Subscription List View
 * <p>
 * Created by TuxPaper on 10/17/16.
 */

public class SubscriptionListActivity
	extends SideListActivity
	implements SubscriptionListReceivedListener
{
	private static final String TAG = "SubscriptionList";

	private static class subscribeUrlAsyncTask
		extends AsyncTask<Object, Void, String>
	{
		String rssURL;

		Session session;

		@Override
		protected String doInBackground(Object... params) {
			rssURL = (String) params[0];
			session = (Session) params[1];
			String name = "Test";
			try {
				XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
				factory.setNamespaceAware(false);
				XmlPullParser xpp = factory.newPullParser();
				xpp.setInput(getInputStream(new URL(rssURL)), "UTF_8");
				int eventType = xpp.getEventType();
				while (eventType != XmlPullParser.END_DOCUMENT) {
					if (eventType == XmlPullParser.START_TAG) {

						if ("item".equalsIgnoreCase(xpp.getName())) {
							break;
						} else if ("title".equalsIgnoreCase(xpp.getName())) {
							name = xpp.nextText();
						}
					}

					eventType = xpp.next(); //move to next element
				}
			} catch (Throwable t) {
				Log.e(TAG, "createRssSubscription: ", t);
			}
			return name;
		}

		@Override
		protected void onPostExecute(final String name) {
			session.subscription.createSubscription(rssURL, name);
		}
	}

	@Thunk
	SubscriptionListAdapter subscriptionListAdapter;

	@Thunk
	SwipeRefreshLayoutExtra swipeRefresh;

	@Thunk
	Handler pullRefreshHandler;

	@Thunk
	long lastUpdated;

	@Thunk
	ActionMode mActionMode;

	private ActionMode.Callback mActionModeCallback;

	private RecyclerView lvResults;

	private TextView tvHeader;

	private TextView tvFilterCurrent;

	@Thunk
	boolean isRefreshing;

	private SideActionSelectionListener sideActionSelectionListener;

	private ProgressBarManager progressBarManager;

	@Override
	protected void onCreateWithSession(@Nullable Bundle savedInstanceState) {
		int SHOW_SIDELIST_MINWIDTH_PX = getResources().getDimensionPixelSize(
				R.dimen.sidelist_subscriptionlist_drawer_until_screen);

		boolean supportsSubscriptions = session.getSupports(
				RPCSupports.SUPPORTS_SUBSCRIPTIONS);

		if (!supportsSubscriptions) {
			setContentView(R.layout.activity_rcm_na);

			TextView tvNA = findViewById(R.id.rcm_na);

			String text = getResources().getString(R.string.rcm_na,
					getResources().getString(R.string.title_activity_subscriptions));

			SpanBubbles.setSpanBubbles(tvNA, text, "|",
					AndroidUtilsUI.getStyleColor(this, R.attr.login_text_color),
					AndroidUtilsUI.getStyleColor(this, R.attr.login_textbubble_color),
					AndroidUtilsUI.getStyleColor(this, R.attr.login_text_color), null);

			return;
		}

		setContentView(
				AndroidUtils.isTV(this) ? R.layout.activity_subscriptionlist_tv
						: AndroidUtilsUI.getScreenWidthPx(this) >= SHOW_SIDELIST_MINWIDTH_PX
								? R.layout.activity_subscriptionlist
								: R.layout.activity_subscriptionlist_drawer);
		setupActionBar();
		setupActionModeCallback();

		View progressBar = findViewById(R.id.progress_spinner);
		if (progressBar != null) {
			progressBarManager = new ProgressBarManager();
			progressBarManager.setProgressBarView(progressBar);
		}

		tvHeader = findViewById(R.id.subscriptions_header);

		subscriptionListAdapter = new SubscriptionListAdapter(getLifecycle(), this,
				new SubscriptionListAdapter.SubscriptionSelectionListener() {
					@Override
					public void performingFilteringChanged(int filterState,
							@DelayedFilter.FilterState int oldState) {

					}

					@Override
					public void onItemCheckedChanged(SubscriptionListAdapter adapter,
							String item, boolean isChecked) {

						if (!adapter.isMultiCheckMode()) {
							if (adapter.getCheckedItemCount() == 1) {
								Intent intent = new Intent(Intent.ACTION_VIEW, null,
										SubscriptionListActivity.this,
										SubscriptionResultsActivity.class);
								intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

								String subscriptionID = getCheckedIDs().get(0);
								intent.putExtra("subscriptionID", subscriptionID);
								intent.putExtra(SessionManager.BUNDLE_KEY, remoteProfileID);

								Map subscriptionMap = getSubscriptionMap(subscriptionID);
								String title = MapUtils.getMapString(subscriptionMap,
										TransmissionVars.FIELD_SUBSCRIPTION_NAME, null);
								if (title != null) {
									intent.putExtra("title", title);
								}
								startActivity(intent);

								adapter.clearChecked();
							}
							return;
						} else if (mActionMode != null) {
							String subtitle = getResources().getString(
									R.string.context_torrent_subtitle_selected,
									subscriptionListAdapter.getCheckedItemCount());
							mActionMode.setSubtitle(subtitle);
						}

						if (adapter.getCheckedItemCount() == 0) {
							finishActionMode();
						} else {
							showContextualActions();
						}

						AndroidUtilsUI.invalidateOptionsMenuHC(
								SubscriptionListActivity.this, mActionMode);
					}

					@Override
					public void onItemClick(SubscriptionListAdapter adapter,
							int position) {

					}

					@Override
					public boolean onItemLongClick(SubscriptionListAdapter adapter,
							int position) {
						if (AndroidUtils.usesNavigationControl()) {
							return showSubscriptionContextMenu();
						}
						return false;
					}

					@Override
					public void onItemSelected(SubscriptionListAdapter adapter,
							int position, boolean isChecked) {

					}

					@Override
					public List<String> getSubscriptionList() {
						return session.subscription.getList();
					}

					@Override
					public long getLastReceivedOn() {
						return session.subscription.getLastSubscriptionListReceivedOn();
					}

					@Override
					public Map getSubscriptionMap(String key) {
						return session.subscription.getSubscription(key);
					}
				});
		subscriptionListAdapter.setMultiCheckModeAllowed(
				!AndroidUtils.usesNavigationControl());
		subscriptionListAdapter.addOnSetItemsCompleteListener(
				adapter -> updateFilterTexts());

		lvResults = findViewById(R.id.sl_list_results);
		lvResults.setAdapter(subscriptionListAdapter);
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

			swipeRefresh.setOnRefreshListener(session.subscription::refreshList);
			swipeRefresh.setOnExtraViewVisibilityChange(
					new SwipeTextUpdater(getLifecycle(), (tv) -> {
						long sinceMS = System.currentTimeMillis() - lastUpdated;
						String since = DateUtils.getRelativeDateTimeString(
								SubscriptionListActivity.this, lastUpdated,
								DateUtils.SECOND_IN_MILLIS, DateUtils.WEEK_IN_MILLIS,
								0).toString();
						tv.setText(getResources().getString(R.string.last_updated, since));

						return sinceMS < DateUtils.MINUTE_IN_MILLIS
								? DateUtils.SECOND_IN_MILLIS : DateUtils.MINUTE_IN_MILLIS;
					}));

		}

		///

		Toolbar abToolBar = findViewById(R.id.actionbar);
		boolean canShowSideActionsArea = abToolBar == null
				|| abToolBar.getVisibility() == View.GONE;

		sideActionSelectionListener = canShowSideActionsArea
				? new SideActionSelectionListener() {
					@Override
					public Session getSession() {
						return session;
					}

					@Override
					public boolean isRefreshing() {
						return isRefreshing;
					}

					@Override
					public void prepareActionMenus(Menu menu) {

					}

					@Override
					public MenuBuilder getMenuBuilder() {
						Context context = SubscriptionListActivity.this;
						@SuppressLint("RestrictedApi")
						MenuBuilder menuBuilder = new MenuBuilder(context);
						new MenuInflater(context).inflate(R.menu.menu_subscriptionlist,
								menuBuilder);
						return menuBuilder;
					}

					@Nullable
					@Override
					public int[] getRestrictToMenuIDs() {
						return null;
					}

					@Override
					public void onItemClick(SideActionsAdapter adapter, int position) {
						SideActionsAdapter.SideActionsInfo item = adapter.getItem(position);
						if (item == null) {
							return;
						}

						SubscriptionListActivity.this.onOptionsItemSelected(item.menuItem);
					}

					@Override
					public boolean onItemLongClick(SideActionsAdapter adapter,
							int position) {
						return false;
					}

					@Override
					public void onItemSelected(SideActionsAdapter adapter, int position,
							boolean isChecked) {

					}

					@Override
					public void onItemCheckedChanged(SideActionsAdapter adapter,
							SideActionsAdapter.SideActionsInfo item, boolean isChecked) {

					}
				} : null;

		updateFilterTexts();
	}

	@Thunk
	boolean showSubscriptionContextMenu() {
		int selectedPosition = subscriptionListAdapter.getSelectedPosition();
		if (selectedPosition < 0) {
			return false;
		}
		String s;
		int checkedItemCount = subscriptionListAdapter.getCheckedItemCount();
		if (checkedItemCount <= 1) {
			Map<?, ?> item = session.subscription.getSubscription(
					subscriptionListAdapter.getItem(selectedPosition));
			s = getResources().getString(R.string.subscription_actions_for,
					MapUtils.getMapString(item, "name", "???"));
		} else {
			s = getResources().getQuantityString(
					R.plurals.subscription_actions_for_multiple, checkedItemCount,
					checkedItemCount);
		}

		return AndroidUtilsUI.popupContextMenu(this, mActionModeCallback, s);
	}

	@Thunk
	void updateFilterTexts() {
		if (!AndroidUtilsUI.isUIThread()) {
			runOnUiThread(this::updateFilterTexts);
			return;
		}

		if (subscriptionListAdapter == null || isFinishing()) {
			return;
		}

		SubscriptionListAdapterFilter filter = subscriptionListAdapter.getFilter();
		if (filter == null) {
			return;
		}
		String sCombined = "";

		if (filter.isFilterOnlyUnseen()) {
			if (sCombined.length() > 0) {
				sCombined += "\n";
			}
			sCombined += getResources().getString(R.string.only_unseen);
		}
		if (filter.isFilterShowSearchTemplates()) {
			if (sCombined.length() > 0) {
				sCombined += "\n";
			}
			sCombined += getResources().getString(R.string.search_templates);
		}

		if (tvFilterCurrent != null) {
			tvFilterCurrent.setText(sCombined);
		}

		int count = session.subscription.getListCount();
		int filteredCount = subscriptionListAdapter.getItemCount();
		String countString = DisplayFormatters.formatNumber(count);
		ActionBar actionBar = getSupportActionBar();
		String sResultsCount;
		if (count == filteredCount) {
			sResultsCount = getResources().getQuantityString(
					R.plurals.subscriptionlist_results_count, count, countString);
		} else {
			sResultsCount = getResources().getQuantityString(
					R.plurals.subscriptionlist_filtered_results_count, count,
					DisplayFormatters.formatNumber(filteredCount), countString);
		}
		if (actionBar != null) {
			actionBar.setSubtitle(sResultsCount);
		}
		if (tvHeader != null) {
			tvHeader.setText(sResultsCount);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_subscriptionlist, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		if (onOptionsItemSelected_drawer(item)) {
			return true;
		}

		int itemId = item.getItemId();
		switch (itemId) {
			case R.id.action_add_subscription:

				AlertDialog dialog = AndroidUtilsUI.createTextBoxDialog(this,
						R.string.action_add_subscription, R.string.subscription_add_hint, 0,
						(dialog1, which, editText) -> createRssSubscription(
								editText.getText().toString()));

				dialog.show();
				return true;
			case R.id.action_refresh:
				session.subscription.refreshList();
				return true;
			case android.R.id.home:
				finish();
				return true;
		}

		return super.onOptionsItemSelected(item);

	}

	@Override
	protected void onPause() {
		super.onPause();
		session.subscription.removeListReceivedListener(this);
	}

	@Override
	public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		if (subscriptionListAdapter != null) {
			subscriptionListAdapter.onRestoreInstanceState(savedInstanceState,
					lvResults);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		session.subscription.addListReceivedListener(this, lastUpdated);
		session.subscription.refreshList();
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState,
			@NonNull PersistableBundle outPersistentState) {
		super.onSaveInstanceState(outState, outPersistentState);
		if (subscriptionListAdapter != null) {
			subscriptionListAdapter.onSaveInstanceState(outState);
		}
	}

	@Thunk
	static InputStream getInputStream(URL url) {
		try {
			return url.openConnection().getInputStream();
		} catch (IOException e) {
			return null;
		}
	}

	@Thunk
	void createRssSubscription(String rssURL) {
		new subscribeUrlAsyncTask().execute(rssURL, session);
	}

	@Thunk
	void finishActionMode() {
		if (mActionMode != null) {
			mActionMode.finish();
			mActionMode = null;
		}
	}

	public List<String> getCheckedIDs() {
		List<String> checkedItems = subscriptionListAdapter.getCheckedItems();
		if (checkedItems.size() == 0) {
			String selectedItem = subscriptionListAdapter.getSelectedItem();
			if (selectedItem != null) {
				checkedItems.add(selectedItem);
			}
		}
		return checkedItems;
	}

	@Thunk
	boolean handleMenu(int itemId) {
		if (itemId == R.id.action_sel_remove) {
			List<String> subscriptionIDs = getCheckedIDs();
			session.subscription.removeSubscription(this,
					subscriptionIDs.toArray(new String[0]),
					new Session_Subscription.SubscriptionsRemovedListener() {
						@Override
						public void subscriptionsRemoved(List<String> subscriptionIDs) {

						}

						@Override
						public void subscriptionsRemovalError(
								Map<String, String> mapSubscriptionIDtoError) {

							// TODO: Pull name, i8n, show only one message
							for (String subscriptionID : mapSubscriptionIDtoError.keySet()) {
								String error = mapSubscriptionIDtoError.get(subscriptionID);
								AndroidUtilsUI.showDialog(SubscriptionListActivity.this,
										R.string.remove_subscription, R.string.error_x, error);
							}
						}

						@Override
						public void subscriptionsRemovalException(Throwable t,
								String message) {
							if (t != null) {
								AndroidUtilsUI.showDialog(SubscriptionListActivity.this,
										R.string.remove_subscription, R.string.error_x,
										t.toString());
							} else {
								AndroidUtilsUI.showDialog(SubscriptionListActivity.this,
										R.string.remove_subscription, R.string.error_x, message);
							}
						}
					});

			return true;
		}

		return false;

	}

	@Override
	public void rpcSubscriptionListError(String id, @NonNull Throwable e) {
	}

	@Override
	public void rpcSubscriptionListFailure(String id, @NonNull String message) {
		AndroidUtilsUI.showDialog(this, R.string.failure, R.string.error_x,
				message);
	}

	@Override
	public void rpcSubscriptionListReceived(@NonNull List<String> subscriptions) {

		if (subscriptions.size() == 0) {
			if (subscriptionListAdapter.isNeverSetItems()) {
				subscriptionListAdapter.triggerEmptyList();
			}
			// TODO: Show "No subscriptions" message
			return;
		}

		subscriptionListAdapter.getFilter().refilter(false);

		lastUpdated = System.currentTimeMillis();
	}

	@Override
	public void rpcSubscriptionListRefreshing(boolean isRefreshing) {
		this.isRefreshing = isRefreshing;
		setRefreshVisible(isRefreshing);
	}

	private void setupActionBar() {
		Toolbar abToolBar = findViewById(R.id.actionbar);
		if (abToolBar == null) {
			return;
		}

		if (AndroidUtils.isTV(this)) {
			abToolBar.setVisibility(View.GONE);
			return;
		}

		try {
			setSupportActionBar(abToolBar);

			RemoteProfile remoteProfile = session.getRemoteProfile();
			abToolBar.setSubtitle(remoteProfile.getNick());
		} catch (NullPointerException ignore) {
		}

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setHomeButtonEnabled(true);
		}
	}

	private void setupActionModeCallback() {
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

				if (subscriptionListAdapter.getSelectedPosition() < 0) {
					return false;
				}

				getMenuInflater().inflate(R.menu.menu_context_subscriptionlist, menu);

				if (AndroidUtils.usesNavigationControl()) {
					MenuItem add = menu.add(R.string.select_multiple_items);
					add.setCheckable(true);
					add.setChecked(subscriptionListAdapter.isMultiCheckMode());
					add.setOnMenuItemClickListener(item -> {
						boolean turnOn = !subscriptionListAdapter.isMultiCheckModeAllowed();

						subscriptionListAdapter.setMultiCheckModeAllowed(turnOn);
						if (turnOn) {
							subscriptionListAdapter.setMultiCheckMode(true);
							subscriptionListAdapter.setItemChecked(
									subscriptionListAdapter.getSelectedPosition(), true);
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

				subscriptionListAdapter.clearChecked();
			}

			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				MenuItem item = menu.findItem(R.id.action_auto_download);
				if (item != null) {
					// only allow setting auto-download from SubscriptionResultsActivity
					// so we don't have to bother with handling multiple autoDLSupports here
					item.setVisible(false);
				}

				AndroidUtils.fixupMenuAlpha(menu);
				return true;
			}
		};
	}

	@Override
	public SortableRecyclerAdapter getMainAdapter() {
		return subscriptionListAdapter;
	}

	@Override
	public SideActionSelectionListener getSideActionSelectionListener() {
		return sideActionSelectionListener;
	}

	@Override
	public void onSideListHelperVisibleSetup(View view) {
		super.onSideListHelperVisibleSetup(view);
		tvFilterCurrent = view.findViewById(R.id.sidefilter_current);
	}

	@Override
	public boolean showFilterEntry() {
		return true;
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
			Map<String, Object> map = session.subscription.getSubscription(
					getCheckedIDs().get(0));
			String name = MapUtils.getMapString(map, "name", null);
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
		Map<String, Object> map = session.subscription.getSubscription(
				getCheckedIDs().get(0));
		String name = MapUtils.getMapString(map, "name", null);
		mActionMode.setSubtitle(name);
		return true;
	}

	public void showSearchTemplates_clicked(View view) {
		boolean checked = ((Checkable) view).isChecked();
		SubscriptionListAdapterFilter filter = subscriptionListAdapter.getFilter();
		if (filter != null) {
			filter.setFilterShowSearchTemplates(checked);
		}
	}

	public void showOnlyUnseen_clicked(View view) {
		boolean checked = ((Checkable) view).isChecked();
		SubscriptionListAdapterFilter filter = subscriptionListAdapter.getFilter();
		if (filter != null) {
			filter.setFilterOnlyUnseen(checked);
		}
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

			updateSideListRefreshButton();
		});
	}

}
