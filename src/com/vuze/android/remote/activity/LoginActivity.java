package com.vuze.android.remote.activity;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.*;
import android.widget.*;
import android.widget.TextView.OnEditorActionListener;

import com.vuze.android.remote.*;
import com.vuze.android.remote.DialogFragmentGenericRemoteProfile.GenericRemoteProfileListener;

public class LoginActivity
	extends FragmentActivity
	implements GenericRemoteProfileListener
{

	private EditText textAccessCode;

	private AppPreferences appPreferences;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

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

		CheckBox chkRemember = (CheckBox) findViewById(R.id.chkLoginRemember);
		chkRemember.setChecked(true);

	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		setBackgroundGradient();
	}

	
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			setBackgroundGradient();
		}
	};

	private void setBackgroundGradient() {
		/*
		ViewGroup mainLayout = (ViewGroup) findViewById(R.id.main_loginlayout);
		int h = mainLayout.getHeight();
		int w = mainLayout.getWidth();
		CheckBox chkRemember = (CheckBox) findViewById(R.id.chkLoginRemember);
		int top = chkRemember.getTop() + (chkRemember.getHeight() / 2);
		ShapeDrawable mDrawable = new ShapeDrawable(new RectShape());
		RadialGradient shader = new RadialGradient(w / 2, top, h / 2,
				getResources().getColor(R.color.login_color_1),
				getResources().getColor(R.color.login_color_2), Shader.TileMode.CLAMP);
		mDrawable.getPaint().setShader(shader);
		mainLayout.setBackgroundDrawable(mDrawable);
		*/
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
		CheckBox chkRemember = (CheckBox) findViewById(R.id.chkLoginRemember);
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
