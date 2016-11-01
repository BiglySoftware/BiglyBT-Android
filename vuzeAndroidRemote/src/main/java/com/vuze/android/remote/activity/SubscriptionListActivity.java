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

package com.vuze.android.remote.activity;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;
import com.vuze.android.FlexibleRecyclerSelectionListener;
import com.vuze.android.FlexibleRecyclerView;
import com.vuze.android.remote.*;
import com.vuze.android.remote.adapter.SideActionsAdapter;
import com.vuze.android.remote.adapter.SubscriptionListAdapter;
import com.vuze.android.remote.rpc.ReplyMapReceivedListener;
import com.vuze.android.remote.rpc.SubscriptionListReceivedListener;
import com.vuze.android.remote.rpc.TransmissionRPC;
import com.vuze.android.widget.PreCachingLayoutManager;
import com.vuze.android.widget.SwipeRefreshLayoutExtra;
import com.vuze.util.DisplayFormatters;
import com.vuze.util.MapUtils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.*;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Subscription List View
 * <p>
 * Created by TuxPaper on 10/17/16.
 */

public class SubscriptionListActivity
	extends DrawerActivity
	implements SideListHelper.SideSortAPI, SubscriptionListReceivedListener,
	SwipeRefreshLayoutExtra.OnExtraViewVisibilityChangeListener
{
	public static final String TAG = "SubscriptionList";

	private static final String ID_SORT_FILTER = "-sl";

	private static final String DEFAULT_SORT_FIELD = TransmissionVars.FIELD_ENGINE_LASTUPDATE;

	private static final boolean DEFAULT_SORT_ASC = false;

	/* @Thunk */ SessionInfo sessionInfo;

	/* @Thunk */ SubscriptionListAdapter subscriptionListAdapter;

	/* @Thunk */ SwipeRefreshLayoutExtra swipeRefresh;

	/* @Thunk */ Handler pullRefreshHandler;

	/* @Thunk */ long lastUpdated;

	/* @Thunk */ SideListHelper sideListHelper;

	/* @Thunk */ ActionMode mActionMode;

	private ActionMode.Callback mActionModeCallback;

	private RecyclerView lvResults;

	private SortByFields[] sortByFields;

	private RecyclerView listSideActions;

	private SideActionsAdapter sideActionsAdapter;

	private TextView tvHeader;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		AndroidUtilsUI.onCreate(this, TAG);

		super.onCreate(savedInstanceState);

		sessionInfo = SessionInfoManager.findSessionInfo(this, TAG, true);

		if (sessionInfo == null) {
			finish();
			return;
		}

		int SHOW_SIDELIST_MINWIDTH_PX = getResources().getDimensionPixelSize(
				R.dimen.sidelist_subscriptionlist_drawer_until_screen);

		setContentView(AndroidUtils.isTV() ? R.layout.activity_subscriptionlist_tv
				: AndroidUtilsUI.getScreenWidthPx(this) >= SHOW_SIDELIST_MINWIDTH_PX
						? R.layout.activity_subscriptionlist
						: R.layout.activity_subscriptionlist_drawer);
		setupActionBar();

		onCreate_setupDrawer();

		tvHeader = (TextView) findViewById(R.id.subscriptions_header);

		subscriptionListAdapter = new SubscriptionListAdapter(this,
				new SubscriptionListAdapter.SubscriptionSelectionListener() {
					@Override
					public void onItemCheckedChanged(SubscriptionListAdapter adapter,
							String item, boolean isChecked) {

						if (!adapter.isMultiCheckMode()) {
							if (adapter.getCheckedItemCount() == 1) {
								Intent intent = new Intent(Intent.ACTION_VIEW, null,
										SubscriptionListActivity.this, SubscriptionActivity.class);
								intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
								intent.putExtra("subscriptionID",
										subscriptionListAdapter.getCheckedItems().get(0));
								intent.putExtra("RemoteProfileID",
										sessionInfo.getRemoteProfile().getID());
								startActivity(intent);

								adapter.clearChecked();
							}
							return;
						} else {
							updateActionModeText(mActionMode);
						}

						if (adapter.getCheckedItemCount() == 0) {
							finishActionMode();
						} else {
							showContextualActions();
						}

						AndroidUtils.invalidateOptionsMenuHC(SubscriptionListActivity.this,
								mActionMode);
					}

					@Override
					public void onItemClick(SubscriptionListAdapter adapter,
							int position) {

					}

					@Override
					public boolean onItemLongClick(SubscriptionListAdapter adapter,
							int position) {
						return false;
					}

					@Override
					public void onItemSelected(SubscriptionListAdapter adapter,
							int position, boolean isChecked) {

					}

					@Override
					public List<String> getSubscriptionList() {
						return sessionInfo.getSubscriptionList();
					}

					@Override
					public Map getSubscriptionMap(String key) {
						return sessionInfo.getSubscription(key);
					}
				}) {
			@Override
			public void lettersUpdated(HashMap<String, Integer> mapLetterCount) {
				sideListHelper.lettersUpdated(mapLetterCount);
			}
		};
		subscriptionListAdapter.setMultiCheckModeAllowed(true);
		subscriptionListAdapter.registerAdapterDataObserver(
				new RecyclerView.AdapterDataObserver() {
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

		lvResults = (RecyclerView) findViewById(R.id.sl_list_results);
		lvResults.setAdapter(subscriptionListAdapter);
		lvResults.setLayoutManager(new PreCachingLayoutManager(this));

		if (AndroidUtils.isTV()) {
			((FastScrollRecyclerView) lvResults).setEnableFastScrolling(false);
			((FlexibleRecyclerView) lvResults).setFixedVerticalHeight(
					AndroidUtilsUI.dpToPx(48));
			lvResults.setVerticalFadingEdgeEnabled(true);
			lvResults.setFadingEdgeLength(AndroidUtilsUI.dpToPx((int) (48 * 1.5)));
		}

		swipeRefresh = (SwipeRefreshLayoutExtra) findViewById(R.id.swipe_container);
		if (swipeRefresh != null) {
			swipeRefresh.setExtraLayout(R.layout.swipe_layout_extra);

			swipeRefresh.setOnRefreshListener(
					new SwipeRefreshLayout.OnRefreshListener() {
						@Override
						public void onRefresh() {
							sessionInfo.refreshSubscriptionList();
						}
					});
			swipeRefresh.setOnExtraViewVisibilityChange(this);
		}

		setupSideListArea(this.getWindow().getDecorView());

		RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
		String[] sortBy = remoteProfile.getSortBy(ID_SORT_FILTER,
				DEFAULT_SORT_FIELD);
		Boolean[] sortOrder = remoteProfile.getSortOrderAsc(ID_SORT_FILTER,
				DEFAULT_SORT_ASC);
		if (sortBy != null) {
			int which = TorrentUtils.findSordIdFromTorrentFields(this, sortBy,
					getSortByFields(this));
			sortBy(sortBy, sortOrder, which, false);
		}

		sessionInfo.refreshSubscriptionList();
	}

	/* @Thunk */ void updateFilterTexts() {
		if (!AndroidUtilsUI.isUIThread()) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					updateFilterTexts();
				}
			});
			return;
		}

		if (subscriptionListAdapter == null || isFinishing()) {
			return;
		}

		int count = sessionInfo.getSubscriptionListCount();
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
	public void onDrawerOpened(View view) {
		setupSideListArea(view);
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
				String since = DateUtils.getRelativeDateTimeString(
						SubscriptionListActivity.this, lastUpdated,
						DateUtils.SECOND_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString();
				String s = getResources().getString(R.string.last_updated, since);

				TextView tvSwipeText = (TextView) view.findViewById(R.id.swipe_text);
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
	public boolean onOptionsItemSelected(MenuItem item) {
		if (onOptionsItemSelected_drawer(item)) {
			return true;
		}

		int itemId = item.getItemId();
		if (itemId == R.id.action_add_subscription) {

			AlertDialog dialog = AndroidUtilsUI.createTextBoxDialog(this,
					R.string.action_add_subscription, R.string.subscription_add_hint,
					new AndroidUtilsUI.OnTextBoxDialogClick() {
						@Override
						public void onClick(DialogInterface dialog, int which,
								EditText editText) {
							createRssSubscription(editText.getText().toString());
						}
					});

			dialog.show();
			return true;
		} else if (itemId == android.R.id.home) {
			finish();
			return true;
		}

		return super.onOptionsItemSelected(item);

	}

	@Override
	protected void onPause() {
		super.onPause();
		if (sessionInfo != null) {
			sessionInfo.activityPaused();
			sessionInfo.removeSubscriptionListReceivedListener(this);
		}
	}

	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		if (subscriptionListAdapter != null) {
			subscriptionListAdapter.onRestoreInstanceState(savedInstanceState,
					lvResults);
		}
		if (sideListHelper != null) {
			sideListHelper.onRestoreInstanceState(savedInstanceState);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (sessionInfo != null) {
			sessionInfo.activityResumed(this);
			sessionInfo.addSubscriptionListReceivedListener(this, lastUpdated);
		}
		if (sideListHelper != null) {
			sideListHelper.onResume();
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState,
			PersistableBundle outPersistentState) {
		super.onSaveInstanceState(outState);
		if (subscriptionListAdapter != null) {
			subscriptionListAdapter.onSaveInstanceState(outState);
		}
		if (sideListHelper != null) {
			sideListHelper.onSaveInstanceState(outState);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).activityStart(this);
	}

	public InputStream getInputStream(URL url) {
		try {
			return url.openConnection().getInputStream();
		} catch (IOException e) {
			return null;
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		VuzeEasyTracker.getInstance(this).activityStop(this);
	}

	/* @Thunk */ void createRssSubscription(final String rssURL) {

		new AsyncTask<String, Void, String>() {

			@Override
			protected String doInBackground(String... params) {
				String name = "Test";
				try {
					XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
					factory.setNamespaceAware(false);
					XmlPullParser xpp = factory.newPullParser();
					xpp.setInput(getInputStream(new URL(rssURL)), "UTF_8");
					int eventType = xpp.getEventType();
					while (eventType != XmlPullParser.END_DOCUMENT) {
						if (eventType == XmlPullParser.START_TAG) {

							if (xpp.getName().equalsIgnoreCase("item")) {
								break;
							} else if (xpp.getName().equalsIgnoreCase("title")) {
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
				sessionInfo.executeRpc(new SessionInfo.RpcExecuter() {
					@Override
					public void executeRpc(TransmissionRPC rpc) {
						rpc.createSubscription(rssURL, name,
								new ReplyMapReceivedListener() {
									@Override
									public void rpcError(String id, Exception e) {

									}

									@Override
									public void rpcFailure(String id, String message) {

									}

									@Override
									public void rpcSuccess(String id, Map<?, ?> optionalMap) {
										sessionInfo.refreshSubscriptionList();
									}

								});
					}
				});
			}
		}.execute(rssURL);

	}

	public void finishActionMode() {
		if (mActionMode != null) {
			mActionMode.finish();
			mActionMode = null;
		}
	}

	public int findSordIdFromTorrentFields(Context context, String[] fields) {
		SortByFields[] sortByFields = getSortByFields(context);
		return TorrentUtils.findSordIdFromTorrentFields(context, fields,
				sortByFields);
	}

	@Override
	public void flipSortOrder() {
		if (sessionInfo == null) {
			return;
		}
		RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
		if (remoteProfile == null) {
			return;
		}
		Boolean[] sortOrder = remoteProfile.getSortOrderAsc(ID_SORT_FILTER,
				DEFAULT_SORT_ASC);
		if (sortOrder == null) {
			return;
		}
		for (int i = 0; i < sortOrder.length; i++) {
			sortOrder[i] = !sortOrder[i];
		}
		String[] sortBy = remoteProfile.getSortBy(ID_SORT_FILTER,
				DEFAULT_SORT_FIELD);
		sortBy(sortBy, sortOrder, findSordIdFromTorrentFields(this, sortBy), true);
	}

	@Override
	public SessionInfo getSessionInfo() {
		return sessionInfo;
	}

	@Override
	public SortByFields[] getSortByFields(Context context) {
		if (sortByFields != null) {
			return sortByFields;
		}
		String[] sortNames = context.getResources().getStringArray(
				R.array.sortby_sl_list);

		sortByFields = new SortByFields[sortNames.length];
		int i = 0;

		//<item>Name</item>
		sortByFields[i] = new SortByFields(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_SUBSCRIPTIONLIST_NAME
		}, new Boolean[] {
			false
		}, true);

		i++; // <item>Last Checked</item>
		sortByFields[i] = new SortByFields(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_ENGINE_LASTUPDATE
		}, new Boolean[] {
			false
		});

		i++; // <item># New</item>
		sortByFields[i] = new SortByFields(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_SUBSCRIPTIONLIST_NEWCOUNT
		}, new Boolean[] {
			false
		});

		return sortByFields;
	}

	/* @Thunk */ boolean handleMenu(int itemId) {
		if (sessionInfo == null) {
			return false;
		}
		if (itemId == R.id.action_sel_remove) {
			sessionInfo.executeRpc(new SessionInfo.RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {

					rpc.removeSubscription(
							subscriptionListAdapter.getCheckedItems().get(0),
							new ReplyMapReceivedListener() {
								@Override
								public void rpcError(String id, Exception e) {
									AndroidUtilsUI.showDialog(SubscriptionListActivity.this,
											"Remove Subscription", "Failed: " + e.toString());
								}

								@Override
								public void rpcFailure(String id, String message) {

									AndroidUtilsUI.showDialog(SubscriptionListActivity.this,
											"Remove Subscription", "Failed: " + message);
								}

								@Override
								public void rpcSuccess(String id, Map<?, ?> optionalMap) {
									sessionInfo.refreshSubscriptionList();
								}
							});
				}
			});
			return true;
		}

		return false;
	}

	@Override
	public void rpcSubscriptionListError(String id, @NonNull Exception e) {
	}

	@Override
	public void rpcSubscriptionListFailure(String id, @NonNull String message) {
		AndroidUtilsUI.showDialog(this, "Failure", message);
	}

	@Override
	public void rpcSubscriptionListReceived(@NonNull List<String> subscriptions) {

		if (subscriptions.size() == 0) {
			// TODO: Show "No subscriptions" message
			return;
		}

		subscriptionListAdapter.getFilter().refilter();

		lastUpdated = System.currentTimeMillis();
	}

	@Override
	public void rpcSubscriptionListRefreshing(boolean isRefreshing) {
		setRefreshVisible(isRefreshing);
	}

	private void setupActionBar() {
		Toolbar abToolBar = (Toolbar) findViewById(R.id.actionbar);
		if (abToolBar == null) {
			return;
		}

		if (AndroidUtils.isTV()) {
			abToolBar.setVisibility(View.GONE);
			return;
		}

		try {
			setSupportActionBar(abToolBar);

			RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
			if (remoteProfile != null) {
				abToolBar.setSubtitle(remoteProfile.getNick());
			}
		} catch (NullPointerException ignore) {
		}

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
				if (sessionInfo == null) {
					return false;
				}
				AndroidUtils.fixupMenuAlpha(menu);
				return true;
			}
		};

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setHomeButtonEnabled(true);
		}
	}

	public void updateActionModeText(ActionMode mode) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "MULTI:CHECK CHANGE");
		}

		if (mode != null) {
			String subtitle = getResources().getString(
					R.string.context_torrent_subtitle_selected,
					subscriptionListAdapter.getCheckedItemCount());
			mode.setSubtitle(subtitle);
		}
	}

	private void setupSideListArea(View view) {
		Toolbar abToolBar = (Toolbar) findViewById(R.id.actionbar);

		boolean showActionsArea = abToolBar == null
				|| abToolBar.getVisibility() == View.GONE;
		if (!showActionsArea) {
			View viewToHide = findViewById(R.id.sideactions_header);
			if (viewToHide != null) {
				viewToHide.setVisibility(View.GONE);
			}
			viewToHide = findViewById(R.id.sideactions_list);
			if (viewToHide != null) {
				viewToHide.setVisibility(View.GONE);
			}
		}

		if (sideListHelper == null || !sideListHelper.isValid()) {
			sideListHelper = new SideListHelper(this, view, R.id.sidelist_layout, 0,
					0, 0, 0, 500);
			if (!sideListHelper.isValid()) {
				return;
			}

			if (showActionsArea) {
				sideListHelper.addEntry(view, R.id.sideactions_header,
						R.id.sideactions_list);
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
					R.id.sidefilter_text, lvResults, subscriptionListAdapter.getFilter());

			setupSideFilters();

			sideListHelper.setupSideSort(view, R.id.sidesort_list,
					R.id.sidelist_sort_current, this);

			if (showActionsArea) {
				setupSideActions(view);
			}

			sideListHelper.expandedStateChanging(sideListHelper.isExpanded());
			sideListHelper.expandedStateChanged(sideListHelper.isExpanded());
		} else if (AndroidUtils.DEBUG) {
			Log.d(TAG,
					"setupSideListArea: sidelist not visible -- not setting up (until "
							+ "drawer is opened)");
		}

		if (sideListHelper.hasSideTextFilterArea()) {
			subscriptionListAdapter.getFilter().setBuildLetters(true);
		}
	}

	private void setupSideFilters() {
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.FROYO) {

			//java.lang.IllegalStateException: Could not find a method
			// showSearchTemplates_clicked(View) in the activity class
			// com.vuze.android.remote.activity.SubscriptionListActivity
			// for onClick handler on view class
			// android.support.v7.widget.SwitchCompat with id
			// 'sidefilter_showsearchtemplates'
			//Caused by: java.lang.NoSuchMethodException
			//at java.lang.Class.getDeclaredMethods(Native Method)
			// Possibly https://medium.com/square-corner-blog/chasing-a-cunning-android-bug-37fb305cebb8

			View v = findViewById(R.id.sidefilter_showsearchtemplates);
			v.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showSearchTemplates_clicked(v);
				}
			});
			v = findViewById(R.id.sidefilter_showonlyunseen);
			v.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showOnlyUnseen_clicked(v);
				}
			});
		}
	}

	private void setupSideActions(View view) {
		RecyclerView oldRV = listSideActions;
		listSideActions = (RecyclerView) view.findViewById(R.id.sideactions_list);
		if (listSideActions == null) {
			return;
		}
		if (oldRV == listSideActions) {
			return;
		}

		listSideActions.setLayoutManager(new PreCachingLayoutManager(this));

		sideActionsAdapter = new SideActionsAdapter(this, sessionInfo,
				R.menu.menu_subscriptionlist, null,
				new FlexibleRecyclerSelectionListener<SideActionsAdapter, SideActionsAdapter.SideActionsInfo>() {
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
				});
		listSideActions.setAdapter(sideActionsAdapter);
	}

	protected boolean showContextualActions() {
		if (AndroidUtils.isTV()) {
			// TV doesn't get action bar changes, because it's impossible to get to
			// with remote control when you are on row 4000
			return false;
		}
		if (mActionMode != null) {
			if (AndroidUtils.DEBUG_MENU) {
				Log.d(TAG, "showContextualActions: invalidate existing");
			}
			Map map = sessionInfo.getSubscription(
					subscriptionListAdapter.getCheckedItems().get(0));
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
		Map map = sessionInfo.getSubscription(
				subscriptionListAdapter.getCheckedItems().get(0));
		String name = MapUtils.getMapString(map, "name", null);
		mActionMode.setSubtitle(name);
		return true;
	}

	public void showSearchTemplates_clicked(View view) {
		boolean checked = ((SwitchCompat) view).isChecked();
		subscriptionListAdapter.getFilter().setFilterShowSearchTemplates(checked);
	}

	public void showOnlyUnseen_clicked(View view) {
		boolean checked = ((SwitchCompat) view).isChecked();
		subscriptionListAdapter.getFilter().setFilterOnlyUnseen(checked);
	}

	@Override
	public void sortBy(String[] sortFieldIDs, final Boolean[] sortOrderAsc,
			final int which, boolean save) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "SORT BY " + Arrays.toString(sortFieldIDs));
		}
		if (subscriptionListAdapter != null) {
			subscriptionListAdapter.setSort(sortFieldIDs, sortOrderAsc);
		}
		runOnUiThread(new Runnable() {
			public void run() {
				if (isFinishing()) {
					return;
				}
				sideListHelper.setCurrentSort(SubscriptionListActivity.this, which,
						sortOrderAsc[0]);
			}
		});

		if (save && sessionInfo != null) {
			sessionInfo.getRemoteProfile().setSortBy(ID_SORT_FILTER, sortFieldIDs,
					sortOrderAsc);
			sessionInfo.saveProfile();
		}
	}

	public void setRefreshVisible(final boolean visible) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				if (isFinishing()) {
					return;
				}
				if (swipeRefresh != null) {
					swipeRefresh.setRefreshing(visible);
				}
				ProgressBar progressBar = (ProgressBar) findViewById(
						R.id.progress_spinner);
				if (progressBar != null) {
					progressBar.setVisibility(visible ? View.VISIBLE : View.GONE);
				}
			}
		});
	}

}
