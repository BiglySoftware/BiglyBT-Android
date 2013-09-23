package com.vuze.android.remote;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

import android.app.Activity;
import android.content.Intent;
import android.util.Base64;

import com.vuze.android.remote.activity.EmbeddedWebRemote;
import com.vuze.android.remote.rpc.RPC;
import com.vuze.android.remote.rpc.RPCException;

public class RemoteUtils
{
	private Activity activity;

	private AppPreferences appPreferences;

	public RemoteUtils(Activity activity) {
		this.activity = activity;
		this.appPreferences = new AppPreferences(activity);
	}

	public RemoteUtils(Activity activity, AppPreferences appPreferences) {
		this.activity = activity;
		this.appPreferences = appPreferences;
	}

	public void openRemote(final String user, final String ac,
			final boolean remember) {
		System.out.println("openRemote " + ac);
		
		Intent myIntent = new Intent(activity.getIntent());
		myIntent.setAction(Intent.ACTION_VIEW);
		myIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
		myIntent.setClassName("com.vuze.android.remote",
				EmbeddedWebRemote.class.getName());
		myIntent.putExtra("com.vuze.android.remote.ac", ac);
		myIntent.putExtra("com.vuze.android.remote.user", user);
		myIntent.putExtra("com.vuze.android.remote.remember", remember);
		activity.startActivity(myIntent);

	}

}
