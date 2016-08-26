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

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Pattern;

import javax.net.ssl.*;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.ByteArrayBuffer;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.*;
import android.app.AlertDialog.Builder;
import android.content.*;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.support.annotation.NonNull;
import android.support.v4.app.*;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.*;
import android.widget.Toast;

import com.vuze.android.remote.activity.MetaSearchActivity;

/**
 * Some generic Android Utility methods.
 * <p/>
 * Some utility methods specific to this app and requiring Android API.
 * Should, and probably should be in their own class.
 */
@SuppressWarnings({
	"SameParameterValue",
	"WeakerAccess"
})
public class AndroidUtils
{
	public static final boolean DEBUG = BuildConfig.DEBUG;

	public static final boolean DEBUG_RPC = DEBUG && false;

	public static final boolean DEBUG_MENU = DEBUG && false;

	public static final boolean DEBUG_ADAPTER = DEBUG && true;

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

	private static Boolean isTV = null;

	private static Boolean hasTouchScreen;

	public static class AlertDialogBuilder
	{
		public View view;

		public final AlertDialog.Builder builder;

		public AlertDialogBuilder(View view, Builder builder) {
			super();
			this.view = view;
			this.builder = builder;
		}
	}

	public static abstract class RunnableWithActivity
		implements Runnable
	{
		public Activity activity;
	}

	// ACTION_POWER_CONNECTED
	public static boolean isPowerConnected(Context context) {
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

	// From http://
	public static void openFileChooser(Activity activity, String mimeType,
			int requestCode) {

		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType(mimeType);
		intent.addCategory(Intent.CATEGORY_OPENABLE);

		// special intent for Samsung file manager
		Intent sIntent = new Intent("com.sec.android.app.myfiles.PICK_DATA");

		sIntent.putExtra("CONTENT_TYPE", mimeType);
		sIntent.addCategory(Intent.CATEGORY_DEFAULT);

		Intent chooserIntent;
		if (activity.getPackageManager().resolveActivity(sIntent, 0) != null) {
			chooserIntent = Intent.createChooser(sIntent, "Open file");
			chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] {
				intent
			});
		} else {
			chooserIntent = Intent.createChooser(intent, "Open file");
		}

