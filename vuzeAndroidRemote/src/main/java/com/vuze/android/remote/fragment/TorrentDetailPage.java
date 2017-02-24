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
 */

package com.vuze.android.remote.fragment;

import com.vuze.android.remote.*;
import com.vuze.android.remote.adapter.TorrentPagerAdapter.PagerPosition;
import com.vuze.android.remote.rpc.TorrentListReceivedListener;
import com.vuze.android.remote.session.RefreshTriggerListener;
import com.vuze.android.remote.session.Session;
import com.vuze.android.remote.session.SessionManager;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

/**
 * A Fragment that belongs to a page in {@link TorrentDetailsFragment}
 */
public abstract class TorrentDetailPage
	extends FragmentM
	implements SetTorrentIdListener, RefreshTriggerListener,
	TorrentListReceivedListener, PagerPosition, FragmentPagerListener
{

	private static final String TAG = "TorrentDetailPage";

	private int pagerPosition = -1;

	protected long torrentID = -1;

	private long pausedTorrentID = -1;

	private boolean viewActive = false;

	protected String remoteProfileID;

	protected @NonNull
	Session getSession() {
		return SessionManager.getSession(remoteProfileID, null, null);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		remoteProfileID = SessionManager.findRemoteProfileID(getActivity(),
				TAG);
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onPause() {
		super.onPause();

		if (SessionManager.hasSession(remoteProfileID)) {
			Session session = getSession();
			session.removeRefreshTriggerListener(this);
			session.torrent.removeListReceivedListener(this);
		}
	}

	@Override
	public void onResume() {
		if (AndroidUtils.DEBUG) {
			logD("onResume, pausedTorrentID=" + pausedTorrentID);
		}
		super.onResume();

		pagerPosition = getArguments().getInt("pagerPosition", pagerPosition);

		if (hasOptionsMenu()) {
			FragmentActivity activity = getActivity();
			if (activity instanceof ActionModeBeingReplacedListener) {
				ActionModeBeingReplacedListener tva = (ActionModeBeingReplacedListener) activity;
				tva.rebuildActionMode();
			}
		}

		boolean active = getArguments().getBoolean("isActive", false);
		if (active) {
			pageActivated();
		}
	}

	@Override
	public void setPagerPosition(int position) {
		pagerPosition = position;
	}

	@Override
	public int getPagerPosition() {
		return pagerPosition;
	}

	@Override
	public void pageDeactivated() {
		if (AndroidUtils.DEBUG) {
			logD("pageDeactivated " + torrentID);
		}
		viewActive = false;
		// We lose focus info when clearing torrentID on page deactivation
		// Disable for now
//		if (torrentID != -1) {
//			pausedTorrentID = torrentID;
//			setTorrentID(-1);
//		}

		Session session = getSession();
		session.removeRefreshTriggerListener(this);
		session.torrent.removeListReceivedListener(this);

		if (hasOptionsMenu()) {
			AndroidUtilsUI.invalidateOptionsMenuHC(getActivity());
		}

		VuzeEasyTracker.getInstance(this).fragmentStop(this);
	}

	@Override
	public void pageActivated() {
		if (viewActive) {
			return;
		}
		if (AndroidUtils.DEBUG) {
			logD("pageActivated");
		}
		viewActive = true;
		if (pausedTorrentID >= 0) {
			setTorrentID(pausedTorrentID);
		} else if (torrentID >= 0) {
			setTorrentID(torrentID);
		} else {
			long newTorrentID = getArguments().getLong("torrentID", -1);
			setTorrentID(newTorrentID);
		}

		Session session = getSession();
		session.addRefreshTriggerListener(this);
		session.torrent.addListReceivedListener(this, false);

		VuzeEasyTracker.getInstance(this).fragmentStart(this, getTAG());
	}

	abstract String getTAG();

	public final void setTorrentID(long id) {
		if (id != -1) {
			if (!viewActive) {
				if (AndroidUtils.DEBUG) {
					logE("setTorrentID: view not Active " + torrentID);
				}
				pausedTorrentID = id;
				return;
			}
			if (getActivity() == null) {
				if (AndroidUtils.DEBUG) {
					logE("setTorrentID: No Activity");
				}
				pausedTorrentID = id;
				return;
			}
		}

		boolean wasTorrent = torrentID >= 0;
		boolean isTorrent = id >= 0;
		boolean torrentIdChanged = id != torrentID;

		torrentID = id;

		updateTorrentID(torrentID, isTorrent, wasTorrent, torrentIdChanged);
	}

	private void logD(String s) {
		Log.d(TAG, AndroidUtils.getSimpleName(getClass()) + "] " + s);
	}

	private void logE(String s) {
		Log.e(TAG, AndroidUtils.getSimpleName(getClass()) + "] " + s);
	}

	/**
	 * refresh request triggered on a timer length set by user.<br>
	 * Also triggered on {@link #pageActivated()}
	 */
	public abstract void triggerRefresh();

	/**
	 * Session will not be null
	 */
	public abstract void updateTorrentID(long torrentID, boolean isTorrent,
			boolean wasTorrent, boolean torrentIdChanged);

}
