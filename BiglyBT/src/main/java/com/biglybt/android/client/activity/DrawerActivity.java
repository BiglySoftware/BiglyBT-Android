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

import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.*;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.biglybt.android.client.*;
import com.biglybt.util.Thunk;

public abstract class DrawerActivity
	extends SessionActivity
	implements SessionGetter
{
	@Thunk
	static final String TAG = "DrawerActivity";

	private DrawerLayout mDrawerLayout;

	private ActionBarDrawerToggle mDrawerToggle;

	private View mDrawerView;

	@Override
	protected void onStart() {
		super.onStart();

		View viewById = findViewById(R.id.drawer_layout);
		if (!(viewById instanceof DrawerLayout)) {
			if (AndroidUtils.DEBUG) {
				log(TAG, "onCreateWithSession: Not DrawerLayout");
			}
			return;
		}
		mDrawerLayout = (DrawerLayout) viewById;
		mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
				R.string.drawer_open, R.string.drawer_close) {

			/** Called when a drawer has settled in a completely closed state. */
			@Override
			public void onDrawerClosed(View view) {
				super.onDrawerClosed(view);
				DrawerActivity.this.onDrawerClosed(view);
			}

			/** Called when a drawer has settled in a completely open state. */
			@Override
			public void onDrawerOpened(View view) {
				DrawerActivity.this.onDrawerOpened(view);
				super.onDrawerOpened(view);
			}

			@Override
			public void onDrawerSlide(View drawerView, float slideOffset) {
				super.onDrawerSlide(drawerView, slideOffset);
				View mainChild = getDrawerLayout().getChildAt(0);
				float x = slideOffset * drawerView.getWidth();

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
					if (mainChild.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
						x *= -1;
					}
				}
				mainChild.setX(x);

				ActionBar supportActionBar = getSupportActionBar();
				if (supportActionBar != null) {
					View actionBarContainer = findViewById(R.id.action_mode_bar);
					if (actionBarContainer != null) {
						actionBarContainer.setX(x);
					}
				}

				setDrawerButtonPosition(slideOffset);
			}
		};

		// Set the drawer toggle as the DrawerListener
		mDrawerLayout.addDrawerListener(mDrawerToggle);

		mDrawerView = findViewById(R.id.drawer_view);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			mDrawerLayout.setElevation(AndroidUtilsUI.dpToPx(16));
		}
	}

	void setDrawerButtonPosition(float slideOffset) {
		ImageButton button = findViewById(R.id.manual_drawer_button);
		if (button != null && button.getVisibility() == View.VISIBLE) {
			Drawable drawable = button.getDrawable();
			if (drawable instanceof DrawerArrowDrawable) {
				float position = Math.min(1f, Math.max(0, slideOffset));
				if (position == 1f) {
					button.setContentDescription(getString(R.string.drawer_close));
					((DrawerArrowDrawable) drawable).setVerticalMirror(true);
				} else if (position == 0f) {
					button.setContentDescription(getString(R.string.drawer_open));
					((DrawerArrowDrawable) drawable).setVerticalMirror(false);
				}
				((DrawerArrowDrawable) drawable).setProgress(position);
			}
		}

	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		if (mDrawerToggle != null) {
			mDrawerToggle.syncState();
		}
		if (findViewById(R.id.actionbar) == null) {
			ImageButton button = findViewById(R.id.manual_drawer_button);
			if (button != null && button.getVisibility() == View.VISIBLE) {
				ViewParent parent = button.getParent();
				if (parent instanceof ViewGroup) {
					int minHeight = AndroidUtilsUI.dpToPx(48);
					int curMinHeight;
					if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
						curMinHeight = ((ViewGroup) parent).getMinimumHeight();
						if (curMinHeight <= 0 && minHeight > curMinHeight) {
							((ViewGroup) parent).setMinimumHeight(minHeight);
						}
					} else {
						((ViewGroup) parent).setMinimumHeight(minHeight);
					}
				}
				DrawerArrowDrawable drawable = new DrawerArrowDrawable(this);
				drawable.setColor(
						AndroidUtilsUI.getStyleColor(this, R.attr.colorOnPrimary));
				if (mDrawerLayout != null) {
					setDrawerButtonPosition(
							mDrawerLayout.isDrawerOpen(GravityCompat.START) ? 1 : 0);
				}
				button.setImageDrawable(drawable);
				button.setOnClickListener(v -> {
					if (mDrawerLayout != null && mDrawerView != null) {
						mDrawerLayout.openDrawer(mDrawerView);
					}
				});
			}
		}
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
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

	@Override
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

	public View getDrawerView() {
		if (mDrawerView == null) {
			mDrawerView = findViewById(R.id.drawer_view);
		}
		return mDrawerView;
	}
}
