package com.vuze.android.remote.activity;

import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.vuze.android.remote.R;
import com.vuze.android.remote.RemoteUtils;
import com.vuze.android.remote.SessionInfoManager;
import com.vuze.android.remote.fragment.SessionInfoGetter;

public abstract class DrawerActivity
	extends ActionBarActivity
	implements SessionInfoGetter
{

	private DrawerLayout mDrawerLayout;

	private ActionBarDrawerToggle mDrawerToggle;

	private ListView mDrawerList;

	public void onCreate_setupDrawer() {
		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		if (mDrawerLayout == null) {
			return;
		}
		mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
				R.drawable.ic_drawer, R.string.drawer_open, R.string.drawer_close) {

			/** Called when a drawer has settled in a completely closed state. */
			public void onDrawerClosed(View view) {
				super.onDrawerClosed(view);
				DrawerActivity.this.onDrawerClosed(view);
			}

			/** Called when a drawer has settled in a completely open state. */
			public void onDrawerOpened(View view) {
				super.onDrawerOpened(view);
				DrawerActivity.this.onDrawerClosed(view);
			}
		};

		// Set the drawer toggle as the DrawerListener
		mDrawerLayout.setDrawerListener(mDrawerToggle);

		mDrawerList = (ListView) findViewById(R.id.left_drawer);

		Resources res = getResources();
		// Set the adapter for the list view
		mDrawerList.setAdapter(new ArrayAdapter<CharSequence>(this,
				R.layout.drawer_list_item, R.id.title, new CharSequence[] {
					res.getText(R.string.drawer_torrents),
					res.getText(R.string.drawer_rcm),
					res.getText(R.string.drawer_logout),
				}));
		// Set the list's click listener
		mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position,
					long id) {
				mDrawerLayout.closeDrawer(mDrawerList);
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
						new RemoteUtils(DrawerActivity.this).openRemoteList(getIntent());
						SessionInfoManager.removeSessionInfo(getSessionInfo().getRemoteProfile().getID());
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
		if (mDrawerToggle != null && mDrawerToggle.onOptionsItemSelected(item)) {
			return true;
		}
		return false;
	}

	public boolean onPrepareOptionsMenu_drawer(Menu menu) {

		if (mDrawerLayout == null) {
			return true;
		}

		if (!mDrawerLayout.isDrawerOpen(mDrawerList)) {
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
}
