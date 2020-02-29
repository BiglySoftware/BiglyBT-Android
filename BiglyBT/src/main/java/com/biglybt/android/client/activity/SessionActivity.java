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

package com.biglybt.android.client.activity;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionManager;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

/**
 * Activity that has an associated {@link Session}
 * Created by TuxPaper on 11/20/16.
 */
public abstract class SessionActivity
	extends ThemedActivity
	implements SessionManager.SessionChangedListener
{
	protected String remoteProfileID;

	/** Never null after onCreate() */
	@SuppressWarnings("NullableProblems")
	protected @NonNull Session session;

	private boolean hasFocus;

	@Override
	protected final void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//noinspection ConstantConditions
		if (findSession() == null) {
			finish();
			return;
		}

		onCreateWithSession(savedInstanceState);
	}

	private Session findSession() {
		remoteProfileID = SessionManager.findRemoteProfileID(this);
		if (remoteProfileID == null) {
			return null;
		}
		session = SessionManager.getSession(remoteProfileID, this);
		return session;
	}

	@Override
	protected void onRestart() {
		if (session == null || session.isDestroyed()) {
			session = SessionManager.getSession(remoteProfileID, this);
		}
		super.onRestart();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (session != null && hasFocus) {
			// Both onResume and onWindowFocusChanged can trigger
			// session.activityResumed.  We only need to call it here if
			// we are in focus.  The case is when the user has the activity
			// active, turns off phone (no onWindowFocusChanged is sent),
			// turns phone on again (no onWindowFocusChanged is sent)
			session.setCurrentActivity(this);
		}
	}

	@Override
	protected void onStop() {
		if (session != null) {
			session.clearCurrentActivity(this);
		}
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		if (session != null) {
			session.clearCurrentActivity(this);
		}
		SessionManager.removeSessionChangedListener(remoteProfileID, this);
		super.onDestroy();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		this.hasFocus = hasFocus;
		if (AndroidUtils.DEBUG) {
			log("SessionActivity",
					"onWindowFocusChanged: hasFocus? " + hasFocus + "; finishing? "
							+ isFinishing() + "; isActivityVisible? " + isActivityVisible());
		}
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus && !isFinishing() && isActivityVisible()) {
			if (findSession() == null) {
				finish();
				return;
			}

			session.setCurrentActivity(this);
		}
	}

	@UiThread
	protected abstract void onCreateWithSession(
			@Nullable Bundle savedInstanceState);

	public String getRemoteProfileID() {
		return remoteProfileID;
	}

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

	@NonNull
	public Session getSession() {
		if (session == null) {
			findSession();
		}
		return session;
	}

}
