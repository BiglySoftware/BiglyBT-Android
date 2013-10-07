package com.vuze.android.remote;

import android.app.Activity;
import android.content.Intent;

import com.vuze.android.remote.activity.EmbeddedWebRemote;
import com.vuze.android.remote.activity.IntentHandler;

public class RemoteUtils
{
	private Activity activity;

	public RemoteUtils(Activity activity) {
		this.activity = activity;
	}

	public void openRemote(final String user, final String ac,
			final boolean remember, boolean isMain) {
		System.out.println("openRemote " + ac);

		Intent myIntent = new Intent(activity.getIntent());
		myIntent.setAction(Intent.ACTION_VIEW);
		myIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
		if (isMain) {
			// Scenario:
			// User has multiple remote hosts.
			// User clicks on torrent link in browser.
			// User is displayed remote selector activity (IntenntHandler) and picks
			// Remote activity is opened, torrent is added
			// We want the back button to go back to the browser.  Going back to
			// the remote selector would be confusing (especially if they then chose
			// another remote!)
			myIntent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
		}
		myIntent.setClassName("com.vuze.android.remote",
				EmbeddedWebRemote.class.getName());
		// TODO: put profile as extra (either as JSON or serializable)
		myIntent.putExtra("com.vuze.android.remote.ac", ac);
		myIntent.putExtra("com.vuze.android.remote.user", user);
		myIntent.putExtra("com.vuze.android.remote.remember", remember);
		activity.startActivity(myIntent);

	}

	public void openRemote(RemoteProfile remoteProfile, boolean remember,
			boolean isMain) {
		Intent myIntent = new Intent(activity.getIntent());
		myIntent.setAction(Intent.ACTION_VIEW);
		myIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
		if (isMain) {
			myIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		}
		myIntent.setClassName("com.vuze.android.remote",
				EmbeddedWebRemote.class.getName());
		// TODO: put profile as extra (either as JSON or serializable)
		myIntent.putExtra("com.vuze.android.remote.ac", remoteProfile.getAC());
		myIntent.putExtra("com.vuze.android.remote.user", remoteProfile.getUser());
		myIntent.putExtra("com.vuze.android.remote.host", remoteProfile.getHost());
		myIntent.putExtra("com.vuze.android.remote.port", remoteProfile.getPort());
		myIntent.putExtra("com.vuze.android.remote.nick", remoteProfile.getNick());
		myIntent.putExtra("com.vuze.android.remote.remember", remember);
		activity.startActivity(myIntent);

	}

	public void openRemoteList(Intent o) {
		Intent myIntent = new Intent(o);
		myIntent.setAction(Intent.ACTION_VIEW);
		myIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION
				| Intent.FLAG_ACTIVITY_CLEAR_TOP);
		myIntent.setClass(activity, IntentHandler.class);
		activity.startActivity(myIntent);
	}

}
