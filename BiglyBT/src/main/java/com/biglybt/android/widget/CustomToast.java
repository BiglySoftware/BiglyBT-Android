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

package com.biglybt.android.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.FragmentActivity;

import com.biglybt.android.client.*;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionManager;

public class CustomToast
	extends Toast
{
	public CustomToast(Context context) {
		super(context);
	}

	public static void showText(@StringRes int textRedId, final int duration) {
		OffThread.runOnUIThread(() -> showText(
				BiglyBTApp.getContext().getResources().getString(textRedId), duration));
	}

	// TODO: Ensure user gets message when notifications are disabled
	@AnyThread
	public static void showText(final CharSequence text, final int duration) {
		OffThread.runOnUIThread(() -> ui_showText(text, duration));
	}

	@UiThread
	private static void ui_showText(final CharSequence text, final int duration) {
		try {
			Context context = BiglyBTApp.getContext();

			boolean enabled = NotificationManagerCompat.from(
					context).areNotificationsEnabled();
			if (!enabled) {
				Log.w("Toast", "Skipping toast: " + text);
				if (AndroidUtils.DEBUG) {
					Session session = SessionManager.getActiveSession();
					if (session != null) {
						FragmentActivity activity = session.getCurrentActivity();
						if (activity != null) {
							AndroidUtilsUI.showDialog(activity, 0, R.string.hardcoded_string,
									text);
						}
					}
				}
				return;
			}

			@SuppressLint("ShowToast") //NON-NLS
			Toast t = Toast.makeText(context, text, duration);
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(
					Context.LAYOUT_INFLATER_SERVICE);
			if (inflater == null) {
				return;
			}
			View layout = inflater.inflate(R.layout.custom_toast, null);

			TextView textView = layout.findViewById(R.id.text);
			textView.setText(text);

			t.setView(layout);

			t.show();
		} catch (Throwable t) {
			Log.e("TOAST", "Can't show toast " + text, t);
		}
	}

	public static Toast makeText(Context context, CharSequence text,
			int duration) {
		@SuppressLint("ShowToast") //NON-NLS
		Toast t = Toast.makeText(context, text, duration);
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);
		if (inflater == null) {
			return t;
		}
		View layout = inflater.inflate(R.layout.custom_toast, null);

		TextView textView = layout.findViewById(R.id.text);
		textView.setText(text);

		t.setView(layout);

		return t;
	}

}