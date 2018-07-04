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

import java.util.List;
import java.util.Map;

import com.biglybt.android.FlexibleRecyclerSelectionListener;
import com.biglybt.android.client.*;
import com.biglybt.android.client.activity.TorrentViewActivity;
import com.biglybt.android.client.adapter.FilesAdapterDisplayFolder;
import com.biglybt.android.client.adapter.FilesAdapterDisplayObject;
import com.biglybt.android.client.adapter.FilesTreeAdapter;
import com.biglybt.android.client.rpc.TorrentListReceivedListener;
import com.biglybt.android.client.rpc.TransmissionRPC;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.Session.RpcExecuter;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.android.client.session.Session_Torrent;
import com.biglybt.android.widget.PreCachingLayoutManager;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.Thunk;

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

	@Thunk
	RecyclerView listview;

	@Thunk
	FilesTreeAdapter adapter;

	@Thunk
	long torrentID;

	@Thunk
	TextView tvScrollTitle;

	@Thunk
	TextView tvSummary;

	private RecyclerView.OnScrollListener listViewOnScrollListener;

	@Override
	public void onStart() {
		super.onStart();
		AnalyticsTracker.getInstance(this).fragmentResume(this, TAG);
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
		if (activity == null) {
			return null;
		}

		String remoteProfileID = SessionManager.findRemoteProfileID(this);
		if (remoteProfileID == null) {
			Log.e(TAG, "No remoteProfileID!");
			return null;
		}

		Intent intent = activity.getIntent();

		final Bundle extras = intent.getExtras();
		if (extras == null) {
			Log.e(TAG, "No extras!");
		} else {
			torrentID = extras.getLong(Session_Torrent.EXTRA_TORRENT_ID);
		}

		Session session = SessionManager.getSession(remoteProfileID, null, null);
		Map<?, ?> torrent = session.torrent.getCachedTorrent(torrentID);
		if (torrent == null) {
			// In theory TorrentOpenOptionsActivity handled this NPE already
			return null;
		}

		View topView = inflater.inflate(AndroidUtils.isTV(getContext())
				? R.layout.frag_fileselection_tv : R.layout.frag_fileselection,
				container, false);
		tvScrollTitle = topView.findViewById(R.id.files_scrolltitle);
		tvSummary = topView.findViewById(R.id.files_summary);

		listview = topView.findViewById(R.id.files_list);
		listview.setLayoutManager(new PreCachingLayoutManager(getContext()));

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

		adapter = new FilesTreeAdapter(getLifecycle(), remoteProfileID, rs);
		adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {

			@Override
			public void onItemRangeInserted(int positionStart, int itemCount) {
				updateSummary();
			}

			@Override
			public void onItemRangeChanged(int positionStart, int itemCount) {
				updateSummary();
			}

			@Override
			public void onChanged() {
				super.onChanged();
				updateSummary();
			}
		});
		adapter.setInEditMode(true);
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

		listViewOnScrollListener = new RecyclerView.OnScrollListener() {
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
		};
		listview.addOnScrollListener(listViewOnScrollListener);

		if (torrent.containsKey(TransmissionVars.FIELD_TORRENT_FILES)) {
			adapter.setTorrentID(torrentID);
		} else {
			session.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.getTorrentFileInfo(TAG, torrentID, null,
							new TorrentListReceivedListener() {

								@Override
								public void rpcTorrentListReceived(String callID,
										List<?> addedTorrentMaps, List<?> removedTorrentIDs) {
									AndroidUtilsUI.runOnUIThread(OpenOptionsFilesFragment.this,
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
			Log.d(TAG, "set " + adapter + " for " + listview + " to " + session + "/"
					+ torrentID);
		}

		return topView;
	}

	@Override
	public void onDestroy() {
		if (listview != null && listViewOnScrollListener != null) {
			listview.removeOnScrollListener(listViewOnScrollListener);
		}
		super.onDestroy();
	}

	@Thunk
	void updateSummary() {
		if (tvSummary != null && adapter != null) {
			tvSummary.setText(DisplayFormatters.formatByteCountToKiBEtc(
					adapter.getTotalSizeWanted()));
		}
	}
}
