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

package com.biglybt.android.client;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.*;

import com.biglybt.android.MenuDialogHelper;
import com.biglybt.android.client.activity.ThemedActivity;
import com.biglybt.android.client.dialog.DialogFragmentConnError;
import com.biglybt.android.client.dialog.DialogFragmentNoBrowser;
import com.biglybt.android.client.rpc.RPC;
import com.biglybt.android.client.rpc.RPCException;
import com.biglybt.android.client.session.SessionManager;
import com.rengwuxian.materialedittext.MaterialEditText;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.*;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Browser;
import android.speech.RecognizerIntent;
import android.support.annotation.*;
import android.support.v4.app.*;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.ImageViewCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.view.ActionMode.Callback;
import android.support.v7.view.menu.MenuBuilder;
import android.support.v7.view.menu.SubMenuBuilder;
import android.support.v7.widget.AppCompatDrawableManager;
import android.support.v7.widget.AppCompatImageView;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

@SuppressWarnings("WeakerAccess")
public class AndroidUtilsUI
{
	public static final boolean ALWAYS_DARK = false;

	private static final String TAG = "AndroidUtilsUI";

	public static class AlertDialogBuilder
	{
		@NonNull
		public final View view;

		@NonNull
		public final AlertDialog.Builder builder;

		public AlertDialogBuilder(@NonNull View view,
				@NonNull AlertDialog.Builder builder) {
			super();
			this.view = view;
			this.builder = builder;
		}
	}

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
				ViewGroup vg = a.findViewById(android.R.id.content);
				ArrayList<View> list = AndroidUtilsUI.findByClass(vg, ViewPager.class,
						new ArrayList<>(0));

