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

package com.vuze.android.remote;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.ByteArrayBuffer;

import android.annotation.TargetApi;
import android.app.*;
import android.app.AlertDialog.Builder;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.pm.ComponentInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.Paint.Align;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.*;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.CharacterStyle;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.*;
import android.view.WindowManager.BadTokenException;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.vuze.android.remote.activity.MetaSearch;

/**
 * Some generic Android Utility methods.
 * <p>
 * Some utility methods specific to this app and requiring Android API.
 * Should, and probably should be in their own class.
 */
public class AndroidUtils
{
	public static final boolean DEBUG = true;

	public static final boolean DEBUG_MENU = false;

	private static final String TAG = "Utils";

	private static boolean hasAlertDialogOpen = false;

	private static AlertDialog currentSingleDialog;

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
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1) {
			return setup10(activity, resource);
		} else {
			return setup(activity, resource);
		}

	}

	private static AlertDialogBuilder setup10(Activity activity, int resource) {
		AlertDialog.Builder builder;
		Context c;

		// ContextThemeWrapper needed for <= v10 because there is no AlertDialog.Builder(Context, theme)
		c = new ContextThemeWrapper(activity, android.R.style.Theme_Dialog);
		builder = new AlertDialog.Builder(c);

		View view = View.inflate(c, resource, null);
		builder.setView(view);

		return new AndroidUtils.AlertDialogBuilder(view, builder);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private static AlertDialogBuilder setup(Activity activity, int resource) {
		AlertDialog.Builder builder;
		Context c;

		builder = new AlertDialog.Builder(activity);
		c = activity;

		View view = View.inflate(c, resource, null);
		builder.setView(view);

		return new AndroidUtils.AlertDialogBuilder(view, builder);
	}

	public static void openSingleAlertDialog(Activity ownerActivity,
			AlertDialog.Builder builder) {
		openSingleAlertDialog(ownerActivity, builder, null);
	}

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
			// android.view.WindowManager$BadTokenException: Unable to add window -- token android.os.BinderProxy@42043ff8 is not valid; is your activity running?
			// ignore.  We checked activity.isFinishing() earlier.. not much we can do
			Log.e(TAG, "AlertDialog", bte);
		}
	}

	public static void showConnectionError(Activity activity, Throwable t,
			boolean allowContinue) {
		String message = t.getMessage();
		while (t != null) {
			String name = t.getClass().getName();
			message.replaceAll(name + ": ", "");
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
						R.string.error_connecting).setMessage(errMsg).setCancelable(true).setNegativeButton(
						R.string.action_logout, new DialogInterface.OnClickListener() {
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

	public static void showDialog(Activity activity, int titleID, CharSequence msg) {
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
				Builder builder = new AlertDialog.Builder(activity).setMessage(msg).setCancelable(
						true).setNegativeButton(android.R.string.ok,
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
				Builder builder = new AlertDialog.Builder(activity).setMessage(msg).setCancelable(
						true).setPositiveButton(android.R.string.ok, new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
					}
				});
				builder.show();
			}
		});

	}

	public static boolean isWifiConnected(Context context) {
		ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connManager == null) {
			return false;
		}
		NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		if (mWifi == null) {
			return false;
		}

		return mWifi.isConnected();
	}

	public static boolean isOnlineMobile(Context context) {
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (cm == null) {
			return false;
		}
		NetworkInfo netInfo = cm.getActiveNetworkInfo();
		if (netInfo != null && netInfo.isConnected()) {
			int type = netInfo.getType();
			return type == ConnectivityManager.TYPE_MOBILE || type == 4 //ConnectivityManager.TYPE_MOBILE_DUN
					|| type == 5 //ConnectivityManager.TYPE_MOBILE_HIPRI
					|| type == 2 //ConnectivityManager.TYPE_MOBILE_MMS
					|| type == 3; //ConnectivityManager.TYPE_MOBILE_SUPL;
		}
		return false;
	}

	public static boolean isOnline(Context context) {
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (cm == null) {
			return false;
		}
		NetworkInfo netInfo = cm.getActiveNetworkInfo();
		if (netInfo != null && netInfo.isConnected()) {
			return true;
		}
		return false;
	}

	// ACTION_POWER_CONNECTED
	public static boolean isConnected(Context context) {
		Intent intent = context.registerReceiver(null, new IntentFilter(
				Intent.ACTION_BATTERY_CHANGED));
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
		Log.d("console.log", message + " -- line " + lineNumber + " of " + sourceId);
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
	 * Android doesn't fade out disbaled menu item icons, so do it ourselves
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
		public long values[];

		public String strings[];

		public ValueStringArray(long[] value, String[] string) {
			this.values = value;
			this.strings = string;
		}

	}

	public static ValueStringArray getValueStringArray(Resources resources, int id) {
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
			bundle.putString("com.vuze.android.remote.ac", remoteProfile.getAC());
			myIntent.putExtra(SearchManager.APP_DATA, bundle);
		}
		myIntent.putExtra(SearchManager.QUERY, search);

		context.startActivity(myIntent);
		return true;
	}

	public static boolean isURLAlive(String URLName) {
		try {
			HttpURLConnection.setFollowRedirects(false);

			HttpURLConnection con = (HttpURLConnection) new URL(URLName).openConnection();
			con.setConnectTimeout(5000);
			con.setReadTimeout(5000);
			con.setRequestMethod("HEAD");
			con.getResponseCode();
			if (DEBUG) {
				Log.d(
						TAG,
						"isLive? conn result=" + con.getResponseCode() + ";"
								+ con.getResponseMessage());
			}
			return true;
		} catch (Exception e) {
			if (DEBUG) {
				Log.e(TAG, "isLive", e);
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

	/**
	 * Replaces TextView's text with span bubbles
	 */
	public static void setSpanBubbles(TextView tv, String token,
			final int borderColor, final int textColor, final int fillColor) {
		if (tv == null) {
			return;
		}
		CharSequence text = tv.getText();

		SpannableStringBuilder ss = new SpannableStringBuilder(text);
		String string = text.toString();

		setSpanBubbles(ss, string, token, tv.getPaint(), borderColor, textColor,
				fillColor);
		tv.setText(ss);
	}

	/**
	 * Outputs span bubbles to ss based on text wrapped in token
	 */
	public static void setSpanBubbles(SpannableStringBuilder ss, String text,
			String token, final TextPaint p, final int borderColor,
			final int textColor, final int fillColor) {
		if (ss.length() > 0) {
			// hack so ensure descent is always added by TextView
			ss.append("\u200B");
		}

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

			final String word = text.substring(start + tokenLen, end);

			Drawable imgDrawable = new Drawable() {

				@Override
				public void setColorFilter(ColorFilter cf) {
				}

				@Override
				public void setAlpha(int alpha) {
				}

				@Override
				public int getOpacity() {
					return 255;
				}

				@Override
				public void draw(Canvas canvas) {
					Rect bounds = getBounds();

					Paint paintLine = new Paint(p);
					paintLine.setAntiAlias(true);
					paintLine.setAlpha(255);

					float strokeWidth = paintLine.getStrokeWidth();

					float wIndent = bounds.height() * 0.02f;
					float topIndent = 1;
					float adjY = p.descent();

					RectF rectF = new RectF(bounds.left + wIndent,
							bounds.top + topIndent, bounds.right - (wIndent * 2),
							bounds.bottom + adjY);
					paintLine.setStyle(Paint.Style.FILL);
					paintLine.setColor(fillColor);
					canvas.drawRoundRect(rectF, bounds.height() / 3, bounds.height() / 3,
							paintLine);

					paintLine.setStrokeWidth(2);
					paintLine.setStyle(Paint.Style.STROKE);
					paintLine.setColor(borderColor);
					canvas.drawRoundRect(rectF, bounds.height() / 3, bounds.height() / 3,
							paintLine);

					paintLine.setStrokeWidth(strokeWidth);

					paintLine.setTextAlign(Align.CENTER);
					paintLine.setColor(textColor);
					paintLine.setSubpixelText(true);
					canvas.drawText(word, bounds.left + bounds.width() / 2, -p.ascent(),
							paintLine);
				}
			};

			float w = p.measureText(word + "__");
			float bottom = -p.ascent();
			int y = 0;

			imgDrawable.setBounds(0, y, (int) w, (int) bottom);

			ImageSpan imageSpan = new ImageSpan(imgDrawable,
					DynamicDrawableSpan.ALIGN_BASELINE);

			ss.setSpan(imageSpan, start, end + tokenLen, 0);
		}
	}

	public static void invalidateOptionsMenuHC(final Activity activity) {
		invalidateOptionsMenuHC(activity, (android.support.v7.view.ActionMode) null);
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

		boolean close_input = _close_input_stream;

		try {
			dest = new FileOutputStream(_dest);

			close_input = false;

			copyFile(_source, dest, close_input);

		} finally {

			try {
				if (close_input) {

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
			byte[] startsWith) {

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

	public static void copyUrlToFile(String uri, File outFile) {

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
				copyFile(is, outFile, true);
			}

		} catch (Exception e) {
			VuzeEasyTracker.getInstance().logError(e);
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
		return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
	}

	public static int getCheckedItemCount(ListView listview) {
		if (listview == null) {
			return 0;
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			return getCheckedItemCount_11(listview);
		}
		return getCheckedItemCount_Pre11(listview);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private static int getCheckedItemCount_11(ListView listview) {
		return listview.getCheckedItemCount();
	}

	private static int getCheckedItemCount_Pre11(ListView listview) {
		int total = 0;
		SparseBooleanArray checked = listview.getCheckedItemPositions();
		int size = checked.size(); // number of name-value pairs in the array
		for (int i = 0; i < size; i++) {
			int key = checked.keyAt(i);
			boolean value = checked.get(key);
			if (value) {
				total++;
			}
		}
		return total;
	}

	public static int[] getCheckedPositions(ListView listview) {
		if (listview == null) {
			return new int[0];
		}
		SparseBooleanArray checked = listview.getCheckedItemPositions();
		int size = checked.size(); // number of name-value pairs in the array
		int[] positions = new int[size];
		int pos = 0;
		for (int i = 0; i < size; i++) {
			int position = checked.keyAt(i);
			positions[pos] = position;
			pos++;
		}
		if (pos < size) {
			int[] finalPositions = new int[pos];
			System.arraycopy(positions, 0, finalPositions, 0, pos);
			return finalPositions;
		}
		return positions;
	}

	public static boolean isChecked(ListView listview, int position) {
		if (listview == null) {
			return false;
		}
		SparseBooleanArray checked = listview.getCheckedItemPositions();
		int size = checked.size(); // number of name-value pairs in the array
		for (int i = 0; i < size; i++) {
			int checkedPosition = checked.keyAt(i);
			if (checkedPosition == position) {
				return true;
			}
		}
		return false;
	}

	public static Map<?, ?> getFirstChecked(ListView listview) {
		if (listview == null) {
			return null;
		}
		SparseBooleanArray checked = listview.getCheckedItemPositions();
		int size = checked.size(); // number of name-value pairs in the array
		for (int i = 0; i < size; i++) {
			int key = checked.keyAt(i);
			boolean value = checked.get(key);
			if (value) {
				try {
					return (Map<?, ?>) listview.getItemAtPosition(key);
				} catch (IndexOutOfBoundsException e) {
					// HeaderViewListAdapter will not call our Adapter, but throw OOB
				}
			}
		}
		return null;
	}

	public static void clearChecked(ListView listview) {
		if (listview == null) {
			return;
		}
		SparseBooleanArray checked = listview.getCheckedItemPositions();
		int size = checked.size(); // number of name-value pairs in the array
		for (int i = 0; i < size; i++) {
			int key = checked.keyAt(i);
			boolean value = checked.get(key);
			if (value) {
				listview.setItemChecked(key, false);
			}
		}
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
		return s.replaceAll("([._\\-\\\\/]+)([^\\s])", "\u200B$1\u200B$2").replaceAll(
				"([;\\]])([^\\s])", "$1\u200B$2");
	}

	public static String getCompressedStackTrace() {
		try {
			throw new Exception();
		} catch (Exception e) {
			return getCompressedStackTrace(e, 1, 9);
		}
	}

	public static String getCompressedStackTrace(Throwable t, int startAt,
			int limit) {
		try {
			StackTraceElement[] stackTrace = t.getStackTrace();
			if (stackTrace.length < startAt) {
				return "";
			}
			StringBuffer sb = new StringBuffer("");
			for (int i = startAt; i < stackTrace.length && i < startAt + limit; i++) {
				StackTraceElement element = stackTrace[i];
				String classname = element.getClassName();
				String cnShort;
				boolean showLineNumber = true;
				if (classname.startsWith("com.vuze.android.remote.")) {
					cnShort = classname.substring(24, classname.length());
				} else if (classname.equals("android.os.Handler")) {
					showLineNumber = false;
					cnShort = "Handler";
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
			}
			Throwable cause = t.getCause();
			if (cause != null) {
				sb.append("\n|Cause ");
				sb.append(cause.getClass().getSimpleName());
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

		} catch (Throwable derp) {
			return "derp " + derp.getClass().getSimpleName();
		}
		return null;
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

	public static int indexOfAny(String findIn, String findAnyChar, int startPos) {
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
}
