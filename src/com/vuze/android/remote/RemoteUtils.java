package com.vuze.android.remote;

import android.app.Activity;
import android.content.Intent;

import com.vuze.android.remote.activity.EmbeddedWebRemote;

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
			final boolean remember, boolean isMain) {
		System.out.println("openRemote " + ac);

		Intent myIntent = new Intent(activity.getIntent());
		myIntent.setAction(Intent.ACTION_VIEW);
		myIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
		if (isMain) {
			myIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		}
		myIntent.setClassName("com.vuze.android.remote",
				EmbeddedWebRemote.class.getName());
		// TODO: put prefs as extra (either as JSON or serializable)
		myIntent.putExtra("com.vuze.android.remote.ac", ac);
		myIntent.putExtra("com.vuze.android.remote.user", user);
		myIntent.putExtra("com.vuze.android.remote.remember", remember);
		activity.startActivity(myIntent);

	}

	public void openRemote(RemotePreferences prefs, boolean remember,
			boolean isMain) {
		Intent myIntent = new Intent(activity.getIntent());
		myIntent.setAction(Intent.ACTION_VIEW);
		myIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
		if (isMain) {
			myIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		}
		myIntent.setClassName("com.vuze.android.remote",
				EmbeddedWebRemote.class.getName());
		// TODO: put prefs as extra (either as JSON or serializable)
		myIntent.putExtra("com.vuze.android.remote.ac", prefs.getAC());
		myIntent.putExtra("com.vuze.android.remote.user", prefs.getUser());
		myIntent.putExtra("com.vuze.android.remote.host", prefs.getHost());
		myIntent.putExtra("com.vuze.android.remote.port", prefs.getPort());
		myIntent.putExtra("com.vuze.android.remote.nick", prefs.getNick());
		myIntent.putExtra("com.vuze.android.remote.remember", remember);
		activity.startActivity(myIntent);

	}

}