				if (list.size() > 0) {
					ViewPager viewPager = (ViewPager) list.get(0);
					viewPager.arrowScroll(View.FOCUS_RIGHT);
				}
				break;
			}

			case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
			case KeyEvent.KEYCODE_MEDIA_REWIND: {
				ViewGroup vg = a.findViewById(android.R.id.content);
				ArrayList<View> list = AndroidUtilsUI.findByClass(vg, ViewPager.class,
						new ArrayList<>(0));

				if (list.size() > 0) {
					ViewPager viewPager = (ViewPager) list.get(0);
					viewPager.arrowScroll(View.FOCUS_LEFT);
				}
				break;
			}

		}

		return false;
	}

	public static void invalidateOptionsMenuHC(final Activity activity) {
		invalidateOptionsMenuHC(activity, null);
	}

	public static void invalidateOptionsMenuHC(final Activity activity,
			@Nullable final android.support.v7.view.ActionMode mActionMode) {
		runOnUIThread(activity, false, validActivity -> {
			if (mActionMode != null) {
				mActionMode.invalidate();
				return;
			}
			validActivity.invalidateOptionsMenu();
		});
	}

	static final Map<CharSequence, SparseIntArray> mapStyleToColor = new HashMap<>(
			2);

	public static int getStyleColor(Context context, int r_attr_theme_color) {
		TypedValue typedValue = new TypedValue();
		if (context == null) {
			return 0;
		}
		Resources.Theme theme = context.getTheme();
		TypedValue themeName = new TypedValue();
		theme.resolveAttribute(R.attr.themeName, themeName, true);
		SparseIntArray themeMap = null;
		if (themeName.string != null) {
			themeMap = mapStyleToColor.get(themeName.string);
			if (themeMap == null) {
				mapStyleToColor.put(themeName.string, new SparseIntArray());
			} else {
				int val = themeMap.get(r_attr_theme_color, 0xDEADBEEF);
				if (val != 0xDEADBEEF) {
					return val;
				}
			}
		}

		if (!theme.resolveAttribute(r_attr_theme_color, typedValue, true)) {
			Log.e(TAG, "Could not get resolveAttribute " + r_attr_theme_color
					+ " for " + AndroidUtils.getCompressedStackTrace());
			return 0;
		}

		if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT
				&& typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
			if (themeMap != null) {
				themeMap.put(r_attr_theme_color, typedValue.data);
			}
			return typedValue.data;
		}

		try {
			TypedArray arr = context.obtainStyledAttributes(typedValue.data,
					new int[] {
						r_attr_theme_color
					});
			int c = arr.getColor(0, 0xDEADBEEF);
//			Log.d(TAG,
//					"Color for " + r_attr_theme_color + ", type " + typedValue.type +
//							";" + typedValue.coerceToString());// + " from " + arr);
			arr.recycle();
			if (c == 0xDEADBEEF) {
				if (AndroidUtils.DEBUG) {
					Log.e(TAG,
							"Could not get obtainStyledAttributes " + r_attr_theme_color
									+ "; " + typedValue + " for "
									+ AndroidUtils.getCompressedStackTrace());
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
				c = ContextCompat.getColor(context, typedValue.resourceId);
				if (themeMap != null) {
					themeMap.put(r_attr_theme_color, c);
				}
				return c;
			} else {
				if (themeMap != null) {
					themeMap.put(r_attr_theme_color, c);
				}
				return c;
			}
		} catch (Resources.NotFoundException ignore) {
		}

		if (themeMap != null) {
			themeMap.put(r_attr_theme_color, typedValue.data);
		}
		return typedValue.data;
	}

	public static void setViewChecked(View child, boolean activate) {
		if (child == null) {
			return;
		}
		if (child instanceof Checkable) {
			((Checkable) child).setChecked(activate);
		} else {
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
			ListView lv;

			View currentFocus = a.getCurrentFocus();
			if (currentFocus instanceof ListView) {
				lv = (ListView) currentFocus;
				if (lv.getChoiceMode() == ListView.CHOICE_MODE_SINGLE) {
					int position = lv.getSelectedItemPosition();
					if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
						position--;
					} else { //if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
						position++;
					}

					if (position > 0 && position < lv.getCount()) {
						lv.setSelection(position);
						return true;
					}
				}
			}
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

	public static void setManyMenuItemsVisible(boolean visible, Menu menu,
			int[] ids) {
		for (int id : ids) {
			MenuItem menuItem = menu.findItem(id);
			if (menuItem != null) {
				menuItem.setVisible(visible);
			}
		}
	}

	public static int dpToPx(int dp) {
		return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
	}

	public static int pxToDp(int px) {
		return (int) (px / Resources.getSystem().getDisplayMetrics().density);
	}

	public static float pxToInchX(int px) {
		return (px / Resources.getSystem().getDisplayMetrics().xdpi);
	}

	public static float pxToInchY(int px) {
		return (px / Resources.getSystem().getDisplayMetrics().ydpi);
	}

	public static int spToPx(int sp) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
				Resources.getSystem().getDisplayMetrics());
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
		@SuppressWarnings("UnusedParameters")
		void onClick(DialogInterface dialog, int which, EditText editText);
	}

	public static AlertDialog createTextBoxDialog(@NonNull Context context,
			@StringRes int titleResID, @StringRes int hintResID,
			@StringRes int helperResID, final OnTextBoxDialogClick onClickListener) {
		return createTextBoxDialog(context, titleResID, hintResID, helperResID,
				null, EditorInfo.IME_ACTION_DONE, InputType.TYPE_CLASS_TEXT,
				onClickListener);
	}

	// So many params, could use a builder
	public static AlertDialog createTextBoxDialog(@NonNull final Context context,
			@StringRes final int titleResID, @StringRes int hintResID,
			@StringRes int helperResID, @Nullable String presetText,
			final int imeOptions,
			@NonNull final OnTextBoxDialogClick onClickListener) {
		return createTextBoxDialog(context, titleResID, hintResID, helperResID,
				presetText, imeOptions, InputType.TYPE_CLASS_TEXT, onClickListener);
	}

	// So many params, could use a builder
	public static AlertDialog createTextBoxDialog(@NonNull final Context context,
			@StringRes final int titleResID, @StringRes int hintResID,
			@StringRes int helperResID, @Nullable String presetText,
			final int imeOptions, int inputType,
			@NonNull final OnTextBoxDialogClick onClickListener) {
		final AlertDialog.Builder builder = new AlertDialog.Builder(context);

		final AlertDialog[] dialog = {
			null
		};

		PackageManager pm = context.getPackageManager();
		List<ResolveInfo> activities = pm.queryIntentActivities(
				new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
		boolean hasSpeechRecognization = activities.size() > 0;
		int tvHorizPadding = dpToPx(20);
		int px48 = dpToPx(48);
		int pxButtonPadding = px48 / 4;

		LinearLayout container = new LinearLayout(context);
		container.setMinimumHeight(dpToPx(100));
		container.setOrientation(LinearLayout.HORIZONTAL);
		container.setGravity(Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL);
		container.setPadding(0, pxButtonPadding, 0, 0);

		final MaterialEditText textView = createFancyTextView(context);
		if (hintResID != 0) {
			textView.setHint(hintResID);
			textView.setFloatingLabelText(context.getString(hintResID));
		} else {
			textView.setHint(titleResID);
		}
		if (helperResID != 0) {
			textView.setHelperText(context.getString(helperResID));
		}
		textView.setSingleLine();
		textView.setImeOptions(imeOptions);
		textView.setInputType(inputType);
		textView.setOnEditorActionListener((v, actionId, event) -> {
			if (dialog[0] == null) {
				return false;
			}
			if (actionId == imeOptions || (actionId == 0 && event != null
					&& event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
				// Won't work
				//dialog[0].dismiss();
				//dialog[0].cancel();

				// From http://stackoverflow.com/a/38390615
				dialog[0].getButton(DialogInterface.BUTTON_POSITIVE).performClick();
				return true;
			}
			return false;
		});

		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		params.gravity = Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL;
		params.weight = 1;
		params.leftMargin = tvHorizPadding;
		if (!hasSpeechRecognization) {
			params.rightMargin = tvHorizPadding;
		}
		params.bottomMargin = pxButtonPadding;
		textView.setLayoutParams(params);
		if (presetText != null) {
			textView.setText(presetText);
		}

		container.addView(textView);

		if (hasSpeechRecognization) {
			ImageView imageButton = new AppCompatImageView(context);
			imageButton.setContentDescription(
					context.getString(R.string.spoken_speak));
			imageButton.setImageResource(R.drawable.ic_keyboard_voice_black_24dp);
			ImageViewCompat.setImageTintList(imageButton,
					AppCompatResources.getColorStateList(context,
							R.color.focus_selector));
			imageButton.setClickable(true);
			imageButton.setFocusable(true);
			Configuration config = context.getResources().getConfiguration();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
					&& config.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
				textView.setNextFocusLeftId(imageButton.getId());
			} else {
				textView.setNextFocusRightId(imageButton.getId());
			}
			imageButton.setOnClickListener(v -> {
				Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
				intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
						RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
				intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
						v.getResources().getText(titleResID));
				if (context instanceof ThemedActivity) {
					ThemedActivity.captureActivityResult = (requestCode, resultCode,
							intent1) -> {
						if (requestCode == ThemedActivity.REQUEST_VOICE) {
							ThemedActivity.captureActivityResult = null;
							if (resultCode == Activity.RESULT_OK
									&& intent1.getExtras() != null) {
								ArrayList<String> list = intent1.getExtras().getStringArrayList(
										RecognizerIntent.EXTRA_RESULTS);
								if (list != null && list.size() > 0) {
									textView.setText(list.get(0));
								}
							}
							return true;
						}
						return false;
					};
					((ThemedActivity) context).startActivityForResult(intent,
							ThemedActivity.REQUEST_VOICE);
				}
			});
			params = new LinearLayout.LayoutParams(px48, px48);
			params.gravity = Gravity.CENTER_VERTICAL;
			// this isn't exact :(
			int extraBottomPadding = textView.getHelperText() == null ? 0
					: textView.getBottomTextSize() + textView.getInnerPaddingBottom();
			imageButton.setPadding(pxButtonPadding,
					pxButtonPadding - extraBottomPadding, pxButtonPadding,
					pxButtonPadding + extraBottomPadding);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
				params.setMarginEnd(tvHorizPadding);
			}
			params.rightMargin = tvHorizPadding;
			imageButton.setLayoutParams(params);

			container.addView(imageButton);
		}

		builder.setView(container);
		builder.setTitle(titleResID);
		builder.setPositiveButton(android.R.string.ok,
				(dialogP, which) -> onClickListener.onClick(dialogP, which, textView));
		builder.setNegativeButton(android.R.string.cancel, (dNeg, which) -> {
		});
		if ((inputType & InputType.TYPE_TEXT_VARIATION_PASSWORD) > 0) {
			builder.setNeutralButton(R.string.button_clear, null);
		}
		builder.setOnDismissListener(
				dialog13 -> ThemedActivity.captureActivityResult = null);

		dialog[0] = builder.create();
		dialog[0].setOnShowListener(di -> {
			final Button btnNeutral = dialog[0].getButton(AlertDialog.BUTTON_NEUTRAL);
			if (btnNeutral != null) {
				btnNeutral.setOnClickListener(v -> textView.setText(""));
			}

		});
		return dialog[0];
	}

	@Nullable
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

		List<Fragment> fragments = activity.getSupportFragmentManager().getFragments();
		for (Fragment f : fragments) {
			if (f instanceof View.OnKeyListener) {
				if (((View.OnKeyListener) f).onKey(null, keyCode, event)) {
					return true;
				}
			}
		}
		return false;
	}

	public static Drawable getTintedDrawable(@NonNull Context context,
			@NonNull Drawable inputDrawable, @ColorInt int color) {
		Drawable wrapDrawable = DrawableCompat.wrap(inputDrawable);
		DrawableCompat.setTint(wrapDrawable, color);
		DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_IN);
		return wrapDrawable;
	}

	public static void tintAllIcons(Menu menu, final int color) {
		for (int i = 0; i < menu.size(); ++i) {
			final MenuItem item = menu.getItem(i);
			tintMenuItemIcon(color, item);
		}
	}

	private static void tintMenuItemIcon(int color, MenuItem item) {
		final Drawable drawable = item.getIcon();
		if (drawable != null) {
			final Drawable wrapped = DrawableCompat.wrap(drawable);
			drawable.mutate();
			DrawableCompat.setTint(wrapped, color);
			item.setIcon(drawable);
		}
	}

	/**
	 * Popup a ActionMode Menu
	 */
	@SuppressLint("RestrictedApi")
	public static boolean popupContextMenu(Context context,
			final Callback actionModeCallback, CharSequence title) {
		MenuBuilder menuBuilder = new MenuBuilder(context);

		SubMenu subMenu = menuBuilder.addSubMenu(title);
		if (subMenu instanceof SubMenuBuilder) {
			return popupSubMenu((SubMenuBuilder) subMenu, actionModeCallback, title);
		}

		Log.w(TAG, "popupContextMenu: SubMenu wasn't SubMenuBuilder");
		return false;
	}

	/**
	 * Popup a SubMenu
	 */
	@SuppressLint("RestrictedApi")
	public static boolean popupSubMenu(SubMenuBuilder subMenu,
			final Callback actionModeCallback, CharSequence title) {
		if (actionModeCallback == null) {
			return false;
		}

		if (title != null) {
			subMenu.setHeaderTitle(title);
		}
		if (!actionModeCallback.onCreateActionMode(null, subMenu)) {
			return false;
		}

		actionModeCallback.onPrepareActionMode(null, subMenu);

		subMenu.setCallback(new MenuBuilder.Callback() {
			@Override
			public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
				return actionModeCallback.onActionItemClicked(null, item);
			}

			@Override
			public void onMenuModeChange(MenuBuilder menu) {
			}
		});

		subMenu.setOptionalIconsVisible(true);
		int styleColor = AndroidUtilsUI.getStyleColor(subMenu.getContext(),
				android.R.attr.textColorPrimary);
		tintAllIcons(subMenu, styleColor);
		MenuDialogHelper menuDialogHelper = new MenuDialogHelper(subMenu);
		menuDialogHelper.show(null);

		return true;
	}

	public static void requestPermissions(Activity activity, String[] permissions,
			@Nullable Runnable runnableOnGrant, @Nullable Runnable runnableOnDeny) {

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

	public interface LinkClickListener
	{
		/**
		 * @return true if handled
		 */
		boolean linkClicked(String link);
	}

	public static void linkify(final Activity activity, @Nullable TextView tv,
			@Nullable final LinkClickListener l, @StringRes int id,
			Object... formatArgs) {
		if (tv == null) {
			return;
		}
		Spanned spanned = AndroidUtils.fromHTML(activity.getResources(), id,
				formatArgs);
		linkify(activity, tv, l, spanned, formatArgs);
	}

	public static void linkify(final Activity activity, TextView tv,
			@Nullable final LinkClickListener l, String msg, Object... formatArgs) {

		Spanned spanned = AndroidUtils.fromHTML(msg);
		linkify(activity, tv, l, spanned, formatArgs);
	}

	public static void linkify(final Activity activity, TextView tv,
			@Nullable final LinkClickListener l, Spanned spanned,
			Object... formatArgs) {
		if (tv == null) {
			return;
		}

		URLSpan[] urls = spanned.getSpans(0, spanned.length(), URLSpan.class);
		SpannableStringBuilder strBuilder = new SpannableStringBuilder(spanned);
		for (final URLSpan span : urls) {
			int start = strBuilder.getSpanStart(span);
			int end = strBuilder.getSpanEnd(span);
			final String title = spanned.subSequence(start, end).toString();

			Object newSpan;
			if (l != null) {
				newSpan = new ClickableSpan() {
					public void onClick(View view) {
						String url = span.getURL();
						if (l.linkClicked(url)) {
							return;
						}
						if (url.contains("://")) {
							openURL(activity, url, title);
						}
					}
				};
			} else {
				newSpan = new UrlSpan2(activity, span.getURL(), title);
			}

			replaceSpan(strBuilder, span, newSpan);
		}
		tv.setText(strBuilder);
		tv.setMovementMethod(LinkMovementMethod.getInstance());
	}

	/**
	 * Replaces one span with the other. 
	 */
	private static void replaceSpan(SpannableStringBuilder builder,
			Object originalSpan, Object newSpan) {
		builder.setSpan(newSpan, builder.getSpanStart(originalSpan),
				builder.getSpanEnd(originalSpan), builder.getSpanFlags(originalSpan));
		builder.removeSpan(originalSpan);
	}

	@SuppressLint("ParcelCreator")
	public static final class UrlSpan2
		extends URLSpan
	{
		private final Activity activity;

		private final String title;

		public UrlSpan2(Activity activity, String url, String title) {
			super(url);
			this.activity = activity;
			this.title = title;
		}

		@SuppressWarnings("MethodDoesntCallSuperMethod")
		@Override
		public void onClick(View widget) {
			openURL(activity, getURL(), title);
		}

	}

	public static void openURL(Activity activity, String url, String title) {
		boolean useNoBrowserDialog = AndroidUtils.isLiterallyLeanback(activity)
				&& url.startsWith("http");
		if (!useNoBrowserDialog) {
			Uri uri = Uri.parse(url);
			Intent intent = new Intent(Intent.ACTION_VIEW, uri);
			intent.putExtra(Browser.EXTRA_APPLICATION_ID, activity.getPackageName());
			PackageManager pm = activity.getPackageManager();
			ResolveInfo info = pm.resolveActivity(intent, 0);

			useNoBrowserDialog = info == null;
			if (info != null) {
				ComponentInfo componentInfo = AndroidUtils.getComponentInfo(info);
				useNoBrowserDialog = componentInfo == null
						|| componentInfo.name.contains("frameworkpackagestubs")
						// Fire TV has a pretty nice dialog notifying the user there is no
						// browser, but we have a better one
						|| "com.amazon.tv.intentsupport.TvIntentSupporter".equals(
								componentInfo.name)
						// Pure evil, PlayStation Video app registers to capture urls,
						// so any app on a Sony Android TV that tries to launch an URL
						// gets their disgusting video store.  Shame on you, Sony.
						|| "com.sony.snei.video.hhvu.MainActivity".equals(
								componentInfo.name);
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "openURL: launch " + componentInfo + " for " + uri);
				}
			}

			try {
				if (!useNoBrowserDialog) {
					activity.startActivity(intent);
					return;
				}
			} catch (ActivityNotFoundException e) {
				Log.w("openURL",
						"Actvity was not found for intent, " + intent.toString());
			}
		}

		if (activity instanceof FragmentActivity) {
			FragmentManager fm = ((FragmentActivity) activity).getSupportFragmentManager();
			DialogFragmentNoBrowser.open(fm, url, title);
		}
	}

	public static boolean isChildOf(@Nullable View child, ViewGroup vg) {
		if (child == null || vg == null) {
			return false;
		}
		ViewParent parent = child.getParent();
		while (parent != null) {
			if (parent == vg) {
				return true;
			}
			parent = parent.getParent();
		}
		return false;
	}

	public static int getScreenWidthPx(Context context) {
		Resources resources = context.getResources();
		DisplayMetrics dm = resources.getDisplayMetrics();
		int orientation = resources.getConfiguration().orientation;

		return (orientation == Configuration.ORIENTATION_LANDSCAPE
				? Math.max(dm.widthPixels, dm.heightPixels)
				: Math.min(dm.widthPixels, dm.heightPixels));
	}

	public static int getScreenHeightPx(Context context) {
		Resources resources = context.getResources();
		DisplayMetrics dm = resources.getDisplayMetrics();
		int orientation = resources.getConfiguration().orientation;

		return (orientation == Configuration.ORIENTATION_PORTRAIT
				? Math.max(dm.widthPixels, dm.heightPixels)
				: Math.min(dm.widthPixels, dm.heightPixels));
	}

	public static int getScreenWidthDp(Context context) {
		Resources resources = context.getResources();
		DisplayMetrics dm = resources.getDisplayMetrics();
		int orientation = resources.getConfiguration().orientation;

		return pxToDp(orientation == Configuration.ORIENTATION_LANDSCAPE
				? Math.max(dm.widthPixels, dm.heightPixels)
				: Math.min(dm.widthPixels, dm.heightPixels));
	}

	public static int getScreenHeightDp(Context context) {
		Resources resources = context.getResources();
		DisplayMetrics dm = resources.getDisplayMetrics();
		int orientation = resources.getConfiguration().orientation;

		return pxToDp(orientation == Configuration.ORIENTATION_PORTRAIT
				? Math.max(dm.widthPixels, dm.heightPixels)
				: Math.min(dm.widthPixels, dm.heightPixels));
	}

	@Nullable
	public static Drawable getDrawableWithBounds(Context context, int resID) {
		@SuppressLint("RestrictedApi")
		Drawable drawableCompat = AppCompatDrawableManager.get().getDrawable(
				context, resID);
		if (drawableCompat != null) {
			if (drawableCompat.getBounds().isEmpty()) {
				drawableCompat.setBounds(0, 0, drawableCompat.getIntrinsicWidth(),
						drawableCompat.getIntrinsicHeight());
			}
		}
		return drawableCompat;
	}

	public static boolean childOrParentHasTag(View child, String tag) {
		if (child == null || tag == null) {
			return false;
		}
		if (tag.equals(child.getTag())) {
			return true;
		}
		ViewParent parent = child.getParent();
		while (parent != null) {

			if ((parent instanceof View) && tag.equals(((View) parent).getTag())) {
				return true;
			}
			parent = parent.getParent();
		}
		return false;

	}

	@SuppressWarnings("unused")
	public static boolean isViewContains(View view, int rx, int ry) {
		Rect rect = new Rect();
		view.getGlobalVisibleRect(rect);

		return rect.contains(rx, ry);
	}

	public static ViewGroup getContentView(Activity activity) {
		return (ViewGroup) ((ViewGroup) activity.findViewById(
				android.R.id.content)).getChildAt(0);
	}

	public static void walkTree(View rootView, String indent) {

		if (rootView instanceof FrameLayout) {
			FrameLayout f = (FrameLayout) rootView;

			int childCount = f.getChildCount();
			if (childCount > 0) {
				for (int i = 0; i < childCount; i++) {
					View childAt = f.getChildAt(i);
					Log.d(TAG, indent + "walkTree: child " + i + ": " + childAt + ";"
							+ Integer.toHexString(childAt.getId()));
					walkTree(childAt, indent + "\t");

				}
			} else {
				Log.d(TAG, indent + "walkTree: rootView=" + rootView);
			}
		}
	}

	/**
	 * Creates an AlertDialog.Builder that has the proper theme for Gingerbread
	 */
	public static AlertDialogBuilder createAlertDialogBuilder(Activity activity,
			int resource) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);

		View view = View.inflate(activity, resource, null);
		builder.setView(view);

		return new AlertDialogBuilder(view, builder);
	}

	public static void showConnectionError(FragmentActivity activity,
			String profileID, Throwable t, boolean allowContinue) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "showConnectionError "
					+ AndroidUtils.getCompressedStackTrace(t, 0, 9));
		}

		Throwable t2 = (t instanceof RPCException) ? t.getCause() : t;

		if ((t2 instanceof ConnectException)
				|| (t2 instanceof UnknownHostException)) {
			String message = t.getMessage();
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "showConnectionError Yup " + message);
			}
			if (message != null && message.contains(RPC.PAIR_DOMAIN)) {
				showConnectionError(activity, profileID, R.string.connerror_pairing,
						allowContinue);
				return;
			}
		}
		String message = "";
		while (t != null) {
			String newMessage = t.getMessage();
			if (newMessage != null && message.contains(newMessage)) {
				t = t.getCause();
				continue;
			}
			message += newMessage + "\n";
			Throwable tReplace = t;
			while (tReplace != null) {
				Class<?> cla = tReplace.getClass();
				String name = cla.getName();
				message = message.replaceAll(name + ": ", cla.getSimpleName() + ": ");
				tReplace = tReplace.getCause();
			}
			t = t.getCause();
		}
		showConnectionError(activity, message, allowContinue);
	}

	public static void showConnectionError(FragmentActivity activity,
			String profileID, int errMsgID, boolean allowContinue) {
		if (activity == null) {
			if (AndroidUtils.DEBUG) {
				Log.w(TAG, "showConnectionError: no activity, can't show " + errMsgID
						+ ";" + AndroidUtils.getCompressedStackTrace());
			}
			if (!allowContinue) {
				SessionManager.removeSession(profileID);
			}
			return;
		}
		String errMsg = activity.getResources().getString(errMsgID);
		showConnectionError(activity, errMsg, allowContinue);
	}

	public static void showConnectionError(final FragmentActivity activity,
			final CharSequence errMsg, final boolean allowContinue) {

		if (activity == null) {
			Log.w(TAG, "can't display '" + errMsg + "'");
			return;
		}
		DialogFragmentConnError.openDialog(activity.getSupportFragmentManager(), "",
				errMsg, allowContinue);
	}

	public static void showDialog(final FragmentActivity activity,
			final @StringRes int title, final @StringRes int msg,
			final Object... formatArgs) {
		runOnUIThread(activity, false, validActivity -> {
			AlertDialog.Builder builder = new AlertDialog.Builder(
					validActivity).setMessage(msg).setCancelable(true).setNegativeButton(
							android.R.string.ok, (dialog, which) -> {
							});
			if (title != 0) {
				builder.setTitle(title);
			}
			AlertDialog alertDialog = builder.show();
			View vMessage = alertDialog.findViewById(android.R.id.message);
			if (vMessage instanceof TextView) {
				linkify(validActivity, (TextView) vMessage, null, msg, formatArgs);
			}
		});

	}

	public static void showFeatureRequiresBiglyBT(final Activity activity,
			final String feature) {
		runOnUIThread(activity, false, validActivity -> {
			String msg = activity.getResources().getString(R.string.biglybt_required,
					feature);
			AlertDialog.Builder builder = new AlertDialog.Builder(
					validActivity).setMessage(msg).setCancelable(true).setPositiveButton(
							android.R.string.ok, (dialog, which) -> {
							});
			builder.show();
		});

	}

	/**
	 * Same as {@link Activity#runOnUiThread(Runnable)}, except ensures
	 * activity still exists while in UI Thread, before executing runnable
	 */
	public static void runOnUIThread(final Fragment fragment,
			final boolean allowFinishing,
			final @NonNull RunnableWithActivity runnable) {
		Activity activity = fragment.getActivity();
		if (activity == null) {
			return;
		}
		final String stack = AndroidUtils.DEBUG
				? AndroidUtils.getCompressedStackTrace() : null;
		activity.runOnUiThread(() -> {
			Activity activity1 = fragment.getActivity();
			if (activity1 == null) {
				return;
			}
			if (allowFinishing || !activity1.isFinishing()) {
				long start = 0;
				if (AndroidUtils.DEBUG) {
					start = System.currentTimeMillis();
				}
				runnable.run(activity1);
				if (AndroidUtils.DEBUG) {
					long diff = System.currentTimeMillis() - start;
					if (diff > 500) {
						Log.w(TAG, "runOnUIThread: " + diff + "ms for " + stack);
					}
				}
			}
		});
	}

	/**
	 * Same as {@link Activity#runOnUiThread(Runnable)}, except ensures
	 * activity still exists while in UI Thread, before executing runnable
	 */
	public static void runOnUIThread(final Activity activity,
			final boolean allowFinishing,
			final @NonNull RunnableWithActivity runnable) {
		if (activity == null) {
			return;
		}
		final String stack = AndroidUtils.DEBUG
				? AndroidUtils.getCompressedStackTrace() : null;
		activity.runOnUiThread(() -> {
			if (allowFinishing || !activity.isFinishing()) {
				long start = 0;
				if (AndroidUtils.DEBUG) {
					start = System.currentTimeMillis();
				}
				runnable.run(activity);
				if (AndroidUtils.DEBUG) {
					long diff = System.currentTimeMillis() - start;
					if (diff > 500) {
						Log.w(TAG, "runOnUIThread: " + diff + "ms for " + stack);
					}
				}
			} else if (AndroidUtils.DEBUG) {
				Log.w(TAG, "runOnUIThread: skipped runOnUIThread on finish activity "
						+ activity + ", " + runnable);
			}
		});
	}

	public static boolean showDialog(DialogFragment dlg, FragmentManager fm,
			String tag) {
		if (fm == null) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG,
						"showDialog: fm null; " + AndroidUtils.getCompressedStackTrace());
			}
			return false;
		}
		try {
			dlg.show(fm, tag);
			return true;
		} catch (IllegalStateException e) {
			e.printStackTrace();
			// Activity is no longer active (ie. most likely paused)
			return false;
		}
	}

	public static boolean showDialog(android.app.DialogFragment dlg,
			android.app.FragmentManager fm, String tag) {
		if (fm == null) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG,
						"showDialog: fm null; " + AndroidUtils.getCompressedStackTrace());
			}
			return false;
		}
		try {
			dlg.show(fm, tag);

			return true;
		} catch (IllegalStateException e) {
			e.printStackTrace();
			// Activity is no longer active (ie. most likely paused)
			return false;
		}
	}

	public static void openMarket(Context context, String appPackageName) {
		try {
			Intent intent = new Intent(Intent.ACTION_VIEW,
					Uri.parse("market://details?id=" + appPackageName)); //NON-NLS
			Intent chooser = Intent.createChooser(intent, null);
			context.startActivity(chooser);
		} catch (android.content.ActivityNotFoundException anfe) {
			context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
					"http://play.google.com/store/apps/details?id=" + appPackageName))); //NON-NLS
		}
	}

	public static @NonNull List<Fragment> getFragments(
			FragmentManager fragmentManager) {
		if (fragmentManager == null) {
			return Collections.emptyList();
		}

		return fragmentManager.getFragments();
	}

	public static boolean runIfNotUIThread(Runnable runnable) {
		if (!AndroidUtilsUI.isUIThread()) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "delaying call to " + runnable);
			}
			new Handler(Looper.getMainLooper()).post(runnable::run);
			return true;
		}
		return false;

	}

	/**
	 * Causes the Runnable r to be added to the message queue on the UI Thread.
	 *
	 * @param r The Runnable that will be executed.
	 *
	 * @return Returns true if the Runnable was successfully placed in to the 
	 *         message queue.  Returns false on failure, usually because the
	 *         looper processing the message queue is exiting.
	 */
	public static boolean postDelayed(Runnable r) {
		return new Handler(Looper.getMainLooper()).post(r::run);
	}
}
