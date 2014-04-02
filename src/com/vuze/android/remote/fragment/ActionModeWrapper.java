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

public class ActionModeWrapper
	extends ActionMode
{

	private android.view.ActionMode modev11;

	private ActionMode modeV7;

	public ActionModeWrapper(android.view.ActionMode mode) {
		this.modev11 = mode;
	}

	public ActionModeWrapper(ActionMode modeV7) {
		this.modeV7 = modeV7;
	}

	@Override
	public void setTitle(CharSequence charSequence) {
		if (modeV7 != null) {
			modeV7.setTitle(charSequence);
		} else {
			modev11.setTitle(charSequence);
		}
	}

	@Override
	public void setSubtitle(CharSequence charSequence) {
		if (modeV7 != null) {
			modeV7.setSubtitle(charSequence);
		} else {
			modev11.setSubtitle(charSequence);
		}
	}

	@Override
	public void invalidate() {
		if (modeV7 != null) {
			modeV7.invalidate();
		} else {
			modev11.invalidate();
		}
	}

	@Override
	public void finish() {
		if (modeV7 != null) {
			modeV7.finish();
		} else {
			modev11.finish();
		}
	}

	@Override
	public Menu getMenu() {
		if (modeV7 != null) {
			return modeV7.getMenu();
		}
		return modev11.getMenu();
	}

	@Override
	public CharSequence getTitle() {
		if (modeV7 != null) {
			return modeV7.getTitle();
		} else {
			return modev11.getTitle();
		}
	}

	@Override
	public void setTitle(int i) {
		if (modeV7 != null) {
			modeV7.setTitle(i);
		} else {
			modev11.setTitle(i);
		}
	}

	@Override
	public CharSequence getSubtitle() {
		if (modeV7 != null) {
			return modeV7.getSubtitle();
		}
		return modev11.getSubtitle();
	}

	@Override
	public void setSubtitle(int i) {
		if (modeV7 != null) {
			modeV7.setSubtitle(i);
		} else {
			modev11.setSubtitle(i);
		}
	}

	@Override
	public View getCustomView() {
		if (modeV7 != null) {
			return modeV7.getCustomView();
		}
		return modev11.getCustomView();
	}

	@Override
	public void setCustomView(View view) {
		if (modeV7 != null) {
			modeV7.setCustomView(view);
		} else {
			modev11.setCustomView(view);
		}
	}

	@Override
	public MenuInflater getMenuInflater() {
		if (modeV7 != null) {
			return modeV7.getMenuInflater();
		}
		return modev11.getMenuInflater();
	}

	public interface MultiChoiceModeListener
	{

		public abstract void onItemCheckedStateChanged(
				ActionModeWrapper actionMode, int position, long id, boolean checked);

		public abstract boolean onCreateActionMode(ActionModeWrapper actionMode,
				Menu menu);

		public abstract boolean onPrepareActionMode(ActionModeWrapper actionMode,
				Menu menu);

		public abstract boolean onActionItemClicked(ActionModeWrapper actionMode,
				MenuItem item);

		public abstract void onDestroyActionMode(ActionModeWrapper actionMode);
	}
}