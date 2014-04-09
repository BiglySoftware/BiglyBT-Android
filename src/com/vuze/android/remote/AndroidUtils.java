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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpProtocolParams;

import android.annotation.TargetApi;
import android.app.*;
import android.app.AlertDialog.Builder;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.Paint.Align;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.*;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.text.Html;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.CharacterStyle;
import android.text.style.ImageSpan;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.*;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.aelitis.azureus.util.MapUtils;
import com.vuze.android.remote.activity.MetaSearch;
import com.vuze.android.remote.dialog.DialogFragmentMoveData;
import com.vuze.android.remote.dialog.DialogFragmentSessionSettings;

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
		AlertDialog.Builder builder;
		Context c;
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1) {
			// ContextThemeWrapper needed for <= v10 because there is no AlertDialog.Builder(Context, theme)
			c = new ContextThemeWrapper(activity, android.R.style.Theme_Dialog);
			builder = new AlertDialog.Builder(c);
		} else {
			builder = new AlertDialog.Builder(activity);
			c = activity;
		}

		View view = View.inflate(c, resource, null);
		builder.setView(view);

		return new AndroidUtils.AlertDialogBuilder(view, builder);
	}

	public static void openSingleAlertDialog(AlertDialog.Builder builder) {
		openSingleAlertDialog(builder, null);
	}

	public static void openSingleAlertDialog(AlertDialog.Builder builder,
			final OnDismissListener dismissListener) {
		// We should always be on the UI Thread, so no need to synchronize
		if (hasAlertDialogOpen) {
			return;
		}

		hasAlertDialogOpen = true;
		AlertDialog show = builder.show();
		// Note: There's a builder.setOnDismissListener(), but it's API 17
		show.setOnDismissListener(new OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialog) {
				hasAlertDialogOpen = false;
				if (dismissListener != null) {
					dismissListener.onDismiss(dialog);
				}
			}
		});
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
									new RemoteUtils(activity).openRemoteList(activity.getIntent());
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
				AndroidUtils.openSingleAlertDialog(builder);
			}
		});

	}

	public static void showDialog(Activity activity, int titleID, String msg) {
		String title = activity.getResources().getString(titleID);
		showDialog(activity, title, msg);
	}

	public static void showDialog(final Activity activity, final String title,
			final String msg) {
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
								if (activity.isTaskRoot()) {
									new RemoteUtils(activity).openRemoteList(activity.getIntent());
								}
								activity.finish();
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
			VuzeEasyTracker.getInstance(ctx).logError(ctx, s, page);
		}
	}

	public static void linkify(View view, int widgetId, int textId) {
		TextView textview = (TextView) view.findViewById(widgetId);
		if (textview != null) {
			textview.setMovementMethod(LinkMovementMethod.getInstance());
			textview.setText(Html.fromHtml(view.getResources().getString(textId)));
		}
	}

	public static void linkify(Activity view, int widgetId, int textId) {
		TextView textview = (TextView) view.findViewById(widgetId);
		if (textview != null) {
			textview.setMovementMethod(LinkMovementMethod.getInstance());
			textview.setText(Html.fromHtml(view.getResources().getString(textId)));
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

	@SuppressWarnings("rawtypes")
	public static void openMoveDataDialog(Map mapTorrent,
			SessionInfo sessionInfo, FragmentManager fm) {
		DialogFragmentMoveData dlg = new DialogFragmentMoveData();
		Bundle bundle = new Bundle();
		if (mapTorrent == null) {
			return;
		}

		bundle.putLong("id", MapUtils.getMapLong(mapTorrent, "id", -1));
		bundle.putString("name", "" + mapTorrent.get("name"));

		String defaultDownloadDir = sessionInfo.getSessionSettings().getDownloadDir();
		String downloadDir = MapUtils.getMapString(mapTorrent, "downloadDir",
				defaultDownloadDir);
		bundle.putString("downloadDir", downloadDir);
		ArrayList<String> history = new ArrayList<String>();
		if (defaultDownloadDir != null) {
			history.add(defaultDownloadDir);
		}

		List<String> saveHistory = sessionInfo.getRemoteProfile().getSavePathHistory();
		for (String s : saveHistory) {
			if (!history.contains(s)) {
				history.add(s);
			}
		}
		bundle.putStringArrayList("history", history);
		dlg.setArguments(bundle);
		dlg.show(fm, "MoveDataDialog");
	}

	public static boolean executeSearch(String search, Context context) {
		return executeSearch(search, context, null, null);
	}

	public static boolean executeSearch(String search, Context context,
			RemoteProfile remoteProfile, String rpcRoot) {
		Intent myIntent = new Intent(Intent.ACTION_SEARCH);
		myIntent.setClass(context, MetaSearch.class);
		if (remoteProfile.getRemoteType() == RemoteProfile.TYPE_LOOKUP) {
			Bundle bundle = new Bundle();
			bundle.putString("com.vuze.android.remote.searchsource", rpcRoot);
			bundle.putString("com.vuze.android.remote.ac", remoteProfile.getAC());
			myIntent.putExtra(SearchManager.APP_DATA, bundle);
		}
		myIntent.putExtra(SearchManager.QUERY, search);

		context.startActivity(myIntent);
		return true;
	}

	public static void showSessionSettings(FragmentManager fm,
			SessionInfo sessionInfo) {
		if (sessionInfo == null) {
			return;
		}
		DialogFragmentSessionSettings dlg = new DialogFragmentSessionSettings();
		Bundle bundle = new Bundle();
		String id = sessionInfo.getRemoteProfile().getID();
		bundle.putString(SessionInfoManager.BUNDLE_KEY, id);
		dlg.setArguments(bundle);
		dlg.show(fm, "SessionSettings");
	}

	public static boolean isURLAlive(String URLName) {
		try {
			HttpURLConnection.setFollowRedirects(false);

			HttpURLConnection con = (HttpURLConnection) new URL(URLName).openConnection();
			con.setConnectTimeout(2000);
			con.setReadTimeout(2000);
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

	public static void setSpanBubbles(SpannableString ss, String text,
			String token, final TextPaint p, final int borderColor,
			final int textColor, final int fillColor) {
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

					Paint paintLine = new Paint();
					paintLine.setAntiAlias(true);

					int strokeWidth = 2;

					float wIndent = bounds.height() * 0.02f;
					float topIndent = bounds.height() * 0.02f;
					float adjY = p.descent();

					RectF rectF = new RectF(bounds.left + wIndent, bounds.top + topIndent
							+ adjY, bounds.right - (wIndent * 2), bounds.bottom);
					paintLine.setStyle(Paint.Style.FILL);
					paintLine.setColor(fillColor);
					canvas.drawRoundRect(rectF, bounds.height() / 3, bounds.height() / 3,
							paintLine);

					paintLine.setStrokeWidth(strokeWidth);
					paintLine.setStyle(Paint.Style.STROKE);
					paintLine.setColor(borderColor);
					canvas.drawRoundRect(rectF, bounds.height() / 3, bounds.height() / 3,
							paintLine);

					Align textAlign = p.getTextAlign();
					p.setTextAlign(Align.CENTER);
					int oldColor = p.getColor();
					p.setColor(textColor);
					canvas.drawText(word, bounds.left + bounds.width() / 2, bounds.bottom
							- adjY, p);
					p.setColor(oldColor);
					p.setTextAlign(textAlign);
				}
			};

			float w = p.measureText(word + "  ");
			float h = p.descent() - p.ascent();
			imgDrawable.setBounds(0, 0, (int) w, (int) (h * 1.2));

			ss.setSpan(new ImageSpan(imgDrawable), start, end + tokenLen, 0);
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
			VuzeEasyTracker.getInstance().logError(null, e);
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
			String s = "";
			StackTraceElement[] stackTrace = t.getStackTrace();
			if (stackTrace.length < startAt) {
				return "";
			}
			for (int i = startAt; i < stackTrace.length && i < startAt + limit; i++) {
				StackTraceElement element = stackTrace[i];
				String classname = element.getClassName();
				String cnShort;
				if (classname.startsWith("com.vuze.android.remote.")) {
					cnShort = classname.substring(24, classname.length());
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
					s += ", ";
				}
				s += cnShort + "." + element.getMethodName() + ":"
						+ element.getLineNumber();
			}
			Throwable cause = t.getCause();
			if (cause != null) {
				s += "\nCause: " + getCompressedStackTrace(cause, 0, 9);
			}
			return s;
		} catch (Throwable derp) {
			return "derp " + derp.getClass().getSimpleName();
		}
	}
}
