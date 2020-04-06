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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.SearchManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.text.*;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.biglybt.android.client.activity.MetaSearchActivity;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.util.Thunk;

import org.jetbrains.annotations.Contract;

import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Pattern;

import javax.net.ssl.*;

/**
 * Some generic Android Utility methods.
 * <p/>
 * Some utility methods specific to this app and requiring Android API.
 */
@SuppressWarnings({
	"SameParameterValue",
	"WeakerAccess"
})
public class AndroidUtils
{
	@SuppressWarnings("ConstantConditions")
	public static final boolean DEBUG = BuildConfig.DEBUG;

	public static final boolean DEBUG_ANNOY = false;

	@SuppressWarnings({
		"PointlessBooleanExpression",
		"ConstantConditions",
		"RedundantSuppression"
	})
	public static final boolean DEBUG_RPC = DEBUG; // && false;

	@SuppressWarnings({
		"PointlessBooleanExpression",
		"ConstantConditions",
		"RedundantSuppression"
	})
	public static final boolean DEBUG_MENU = DEBUG; // && false;

	@SuppressWarnings({
		"PointlessBooleanExpression",
		"ConstantConditions",
		"RedundantSuppression"
	})
	public static final boolean DEBUG_ADAPTER = DEBUG; // && false;

	@SuppressWarnings({
		"PointlessBooleanExpression",
		"ConstantConditions",
		"RedundantSuppression"
	})
	public static final boolean DEBUG_LIFECYCLE = DEBUG; // && false;

	private static final String TAG = "Utils";

	private static final Object[] XMLescapes = new Object[] {
		new String[] {
			"&",
			"&amp;"
		},
		new String[] {
			">",
			"&gt;"
		},
		new String[] {
			"<",
			"&lt;"
		},
		new String[] {
			"\"",
			"&quot;"
		},
		new String[] {
			"'",
			"&apos;"
		},
	};

	// 	 . _ - \ /
	private static final Pattern patLineBreakerAround = Pattern.compile(
			"([._\\-\\\\/]+)([^\\s])");

	//   ; ]
	private static final Pattern patLineBreakerAfter = Pattern.compile(
			"([;\\]])([^\\s])");

	public static final String BIGLYBT_USERAGENT = "BiglyBT for Android";

	public static final String HTTPS = "https";

	public static final String HTTP = "http";

	private static final String REQPROPKEY_USER_AGENT = "User-Agent";

	public static final String UTF_8 = "utf-8";

	private static Boolean isTV = null;

	private static Boolean hasTouchScreen = null;

	private static Boolean isChromium = null;

