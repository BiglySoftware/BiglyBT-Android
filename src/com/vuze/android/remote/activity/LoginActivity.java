package com.vuze.android.remote.activity;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.Bitmap.Config;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import android.widget.TextView.OnEditorActionListener;

import com.vuze.android.remote.*;
import com.vuze.android.remote.dialog.DialogFragmentAbout;
import com.vuze.android.remote.dialog.DialogFragmentGenericRemoteProfile;
import com.vuze.android.remote.dialog.DialogFragmentGenericRemoteProfile.GenericRemoteProfileListener;

public class LoginActivity
	extends FragmentActivity
	implements GenericRemoteProfileListener
{

	private EditText textAccessCode;

	private AppPreferences appPreferences;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().setFormat(PixelFormat.RGBA_8888);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_DITHER);
		Intent intent = getIntent();
		Bundle extras = intent.getExtras();

		if (AndroidUtils.DEBUG) {
			System.out.println("LoginActivity intent = " + getIntent());
		}

		appPreferences = new AppPreferences(getApplicationContext());

		setContentView(R.layout.activity_login);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			setupIceCream();
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			setupHoneyComb();
		}

		textAccessCode = (EditText) findViewById(R.id.editTextAccessCode);

		String ac = null;

		if (extras != null) {
			ac = intent.getExtras().getString("com.vuze.android.remote.login.ac");
		}
		if (ac == null) {
			ac = appPreferences.getLastUsedRemoteID();
		}
		if (ac != null) {
			textAccessCode.setText(ac);
			textAccessCode.selectAll();
		}
		textAccessCode.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				loginButtonClicked(v);
				return true;
			}
		});

		CheckBox chkRemember = (CheckBox) findViewById(R.id.login_remember);
		chkRemember.setChecked(true);

		Resources res = getResources();

		TextView tvCopyright = (TextView) findViewById(R.id.login_copyright);
		tvCopyright.setMovementMethod(LinkMovementMethod.getInstance());
		tvCopyright.setText(Html.fromHtml(tvCopyright.getText().toString()));

		TextView tvLoginGuide = (TextView) findViewById(R.id.login_guide);
		tvLoginGuide.setMovementMethod(LinkMovementMethod.getInstance());
		CharSequence text = tvLoginGuide.getText();

		Spanned s = Html.fromHtml(text.toString());
		SpannableString ss = new SpannableString(s);
		String string = text.toString();

		setSpanBetweenTokens(ss, string, "##",
				new BackgroundColorSpan(res.getColor(R.color.login_text_color)),
				new ForegroundColorSpan(res.getColor(R.color.login_link_color)));
		setSpanBetweenTokens(ss, string, "!!",
				new BackgroundColorSpan(res.getColor(R.color.login_text_color)),
				new ForegroundColorSpan(res.getColor(R.color.login_link_color)));
		tvLoginGuide.setText(ss);
	}

	public static void setSpanBetweenTokens(SpannableString ss, String text,
			String token, CharacterStyle... cs) {
		// Start and end refer to the points where the span will apply
		int tokenLen = token.length();
		int start = text.indexOf(token);
		int end = text.indexOf(token, start + tokenLen);

		if (start > -1 && end > -1) {
			for (CharacterStyle c : cs) {
				ss.setSpan(c, start + tokenLen, end, 0);
			}

			Drawable blankDrawable = new Drawable() {

				@Override
				public void setColorFilter(ColorFilter cf) {
				}

				@Override
				public void setAlpha(int alpha) {
				}

				@Override
				public int getOpacity() {
					return 0;
				}

				@Override
				public void draw(Canvas canvas) {
				}
			};

			// because AbsoluteSizeSpan(0) doesn't work on older versions
			ss.setSpan(new ImageSpan(blankDrawable), start, start + tokenLen, 0);
			ss.setSpan(new ImageSpan(blankDrawable), end, end + tokenLen, 0);
		}
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
	};

	private void setBackgroundGradient() {

		ViewGroup mainLayout = (ViewGroup) findViewById(R.id.main_loginlayout);
		int h = mainLayout.getHeight();
		int w = mainLayout.getWidth();
		System.out.println("wxh=" + w + "x" + h);
		View viewCenterOn = findViewById(R.id.login_frog_logo);
		int top = viewCenterOn.getTop() + (viewCenterOn.getHeight() / 2);
		System.out.println("center at " + top);

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

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private void setupIceCream() {
		getActionBar().setHomeButtonEnabled(true);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupHoneyComb() {
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_login, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
			case R.id.action_adv_login: {
				DialogFragmentGenericRemoteProfile dlg = new DialogFragmentGenericRemoteProfile();
				dlg.show(getSupportFragmentManager(), "GenericRemoteProfile");

				return true;
			}
			case R.id.action_about: {
				DialogFragmentAbout dlg = new DialogFragmentAbout();
				dlg.show(getSupportFragmentManager(), "About");
				return true;
			}
		}

		return super.onOptionsItemSelected(item);
	}

	public void loginButtonClicked(View v) {
		final String ac = textAccessCode.getText().toString();
		CheckBox chkRemember = (CheckBox) findViewById(R.id.login_remember);
		final boolean remember = chkRemember.isChecked();

		if (!remember) {
			appPreferences.setLastRemote(null);
		}

		new RemoteUtils(this).openRemote("vuze", ac, remember, false);
	}

	@Override
	public void profileEditDone(RemoteProfile oldProfile, RemoteProfile newProfile) {
		new RemoteUtils(this).openRemote(newProfile, true, false);
	}

}
