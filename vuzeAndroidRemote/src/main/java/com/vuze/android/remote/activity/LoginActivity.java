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

package com.vuze.android.remote.activity;

import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.*;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.vuze.android.remote.*;
import com.vuze.android.remote.dialog.DialogFragmentAbout;
import com.vuze.android.remote.dialog.DialogFragmentGenericRemoteProfile;
import com.vuze.android.remote.dialog.DialogFragmentGenericRemoteProfile.GenericRemoteProfileListener;
import com.vuze.android.remote.spanbubbles.SpanBubbles;

/**
 * TODO: QR Scan button that links to QR reader apps like QR Droid (http://qrdroid.com/android-developers/ )
 */
public class LoginActivity
	extends AppCompatActivity
	implements GenericRemoteProfileListener
{

	private static final String TAG = "LoginActivity";

	private EditText textAccessCode;

	private AppPreferences appPreferences;

	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// These are an attempt to make the gradient look better on some
		// android devices.  It doesn't on the ones I tested, but it can't hurt to
		// have it here, right?
		getWindow().setFormat(PixelFormat.RGBA_8888);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_DITHER);

		super.onCreate(savedInstanceState);

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "LoginActivity intent = " + getIntent() + "/"
					+ getIntent().getDataString());
		}

		appPreferences = VuzeRemoteApp.getAppPreferences();

		setContentView(R.layout.activity_login);

		textAccessCode = (EditText) findViewById(R.id.editTextAccessCode);

		RemoteProfile lastUsedRemote = appPreferences.getLastUsedRemote();
		if (lastUsedRemote != null
				&& lastUsedRemote.getRemoteType() == RemoteProfile.TYPE_LOOKUP
				&& lastUsedRemote.getAC() != null) {
			textAccessCode.setText(lastUsedRemote.getAC());
			textAccessCode.selectAll();
		}
		textAccessCode.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				loginButtonClicked(v);
				return true;
			}
		});

		TextView tvLoginCopyright = (TextView) findViewById(R.id.login_copyright);
		if (tvLoginCopyright != null) {
			tvLoginCopyright.setMovementMethod(LinkMovementMethod.getInstance());
		}

		TextView tvLoginGuide = (TextView) findViewById(R.id.login_guide);
		setupGuideText(tvLoginGuide);
		TextView tvLoginGuide2 = (TextView) findViewById(R.id.login_guide2);
		setupGuideText(tvLoginGuide2);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayShowHomeEnabled(true);
			actionBar.setIcon(R.drawable.ic_launcher);
		}
	}

	private void setupGuideText(TextView tvLoginGuide) {
		tvLoginGuide.setMovementMethod(LinkMovementMethod.getInstance());
		CharSequence text = tvLoginGuide.getText();

		SpannableStringBuilder ss = new SpannableStringBuilder(text);
		String string = text.toString();

		new SpanBubbles().setSpanBubbles(ss, string, "|", tvLoginGuide.getPaint(),
				ContextCompat.getColor(this, R.color.login_text_color),
				ContextCompat.getColor(this, R.color.login_textbubble_color),
				ContextCompat.getColor(this, R.color.login_text_color));

		int indexOf = string.indexOf("@@");
		if (indexOf >= 0) {
			int style = ImageSpan.ALIGN_BASELINE;
			int newHeight = tvLoginGuide.getBaseline();
			if (newHeight <= 0) {
				newHeight = tvLoginGuide.getLineHeight();
				style = ImageSpan.ALIGN_BOTTOM;
				if (newHeight <= 0) {
					newHeight = 20;
				}
			}
			Drawable drawable = ContextCompat.getDrawable(this,
					R.drawable.guide_icon);
			int oldWidth = drawable.getIntrinsicWidth();
			int oldHeight = drawable.getIntrinsicHeight();
			int newWidth = (oldHeight > 0) ? (oldWidth * newHeight) / oldHeight
					: newHeight;
			drawable.setBounds(0, 0, newWidth, newHeight);

			ImageSpan imageSpan = new ImageSpan(drawable, style);
			ss.setSpan(imageSpan, indexOf, indexOf + 2, 0);
		}

		tvLoginGuide.setText(ss);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			setBackgroundGradient();
		}
	}

	@SuppressWarnings("deprecation")
	private void setBackgroundGradient() {

		ViewGroup mainLayout = (ViewGroup) findViewById(R.id.main_loginlayout);
		int h = mainLayout.getHeight();
		int w = mainLayout.getWidth();
		View viewCenterOn = findViewById(R.id.login_frog_logo);
		int top = viewCenterOn.getTop() + (viewCenterOn.getHeight() / 2);

		RectShape shape = new RectShape();
		ShapeDrawable mDrawable = new ShapeDrawable(shape);
		RadialGradient shader = new RadialGradient(w / 2, top, w * 2 / 3,
				getResources().getColor(R.color.login_grad_color_1),
				getResources().getColor(R.color.login_grad_color_2),
				Shader.TileMode.CLAMP);
		mDrawable.setBounds(0, 0, w, h);
		mDrawable.getPaint().setShader(shader);
		mDrawable.getPaint().setDither(true);
		mDrawable.getPaint().setAntiAlias(true);
		mDrawable.setDither(true);

		mainLayout.setDrawingCacheEnabled(true);
		mainLayout.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
		mainLayout.setAnimationCacheEnabled(false);

		mainLayout.setBackgroundDrawable(mDrawable);
	}

	@Override
	public void onAttachedToWindow() {
		super.onAttachedToWindow();
		getWindow().setFormat(PixelFormat.RGBA_8888);
	}

	@Override
	protected void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).activityStart(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		VuzeEasyTracker.getInstance(this).activityStop(this);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_login, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		int itemId = item.getItemId();
		if (itemId == R.id.action_adv_login) {
			DialogFragmentGenericRemoteProfile dlg = new DialogFragmentGenericRemoteProfile();
			AndroidUtils.showDialog(dlg, getSupportFragmentManager(),
					"GenericRemoteProfile");
			return true;
		} else if (itemId == R.id.action_about) {
			DialogFragmentAbout dlg = new DialogFragmentAbout();
			AndroidUtils.showDialog(dlg, getSupportFragmentManager(), "About");
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	public void loginButtonClicked(View v) {
		final String ac = textAccessCode.getText().toString().replaceAll(
				"[^a-zA-Z0-9]", "");
		appPreferences.setLastRemote(null);

		RemoteProfile remoteProfile = new RemoteProfile("vuze", ac);
		new RemoteUtils(this).openRemote(remoteProfile, false);
	}

	@Override
	public void profileEditDone(RemoteProfile oldProfile,
			RemoteProfile newProfile) {
		new RemoteUtils(this).openRemote(newProfile, false);
	}

}
