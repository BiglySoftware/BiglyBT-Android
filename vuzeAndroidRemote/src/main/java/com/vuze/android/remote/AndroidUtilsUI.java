/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 * <p/>
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

package com.vuze.android.remote;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.menu.MenuBuilder;
import android.util.Log;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

import com.rengwuxian.materialedittext.MaterialEditText;
import com.vuze.android.remote.activity.DrawerActivity;
import com.vuze.android.remote.fragment.ActionModeBeingReplacedListener;
import com.vuze.android.MenuDialogHelper;

public class AndroidUtilsUI
{
	public static final boolean ALWAYS_DARK = false;

	private static final String TAG = "AndroidUtilsUI";

	public static ArrayList<View> findByClass(ViewGroup root, Class type,
			ArrayList<View> list) {
		final int childCount = root.getChildCount();

		for (int i = 0; i < childCount; ++i) {
			final View child = root.getChildAt(i);
			if (type.isInstance(child)) {
				list.add(child);
			}

			if (child instanceof ViewGroup) {
				findByClass((ViewGroup) child, type, list);
			}
		}
		return list;
	}

	public static boolean handleCommonKeyDownEvents(Activity a, int keyCode,
			KeyEvent event) {
		if (event.getAction() != KeyEvent.ACTION_DOWN) {
			return false;
		}
		switch (keyCode) {
			case KeyEvent.KEYCODE_MEDIA_NEXT:
			case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: {
				ViewGroup vg = (ViewGroup) a.findViewById(android.R.id.content);
				ArrayList list = AndroidUtilsUI.findByClass(vg, ViewPager.class,
						new ArrayList<View>(0));

				if (list.size() > 0) {
					ViewPager viewPager = (ViewPager) list.get(0);
					viewPager.arrowScroll(View.FOCUS_RIGHT);
				}
				break;
			}

			case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
			case KeyEvent.KEYCODE_MEDIA_REWIND: {
				ViewGroup vg = (ViewGroup) a.findViewById(android.R.id.content);
				ArrayList list = AndroidUtilsUI.findByClass(vg, ViewPager.class,
						new ArrayList<View>(0));

				if (list.size() > 0) {
					ViewPager viewPager = (ViewPager) list.get(0);
					viewPager.arrowScroll(View.FOCUS_LEFT);
				}
				break;
			}

			case KeyEvent.KEYCODE_DPAD_LEFT: {
				if (a instanceof DrawerActivity) {
					DrawerActivity da = (DrawerActivity) a;
					DrawerLayout drawerLayout = da.getDrawerLayout();
					View viewFocus = a.getCurrentFocus();
					boolean canOpenDrawer = viewFocus != null
							&& "leftmost".equals(viewFocus.getTag());
					if (canOpenDrawer) {
						drawerLayout.openDrawer(Gravity.LEFT);
						drawerLayout.requestFocus();
						return true;
					}
				}
				break;
			}

		}

		return false;
	}

