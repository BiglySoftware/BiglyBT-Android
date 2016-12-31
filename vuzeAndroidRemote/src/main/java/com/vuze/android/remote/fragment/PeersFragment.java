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
 * 
 */

package com.vuze.android.remote.fragment;

import java.util.List;
import java.util.Map;

import com.vuze.android.remote.AndroidUtils;
import com.vuze.android.remote.R;
import com.vuze.android.remote.session.Session;
import com.vuze.android.remote.session.Session.RpcExecuter;
import com.vuze.android.remote.adapter.PeersAdapter;
import com.vuze.android.remote.rpc.ReplyMapReceivedListener;
import com.vuze.android.remote.rpc.TorrentListReceivedListener;
import com.vuze.android.remote.rpc.TransmissionRPC;
import com.vuze.util.Thunk;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.*;
import android.widget.ListView;

public class PeersFragment
	extends TorrentDetailPage
{
	private static final String TAG = "PeersFragment";

	private ListView listview;

	@Thunk
	PeersAdapter adapter;

	public PeersFragment() {
		super();
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		adapter = new PeersAdapter(this.getActivity(), remoteProfileID);
		listview.setItemsCanFocus(true);
		listview.setAdapter(adapter);
	}

	public View onCreateView(android.view.LayoutInflater inflater,
			android.view.ViewGroup container, Bundle savedInstanceState) {

		setHasOptionsMenu(true);
		View view = inflater.inflate(R.layout.frag_torrent_peers, container, false);

		listview = (ListView) view.findViewById(R.id.peers_list);

		listview.setItemsCanFocus(false);
		listview.setClickable(true);
		listview.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

		return view;
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.TorrentDetailPage#updateTorrentID(long, boolean, boolean, boolean)
	 */
	@Override
	public void updateTorrentID(final long torrentID, boolean isTorrent,
			boolean wasTorrent, boolean torrentIdChanged) {
		if (torrentIdChanged) {
			adapter.clearList();
		}

		//System.out.println("torrent is " + torrent);
		Session session = getSession();
		if (isTorrent) {
			session.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.getTorrentPeerInfo(TAG, torrentID,
							new TorrentListReceivedListener() {

								@Override
								public void rpcTorrentListReceived(String callID,
										List<?> addedTorrentMaps, List<?> removedTorrentIDs) {
									updateAdapterTorrentID(torrentID);
								}
							});
				}
			});
		}
	}

	@Thunk
	void updateAdapterTorrentID(long id) {
		if (adapter == null) {
			return;
		}
		FragmentActivity activity = getActivity();
		if (activity == null) {
			return;
		}
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				adapter.setTorrentID(torrentID);
			}
		});
	}

	@Override
	public void triggerRefresh() {
		Session session = getSession();
		if (torrentID < 0) {
			return;
		}
		session.executeRpc(new RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.getTorrentPeerInfo(TAG, torrentID,
						new TorrentListReceivedListener() {

							@Override
							public void rpcTorrentListReceived(String callID,
									List<?> addedTorrentMaps, List<?> removedTorrentIDs) {
								updateAdapterTorrentID(torrentID);
							}
						});
			}
		});
	}

	@Override
	public void rpcTorrentListReceived(String callID, List<?> addedTorrentMaps,
			List<?> removedTorrentIDs) {
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);

		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "onCreateOptionsMenu " + torrentID + "/" + menu + " via "
					+ AndroidUtils.getCompressedStackTrace());
		}
		inflater.inflate(R.menu.menu_torrent_connections, menu);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "onPrepareOptionsMenu " + torrentID);
		}

		MenuItem menuItem = menu.findItem(R.id.action_update_tracker);
		if (menuItem != null) {
			menuItem.setVisible(torrentID >= 0);
		}
		super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_update_tracker) {
			Session session = getSession();
			session.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					if (torrentID < 0) {
						return;
					}
					long[] torrentIDs = {
						torrentID
					};
					rpc.simpleRpcCall("torrent-reannounce", torrentIDs,
							new ReplyMapReceivedListener() {

								@Override
								public void rpcSuccess(String id, Map<?, ?> optionalMap) {
									triggerRefresh();
								}

								@Override
								public void rpcFailure(String id, String message) {
								}

								@Override
								public void rpcError(String id, Exception e) {
								}
							});
				}
			});
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.TorrentDetailPage#getTAG()
	 */
	@Override
	String getTAG() {
		return TAG;
	}
}
