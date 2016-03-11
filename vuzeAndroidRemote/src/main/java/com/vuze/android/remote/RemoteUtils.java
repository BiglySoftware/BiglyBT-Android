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

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.vuze.android.remote.activity.IntentHandler;
import com.vuze.android.remote.activity.TorrentViewActivity;

public class RemoteUtils
{
	//private static final String TAG = "RemoteUtils";

	private Activity activity;

	public RemoteUtils(Activity activity) {
		this.activity = activity;
	}

	public void openRemote(RemoteProfile remoteProfile, boolean isMain) {
		AppPreferences appPreferences = VuzeRemoteApp.getAppPreferences();
		
		if (appPreferences.getRemote(remoteProfile.getID()) == null) {
			appPreferences.addRemoteProfile(remoteProfile);
		}

		Intent myIntent = new Intent(activity.getIntent());
		myIntent.setAction(Intent.ACTION_VIEW);
		myIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
		if (isMain) {
			myIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
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
		myIntent.setClass(activity, TorrentViewActivity.class);

		myIntent.putExtra(SessionInfoManager.BUNDLE_KEY, remoteProfile.getID());

		activity.startActivity(myIntent);

	}

	public void openRemoteList() {
		Intent myIntent = new Intent();
		myIntent.setAction(Intent.ACTION_VIEW);
		myIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION
				| Intent.FLAG_ACTIVITY_CLEAR_TOP);
		myIntent.setClass(activity, IntentHandler.class);
		activity.startActivity(myIntent);
	}

}
