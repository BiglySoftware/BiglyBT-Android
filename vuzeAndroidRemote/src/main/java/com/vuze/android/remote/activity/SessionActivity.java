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

package com.vuze.android.remote.activity;

import com.vuze.android.remote.*;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Activity that has an associated {@link com.vuze.android.remote.SessionInfo}
 * Created by TuxPaper on 11/20/16.
 */
public abstract class SessionActivity
	extends AppCompatActivityM
	implements SessionInfoManager.SessionInfoChangedListener
{
	@SuppressWarnings("FieldCanBeLocal")
	private String TAG;

	protected String remoteProfileID;

	/** Never null after onCreate() */
	protected @NonNull SessionInfo sessionInfo;

	@Override
	protected final void onCreate(@Nullable Bundle savedInstanceState) {
		TAG = getTag();
		AndroidUtilsUI.onCreate(this, TAG);

		super.onCreate(savedInstanceState);

		remoteProfileID = SessionInfoManager.findRemoteProfileID(this, TAG);
		sessionInfo = SessionInfoManager.getSessionInfo(remoteProfileID, this,
				this);

		if (sessionInfo == null) {
			finish();
			return;
		}

		sessionInfo.setCurrentActivity(this);

		onCreateWithSession(savedInstanceState);
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (sessionInfo != null) {
			sessionInfo.activityPaused();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (sessionInfo != null) {
			sessionInfo.activityResumed(this);
		}
	}

	@Override
	protected void onDestroy() {
		SessionInfoManager.removeSessionInfoChangedListener(remoteProfileID, this);
		super.onDestroy();
	}

	protected abstract void onCreateWithSession(
			@Nullable Bundle savedInstanceState);

	protected abstract String getTag();

	@Override
	public final void sessionInfoChanged(SessionInfo newSessionInfo) {
		if (newSessionInfo == null) {
			// Don't call finish(). Typically when newSessionInfo is null, we will
			// display an AlertDialog to the user on this activity.
			//finish();
			// Don't set sessionInfo to null, in case activity's finishing code
			// tries to use sessionInfo
			return;
		}
		sessionInfo = newSessionInfo;
	}

	public SessionInfo getSessionInfo() {
		return sessionInfo;
	}

}