	// ACTION_POWER_CONNECTED
	public static boolean isPowerConnected(@NonNull Context context) {
		Intent intent = context.registerReceiver(null,
				new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		if (intent == null) {
			return true;
		}
		int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
		return plugged == BatteryManager.BATTERY_PLUGGED_AC
				|| plugged == BatteryManager.BATTERY_PLUGGED_USB
				|| plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
	}

	/**
	 * Remove all extras from intent
	 */
	@SuppressWarnings("unused")
	public static void clearExtras(@NonNull Intent intent) {
		Bundle extras = intent.getExtras();
		if (extras == null) {
			return;
		}
		Set<String> strings = extras.keySet();
		if (strings == null) {
			return;
		}
		for (String key : strings) {
			intent.removeExtra(key);
		}
	}

	/**
	 * Android doesn't fade out disabled menu item icons, so do it ourselves
	 */
	public static void fixupMenuAlpha(Menu menu) {
		if (menu == null) {
			return;
		}
		for (int i = 0; i < menu.size(); i++) {
			MenuItem item = menu.getItem(i);
			if (item == null) {
				continue;
			}
			Drawable icon = item.getIcon();
			if (icon != null) {
				int newAlpha = item.isEnabled() ? 255 : 64;
				int oldAlpha = DrawableCompat.getAlpha(icon);
				if (oldAlpha != newAlpha) {
					icon.mutate().setAlpha(newAlpha);
					item.setIcon(icon);
				}
			}
		}
	}

	public static class ValueStringArray
	{
		public final int size;

		public final long[] values;

		public final String[] strings;

		public ValueStringArray(@NonNull long[] value, @NonNull String[] string) {
			this.values = value;
			this.strings = string;
			this.size = Math.min(values.length, string.length);
		}

	}

	public static ValueStringArray getValueStringArray(
			@NonNull Resources resources, int id) {
		String[] stringArray = resources.getStringArray(id);
		String[] strings = new String[stringArray.length];
		long[] values = new long[stringArray.length];

		for (int i = 0; i < stringArray.length; i++) {
			String[] s = stringArray[i].split(",");
			values[i] = Integer.parseInt(s[0]);
			strings[i] = s[1];
		}
		return new ValueStringArray(values, strings);
	}

	public static boolean executeSearch(String search, @NonNull Context context,
			Session session) {
		if (session == null) {
			return false;
		}
		Intent myIntent = new Intent(Intent.ACTION_SEARCH);
		myIntent.setClass(context, MetaSearchActivity.class);

		RemoteProfile remoteProfile = session.getRemoteProfile();
		myIntent.putExtra(SessionManager.BUNDLE_KEY, remoteProfile.getID());

		if (remoteProfile.getRemoteType() == RemoteProfile.TYPE_LOOKUP) {
			Bundle bundle = new Bundle();
			bundle.putString(SessionManager.BUNDLE_KEY, remoteProfile.getID());

			myIntent.putExtra(SearchManager.APP_DATA, bundle);
		}

		myIntent.putExtra(SearchManager.QUERY, search);

		context.startActivity(myIntent);
		return true;
	}

	public static boolean isURLAlive(@NonNull String URLName) {
		if (isURLAlive(URLName, 1000, 1000)) {
			return true;
		}
		if (isURLAlive(URLName, 10000, 5000)) {
			return true;
		}
		return false;
	}

	private static boolean isURLAlive(@NonNull String URLName, int conTimeout,
			int readTimeout) {
		try {
			HttpURLConnection.setFollowRedirects(false);

			URL url = new URL(URLName);
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			if (con == null) {
				return false;
			}
			if (con instanceof HttpsURLConnection) {
				HttpsURLConnection conHttps = (HttpsURLConnection) con;

				SSLContext ctx = SSLContext.getInstance("TLS");
				if (ctx != null) {
					ctx.init(new KeyManager[0], new TrustManager[] {
						new DefaultTrustManager()
					}, new SecureRandom());
					conHttps.setSSLSocketFactory(ctx.getSocketFactory());
				}

				conHttps.setHostnameVerifier((hostname, session) -> true);
			}

			con.setConnectTimeout(conTimeout);
			con.setReadTimeout(readTimeout);
			con.setRequestMethod("HEAD");
			con.getResponseCode();
			if (DEBUG) {
				Log.d(TAG, "isLive? conn result=" + con.getResponseCode() + ";"
						+ con.getResponseMessage());
			}
			con.disconnect();
			return true;
		} catch (Exception e) {
			if (DEBUG) {
				Log.e(TAG, "isLive " + URLName, e);
			}
			return false;
		}
	}

	/**
	 * Integer.parseInt that returns 0 instead of throwing
	 */
	@Thunk
	public static int parseInt(@NonNull String s) {
		try {
			return Integer.parseInt(s);
		} catch (Exception ignore) {
		}
		return 0;
	}

	/**
	 * Integer.parseLong that returns 0 instead of throwing
	 */
	@Thunk
	public static long parseLong(@NonNull String s) {
		try {
			return Long.parseLong(s);
		} catch (Exception ignore) {
		}
		return 0;
	}

	public static boolean readInputStreamIfStartWith(@NonNull InputStream is,
			@NonNull ByteArrayOutputStream bab, @NonNull byte[] startsWith)
			throws IOException {

		byte[] buffer = new byte[32 * 1024];

		boolean first = true;

		try {
			while (true) {

				int len = is.read(buffer);

				if (len <= 0) {

					break;
				}

				bab.write(buffer, 0, len);

				if (first) {
					first = false;
					for (int i = 0; i < startsWith.length; i++) {
						if (startsWith[i] != buffer[i]) {
							return false;
						}
					}
				}
			}

			return bab.size() != 0;

		} finally {

			is.close();
		}
	}

	@NonNull
	public static byte[] readInputStreamAsByteArray(@NonNull InputStream is,
			int sizeLimit)
			throws IOException {
		int available = is.available();
		if (available <= 0) {
			available = 32 * 1024;
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream(available);

		byte[] buffer = new byte[32 * 1024];

		try {
			while (true) {
				int readLen = Math.min(sizeLimit, buffer.length);

				int len = is.read(buffer, 0, readLen);

				if (len <= 0) {

					break;
				}

				baos.write(buffer, 0, len);

				sizeLimit -= len;
				if (sizeLimit <= 0) {
					break;
				}
			}

			return (baos.toByteArray());

		} finally {

			is.close();
		}
	}

	// From FileUtil.java
	private static void copyFile(@NonNull InputStream _source,
			@NonNull File _dest, boolean _close_input_stream)

			throws IOException {
		FileOutputStream dest = null;

		try {
			// FileNotFoundException
			dest = new FileOutputStream(_dest);

			copyFile(_source, dest, _close_input_stream);

		} finally {

			try {
				if (_close_input_stream) {

					_source.close();
				}
			} catch (IOException ignore) {
			}

			if (dest != null) {

				dest.close();
			}
		}
	}

	// From FileUtil.java
	private static void copyFile(@NonNull InputStream is,
			@NonNull OutputStream os, boolean closeInputStream)

			throws IOException {
		try {

			if (!(is instanceof BufferedInputStream)) {

				is = new BufferedInputStream(is, 8192);
			}

			byte[] buffer = new byte[65536 * 2];

			while (true) {

				int len = is.read(buffer);

				if (len == -1) {

					break;
				}

				os.write(buffer, 0, len);
			}
		} finally {
			try {
				if (closeInputStream) {
					is.close();
				}
			} catch (IOException ignore) {

			}

			os.close();
		}
	}

	public static boolean readURL(String uri, @NonNull ByteArrayOutputStream bab,
			@NonNull byte[] startsWith)
			throws IllegalArgumentException {

		try {
			URLConnection cn = new URL(uri).openConnection();
			if (cn == null) {
				return false;
			}
			cn.setRequestProperty(REQPROPKEY_USER_AGENT, BIGLYBT_USERAGENT);
			cn.connect();
			InputStream is = cn.getInputStream();
			if (is == null) {
				return false;
			}

			return readInputStreamIfStartWith(is, bab, startsWith);

		} catch (Exception e) {
			AnalyticsTracker.getInstance().logError(e);
		}

		return false;
	}

	public static File getDownloadDir() {
		return Environment.getExternalStoragePublicDirectory(
				Environment.DIRECTORY_DOWNLOADS);
	}

	/**
	 * Better line breaking for text view.  Puts invisible whitespace around<br>
	 * . _ - \ /
	 * <br>
	 * and after<br>
	 * ] ;
	 * <br>
	 */
	@NonNull
	public static String lineBreaker(@NonNull String s) {
		s = patLineBreakerAfter.matcher(s).replaceAll("$1\u200B$2");
		s = patLineBreakerAround.matcher(s).replaceAll("\u200B$1\u200B$2");
		return s;
	}

	@NonNull
	public static String getCompressedStackTrace() {
		try {
			throw new Exception();
		} catch (Exception e) {
			return getCompressedStackTrace(e, 1, 12);
		}
	}

	@NonNull
	public static String getCompressedStackTrace(int limit) {
		try {
			throw new Exception();
		} catch (Exception e) {
			return getCompressedStackTrace(e, 1, limit);
		}
	}

	@NonNull
	public static String getCompressedStackTrace(@NonNull Throwable t,
			int startAt, int limit) {
		try {
			StackTraceElement[] stackTrace = t.getStackTrace();
			if (stackTrace.length < startAt) {
				return "";
			}
			StringBuilder sb = new StringBuilder();
			for (int i = startAt; i < stackTrace.length && i < startAt + limit; i++) {
				StackTraceElement element = stackTrace[i];
				String classname = element.getClassName();
				String cnShort;
				boolean showLineNumber = true;
				boolean breakAfter = false;

				cnShort = element.getFileName();
				if (cnShort == null && classname != null) {

					if (classname.startsWith("com.biglybt.android.client.")) { //NON-NLS
						cnShort = classname.substring(24);
					} else if ("java.lang.Thread".equals(classname)) {
						showLineNumber = false;
						cnShort = "Thread"; //NON-NLS
					} else if ("android.os.Handler".equals(classname)) {
						showLineNumber = false;
						cnShort = "Handler"; //NON-NLS
					} else if ("android.os.Looper".equals(classname)) {
						showLineNumber = false;
						cnShort = "Looper"; //NON-NLS
						breakAfter = true;
					} else if (classname.length() < 9) { // include full if something like aa.ab.ac
						cnShort = classname;
					} else {
						int len = classname.length();
						int start = len > 14 ? len - 14 : 0;

						int pos = classname.indexOf('.', start);
						if (pos >= 0) {
							start = pos + 1;
						}
						cnShort = classname.substring(start, len);
					}
				} else {
//					int posDot = cnShort.indexOf('.');
//					if (posDot >= 0) {
//						cnShort = cnShort.substring(0, posDot);
//					}
				}
				if (i != startAt) {
					sb.append(", ");
				}
				sb.append(element.getMethodName());
				sb.append('@');
				sb.append(cnShort);
				if (showLineNumber) {
					sb.append(':');
					sb.append(element.getLineNumber());
				}
				if (breakAfter) {
					break;
				}
			}
			Throwable cause = t.getCause();
			if (cause != null) {
				sb.append("\n|Cause "); //NON-NLS
				sb.append(cause.getClass().getSimpleName());
				if (cause instanceof RuntimeException) {
					sb.append(' ');
					sb.append(cause.getMessage());
				}
				sb.append(' ');
				sb.append(getCompressedStackTrace(cause, 0, 9));
			}
			return sb.toString();
		} catch (Throwable derp) {
			return "derp " + derp.getClass().getSimpleName(); //NON-NLS
		}
	}

	@NonNull
	public static String getCauses(Throwable e) {
		try {
			StringBuilder sb = new StringBuilder();
			while (e != null) {
				if (sb.length() > 0) {
					sb.append(", ");
				}
				sb.append(e.getClass().getSimpleName());
				e = e.getCause();
			}

			return sb.toString();

		} catch (Throwable derp) {
			return "derp " + derp.getClass().getSimpleName(); //NON-NLS
		}
	}

	@NonNull
	public static String getCausesMesssages(Throwable e) {
		try {
			StringBuilder sb = new StringBuilder();
			while (e != null) {
				if (sb.length() > 0) {
					sb.append(", ");
				}
				sb.append(e.getClass().getSimpleName());
				sb.append(": ");
				sb.append(e.getMessage());
				e = e.getCause();
			}

			return sb.toString();

		} catch (Throwable derp) {
			return "derp " + derp.getClass().getSimpleName(); //NON-NLS
		}
	}

	@Nullable
	public static ComponentInfo getComponentInfo(@NonNull ResolveInfo info) {
		if (info.activityInfo != null)
			return info.activityInfo;
		if (info.serviceInfo != null)
			return info.serviceInfo;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			return getComponentInfo_v19(info);
		}
		return null;
	}

	@Nullable
	@TargetApi(Build.VERSION_CODES.KITKAT)
	private static ComponentInfo getComponentInfo_v19(@NonNull ResolveInfo info) {
		if (info.providerInfo != null)
			return info.providerInfo;
		return null;
	}

	@NonNull
	@SuppressWarnings("unused,nls")
	public static String getStatesString(@NonNull int[] ints) {
		String[] s = new String[ints.length];
		for (int i = 0; i < ints.length; i++) {
			int state = ints[i];
			switch (state) {
				case android.R.attr.state_above_anchor:
					s[i] = "state_above_anchor";
					break;

				case android.R.attr.state_accelerated:
					s[i] = "state_accelerated";
					break;

				case android.R.attr.state_activated:
					s[i] = "state_activated";
					break;

				case android.R.attr.state_active:
					s[i] = "state_active";
					break;

				case android.R.attr.state_checkable:
					s[i] = "state_checkable";
					break;

				case android.R.attr.state_checked:
					s[i] = "state_checked";
					break;

				case android.R.attr.state_drag_can_accept:
					s[i] = "state_drag_can_accept";
					break;

				case android.R.attr.state_drag_hovered:
					s[i] = "state_drag_hovered";
					break;

				case android.R.attr.state_empty:
					s[i] = "state_empty";
					break;

				case android.R.attr.state_enabled:
					s[i] = "state_enabled";
					break;

				case android.R.attr.state_expanded:
					s[i] = "state_expanded";
					break;

				case android.R.attr.state_focused:
					s[i] = "state_focused";
					break;

				case android.R.attr.state_hovered:
					s[i] = "state_hovered";
					break;

				case android.R.attr.state_last:
					s[i] = "state_last";
					break;

				case android.R.attr.state_long_pressable:
					s[i] = "state_long_pressable";
					break;

				case android.R.attr.state_middle:
					s[i] = "state_middle";
					break;

				case android.R.attr.state_multiline:
					s[i] = "state_multiline";
					break;

				case android.R.attr.state_pressed:
					s[i] = "state_pressed";
					break;

				case android.R.attr.state_selected:
					s[i] = "state_selected";
					break;

				case android.R.attr.state_single:
					s[i] = "state_single";
					break;

				case android.R.attr.state_window_focused:
					s[i] = "state_window_focused";
					break;
				default:
					s[i] = "" + state;
			}
		}
		return Arrays.toString(s);
	}

	@SuppressWarnings("unused")
	public static int indexOfAny(@NonNull String findIn,
			@NonNull String findAnyChar, int startPos) {
		for (int i = 0; i < findAnyChar.length(); i++) {
			char c = findAnyChar.charAt(i);
			int pos = findIn.indexOf(c, startPos);
			if (pos >= 0) {
				return pos;
			}
		}
		return -1;
	}

	public static int lastindexOfAny(@NonNull String findIn,
			@NonNull char[] findAnyChar, int startPos) {
		if (startPos > findIn.length()) {
			return -1;
		}
		for (char c : findAnyChar) {
			int pos = startPos >= 0 ? findIn.lastIndexOf(c, startPos)
					: findIn.lastIndexOf(c);
			if (pos >= 0) {
				return pos;
			}
		}
		return -1;
	}

	@SuppressWarnings("unused")
	public static boolean isAmazonFire() {
		return Build.MODEL != null && Build.MODEL.startsWith("AFT");
	}

	/**
	 * Literally Leanback, as in, no guessing based on mode type or model like {@link #isTV(Context)}
	 */
	public static boolean isLiterallyLeanback(Context context) {
		if (context == null) {
			context = BiglyBTApp.getContext();
		}
		PackageManager packageManager = requirePackageManager(context);
		return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
				|| packageManager.hasSystemFeature("android.software.leanback_only"); //NON-NLS

		// API 26:
		/**
		 * Feature for {@link #getSystemAvailableFeatures} and
		 * {@link #hasSystemFeature}: The device supports only leanback UI. Only
		 * applications designed for this experience should be run, though this is
		 * not enforced by the system.
		 * @hide
		 */
		//@SdkConstant(SdkConstantType.FEATURE)
		//public static final String FEATURE_LEANBACK_ONLY = "android.software.leanback_only";
	}

	public static boolean isTV(Context context) {
		if (isTV != null) {
			return isTV;
		}

		if (context == null) {
			// Potentially null when called from fragment that's not attached
			context = BiglyBTApp.getContext();
		}
		UiModeManager uiModeManager = (UiModeManager) context.getSystemService(
				Context.UI_MODE_SERVICE);
		isTV = uiModeManager != null
				&& uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
		if (isTV) {
			return isTV;
		}

		PackageManager packageManager = requirePackageManager(context);
		// alternate check
		//noinspection deprecation
		isTV = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
				|| packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
				|| packageManager.hasSystemFeature("android.software.leanback_only"); //NON-NLS
		if (isTV) {
			if (DEBUG) {
				Log.d(TAG,
						"isTV: not UI_MODE_TYPE_TELEVISION, however is has system feature suggesting tv");
			}
			return isTV;
		}

		String[] names = packageManager.getSystemSharedLibraryNames();
		if (names != null) {
			for (String name : names) {
				if (name.startsWith("com.google.android.tv")) { //NON-NLS
					isTV = true;
					if (DEBUG) {
						Log.d(TAG, "isTV: found tv shared library. Assuming tv");
					}
					return true;
				}
			}
		}

		if (!isTV) {
			// Odd instance where Shield Android TV isn't in UI_MODE_TYPE_TELEVISION
			// Most of the time it is..
			isTV = "SHIELD Android TV".equals(Build.MODEL);
		}

		if (!isTV) {
			// Example Android Box
			// {1.0 ?mcc?mnc [en_US] ldltr sw720dp w1280dp h648dp 160dpi lrg long land -touch qwerty/v/v dpad/v s.5}
			// sw720dp-land-notouch-dpad
			Resources resources = context.getResources();
			if (resources != null) {
				Configuration configuration = resources.getConfiguration();
				isTV = configuration != null
						&& configuration.smallestScreenWidthDp >= 720
						&& configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
						&& configuration.touchscreen == Configuration.TOUCHSCREEN_NOTOUCH
						&& configuration.navigation == Configuration.NAVIGATION_DPAD;
			}
		}

		return isTV;
	}

	public static boolean hasTouchScreen() {
		if (hasTouchScreen == null) {
			hasTouchScreen = requirePackageManager(null).hasSystemFeature(
					PackageManager.FEATURE_TOUCHSCREEN);
		}
		return hasTouchScreen;
	}

	public static boolean isChromium() {
		if (isChromium != null) {
			return isChromium;
		}
		if (Build.BRAND != null && Build.MANUFACTURER != null
				&& Build.BRAND.contains("chromium")
				&& Build.MANUFACTURER.contains("chromium")) {
			isChromium = true;
		} else {
			PackageManager pm = requirePackageManager(null);
			isChromium = pm.hasSystemFeature("org.chromium.arc.device_management")
					|| pm.hasSystemFeature("org.chromium.arc");
		}
		return isChromium;
	}

	// From http://stackoverflow.com/a/22883271
	public static boolean usesNavigationControl() {
		Resources resources = BiglyBTApp.getContext().getResources();
		if (resources == null) {
			return false;
		}
		Configuration configuration = resources.getConfiguration();
		if (configuration == null
				|| configuration.navigation == Configuration.NAVIGATION_NONAV
				|| configuration.touchscreen == Configuration.TOUCHSCREEN_FINGER) {
			return false;
		}

		if (configuration.navigation == Configuration.NAVIGATION_DPAD) {
			// Chromebooks all have some sort of mouse/trackpad, but often identify
			// as DPAD
			return !isChromium();
		}

		return configuration.touchscreen == Configuration.TOUCHSCREEN_NOTOUCH
				|| configuration.touchscreen == Configuration.TOUCHSCREEN_UNDEFINED
				|| configuration.navigationHidden == Configuration.NAVIGATIONHIDDEN_YES
				|| configuration.uiMode == Configuration.UI_MODE_TYPE_TELEVISION;
	}

	@SuppressWarnings("unused")
	public static int[] removeState(@NonNull int[] states, int state) {
		for (int i = 0; i < states.length; i++) {
			if (states[i] == state) {
				int[] newState = new int[states.length - 1];
				if (i > 0) {
					System.arraycopy(states, 0, newState, 0, i);
				}
				System.arraycopy(states, i + 1, newState, i, states.length - i - 1);
				return newState;
			}
		}
		return states;
	}

	@SuppressWarnings("unused")
	public static int[] addState(@NonNull int[] states, int state) {
		for (int oldState : states) {
			if (oldState == state) {
				return states;
			}
		}
		int[] newState = new int[states.length + 1];
		System.arraycopy(states, 0, newState, 1, states.length);
		newState[0] = state;
		return newState;
	}

	@NonNull
	@SuppressLint("InlinedApi")
	@SuppressWarnings("unused,nls")
	public static String statesDebug(int[] states) {
		if (states == null) {
			return "null";
		}
		if (states.length == 0) {
			return "[]";
		}
		SparseArray<String> map = new SparseArray<>();
		map.put(android.R.attr.state_above_anchor, "above_anchor");
		map.put(android.R.attr.state_accelerated, "accelerated");
		map.put(android.R.attr.state_activated, "activated");
		map.put(android.R.attr.state_active, "active");
		map.put(android.R.attr.state_checkable, "checkable");
		map.put(android.R.attr.state_checked, "checked");
		map.put(android.R.attr.state_drag_can_accept, "drag_can_accept");
		map.put(android.R.attr.state_drag_hovered, "drag_hovered");
		map.put(android.R.attr.state_empty, "empty");
		map.put(android.R.attr.state_enabled, "enabled");
		map.put(android.R.attr.state_expanded, "expanded");
		map.put(android.R.attr.state_first, "first");
		map.put(android.R.attr.state_focused, "focused");
		map.put(android.R.attr.state_hovered, "hovered");
		map.put(android.R.attr.state_last, "last");
		map.put(android.R.attr.state_long_pressable, "long_pressable");
		map.put(android.R.attr.state_middle, "middle");
		map.put(android.R.attr.state_multiline, "multiline");
		map.put(android.R.attr.state_pressed, "pressed");
		map.put(android.R.attr.state_selected, "selected");
		map.put(android.R.attr.state_single, "single");
		map.put(android.R.attr.state_window_focused, "window_focused");

		StringBuilder sb = new StringBuilder();
		sb.append("[");
		for (int state : states) {
			if (sb.length() > 1) {
				sb.append(',');
			}
			String s = map.get(state);
			if (s == null) {
				sb.append(state);
			} else {
				sb.append(s);
			}
		}
		sb.append(']');

		return sb.toString();
	}

	public static int stringCompare(CharSequence s1, CharSequence s2) {
		if (s1 == null) {
			return s2 == null ? 0 : 1;
		} else if (s2 == null) {
			return -1;
		} else {
			return s1.toString().compareTo(s2.toString());
		}
	}

	/**
	 * See {@link Integer#compare(int, int)}, available in API 19+
	 */
	public static int integerCompare(int lhs, int rhs) {
		//noinspection UseCompareMethod (Integer.compare is API 19)
		return lhs < rhs ? -1 : (lhs == rhs ? 0 : 1);
	}

	public static int longCompare(long lhs, long rhs) {
		//noinspection UseCompareMethod (Long.compare is API 19)
		return lhs < rhs ? -1 : (lhs == rhs ? 0 : 1);
	}

	@SuppressLint("LogConditional")
	public static boolean hasPermisssion(@NonNull Context context,
			@NonNull String permission) {
		PackageManager packageManager = requirePackageManager(context);
		try {
			packageManager.getPermissionInfo(permission, 0);
		} catch (PackageManager.NameNotFoundException e) {
			Log.d("Perms", "requestPermissions: Permission " + permission
					+ " doesn't exist.  Assuming granted.");
			return true;
		}
		return ContextCompat.checkSelfPermission(context,
				permission) == PackageManager.PERMISSION_GRANTED;
	}

	public static String getProcessName(@NonNull Context context, int pID) {
		BufferedReader cmdlineReader = null;
		// https://github.com/facebook/stetho/issues/379 says /proc/cmdline
		// is a kernal interface and the disk warning can be ignored.
		StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
		try {
			cmdlineReader = new BufferedReader(
					new InputStreamReader(
							new FileInputStream("/proc/" + pID + "/cmdline"), "iso-8859-1"), //NON-NLS
					100);
			int c;
			StringBuilder processName = new StringBuilder();
			while ((c = cmdlineReader.read()) > 0) {
				processName.append((char) c);
			}
			return processName.toString();
		} catch (Throwable ignore) {
		} finally {
			StrictMode.setThreadPolicy(oldPolicy);
			if (cmdlineReader != null) {
				try {
					cmdlineReader.close();
				} catch (IOException ignore) {
				}
			}
		}
		return getProcessName_PM(context, pID);
	}

	/**
	 * Get Process Name by getRunningAppProcesses
	 * <p/>
	 * It's been reported that sometimes, the list returned from
	 * getRunningAppProcesses simply doesn't contain your own process
	 * (especially when called from Application).
	 * Use {@link #getProcessName(Context, int)} instead
	 */
	@NonNull
	private static String getProcessName_PM(@NonNull Context context, int pID) {
		String processName = "";
		ActivityManager am = (ActivityManager) context.getSystemService(
				Context.ACTIVITY_SERVICE);
		if (am == null) {
			return processName;
		}
		List<ActivityManager.RunningAppProcessInfo> l = am.getRunningAppProcesses();
		if (l == null) {
			return processName;
		}
		for (Object aL : l) {
			ActivityManager.RunningAppProcessInfo info = (ActivityManager.RunningAppProcessInfo) (aL);
			try {
				if (info.pid == pID && info.processName != null) {
					return info.processName;
				}
			} catch (Exception e) {
				Log.e(TAG, "getAppName: error", e);
			}
		}
		return processName;
	}

	@Nullable
	@SuppressWarnings("unused")
	public static Thread getThreadByName(String name) {
		ThreadGroup tg = Thread.currentThread().getThreadGroup();
		if (tg == null) {
			return null;
		}

		while (tg.getParent() != null) {

			tg = tg.getParent();
		}

		Thread[] threads = new Thread[tg.activeCount() + 1024];

		tg.enumerate(threads, true);

		for (Thread t : threads) {

			if (t != null && t.isAlive() && t != Thread.currentThread()
					&& !t.isDaemon() && t.getName().equals(name)) {
				return t;
			}
		}

		return null;
	}

	@SuppressLint("LogConditional")
	public static void dumpBatteryStats(@NonNull Context context) {
		IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		Intent batteryStatus = context.registerReceiver(null, ifilter);
		if (batteryStatus == null) {
			Log.d(TAG, "dumpBatteryStats: null");
			return;
		}

		Bundle bundle = batteryStatus.getExtras();
		if (bundle == null) {
			Log.d(TAG, "dumpBatteryStats: " + batteryStatus);
			return;
		}
		StringBuilder sb = new StringBuilder();
		sb.append("Battery: ");
		boolean first = true;
		Set<String> keys = bundle.keySet();
		if (keys == null) {
			return;
		}
		for (String key : keys) {
			if (first) {
				first = false;
			} else {
				sb.append(", ");
			}

			Object value = bundle.get(key);
			sb.append(key);
			sb.append('=');
			if (value == null) {
				sb.append("null");
			} else {
				sb.append(value.toString());
			}
		}
		Log.d(TAG, sb.toString());
	}

	@Contract("null -> !null")
	public static String unescapeXML(String s) {
		if (s == null) {
			return "";
		}
		if (s.indexOf('&') < 0) {
			return s;
		}
		String ret = s;
		for (Object XMLescape : XMLescapes) {
			String[] escapeEntry = (String[]) XMLescape;
			ret = ret.replace(escapeEntry[1], escapeEntry[0]);
		}
		return ret;
	}

	public static long getTodayMS() {
		Calendar today = Calendar.getInstance();
		today.set(Calendar.MILLISECOND, 0);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.HOUR_OF_DAY, 0);
		return today.getTimeInMillis();
	}

