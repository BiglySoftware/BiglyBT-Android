package com.vuze.android.remote.activity;

import android.annotation.TargetApi;
import android.content.Intent;
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

		System.out.println("LoginActivity intent = " + getIntent());

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
			case R.id.action_adv_login:
				DialogFragmentGenericRemoteProfile dlg = new DialogFragmentGenericRemoteProfile();
				dlg.show(getSupportFragmentManager(), "GenericRemoteProfile");

				return true;
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
