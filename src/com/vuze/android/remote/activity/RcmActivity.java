/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

import java.util.HashMap;
import java.util.Map;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.res.Resources;
import android.os.*;
import android.support.v7.app.ActionBar;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;

import com.aelitis.azureus.util.MapUtils;
import com.google.analytics.tracking.android.MapBuilder;
import com.handmark.pulltorefresh.library.*;
import com.handmark.pulltorefresh.library.PullToRefreshBase.Mode;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnPullEventListener;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.handmark.pulltorefresh.library.PullToRefreshBase.State;
import com.vuze.android.remote.*;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.R;
import com.vuze.android.remote.dialog.DialogFragmentRcmAuth;
import com.vuze.android.remote.dialog.DialogFragmentRcmAuth.DialogFragmentRcmAuthListener;
import com.vuze.android.remote.fragment.TorrentListFragment;
import com.vuze.android.remote.rpc.ReplyMapReceivedListener;
import com.vuze.android.remote.rpc.TransmissionRPC;

/**
 * Activity to hold {@link TorrentListFragment}.  Used for narrow screens.
 */
public class RcmActivity
	extends DrawerActivity
	implements RefreshTriggerListener, DialogFragmentRcmAuthListener
{
	private static final String TAG = "RCM";

	private SessionInfo sessionInfo;

	private ListView listview;

	private PullToRefreshListView pullListView;

	private long lastUpdated;

	private RcmAdapter adapter;

	private long rcmGotUntil;

	private boolean enabled;

	private boolean supportsRCM;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();

		final Bundle extras = intent.getExtras();
		if (extras == null) {
			Log.e(TAG, "No extras!");
			finish();
			return;
		}

		final String remoteProfileID = extras.getString(SessionInfoManager.BUNDLE_KEY);
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
							enabled = MapUtils.getMapBoolean(optionalMap, "ui-enabled", false);
							if (enabled) {
								triggerRefresh();
								VuzeEasyTracker.getInstance().send(
										MapBuilder.createEvent("RCM", "Show", null, null).build());
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

		setupActionBar();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			setupIceCream();
		}

		setContentView(supportsRCM ? R.layout.activity_rcm
				: R.layout.activity_rcm_na);

		if (supportsRCM) {
			setupListView();
		} else {
			Resources res = getResources();
			TextView tvNA = (TextView) findViewById(R.id.rcm_na);

			AndroidUtils.setSpanBubbles(tvNA, "|",
					res.getColor(R.color.login_link_color),
					res.getColor(R.color.login_link_color),
					res.getColor(R.color.login_text_color));
		}

		onCreate_setupDrawer();
	}

	private void setupListView() {
		View oListView = findViewById(R.id.rcm_list);
		if (oListView instanceof ListView) {
			listview = (ListView) oListView;
		} else if (oListView instanceof PullToRefreshListView) {
			pullListView = (PullToRefreshListView) oListView;
			listview = pullListView.getRefreshableView();
			pullListView.setOnPullEventListener(new OnPullEventListener<ListView>() {
				private Handler pullRefreshHandler;

				@Override
				public void onPullEvent(PullToRefreshBase<ListView> refreshView,
						State state, Mode direction) {
					if (state == State.PULL_TO_REFRESH) {
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
										RcmActivity.this, lastUpdated, DateUtils.SECOND_IN_MILLIS,
										DateUtils.WEEK_IN_MILLIS, 0).toString();
								String s = getResources().getString(R.string.last_updated,
										since);
								if (pullListView.getState() != State.REFRESHING) {
									pullListView.getLoadingLayoutProxy().setLastUpdatedLabel(s);
								}

								if (pullRefreshHandler != null) {
									pullRefreshHandler.postDelayed(this,
											sinceMS < DateUtils.MINUTE_IN_MILLIS
													? DateUtils.SECOND_IN_MILLIS
													: sinceMS < DateUtils.HOUR_IN_MILLIS
															? DateUtils.MINUTE_IN_MILLIS
															: DateUtils.HOUR_IN_MILLIS);
								}
							}
						}, 0);
					} else if (state == State.RESET || state == State.REFRESHING) {
						if (pullRefreshHandler != null) {
							pullRefreshHandler.removeCallbacksAndMessages(null);
							pullRefreshHandler = null;
						}
					}
				}
			});
			pullListView.setOnRefreshListener(new OnRefreshListener<ListView>() {
				@Override
				public void onRefresh(PullToRefreshBase<ListView> refreshView) {
					triggerRefresh();
				}

			});
		}

		listview.setItemsCanFocus(false);
		listview.setClickable(true);
		listview.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

		listview.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position,
					long id) {
				AndroidUtils.invalidateOptionsMenuHC(RcmActivity.this);
			}

		});

		adapter = new RcmAdapter(this);
		listview.setItemsCanFocus(true);
		listview.setAdapter(adapter);
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

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private void setupIceCream() {
		getActionBar().setHomeButtonEnabled(true);
	}

	private void setupActionBar() {
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
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		if (onOptionsItemSelected_drawer(item)) {
			return true;
		}
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				return true;
			case R.id.action_download: {
				Map<?, ?> map = AndroidUtils.getFirstChecked(listview);
				String hash = MapUtils.getMapString(map, "hash", null);
				if (hash != null && sessionInfo != null) {
					sessionInfo.openTorrent(RcmActivity.this, hash);
				}
				break;
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
			menuDownload.setEnabled(AndroidUtils.getCheckedItemCount(listview) > 0);
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
				map.put("since", rcmGotUntil);
				rpc.simpleRpcCall("rcm-get-list", map, new ReplyMapReceivedListener() {

					@Override
					public void rpcSuccess(String id, final Map<?, ?> map) {
						lastUpdated = System.currentTimeMillis();
						System.out.println("rcm-get-list: " + map);
						runOnUiThread(new Runnable() {

							@Override
							public void run() {
								if (isFinishing()) {
									return;
								}
								pullListView.onRefreshComplete();

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
}