	@NonNull
	public static Spanned fromHTML(@NonNull String message) {
		message = message.replaceAll("\n", "<br/>");
		if (message.indexOf('<') < 0) {
			return new SpannedString(message);
		}
		Spanned spanned;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			spanned = Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY);
		} else {
			//noinspection deprecation
			spanned = Html.fromHtml(message);
		}
		return spanned == null ? new SpannedString(message) : spanned;
	}

	/**
	 * Gets a html String resource with format arguements.  Ensures format
	 * arguments aren't html
	 */
	@NonNull
	public static Spanned fromHTML(@NonNull Resources resources,
			@StringRes int id, @NonNull Object... formatArgs)
			throws Resources.NotFoundException {
		Object[] encodedArgs = new Object[formatArgs.length];
		for (int i = 0; i < formatArgs.length; i++) {
			Object formatArg = formatArgs[i];
			if (formatArg instanceof String) {
				encodedArgs[i] = TextUtils.htmlEncode((String) formatArg);
			} else {
				encodedArgs[i] = formatArg;
			}
		}
		return AndroidUtils.fromHTML(resources.getString(id, encodedArgs));
	}

	public static long mutiplyBy1024(long num, long times) {
		long r = num;
		for (int i = 0; i < times; i++) {
			r = r << 10;
		}
		return r;
	}

	@NonNull
	public static String getFileName(@NonNull String s) {
		int i = s.lastIndexOf('/');
		if (i >= 0 && i < s.length() - 1) {
			return s.substring(i + 1);
		}

		i = s.lastIndexOf('\\');
		if (i >= 0 && i < s.length() - 1) {
			return s.substring(i + 1);
		}

		return s;
	}

	/**
	 * Gets the extension of a file name, ensuring we don't go into the path
	 *
	 * @param fileNameWithOptionalPath  File name
	 * @return extension, with the '.'
	 */
	@NonNull
	public static String getFileExtension(
			@NonNull String fileNameWithOptionalPath) {
		String fileName = getFileName(fileNameWithOptionalPath);
		final int fileDotIndex = fileName.lastIndexOf('.');
		if (fileDotIndex == -1) {
			return "";
		}

		return fileName.substring(fileDotIndex);
	}

	@NonNull
	public static String getSimpleName(@NonNull Class aClass) {
		String simpleName = aClass.getSimpleName();
		if (simpleName.isEmpty()) {
			String name = aClass.getName();
			int i = name.lastIndexOf('.');
			if (i >= 0) {
				simpleName = name.substring(i + 1);
			} else {
				simpleName = name;
			}
		}
		return simpleName;
	}

	public static String decodeURL(String url) {
		try {
			return URLDecoder.decode(url, UTF_8);
		} catch (UnsupportedEncodingException e) {
			return url;
		}
	}

	/**
	 * Retrieve a string from the system, with fallback in case the system
	 * distributor didn't include the string.
	 */
	public static String getSystemString(@NonNull Context context,
			@NonNull String key, @StringRes int fallbackTextId) {
		Resources system = Resources.getSystem();
		if (system == null) {
			return context.getString(fallbackTextId);
		}

		String text;
		int textId = system.getIdentifier(key, "string", "android"); //NON-NLS
		try {
			text = textId == 0 ? context.getString(fallbackTextId)
					: system.getString(textId);
			if (textId != 0 && text.contains("%")) {
				// Samsung SM-T813 API 19 returns "%-B"
				text = context.getString(fallbackTextId);
			}
		} catch (Throwable e) { // android.content.res.Resources$NotFoundException
			// Case in the wild where getIdentifier returns non-0, 
			// but getString fails to find it
			text = context.getString(fallbackTextId);
		}

		return text;
	}

	@NonNull
	public static String generateEasyPW(int numChars) {
		StringBuilder sb = new StringBuilder(numChars);
		SecureRandom r = new SecureRandom();
		for (int i = 0; i < numChars; i++) {
			sb.append('a' + (int) (r.nextDouble() * 26));
		}
		return sb.toString();
	}

	public static int getBundleSizeInBytes(Bundle bundle) {
		if (bundle == null) {
			return 0;
		}
		Parcel parcel = Parcel.obtain();
		int size;

		parcel.writeBundle(bundle);
		size = parcel.dataSize();
		parcel.recycle();

		return size;
	}

	public static boolean addToBundleIf(Bundle src, Bundle dst, long maxSize) {
		if (src == null || dst == null) {
			return false;
		}
		int bytesSrc = AndroidUtils.getBundleSizeInBytes(src);
		int bytesDest = AndroidUtils.getBundleSizeInBytes(dst);
		if (bytesSrc + bytesDest <= maxSize) {
			dst.putAll(src);
			return true;
		}
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onSaveInstanceState: Bundle too large, skipping (src="
					+ bytesSrc + "; dst=" + bytesDest + ")");
		}
		return false;
	}

	@NonNull
	public static Resources requireResources() {
		Resources resources = BiglyBTApp.getContext().getResources();
		if (resources == null) {
			throw new IllegalStateException("getResources is null");
		}
		return resources;
	}

	@NonNull
	public static Resources requireResources(@Nullable Context context) {
		if (context != null) {
			Resources resources = context.getResources();
			if (resources != null) {
				return resources;
			}
		}
		Resources resources = BiglyBTApp.getContext().getResources();
		if (resources == null) {
			throw new IllegalStateException("getResources is null");
		}
		return resources;
	}

	@NonNull
	public static Resources requireResources(@Nullable View view) {
		if (view != null) {
			Resources resources = view.getResources();
			if (resources != null) {
				return resources;
			}
		}
		Resources resources = BiglyBTApp.getContext().getResources();
		if (resources == null) {
			throw new IllegalStateException("getResources is null");
		}
		return resources;
	}

	@NonNull
	public static PackageManager requirePackageManager(
			@Nullable Context context) {
		if (context != null) {
			PackageManager packageManager = context.getPackageManager();
			if (packageManager != null) {
				return packageManager;
			}
		}
		PackageManager packageManager = BiglyBTApp.getContext().getPackageManager();
		if (packageManager == null) {
			throw new IllegalStateException("getPackageManager is null");
		}
		return packageManager;
	}
}
