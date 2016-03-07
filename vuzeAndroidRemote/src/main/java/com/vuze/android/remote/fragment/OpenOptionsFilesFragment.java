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

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import com.vuze.android.FlexibleRecyclerSelectionListener;
import com.vuze.android.remote.*;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.activity.TorrentViewActivity;
import com.vuze.android.remote.rpc.TorrentListReceivedListener;
import com.vuze.android.remote.rpc.TransmissionRPC;
import com.vuze.util.DisplayFormatters;

/**
 * Files list for Open Options window.
 *
 * NOTE: There's duplicate code in FilesFragment.  Untill common code is merged,
 * changes here should be done there.
 */
public class OpenOptionsFilesFragment
	extends Fragment
{

	private static final String TAG = "FilesSelection";

	private RecyclerView listview;

	private FilesTreeAdapter adapter;

	private SessionInfo sessionInfo;

	private long torrentID;

	private TextView tvScrollTitle;

	private TextView tvSummary;

	@Override
	public void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).fragmentStart(this, TAG);
	}

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

		Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
		if (torrent == null) {
			// In theory TorrentOpenOptionsActivity handled this NPE already
			return null;
		}

		View topView = inflater.inflate(R.layout.frag_fileselection, container,
				false);
		tvScrollTitle = (TextView) topView.findViewById(R.id.files_scrolltitle);
		tvSummary = (TextView) topView.findViewById(R.id.files_summary);

		listview = (RecyclerView) topView.findViewById(R.id.files_list);
		listview.setLayoutManager(new PreCachingLayoutManager(getContext()));
		listview.setAdapter(adapter);

		FlexibleRecyclerSelectionListener rs = new FlexibleRecyclerSelectionListener<FilesTreeAdapter, FilesAdapterDisplayObject>() {
			@Override
			public void onItemClick(FilesTreeAdapter adapter, int position) {
				if (AndroidUtils.usesNavigationControl()) {
					FilesAdapterDisplayObject oItem = adapter.getItem(position);
					if (adapter.isInEditMode()) {
						adapter.flipWant(oItem);
						return;
					}
					if (oItem instanceof FilesAdapterDisplayFolder) {
						FilesAdapterDisplayFolder oFolder = (FilesAdapterDisplayFolder) oItem;
						oFolder.expand = !oFolder.expand;
						adapter.getFilter().filter("");
					}
				}
			}

			@Override
			public boolean onItemLongClick(FilesTreeAdapter adapter, int position) {
				return false;
			}

			@Override
			public void onItemSelected(FilesTreeAdapter adapter, int position,
					boolean isChecked) {

			}

			@Override
			public void onItemCheckedChanged(FilesTreeAdapter adapter,
					FilesAdapterDisplayObject item, boolean isChecked) {
			}
		};

		adapter = new FilesTreeAdapter(this.getActivity(), rs);
		adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
			@Override
			public void onItemRangeChanged(int positionStart, int itemCount) {
				if (tvSummary != null) {
					tvSummary.setText(DisplayFormatters.formatByteCountToKiBEtc(
							adapter.getTotalSizeWanted()));
				}
			}

			@Override
			public void onChanged() {
				super.onChanged();
				if (tvSummary != null) {
					tvSummary.setText(DisplayFormatters.formatByteCountToKiBEtc(
							adapter.getTotalSizeWanted()));
				}
			}
		});
		adapter.setInEditMode(true);
		adapter.setSessionInfo(sessionInfo);
		adapter.setCheckOnSelectedAfterMS(0);
		listview.setAdapter(adapter);

		listview.setOnKeyListener(new View.OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				{
					if (event.getAction() != KeyEvent.ACTION_DOWN) {
						return false;
					}
					switch (keyCode) {
						case KeyEvent.KEYCODE_DPAD_RIGHT: {
							// expand
							int i = adapter.getSelectedPosition();
							FilesAdapterDisplayObject item = adapter.getItem(i);
							if (item instanceof FilesAdapterDisplayFolder) {
								if (!((FilesAdapterDisplayFolder) item).expand) {
									((FilesAdapterDisplayFolder) item).expand = true;
									adapter.getFilter().filter("");
									return true;
								}
							}
							break;
						}

						case KeyEvent.KEYCODE_DPAD_LEFT: {
							// collapse
							int i = adapter.getSelectedPosition();
							FilesAdapterDisplayObject item = adapter.getItem(i);
							if (item instanceof FilesAdapterDisplayFolder) {
								if (((FilesAdapterDisplayFolder) item).expand) {
									((FilesAdapterDisplayFolder) item).expand = false;
									adapter.getFilter().filter("");
									return true;
								}
							}
							break;
						}
					}

					return false;
				}
			}
		});

		listview.setOnScrollListener(new RecyclerView.OnScrollListener() {
			int firstVisibleItem = 0;

			@Override
			public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
				super.onScrolled(recyclerView, dx, dy);
				LinearLayoutManager lm = (LinearLayoutManager) listview.getLayoutManager();
				int firstVisibleItem = lm.findFirstCompletelyVisibleItemPosition();
				if (firstVisibleItem != this.firstVisibleItem) {
					this.firstVisibleItem = firstVisibleItem;
					FilesAdapterDisplayObject itemAtPosition = adapter.getItem(
							firstVisibleItem);

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
