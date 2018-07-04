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

import com.biglybt.android.client.*;
import com.biglybt.android.client.dialog.DialogFragmentAbout;
import com.biglybt.android.client.dialog.DialogFragmentGenericRemoteProfile;
import com.biglybt.android.client.dialog.DialogFragmentGenericRemoteProfile.GenericRemoteProfileListener;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.client.session.RemoteProfileFactory;
import com.biglybt.android.client.spanbubbles.SpanBubbles;
import com.biglybt.android.util.FileUtils;
import com.biglybt.util.Thunk;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.*;
import android.widget.*;

/**
 * TODO: QR Scan button that links to QR reader apps like QR Droid (http://qrdroid.com/android-developers/ )
 */
public class LoginActivity
	extends ThemedActivity
	implements GenericRemoteProfileListener
{

	private static final String TAG = "LoginActivity";

	private static final boolean TEST_GDPR_DIALOGS = AndroidUtils.DEBUG;

	public static final String PREF_ASKED_LOOKUP_GDPR = "asked.gdpr.code.lookup";

	public static final String PREF_ASKED_CORE_GDPR = "asked.gdpr.core";

	private EditText textAccessCode;

	@Thunk
	Button loginButton;

	private AppPreferences appPreferences;

	@Thunk
	ViewSwitcher viewSwitcher;

	@Override
	protected String getTag() {
		return TAG;
	}

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

		super.onCreate(savedInstanceState);

		appPreferences = BiglyBTApp.getAppPreferences();

		setContentView(R.layout.activity_login);

		textAccessCode = findViewById(R.id.editTextAccessCode);

		View clickCore = findViewById(R.id.login_server);
		View clickRemote = findViewById(R.id.login_remote);

		viewSwitcher = findViewById(R.id.login_switcher);
		viewSwitcher.setOutAnimation(this, R.anim.slide_out_left);
		viewSwitcher.setInAnimation(this, R.anim.slide_in_right);

		if (clickCore != null) {
			clickCore.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					startTorrentingButtonClicked(v);
				}
			});
		}
		if (clickRemote != null) {
			clickRemote.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					viewSwitcher.showNext();
					viewSwitcher.setOutAnimation(LoginActivity.this,
							R.anim.slide_out_right);
					viewSwitcher.setInAnimation(LoginActivity.this, R.anim.slide_in_left);
				}
			});
		}

		loginButton = findViewById(R.id.login_button);

		RemoteProfile lastUsedRemote = appPreferences.getLastUsedRemote();
		if (lastUsedRemote != null
				&& lastUsedRemote.getRemoteType() == RemoteProfile.TYPE_LOOKUP
				&& lastUsedRemote.getAC() != null) {
			textAccessCode.setText(lastUsedRemote.getAC());
			textAccessCode.selectAll();
		}

		Editable s = textAccessCode.getText();
		loginButton.setEnabled(s.length() > 0);
		loginButton.setAlpha(s.length() == 0 ? 0.2f : 1.0f);

		textAccessCode.setOnEditorActionListener(
				new TextView.OnEditorActionListener() {
					public boolean onEditorAction(TextView v, int actionId,
							KeyEvent event) {
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
				loginButton.setAlpha(s.length() == 0 ? 0.2f : 1.0f);
			}
		});

		TextView tvLoginGuide = findViewById(R.id.login_guide);
		if (tvLoginGuide != null) {
			setupGuideText(tvLoginGuide, R.string.login_guide);
			tvLoginGuide.setFocusable(false);
		}
		TextView tvLoginGuide2 = findViewById(R.id.login_guide2);
		setupGuideText(tvLoginGuide2, R.string.login_guide2);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayShowHomeEnabled(true);
			actionBar.setIcon(R.drawable.biglybt_logo_toolbar);
		}
	}

	@Override
	public void onBackPressed() {
		if (viewSwitcher != null && viewSwitcher.getDisplayedChild() == 1) {
			viewSwitcher.showPrevious();
			viewSwitcher.setOutAnimation(this, R.anim.slide_out_left);
			viewSwitcher.setInAnimation(this, R.anim.slide_in_right);
			return;
		}
		super.onBackPressed();
	}

	private void setupGuideText(TextView tv, @StringRes int textID) {
		if (tv == null) {
			return;
		}
		AndroidUtilsUI.linkify(this, tv, null, textID);
		CharSequence text = tv.getText();

		SpannableStringBuilder ss = new SpannableStringBuilder(text);
		String string = text.toString();

		SpanBubbles.setSpanBubbles(ss, string, "|", tv.getPaint(),
				AndroidUtilsUI.getStyleColor(this, R.attr.login_text_color),
				AndroidUtilsUI.getStyleColor(this, R.attr.login_textbubble_color),
				AndroidUtilsUI.getStyleColor(this, R.attr.login_text_color), null);

		int indexOf = string.indexOf("@@");
		if (indexOf >= 0) {
			int style = ImageSpan.ALIGN_BASELINE;
			int newHeight = tv.getBaseline();
			if (newHeight <= 0) {
				newHeight = tv.getLineHeight();
				style = ImageSpan.ALIGN_BOTTOM;
				if (newHeight <= 0) {
					newHeight = 20;
				}
			}
			Drawable drawable = ContextCompat.getDrawable(this,
					R.drawable.guide_icon);
			if (drawable != null) {
				int oldWidth = drawable.getIntrinsicWidth();
				int oldHeight = drawable.getIntrinsicHeight();
				int newWidth = (oldHeight > 0) ? (oldWidth * newHeight) / oldHeight
						: newHeight;
				drawable.setBounds(0, 0, newWidth, newHeight);

				ImageSpan imageSpan = new ImageSpan(drawable, style);
				ss.setSpan(imageSpan, indexOf, indexOf + 2, 0);
			}
		}

		tv.setText(ss);
	}

	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			try {
				setBackgroundGradient();
			} catch (Throwable ignore) {
			}
		}
	}

	@SuppressWarnings("deprecation")
	private void setBackgroundGradient() {

		ViewGroup mainLayout = findViewById(R.id.main_loginlayout);
		assert mainLayout != null;
		int h = mainLayout.getHeight();
		int w = mainLayout.getWidth();
		View viewCenterOn = findViewById(R.id.login_logo);
		assert viewCenterOn != null;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			LayoutTransition layoutTransition = mainLayout.getLayoutTransition();
			layoutTransition.enableTransitionType(LayoutTransition.CHANGING);

			ViewGroup ll = findViewById(R.id.login_logo_layout);
			if (ll != null) {
				LayoutTransition layoutTransition1 = ll.getLayoutTransition();
				if (layoutTransition1 != null) {
					layoutTransition1.enableTransitionType(LayoutTransition.CHANGING);
				}
			}
		}

		RectShape shape = new RectShape();
		ShapeDrawable mDrawable = new ShapeDrawable(shape);
		int color1 = AndroidUtilsUI.getStyleColor(this, R.attr.login_grad_color_1);
		int color2 = AndroidUtilsUI.getStyleColor(this, R.attr.login_grad_color_2);

		int[] pos = new int[2];
		viewCenterOn.getLocationOnScreen(pos);
		int left = pos[0] + (viewCenterOn.getWidth() / 2);
		int top = pos[1] + (viewCenterOn.getHeight() / 2);
		int radius = Math.max(viewCenterOn.getWidth(), viewCenterOn.getHeight())
				/ 2;
		RadialGradient shader = new RadialGradient(left, top, radius, color1,
				color2, Shader.TileMode.CLAMP);
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
	protected void onResume() {
		super.onResume();
		AnalyticsTracker.getInstance(this).activityResume(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		AnalyticsTracker.getInstance(this).activityPause(this);
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
			FileUtils.openFileChooser(this, "application/octet-stream",
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

		if (!TEST_GDPR_DIALOGS && BiglyBTApp.getAppPreferences().getBoolean(
				PREF_ASKED_LOOKUP_GDPR, false)) {
			openRemote(ac);
			return;
		}
		AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(
				R.string.gdpr_dialog_title).setCancelable(true).setPositiveButton(
						R.string.accept, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								AsyncTask.execute(new Runnable() {
									@Override
									public void run() {
										BiglyBTApp.getAppPreferences().setBoolean(
												PREF_ASKED_LOOKUP_GDPR, true);
									}
								});
								openRemote(ac);
							}
						}).setNegativeButton(R.string.decline,
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {

									}
								});
		String msg = getString(R.string.gdpr_code_lookup) + " "
				+ getString(R.string.gdpr_we_dont_process) + " "
				+ getString(R.string.gdpr_ip_warning) + "\n\n"
				+ getString(R.string.gdpr_one_time).replaceAll(" *\n *", "\n");
		builder.setMessage(msg);
		builder.show();
	}

	public void openRemote(String ac) {
		RemoteProfile remoteProfile = RemoteProfileFactory.create(
				RemoteProfile.DEFAULT_USERNAME, ac);
		RemoteUtils.openRemote(this, remoteProfile, false, false);
	}

	@SuppressWarnings("UnusedParameters")
	public void startTorrentingButtonClicked(View view) {
		if (!TEST_GDPR_DIALOGS && BiglyBTApp.getAppPreferences().getBoolean(
				PREF_ASKED_CORE_GDPR, false)) {
			createCore();
			return;
		}
		AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(
				R.string.gdpr_dialog_title).setCancelable(true).setPositiveButton(
						R.string.accept, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								AsyncTask.execute(new Runnable() {
									@Override
									public void run() {
										BiglyBTApp.getAppPreferences().setBoolean(
												PREF_ASKED_CORE_GDPR, true);
									}
								});
								createCore();
							}
						}).setNegativeButton(R.string.decline,
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {
									}
								});
		String msg = getString(R.string.gdpr_full_client) + "\n\n"
				+ getString(R.string.gdpr_full_client_data) + "\n\n"
				+ getString(R.string.gdpr_one_time);
		msg = msg.replaceAll(" *\n *", "\n");
		builder.setMessage(msg);
		builder.show();
	}

	public void createCore() {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "Adding localhost profile..");
		}
		RemoteUtils.createCoreProfile(this, new RemoteUtils.OnCoreProfileCreated() {
			@Override
			public void onCoreProfileCreated(RemoteProfile coreProfile,
					boolean alreadyCreated) {
				RemoteUtils.editProfile(coreProfile, getSupportFragmentManager());
			}
		});
	}

	@Override
	public void profileEditDone(RemoteProfile oldProfile,
			RemoteProfile newProfile) {
		RemoteUtils.openRemote(this, newProfile, false, false);
	}

}
