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

package com.vuze.android.remote.fragment;

import com.vuze.android.remote.AndroidUtils;
import com.vuze.android.remote.AndroidUtilsUI;

import android.app.Activity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;

class ActionModeWrapperV7
	extends ActionMode
{

	private final ActionMode modeV7;

	private boolean finished = false;

	private final Toolbar toolbar;

	private final Activity activity;

	public ActionModeWrapperV7(ActionMode modeV7, Toolbar toolbar, Activity activity) {
		this.toolbar = toolbar;
		this.activity = activity;
		this.modeV7 = modeV7;
	}

	@Override
	public void setTitle(CharSequence charSequence) {
		modeV7.setTitle(charSequence);
	}

	@Override
	public void setSubtitle(CharSequence charSequence) {
		modeV7.setSubtitle(charSequence);
	}

	@Override
	public void invalidate() {
		if (finished) {
			// prevent NPE in
			// android.support.v7.internal.app.WindowDecorActionBar$ActionModeImpl.invalidate(WindowDecorActionBar.java:1003)
			// when calling invalidate after finish
			return;
		}

		modeV7.invalidate();
		if (toolbar != null) {
			toolbar.invalidate();
		}
	}

	@Override
	public void finish() {
		finished = true;
		if (toolbar != null) {
			AndroidUtilsUI.invalidateOptionsMenuHC(activity);
		}
		modeV7.finish();
	}

	@Override
	public Menu getMenu() {
		if (toolbar != null) {
			if (AndroidUtils.DEBUG_MENU) {
				Log.d("MENU", "getMenu: using toolbar");
			}

			return toolbar.getMenu();
		}
		if (AndroidUtils.DEBUG_MENU) {
			Log.d("MENU", "getMenu: using actionmode");
		}

		return modeV7.getMenu();
	}

	@Override
	public CharSequence getTitle() {
		return modeV7.getTitle();
	}

	@Override
	public void setTitle(int i) {
		modeV7.setTitle(i);
	}

	@Override
	public CharSequence getSubtitle() {
		return modeV7.getSubtitle();
	}

	@Override
	public void setSubtitle(int i) {
		modeV7.setSubtitle(i);
	}

	@Override
	public View getCustomView() {
		return modeV7.getCustomView();
	}

	@Override
	public void setCustomView(View view) {
		modeV7.setCustomView(view);
	}

	@Override
	public MenuInflater getMenuInflater() {
		return modeV7.getMenuInflater();
	}
}