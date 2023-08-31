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
import com.biglybt.android.client.session.SessionManager.SessionChangedListener;

/**
 * Created by TuxPaper on 9/15/18.
 */

public abstract class SessionFragment
	extends FragmentM
	implements SessionGetter, SessionChangedListener
{
	private static final String TAG = "SessionFragment";

	/** Never null after onCreateView()
	 * @noinspection NotNullFieldNotInitialized*/
	@SuppressWarnings("NullableProblems")
	protected @NonNull Session session;

	private String remoteProfileID;

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
			sessionChangedListener = newSession -> {
				if (newSession != null) {
					session = newSession;
					sessionChanged(session);
				}
			};
		}
		session = SessionManager.findOrCreateSession(this, sessionChangedListener);
		if (session != null) {
			sessionChanged(session);
		}
		return session;
	}

	@Override
	public void sessionChanged(@Nullable Session newSession) {
	}

	@Override
	public final void onAttach(@NonNull Context context) {
		super.onAttach(context);
		if (getSession() == null) {
			log(Log.ERROR, TAG, "No session onAttach!");
			return;
		}
		onAttachWithSession(context);
	}

	/**
	 * Called when a fragment is first attached to its context.
	 * {@link #onCreate(Bundle)} will be called after this.
	 */
	@SuppressWarnings("WeakerAccess")
	@UiThread
	public void onAttachWithSession(Context context) {
	}

	@Nullable
	@Override
	public final View onCreateView(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		Session session = getSession();
		if (session == null) {
			log(Log.ERROR, TAG, "No session onCreateView!");
			return null;
		}
		return onCreateViewWithSession(inflater, container, savedInstanceState);
	}

	/**
	 * <i>From {@link androidx.fragment.app.Fragment#onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)}:</i>
	 * <p/>
	 * Called to have the fragment instantiate its user interface view.
	 * This is optional, and non-graphical fragments can return null. This will be called between
	 * {@link #onCreate(Bundle)} and {@link #onActivityCreated(Bundle)}.
	 * <p>A default View can be returned by calling {@link #Fragment(int)} in your
	 * constructor. Otherwise, this method returns null.
	 *
	 * <p>It is recommended to <strong>only</strong> inflate the layout in this method and move
	 * logic that operates on the returned View to {@link #onViewCreated(View, Bundle)}.
	 *
	 * <p>If you return a View from here, you will later be called in
	 * {@link #onDestroyView} when the view is being released.
	 *
	 * @param inflater The LayoutInflater object that can be used to inflate
	 * any views in the fragment,
	 * @param container If non-null, this is the parent view that the fragment's
	 * UI should be attached to.  The fragment should not add the view itself,
	 * but this can be used to generate the LayoutParams of the view.
	 * @param savedInstanceState If non-null, this fragment is being re-constructed
	 * from a previous saved state as given here.
	 * 
	 * @apiNote {@link android.app.Activity#findViewById(int)} will not work here.
	 * Use {@link #onActivityCreated(Bundle)} instead.
	 *
	 * @return Return the View for the fragment's UI, or null.
	 */
	@SuppressWarnings("WeakerAccess")
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

	public String getRemoteProfileID() {
		return remoteProfileID;
	}
}
