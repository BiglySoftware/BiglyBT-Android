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

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;

import com.biglybt.android.client.BiglyBTApp;
import com.biglybt.android.client.OffThread;
import com.biglybt.android.client.R;

public class CustomToast
{
	public static void showText(@StringRes int textRedId, final int duration) {
		OffThread.runOnUIThread(() -> ui_showText(
				BiglyBTApp.getContext().getResources().getString(textRedId), duration));
	}

	@AnyThread
	public static void showText(final CharSequence text, final int duration) {
		OffThread.runOnUIThread(() -> ui_showText(text, duration));
	}

	@UiThread
	private static void ui_showText(final CharSequence text, final int duration) {
		try {
			Context context = BiglyBTApp.getContext();

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
}