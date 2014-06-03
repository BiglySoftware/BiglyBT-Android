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

import java.util.List;
import java.util.Map;

import org.gudy.azureus2.core3.util.DisplayFormatters;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.*;
import android.widget.AbsListView.OnScrollListener;

import com.handmark.pulltorefresh.library.PullToRefreshBase.Mode;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.vuze.android.remote.*;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.activity.TorrentViewActivity;
import com.vuze.android.remote.rpc.TorrentListReceivedListener;
import com.vuze.android.remote.rpc.TransmissionRPC;

public class OpenOptionsFilesFragment
	extends Fragment
{

	private static final String TAG = "FilesSelection";

	private ListView listview;

	private PullToRefreshListView pullListView;

	private FilesTreeAdapter adapter;

	private SessionInfo sessionInfo;

	private long torrentID;

	private View topView;

	private TextView tvScrollTitle;

	private TextView tvSummary;

	@Override
	public void onResume() {
		super.onResume();

		if (hasOptionsMenu()) {
			FragmentActivity activity = getActivity();
			if (activity instanceof ActionModeBeingReplacedListener) {
				TorrentViewActivity tva = (TorrentViewActivity) activity;
				tva.rebuildActionMode();
			}
		}
	}

	public View onCreateView(android.view.LayoutInflater inflater,
			android.view.ViewGroup container, Bundle savedInstanceState) {

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onCreateview " + this);
		}

		FragmentActivity activity = getActivity();
		Intent intent = activity.getIntent();

		final Bundle extras = intent.getExtras();
		if (extras == null) {
			Log.e(TAG, "No extras!");
		} else {

			String remoteProfileID = extras.getString(SessionInfoManager.BUNDLE_KEY);
			if (remoteProfileID != null) {
				sessionInfo = SessionInfoManager.getSessionInfo(remoteProfileID,
						activity);
			}

			torrentID = extras.getLong("TorrentID");
		}

		topView = inflater.inflate(R.layout.frag_fileselection, container, false);
		tvScrollTitle = (TextView) topView.findViewById(R.id.files_scrolltitle);
		tvSummary = (TextView) topView.findViewById(R.id.files_summary);

		View oListView = topView.findViewById(R.id.files_list);
		if (oListView instanceof ListView) {
			listview = (ListView) oListView;
		} else if (oListView instanceof PullToRefreshListView) {
			pullListView = (PullToRefreshListView) oListView;
			listview = pullListView.getRefreshableView();
			pullListView.setMode(Mode.DISABLED);
		}

		listview.setItemsCanFocus(false);
		listview.setClickable(true);
		listview.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

		if (Build.VERSION.SDK_INT >= 19) {
			listview.getViewTreeObserver().addOnGlobalLayoutListener(
					new OnGlobalLayoutListener() {
						@SuppressWarnings("deprecation")
						@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
						@Override
						public void onGlobalLayout() {
							listview.setFastScrollEnabled(true);
							if (Build.VERSION.SDK_INT >= 16) {
								listview.getViewTreeObserver().removeOnGlobalLayoutListener(
										this);
							} else {
								listview.getViewTreeObserver().removeGlobalOnLayoutListener(
										this);
							}
						}
					});
		} else {
			listview.setFastScrollEnabled(true);
		}

		adapter = new FilesTreeAdapter(this.getActivity()) {
			@Override
			public void notifyDataSetChanged() {
				super.notifyDataSetChanged();
				if (tvSummary != null) {
					tvSummary.setText(DisplayFormatters.formatByteCountToKiBEtc(adapter.getTotalSizeWanted()));
				}
			}
		};
		adapter.setInEditMode(true);
		adapter.setSessionInfo(sessionInfo);
		listview.setItemsCanFocus(true);
		listview.setAdapter(adapter);

		listview.setOnScrollListener(new OnScrollListener() {
			int firstVisibleItem = 0;

			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {
			}

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem,
					int visibleItemCount, int totalItemCount) {
				if (firstVisibleItem != this.firstVisibleItem) {
					this.firstVisibleItem = firstVisibleItem;
					FilesAdapterDisplayObject itemAtPosition = (FilesAdapterDisplayObject) listview.getItemAtPosition(firstVisibleItem);

					if (itemAtPosition == null) {
						return;
					}
					if (itemAtPosition.parent != null) {
						if (tvScrollTitle != null) {
							tvScrollTitle.setText(itemAtPosition.parent.folder);
						}
					} else {
						if (tvScrollTitle != null) {
							tvScrollTitle.setText("");
						}
					}
				}
			}
		});

		Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
		if (torrent.containsKey("files")) {
			adapter.setTorrentID(torrentID);
		} else {
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.getTorrentFileInfo(TAG, torrentID, null,
							new TorrentListReceivedListener() {

								@Override
								public void rpcTorrentListReceived(String callID,
										List<?> addedTorrentMaps, List<?> removedTorrentIDs) {
									AndroidUtils.runOnUIThread(OpenOptionsFilesFragment.this,
											new Runnable() {
												@Override
												public void run() {
													adapter.setTorrentID(torrentID);
												}
											});
								}
							});
				}
			});
		}

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "set " + adapter + " for " + listview + " to " + sessionInfo
					+ "/" + torrentID);
		}

		return topView;
	}

}
