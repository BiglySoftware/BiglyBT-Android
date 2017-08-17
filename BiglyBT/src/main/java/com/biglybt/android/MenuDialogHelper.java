/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.biglybt.android;

import java.util.ArrayList;

import com.biglybt.util.Thunk;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.appcompat.R;
import android.support.v7.view.menu.*;
import android.view.*;
import android.widget.BaseAdapter;

/**
 * Helper for menus that appear as Dialogs (context and submenus).
 *
 * This is a copy of android.support.v7.internal.view.menu.MenuDialogHelper,
 * replacing it's MenuAdapter with the one at
 * android.support.v7.view.menu.MenuPopupHelper.MenuAdapter, allowing us to
 * show icons.
 *
 * Plus, it's not marked as hide, so I can actually use it.
 */
public class MenuDialogHelper
	implements DialogInterface.OnKeyListener, DialogInterface.OnClickListener,
	DialogInterface.OnDismissListener, MenuPresenter.Callback
{
	@Thunk
	final MenuBuilder mMenu;

	private AlertDialog mDialog;

	private ListMenuPresenter mPresenter;

	private MenuPresenter.Callback mPresenterCallback;

	@Thunk
	final boolean mOverflowOnly = false;

	// >> BiglyBT
	@Thunk
	final boolean mForceShowIcon = true;

	@Thunk
	LayoutInflater mInflater;

	@Thunk
	int mItemLayourRes;
	// << BiglyBT

	public MenuDialogHelper(MenuBuilder menu) {
		mMenu = menu;
	}

	/**
	 * Shows menu as a dialog.
	 *
	 * @param windowToken Optional token to assign to the window.
	 */
	public void show(@Nullable IBinder windowToken) {
		// Many references to mMenu, create local reference
		final MenuBuilder menu = mMenu;

		// Get the builder for the dialog
		final AlertDialog.Builder builder = new AlertDialog.Builder(
				menu.getContext());

		// >> BiglyBT
		// Was:
		// mPresenter = new ListMenuPresenter(builder.getContext(),
		//  R.layout.abc_list_menu_item_layout);
		mInflater = LayoutInflater.from(menu.getContext());
		mItemLayourRes = R.layout.abc_list_menu_item_layout;
		mPresenter = new ListMenuPresenter(mItemLayourRes,
				R.style.Theme_AppCompat_CompactMenu);
		// << BiglyBT

		mPresenter.setCallback(this);
		mMenu.addMenuPresenter(mPresenter);
		// >> BiglyBT
		// Was:
		// builder.setAdapter(mPresenter.getAdapter(), this);

		// mPresenter's adapter doesn't show icons.. use our own (which is
		// actually MenuPopupHelper.MenuAdapter)
		builder.setAdapter(new MenuAdapter(menu), this);
		// << BiglyBT

		// Set the title
		final View headerView = menu.getHeaderView();
		if (headerView != null) {
			// Menu's client has given a custom header view, use it
			builder.setCustomTitle(headerView);
		} else {
			// Otherwise use the (text) title and icon
			builder.setIcon(menu.getHeaderIcon()).setTitle(menu.getHeaderTitle());
		}

		// Set the key listener
		builder.setOnKeyListener(this);

		// Show the menu
		mDialog = builder.create();
		mDialog.setOnDismissListener(this);

		Window window = mDialog.getWindow();
		if (window != null) {
			WindowManager.LayoutParams lp = window.getAttributes();
			lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
			if (windowToken != null) {
				lp.token = windowToken;
			}
			lp.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
		}

		mDialog.show();
	}

	public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_BACK) {
			if (event.getAction() == KeyEvent.ACTION_DOWN
					&& event.getRepeatCount() == 0) {
				Window win = mDialog.getWindow();
				if (win != null) {
					View decor = win.getDecorView();
					if (decor != null) {
						KeyEvent.DispatcherState ds = decor.getKeyDispatcherState();
						if (ds != null) {
							ds.startTracking(event, this);
							return true;
						}
					}
				}
			} else if (event.getAction() == KeyEvent.ACTION_UP
					&& !event.isCanceled()) {
				Window win = mDialog.getWindow();
				if (win != null) {
					View decor = win.getDecorView();
					if (decor != null) {
						KeyEvent.DispatcherState ds = decor.getKeyDispatcherState();
						if (ds != null && ds.isTracking(event)) {
							mMenu.close(true);
							dialog.dismiss();
							return true;
						}
					}
				}
			}
		}

		// Menu shortcut matching
		return mMenu.performShortcut(keyCode, event, 0);

	}

	public void setPresenterCallback(MenuPresenter.Callback cb) {
		mPresenterCallback = cb;
	}

	/**
	 * Dismisses the menu's dialog.
	 *
	 * @see Dialog#dismiss()
	 */
	@SuppressWarnings("WeakerAccess")
	public void dismiss() {
		if (mDialog != null) {
			mDialog.dismiss();
		}
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		mPresenter.onCloseMenu(mMenu, true);
	}

	@Override
	public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing) {
		if (allMenusAreClosing || menu == mMenu) {
			dismiss();
		}
		if (mPresenterCallback != null) {
			mPresenterCallback.onCloseMenu(menu, allMenusAreClosing);
		}
	}

	@Override
	public boolean onOpenSubMenu(MenuBuilder subMenu) {
		if (mPresenterCallback != null) {
			return mPresenterCallback.onOpenSubMenu(subMenu);
		}
		return false;
	}

	public void onClick(DialogInterface dialog, int which) {
		mMenu.performItemAction(
				(MenuItemImpl) mPresenter.getAdapter().getItem(which), 0);
	}

	/**
	 * Display menu with icons.
	 *
	 * From {android.support.v7.view.menu.MenuPopupHelper.MenuAdapter}
	 */
	private class MenuAdapter
		extends BaseAdapter
	{
		private final MenuBuilder mAdapterMenu;

		private int mExpandedIndex = -1;

		public MenuAdapter(MenuBuilder menu) {
			mAdapterMenu = menu;
			findExpandedIndex();
		}

		public int getCount() {
			ArrayList<MenuItemImpl> items = mOverflowOnly
					? mAdapterMenu.getNonActionItems() : mAdapterMenu.getVisibleItems();
			if (mExpandedIndex < 0) {
				return items.size();
			}
			return items.size() - 1;
		}

		public MenuItemImpl getItem(int position) {
			ArrayList<MenuItemImpl> items = mOverflowOnly
					? mAdapterMenu.getNonActionItems() : mAdapterMenu.getVisibleItems();
			if (mExpandedIndex >= 0 && position >= mExpandedIndex) {
				position++;
			}
			return items.get(position);
		}

		public long getItemId(int position) {
			// Since a menu item's ID is optional, we'll use the position as an
			// ID for the item in the AdapterView
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = mInflater.inflate(mItemLayourRes, parent, false);
			}

			MenuView.ItemView itemView = (MenuView.ItemView) convertView;
			if (mForceShowIcon) {
				((ListMenuItemView) convertView).setForceShowIcon(true);
			}
			itemView.initialize(getItem(position), 0);
			return convertView;
		}

		void findExpandedIndex() {
			final MenuItemImpl expandedItem = mMenu.getExpandedItem();
			if (expandedItem != null) {
				final ArrayList<MenuItemImpl> items = mMenu.getNonActionItems();
				final int count = items.size();
				for (int i = 0; i < count; i++) {
					final MenuItemImpl item = items.get(i);
					if (item == expandedItem) {
						mExpandedIndex = i;
						return;
					}
				}
			}
			mExpandedIndex = -1;
		}

		@Override
		public void notifyDataSetChanged() {
			findExpandedIndex();
			super.notifyDataSetChanged();
		}
	}
}
