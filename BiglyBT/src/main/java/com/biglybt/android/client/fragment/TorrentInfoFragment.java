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

import java.util.*;

import com.biglybt.android.client.*;
import com.biglybt.android.client.rpc.TorrentListReceivedListener;
import com.biglybt.android.client.rpc.TransmissionRPC;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.Session.RpcExecuter;
import com.biglybt.android.util.MapUtils;
import com.biglybt.android.widget.SwipeRefreshLayoutExtra;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.Thunk;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.FragmentActivity;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class TorrentInfoFragment
	extends TorrentDetailPage
	implements SwipeRefreshLayoutExtra.OnExtraViewVisibilityChangeListener

{
	private static final String TAG = "TorrentInfoFragment";

	@Thunk
	static final String[] fields = {
		TransmissionVars.FIELD_TORRENT_ID,
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
		TransmissionVars.FIELD_TORRENT_SEEDS,
		TransmissionVars.FIELD_TORRENT_PEERS,
	};

	@Thunk
	final Object mLock = new Object();

	@Thunk
	boolean refreshing = false;

	@Thunk
	SwipeRefreshLayoutExtra swipeRefresh;

	@Thunk
	Handler pullRefreshHandler;

	@Thunk
	long lastUpdated;

	public TorrentInfoFragment() {
		super();
	}

	public View onCreateView(android.view.LayoutInflater inflater,
			android.view.ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.frag_torrent_info, container, false);

		swipeRefresh = view.findViewById(
				R.id.swipe_container);
		if (swipeRefresh != null) {
			swipeRefresh.setExtraLayout(R.layout.swipe_layout_extra);

			swipeRefresh.setOnRefreshListener(
					new SwipeRefreshLayout.OnRefreshListener() {
						@Override
						public void onRefresh() {
							triggerRefresh();
						}
					});
			swipeRefresh.setOnExtraViewVisibilityChange(this);
		}

		return view;
	}

	@Override
	public void onExtraViewVisibilityChange(final View view, int visibility) {
		{
			if (visibility != View.VISIBLE) {
				if (pullRefreshHandler != null) {
					pullRefreshHandler.removeCallbacksAndMessages(null);
					pullRefreshHandler = null;
				}
				return;
			}

			if (pullRefreshHandler != null) {
				pullRefreshHandler.removeCallbacks(null);
				pullRefreshHandler = null;
			}
			pullRefreshHandler = new Handler(Looper.getMainLooper());

			pullRefreshHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (getActivity() == null) {
						return;
					}

					long sinceMS = System.currentTimeMillis() - lastUpdated;
					String since = DateUtils.getRelativeDateTimeString(getContext(),
							lastUpdated, DateUtils.SECOND_IN_MILLIS, DateUtils.WEEK_IN_MILLIS,
							0).toString();
					String s = getResources().getString(R.string.last_updated, since);

					TextView tvSwipeText = view.findViewById(R.id.swipe_text);
					tvSwipeText.setText(s);

					if (pullRefreshHandler == null) {
						return;
					}
					pullRefreshHandler.postDelayed(this,
							sinceMS < DateUtils.MINUTE_IN_MILLIS ? DateUtils.SECOND_IN_MILLIS
									: sinceMS < DateUtils.HOUR_IN_MILLIS
											? DateUtils.MINUTE_IN_MILLIS : DateUtils.HOUR_IN_MILLIS);
				}
			}, 0);
		}
	}

	public void updateTorrentID(final long torrentID, boolean isTorrent,
			boolean wasTorrent, boolean torrentIdChanged) {
		if (!wasTorrent && isTorrent) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "setTorrentID: add listener");
			}
			Session session = getSession();
			session.torrent.addListReceivedListener(TAG, this);
		} else if (wasTorrent && !isTorrent) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "setTorrentID: remove listener");
			}
			Session session = getSession();
			session.torrent.removeListReceivedListener(this);
		}

		if (isTorrent) {
			triggerRefresh();
		}
	}

	@Override
	public void triggerRefresh() {
		if (torrentID < 0) {
			return;
		}
		synchronized (mLock) {
			if (refreshing) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Skipping Refresh");
				}
				return;
			}
			refreshing = true;
		}

		Session session = getSession();

		session.executeRpc(new RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.getTorrent(TAG, torrentID, Arrays.asList(fields),
						new TorrentListReceivedListener() {

							@Override
							public void rpcTorrentListReceived(String callID,
									List<?> addedTorrentMaps, List<?> removedTorrentIDs) {
								lastUpdated = System.currentTimeMillis();
								synchronized (mLock) {
									refreshing = false;
								}
								FragmentActivity activity = getActivity();
								if (activity == null) {
									return;
								}
								activity.runOnUiThread(new Runnable() {

									@Override
									public void run() {
										if (getActivity() == null) {
											return;
										}
										if (swipeRefresh != null) {
											swipeRefresh.setRefreshing(false);
										}

									}
								});
							}
						});
			}
		});
	}

	@Override
	public void rpcTorrentListReceived(String callID, List<?> addedTorrentMaps,
			List<?> removedTorrentIDs) {
		AndroidUtilsUI.runOnUIThread(this, new Runnable() {
			@Override
			public void run() {
				fillDisplay();
			}
		});
	}

	@Thunk
	void fillDisplay() {
		FragmentActivity activity = getActivity();

		if (activity == null) {
			return;
		}

		Session session = getSession();

		Map<?, ?> mapTorrent = session.torrent.getCachedTorrent(torrentID);
		if (mapTorrent == null) {
			mapTorrent = Collections.EMPTY_MAP;
		}

		fillTimeline(activity, mapTorrent);

		fillContent(activity, mapTorrent);

		fillSharing(activity, mapTorrent);
	}

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

		long seeds = MapUtils.getMapLong(mapTorrent,
				TransmissionVars.FIELD_TORRENT_SEEDS, -1);
		s = seeds < 0 ? "" : Long.toString(seeds);
		fillRow(a, R.id.torrentInfo_row_seedCount, R.id.torrentInfo_val_seedCount,
				s);

		long peers = MapUtils.getMapLong(mapTorrent,
				TransmissionVars.FIELD_TORRENT_PEERS, -1);
		s = peers < 0 ? "" : Long.toString(peers);
		fillRow(a, R.id.torrentInfo_row_peerCount, R.id.torrentInfo_val_peerCount,
				s);

	}

	private static void fillContent(FragmentActivity a, Map<?, ?> mapTorrent) {
		String s;
		long position = MapUtils.getMapLong(mapTorrent,
				TransmissionVars.FIELD_TORRENT_POSITION, -1);
		boolean done = MapUtils.getMapLong(mapTorrent,
				TransmissionVars.FIELD_TORRENT_LEFT_UNTIL_DONE, 1) == 0;
		s = (done ? "Seeding" : "Downloading") + " Position #"
				+ String.valueOf(position);
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

		s = MapUtils.getMapString(mapTorrent,
				TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR, "");
		fillRow(a, R.id.torrentInfo_row_saveLocation,
				R.id.torrentInfo_val_saveLocation, s);
	}

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
				TransmissionVars.FIELD_TORRENT_DATE_ACTIVITY, 0);
		s = activeOn <= 0 ? ""
				: DateUtils.getRelativeDateTimeString(getActivity(), activeOn * 1000,
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

	private static void fillRow(Activity activity, int idRow, int idVal,
			String s) {
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

		tv.setText(s);

		if (viewRow.getVisibility() != View.VISIBLE) {
			viewRow.setVisibility(View.VISIBLE);
		}
	}

	@Override
	public void pageDeactivated() {
		synchronized (mLock) {
			refreshing = false;
		}
		super.pageDeactivated();
	}

	@Override
	String getTAG() {
		return TAG;
	}
}
