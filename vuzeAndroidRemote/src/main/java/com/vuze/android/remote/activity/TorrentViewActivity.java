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

import java.util.Arrays;

import com.vuze.android.remote.*;
import com.vuze.android.remote.NetworkState.NetworkStateListener;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.dialog.DialogFragmentAbout;
import com.vuze.android.remote.dialog.DialogFragmentOpenTorrent;
import com.vuze.android.remote.dialog.DialogFragmentSessionSettings;
import com.vuze.android.remote.fragment.ActionModeBeingReplacedListener;
import com.vuze.android.remote.fragment.TorrentDetailsFragment;
import com.vuze.android.remote.fragment.TorrentListFragment;
import com.vuze.android.remote.fragment.TorrentListFragment.OnTorrentSelectedListener;
import com.vuze.android.remote.rpc.RPCSupports;
import com.vuze.android.remote.rpc.TorrentListRefreshingListener;
import com.vuze.android.remote.rpc.TransmissionRPC;
import com.vuze.util.DisplayFormatters;
import com.vuze.util.Thunk;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.*;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.SearchView.OnQueryTextListener;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Torrent View -- containing:<br>
 * - Header with speed, filter info, torrent count<br>
 * - Torrent List {@link TorrentListFragment} and<br>
 * - Torrent Details {@link TorrentDetailsFragment} if room provides.
 */
