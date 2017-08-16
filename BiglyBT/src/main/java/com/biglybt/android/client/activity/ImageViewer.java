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

package com.biglybt.android.client.activity;

import java.net.MalformedURLException;
import java.net.URL;

import com.biglybt.android.client.*;
import com.biglybt.android.widget.UrlImageView;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.WindowManager;

/**
 * Very simple view that shows an image.  This is a fallback, in case the user
 * doesn't not have a registered image intent
 */
public class ImageViewer
	extends AppCompatActivity
{

	private static final String TAG = "ImageViewer";

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		AndroidUtilsUI.onCreate(this, TAG);

		super.onCreate(savedInstanceState);

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			getSupportActionBar().hide();
		}

		setContentView(R.layout.image_view);

		final UrlImageView imageView = (UrlImageView) findViewById(R.id.imageView1);

		try {
			if (imageView != null) {
				imageView.setImageURL(new URL(getIntent().getData().toString()));
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "SHOW " + getIntent().getData());
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		AnalyticsTracker.getInstance(this).activityResume(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		AnalyticsTracker.getInstance(this).activityPause(this);
	}
}
