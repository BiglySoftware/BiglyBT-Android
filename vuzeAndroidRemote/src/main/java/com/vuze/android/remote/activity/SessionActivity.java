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
import com.vuze.android.remote.session.Session;
import com.vuze.android.remote.session.SessionManager;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Activity that has an associated {@link Session}
 * Created by TuxPaper on 11/20/16.
 */
public abstract class SessionActivity
	extends AppCompatActivityM
	implements SessionManager.SessionChangedListener
{
	@SuppressWarnings("FieldCanBeLocal")
	private String TAG;

	protected String remoteProfileID;

	/** Never null after onCreate() */
	protected @NonNull
	Session session;

	@Override
	protected final void onCreate(@Nullable Bundle savedInstanceState) {
		TAG = getTag();
		AndroidUtilsUI.onCreate(this, TAG);

		super.onCreate(savedInstanceState);

		remoteProfileID = SessionManager.findRemoteProfileID(this, TAG);
		session = SessionManager.getSession(remoteProfileID, this,
				this);

		if (session == null) {
			finish();
			return;
		}

		session.setCurrentActivity(this);

		onCreateWithSession(savedInstanceState);
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (session != null) {
			session.activityPaused();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (session != null) {
			session.activityResumed(this);
		}
	}

	@Override
	protected void onDestroy() {
		SessionManager.removeSessionChangedListener(remoteProfileID, this);
		super.onDestroy();
	}

	protected abstract void onCreateWithSession(
			@Nullable Bundle savedInstanceState);

	protected abstract String getTag();

	@Override
	public final void sessionChanged(Session newSession) {
		if (newSession == null) {
			// Don't call finish(). Typically when newSession is null, we will
			// display an AlertDialog to the user on this activity.
			//finish();
			// Don't set session to null, in case activity's finishing code
			// tries to use session
			return;
		}
		session = newSession;
	}

	public Session getSession() {
		return session;
	}

}
