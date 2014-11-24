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
 * 
 */

package com.vuze.android.remote.activity;

import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import org.gudy.azureus2.core3.util.DisplayFormatters;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.OnNavigationListener;
import android.support.v7.internal.view.menu.MenuDialogHelper;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.SearchView.OnQueryTextListener;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.view.*;
import android.widget.TextView;

import com.vuze.android.remote.*;
import com.vuze.android.remote.NetworkState.NetworkStateListener;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.dialog.DialogFragmentAbout;
import com.vuze.android.remote.dialog.DialogFragmentOpenTorrent;
import com.vuze.android.remote.dialog.DialogFragmentSessionSettings;
import com.vuze.android.remote.fragment.*;
import com.vuze.android.remote.fragment.TorrentListFragment.OnTorrentSelectedListener;
import com.vuze.android.remote.rpc.TransmissionRPC;

/**
 * Torrent View -- containing:<br>
 * - Header with speed, filter info, torrent count<br>
 * - Torrent List {@link TorrentListFragment} and<br>
 * - Torrent Details {@link TorrentDetailsFragment} if room provides.
 */
public class TorrentViewActivity
	extends DrawerActivity
	implements SessionSettingsChangedListener, OnTorrentSelectedListener,
	SessionInfoListener, ActionModeBeingReplacedListener, NetworkStateListener
{

	private static final boolean DEBUG_SPINNER = false;

	private static final int[] fragmentIDS = {
		R.id.frag_torrent_list,
		R.id.frag_torrent_details
	};

	public final static int FILECHOOSER_RESULTCODE = 1;

	private static final boolean DEBUG = AndroidUtils.DEBUG;

	private static final String TAG = "TorrentView";

	private SearchView mSearchView;

	private TextView tvUpSpeed;

	private TextView tvDownSpeed;

	private TextView tvCenter;

	protected boolean searchIsIconified = true;

	private RemoteProfile remoteProfile;

	private boolean disableRefreshButton;

	protected String page;

	private SessionInfo sessionInfo;

	private boolean isLocalHost;

	private boolean uiReady = false;

	private boolean hasActionMode;

	private Menu currentActionBarMenu;

	/**
	 * Used to capture the File Chooser results from {@link DialogFragmentOpenTorrent}
	 * 
	 * @see android.support.v4.app.FragmentActivity#onActivityResult(int, int, android.content.Intent)
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (DEBUG) {
			Log.d(TAG, "ActivityResult!! " + requestCode + "/" + resultCode + ";"
					+ intent);
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
			if (sessionInfo == null) {
				return;
			}

			sessionInfo.openTorrent(this, result);
			return;
		}

		super.onActivityResult(requestCode, resultCode, intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		if (DEBUG) {
			Log.d(TAG, "TorrentViewActivity intent = " + intent);
			Log.d(TAG, "Type:" + intent.getType() + ";" + intent.getDataString());
		}

		final Bundle extras = intent.getExtras();
		if (extras == null) {
			Log.e(TAG, "No extras!");
			finish();
			return;
		}

		String remoteProfileID = extras.getString(SessionInfoManager.BUNDLE_KEY);
		if (remoteProfileID != null) {
			sessionInfo = SessionInfoManager.getSessionInfo(remoteProfileID, this);
		}

		if (sessionInfo == null) {
			Log.e(TAG, "sessionInfo NULL!");
			finish();
			return;
		}

		sessionInfo.addRpcAvailableListener(this);
		sessionInfo.addSessionSettingsChangedListeners(this);
		remoteProfile = sessionInfo.getRemoteProfile();

		setupActionBar();

		setContentView(R.layout.activity_torrent_view);

		// setup view ids now because listeners below may trigger as soon as we get them
		tvUpSpeed = (TextView) findViewById(R.id.wvUpSpeed);
		tvDownSpeed = (TextView) findViewById(R.id.wvDnSpeed);
		tvCenter = (TextView) findViewById(R.id.wvCenter);

		setSubtitle(remoteProfile.getNick());

		isLocalHost = remoteProfile.isLocalHost();
		if (!VuzeRemoteApp.getNetworkState().isOnline() && !isLocalHost) {
			AndroidUtils.showConnectionError(this, R.string.no_network_connection,
					false);
			return;
		}

		onCreate_setupDrawer();
	}

	/** Called when a drawer has settled in a completely closed state. */
	public void onDrawerClosed(View view) {
		AndroidUtils.invalidateOptionsMenuHC(TorrentViewActivity.this);
		//            getActionBar().setTitle(mTitle);
		actionModeBeingReplacedDone();
	}

	/** Called when a drawer has settled in a completely open state. */
	public void onDrawerOpened(View drawerView) {
		setActionModeBeingReplaced(true);
		setActionModeBeingReplaced(false);

		AndroidUtils.invalidateOptionsMenuHC(TorrentViewActivity.this);

		//            getActionBar().setTitle(mDrawerTitle);
	}

	private void setSubtitle(String name) {
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null
				&& actionBar.getNavigationMode() == ActionBar.NAVIGATION_MODE_STANDARD) {
			actionBar.setSubtitle(name);
		}
	}

	protected void ui_showOldRPCDialog() {
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

		uiReady = true;
		// first time: track RPC version
		page = "RPC v" + rpc.getRPCVersion() + "/" + rpc.getRPCVersionAZ();

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
				if (dataString != null && sessionInfo != null) {
					sessionInfo.openTorrent(TorrentViewActivity.this,
							getIntent().getData());
					getIntent().setData(null);
				}

				if (tvCenter != null && VuzeRemoteApp.getNetworkState().isOnline()) {
					tvCenter.setText("");
				}

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
		if (sessionInfo != null) {
			sessionInfo.openTorrent(this, intent.getData());
		}
	}

	protected void invalidateOptionsMenuHC() {
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

		super.supportInvalidateOptionsMenu();
	}

	private void setupActionBar() {
		ActionBar actionBar = getSupportActionBar();
		if (actionBar == null) {
			if (DEBUG) {
				System.err.println("actionBar is null");
			}
			return;
		}
		actionBar.setDisplayHomeAsUpEnabled(true);

		AppPreferences appPreferences = VuzeRemoteApp.getAppPreferences();
		if (appPreferences.getNumRemotes() > 1) {
			setupActionBarSpinner(actionBar);
		}
	}

	private void setupActionBarSpinner(ActionBar actionBar) {

		final ActionBarArrayAdapter adapter = new ActionBarArrayAdapter(this);
		final int initialPos = adapter.refreshList(remoteProfile);

		// Note: If the adapter returns itemPosition for itemID, we have problems
		// when the user rotates the screen (something about restoring the drop
		// down list, firing the wrong id/position)
		// Most "solutions" on the internet say "ignore first call too onNavigationItemSelected"
		// but I've found this not to be consistent (in some cases there is no phantom
		// call)
		OnNavigationListener navigationListener = new ActionBar.OnNavigationListener() {
			@Override
			public boolean onNavigationItemSelected(int itemPosition, long itemId) {
				RemoteProfile profile = adapter.getItem(itemPosition);
				if (profile != null && !profile.getID().equals(remoteProfile.getID())) {
					if (DEBUG_SPINNER) {
						Log.d(TAG, remoteProfile.getNick() + "] Spinner Selected "
								+ itemPosition + ":" + itemId + "/" + profile.getNick()
								+ " via " + AndroidUtils.getCompressedStackTrace());
					}
					finish();
					new RemoteUtils(TorrentViewActivity.this).openRemote(profile, false);
					return false;
				}
				if (DEBUG_SPINNER) {
					Log.d(TAG, remoteProfile.getNick() + "] Spinner Selected "
							+ itemPosition + ":" + itemId + "/"
							+ (profile == null ? "null" : profile.getNick()) + " ignored");
				}
				return true;
			}
		};

		actionBar.setDisplayShowTitleEnabled(false);
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
		actionBar.setListNavigationCallbacks(adapter, navigationListener);
		if (DEBUG_SPINNER) {
			Log.d(TAG, remoteProfile.getNick() + "] Spinner seting pos to "
					+ initialPos);
		}
		// This doesn't seem to trigger naviationListener
		actionBar.setSelectedNavigationItem(initialPos);
		if (DEBUG_SPINNER) {
			Log.d(TAG, remoteProfile.getNick() + "] Spinner set pos to " + initialPos);
		}

		AndroidUtilsUI.setABSpinnerDropDownWidth(this, 400);
	}

	@Override
	protected void onPause() {
		VuzeRemoteApp.getNetworkState().removeListener(this);
		if (sessionInfo != null) {
			sessionInfo.activityPaused();
			sessionInfo.removeSessionSettingsChangedListeners(TorrentViewActivity.this);
		}
		super.onPause();
	}

	@Override
	protected void onResume() {
		VuzeRemoteApp.getNetworkState().addListener(this);
		if (sessionInfo != null) {
			sessionInfo.activityResumed(this);
			sessionInfo.addSessionSettingsChangedListeners(TorrentViewActivity.this);
		}

		super.onResume();
	}

	/** Fragments call VET, so it's redundant here
	@Override
	protected void onStop() {
		super.onStop();
		VuzeEasyTracker.getInstance(this).activityStop(this);
	}

	@Override
	protected void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).activityStart(this);
	}
	**/

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (onOptionsItemSelected_drawer(item)) {
			return true;
		}
		if (handleMenu(item.getItemId())) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	protected boolean handleMenu(int itemId) {
		if (DEBUG) {
			Log.d(TAG, "HANDLE MENU " + itemId);
		}
		if (isFinishing()) {
			return true;
		}
		switch (itemId) {
			case android.R.id.home:

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
					// Note: navigateUpFromSameTask and navigateUpTo doesn't set FLAG_ACTIVITY_CLEAR_TOP on JellyBean
					//NavUtils.navigateUpFromSameTask(this);
					//NavUtils.navigateUpTo(this, upIntent);
				}
				return true;
			case R.id.action_settings: {
				return DialogFragmentSessionSettings.openDialog(
						getSupportFragmentManager(), sessionInfo);
			}
			case R.id.action_add_torrent: {
				DialogFragmentOpenTorrent dlg = new DialogFragmentOpenTorrent();
				AndroidUtils.showDialog(dlg, getSupportFragmentManager(),
						"OpenTorrentDialog");
				break;
			}

			case R.id.action_search:
				onSearchRequested();
				return true;

			case R.id.action_logout: {
				new RemoteUtils(TorrentViewActivity.this).openRemoteList();
				SessionInfoManager.removeSessionInfo(remoteProfile.getID());
				finish();
				return true;
			}

			case R.id.action_start_all: {
				if (sessionInfo == null) {
					return false;
				}
				sessionInfo.executeRpc(new RpcExecuter() {
					@Override
					public void executeRpc(TransmissionRPC rpc) {
						rpc.startTorrents(TAG, null, false, null);
					}
				});

				return true;
			}

			case R.id.action_stop_all: {
				if (sessionInfo == null) {
					return false;
				}
				sessionInfo.executeRpc(new RpcExecuter() {
					@Override
					public void executeRpc(TransmissionRPC rpc) {
						rpc.stopTorrents(TAG, null, null);
					}
				});

				return true;
			}

			case R.id.action_refresh: {
				if (sessionInfo == null) {
					return false;
				}
				sessionInfo.triggerRefresh(false, null);

				disableRefreshButton = true;
				invalidateOptionsMenuHC();

				new Timer().schedule(new TimerTask() {
					public void run() {
						disableRefreshButton = false;
						invalidateOptionsMenuHC();
					}
				}, 10000);
				return true;
			}

			case R.id.action_about: {
				DialogFragmentAbout dlg = new DialogFragmentAbout();
				AndroidUtils.showDialog(dlg, getSupportFragmentManager(), "About");
				return true;
			}

			case R.id.action_rate: {
				final String appPackageName = getPackageName();
				try {
					startActivity(new Intent(Intent.ACTION_VIEW,
							Uri.parse("market://details?id=" + appPackageName)));
				} catch (android.content.ActivityNotFoundException anfe) {
					startActivity(new Intent(Intent.ACTION_VIEW,
							Uri.parse("http://play.google.com/store/apps/details?id="
									+ appPackageName)));
				}
				VuzeEasyTracker.getInstance(this).sendEvent("uiAction", "Rating",
						"StoreClick", null);
				return true;
			}

			case R.id.action_forum: {
				String url = "http://www.vuze.com/forums/android-remote";
				Intent i = new Intent(Intent.ACTION_VIEW);
				i.setData(Uri.parse(url));
				startActivity(i);
				return true;
			}

			case R.id.action_vote: {
				String url = "http://vote.vuze.com/forums/227649";
				Intent i = new Intent(Intent.ACTION_VIEW);
				i.setData(Uri.parse(url));
				startActivity(i);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "onCreateOptionsMenu; hasActionMode=" + hasActionMode);
		}
		
		if (hasActionMode) {
			return true;
		}
		
		MenuItem searchItem;
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_bottom);

		ActionBarToolbarSplitter.buildActionBar(this, null,
				R.menu.menu_torrent_list, menu, toolbar);
		
		// if Menu is a Submenu, we are calling it to fill one of ours, instead
		// of the Android OS calling
		if (toolbar == null || menu instanceof SubMenu) {
			searchItem = menu.findItem(R.id.action_search);
			setupSearchView(searchItem);
		} else {
			searchItem = toolbar.getMenu().findItem(R.id.action_search);
			onPrepareOptionsMenu(toolbar.getMenu());
			setupSearchView(searchItem);

			return true;
		}

		currentActionBarMenu = menu;
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "onPrepareOptionsMenu");
		}
		super.onPrepareOptionsMenu(menu);

		if (!onPrepareOptionsMenu_drawer(menu)) {
			return true;
		}

		SessionSettings sessionSettings = sessionInfo == null ? null
				: sessionInfo.getSessionSettings();

		MenuItem menuSessionSettings = menu.findItem(R.id.action_settings);
		if (menuSessionSettings != null) {
			menuSessionSettings.setEnabled(sessionSettings != null);
		}

		if (sessionSettings != null) {
			MenuItem menuRefresh = menu.findItem(R.id.action_refresh);
			if (menuRefresh != null) {
				boolean refreshVisible = false;
				if (!remoteProfile.isUpdateIntervalEnabled()
						|| remoteProfile.getUpdateInterval() >= 45) {
					refreshVisible = true;
				}
				menuRefresh.setVisible(refreshVisible);
				menuRefresh.setEnabled(!disableRefreshButton);
			}
		}

		MenuItem menuSearch = menu.findItem(R.id.action_search);
		if (menuSearch != null) {
			menuSearch.setEnabled(VuzeRemoteApp.getNetworkState().isOnline());
		}

		AndroidUtils.fixupMenuAlpha(menu);
		return true;
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
		mSearchView.setQueryHint(getResources().getString(R.string.search_box_hint));
		mSearchView.setOnQueryTextListener(new OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				AndroidUtils.executeSearch(query, TorrentViewActivity.this, sessionInfo);
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
		SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
		if (searchManager != null) {
			mSearchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
		}
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onSearchRequested()
	 */
	@Override
	public boolean onSearchRequested() {
		Bundle appData = new Bundle();
		if (sessionInfo != null && sessionInfo.getRPCVersionAZ() >= 0) {
			appData.putString("com.vuze.android.remote.searchsource",
					sessionInfo.getRpcRoot());
			if (remoteProfile.getRemoteType() == RemoteProfile.TYPE_LOOKUP) {
				appData.putString("com.vuze.android.remote.ac", remoteProfile.getAC());
			}
			appData.putString(SessionInfoManager.BUNDLE_KEY, remoteProfile.getID());
		}
		startSearch(null, false, appData, false);
		return true;
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.SessionSettingsChangedListener#sessionSettingsChanged(com.vuze.android.remote.SessionSettings)
	 */
	@Override
	public void sessionSettingsChanged(SessionSettings newSettings) {
		invalidateOptionsMenuHC();
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.SessionSettingsChangedListener#speedChanged(long, long)
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
						tvDownSpeed.setText(Html.fromHtml(s));
						tvDownSpeed.setVisibility(View.VISIBLE);
					}
				}
				if (tvUpSpeed != null) {
					if (upSpeed <= 0) {
						tvUpSpeed.setVisibility(View.GONE);
					} else {
						String s = "\u25B2 "
								+ DisplayFormatters.formatByteCountToKiBEtcPerSec(upSpeed);
						tvUpSpeed.setText(Html.fromHtml(s));
						tvUpSpeed.setVisibility(View.VISIBLE);
					}
				}
			}
		});
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.TorrentListFragment.OnTorrentSelectedListener#onTorrentSelectedListener(long[])
	 */
	@Override
	public void onTorrentSelectedListener(
			TorrentListFragment torrentListFragment, long[] ids, boolean inMultiMode) {
		// The user selected the headline of an article from the HeadlinesFragment
		// Do something here to display that article

		TorrentDetailsFragment detailFrag = (TorrentDetailsFragment) getSupportFragmentManager().findFragmentById(
				R.id.frag_torrent_details);
		View fragmentView = findViewById(R.id.frag_details_container);

		if (DEBUG) {
			Log.d(
					TAG,
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
			detailFrag.setTorrentIDs(sessionInfo.getRemoteProfile().getID(), ids);
		} else if (ids != null && ids.length == 1 && !inMultiMode) {
			torrentListFragment.clearSelection();

			Intent intent = new Intent(Intent.ACTION_VIEW, null, this,
					TorrentDetailsActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			intent.putExtra("TorrentID", ids[0]);
			intent.putExtra("RemoteProfileID", remoteProfile.getID());
			startActivity(intent);
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.SessionInfo.TransmissionRpcAvailableListener#transmissionRpcAvailable(com.vuze.android.remote.SessionInfo)
	 */
	@Override
	public void transmissionRpcAvailable(SessionInfo sessionInfo) {
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.ActionModeBeingReplacedListener#setActionModeBeingReplaced(boolean)
	 */
	@Override
	public void setActionModeBeingReplaced(boolean beingReplaced) {
		if (beingReplaced) {
			hasActionMode = true;
		}

		for (int id : fragmentIDS) {
			Fragment fragment = getSupportFragmentManager().findFragmentById(id);

			if (fragment instanceof ActionModeBeingReplacedListener) {
				((ActionModeBeingReplacedListener) fragment).setActionModeBeingReplaced(beingReplaced);
			}
		}
	}

	@Override
	public void actionModeBeingReplacedDone() {
		hasActionMode = false;
		for (int id : fragmentIDS) {
			Fragment fragment = getSupportFragmentManager().findFragmentById(id);

			if (fragment instanceof ActionModeBeingReplacedListener) {
				((ActionModeBeingReplacedListener) fragment).actionModeBeingReplacedDone();
			}
		}
	}

	@Override
	public void onlineStateChanged(final boolean isOnline) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (isFinishing()) {
					return;
				}
				supportInvalidateOptionsMenu();
				if (isOnline) {
					if (uiReady && tvCenter != null) {
						tvCenter.setText("");
					}
				} else {
					if (tvCenter != null) {
						tvCenter.setText(R.string.no_network_connection);
					}
				}
			}
		});
	}

	@Override
	public void wifiConnectionChanged(boolean isWifiConnected) {
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.SessionInfoGetter#getSessionInfo()
	 */
	@Override
	public SessionInfo getSessionInfo() {
		return sessionInfo;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
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
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
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
			
			case KeyEvent.KEYCODE_MENU: {
				if (super.onKeyDown(keyCode, event)) {
					return true;
				}
				Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_bottom);
				if (toolbar != null) {
					return toolbar.showOverflowMenu();
				}
				
				
				return false;
			}

			default:
				break;
		}
		return super.onKeyDown(keyCode, event);
	}
	
	/* (non-Javadoc)
	 * @see android.app.Activity#openOptionsMenu()
	 */
	@Override
	public void openOptionsMenu() {
		// is this called on hardware menu key?
		super.openOptionsMenu();
		System.out.println("FOO");
		// also check menu from long press on profile list for correct color
	}
}
