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

import com.vuze.android.remote.AndroidUtils;
import com.vuze.android.remote.AppCompatActivityM;
import com.vuze.android.remote.R;
import com.vuze.android.remote.fragment.SessionInfoGetter;

import android.annotation.TargetApi;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public abstract class DrawerActivity
	extends AppCompatActivityM
	implements SessionInfoGetter
{
	protected static final String TAG = "DrawerActivity";

	private DrawerLayout mDrawerLayout;

	private ActionBarDrawerToggle mDrawerToggle;

	private View mDrawerView;

	public void onCreate_setupDrawer() {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onCreate_setupDrawer");
		}
		View viewById = findViewById(R.id.drawer_layout);
		if (!(viewById instanceof DrawerLayout)) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "onCreate_setupDrawer: Not DrawerLayout");
			}
			return;
		}
		mDrawerLayout = (DrawerLayout) viewById;
		mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
				R.string.drawer_open, R.string.drawer_close) {

			/** Called when a drawer has settled in a completely closed state. */
			public void onDrawerClosed(View view) {
				super.onDrawerClosed(view);
				DrawerActivity.this.onDrawerClosed(view);
			}

			/** Called when a drawer has settled in a completely open state. */
			public void onDrawerOpened(View view) {
				DrawerActivity.this.onDrawerOpened(view);
				super.onDrawerOpened(view);
			}

			@Override
			public void onDrawerSlide(View drawerView, float slideOffset) {
				super.onDrawerSlide(drawerView, slideOffset);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
					onDrawerSlide_11(drawerView, slideOffset);
				} else {
					View mainChild = getDrawerLayout().getChildAt(0);
					int x = (int) (slideOffset * drawerView.getWidth());

					mainChild.setPadding(x, mainChild.getPaddingTop(), -x,
							mainChild.getPaddingBottom());
				}
			}

			@TargetApi(Build.VERSION_CODES.HONEYCOMB)
			private void onDrawerSlide_11(View drawerView, float slideOffset) {
				View mainChild = getDrawerLayout().getChildAt(0);
				float x = slideOffset * drawerView.getWidth();

				mainChild.setX(x);

				ActionBar supportActionBar = getSupportActionBar();
				if (supportActionBar != null) {
					View actionBarContainer = findViewById(R.id.action_mode_bar);
					if (actionBarContainer != null) {
						actionBarContainer.setX(x);
					}
				}
			}
		};

		// Set the drawer toggle as the DrawerListener
		mDrawerLayout.addDrawerListener(mDrawerToggle);

		mDrawerView = findViewById(R.id.drawer_view);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			mDrawerLayout.setElevation(30);
		}
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
		return mDrawerView != null && mDrawerToggle != null
				&& mDrawerToggle.onOptionsItemSelected(item);
	}

	public boolean onPrepareOptionsMenu_drawer(Menu menu) {

		if (mDrawerLayout == null) {
			return true;
		}

		/*
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
		*/

		return true;
	}

	public abstract void onDrawerClosed(View view);

	public abstract void onDrawerOpened(View view);

	public void onBackPressed() {
		if (mDrawerLayout != null && mDrawerView != null
				&& mDrawerLayout.isDrawerOpen(mDrawerView)) {
			mDrawerLayout.closeDrawer(mDrawerView);
			return;
		}
		super.onBackPressed();
	}

	public DrawerLayout getDrawerLayout() {
		if (mDrawerLayout != null) {
			return mDrawerLayout;
		}
		View viewById = findViewById(R.id.drawer_layout);
		if (viewById instanceof DrawerLayout) {
			mDrawerLayout = (DrawerLayout) viewById;
		}
		return mDrawerLayout;
	}

	public void setDrawerLockMode(int lockMode) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "setDrawerLockMode: " + lockMode);
		}
		if (mDrawerLayout == null) {
			return;
		}
		mDrawerLayout.setDrawerLockMode(lockMode);
	}

	@Override
	protected void onResume() {
		super.onResume();
//		if (mDrawerLayout != null) {
//			mDrawerLayout.closeDrawer(mDrawerView);
//		}
	}
}
