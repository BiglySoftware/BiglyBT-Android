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

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.R;
import com.biglybt.util.Thunk;

import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import android.util.Log;
import android.view.WindowManager;
import android.widget.MediaController;
import android.widget.VideoView;

public class VideoViewer
	extends ThemedActivity
{

	private static final String TAG = "VideoViewer";

	@Thunk
	boolean hasError;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			getSupportActionBar().hide();
		}

		setContentView(R.layout.video_view);

		final VideoView videoView = findViewById(R.id.videoView1);

		videoView.setVideoURI(getIntent().getData());

		MediaController mediaController = new MediaController(this);
		mediaController.setAnchorView(videoView);
		videoView.setMediaController(mediaController);

		videoView.setOnErrorListener((mp, what, extra) -> {
			hasError = true;
			// Return false here so VideoView shows error dialog
			return false;
		});

		videoView.setOnCompletionListener(mp -> {
			if (hasError) {
				finish();
			}
		});

		videoView.start();
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "PLAY " + getIntent().getData());
		}
	}
}
