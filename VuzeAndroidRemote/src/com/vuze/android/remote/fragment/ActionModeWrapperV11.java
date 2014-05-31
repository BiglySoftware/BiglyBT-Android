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

public class ActionModeWrapperV11
	extends ActionMode
{

	private android.view.ActionMode modev11;

	public ActionModeWrapperV11(android.view.ActionMode mode) {
		this.modev11 = mode;
	}

	@Override
	public void setTitle(CharSequence charSequence) {
		modev11.setTitle(charSequence);
	}

	@Override
	public void setSubtitle(CharSequence charSequence) {
		modev11.setSubtitle(charSequence);
	}

	@Override
	public void invalidate() {
		modev11.invalidate();
	}

	@Override
	public void finish() {
		modev11.finish();
	}

	@Override
	public Menu getMenu() {
		return modev11.getMenu();
	}

	@Override
	public CharSequence getTitle() {
		return modev11.getTitle();
	}

	@Override
	public void setTitle(int i) {
		modev11.setTitle(i);
	}

	@Override
	public CharSequence getSubtitle() {
		return modev11.getSubtitle();
	}

	@Override
	public void setSubtitle(int i) {
		modev11.setSubtitle(i);
	}

	@Override
	public View getCustomView() {
		return modev11.getCustomView();
	}

	@Override
	public void setCustomView(View view) {
		modev11.setCustomView(view);
	}

	@Override
	public MenuInflater getMenuInflater() {
		return modev11.getMenuInflater();
	}
}