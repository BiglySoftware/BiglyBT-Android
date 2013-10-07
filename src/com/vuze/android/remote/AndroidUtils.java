package com.vuze.android.remote;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.os.Build;
import android.view.ContextThemeWrapper;
import android.view.View;

public class AndroidUtils
{
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

		// Inflate and set the layout for the dialog
		// Pass null as the parent view because its going in the dialog layout
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
}
