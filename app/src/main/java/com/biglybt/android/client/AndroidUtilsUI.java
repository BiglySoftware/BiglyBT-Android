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

import static com.biglybt.android.client.AndroidUtils.requirePackageManager;
import static com.biglybt.android.client.AndroidUtils.requireResources;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.*;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Browser;
import android.speech.RecognizerIntent;
import android.text.*;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ActionMode.Callback;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.SubMenuBuilder;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.*;
import androidx.viewpager.widget.ViewPager;

import com.biglybt.android.MenuDialogHelper;
import com.biglybt.android.client.activity.ThemedActivity;
import com.biglybt.android.client.dialog.DialogFragmentConnError;
import com.biglybt.android.client.dialog.DialogFragmentNoBrowser;
import com.biglybt.android.client.rpc.RPC;
import com.biglybt.android.client.rpc.RPCException;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.android.client.spanbubbles.SpanBubbles;
import com.biglybt.android.util.PathInfo;
import com.biglybt.util.RunnableUIThread;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.BaseProgressIndicator;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.jetbrains.annotations.Contract;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.*;

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

	@NonNull
	public static ArrayList<View> findByClass(@NonNull ViewGroup root,
			@NonNull Class<? extends View> type, ArrayList<View> list) {
		if (list == null) {
			list = new ArrayList<>();
		}
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

	@UiThread
	public static boolean handleCommonKeyDownEvents(@NonNull Activity a,
			int keyCode, @NonNull KeyEvent event) {
		if (event.getAction() != KeyEvent.ACTION_DOWN) {
			return false;
		}
		switch (keyCode) {
			case KeyEvent.KEYCODE_MEDIA_NEXT:
			case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: {
				ViewGroup vg = a.findViewById(android.R.id.content);
				if (vg == null) {
					break;
				}
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
				if (vg == null) {
					break;
				}
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

	@AnyThread
	public static void invalidateOptionsMenuHC(final Activity activity) {
		invalidateOptionsMenuHC(activity, null);
	}

	@AnyThread
	public static void invalidateOptionsMenuHC(final Activity activity,
			@Nullable final androidx.appcompat.view.ActionMode mActionMode) {
		OffThread.runOnUIThread(activity, false, validActivity -> {
			if (mActionMode != null) {
				mActionMode.invalidate();
				return;
			}
			validActivity.invalidateOptionsMenu();
		});
	}

	static final Map<CharSequence, SparseIntArray> mapStyleToColor = new HashMap<>(
			2);

	@UiThread
	public static int getStyleColor(Context context, int r_attr_theme_color) {
		TypedValue typedValue = new TypedValue();
		if (context == null) {
			return 0;
		}
		Resources.Theme theme = context.getTheme();
		if (theme == null) {
			return 0;
		}
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
			}
			if (themeMap != null) {
				themeMap.put(r_attr_theme_color, c);
			}
			return c;
		} catch (Resources.NotFoundException ignore) {
		}

		if (themeMap != null) {
			themeMap.put(r_attr_theme_color, typedValue.data);
		}
		return typedValue.data;
	}

	@UiThread
	public static boolean handleBrokenListViewScrolling(@NonNull Activity a,
			int keyCode) {
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

	@UiThread
	public static void setManyMenuItemsEnabled(boolean enabled,
			@NonNull Menu menu, @NonNull @IdRes int... ids) {
		for (int id : ids) {
			MenuItem menuItem = menu.findItem(id);
			if (menuItem != null) {
				menuItem.setEnabled(enabled);
			}
		}
	}

	@UiThread
	public static void setManyMenuItemsVisible(boolean visible,
			@NonNull Menu menu, @NonNull @IdRes int... ids) {
		for (int id : ids) {
			MenuItem menuItem = menu.findItem(id);
			if (menuItem != null) {
				menuItem.setVisible(visible);
			}
		}
	}

	/**
	 * @todo Should Use: return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
	 */
	public static int dpToPx(int dp) {
		return dp == 0 ? 0 : (int) (dp
				* requireResources((Context) null).getDisplayMetrics().density);
	}

	/**
	 * @todo Should Use: return px / context.getResources().getDisplayMetrics().density;
	 */
	public static int pxToDp(int px) {
		return (int) (px
				/ requireResources((Context) null).getDisplayMetrics().density);
	}

	public static float pxToInchX(int px) {
		return (px / requireResources((Context) null).getDisplayMetrics().xdpi);
	}

	public static float pxToInchY(int px) {
		return (px / requireResources((Context) null).getDisplayMetrics().ydpi);
	}

	public static int spToPx(int sp) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
				requireResources((Context) null).getDisplayMetrics());
	}

	public interface OnTextBoxDialogClick
	{
		@SuppressWarnings("UnusedParameters")
		@UiThread
		void onClick(DialogInterface dialog, int which, EditText editText);
	}

	@UiThread
	public static AlertDialog createTextBoxDialog(@NonNull Context context,
			@StringRes int titleResID, @StringRes int hintResID,
			@StringRes int helperResID,
			@NonNull OnTextBoxDialogClick onClickListener) {
		return createTextBoxDialog(context,
				titleResID == View.NO_ID ? null : context.getString(titleResID),
				hintResID == View.NO_ID ? null : context.getString(hintResID),
				helperResID == View.NO_ID ? null : context.getString(helperResID), null,
				EditorInfo.IME_ACTION_DONE, InputType.TYPE_CLASS_TEXT, onClickListener);
	}

	// So many params, could use a builder
	@UiThread
	public static AlertDialog createTextBoxDialog(@NonNull final Context context,
			@StringRes final int titleResID, @StringRes int hintResID,
			@StringRes int helperResID, @Nullable String presetText,
			final int imeOptions,
			@NonNull final OnTextBoxDialogClick onClickListener) {
		return createTextBoxDialog(context,
				titleResID == View.NO_ID ? null : context.getString(titleResID),
				hintResID == View.NO_ID ? null : context.getString(hintResID),
				helperResID == View.NO_ID ? null : context.getString(helperResID),
				presetText, imeOptions, InputType.TYPE_CLASS_TEXT, onClickListener);
	}

	// So many params, could use a builder
	@NonNull
	@UiThread
	public static AlertDialog createTextBoxDialog(@NonNull final Context context,
			CharSequence title, String hint, String helper,
			@Nullable String presetText, final int imeOptions, int inputType,
			@NonNull final OnTextBoxDialogClick onClickListener) {
		AlertDialogBuilder adb = createAlertDialogBuilder(context,
				R.layout.dialog_text_input);
		return createTextBoxDialog(context, adb, title, hint,
				helper, presetText, imeOptions, inputType, onClickListener);
	}

	// So many params, could use a builder
	@NonNull
	@UiThread
	public static AlertDialog createTextBoxDialog(@NonNull final Context context,
			AlertDialogBuilder adb, CharSequence title, String hint,
			String helper, @Nullable String presetText, final int imeOptions,
			int inputType, @NonNull final OnTextBoxDialogClick onClickListener) {

		AlertDialog.Builder builder = adb.builder;

		final AlertDialog[] dialog = {
			null
		};

		PackageManager pm = requirePackageManager(context);
		List<ResolveInfo> activities = pm.queryIntentActivities(
				new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
		boolean hasSpeechRecognization = activities.size() > 0;

		TextInputLayout textInputLayout = adb.view.findViewById(
				R.id.textInputLayout);
		TextInputEditText textView = adb.view.findViewById(R.id.textInputEditText);
		if (textInputLayout == null || textView == null) {
			throw new IllegalStateException(
					"No textInputLayout or no textInputEditText in dialog_text_input");
		}

		textInputLayout.setHint(hint != null ? hint : title);

		if (helper != null) {
			textInputLayout.setHelperText(helper);
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
				Button button = dialog[0].getButton(DialogInterface.BUTTON_POSITIVE);
				if (button != null) {
					button.performClick();
				}
				return true;
			}
			return false;
		});

		if (presetText != null) {
			textView.setText(presetText);
			textView.setSelection(presetText.length());
		}

		ImageView imageButton = adb.view.findViewById(R.id.textInputSpeaker);
		if (imageButton == null) {
			throw new IllegalStateException("No textInputSpeaker");
		}
		imageButton.setVisibility(
				hasSpeechRecognization ? View.GONE : View.VISIBLE);
		if (hasSpeechRecognization) {
			imageButton.setOnClickListener(v -> {
				Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
				intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
						RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
				Resources resources = v.getResources();
				if (resources != null) {
					intent.putExtra(RecognizerIntent.EXTRA_PROMPT, title);
				}
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
		}

		builder.setTitle(title);
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
	@UiThread
	public static Fragment getFocusedFragment(
			@NonNull FragmentActivity activity) {
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

	@AnyThread
	public static boolean isUIThread() {
		return Looper.getMainLooper().getThread() == Thread.currentThread();
	}

	@UiThread
	public static boolean sendOnKeyToFragments(@NonNull FragmentActivity activity,
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

	@UiThread
	public static Drawable getTintedDrawable(@NonNull Context context,
			@NonNull Drawable inputDrawable, @ColorInt int color) {
		Drawable wrapDrawable = DrawableCompat.wrap(inputDrawable);
		DrawableCompat.setTint(wrapDrawable, color);
		DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_IN);
		return wrapDrawable;
	}

	@UiThread
	public static void tintAllIcons(@NonNull Menu menu, final int color) {
		for (int i = 0; i < menu.size(); ++i) {
			MenuItem item = menu.getItem(i);
			if (item == null) {
				continue;
			}
			tintMenuItemIcon(color, item);
		}
	}

	@UiThread
	private static void tintMenuItemIcon(int color, @NonNull MenuItem item) {
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
	@UiThread
	public static boolean popupContextMenu(@NonNull Context context,
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
	@UiThread
	public static boolean popupSubMenu(@NonNull SubMenuBuilder subMenu,
			final Callback actionModeCallback, @Nullable CharSequence title) {
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
			public boolean onMenuItemSelected(@NonNull MenuBuilder menu,
					@NonNull MenuItem item) {
				return actionModeCallback.onActionItemClicked(null, item);
			}

			@Override
			public void onMenuModeChange(@NonNull MenuBuilder menu) {
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

	public interface LinkClickListener
	{
		/**
		 * @return true if handled
		 */
		@UiThread
		boolean linkClicked(String link);
	}

	public static void linkifyIfHREF(@NonNull FragmentActivity activity,
			@NonNull TextView tv) {
		String msg = tv.getText().toString();
		if (msg.contains("<A HREF=") || msg.contains("<a href=")) {
			linkify(activity, tv, null, msg);
		}
	}

	@UiThread
	public static void linkify(@NonNull FragmentActivity activity,
			@Nullable TextView tv, @Nullable final LinkClickListener l,
			@StringRes int id, @NonNull Object... formatArgs) {
		if (tv == null) {
			return;
		}
		Spanned spanned = AndroidUtils.fromHTML(
				AndroidUtils.requireResources(activity), id, formatArgs);
		linkify(activity, tv, l, spanned);
	}

	@UiThread
	public static void linkify(@NonNull FragmentActivity context, TextView tv,
			@Nullable final LinkClickListener l, @NonNull String msg) {

		Spanned spanned = AndroidUtils.fromHTML(msg);
		linkify(context, tv, l, spanned);
	}

	@UiThread
	public static void linkify(@NonNull FragmentActivity activity, TextView tv,
			@Nullable final LinkClickListener l, @NonNull Spanned spanned) {
		if (tv == null) {
			return;
		}

		URLSpan[] urls = spanned.getSpans(0, spanned.length(), URLSpan.class);
		SpannableStringBuilder strBuilder = new SpannableStringBuilder(spanned);
		if (urls != null) {
			for (final URLSpan span : urls) {
				String url = span.getURL();
				if (url == null) {
					continue;
				}
				int start = strBuilder.getSpanStart(span);
				int end = strBuilder.getSpanEnd(span);
				final String title = spanned.subSequence(start, end).toString();

				Object newSpan;
				if (l != null) {
					newSpan = new ClickableSpan() {
						@Override
						public void onClick(@NonNull View view) {
							if (l.linkClicked(url)) {
								return;
							}
							if (url.contains("://")) {
								openURL(activity, url, title);
							}
						}
					};
				} else {
					newSpan = new UrlSpan2(activity, url, title);
				}

				replaceSpan(strBuilder, span, newSpan);
			}
		}
		tv.setText(strBuilder);
		tv.setMovementMethod(LinkMovementMethod.getInstance());
	}

	/**
	 * Replaces one span with the other. 
	 */
	private static void replaceSpan(@NonNull SpannableStringBuilder builder,
			Object originalSpan, Object newSpan) {
		builder.setSpan(newSpan, builder.getSpanStart(originalSpan),
				builder.getSpanEnd(originalSpan), builder.getSpanFlags(originalSpan));
		builder.removeSpan(originalSpan);
	}

	@SuppressLint("ParcelCreator")
	public static final class UrlSpan2
		extends URLSpan
	{
		@NonNull
		private final FragmentActivity context;

		private final String title;

		public UrlSpan2(@NonNull FragmentActivity activity, @NonNull String url,
				String title) {
			super(url);
			this.context = activity;
			this.title = title;
		}

		@SuppressWarnings("MethodDoesntCallSuperMethod")
		@Override
		@UiThread
		public void onClick(View widget) {
			//noinspection ConstantConditions
			openURL(context, getURL(), title);
		}

	}

	@UiThread
	public static void openURL(@NonNull FragmentActivity activity,
			@NonNull String url, String title) {
		boolean useNoBrowserDialog = AndroidUtils.isLiterallyLeanback(activity)
				&& url.startsWith("http");
		if (!useNoBrowserDialog) {
			Uri uri = Uri.parse(url);
			Intent intent = new Intent(Intent.ACTION_VIEW, uri);
			intent.putExtra(Browser.EXTRA_APPLICATION_ID, activity.getPackageName());
			PackageManager pm = requirePackageManager(activity);
			ResolveInfo info = pm.resolveActivity(intent, 0);

			useNoBrowserDialog = info == null;
			if (info != null) {
				ComponentInfo componentInfo = AndroidUtils.getComponentInfo(info);
				useNoBrowserDialog = componentInfo == null || componentInfo.name == null
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

		FragmentManager fm = activity.getSupportFragmentManager();
		DialogFragmentNoBrowser.open(fm, url, title);
	}

	@UiThread
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

	public static int getScreenWidthPx(@NonNull Context context) {
		Resources resources = requireResources(context);
		DisplayMetrics dm = resources.getDisplayMetrics();
		int orientation = resources.getConfiguration().orientation;

		return (orientation == Configuration.ORIENTATION_LANDSCAPE
				? Math.max(dm.widthPixels, dm.heightPixels)
				: Math.min(dm.widthPixels, dm.heightPixels));
	}

	public static int getScreenHeightPx(@NonNull Context context) {
		Resources resources = requireResources(context);
		DisplayMetrics dm = resources.getDisplayMetrics();
		int orientation = resources.getConfiguration().orientation;

		return (orientation == Configuration.ORIENTATION_PORTRAIT
				? Math.max(dm.widthPixels, dm.heightPixels)
				: Math.min(dm.widthPixels, dm.heightPixels));
	}

	public static int getScreenWidthDp(@NonNull Context context) {
		Resources resources = requireResources(context);
		DisplayMetrics dm = resources.getDisplayMetrics();
		int orientation = resources.getConfiguration().orientation;

		return pxToDp(orientation == Configuration.ORIENTATION_LANDSCAPE
				? Math.max(dm.widthPixels, dm.heightPixels)
				: Math.min(dm.widthPixels, dm.heightPixels));
	}

	public static int getScreenHeightDp(@NonNull Context context) {
		Resources resources = requireResources(context);
		DisplayMetrics dm = resources.getDisplayMetrics();
		int orientation = resources.getConfiguration().orientation;

		return pxToDp(orientation == Configuration.ORIENTATION_PORTRAIT
				? Math.max(dm.widthPixels, dm.heightPixels)
				: Math.min(dm.widthPixels, dm.heightPixels));
	}

	@Nullable
	public static Drawable getDrawableWithBounds(@NonNull Context context,
			@DrawableRes int resID) {
		try {
			Drawable drawableCompat = ContextCompat.getDrawable(context, resID);
			if (drawableCompat != null) {
				if (drawableCompat.getBounds().isEmpty()) {
					drawableCompat.setBounds(0, 0, drawableCompat.getIntrinsicWidth(),
							drawableCompat.getIntrinsicHeight());
				}
			}
			return drawableCompat;
		} catch (Throwable t) { // Crash: NotFoundException
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "getDrawableWithBounds: " + AndroidUtils.getResourceName(
						AndroidUtils.requireResources(context), resID), t);
			}
		}
		return null;
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
	@UiThread
	public static boolean isViewContains(@NonNull View view, int rx, int ry) {
		Rect rect = new Rect();
		view.getGlobalVisibleRect(rect);

		return rect.contains(rx, ry);
	}

	@UiThread
	@NonNull
	public static ViewGroup requireContentView(@NonNull Activity activity) {
		ViewGroup contentView = activity.findViewById(android.R.id.content);
		if (contentView == null) {
			throw new IllegalStateException();
		}
		ViewGroup child = (ViewGroup) contentView.getChildAt(0);
		if (child == null) {
			throw new IllegalStateException();
		}
		return child;
	}

	@Nullable
	@UiThread
	public static ViewGroup getContentView(@NonNull Activity activity) {
		ViewGroup contentView = activity.findViewById(android.R.id.content);
		if (contentView == null) {
			return null;
		}
		ViewGroup child = (ViewGroup) contentView.getChildAt(0);
		if (child == null) {
			return null;
		}
		return child;
	}

	@UiThread
	public static void walkTree(View rootView, String indent) {

		if (rootView instanceof ViewGroup) {
			Resources resources = rootView.getResources();
			if (resources == null) {
				return;
			}
			ViewGroup f = (ViewGroup) rootView;

			int childCount = f.getChildCount();
			if (childCount > 0) {
				for (int i = 0; i < childCount; i++) {
					View childAt = f.getChildAt(i);
					if (childAt == null) {
						continue;
					}
					int id = childAt.getId();
					String resourceName = "";
					try {
						if (id != -1) {
							resourceName = resources.getResourceName(id);
						}
					} catch (Throwable ignore) {

					}
					Log.d(TAG, indent + "walkTree: child " + i + ": " + childAt + ";"
							+ Integer.toHexString(id) + ";" + resourceName);
					walkTree(childAt, indent + "\t");

				}
			} else {
				Log.d(TAG, indent + "walkTree: rootView=" + rootView);
			}
		}
	}

	public interface WalkTreeListener
	{
		void foundView(View view);
	}

	@UiThread
	public static void walkTree(@Nullable View rootView, WalkTreeListener l) {

		if (rootView instanceof ViewGroup) {
			Resources resources = rootView.getResources();
			if (resources == null) {
				return;
			}
			ViewGroup f = (ViewGroup) rootView;

			int childCount = f.getChildCount();
			if (childCount > 0) {
				for (int i = 0; i < childCount; i++) {
					View childAt = f.getChildAt(i);
					if (childAt == null) {
						continue;
					}
					l.foundView(childAt);
					walkTree(childAt, l);

				}
			} else {
				l.foundView(rootView);
			}
		}
	}

	@NonNull
	@UiThread
	public static AlertDialogBuilder createAlertDialogBuilder(
			@NonNull Context context, @LayoutRes int resource) {
		return createAlertDialogBuilder(context, resource,
				R.style.MyMaterialAlertDialogTheme);
	}

	@NonNull
	@Contract("_, _, _ -> new")
	@UiThread
	public static AlertDialogBuilder createAlertDialogBuilder(
			@NonNull Context context, @LayoutRes int resource, @StyleRes int theme) {
		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context,
				theme);

		View view = View.inflate(context, resource, null);
		if (view == null) {
			throw new IllegalStateException("Layour inflate returned null");
		}
		builder.setView(view);

		return new AlertDialogBuilder(view, builder);
	}

	@NonNull
	@Contract("_, _ -> new")
	@UiThread
	public static AlertDialogBuilder createAlertDialogBuilder(
			@NonNull Context context, @NonNull View view) {
		AlertDialog.Builder builder = new MaterialAlertDialogBuilder(context);

		builder.setView(view);

		return new AlertDialogBuilder(view, builder);
	}

	@AnyThread
	public static void showConnectionError(FragmentActivity activity,
			@NonNull String profileID, @NonNull Throwable t, boolean allowContinue) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG,
					"showConnectionError " + AndroidUtils.getCompressedStackTrace(t, 9));
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
			if (newMessage == null) {
				t = t.getCause();
				continue;
			}
			if (message.contains(newMessage)) {
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

	@AnyThread
	public static void showConnectionError(FragmentActivity activity,
			@NonNull String profileID, int errMsgID, boolean allowContinue) {
		if (activity == null || activity.isFinishing()) {
			if (AndroidUtils.DEBUG) {
				Log.w(TAG, "showConnectionError: no activity, can't show " + errMsgID
						+ ";" + AndroidUtils.getCompressedStackTrace());
			}
			if (!allowContinue) {
				SessionManager.removeSession(profileID, true);
			}
			return;
		}
		String errMsg = activity.getResources().getString(errMsgID);
		showConnectionError(activity, errMsg, allowContinue);
	}

	@AnyThread
	public static void showConnectionError(final FragmentActivity activity,
			final CharSequence errMsg, final boolean allowContinue) {

		if (activity == null || activity.isFinishing()) {
			Log.w(TAG, "can't display '" + errMsg + "'");
			return;
		}
		OffThread.runOnUIThread(() -> DialogFragmentConnError.openDialog(
				activity.getSupportFragmentManager(), "", errMsg, allowContinue));
	}

	@AnyThread
	public static void showDialog(final FragmentActivity activity,
			final @StringRes int title, final @StringRes int msg,
			@NonNull Object... formatArgs) {
		OffThread.runOnUIThread(activity, false, validActivity -> {
			AlertDialog.Builder builder = new MaterialAlertDialogBuilder(
					validActivity).setMessage(msg).setCancelable(true).setNegativeButton(
							android.R.string.ok, (dialog, which) -> {
							});
			if (title != 0) {
				builder.setTitle(title);
			}
			AlertDialog alertDialog = builder.show();
			if (alertDialog == null) {
				return;
			}
			View vMessage = alertDialog.findViewById(android.R.id.message);
			if (vMessage instanceof TextView) {
				linkify(validActivity, (TextView) vMessage, null, msg, formatArgs);
			}
		});

	}

	public static void showFeatureRequiresBiglyBT(final Activity activity,
			final String feature) {
		OffThread.runOnUIThread(activity, false, validActivity -> {
			String msg = requireResources(activity).getString(
					R.string.biglybt_required, feature);
			AlertDialog.Builder builder = new MaterialAlertDialogBuilder(
					validActivity).setMessage(msg).setCancelable(true).setPositiveButton(
							android.R.string.ok, (dialog, which) -> {
							});
			builder.show();
		});

	}

	@UiThread
	public static boolean showDialog(@NonNull DialogFragment dlg,
			FragmentManager fm, String tag) {
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

	@UiThread
	public static boolean showDialog(@NonNull android.app.DialogFragment dlg,
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

	public static void openMarket(@NonNull Context context,
			String appPackageName) {
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

	@UiThread
	public static @NonNull List<Fragment> getSafeChildFragments(
			@NonNull Fragment fragment) {
		try {
			FragmentManager fm = fragment.getChildFragmentManager();
			return fm.getFragments();
		} catch (IllegalStateException e) {
			return Collections.emptyList();
		}
	}

	@UiThread
	public static @NonNull List<Fragment> getSafeParentFragments(
			@NonNull Fragment fragment) {
		try {
			FragmentManager fm = fragment.getParentFragmentManager();
			return fm.getFragments();
		} catch (IllegalStateException e) {
			return Collections.emptyList();
		}
	}

	@Nullable
	@Contract(pure = true)
	@UiThread
	public static FragmentManager getSafeParentFragmentManager(
			@NonNull Fragment fragment) {
		try {
			return fragment.getParentFragmentManager();
		} catch (IllegalStateException e) {
			return null;
		}
	}

	public static Fragment findFragmentByClass(FragmentActivity activity,
			Class cla) {
		List<Fragment> fragments = activity.getSupportFragmentManager().getFragments();
		for (Fragment frag : fragments) {
			if (frag != null && cla.isAssignableFrom(frag.getClass())) {
				return frag;
			}
		}
		return null;
	}

	@AnyThread
	public static boolean runIfNotUIThread(
			@UiThread @NonNull RunnableUIThread uiThreadRunnable) {
		if (!AndroidUtilsUI.isUIThread()) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "delaying call to " + uiThreadRunnable);
			}
			new Handler(Looper.getMainLooper()).post(uiThreadRunnable);
			return true;
		}
		return false;

	}

	@AnyThread
	public static boolean runIfNotUIThread(
			@UiThread @NonNull RunnableUIThread uiThreadRunnable, int delayMS) {
		if (!AndroidUtilsUI.isUIThread()) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "delaying call to " + uiThreadRunnable);
			}
			new Handler(Looper.getMainLooper()).postDelayed(uiThreadRunnable,
					delayMS);
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
	@AnyThread
	public static boolean postDelayed(@UiThread @NonNull RunnableUIThread r) {
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		return new Handler(Looper.getMainLooper()).post(() -> {
			try {
				r.run();
			} catch (Throwable t) {
				AnalyticsTracker.getInstance().logError(t, stackTrace);
			}
		});
	}

	/**
	 * Return the screen's dimension that is typically available for our content.  ie. Screen dimensions
	 * minus status bar.
	 * 
	 * @apiNote We can return android.util.Size once minSDK >= 21
	 */
	@NonNull
	@Contract("_ -> new")
	public static Point getContentAreaSize(@NonNull FragmentActivity activity) {
		int maxH = 0;
		int maxW = 0;

		Window window = activity.getWindow();
		if (window != null) {
			View activityWindowContent = window.findViewById(
					Window.ID_ANDROID_CONTENT);
			if (activityWindowContent != null) {
				maxH = activityWindowContent.getHeight();
				maxW = activityWindowContent.getWidth();
			}
		}

		// Can't fully rely on content area dimensions. Some devices/versions it
		// includes the status bar area.

		int screenWidth = AndroidUtilsUI.getScreenWidthPx(activity);
		int screenHeight = AndroidUtilsUI.getScreenHeightPx(activity);
		try {
			Resources resources = activity.getResources();

			@SuppressLint("DiscouragedApi")
			int statusBarHeight = resources.getDimensionPixelSize(
					resources.getIdentifier("status_bar_height", "dimen", "android"));
			screenHeight -= statusBarHeight;
			// Note: on screen system navigation bar appears to always be excluded
			// from the dimensions.
		} catch (Throwable ignore) {
		}
		maxW = maxW == 0 ? screenWidth : Math.min(maxW, screenWidth);
		maxH = maxH == 0 ? screenHeight : Math.min(maxH, screenHeight);
		return new Point(maxW, maxH);
	}

	public static int getResourceValuePX(@NonNull Resources resources,
			@AnyRes int id) {
		final TypedValue value = new TypedValue();
		resources.getValue(id, value, true);
		if (value.type >= TypedValue.TYPE_FIRST_INT
				&& value.type <= TypedValue.TYPE_LAST_INT) {
			return value.data;
		}
		if (value.type == TypedValue.TYPE_DIMENSION) {
			return TypedValue.complexToDimensionPixelSize(value.data,
					resources.getDisplayMetrics());
		}
		if (value.type == TypedValue.TYPE_FLOAT) {
			return (int) (getScreenWidthPx(BiglyBTApp.getContext())
					* value.getFloat());
		}

		throw new Resources.NotFoundException(
				"Resource ID #0x" + Integer.toHexString(id) + " type #0x"
						+ Integer.toHexString(value.type) + " is not valid");
	}

	/**
	 * Give each number picker in a viewgroup (ie. 3 number pickers in a datepicker) a background
	 * that shows focus on Android TV.
	 * <p/>
	 * Needs to be reassessed with every appcompat/material library upgrade in case one day
	 * it's ok on AndroidTV.
	 */
	public static void changePickersBackground(@NonNull ViewGroup vg) {
		TypedValue typedValue = new TypedValue();
		vg.getContext().getTheme().resolveAttribute(R.attr.singlelist_selector_attr,
				typedValue, true);

		if (typedValue.resourceId == 0) {
			return;
		}

		ArrayList<View> list = findByClass(vg, NumberPicker.class,
				new ArrayList<>());
		for (View v : list) {
			v.setBackgroundResource(typedValue.resourceId);
		}
	}

	@NonNull
	@UiThread
	public static View requireInflate(@NonNull LayoutInflater inflater,
			@LayoutRes int resource, @Nullable ViewGroup root, boolean attachToRoot) {
		View inflate = inflater.inflate(resource, root, attachToRoot);
		if (inflate == null) {
			throw new IllegalStateException("inflate failed");
		}
		return inflate;
	}

	/**
	 * When TabLayout is in "auto" mode and "center" gravity:<br/>
	 * <li>Each tab will have it's own width</li>
	 * <li>If all tabs don't fit, view will scroll</li>
	 * <li>(FIX) When all tabs can be set to the same width, and still fit in
	 * the view, tabs will be expanded to fill full view (instead of center)</li>
	 * <br/>
	 * TabLayout needs to be:<br/>
	 * <tt>
	 * 	app:tabMode="auto"<br/>
	 * 	app:tabGravity="center"<br/>
	 * 	app:tabMaxWidth="0dp"<br/>
	 * </tt>
	 */
	public static void fixupTabLayout(@NonNull TabLayout tabLayout) {
		if (tabLayout.getTabMode() != TabLayout.MODE_AUTO) {
			return;
		}
		tabLayout.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(View v, int left, int top, int right,
					int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
				int tabCount = tabLayout.getTabCount();
				int width = 0;
				int largestTabWidth = 0;
				int[] tabWidths = new int[tabCount];
				for (int i = 0; i < tabCount; i++) {
					TabLayout.Tab tabAt = tabLayout.getTabAt(i);
					if (tabAt == null) {
						continue;
					}
					tabWidths[i] = tabAt.view.getMeasuredWidth();
					width += tabWidths[i];
					if (tabWidths[i] > largestTabWidth) {
						largestTabWidth = tabWidths[i];
					}
				}
				if (largestTabWidth == 0) {
					return;
				}
				int maxWidth = largestTabWidth * tabCount;
				int layoutWidth = tabLayout.getWidth();
				if (AndroidUtils.DEBUG_LIFECYCLE) {
					Log.d("TDF",
							"l.w=" + layoutWidth + "; w=" + width + "; mW=" + maxWidth);
				}

				// Switch only if all tabs are same width and total width is less
				// than tablayout's width
				if (width == maxWidth && maxWidth < layoutWidth) {
					if (AndroidUtils.DEBUG_LIFECYCLE) {
						Log.d("TDF", "Switch to fixed");
					}
					tabLayout.setTabMode(TabLayout.MODE_FIXED);
					tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);
					tabLayout.removeOnLayoutChangeListener(this);
				} else if (width < maxWidth && width < layoutWidth && tabCount > 2) {
					// Can't set each tab's minimum width, tablayout overwrites it,
					// so use smallest width as TabMinWidth
					int remaining = layoutWidth - width;
					Arrays.sort(tabWidths);
					while (remaining > 0) {
						if (tabWidths[0] == tabWidths[tabCount - 1]) {
							tabWidths[0] += remaining / tabCount;
							break;
						}

						int smallest = tabWidths[0];
						int numToAdjust = 1;
						while (tabWidths[0] == tabWidths[numToAdjust]) {
							numToAdjust++;
							if (numToAdjust == tabCount) {
								break;
							}
						}
						if (numToAdjust < tabCount) {
							int lastSmallest = tabWidths[numToAdjust];
							int adj = Math.min(remaining, lastSmallest - smallest)
									/ numToAdjust;
							if (adj <= 0) {
								break;
							}
							for (int i = 0; i < numToAdjust; i++) {
								tabWidths[i] += adj;
								remaining -= adj;
							}
						}
						Arrays.sort(tabWidths);
					}
					int smallest = tabWidths[0];

					try {
						if (AndroidUtils.DEBUG_LIFECYCLE) {
							Log.d("TDF", "spread'm " + smallest + ", lazy="
									+ (layoutWidth / tabCount));
						}
						Field requestedTabMinWidth = TabLayout.class.getDeclaredField(
								"requestedTabMinWidth");
						requestedTabMinWidth.setAccessible(true);
						requestedTabMinWidth.set(tabLayout, smallest);
						Method updateTabViews = TabLayout.class.getDeclaredMethod(
								"updateTabViews", boolean.class);
						updateTabViews.setAccessible(true);
						updateTabViews.invoke(tabLayout, true);
					} catch (Throwable e) {
						if (AndroidUtils.DEBUG) {
							e.printStackTrace();
						}
					}
					tabLayout.removeOnLayoutChangeListener(this);
				}
			}
		});
	}

	/**
	 * @return The resource ID value in the {@code context} specified by {@code attr}. If it does
	 * not exist, {@code fallbackAttr}.
	 */
	public static int getAttr(@NonNull Context context, int attr,
			int fallbackAttr) {
		TypedValue value = new TypedValue();
		context.getTheme().resolveAttribute(attr, value, true);
		if (value.resourceId != 0) {
			return attr;
		}
		return fallbackAttr;
	}

	@Nullable
	public static Activity findActivity(Context context) {
		while (context instanceof ContextWrapper) {
			if (context instanceof Activity) {
				return (Activity) context;
			}
			context = ((ContextWrapper) context).getBaseContext();
		}

		return null;
	}

	public static void setProgress(@NonNull ProgressBar pb, int progress,
			boolean animate) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !animate) {
			pb.setProgress(progress);
			return;
		}
		pb.setProgress(progress, animate);
	}

	public static void hideProgressBar(BaseProgressIndicator pb) {
		// Bug on older (ICS, JB) OS: hide() stops the animation, but leaves the
		// widget visible (probably only when showAnimation is running)
		pb.setVisibility(View.GONE);
		pb.hide();
	}

	public static void setText(@NonNull Resources resources, @NonNull TextView tv,
			@NonNull PathInfo pathInfo) {

		CharSequence friendlyName = pathInfo.getFriendlyName();
		String accessType = resources.getString(pathInfo.isSAF
				? R.string.fileaccess_saf_short : R.string.fileaccess_direct_short);
		friendlyName = "|" + accessType + "| " + friendlyName;

		SpannableStringBuilder ssPath = new SpannableStringBuilder(friendlyName);
		TextPaint paintPath = new TextPaint(tv.getPaint());
		paintPath.setTextSize(paintPath.getTextSize() * 0.7f);
		int pathTextColor = tv.getCurrentTextColor();
		SpanBubbles.setSpanBubbles(ssPath, friendlyName.toString(), "|", paintPath,
				pathTextColor, pathTextColor, 0, null);

		tv.setText(ssPath);
	}
}