	public static void onCreate(Context context) {
		// AppThemeDark is LeanBack, and LeanBack is API 17
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			boolean isTV = AndroidUtils.isTV();
			if (ALWAYS_DARK || isTV) {
				context.setTheme(R.style.AppThemeDark);
				if (!isTV
						&& Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
					Window window = ((AppCompatActivity) context).getWindow();
					window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN);
				}
			}
		}
	}

	public static int getStyleColor(Context context, int r_attr_theme_color) {
		TypedValue typedValue = new TypedValue();
		if (context == null) {
			return 0;
		}
		Resources.Theme theme = context.getTheme();
		if (!theme.resolveAttribute(r_attr_theme_color, typedValue, true)) {
			Log.e(TAG, "Could not get resolveAttribute " + r_attr_theme_color
					+ " for " + AndroidUtils.getCompressedStackTrace());
			return 0;
		}

		try {
			TypedArray arr = context.obtainStyledAttributes(typedValue.data,
					new int[] {
						r_attr_theme_color
			});
			int c = arr.getColor(0, -1);
//			Log.d(TAG,
//					"Color for " + r_attr_theme_color + ", type " + typedValue.type +
//							";" + typedValue.coerceToString());// + " from " + arr);
			arr.recycle();
			if (c == -1) {
				if (AndroidUtils.DEBUG) {
					Log.e(TAG,
							"Could not get obtainStyledAttributes " + r_attr_theme_color
									+ " for " + AndroidUtils.getCompressedStackTrace());
				}

				// Sometimes TypedArray.getColor fails, but using TypedValue.resourceId
				// seems to work fine.  Could be related to Leanback?
				// TypedArray.getColor:
				// - failed | API 22 | DarkTheme  | Android TV
				// - failed | API 22 | DarkTheme  | Nexus 7
				// - Ok     | API 22 | LightTheme | Android TV
				// - Ok     | API 22 | LightTheme | Nexus 7
				// - Ok     | API 19 | LightTheme | GT 3
				// - Ok     | API 19 | DarkTheme  | GT 3
				// - Ok     | API 18 | LightTheme | Smartphone
				// - Ok     | API 18 | DarkTheme  | Smartphone
				// - Ok     | API 17 | DarkTheme  | FireTV
				return ContextCompat.getColor(context, typedValue.resourceId);
			} else {
				return c;
			}
		} catch (Resources.NotFoundException ignore) {
		}

		return typedValue.data;
	}

	public static void setViewChecked(View child, boolean activate) {
		if (child == null) {
			return;
		}
		if (child instanceof Checkable) {
			((Checkable) child).setChecked(activate);
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			child.setActivated(activate);
		}
	}

	public static boolean handleBrokenListViewScrolling(Activity a, int keyCode) {
		// Hack for FireTV 1st Gen (and possibly others):
		// sometimes scrolling up/down stops being processed by ListView,
		// even though there's more list to show.  Handle this manually
		// Dirty implemenation -- doesn't take into consideration disabled rows
		// or page-up/down/top/bottom key modifiers
		if (keyCode == KeyEvent.KEYCODE_DPAD_UP
				|| keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
			ListView lv = null;

			View currentFocus = a.getCurrentFocus();
			if (currentFocus instanceof ListView) {
				lv = (ListView) currentFocus;
				if (lv.getChoiceMode() == ListView.CHOICE_MODE_SINGLE) {
					int position = lv.getSelectedItemPosition();
					if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
						position--;
					} else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
						position++;
					}

					if (position > 0 && position < lv.getCount()) {
						lv.setSelection(position);
						return true;
					}
				}
			}

