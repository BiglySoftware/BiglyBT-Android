/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 * <p/>
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

import java.util.HashMap;
import java.util.Map;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.vuze.android.FlexibleRecyclerSelectionListener;
import com.vuze.android.remote.*;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.dialog.DialogFragmentRcmAuth;
import com.vuze.android.remote.dialog.DialogFragmentRcmAuth.DialogFragmentRcmAuthListener;
import com.vuze.android.remote.rpc.ReplyMapReceivedListener;
import com.vuze.android.remote.rpc.TransmissionRPC;
import com.vuze.android.remote.spanbubbles.SpanBubbles;
import com.vuze.util.MapUtils;

/**
 * Swarm Discoveries activity.
 */
public class RcmActivity
	extends DrawerActivity
	implements RefreshTriggerListener, DialogFragmentRcmAuthListener,
	SwipeRefreshLayoutExtra.OnExtraViewVisibilityChangeListener
{
	@SuppressWarnings("hiding")
	private static final String TAG = "RCM";

	private SessionInfo sessionInfo;

	private RecyclerView listview;

	private long lastUpdated;

	private RcmAdapter adapter;

	private long rcmGotUntil;

	private boolean enabled;

	private boolean supportsRCM;

	private SwipeRefreshLayoutExtra swipeRefresh;

	private Handler pullRefreshHandler;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		AndroidUtilsUI.onCreate(this);
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();

		final Bundle extras = intent.getExtras();
		if (extras == null) {
			Log.e(TAG, "No extras!");
			finish();
			return;
		}

		final String remoteProfileID = extras.getString(
				SessionInfoManager.BUNDLE_KEY);
		sessionInfo = SessionInfoManager.getSessionInfo(remoteProfileID, this);

		if (sessionInfo == null) {
			Log.e(TAG, "No sessionInfo!");
			finish();
			return;
		}

		supportsRCM = sessionInfo.getSupportsRCM();

		if (supportsRCM) {
			sessionInfo.executeRpc(new RpcExecuter() {

				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.simpleRpcCall("rcm-is-enabled", new ReplyMapReceivedListener() {

						@Override
						public void rpcSuccess(String id, Map<?, ?> optionalMap) {
							if (optionalMap == null) {
								return;
							}

							if (!optionalMap.containsKey("ui-enabled")) {
								// old version
								return;
							}
							enabled = MapUtils.getMapBoolean(optionalMap, "ui-enabled",
									false);
							if (enabled) {
								triggerRefresh();
								VuzeEasyTracker.getInstance().sendEvent("RCM", "Show", null,
										null);
							} else {
								if (isFinishing()) {
									// Hopefully fix IllegalStateException in v2.1
									return;
								}
								DialogFragmentRcmAuth.openDialog(RcmActivity.this,
										remoteProfileID);
							}
						}

						@Override
						public void rpcFailure(String id, String message) {
						}

						@Override
						public void rpcError(String id, Exception e) {
						}
					});
				}
			});
		}

		setContentView(
				supportsRCM ? R.layout.activity_rcm : R.layout.activity_rcm_na);
		setupActionBar();

		if (supportsRCM) {
			setupListView();
		} else {
			TextView tvNA = (TextView) findViewById(R.id.rcm_na);

			new SpanBubbles().setSpanBubbles(tvNA, "|",
					AndroidUtilsUI.getStyleColor(this, R.attr.login_text_color),
					AndroidUtilsUI.getStyleColor(this, R.attr.login_textbubble_color),
					AndroidUtilsUI.getStyleColor(this, R.attr.login_text_color));
		}

		onCreate_setupDrawer();
	}

	private void setupListView() {

		FlexibleRecyclerSelectionListener selectionListener = new FlexibleRecyclerSelectionListener<RcmAdapter, String>() {
			@Override
			public void onItemClick(RcmAdapter adapter, int position) {
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
				AndroidUtils.invalidateOptionsMenuHC(RcmActivity.this);
			}
		};

		adapter = new RcmAdapter(this, selectionListener);

		listview = (RecyclerView) findViewById(R.id.rcm_list);
		listview.setLayoutManager(new PreCachingLayoutManager(this));
		listview.setAdapter(adapter);

		swipeRefresh = (SwipeRefreshLayoutExtra) findViewById(R.id.swipe_container);
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
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (supportsRCM) {
			VuzeEasyTracker.getInstance(this).screenStart(TAG);
		} else {
			VuzeEasyTracker.getInstance(this).screenStart(TAG + ":NA");
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		VuzeEasyTracker.getInstance(this).activityStop(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (sessionInfo != null) {
			sessionInfo.activityPaused();
			sessionInfo.removeRefreshTriggerListener(this);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (sessionInfo != null) {
			sessionInfo.activityResumed(this);
			sessionInfo.addRefreshTriggerListener(this);
		}
	}

	private void setupActionBar() {
		Toolbar toolBar = (Toolbar) findViewById(R.id.actionbar);
		if (toolBar != null) {
			setSupportActionBar(toolBar);
		}

		// enable ActionBar app icon to behave as action to toggle nav drawer
		ActionBar actionBar = getSupportActionBar();
		if (actionBar == null) {
			System.err.println("actionBar is null");
			return;
		}

		RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
		if (remoteProfile != null) {
			actionBar.setSubtitle(remoteProfile.getNick());
		}

		// enable ActionBar app icon to behave as action to toggle nav drawer
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
		} else if (itemId == R.id.action_download) {
			int[] checkedItemPositions = adapter.getCheckedItemPositions();
			if (checkedItemPositions.length == 1) {
				Map<?, ?> map = adapter.getMapAtPosition(checkedItemPositions[0]);
				String hash = MapUtils.getMapString(map, "hash", null);
				String name = MapUtils.getMapString(map, "title", null);
				if (hash != null && sessionInfo != null) {
					// TODO: When opening torrent, directory is "dunno" from here!!
					sessionInfo.openTorrent(RcmActivity.this, hash, name);
				}
			}
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "onCreateOptionsMenu");
		}

		super.onCreateOptionsMenu(menu);

		getMenuInflater().inflate(R.menu.menu_rcm_list, menu);

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "onPrepareOptionsMenu");
		}
		if (!onPrepareOptionsMenu_drawer(menu)) {
			return true;
		}

		MenuItem menuDownload = menu.findItem(R.id.action_download);
		if (menuDownload != null) {
			menuDownload.setEnabled(adapter.getCheckedItemCount() > 0);
		}

		AndroidUtils.fixupMenuAlpha(menu);
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public void triggerRefresh() {
		if (sessionInfo == null) {
			return;
		}
		if (!enabled) {
			return;
		}
		sessionInfo.executeRpc(new RpcExecuter() {

			@Override
			public void executeRpc(TransmissionRPC rpc) {
				Map<String, Object> map = new HashMap<>();
				if (rcmGotUntil > 0) {
					map.put("since", rcmGotUntil);
				}
				rpc.simpleRpcCall("rcm-get-list", map, new ReplyMapReceivedListener() {

					@Override
					public void rpcSuccess(String id, final Map<?, ?> map) {
						lastUpdated = System.currentTimeMillis();
						try {
							Log.d(TAG, "rcm-get-list: " + map);
						} catch (Throwable ignored) {
						}
						runOnUiThread(new Runnable() {

							@Override
							public void run() {
								if (isFinishing()) {
									return;
								}
								if (swipeRefresh != null) {
									swipeRefresh.setRefreshing(false);
								}

								long until = MapUtils.getMapLong(map, "until", 0);
								adapter.updateList(MapUtils.getMapList(map, "related", null));
								rcmGotUntil = until;
							}
						});

					}

					@Override
					public void rpcFailure(String id, String message) {
					}

					@Override
					public void rpcError(String id, Exception e) {
					}
				});
			}
		});
	}

	@Override
	public SessionInfo getSessionInfo() {
		return sessionInfo;
	}

	@Override
	public void onDrawerClosed(View view) {
	}

	@Override
	public void onDrawerOpened(View view) {
	}

	@Override
	public void rcmEnabledChanged(boolean enable, boolean all) {
		this.enabled = enable;
		if (enabled) {
			triggerRefresh();
		}
	}

	@Override
	public void onExtraViewVisibilityChange(final View view, int visibility) {
		{
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
	}
}
