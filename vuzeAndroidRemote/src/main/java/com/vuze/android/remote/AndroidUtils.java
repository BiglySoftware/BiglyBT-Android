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
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.net.ssl.*;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.ByteArrayBuffer;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.*;
import android.app.AlertDialog.Builder;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.support.v4.app.*;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.InputDeviceCompat;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.CharacterStyle;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.*;
import android.view.WindowManager.BadTokenException;
import android.widget.TextView;
import android.widget.Toast;

import com.vuze.android.remote.activity.MetaSearch;
import com.vuze.android.remote.rpc.RPCException;

/**
 * Some generic Android Utility methods.
 * <p/>
 * Some utility methods specific to this app and requiring Android API.
 * Should, and probably should be in their own class.
 */
@SuppressWarnings("SameParameterValue")
public class AndroidUtils
{
	public static final boolean DEBUG = BuildConfig.DEBUG;

	public static final boolean DEBUG_RPC = DEBUG && false;

	public static final boolean DEBUG_MENU = DEBUG && false;

	public static final boolean DEBUG_ADAPTER = DEBUG && false;

	private static final String TAG = "Utils";

	// 	 . _ - \ /
	private static final Pattern patLineBreakerAround = Pattern.compile(
			"([._\\-\\\\/]+)([^\\s])");

	//   ; ]
	private static final Pattern patLineBreakerAfter = Pattern.compile(
			"([;\\]])([^\\s])");

	private static boolean hasAlertDialogOpen = false;

	private static AlertDialog currentSingleDialog;

	private static Boolean isTV = null;

	private static Boolean hasTouchScreen;

	public static class AlertDialogBuilder
	{
		public View view;

		public AlertDialog.Builder builder;

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

	/**
	 * Creates an AlertDialog.Builder that has the proper theme for Gingerbread
	 */
	public static AndroidUtils.AlertDialogBuilder createAlertDialogBuilder(
			Activity activity, int resource) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);