//			// For RecyclerView, we just need to scroll the next item into
//			// view, so that the super logic can find that the next down/up
//			// item exists.
//			// A PreCachingLayoutManager might also fix this problem
//			if (currentFocus != null &&
//					currentFocus.getParent() instanceof RecyclerView) {
//				RecyclerView rv = (RecyclerView) currentFocus.getParent();
//				RecyclerView.Adapter adapter = rv.getAdapter();
//				if (adapter instanceof FlexibleRecyclerAdapter) {
//					int position = ((FlexibleRecyclerAdapter) adapter)
//							.getSelectedPosition();
//					if (position >= 0) {
//						if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
//							position--;
//						} else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
//							position++;
//						}
//
//						if (position >= 0 && position < adapter.getItemCount()) {
//							rv.scrollToPosition(position);
//							Log.d(TAG, "handleBrokenListViewScrolling: DPAD HANDLED ");
//							return false;
//						}
//					}
//				}
//			}
		}

		return false;
	}

	public static void setManyMenuItemsEnabled(boolean enabled, Menu menu,
			int[] ids) {
		for (int id : ids) {
			MenuItem menuItem = menu.findItem(id);
			if (menuItem != null) {
				menuItem.setEnabled(enabled);
			}
		}
	}

	public static int dpToPx(int dp) {
		return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
	}

	public static int pxToDp(int px) {
		return (int) (px / Resources.getSystem().getDisplayMetrics().density);
	}

	public static MaterialEditText createFancyTextView(Context context) {
		MaterialEditText textView = new MaterialEditText(context);
		int styleColor = getStyleColor(context, android.R.attr.textColorPrimary);
		textView.setBaseColor(styleColor);
		textView.setMetTextColor(styleColor);
		textView.setFloatingLabel(MaterialEditText.FLOATING_LABEL_HIGHLIGHT);
		textView.setPrimaryColor(getStyleColor(context, R.attr.met_primary_color));
		return textView;
	}

	public interface OnTextBoxDialogClick
	{
		void onClick(DialogInterface dialog, int which, EditText editText);
	}

	public static AlertDialog.Builder createTextBoxDialog(Context context,
			int newtag_title, int newtag_hint,
			final OnTextBoxDialogClick onClickListener) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);

		FrameLayout container = new FrameLayout(context);
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		params.gravity = Gravity.CENTER_VERTICAL;
		container.setMinimumHeight(AndroidUtilsUI.dpToPx(100));
		int padding = AndroidUtilsUI.dpToPx(20);
		params.leftMargin = padding;
		params.rightMargin = padding;

		final MaterialEditText textView = AndroidUtilsUI.createFancyTextView(
				context);
		textView.setHint(newtag_hint);
		textView.setFloatingLabelText(
				context.getResources().getString(newtag_hint));
		textView.setSingleLine();
		textView.setLayoutParams(params);

		container.addView(textView);

		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1) {
			builder.setInverseBackgroundForced(true);
		}

		builder.setView(container);
		builder.setTitle(newtag_title);
		builder.setPositiveButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						onClickListener.onClick(dialog, which, textView);
					}
				});
		builder.setNegativeButton(android.R.string.cancel,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
					}
				});
		return builder;
	}

	public static Fragment getFocusedFragment(FragmentActivity activity) {
		View currentFocus = activity.getCurrentFocus();
		if (currentFocus == null) {
			return null;
		}
		ViewParent currentFocusParent = currentFocus.getParent();
		if (currentFocusParent == null) {
			return null;
		}
		List<Fragment> fragments = activity.getSupportFragmentManager().getFragments();
		for (Fragment f : fragments) {
			if (f == null) {
				continue;
			}
			ViewParent v = currentFocusParent;
			View fragmentView = f.getView();
			while (v != null) {
				if (v == fragmentView) {
					return f;
				}
				v = v.getParent();
			}
		}

		return null;
	}

	public static boolean isUIThread() {
		return Looper.getMainLooper().getThread() == Thread.currentThread();
	}

	public static boolean sendOnKeyToFragments(FragmentActivity activity,
			int keyCode, KeyEvent event) {
		Fragment focusedFragment = AndroidUtilsUI.getFocusedFragment(activity);
		if (focusedFragment instanceof View.OnKeyListener) {
			if (((View.OnKeyListener) focusedFragment).onKey(null, keyCode, event)) {
				return true;
			}
		}
		return false;
	}

	public static boolean popupContextMenu(Context context,
			ActionModeBeingReplacedListener l, String title) {
		final android.support.v7.view.ActionMode.Callback actionModeCallback = l.getActionModeCallback();
		if (actionModeCallback == null) {
			return false;
		}

		MenuBuilder menuBuilder = new MenuBuilder(context);

		if (title != null) {
			try {
				Method mSetHeaderTitle = menuBuilder.getClass().getDeclaredMethod(
						"setHeaderTitleInt", CharSequence.class);
				if (mSetHeaderTitle != null) {
					mSetHeaderTitle.setAccessible(true);
					mSetHeaderTitle.invoke(menuBuilder, title);
				}
			} catch (Throwable ignore) {
			}
		}

		if (!actionModeCallback.onCreateActionMode(null, menuBuilder)) {
			return false;
		}

		actionModeCallback.onPrepareActionMode(null, menuBuilder);

		menuBuilder.setCallback(new MenuBuilder.Callback() {
			@Override
			public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
				return actionModeCallback.onActionItemClicked(null, item);
			}

			@Override
			public void onMenuModeChange(MenuBuilder menu) {

			}
		});

		MenuDialogHelper menuDialogHelper = new MenuDialogHelper(menuBuilder);
		menuDialogHelper.show(null);

		return true;
	}

	public static boolean popupContextMenu(final Activity activity,
			String title) {
		MenuBuilder menuBuilder = new MenuBuilder(activity);

		if (title != null) {
			try {
				Method mSetHeaderTitle = menuBuilder.getClass().getDeclaredMethod(
						"setHeaderTitleInt", CharSequence.class);
				if (mSetHeaderTitle != null) {
					mSetHeaderTitle.setAccessible(true);
					mSetHeaderTitle.invoke(menuBuilder, title);
				}
			} catch (Throwable ignore) {
			}
		}

		if (!activity.onCreateOptionsMenu(menuBuilder)) {
			return false;
		}

		activity.onPrepareOptionsMenu(menuBuilder);

		menuBuilder.setCallback(new MenuBuilder.Callback() {
			@Override
			public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
				return activity.onOptionsItemSelected(item);
			}

			@Override
			public void onMenuModeChange(MenuBuilder menu) {

			}
		});

		MenuDialogHelper menuDialogHelper = new MenuDialogHelper(menuBuilder);
		menuDialogHelper.show(null);

		return true;
	}

	public static void requestPermissions(Activity activity, String[] permissions,
			Runnable runnableOnGrant, Runnable runnableOnDeny) {

		if (!(activity instanceof AppCompatActivityM)) {
			Log.e(TAG,
					"requestPermissions: activity " + activity
							+ " is not AppCompatActivityM for "
							+ AndroidUtils.getCompressedStackTrace());
			// fallback and just run it and hope we have perms
			if (runnableOnGrant != null) {
				runnableOnGrant.run();
			}
			return;
		}

		AppCompatActivityM a = (AppCompatActivityM) activity;
		a.requestPermissions(permissions, runnableOnGrant, runnableOnDeny);
	}
}
