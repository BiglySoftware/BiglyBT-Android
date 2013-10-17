package com.vuze.android.remote;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.Toast;

public class AndroidUtils
{
	public static final boolean DEBUG = true;

	private static boolean hasAlertDialogOpen = false;

	private static boolean webViewsPaused;

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

	public static void showError(Activity activity, int errMsgID,
			boolean allowContinue) {
		String errMsg = activity.getResources().getString(errMsgID);
		showError(activity, errMsg, allowContinue);
	}

	public static void showError(final Activity activity, final String errMsg,
			final boolean allowContinue) {
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

	public static boolean isWifiConnected(Context context) {
		ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

		return mWifi.isConnected();
	}

	public static boolean isOnline(Context context) {
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
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

		try {
			activity.startActivityForResult(chooserIntent, requestCode);
		} catch (android.content.ActivityNotFoundException ex) {
			Toast.makeText(activity.getApplicationContext(),
					activity.getResources().getString(R.string.no_file_chooser),
					Toast.LENGTH_SHORT).show();
		}

	}

	public static boolean areWebViewsPaused() {
		return webViewsPaused;
	}
	
	public static void setWebViewsPaused(boolean paused) {
		webViewsPaused = paused;
	}
}
