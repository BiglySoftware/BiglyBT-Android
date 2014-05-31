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

import android.support.v7.view.ActionMode;
import android.view.*;

public class ActionModeWrapperV7
	extends ActionMode
{

	private ActionMode modeV7;

	public ActionModeWrapperV7(ActionMode modeV7) {
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
		modeV7.invalidate();
	}

	@Override
	public void finish() {
		modeV7.finish();
	}

	@Override
	public Menu getMenu() {
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