		if (chooserIntent != null) {
			try {
				activity.startActivityForResult(chooserIntent, requestCode);
				return;
			} catch (android.content.ActivityNotFoundException ex) {
			}
		}
		Toast.makeText(activity.getApplicationContext(),
				activity.getResources().getString(R.string.no_file_chooser),
				Toast.LENGTH_SHORT).show();
	}

	/**
	 * Remove all extras from intent
	 */
	@SuppressWarnings("unused")
	public static void clearExtras(Intent intent) {
		Bundle extras = intent.getExtras();
		if (extras == null) {
			return;
		}
		for (String key : extras.keySet()) {
			intent.removeExtra(key);
		}
	}

	/**
	 * Android doesn't fade out disabled menu item icons, so do it ourselves
	 */
	public static void fixupMenuAlpha(Menu menu) {
		for (int i = 0; i < menu.size(); i++) {
			MenuItem item = menu.getItem(i);
			Drawable icon = item.getIcon();
			if (icon != null) {
				icon.setAlpha(item.isEnabled() ? 255 : 64);
			}
		}
	}

	public static class ValueStringArray
	{
		public final int size;

		public final long[] values;

		public final String[] strings;

		public ValueStringArray(long[] value, String[] string) {
			this.values = value;
			this.strings = string;
			this.size = Math.min(values.length, string.length);
		}

	}

	public static ValueStringArray getValueStringArray(Resources resources,
			int id) {
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

	public static boolean executeSearch(String search, Context context,
			SessionInfo sessionInfo) {
		Intent myIntent = new Intent(Intent.ACTION_SEARCH);
		myIntent.setClass(context, MetaSearchActivity.class);

		RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
		if (remoteProfile != null) {
			myIntent.putExtra(SessionInfoManager.BUNDLE_KEY, remoteProfile.getID());

			if (remoteProfile.getRemoteType() == RemoteProfile.TYPE_LOOKUP) {
				Bundle bundle = new Bundle();
				bundle.putString("com.vuze.android.remote.searchsource",
						sessionInfo.getRpcRoot());
				if (remoteProfile.getRemoteType() == RemoteProfile.TYPE_LOOKUP) {
					bundle.putString("com.vuze.android.remote.ac", remoteProfile.getAC());

				}
				bundle.putString(SessionInfoManager.BUNDLE_KEY, remoteProfile.getID());

				myIntent.putExtra(SearchManager.APP_DATA, bundle);
			}
		}

		myIntent.putExtra(SearchManager.QUERY, search);

		context.startActivity(myIntent);
		return true;
	}

	public static boolean isURLAlive(String URLName) {
		if (isURLAlive(URLName, 1000, 1000)) {
			return true;
		}
		if (isURLAlive(URLName, 10000, 5000)) {
			return true;
		}
		return false;
	}

	private static boolean isURLAlive(String URLName, int conTimeout,
			int readTimeout) {
		try {
			HttpURLConnection.setFollowRedirects(false);

			URL url = new URL(URLName);
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			if (con instanceof HttpsURLConnection) {
				HttpsURLConnection conHttps = (HttpsURLConnection) con;

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
					SSLContext ctx = SSLContext.getInstance("TLS");
					ctx.init(new KeyManager[0], new TrustManager[] {
						new DefaultTrustManager()
					}, new SecureRandom());
					conHttps.setSSLSocketFactory(ctx.getSocketFactory());
				}

				conHttps.setHostnameVerifier(new HostnameVerifier() {
					@Override
					public boolean verify(String hostname, SSLSession session) {
						return true;
					}
				});
			}

			con.setConnectTimeout(conTimeout);
			con.setReadTimeout(readTimeout);
			con.setRequestMethod("HEAD");
			con.getResponseCode();
			if (DEBUG) {
				Log.d(TAG, "isLive? conn result=" + con.getResponseCode() + ";"
						+ con.getResponseMessage());
			}
			return true;
		} catch (Exception e) {
			if (DEBUG) {
				Log.e(TAG, "isLive " + URLName, e);
			}
			return false;
		}
	}

	public static boolean readInputStreamIfStartWith(InputStream is,
			ByteArrayBuffer bab, byte[] startsWith)
			throws IOException {

		byte[] buffer = new byte[32 * 1024];

		boolean first = true;

		try {
			while (true) {

				int len = is.read(buffer);

				if (len <= 0) {

					break;
				}

				bab.append(buffer, 0, len);

				if (first) {
					first = false;
					for (int i = 0; i < startsWith.length; i++) {
						if (startsWith[i] != buffer[i]) {
							return false;
						}
					}
				}
			}

			return !bab.isEmpty();

		} finally {

			is.close();
		}
	}

	public static byte[] readInputStreamAsByteArray(InputStream is)
			throws IOException {
		int available = is.available();
		if (available <= 0) {
			available = 32 * 1024;
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream(available);

		byte[] buffer = new byte[32 * 1024];

		try {
			while (true) {

				int len = is.read(buffer);

				if (len <= 0) {

					break;
				}

				baos.write(buffer, 0, len);
			}

			return (baos.toByteArray());

		} finally {

			is.close();
		}
	}

	public static void invalidateOptionsMenuHC(final Activity activity) {
		invalidateOptionsMenuHC(activity, null);
	}

	public static void invalidateOptionsMenuHC(final Activity activity,
			final android.support.v7.view.ActionMode mActionMode) {
		if (activity == null) {
			return;
		}
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (activity.isFinishing()) {
					return;
				}
				if (mActionMode != null) {
					mActionMode.invalidate();
					return;
				}
				if (activity instanceof FragmentActivity) {
					FragmentActivity aba = (FragmentActivity) activity;
					aba.supportInvalidateOptionsMenu();
				} else {
					ActivityCompat.invalidateOptionsMenu(activity);
				}
			}
		});
	}

	// From FileUtil.java
	public static void copyFile(final InputStream _source, final File _dest,
			boolean _close_input_stream)

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
			} catch (IOException e) {
			}

			if (dest != null) {

				dest.close();
			}
		}
	}

	// From FileUtil.java
	public static void copyFile(InputStream is, OutputStream os,
			boolean closeInputStream)

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
			} catch (IOException e) {

			}

			os.close();
		}
	}

	public static boolean readURL(String uri, ByteArrayBuffer bab,
			byte[] startsWith)
			throws IllegalArgumentException {

		BasicHttpParams basicHttpParams = new BasicHttpParams();
		HttpProtocolParams.setUserAgent(basicHttpParams, "Vuze Android Remote");
		DefaultHttpClient httpclient = new DefaultHttpClient(basicHttpParams);

		// Prepare a request object
		HttpRequestBase httpRequest = new HttpGet(uri);

		// Execute the request
		HttpResponse response;

		try {
			response = httpclient.execute(httpRequest);

			HttpEntity entity = response.getEntity();
			if (entity != null) {

				// A Simple JSON Response Read
				InputStream is = entity.getContent();
				return readInputStreamIfStartWith(is, bab, startsWith);
			}

		} catch (Exception e) {
			VuzeEasyTracker.getInstance().logError(e);
		}

		return false;
	}

	public static void copyUrlToFile(String uri, File outFile)
			throws ClientProtocolException, IOException {

		BasicHttpParams basicHttpParams = new BasicHttpParams();
		HttpProtocolParams.setUserAgent(basicHttpParams, "Vuze Android Remote");
		DefaultHttpClient httpclient = new DefaultHttpClient(basicHttpParams);

		// Prepare a request object
		HttpRequestBase httpRequest = new HttpGet(uri);

		// Execute the request
		HttpResponse response;

		response = httpclient.execute(httpRequest); // HttpHostConnectException

		HttpEntity entity = response.getEntity();
		if (entity != null) {

			// A Simple JSON Response Read
			InputStream is = entity.getContent();
			copyFile(is, outFile, true); // FileNotFoundException
		}
	}

	public static File getDownloadDir() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
			return getDownloadDir_Froyo();
		}
		return new File(Environment.getExternalStorageDirectory() + "/downloads");
	}

	@TargetApi(Build.VERSION_CODES.FROYO)
	private static File getDownloadDir_Froyo() {
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
	public static String lineBreaker(String s) {
		s = patLineBreakerAfter.matcher(s).replaceAll("$1\u200B$2");
		s = patLineBreakerAround.matcher(s).replaceAll("\u200B$1\u200B$2");
		return s;
	}

	public static String getCompressedStackTrace() {
		try {
			throw new Exception();
		} catch (Exception e) {
			return getCompressedStackTrace(e, 1, 12);
		}
	}

	public static String getCompressedStackTrace(int limit) {
		try {
			throw new Exception();
		} catch (Exception e) {
			return getCompressedStackTrace(e, 1, limit);
		}
	}

	public static String getCompressedStackTrace(Throwable t, int startAt,
			int limit) {
		try {
			StackTraceElement[] stackTrace = t.getStackTrace();
			if (stackTrace.length < startAt) {
				return "";
			}
			StringBuilder sb = new StringBuilder("");
			for (int i = startAt; i < stackTrace.length && i < startAt + limit; i++) {
				StackTraceElement element = stackTrace[i];
				String classname = element.getClassName();
				String cnShort;
				boolean showLineNumber = true;
				boolean breakAfter = false;
				if (classname.startsWith("com.vuze.android.remote.")) {
					cnShort = classname.substring(24, classname.length());
				} else if (classname.equals("android.os.Handler")) {
					showLineNumber = false;
					cnShort = "Handler";
				} else if (classname.equals("android.os.Looper")) {
					showLineNumber = false;
					cnShort = "Looper";
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
				if (i != startAt) {
					sb.append(", ");
				}
				sb.append(cnShort);
				sb.append('.');
				sb.append(element.getMethodName());
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
				sb.append("\n|Cause ");
				sb.append(cause.getClass().getSimpleName());
				if (cause instanceof Resources.NotFoundException
						|| cause instanceof RuntimeException) {
					sb.append(' ');
					sb.append(cause.getMessage());
				}
				sb.append(' ');
				sb.append(getCompressedStackTrace(cause, 0, 9));
			}
			return sb.toString();
		} catch (Throwable derp) {
			return "derp " + derp.getClass().getSimpleName();
		}
	}

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
			return "derp " + derp.getClass().getSimpleName();
		}
	}

	public static ComponentInfo getComponentInfo(ResolveInfo info) {
		if (info.activityInfo != null)
			return info.activityInfo;
		if (info.serviceInfo != null)
			return info.serviceInfo;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			return getComponentInfo_v19(info);
		}
		return null;
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	private static ComponentInfo getComponentInfo_v19(ResolveInfo info) {
		if (info.providerInfo != null)
			return info.providerInfo;
		return null;
	}

	@SuppressWarnings("unused")
	public static String getStatesString(int[] ints) {
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
	public static int indexOfAny(String findIn, String findAnyChar,
			int startPos) {
		for (int i = 0; i < findAnyChar.length(); i++) {
			char c = findAnyChar.charAt(i);
			int pos = findIn.indexOf(c, startPos);
			if (pos >= 0) {
				return pos;
			}
		}
		return -1;
	}

	public static int lastindexOfAny(String findIn, String findAnyChar,
			int startPos) {
		if (startPos > findIn.length()) {
			return -1;
		}
		for (int i = 0; i < findAnyChar.length(); i++) {
			char c = findAnyChar.charAt(i);
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
		return Build.MODEL.startsWith("AFT");
	}

	public static boolean isTV() {
		if (isTV == null) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
				Context context = VuzeRemoteApp.getContext();
				UiModeManager uiModeManager = (UiModeManager) context.getSystemService(
						Context.UI_MODE_SERVICE);
				isTV = uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
				if (!isTV) {
					// alternate check
					isTV = context.getPackageManager().hasSystemFeature(
							PackageManager.FEATURE_TELEVISION)
							|| context.getPackageManager().hasSystemFeature(
									PackageManager.FEATURE_LEANBACK)
							|| context.getPackageManager().hasSystemFeature(
									"android.software.leanback_only");
					if (isTV && DEBUG) {
						Log.d(TAG,
								"isTV: not UI_MODE_TYPE_TELEVISION, however is has system "
										+ "feature suggesting tv");
					}

					if (!isTV) {
						String[] names = context.getPackageManager().getSystemSharedLibraryNames();
						for (String name : names) {
							if (name.startsWith("com.google.android.tv")) {
								isTV = true;
								if (DEBUG) {
									Log.d(TAG, "isTV: found tv shared library. Assuming tv");
								}
								break;
							}
						}
					}
				}
			} else {
				isTV = false;
			}
		}

		return isTV;
	}

	public static boolean hasTouchScreen() {
		if (hasTouchScreen == null) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
				hasTouchScreen = VuzeRemoteApp.getContext().getPackageManager().hasSystemFeature(
						PackageManager.FEATURE_TOUCHSCREEN);
			} else {
				hasTouchScreen = true;
			}
		}
		return hasTouchScreen;
	}

	// From http://stackoverflow.com/a/22883271
	public static boolean usesNavigationControl() {
		Configuration configuration = VuzeRemoteApp.getContext().getResources().getConfiguration();
		if (configuration.navigation == Configuration.NAVIGATION_NONAV) {
			return false;
		} else if (configuration.touchscreen == Configuration.TOUCHSCREEN_FINGER) {
			return false;
		} else if (configuration.navigation == Configuration.NAVIGATION_DPAD) {
			// Chromebooks all have some sort of mouse/trackpad, but often identify
			// as DPAD
			if (Build.BRAND.contains("chromium")
					&& Build.MANUFACTURER.contains("chromium")) {
				return false;
			}
			return true;
		} else if (configuration.touchscreen == Configuration.TOUCHSCREEN_NOTOUCH) {
			return true;
		} else if (configuration.touchscreen == Configuration.TOUCHSCREEN_UNDEFINED) {
			return true;
		} else if (configuration.navigationHidden == Configuration.NAVIGATIONHIDDEN_YES) {
			return true;
		} else if (configuration.uiMode == Configuration.UI_MODE_TYPE_TELEVISION) {
			return true;
		}
		return false;
	}

	@SuppressWarnings("unused")
	public static int[] removeState(int[] states, int state) {
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
	public static int[] addState(int[] states, int state) {
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

	@SuppressLint("InlinedApi")
	@SuppressWarnings("unused")
	public static String statesDebug(int[] states) {
		if (states == null) {
			return "null";
		}
		if (states.length == 0) {
			return "[]";
		}
		Map<Integer, String> map = new HashMap<>();
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

	public static int integerCompare(int lhs, int rhs) {
		return lhs < rhs ? -1 : (lhs == rhs ? 0 : 1);
	}

	public static int longCompare(long lhs, long rhs) {
		return lhs < rhs ? -1 : (lhs == rhs ? 0 : 1);
	}

	public static boolean hasPermisssion(@NonNull Context context,
			@NonNull String permission) {
		PackageManager packageManager = context.getPackageManager();
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

	public static String getProcessName(Context context, int pID) {
		BufferedReader cmdlineReader = null;
		try {
			cmdlineReader = new BufferedReader(
					new InputStreamReader(
							new FileInputStream("/proc/" + pID + "/cmdline"), "iso-8859-1"),
					100);
			int c;
			StringBuilder processName = new StringBuilder();
			while ((c = cmdlineReader.read()) > 0) {
				processName.append((char) c);
			}
			return processName.toString();
		} catch (Throwable ignore) {
		} finally {
			if (cmdlineReader != null) {
				try {
					cmdlineReader.close();
				} catch (IOException e) {
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
	public static String getProcessName_PM(Context context, int pID) {
		String processName = "";
		ActivityManager am = (ActivityManager) context.getSystemService(
				Context.ACTIVITY_SERVICE);
		List l = am.getRunningAppProcesses();
		for (Object aL : l) {
			ActivityManager.RunningAppProcessInfo info = (ActivityManager.RunningAppProcessInfo) (aL);
			try {
				if (info.pid == pID) {
					return info.processName;
				}
			} catch (Exception e) {
				Log.e(TAG, "getAppName: error", e);
			}
		}
		return processName;
	}

	@SuppressWarnings("unused")
	public static Thread getThreadByName(String name) {
		ThreadGroup tg = Thread.currentThread().getThreadGroup();

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

	public static void dumpBatteryStats(Context context) {
		IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		Intent batteryStatus = context.registerReceiver(null, ifilter);
		if (batteryStatus == null) {
			Log.d(TAG, "dumpBatteryStats: null");
			return;
		}

		Bundle bundle = batteryStatus.getExtras();
		for (String key : bundle.keySet()) {
			Object value = bundle.get(key);
			if (value == null) {
				Log.d(TAG, "Battery,%s=" + key);
			} else {
				Log.d(TAG, String.format("Battery,%s=%s (%s)", key, value.toString(),
						value.getClass().getName()));
			}
		}
	}

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

	public static Spanned fromHTML(String message) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			return Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY);
		}
		return Html.fromHtml(message);
	}

	public static long mutiplyBy1024(long num, long times) {
		long r = num;
		for (int i = 0; i < times; i++) {
			r = r << 10;
		}
		return r;
	}

}