		// Not sure if we need this anymore, but once upon a time, pre-honeycomb
		// (2.x) had dialog color issues
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1) {
			builder.setInverseBackgroundForced(true);
		}

		View view = View.inflate(activity, resource, null);
		builder.setView(view);

		return new AndroidUtils.AlertDialogBuilder(view, builder);
	}

	public static void openSingleAlertDialog(Activity ownerActivity,
			AlertDialog.Builder builder) {
		openSingleAlertDialog(ownerActivity, builder, null);
	}

	@SuppressWarnings("ConstantConditions")
	public static void openSingleAlertDialog(Activity ownerActivity,
			AlertDialog.Builder builder, final OnDismissListener dismissListener) {
		// We should always be on the UI Thread, so no need to synchronize
		if (hasAlertDialogOpen) {
			if (currentSingleDialog == null
					|| currentSingleDialog.getOwnerActivity() == null
					|| !currentSingleDialog.getOwnerActivity().isFinishing()) {
				if (DEBUG) {
					Log.e(TAG, "Already have Alert Dialog Open " + currentSingleDialog);
				}
				return;
			}
		}

		if (DEBUG && hasAlertDialogOpen) {
			Log.e(TAG, "hasAlertDialogOpen flag ON, but dialog isn't showing");
		}

		hasAlertDialogOpen = true;

		try {
			currentSingleDialog = builder.show();
			currentSingleDialog.setOwnerActivity(ownerActivity);
			if (DEBUG) {
				Log.d(TAG, "Alert Dialog Open " + getCompressedStackTrace());
			}

			// Note: There's a builder.setOnDismissListener(), but it's API 17
			currentSingleDialog.setOnDismissListener(new OnDismissListener() {
				@Override
				public void onDismiss(DialogInterface dialog) {
					hasAlertDialogOpen = false;
					if (dismissListener != null) {
						dismissListener.onDismiss(dialog);
					}
				}
			});
		} catch (BadTokenException bte) {
			// android.view.WindowManager$BadTokenException: Unable to add window --
			// token android.os.BinderProxy@42043ff8 is not valid; is your activity
			// running?
			// ignore.  We checked activity.isFinishing() earlier.. not much we
			// can do
			Log.e(TAG, "AlertDialog", bte);
		}
	}

	public static void showConnectionError(Activity activity, Throwable t,
			boolean allowContinue) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "showConnectionError "
					+ AndroidUtils.getCompressedStackTrace(t, 0, 9));
		}

		Throwable t2 = (t instanceof RPCException) ? t.getCause() : t;

		if ((t2 instanceof HttpHostConnectException)
				|| (t2 instanceof UnknownHostException)) {
			String message = t.getMessage();
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "showConnectionError Yup " + message);
			}
			if (message != null && message.contains("pair.vuze.com")) {
				showConnectionError(activity, R.string.connerror_pairing,
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

	public static void showConnectionError(Activity activity, int errMsgID,
			boolean allowContinue) {
		String errMsg = activity.getResources().getString(errMsgID);
		showConnectionError(activity, errMsg, allowContinue);
	}

	public static void showConnectionError(final Activity activity,
			final String errMsg, final boolean allowContinue) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "showConnectionError.string "
					+ AndroidUtils.getCompressedStackTrace());
		}
		if (activity == null) {
			Log.e(null, "No activity for error message " + errMsg);
			return;
		}
		activity.runOnUiThread(new Runnable() {
			public void run() {
				if (activity.isFinishing()) {
					if (DEBUG) {
						System.out.println("can't display -- finishing");
					}
					return;
				}
				Builder builder = new AlertDialog.Builder(activity).setTitle(
						R.string.error_connecting).setMessage(errMsg).setCancelable(
								true).setNegativeButton(R.string.action_logout,
										new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						if (activity.isTaskRoot()) {
							new RemoteUtils(activity).openRemoteList();
						}
						activity.finish();
					}
				});
				if (allowContinue) {
					builder.setPositiveButton(R.string.button_continue,
							new OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
						}
					});
				}
				AndroidUtils.openSingleAlertDialog(activity, builder);
			}
		});

	}

	public static void showDialog(Activity activity, int titleID,
			CharSequence msg) {
		String title = activity.getResources().getString(titleID);
		showDialog(activity, title, msg);
	}

	public static void showDialog(final Activity activity,
			final CharSequence title, final CharSequence msg) {
		activity.runOnUiThread(new Runnable() {
			public void run() {
				if (activity.isFinishing()) {
					if (DEBUG) {
						System.out.println("can't display -- finishing");
					}
					return;
				}
				Builder builder = new AlertDialog.Builder(activity).setMessage(
						msg).setCancelable(true).setNegativeButton(android.R.string.ok,
								new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
					}
				});
				if (title != null) {
					builder.setTitle(title);
				}
				builder.show();
			}
		});

	}

	public static void showFeatureRequiresVuze(final Activity activity,
			final String feature) {
		activity.runOnUiThread(new Runnable() {
			public void run() {
				if (activity.isFinishing()) {
					if (DEBUG) {
						System.out.println("can't display -- finishing");
					}
					return;
				}
				String msg = activity.getResources().getString(R.string.vuze_required,
						feature);
				Builder builder = new AlertDialog.Builder(activity).setMessage(
						msg).setCancelable(true).setPositiveButton(android.R.string.ok,
								new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
					}
				});
				builder.show();
			}
		});

	}

	// ACTION_POWER_CONNECTED
	public static boolean isConnected(Context context) {
		Intent intent = context.registerReceiver(null,
				new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		if (intent == null) {
			return true;
		}
		int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
		return plugged == BatteryManager.BATTERY_PLUGGED_AC
				|| plugged == BatteryManager.BATTERY_PLUGGED_USB;
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

	public static void handleConsoleMessageFroyo(Context ctx, String message,
			String sourceId, int lineNumber, String page) {
		Log.d("console.log",
				message + " -- line " + lineNumber + " of " + sourceId);
		if (message != null && message.startsWith("Uncaught")) {
			if (sourceId == null) {
				sourceId = "unknown";
			}
			if (sourceId.indexOf('/') > 0) {
				sourceId = sourceId.substring(sourceId.lastIndexOf('/'));
			}
			int qPos = sourceId.indexOf('?');
			if (qPos > 0 && sourceId.length() > 1) {
				sourceId = sourceId.substring(0, qPos);
			}
			String s = sourceId + ":" + lineNumber + " " + message.substring(9);
			if (s.length() > 100) {
				s = s.substring(0, 100);
			}
			VuzeEasyTracker.getInstance(ctx).logError(s, page);
		}
	}

	public static void linkify(View view, int widgetId) {
		TextView textview = (TextView) view.findViewById(widgetId);
		if (textview != null) {
			textview.setMovementMethod(LinkMovementMethod.getInstance());
		} else {
			Log.d(TAG, "NO " + widgetId);
		}
	}

	/**
	 * Remove all extras from intent
	 */
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
		public int size;

		public long values[];

		public String strings[];

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

	public static boolean executeSearch(String search, Context context) {
		return executeSearch(search, context, null);
	}

	public static boolean executeSearch(String search, Context context,
			SessionInfo sessionInfo) {
		Intent myIntent = new Intent(Intent.ACTION_SEARCH);
		myIntent.setClass(context, MetaSearch.class);
		RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
		if (remoteProfile != null
				&& remoteProfile.getRemoteType() == RemoteProfile.TYPE_LOOKUP) {
			Bundle bundle = new Bundle();
			bundle.putString("com.vuze.android.remote.searchsource",
					sessionInfo.getRpcRoot());
			if (remoteProfile.getRemoteType() == RemoteProfile.TYPE_LOOKUP) {
				bundle.putString("com.vuze.android.remote.ac", remoteProfile.getAC());
			}
			bundle.putString(SessionInfoManager.BUNDLE_KEY, remoteProfile.getID());

			myIntent.putExtra(SearchManager.APP_DATA, bundle);
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

			return bab.isEmpty() ? false : true;

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

	public static void setSpanBetweenTokens(SpannableString ss, String text,
			String token, CharacterStyle... cs) {
		// Start and end refer to the points where the span will apply
		int tokenLen = token.length();
		int base = 0;

		while (true) {
			int start = text.indexOf(token, base);
			int end = text.indexOf(token, start + tokenLen);

			if (start < 0 || end < 0) {
				break;
			}

			base = end + tokenLen;

			for (CharacterStyle c : cs) {
				ss.setSpan(CharacterStyle.wrap(c), start + tokenLen, end, 0);
			}

			Drawable blankDrawable = new Drawable() {

				@Override
				public void setColorFilter(ColorFilter cf) {
				}

				@Override
				public void setAlpha(int alpha) {
				}

				@Override
				public int getOpacity() {
					return 0;
				}

				@Override
				public void draw(Canvas canvas) {
				}
			};

			// because AbsoluteSizeSpan(0) doesn't work on older versions
			ss.setSpan(new ImageSpan(blankDrawable), start, start + tokenLen, 0);
			ss.setSpan(new ImageSpan(blankDrawable), end, end + tokenLen, 0);
		}
	}

	public static void invalidateOptionsMenuHC(final Activity activity) {
		invalidateOptionsMenuHC(activity,
				(android.support.v7.view.ActionMode) null);
	}

	public static void invalidateOptionsMenuHC(final Activity activity,
			final ActionMode mActionMode) {
		if (activity == null) {
			return;
		}
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (activity instanceof FragmentActivity) {
					FragmentActivity aba = (FragmentActivity) activity;
					aba.supportInvalidateOptionsMenu();
				} else {
					ActivityCompat.invalidateOptionsMenu(activity);
				}
				invalidateActionMode();
			}

			@TargetApi(Build.VERSION_CODES.HONEYCOMB)
			private void invalidateActionMode() {
				if (mActionMode != null) {
					mActionMode.invalidate();
				}
			}
		});
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

				is = new BufferedInputStream(is);
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
	 * Same as {@link Activity#runOnUiThread(Runnable)}, except ensures
	 * activity still exists while in UI Thread, before executing runnable
	 */
	public static void runOnUIThread(
			final android.support.v4.app.Fragment fragment, final Runnable runnable) {
		Activity activity = fragment.getActivity();
		if (activity == null) {
			return;
		}
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Activity activity = fragment.getActivity();
				if (activity == null) {
					return;
				}
				if (runnable instanceof RunnableWithActivity) {
					((RunnableWithActivity) runnable).activity = activity;
				}
				runnable.run();
			}
		});
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

	public static boolean showDialog(DialogFragment dlg, FragmentManager fm,
			String tag) {
		try {
			dlg.show(fm, tag);
			return true;
		} catch (IllegalStateException e) {
			// Activity is no longer active (ie. most likely paused)
			return false;
		}
	}

	public static boolean isAmazonFire() {
		return Build.MODEL.startsWith("AFT");
	}

	public static boolean isTV() {
		if (isTV == null) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
				UiModeManager uiModeManager = (UiModeManager) VuzeRemoteApp.getContext().getSystemService(
						Context.UI_MODE_SERVICE);
				isTV = uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
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

	public static boolean usesNavigationControl() {
		Configuration configuration = VuzeRemoteApp.getContext().getResources().getConfiguration();
		if (configuration.navigation == Configuration.NAVIGATION_NONAV) {
			return false;
		} else if (configuration.touchscreen == Configuration.TOUCHSCREEN_FINGER) {
			return false;
		} else if (configuration.navigation == Configuration.NAVIGATION_DPAD) {
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
}
