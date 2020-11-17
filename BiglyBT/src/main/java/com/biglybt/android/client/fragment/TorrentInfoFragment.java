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

package com.biglybt.android.client.fragment;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.fragment.app.FragmentActivity;

import com.biglybt.android.adapter.SortableRecyclerAdapter;
import com.biglybt.android.client.*;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.util.MapUtils;
import com.biglybt.android.util.PathInfo;
import com.biglybt.android.widget.SwipeRefreshLayoutExtra;
import com.biglybt.android.widget.SwipeRefreshLayoutExtra.SwipeTextUpdater;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.Thunk;

import java.util.*;

public class TorrentInfoFragment
	extends TorrentDetailPage
{
	private static final String TAG = "TorrentInfoFragment";

	@Thunk
	static final List<String> fields = Arrays.asList(
			TransmissionVars.FIELD_TORRENT_ID,
			TransmissionVars.FIELD_TORRENT_PERCENT_DONE,
			// TimeLine
			TransmissionVars.FIELD_TORRENT_DATE_ADDED,
			TransmissionVars.FIELD_TORRENT_DATE_STARTED,
			TransmissionVars.FIELD_TORRENT_DATE_ACTIVITY,
			TransmissionVars.FIELD_TORRENT_DATE_DONE,
			TransmissionVars.FIELD_TORRENT_SECONDS_DOWNLOADING,
			TransmissionVars.FIELD_TORRENT_SECONDS_SEEDING,
			TransmissionVars.FIELD_TORRENT_ETA,
			// Content
			TransmissionVars.FIELD_TORRENT_POSITION,
			TransmissionVars.FIELD_TORRENT_CREATOR,
			TransmissionVars.FIELD_TORRENT_COMMENT,
			TransmissionVars.FIELD_TORRENT_USER_COMMENT,
			TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR,
			// Sharing
			TransmissionVars.FIELD_TORRENT_DOWNLOADED_EVER,
			TransmissionVars.FIELD_TORRENT_UPLOADED_EVER,
			TransmissionVars.FIELD_TORRENT_UPLOAD_RATIO,
			TransmissionVars.FIELD_TORRENT_PEERS_CONNECTED,
			TransmissionVars.FIELD_TORRENT_PEERS_GETTING_FROM_US,
			TransmissionVars.FIELD_TORRENT_PEERS_SENDING_TO_US);

	@Thunk
	final Object mLock = new Object();

	boolean neverRefreshed = true;

	@Thunk
	SwipeRefreshLayoutExtra swipeRefresh;

	@Thunk
	long lastUpdated;

	public TorrentInfoFragment() {
		super();
	}

	@Override
	public View onCreateViewWithSession(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.frag_torrent_info, container, false);

		swipeRefresh = view.findViewById(R.id.swipe_container);
		if (swipeRefresh != null) {
			swipeRefresh.setExtraLayout(R.layout.swipe_layout_extra);

			swipeRefresh.setOnRefreshListener(this::triggerRefresh);
			swipeRefresh.setOnExtraViewVisibilityChange(
					new SwipeTextUpdater(getLifecycle(), (tvSwipeText) -> {
						long sinceMS = System.currentTimeMillis() - lastUpdated;
						String since = DateUtils.getRelativeDateTimeString(getContext(),
								lastUpdated, DateUtils.SECOND_IN_MILLIS,
								DateUtils.WEEK_IN_MILLIS, 0).toString();
						String s = getString(R.string.last_updated, since);

						tvSwipeText.setText(s);

						return sinceMS < DateUtils.MINUTE_IN_MILLIS
								? DateUtils.SECOND_IN_MILLIS : DateUtils.MINUTE_IN_MILLIS;
					}));
		}

		return view;
	}

	@Override
	public void triggerRefresh() {
		if (torrentID < 0) {
			return;
		}
		if (isRefreshing()) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "Skipping Refresh");
			}
			return;
		}
		setRefreshing(true);

		session.executeRpc(rpc -> rpc.getTorrent(TAG, torrentID, fields,
				(String callID, List<?> addedTorrentMaps, List<String> fields,
						int[] fileIndexes, List<?> removedTorrentIDs) -> {
					neverRefreshed = false;
					lastUpdated = System.currentTimeMillis();
					setRefreshing(false);
					AndroidUtilsUI.runOnUIThread(getActivity(), false, (activity) -> {
						if (swipeRefresh != null) {
							swipeRefresh.setRefreshing(false);
						}
					});
				}));
	}

	@Override
	public void rpcTorrentListReceived(String callID, List<?> addedTorrentMaps,
			List<String> fields, final int[] fileIndexes, List<?> removedTorrentIDs) {
		super.rpcTorrentListReceived(callID, addedTorrentMaps, fields, fileIndexes,
				removedTorrentIDs);

		// tirggerRefresh on normal torrent field update.
		// This allows view to update on location change (which
		// automatically updates the normal torrent fields) and other actions
		if (fields == null || !fields.containsAll(TorrentInfoFragment.fields)) {
			AndroidUtilsUI.runOnUIThread(this::triggerRefresh);
		}
		AndroidUtilsUI.runOnUIThread(this::fillDisplay);
	}

	@Thunk
	@UiThread
	void fillDisplay() {
		FragmentActivity activity = getActivity();

		if (activity == null) {
			return;
		}

		Map<?, ?> mapTorrent = session.torrent.getCachedTorrent(torrentID);
		if (mapTorrent == null) {
			mapTorrent = Collections.EMPTY_MAP;
		}

		fillTimeline(activity, mapTorrent);

		fillContent(activity, mapTorrent);

		fillSharing(activity, mapTorrent);
	}

	@UiThread
	private static void fillSharing(FragmentActivity a, Map<?, ?> mapTorrent) {
		String s;

		long bytesUploaded = MapUtils.getMapLong(mapTorrent,
				TransmissionVars.FIELD_TORRENT_UPLOADED_EVER, -1);
		s = bytesUploaded < 0 ? ""
				: DisplayFormatters.formatByteCountToKiBEtc(bytesUploaded);
		fillRow(a, R.id.torrentInfo_row_bytesUploaded,
				R.id.torrentInfo_val_bytesUploaded, s);

		float shareRatio = MapUtils.getMapFloat(mapTorrent,
				TransmissionVars.FIELD_TORRENT_UPLOAD_RATIO, -1);
		s = shareRatio < 0 ? ""
				: String.format(Locale.getDefault(), "%.02f", shareRatio);
		fillRow(a, R.id.torrentInfo_row_shareRatio, R.id.torrentInfo_val_shareRatio,
				s);

		float pctDone = MapUtils.getMapFloat(mapTorrent,
				TransmissionVars.FIELD_TORRENT_PERCENT_DONE, -1f);

		long numActivePeers = MapUtils.getMapLong(mapTorrent,
				pctDone >= 1.0 ? TransmissionVars.FIELD_TORRENT_PEERS_GETTING_FROM_US
						: TransmissionVars.FIELD_TORRENT_PEERS_SENDING_TO_US,
				-1);
		s = numActivePeers < 0 ? "" : Long.toString(numActivePeers);
		fillRow(a, R.id.torrentInfo_row_seedCount,
				R.id.torrentInfo_val_activePeerCount, s);

		long numConnectedPeers = MapUtils.getMapLong(mapTorrent,
				TransmissionVars.FIELD_TORRENT_PEERS_CONNECTED, -1);
		s = numConnectedPeers < 0 ? "" : Long.toString(numConnectedPeers);
		fillRow(a, R.id.torrentInfo_row_peerCount,
				R.id.torrentInfo_val_connectedPeerCount, s);
	}

	@UiThread
	private void fillContent(@NonNull FragmentActivity a, Map<?, ?> mapTorrent) {
		String s;
		long position = MapUtils.getMapLong(mapTorrent,
				TransmissionVars.FIELD_TORRENT_POSITION, -1);
		boolean done = MapUtils.getMapLong(mapTorrent,
				TransmissionVars.FIELD_TORRENT_LEFT_UNTIL_DONE, 1) == 0;
		s = a.getString(
				done ? R.string.seeding_position_x : R.string.downloading_position_x,
				String.valueOf(position));
		fillRow(a, R.id.torrentInfo_row_position, R.id.torrentInfo_val_position, s);

		s = MapUtils.getMapString(mapTorrent,
				TransmissionVars.FIELD_TORRENT_CREATOR, "");
		fillRow(a, R.id.torrentInfo_row_createdBy, R.id.torrentInfo_val_createdBy,
				s);

		s = MapUtils.getMapString(mapTorrent,
				TransmissionVars.FIELD_TORRENT_COMMENT, "");
		fillRow(a, R.id.torrentInfo_row_comment, R.id.torrentInfo_val_comment, s);

		s = MapUtils.getMapString(mapTorrent,
				TransmissionVars.FIELD_TORRENT_USER_COMMENT, "");
		fillRow(a, R.id.torrentInfo_row_userComment,
				R.id.torrentInfo_val_userComment, s);

		String saveLocation = MapUtils.getMapString(mapTorrent,
				TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR, "");
		boolean isCoreSession = session.getRemoteProfile().getRemoteType() == RemoteProfile.TYPE_CORE;
		if (isCoreSession) {
			AndroidUtilsUI.runOffUIThread(() -> {
				final PathInfo pathInfo = PathInfo.buildPathInfo(saveLocation);
				AndroidUtilsUI.runOnUIThread(a, false,
						activity -> fillRow(a, R.id.torrentInfo_row_saveLocation,
								R.id.torrentInfo_val_saveLocation, pathInfo.getFriendlyName()));
			});
		} else {
			fillRow(a, R.id.torrentInfo_row_saveLocation,
					R.id.torrentInfo_val_saveLocation, saveLocation);
		}

		s = MapUtils.getMapString(mapTorrent,
				TransmissionVars.FIELD_TORRENT_HASH_STRING, "");
		fillRow(a, R.id.torrentInfo_row_hash, R.id.torrentInfo_val_hash, s);
	}

	@UiThread
	private void fillTimeline(FragmentActivity a, Map<?, ?> mapTorrent) {
		String s;
		Resources resources = getResources();
		long addedOn = MapUtils.getMapLong(mapTorrent,
				TransmissionVars.FIELD_TORRENT_DATE_ADDED, 0);
		s = addedOn <= 0 ? ""
				: DateUtils.getRelativeDateTimeString(getActivity(), addedOn * 1000,
						DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS * 2,
						0).toString();
		fillRow(a, R.id.torrentInfo_row_addedOn, R.id.torrentInfo_val_addedOn, s);

		long activeOn = MapUtils.getMapLong(mapTorrent,
				TransmissionVars.FIELD_TORRENT_DATE_ACTIVITY, 0) * 1000;
		long now = System.currentTimeMillis();
		if (activeOn > now) {
			activeOn = now;
		}
		s = activeOn <= 0 ? ""
				: DateUtils.getRelativeDateTimeString(getActivity(), activeOn,
						DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS * 2,
						0).toString();
		fillRow(a, R.id.torrentInfo_row_lastActiveOn,
				R.id.torrentInfo_val_lastActiveOn, s);

		long doneOn = MapUtils.getMapLong(mapTorrent,
				TransmissionVars.FIELD_TORRENT_DATE_DONE, 0);
		s = doneOn <= 0 ? ""
				: DateUtils.getRelativeDateTimeString(getActivity(), doneOn * 1000,
						DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS * 2,
						0).toString();
		fillRow(a, R.id.torrentInfo_row_completedOn,
				R.id.torrentInfo_val_completedOn, s);

		long startedOn = MapUtils.getMapLong(mapTorrent,
				TransmissionVars.FIELD_TORRENT_DATE_STARTED, 0);
		s = startedOn <= 0 ? ""
				: DateUtils.getRelativeDateTimeString(getActivity(), startedOn * 1000,
						DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS * 2,
						0).toString();
		fillRow(a, R.id.torrentInfo_row_startedOn, R.id.torrentInfo_val_startedOn,
				s);

		long secondsDownloading = MapUtils.getMapLong(mapTorrent,
				TransmissionVars.FIELD_TORRENT_SECONDS_DOWNLOADING, 0);
		s = secondsDownloading <= 0 ? ""
				: DisplayFormatters.prettyFormatTimeDiffShort(resources,
						secondsDownloading);
		fillRow(a, R.id.torrentInfo_row_downloadingFor,
				R.id.torrentInfo_val_downloadingFor, s);

		long secondsUploading = MapUtils.getMapLong(mapTorrent,
				TransmissionVars.FIELD_TORRENT_SECONDS_SEEDING, 0);
		s = secondsUploading <= 0 ? ""
				: DisplayFormatters.prettyFormatTimeDiffShort(resources,
						secondsUploading);
		fillRow(a, R.id.torrentInfo_row_seedingFor, R.id.torrentInfo_val_seedingFor,
				s);

		long etaSecs = MapUtils.getMapLong(mapTorrent,
				TransmissionVars.FIELD_TORRENT_ETA, -1);
		s = etaSecs > 0 && etaSecs * 1000 < DateUtils.WEEK_IN_MILLIS
				? DisplayFormatters.prettyFormatTimeDiffShort(resources, etaSecs) : "";
		fillRow(a, R.id.torrentInfo_row_eta, R.id.torrentInfo_val_eta, s);

	}

	@UiThread
	private static void fillRow(Activity activity, int idRow, int idVal,
			CharSequence s) {
		View viewRow = activity.findViewById(idRow);
		if (viewRow == null) {
			return;
		}
		if (s == null || s.length() == 0) {
			viewRow.setVisibility(View.GONE);
			return;
		}

		View viewVal = activity.findViewById(idVal);
		if (!(viewVal instanceof TextView)) {
			return;
		}
		TextView tv = (TextView) viewVal;
		boolean allowSelect = AndroidUtils.hasTouchScreen();
		tv.setSelectAllOnFocus(allowSelect);
		tv.setTextIsSelectable(allowSelect);

		tv.setText(s);

		if (viewRow.getVisibility() != View.VISIBLE) {
			viewRow.setVisibility(View.VISIBLE);
		}
	}

	@Override
	protected MenuBuilder getActionMenuBuilder() {
		return null;
	}

	@Override
	public SortableRecyclerAdapter getMainAdapter() {
		return null;
	}
}