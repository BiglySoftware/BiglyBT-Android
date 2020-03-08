/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.android.client.fragment;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.biglybt.android.client.FragmentM;
import com.biglybt.android.client.SessionGetter;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionManager;

/**
 * Created by TuxPaper on 9/15/18.
 */

public abstract class SessionFragment
	extends FragmentM
	implements SessionGetter
{
	/** Never null after onCreateView() */
	@SuppressWarnings("NullableProblems")
	protected @NonNull Session session;

	protected String remoteProfileID;

	private SessionManager.SessionChangedListener sessionChangedListener;

	@Override
	public Session getSession() {
		if (session != null && !session.isDestroyed()
				&& sessionChangedListener != null) {
			return session;
		}
		remoteProfileID = SessionManager.findRemoteProfileID(this);
		if (remoteProfileID == null) {
			return null;
		}
		if (sessionChangedListener == null) {
			sessionChangedListener = newSession -> session = newSession;
		}
		session = SessionManager.findOrCreateSession(this, sessionChangedListener);
		return session;
	}

	@Override
	public final void onAttach(@NonNull Context context) {
		super.onAttach(context);
		if (getSession() == null) {
			log(Log.ERROR, "SessionFragment", "No session onAttach!");
			return;
		}
		onAttachWithSession(context);
	}

	/**
	 * Called when a fragment is first attached to its context.
	 * {@link #onCreate(Bundle)} will be called after this.
	 */
	@UiThread
	public void onAttachWithSession(Context context) {
	}

	@Nullable
	@Override
	public final View onCreateView(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		Session session = getSession();
		if (session == null) {
			log(Log.ERROR, "SessionFragment", "No session onCreateView!");
			return null;
		}
		return onCreateViewWithSession(inflater, container, savedInstanceState);
	}

	@UiThread
	public abstract View onCreateViewWithSession(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container, @Nullable Bundle savedInstanceState);

	@Override
	public void onDestroy() {
		if (remoteProfileID != null && sessionChangedListener != null) {
			SessionManager.removeSessionChangedListener(remoteProfileID,
					sessionChangedListener);
			sessionChangedListener = null;
		}
		super.onDestroy();
	}
}
