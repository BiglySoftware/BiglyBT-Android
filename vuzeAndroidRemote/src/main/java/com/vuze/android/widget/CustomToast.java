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

package com.vuze.android.widget;

import com.vuze.android.remote.R;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class CustomToast
	extends Toast
{
	public CustomToast(Context context) {
		super(context);
	}

	public static Toast makeText(Context context, CharSequence text,
			int duration) {
		@SuppressLint("ShowToast")
		Toast t = Toast.makeText(context, text, duration);
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);
		View layout = inflater.inflate(R.layout.custom_toast, null);

		TextView textView = (TextView) layout.findViewById(R.id.text);
		textView.setText(text);

		t.setView(layout);

		return t;
	}

}