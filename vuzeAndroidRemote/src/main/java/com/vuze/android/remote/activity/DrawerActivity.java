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

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import android.widget.AdapterView.OnItemSelectedListener;

import com.vuze.android.remote.*;
import com.vuze.android.remote.adapter.ActionBarArrayAdapter;
import com.vuze.android.remote.fragment.SessionInfoGetter;

public abstract class DrawerActivity
	extends AppCompatActivityM
	implements SessionInfoGetter
{
	private static final boolean DEBUG_SPINNER = false;

	protected static final String TAG = "DrawerActivity";

	private DrawerLayout mDrawerLayout;

	private ActionBarDrawerToggle mDrawerToggle;

	private View mDrawerView;

	@SuppressLint("NewApi")
	public void onCreate_setupDrawer() {
		setupProfileSpinner();

		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		if (mDrawerLayout == null) {
			return;
		}
		mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
				R.string.drawer_open, R.string.drawer_close) {

			/** Called when a drawer has settled in a completely closed state. */
			public void onDrawerClosed(View view) {
				super.onDrawerClosed(view);
				DrawerActivity.this.onDrawerClosed(view);
			}

			/** Called when a drawer has settled in a completely open state. */
			public void onDrawerOpened(View view) {
				super.onDrawerOpened(view);
				DrawerActivity.this.onDrawerOpened(view);
			}
		};

		// Set the drawer toggle as the DrawerListener
		mDrawerLayout.setDrawerListener(mDrawerToggle);

		mDrawerView = findViewById(R.id.drawer_view);
		ListView mDrawerList = (ListView) findViewById(R.id.drawer_listview);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			mDrawerLayout.setElevation(30);
		}

		Resources res = getResources();
		// Set the adapter for the list view
		mDrawerList.setAdapter(new ArrayAdapter<>(this, R.layout.drawer_list_item,
				R.id.title, new CharSequence[] {
					res.getText(R.string.drawer_torrents),
					res.getText(R.string.drawer_rcm),
					res.getText(R.string.drawer_logout),
		}));
		// Set the list's click listener
		mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position,
					long id) {
				mDrawerLayout.closeDrawer(mDrawerView);
				switch (position) {
					case 0: {
						Intent intent = new Intent(Intent.ACTION_VIEW, null,
								DrawerActivity.this, TorrentViewActivity.class);
						intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
						intent.putExtra(SessionInfoManager.BUNDLE_KEY,
								getSessionInfo().getRemoteProfile().getID());
						startActivity(intent);
						break;
					}

					case 1: {
						Intent intent = new Intent(Intent.ACTION_VIEW, null,
								DrawerActivity.this, RcmActivity.class);
						intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
						intent.putExtra(SessionInfoManager.BUNDLE_KEY,
								getSessionInfo().getRemoteProfile().getID());
						startActivity(intent);
						break;
					}

					case 2: {
						new RemoteUtils(DrawerActivity.this).openRemoteList();
						SessionInfoManager.removeSessionInfo(
								getSessionInfo().getRemoteProfile().getID());
						finish();
						break;
					}
				}
			}
		});
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		if (mDrawerToggle != null) {
			mDrawerToggle.syncState();
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (mDrawerToggle != null) {
			mDrawerToggle.onConfigurationChanged(newConfig);
		}
	}

	public boolean onOptionsItemSelected_drawer(MenuItem item) {
		return mDrawerToggle != null && mDrawerToggle.onOptionsItemSelected(item);
	}

	public boolean onPrepareOptionsMenu_drawer(Menu menu) {

		if (mDrawerLayout == null) {
			return true;
		}

		if (!mDrawerLayout.isDrawerOpen(mDrawerView)) {
			for (int i = 0; i < menu.size(); i++) {
				MenuItem item = menu.getItem(i);
				item.setVisible(true);
			}
		} else {
			final int[] itemsToKeep = {
				R.id.action_about,
				R.id.action_logout,
				R.id.action_social,
				R.id.action_settings
			};
			for (int i = 0; i < menu.size(); i++) {
				MenuItem item = menu.getItem(i);
				int id = item.getItemId();
				boolean visible = false;
				for (int itemID : itemsToKeep) {
					if (id == itemID) {
						visible = true;
						break;
					}
				}
				item.setVisible(visible);
			}
			return false; // skip all other prepares
		}

		return true;
	}

	public abstract void onDrawerClosed(View view);

	public abstract void onDrawerOpened(View view);

	private void setupProfileSpinner() {

		Spinner spinner = (Spinner) findViewById(R.id.drawer_profile_spinner);
		if (spinner == null) {
			return;
		}

		AppPreferences appPreferences = VuzeRemoteApp.getAppPreferences();
		if (appPreferences.getNumRemotes() <= 1) {
			spinner.setEnabled(false);
		}

		RemoteProfile remoteProfile = getSessionInfo().getRemoteProfile();

		final ActionBarArrayAdapter adapter = new ActionBarArrayAdapter(this);
		final int initialPos = adapter.refreshList(remoteProfile);

		// Note: If the adapter returns itemPosition for itemID, we have problems
		// when the user rotates the screen (something about restoring the drop
		// down list, firing the wrong id/position)
		// Most "solutions" on the internet say "ignore first call too onNavigationItemSelected"
		// but I've found this not to be consistent (in some cases there is no phantom
		// call)
		OnItemSelectedListener navigationListener = new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int itemPosition, long itemId) {
				RemoteProfile remoteProfile = getSessionInfo().getRemoteProfile();
				RemoteProfile profile = adapter.getItem(itemPosition);
				if (profile != null && !profile.getID().equals(remoteProfile.getID())) {
					if (DEBUG_SPINNER) {
						Log.d(TAG,
								remoteProfile.getNick() + "] Spinner Selected " + itemPosition
										+ ":" + itemId + "/" + profile.getNick() + " via "
										+ AndroidUtils.getCompressedStackTrace());
					}
					finish();
					new RemoteUtils(DrawerActivity.this).openRemote(profile, false);
					return;
				}
				if (DEBUG_SPINNER) {
					Log.d(TAG, remoteProfile.getNick() + "] Spinner Selected "
							+ itemPosition + ":" + itemId + "/"
							+ (profile == null ? "null" : profile.getNick()) + " ignored");
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		};

		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(navigationListener);

		if (DEBUG_SPINNER) {
			Log.d(TAG,
					remoteProfile.getNick() + "] Spinner seting pos to " + initialPos);
		}
		// This doesn't seem to trigger naviationListener
		spinner.setSelection(initialPos);
		if (DEBUG_SPINNER) {
			Log.d(TAG,
					remoteProfile.getNick() + "] Spinner set pos to " + initialPos);
		}
	}

	public void onBackPressed() {
		if (mDrawerLayout != null && mDrawerView != null
				&& mDrawerLayout.isDrawerOpen(mDrawerView)) {
			mDrawerLayout.closeDrawer(mDrawerView);
			return;
		}
		super.onBackPressed();
	}

	public DrawerLayout getDrawerLayout() {
		return mDrawerLayout;
	}

}
