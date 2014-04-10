package com.vuze.android.remote.activity;

import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.WindowManager;
import android.widget.MediaController;
import android.widget.VideoView;

import com.vuze.android.remote.AndroidUtils;
import com.vuze.android.remote.R;

public class VideoViewer
	extends ActionBarActivity
{

	private static final String TAG = "VideoViewer";
	protected boolean hasError;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getSupportActionBar().hide();

		setContentView(R.layout.video_view);

		final VideoView videoView = (VideoView) findViewById(R.id.videoView1);

		videoView.setVideoURI(getIntent().getData());

		MediaController mediaController = new MediaController(this);
		mediaController.setAnchorView(videoView);
		videoView.setMediaController(mediaController);
		
		videoView.setOnErrorListener(new OnErrorListener() {
			@Override
			public boolean onError(MediaPlayer mp, int what, int extra) {
				hasError = true;
				// Return false here so VideoView shows error dialog
				return false;
			}
		});
		
		videoView.setOnCompletionListener(new OnCompletionListener() {
			@Override
			public void onCompletion(MediaPlayer mp) {
				if (hasError) {
					finish();
				}
			}
		});

		videoView.start();
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "PLAY " + getIntent().getData());
		}

	}
}
