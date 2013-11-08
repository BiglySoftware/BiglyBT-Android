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

package com.vuze.android.remote;

import java.util.Map;

import android.app.Activity;
import android.content.Intent;

import com.aelitis.azureus.util.JSONUtils;
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
		if (AndroidUtils.DEBUG) {
			System.out.println("openRemote " + ac);
		}

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
		myIntent.setClass(activity, EmbeddedWebRemote.class);

		// TODO: put profile as extra (either as JSON or serializable)
		AndroidUtils.clearExtras(myIntent);
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
		myIntent.setClass(activity, EmbeddedWebRemote.class);

		Map<?, ?> profileAsMap = remoteProfile.getAsMap(false);
		String profileAsJSON = JSONUtils.encodeToJSON(profileAsMap);
		myIntent.putExtra("remote.json", profileAsJSON);

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
