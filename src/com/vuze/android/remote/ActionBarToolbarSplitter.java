package com.vuze.android.remote;

import java.util.List;

import android.graphics.drawable.Drawable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.internal.view.SupportMenuItem;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.internal.view.menu.MenuItemImpl;
import android.support.v7.view.ActionMode.Callback;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;

public class ActionBarToolbarSplitter
{

	public static void buildActionBar(final FragmentActivity activity,
			final Callback callback, int menuRes, Menu menu, Toolbar tb) {
		Menu origMenu = menu;
		if (tb != null) {
			menu = tb.getMenu();
		}
		if (menu.size() > 0) {
			menu.clear();
		}
		activity.getMenuInflater().inflate(menuRes, menu);

		// if Menu is a Submenu, we are calling it to fill one of ours, instead
		// of the Android OS calling
		if (tb == null || (origMenu instanceof SubMenu)) {
			return;
		}

		if (AndroidUtils.DEBUG_MENU) {
			Log.d("ActionBarToolbarSplitter", "Force Menu Items visible");
		}

		int size = menu.size();
		int totalWidth = tb.getWidth();

		/* Doesn't work.  I'm doing something wrong, but have no idea what
		int[] attrs = new int[] { R.attr.paddingStart, R.attr.paddingEnd };
		TypedArray a = tb.getContext().obtainStyledAttributes(R.style.Base_Widget_AppCompat_ActionButton, attrs);
		int paddingStart = a.getDimensionPixelOffset(0, -1);
		int paddingEnd = a.getDimensionPixelSize(1, -1);
		a.recycle();
		Log.d("Padding", "start=" + paddingEnd + ";" + paddingStart);
		*/

		int hardCodedPaddingPx = (int) TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_DIP, 12, tb.getResources().getDisplayMetrics());

		int padding = hardCodedPaddingPx * 2;

		for (int i = 0; i < size; i++) {
			MenuItem item = menu.getItem(i);
			// check if app:showAsAction = "ifRoom"
			if (((MenuItemImpl) item).requestsActionButton()) {

				Drawable icon = item.getIcon();
				if (icon != null) {
					int width = item.getIcon().getIntrinsicWidth();
					totalWidth -= (width + padding);
					if (totalWidth < 0) {
						break;
					}
				} else {
					// maybe use minWidth.. although it's most likely text so we are screwed
				}

				MenuItemCompat.setShowAsAction(item,
						SupportMenuItem.SHOW_AS_ACTION_ALWAYS);
			}
		}

		// Build bottom toolbar
		tb.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				
				if (callback != null) {
					callback.onActionItemClicked(null, item);
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
}