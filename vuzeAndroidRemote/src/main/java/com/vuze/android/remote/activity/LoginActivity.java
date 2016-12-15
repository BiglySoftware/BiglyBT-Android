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

import com.vuze.android.remote.*;
import com.vuze.android.remote.dialog.DialogFragmentAbout;
import com.vuze.android.remote.dialog.DialogFragmentGenericRemoteProfile;
import com.vuze.android.remote.dialog.DialogFragmentGenericRemoteProfile.GenericRemoteProfileListener;
import com.vuze.android.remote.session.RemoteProfile;
import com.vuze.android.remote.spanbubbles.SpanBubbles;
import com.vuze.util.Thunk;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.*;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

/**
 * TODO: QR Scan button that links to QR reader apps like QR Droid (http://qrdroid.com/android-developers/ )
 */
public class LoginActivity
	extends AppCompatActivityM
	implements GenericRemoteProfileListener
{

	private static final String TAG = "LoginActivity";

	private EditText textAccessCode;

	@Thunk
	Button loginButton;

	private AppPreferences appPreferences;

	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// These are an attempt to make the gradient look better on some
		// android devices.  It doesn't on the ones I tested, but it can't hurt to
		// have it here, right?
		getWindow().setFormat(PixelFormat.RGBA_8888);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_DITHER);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			Window w = getWindow(); // in Activity's onCreate() for instance
			w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
					WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				w.addFlags(
						WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
				w.setNavigationBarColor(
						ContextCompat.getColor(this, R.color.login_grad_color_2));
			}
		}

		AndroidUtilsUI.onCreate(this, TAG);
		super.onCreate(savedInstanceState);

		appPreferences = VuzeRemoteApp.getAppPreferences();

		setContentView(R.layout.activity_login);

		textAccessCode = (EditText) findViewById(R.id.editTextAccessCode);
		assert textAccessCode != null;

		loginButton = (Button) findViewById(R.id.login_button);

		RemoteProfile lastUsedRemote = appPreferences.getLastUsedRemote();
		if (lastUsedRemote != null
				&& lastUsedRemote.getRemoteType() == RemoteProfile.TYPE_LOOKUP
				&& lastUsedRemote.getAC() != null) {
			textAccessCode.setText(lastUsedRemote.getAC());
			textAccessCode.selectAll();
		}

		Editable s = textAccessCode.getText();
		loginButton.setEnabled(s.length() > 0);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			loginButton.setAlpha(s.length() == 0 ? 0.2f : 1.0f);
		}

		textAccessCode.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				loginButtonClicked(v);
				return true;
			}
		});
		textAccessCode.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {

			}

			@Override
			public void afterTextChanged(Editable s) {
				loginButton.setEnabled(s.length() > 0);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
					loginButton.setAlpha(s.length() == 0 ? 0.2f : 1.0f);
				}
			}
		});

		TextView tvLoginCopyright = (TextView) findViewById(R.id.login_copyright);
		if (tvLoginCopyright != null) {
			AndroidUtilsUI.linkify(tvLoginCopyright);
		}

		TextView tvLoginGuide = (TextView) findViewById(R.id.login_guide);
		setupGuideText(tvLoginGuide);
		tvLoginGuide.setFocusable(false);
		TextView tvLoginGuide2 = (TextView) findViewById(R.id.login_guide2);
		setupGuideText(tvLoginGuide2);

		View coreArea = findViewById(R.id.login_core_area);
		if (coreArea != null) {
			coreArea.setVisibility(
					VuzeRemoteApp.isCoreAllowed() ? View.VISIBLE : View.GONE);
		}

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayShowHomeEnabled(true);
			actionBar.setIcon(R.drawable.ic_launcher);
		}
	}

	private void setupGuideText(TextView tvLoginGuide) {
		AndroidUtilsUI.linkify(tvLoginGuide);
		CharSequence text = tvLoginGuide.getText();

		SpannableStringBuilder ss = new SpannableStringBuilder(text);
		String string = text.toString();

		new SpanBubbles().setSpanBubbles(ss, string, "|", tvLoginGuide.getPaint(),
				AndroidUtilsUI.getStyleColor(this, R.attr.login_text_color),
				AndroidUtilsUI.getStyleColor(this, R.attr.login_textbubble_color),
				AndroidUtilsUI.getStyleColor(this, R.attr.login_text_color), null);

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

	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			setBackgroundGradient();
		}
	}

	@SuppressWarnings("deprecation")
	private void setBackgroundGradient() {

		ViewGroup mainLayout = (ViewGroup) findViewById(R.id.main_loginlayout);
		assert mainLayout != null;
		int h = mainLayout.getHeight();
		int w = mainLayout.getWidth();
		View viewCenterOn = findViewById(R.id.login_frog_logo);
		assert viewCenterOn != null;

		RectShape shape = new RectShape();
		ShapeDrawable mDrawable = new ShapeDrawable(shape);
		int color1 = AndroidUtilsUI.getStyleColor(this, R.attr.login_grad_color_1);
		int color2 = AndroidUtilsUI.getStyleColor(this, R.attr.login_grad_color_2);

		RadialGradient shader;
		if (w > h) {
			int left = viewCenterOn.getLeft() + (viewCenterOn.getWidth() / 2);
			int top = viewCenterOn.getTop() + (viewCenterOn.getHeight() / 2);
			int remaining = w - left;
			shader = new RadialGradient(left, top, remaining, new int[] {
				color1,
				color2
			}, new float[] {
				0,
				1.0f
			}, Shader.TileMode.CLAMP);
		} else {
			int top = viewCenterOn.getTop() + (viewCenterOn.getHeight() / 2);
			shader = new RadialGradient(w / 2, top, w * 2 / 3, color1, color2,
					Shader.TileMode.CLAMP);
		}
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
		if (itemId == R.id.action_add_adv_profile) {
			DialogFragmentGenericRemoteProfile dlg = new DialogFragmentGenericRemoteProfile();
			AndroidUtilsUI.showDialog(dlg, getSupportFragmentManager(),
					"GenericRemoteProfile");
			return true;
		} else if (itemId == R.id.action_about) {
			DialogFragmentAbout dlg = new DialogFragmentAbout();
			AndroidUtilsUI.showDialog(dlg, getSupportFragmentManager(), "About");
			return true;
		} else if (itemId == R.id.action_import_prefs) {
			AndroidUtils.openFileChooser(this, "application/octet-stream",
					TorrentViewActivity.FILECHOOSER_RESULTCODE);
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onActivityResult: " + requestCode + "/" + resultCode);
		}
		if (requestCode == TorrentViewActivity.FILECHOOSER_RESULTCODE) {
			Uri uri = intent == null || resultCode != Activity.RESULT_OK ? null
					: intent.getData();
			if (uri == null) {
				return;
			}
			AppPreferences.importPrefs(this, uri);
			if (appPreferences.getNumRemotes() > 0) {
				RemoteUtils.openRemoteList(this);
			}
		}
	}

	@SuppressWarnings("UnusedParameters")
	public void loginButtonClicked(View v) {
		final String ac = textAccessCode.getText().toString().replaceAll(
				"[^a-zA-Z0-9]", "");
		appPreferences.setLastRemote(null);

		RemoteProfile remoteProfile = new RemoteProfile("vuze", ac);
		RemoteUtils.openRemote(this, remoteProfile, false);
	}

	@SuppressWarnings("UnusedParameters")
	public void startTorrentingButtonClicked(View view) {
		RemoteProfile[] remotes = appPreferences.getRemotes();
		RemoteProfile coreProfile = null;
		for (RemoteProfile remoteProfile : remotes) {
			if (remoteProfile.getRemoteType() == RemoteProfile.TYPE_CORE) {
				coreProfile = remoteProfile;
				break;
			}
		}
		if (coreProfile == null) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "Adding localhost profile..");
			}
			RemoteUtils.createCoreProfile(this,
					new RemoteUtils.OnCoreProfileCreated() {
						@Override
						public void onCoreProfileCreated(RemoteProfile coreProfile) {
							RemoteUtils.editProfile(coreProfile, getSupportFragmentManager());
						}
					});
		} else {
			RemoteUtils.editProfile(coreProfile, getSupportFragmentManager());
		}
	}

	@Override
	public void profileEditDone(RemoteProfile oldProfile,
			RemoteProfile newProfile) {
		RemoteUtils.openRemote(this, newProfile, false);
	}

}
