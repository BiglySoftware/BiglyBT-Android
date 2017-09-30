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

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.R;
import com.biglybt.android.client.fragment.SessionGetter;
import com.biglybt.util.Thunk;

import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

public abstract class DrawerActivity
	extends SessionActivity
	implements SessionGetter
{
	@Thunk
	static final String TAG = "DrawerActivity";

	private DrawerLayout mDrawerLayout;

	private ActionBarDrawerToggle mDrawerToggle;

	private View mDrawerView;

	public void onCreate_setupDrawer() {
		if (AndroidUtils.DEBUG) {
			log("onCreate_setupDrawer");
		}
		View viewById = findViewById(R.id.drawer_layout);
		if (!(viewById instanceof DrawerLayout)) {
			if (AndroidUtils.DEBUG) {
				log("onCreate_setupDrawer: Not DrawerLayout");
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
			mDrawerLayout.setElevation(AndroidUtilsUI.dpToPx(16));
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

	public void onDrawerClosed(View view) {
	}

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

	private void log(String s) {
		Log.d(AndroidUtils.getSimpleName(getClass()), TAG + ": " + s);
	}
}