public class TorrentViewActivity
	extends DrawerActivity
	implements SessionSettingsChangedListener, OnTorrentSelectedListener,
	SessionInfoListener, NetworkStateListener, TorrentListRefreshingListener
{

	private static final int[] fragmentIDS = {
		R.id.frag_torrent_list,
		R.id.frag_torrent_details
	};

	public final static int FILECHOOSER_RESULTCODE = 1;

	private static final boolean DEBUG = AndroidUtils.DEBUG;

	@SuppressWarnings("hiding")
	private static final String TAG = "TorrentView";

	private SearchView mSearchView;

	@Thunk
	TextView tvUpSpeed;

	@Thunk
	TextView tvDownSpeed;

	@Thunk
	TextView tvCenter;

	@Thunk
	TextView tvTVHeader;

	private boolean searchIsIconified = true;

	/**
	 * Used to capture the File Chooser results from {@link
	 * DialogFragmentOpenTorrent}
	 *
	 * @see android.support.v4.app.FragmentActivity#onActivityResult(int, int,
	 * android.content.Intent)
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent intent) {
		if (DEBUG) {
			Log.d(TAG,
					"ActivityResult!! " + requestCode + "/" + resultCode + ";" + intent);
		}

		requestCode &= 0xFFFF;

		if (requestCode == FILECHOOSER_RESULTCODE) {
			Uri result = intent == null || resultCode != RESULT_OK ? null
					: intent.getData();
			if (DEBUG) {
				Log.d(TAG, "result = " + result);
			}
			if (result == null) {
				return;
			}

			sessionInfo.openTorrent(this, result);
			return;
		}

		super.onActivityResult(requestCode, resultCode, intent);
	}

	@Override
	protected String getTag() {
		return TAG;
	}

	@Override
	protected void onCreateWithSession(Bundle savedInstanceState) {
		setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

		sessionInfo.addRpcAvailableListener(this);
		sessionInfo.addSessionSettingsChangedListeners(this);

		int contentViewID = R.layout.activity_torrent_view;
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR2) {
			int width = AndroidUtilsUI.getScreenWidthDp(this);
			if (width >= 800) {
				if (width >= 1024) {
					contentViewID = R.layout.activity_torrent_view_split;
				} else {
					contentViewID = R.layout.activity_torrent_view_nodrawer;
				}
			}
		}
		setContentView(contentViewID);
		setupActionBar();

		// setup view ids now because listeners below may trigger as soon as we
		// get them
		tvUpSpeed = (TextView) findViewById(R.id.wvUpSpeed);
		tvDownSpeed = (TextView) findViewById(R.id.wvDnSpeed);
		tvCenter = (TextView) findViewById(R.id.wvCenter);
		tvTVHeader = (TextView) findViewById(R.id.torrentview_tv_header);

		RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
		setSubtitle(remoteProfile.getNick());
		if (tvTVHeader != null) {
			tvTVHeader.setText(remoteProfile.getNick());
		}

		boolean isLocalHost = remoteProfile.isLocalHost();
		if (!VuzeRemoteApp.getNetworkState().isOnline() && !isLocalHost) {
			Resources resources = getResources();
			String msg = resources.getString(R.string.no_network_connection);
			String reason = VuzeRemoteApp.getNetworkState().getOnlineStateReason();
			if (reason != null) {
				msg += "\n\n" + reason;
			}
			AndroidUtilsUI.showConnectionError(this, msg, false);
			return;
		}

		onCreate_setupDrawer();
	}

	/** Called when a drawer has settled in a completely closed state. */
	public void onDrawerClosed(View view) {
		AndroidUtilsUI.invalidateOptionsMenuHC(TorrentViewActivity.this);
		//            getActionBar().setTitle(mTitle);
		actionModeBeingReplacedDone();
	}

	/** Called when a drawer has settled in a completely open state. */
	public void onDrawerOpened(View drawerView) {
		AndroidUtilsUI.invalidateOptionsMenuHC(TorrentViewActivity.this);

		TorrentListFragment frag = (TorrentListFragment) getSupportFragmentManager().findFragmentById(
				R.id.frag_torrent_list);
		if (frag != null) {
			frag.onDrawerOpened(drawerView);
		}

		//            getActionBar().setTitle(mDrawerTitle);
	}

	@Override
	public void rpcTorrentListRefreshingChanged(final boolean refreshing) {
		new Handler(getMainLooper()).postDelayed(new Runnable() {
			@Override
			public void run() {
				if (isFinishing()) {
					return;
				}
				if (refreshing != sessionInfo.isRefreshingTorrentList()) {
					return;
				}
				supportInvalidateOptionsMenu();
				View view = findViewById(R.id.progress_spinner);
				if (view != null) {
					view.setVisibility(refreshing ? View.VISIBLE : View.GONE);
				}
			}
		}, 500);
	}

	private void setSubtitle(String name) {
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setSubtitle(name);
		}
	}

	@Thunk
	void ui_showOldRPCDialog() {
		if (isFinishing()) {
			return;
		}
		new AlertDialog.Builder(TorrentViewActivity.this).setMessage(
				R.string.old_rpc).setPositiveButton(android.R.string.ok,
						new OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
							}
						}).show();
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.SessionInfoListener#uiReady()
	 */
	@Override
	public void uiReady(final TransmissionRPC rpc) {
		if (DEBUG) {
			Log.d(TAG, "UI READY");
		}

		runOnUiThread(new Runnable() {
			public void run() {
				if (isFinishing()) {
					return;
				}
				if (rpc.getRPCVersion() < 14) {
					ui_showOldRPCDialog();
				} else {
					AppPreferences appPreferences = VuzeRemoteApp.getAppPreferences();
					appPreferences.showRateDialog(TorrentViewActivity.this);
				}

				String dataString = getIntent().getDataString();
				if (dataString != null) {
					sessionInfo.openTorrent(TorrentViewActivity.this,
							getIntent().getData());
					getIntent().setData(null);
				}

				if (tvCenter != null && VuzeRemoteApp.getNetworkState().isOnline()) {
					tvCenter.setText("");
				}

				supportInvalidateOptionsMenu();

			}
		});

	}

	@Override
	protected void onNewIntent(Intent intent) {
		if (DEBUG) {
			Log.d(TAG, "onNewIntent " + intent);
		}
		super.onNewIntent(intent);
		// Called via MetaSearch
		sessionInfo.openTorrent(this, intent.getData());
	}

	private void invalidateOptionsMenuHC() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				supportInvalidateOptionsMenu();
			}
		});
	}

	@Override
	public void supportInvalidateOptionsMenu() {
		if (mSearchView != null) {
			searchIsIconified = mSearchView.isIconified();
		}
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "InvalidateOptionsMenu Called");
		}

		ActionMode actionMode = getActionMode();
		if (actionMode != null) {
			actionMode.invalidate();
		}

		super.supportInvalidateOptionsMenu();
	}

	private void setupActionBar() {
		Toolbar abToolBar = (Toolbar) findViewById(R.id.actionbar);
		try {
			setSupportActionBar(abToolBar);
		} catch (NullPointerException ignore) {
			//setSupportActionBar says it can be nullable, but on Android TV API 22,
			// appcompat 23.1.1:
			//		Caused by: java.lang.NullPointerException: Attempt to invoke
			// virtual method 'java.lang.CharSequence android.support.v7.widget
			// .Toolbar.getTitle()' on a null object reference
			//		at android.support.v7.widget.ToolbarWidgetWrapper.<init>
			// (ToolbarWidgetWrapper.java:98)
			//		at android.support.v7.widget.ToolbarWidgetWrapper.<init>
			// (ToolbarWidgetWrapper.java:91)
			//		at android.support.v7.app.ToolbarActionBar.<init>(ToolbarActionBar
			// .java:73)
			//		at android.support.v7.app.AppCompatDelegateImplV7
			// .setSupportActionBar(AppCompatDelegateImplV7.java:205)
			//		at android.support.v7.app.AppCompatActivity.setSupportActionBar
			// (AppCompatActivity.java:99)
		}
	}

	@Override
	protected void onPause() {
		VuzeRemoteApp.getNetworkState().removeListener(this);
		sessionInfo.removeSessionSettingsChangedListeners(this);
		sessionInfo.removeTorrentListRefreshingListener(this);
		super.onPause();
	}

	@Override
	protected void onResume() {
		VuzeRemoteApp.getNetworkState().addListener(this);
		sessionInfo.addSessionSettingsChangedListeners(TorrentViewActivity.this);
		sessionInfo.addTorrentListRefreshingListener(this, true);

		super.onResume();
	}

	/**
	 * Fragments call VET, so it's redundant here
	 * protected void onStop() {
	 * super.onStop();
	 * VuzeEasyTracker.getInstance(this).activityStop(this);
	 * }
	 * <p/>
	 * protected void onStart() {
	 * super.onStart();
	 * VuzeEasyTracker.getInstance(this).activityStart(this);
	 * }
	 **/

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		for (int id : fragmentIDS) {
			Fragment fragment = getSupportFragmentManager().findFragmentById(id);

			if (fragment != null) {
				if (fragment.onOptionsItemSelected(item)) {
					return true;
				}
			}
		}

		if (onOptionsItemSelected_drawer(item)) {
			return true;
		}
		if (handleMenu(item.getItemId())) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private boolean handleMenu(int itemId) {
		if (DEBUG) {
			Log.d(TAG, "HANDLE MENU " + itemId);
		}
		if (isFinishing()) {
			return true;
		}
		if (itemId == android.R.id.home) {
			Intent upIntent = NavUtils.getParentActivityIntent(this);
			if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
				upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

				// This activity is NOT part of this app's task, so create a new task
				// when navigating up, with a synthesized back stack.
				TaskStackBuilder.create(this)
						// Add all of this activity's parents to the back stack
						.addNextIntentWithParentStack(upIntent)
						// Navigate up to the closest parent
						.startActivities();
				finish();
			} else {
				upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(upIntent);
				finish();
				// Opens parent with FLAG_ACTIVITY_CLEAR_TOP
				// Note: navigateUpFromSameTask and navigateUpTo doesn't set
				// FLAG_ACTIVITY_CLEAR_TOP on JellyBean
				//NavUtils.navigateUpFromSameTask(this);
				//NavUtils.navigateUpTo(this, upIntent);
			}
			return true;
		} else if (itemId == R.id.action_settings) {
			return DialogFragmentSessionSettings.openDialog(
					getSupportFragmentManager(), sessionInfo);
		} else if (itemId == R.id.action_swarm_discoveries) {
			Intent intent = new Intent(Intent.ACTION_VIEW, null, this,
					RcmActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			intent.putExtra(SessionInfoManager.BUNDLE_KEY, remoteProfileID);
			startActivity(intent);
			return true;
		} else if (itemId == R.id.action_subscriptions) {
			Intent intent = new Intent(Intent.ACTION_VIEW, null, this,
					SubscriptionListActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			intent.putExtra(SessionInfoManager.BUNDLE_KEY, remoteProfileID);
			startActivity(intent);
			return true;
		} else if (itemId == R.id.action_add_torrent) {
			DialogFragmentOpenTorrent.openOpenTorrentDialog(
					getSupportFragmentManager(), remoteProfileID);
		} else if (itemId == R.id.action_search) {
			onSearchRequested();
			return true;
		} else if (itemId == R.id.action_logout) {
			RemoteUtils.openRemoteList(TorrentViewActivity.this);
			SessionInfoManager.removeSessionInfo(remoteProfileID);
			finish();
			return true;
		} else if (itemId == R.id.action_start_all) {
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.startTorrents(TAG, null, false, null);
				}
			});
			return true;
		} else if (itemId == R.id.action_stop_all) {
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.stopTorrents(TAG, null, null);
				}
			});
			return true;
		} else if (itemId == R.id.action_refresh) {
			sessionInfo.triggerRefresh(true);
			return true;
		} else if (itemId == R.id.action_about) {
			DialogFragmentAbout dlg = new DialogFragmentAbout();
			AndroidUtilsUI.showDialog(dlg, getSupportFragmentManager(), "About");
			return true;
		} else if (itemId == R.id.action_rate) {
			final String appPackageName = getPackageName();
			try {
				startActivity(new Intent(Intent.ACTION_VIEW,
						Uri.parse("market://details?id=" + appPackageName)));
			} catch (android.content.ActivityNotFoundException anfe) {
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
						"http://play.google.com/store/apps/details?id=" + appPackageName)));
			}
			VuzeEasyTracker.getInstance(this).sendEvent(VuzeEasyTracker.CAT_UI_ACTION,
				VuzeEasyTracker.ACTION_RATING,
					"StoreClick", null);
			return true;
		} else if (itemId == R.id.action_forum) {
			String url = "http://www.vuze.com/forums/android-remote";
			Intent i = new Intent(Intent.ACTION_VIEW);
			i.setData(Uri.parse(url));
			startActivity(i);
			return true;
		} else if (itemId == R.id.action_vote) {
			String url = "http://vote.vuze.com/forums/227649";
			Intent i = new Intent(Intent.ACTION_VIEW);
			i.setData(Uri.parse(url));
			startActivity(i);
			return true;
		} else if (itemId == R.id.action_shutdown) {
			VuzeRemoteApp.shutdownCoreService();
			RemoteUtils.openRemoteList(TorrentViewActivity.this);
			SessionInfoManager.removeSessionInfo(remoteProfileID);
			finish();
		}
		return false;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "onCreateOptionsMenu; currentActionMode=" + getActionMode());
		}

		boolean fillSubmenu = menu instanceof SubMenu;
		// TV doesn't get action bar menu, because it's impossible to get to
		// with remote control when you are on row 4000
		if (!fillSubmenu && (getActionMode() != null || AndroidUtils.isTV())) {
			return false;
		}

		MenuItem searchItem;

		getMenuInflater().inflate(R.menu.menu_torrent_list, menu);

		searchItem = menu.findItem(R.id.action_search);
		onPrepareOptionsMenu(menu);
		setupSearchView(searchItem);

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "onPrepareOptionsMenu: am=" + getActionMode());
		}

		super.onPrepareOptionsMenu(menu);

		for (int id : fragmentIDS) {
			Fragment fragment = getSupportFragmentManager().findFragmentById(id);

			if (fragment != null) {
				fragment.onPrepareOptionsMenu(menu);
			}
		}

		if (SessionInfoManager.hasSessionInfo(remoteProfileID)) {
			prepareGlobalMenu(menu, sessionInfo);
		}

		AndroidUtils.fixupMenuAlpha(menu);

		return true;
	}

	public static void prepareGlobalMenu(Menu menu, SessionInfo sessionInfo) {
		SessionSettings sessionSettings = sessionInfo == null ? null
				: sessionInfo.getSessionSettings();

		boolean uiReady = sessionInfo != null && sessionInfo.isUIReady();

		boolean isLocalHost = sessionInfo != null
				&& sessionInfo.getRemoteProfile().isLocalHost();
		boolean isOnline = VuzeRemoteApp.getNetworkState().isOnline();

		MenuItem menuSessionSettings = menu.findItem(R.id.action_settings);
		if (menuSessionSettings != null) {
			menuSessionSettings.setEnabled(uiReady && sessionSettings != null);
		}

		MenuItem menuRefresh = menu.findItem(R.id.action_refresh);
		if (menuRefresh != null) {
			boolean refreshVisible = TorrentUtils.isAllowRefresh(sessionInfo);
			boolean enable = sessionInfo == null ? false
					: !sessionInfo.isRefreshingTorrentList();
			menuRefresh.setVisible(refreshVisible);
			menuRefresh.setEnabled(enable);
		}

		MenuItem menuSwarmDiscoveries = menu.findItem(
				R.id.action_swarm_discoveries);
		if (menuSwarmDiscoveries != null) {
			menuSwarmDiscoveries.setEnabled(uiReady);
		}

		MenuItem menuSubscriptions = menu.findItem(R.id.action_subscriptions);
		if (menuSubscriptions != null) {
			menuSubscriptions.setEnabled(uiReady);
		}

		MenuItem menuAdd = menu.findItem(R.id.action_add_torrent);
		if (menuAdd != null) {
			menuAdd.setEnabled(isOnline && uiReady);
		}

		MenuItem menuSearch = menu.findItem(R.id.action_search);
		if (menuSearch != null) {
			menuSearch.setEnabled(isOnline && uiReady);
		}

		MenuItem menuStartAll = menu.findItem(R.id.action_start_all);
		if (menuStartAll != null) {
			menuStartAll.setEnabled(uiReady && (isOnline || isLocalHost));
		}

		MenuItem menuStopAll = menu.findItem(R.id.action_stop_all);
		if (menuStopAll != null) {
			menuStopAll.setEnabled(uiReady && (isOnline || isLocalHost));
		}

		MenuItem itemSocial = menu.findItem(R.id.action_social);
		if (itemSocial != null) {

			MenuItem menuVote = menu.findItem(R.id.action_vote);
			if (menuVote != null) {
				menuVote.setVisible(!AndroidUtils.isTV());
			}

			MenuItem menuForum = menu.findItem(R.id.action_forum);
			if (menuForum != null) {
				menuForum.setVisible(!AndroidUtils.isTV());
			}
		}

		MenuItem menuShutdownCore = menu.findItem(R.id.action_shutdown);
		if (menuShutdownCore != null) {
			boolean visible = sessionInfo != null
					&& sessionInfo.getRemoteProfile().getRemoteType() == RemoteProfile.TYPE_CORE;
			menuShutdownCore.setVisible(visible);
		}
	}

	private void setupSearchView(MenuItem searchItem) {
		if (searchItem == null) {
			return;
		}
		mSearchView = (SearchView) MenuItemCompat.getActionView(searchItem);
		if (mSearchView == null) {
			return;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
			setupSearchView_Froyo(mSearchView);
		}
		mSearchView.setIconifiedByDefault(true);
		mSearchView.setIconified(searchIsIconified);
		mSearchView.setQueryHint(
				getResources().getString(R.string.search_box_hint));
		mSearchView.setOnQueryTextListener(new OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				AndroidUtils.executeSearch(query, TorrentViewActivity.this,
						sessionInfo);
				return true;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				return false;
			}
		});
	}

	@TargetApi(Build.VERSION_CODES.FROYO)
	private void setupSearchView_Froyo(SearchView mSearchView) {
		SearchManager searchManager = (SearchManager) getSystemService(
				Context.SEARCH_SERVICE);
		if (searchManager != null) {
			mSearchView.setSearchableInfo(
					searchManager.getSearchableInfo(getComponentName()));
		}
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onSearchRequested()
	 */
	@Override
	public boolean onSearchRequested() {
		if (!AndroidUtils.isTV()) {
			Bundle appData = new Bundle();
			if (sessionInfo.getSupports(RPCSupports.SUPPORTS_SEARCH)) {
				RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
				appData.putString(SessionInfoManager.BUNDLE_KEY, remoteProfile.getID());
			}

			startSearch(null, false, appData, false);
		} else {
			AlertDialog alertDialog = AndroidUtilsUI.createTextBoxDialog(this,
					R.string.search, R.string.search_box_hint,
					AndroidUtils.DEBUG ? "wallpaper" : null, EditorInfo.IME_ACTION_SEARCH,
					new AndroidUtilsUI.OnTextBoxDialogClick() {

						@Override
						public void onClick(DialogInterface dialog, int which,
								EditText editText) {

							final String newName = editText.getText().toString();
							AndroidUtils.executeSearch(newName, TorrentViewActivity.this,
									sessionInfo);
						}
					});
			alertDialog.show();

		}
		return true;
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote
	 * .SessionSettingsChangedListener#sessionSettingsChanged(com.vuze.android
	 * .remote.SessionSettings)
	 */
	@Override
	public void sessionSettingsChanged(SessionSettings newSettings) {
		invalidateOptionsMenuHC();
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.SessionSettingsChangedListener#speedChanged
	 * (long, long)
	 */
	public void speedChanged(final long downSpeed, final long upSpeed) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (isFinishing()) {
					return;
				}
				if (tvDownSpeed != null) {
					if (downSpeed <= 0) {
						tvDownSpeed.setVisibility(View.GONE);
					} else {
						String s = "\u25BC "
								+ DisplayFormatters.formatByteCountToKiBEtcPerSec(downSpeed);
						tvDownSpeed.setText(AndroidUtils.fromHTML(s));
						tvDownSpeed.setVisibility(View.VISIBLE);
					}
				}
				if (tvUpSpeed != null) {
					if (upSpeed <= 0) {
						tvUpSpeed.setVisibility(View.GONE);
					} else {
						String s = "\u25B2 "
								+ DisplayFormatters.formatByteCountToKiBEtcPerSec(upSpeed);
						tvUpSpeed.setText(AndroidUtils.fromHTML(s));
						tvUpSpeed.setVisibility(View.VISIBLE);
					}
				}
			}
		});
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.TorrentListFragment
	 * .OnTorrentSelectedListener#onTorrentSelectedListener(long[])
	 */
	@Override
	public void onTorrentSelectedListener(TorrentListFragment torrentListFragment,
			long[] ids, boolean inMultiMode) {
		// The user selected the headline of an article from the HeadlinesFragment
		// Do something here to display that article

		TorrentDetailsFragment detailFrag = (TorrentDetailsFragment) getSupportFragmentManager().findFragmentById(
				R.id.frag_torrent_details);
		View fragmentView = findViewById(R.id.frag_details_container);

		if (DEBUG) {
			Log.d(TAG,
					"onTorrentSelectedListener: " + Arrays.toString(ids) + ";multi?"
							+ inMultiMode + ";" + detailFrag + " via "
							+ AndroidUtils.getCompressedStackTrace());
		}
		if (detailFrag != null && fragmentView != null) {
			// If article frag is available, we're in two-pane layout...

			// Call a method in the TorrentDetailsFragment to update its content
			if (ids == null || ids.length != 1) {
				fragmentView.setVisibility(View.GONE);
			} else {
				fragmentView.setVisibility(View.VISIBLE);
			}
			detailFrag.setTorrentIDs(ids);
		} else if (ids != null && ids.length == 1 && !inMultiMode) {
			Intent intent = new Intent(Intent.ACTION_VIEW, null, this,
					AndroidUtils.isTV() ? TorrentDetailsActivityTV.class
							: TorrentDetailsCoordActivity.class);
			intent.putExtra("TorrentID", ids[0]);
			intent.putExtra("RemoteProfileID", remoteProfileID);

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				View view = torrentListFragment.getItemView(ids[0]);
				if (view != null) {
					view.setTransitionName("TVtoTD");
					ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
							this, view, "TVtoTD");
					startActivity(intent, options.toBundle());
				} else {
					intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
					startActivity(intent);
				}
			} else {
				intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
				startActivity(intent);
			}

			torrentListFragment.clearSelection();
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.SessionInfo
	 * .TransmissionRpcAvailableListener#transmissionRpcAvailable(com.vuze
	 * .android.remote.SessionInfo)
	 */
	@Override
	public void transmissionRpcAvailable(SessionInfo sessionInfo) {
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment
	 * .ActionModeBeingReplacedListener#setActionModeBeingReplaced(boolean)
	 */
	@Override
	public void setActionModeBeingReplaced(
			android.support.v7.view.ActionMode actionMode, boolean beingReplaced) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG,
					"setActionModeBeingReplaced: replaced? " + beingReplaced
							+ "; actionMode=" + actionMode + ";"
							+ AndroidUtils.getCompressedStackTrace());
		}

		for (int id : fragmentIDS) {
			Fragment fragment = getSupportFragmentManager().findFragmentById(id);

			if (fragment instanceof ActionModeBeingReplacedListener) {
				((ActionModeBeingReplacedListener) fragment).setActionModeBeingReplaced(
						actionMode, beingReplaced);
			}
		}
	}

	@Override
	public void actionModeBeingReplacedDone() {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "actionModeBeingReplacedDone;"
					+ AndroidUtils.getCompressedStackTrace());
		}
		for (int id : fragmentIDS) {
			Fragment fragment = getSupportFragmentManager().findFragmentById(id);

			if (fragment instanceof ActionModeBeingReplacedListener) {
				((ActionModeBeingReplacedListener) fragment).actionModeBeingReplacedDone();
			}
		}
	}

	@Override
	public ActionMode getActionMode() {
		for (int id : fragmentIDS) {
			Fragment fragment = getSupportFragmentManager().findFragmentById(id);

			if (fragment instanceof ActionModeBeingReplacedListener) {
				ActionMode actionMode = ((ActionModeBeingReplacedListener) fragment).getActionMode();
				if (actionMode != null) {
					return actionMode;
				}
			}
		}
		return null;
	}

	@Override
	public ActionMode.Callback getActionModeCallback() {
		for (int id : fragmentIDS) {
			Fragment fragment = getSupportFragmentManager().findFragmentById(id);

			if (fragment instanceof ActionModeBeingReplacedListener) {
				ActionMode.Callback callback = ((ActionModeBeingReplacedListener) fragment).getActionModeCallback();
				if (callback != null) {
					return callback;
				}
			}
		}
		return null;
	}

	@Override
	public void onlineStateChanged(final boolean isOnline,
			boolean isOnlineMobile) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (isFinishing()) {
					return;
				}
				supportInvalidateOptionsMenu();
				if (isOnline) {
					boolean uiReady = sessionInfo.isUIReady();
					if (uiReady && tvCenter != null) {
						tvCenter.setText("");
					}
				} else {
					if (tvCenter != null) {
						tvCenter.setText(R.string.no_network_connection);
						tvDownSpeed.setText("");
						tvUpSpeed.setText("");
					}
				}
			}
		});
	}

	@Override
	public void rebuildActionMode() {
		TorrentListFragment frag = (TorrentListFragment) getSupportFragmentManager().findFragmentById(
				R.id.frag_torrent_list);
		if (frag != null) {
			frag.rebuildActionMode();
		}
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {

		if (AndroidUtilsUI.sendOnKeyToFragments(this, keyCode, event)) {
			return true;
		}

		switch (keyCode) {
			case KeyEvent.KEYCODE_PROG_RED: {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
					finishAffinity();
				}
				break;
			}
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {

		if (AndroidUtilsUI.sendOnKeyToFragments(this, keyCode, event)) {
			return true;
		}

		switch (keyCode) {
			case KeyEvent.KEYCODE_MEDIA_PLAY:
			case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: {

				TorrentDetailsFragment detailFrag = (TorrentDetailsFragment) getSupportFragmentManager().findFragmentById(
						R.id.frag_torrent_details);
				View fragmentView = findViewById(R.id.frag_details_container);

				if (detailFrag != null && fragmentView != null
						&& fragmentView.getVisibility() == View.VISIBLE) {
					detailFrag.playVideo();

				} else {
					TorrentListFragment frag = (TorrentListFragment) getSupportFragmentManager().findFragmentById(
							R.id.frag_torrent_list);
					if (frag != null) {
						frag.startStopTorrents();
					}
				}
				return true;
			}

			case KeyEvent.KEYCODE_PROG_YELLOW: {
				DrawerLayout drawerLayout = getDrawerLayout();
				if (drawerLayout != null) {
					drawerLayout.openDrawer(Gravity.LEFT);
				}
				return true;
			}

			case KeyEvent.KEYCODE_PROG_GREEN: {
				Log.d(TAG, "CurrentFocus is " + getCurrentFocus());
				break;
			}

			default:
				if (AndroidUtilsUI.handleCommonKeyDownEvents(this, keyCode, event)) {
					return true;
				}
				if (DEBUG) {
					Log.d(TAG, "Didn't handle key " + keyCode + ";" + event + ";focus="
							+ getCurrentFocus());
				}

				if (AndroidUtilsUI.handleBrokenListViewScrolling(this, keyCode)) {
					return true;
				}

				break;
		}
		return super.onKeyDown(keyCode, event);
	}
}
