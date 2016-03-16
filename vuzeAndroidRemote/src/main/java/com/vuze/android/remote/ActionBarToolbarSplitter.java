package com.vuze.android.remote;

import java.util.List;

import android.graphics.drawable.Drawable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.internal.view.SupportMenuItem;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.view.ActionMode.Callback;
import android.support.v7.view.menu.MenuItemImpl;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.*;

public class ActionBarToolbarSplitter
{
	private static final String TAG = "ABToolbarSplitter";

	public static final boolean DEBUG_AB_METRICS = false;

	// From ActionMenuView
	static final int MIN_CELL_SIZE = 56; // dips
	// From ActionMenuView

	static final int GENERATED_ITEM_PADDING = 4; // dips

	public static void buildActionBar(final FragmentActivity activity,
			final Callback callback, int menuRes, Menu menu, Toolbar tb) {
		Menu origMenu = menu;
		boolean hasToolbar = tb != null && tb.getVisibility() == View.VISIBLE;
		if (hasToolbar) {
			menu = tb.getMenu();
		}
		if (menu.size() > 0) {
			menu.clear();
		}
		activity.getMenuInflater().inflate(menuRes, menu);

		// if Menu is a Submenu, we are calling it to fill one of ours, instead
		// of the Android OS calling
		if (!hasToolbar || (origMenu instanceof SubMenu)) {
			return;
		}

		// Build bottom toolbar
		tb.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {

				if (callback != null) {
					if (callback.onActionItemClicked(null, item)) {
						return true;
					}
				}

				List<Fragment> fragments = activity.getSupportFragmentManager().getFragments();
				if (fragments != null) {
					for (Fragment fragment : fragments) {
						if (fragment == null) {
							continue;
						}
						if (fragment.onOptionsItemSelected(item)) {
							return true;
						}
					}
				}

				return activity.onOptionsItemSelected(item);
			}
		});

		if (callback != null) {
			callback.onPrepareActionMode(null, tb.getMenu());
		}

		activity.onPrepareOptionsMenu(tb.getMenu());
	}

	public static void prepareToolbar(Menu menu, Toolbar tb) {
		prepareToolbar(menu, tb, false);
	}

	public static void prepareToolbar(Menu menu, Toolbar tb, boolean showText) {
		boolean hasToolbar = tb != null && tb.getVisibility() == View.VISIBLE;
		if (!hasToolbar) {
			return;
		}

		View firstChild = tb.getChildAt(0);
		int size = menu.size();
		int widthRemaining = firstChild.getWidth();
		if (widthRemaining == 0) {
			widthRemaining = tb.getWidth();
		}

		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "Force Menu Items (" + size + ") visible " + widthRemaining);
		}

		final int widthPadding = firstChild.getPaddingLeft()
				+ firstChild.getPaddingRight();

		widthRemaining -= widthPadding;

		int hardCodedPaddingPx = AndroidUtilsUI.dpToPx(GENERATED_ITEM_PADDING);

		int padding = hardCodedPaddingPx * 2;

		int minIconWidth = AndroidUtilsUI.dpToPx(MIN_CELL_SIZE);

		int hardCodedOverFlowWidth = minIconWidth;

		widthRemaining -= hardCodedOverFlowWidth;

		if (DEBUG_AB_METRICS) {
			Log.d(TAG, "hardCodedPaddingPx=" + hardCodedPaddingPx
					+ "; hardCodedOverFlowWidth=" + hardCodedOverFlowWidth);
		}

		for (int i = 0; i < size; i++) {
			MenuItem item = menu.getItem(i);

			if (!item.isVisible()) {
				if (DEBUG_AB_METRICS) {
					Log.d(TAG, item.getTitle() + "; not visible");
				}
				continue;
			}

			// check if app:showAsAction = "ifRoom"
			if (widthRemaining <= 0) {
				if (DEBUG_AB_METRICS) {
					Log.d(TAG, item.getTitle() + "; no space. " + widthRemaining);
				}

				if (((MenuItemImpl) item).requiresActionButton()) {
					MenuItemCompat.setShowAsAction(item,
							SupportMenuItem.SHOW_AS_ACTION_IF_ROOM);
				}
			} else if (((MenuItemImpl) item).requestsActionButton()
					|| ((MenuItemImpl) item).requiresActionButton()) {
				Drawable icon = item.getIcon();
				if (icon != null) {
					int width = Math.max(minIconWidth,
							item.getIcon().getIntrinsicWidth() + padding);

					boolean outofSpace = widthRemaining < width;
					boolean outofSpaceWithNoOverflow = widthRemaining
							+ hardCodedOverFlowWidth < width;
					boolean isLast = i == size - 1;

					if (DEBUG_AB_METRICS) {
						Log.d(TAG,
								item.getTitle() + "/remaining=" + widthRemaining + "/w=" + width
										+ "/last= " + isLast + "; outofSpaceWithNoOverflow?"
										+ outofSpaceWithNoOverflow);
					}

					widthRemaining -= width;
					if (outofSpace) {
						if (!isLast) {
							continue;
						}
						if (outofSpaceWithNoOverflow) {
							continue;
						}
					}
				} else {
					// maybe use minWidth.. although it's most likely text so we are screwed
				}

				MenuItemCompat.setShowAsAction(item,
						showText ? SupportMenuItem.SHOW_AS_ACTION_WITH_TEXT
								: SupportMenuItem.SHOW_AS_ACTION_ALWAYS);
			}
		}
	}
}