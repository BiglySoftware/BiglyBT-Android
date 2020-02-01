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

import com.biglybt.android.adapter.SortableRecyclerAdapter;
import com.biglybt.android.client.*;
import com.biglybt.android.client.adapter.PeersAdapter;
import com.biglybt.android.client.rpc.SuccessReplyMapRecievedListener;
import com.biglybt.util.Thunk;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.view.menu.MenuBuilder;
import android.view.*;
import android.widget.ListView;

public class PeersFragment
	extends TorrentDetailPage
{
	private static final String TAG = "PeersFragment";

	private ListView listview;

	@Thunk
	PeersAdapter adapter;

	private MenuBuilder actionmenuBuilder;

	public PeersFragment() {
		super();
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
	}

	@Nullable
	@Override
	public View onCreateViewWithSession(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		setHasOptionsMenu(true);
		View view = inflater.inflate(R.layout.frag_torrent_peers, container, false);

		listview = view.findViewById(R.id.peers_list);

		listview.setItemsCanFocus(false);
		listview.setClickable(true);
		listview.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

		adapter = new PeersAdapter(requireActivity(), remoteProfileID);
		listview.setAdapter(adapter);

		adapter.setTorrentID(torrentID, false);

		return view;
	}

	@Thunk
	void updateAdapterTorrentID(long id, boolean alwaysRefilter) {
		if (adapter == null) {
			return;
		}
		AndroidUtilsUI.runOnUIThread(getActivity(), false,
				(a) -> adapter.setTorrentID(torrentID, alwaysRefilter));
	}

	@Override
	public void triggerRefresh() {
		if (torrentID < 0) {
			adapter.clearList();
			return;
		}
		if (isRefreshing()) {
			if (AndroidUtils.DEBUG) {
				log(TAG, "Skipping Refresh");
			}
			return;
		}

		setRefreshing(true);
		session.executeRpc(rpc -> rpc.getTorrentPeerInfo(TAG, torrentID,
				(callID, addedTorrentMaps, fields, fileIndexes, removedTorrentIDs) -> {
					setRefreshing(false);
					updateAdapterTorrentID(torrentID, true);
				}));
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);

		if (AndroidUtils.DEBUG_MENU) {
			log(TAG, "onCreateOptionsMenu " + torrentID + "/" + menu + " via "
					+ AndroidUtils.getCompressedStackTrace());
		}
		inflater.inflate(R.menu.menu_torrent_connections, menu);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		if (AndroidUtils.DEBUG_MENU) {
			log(TAG, "onPrepareOptionsMenu " + torrentID);
		}

		MenuItem menuItem = menu.findItem(R.id.action_update_tracker);
		if (menuItem != null) {
			menuItem.setVisible(torrentID >= 0);
		}
		super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return super.onOptionsItemSelected(item) || handleMenu(item);
	}

	@Override
	@UiThread
	public boolean handleMenu(MenuItem menuItem) {
		if (super.handleMenu(menuItem)) {
			return true;
		}

		if (menuItem.getItemId() == R.id.action_update_tracker) {
			session.executeRpc(rpc -> {
				if (torrentID < 0) {
					return;
				}
				rpc.simpleRpcCall(TransmissionVars.METHOD_TORRENT_REANNOUNCE,
						new long[] {
							torrentID
				}, (SuccessReplyMapRecievedListener) (id,
						optionalMap) -> AndroidUtilsUI.runOnUIThread(getActivity(), false,
								activity -> triggerRefresh()));
			});
			return true;
		}
		return false;
	}

	@SuppressLint("RestrictedApi")
	@Override
	protected MenuBuilder getActionMenuBuilder() {
		if (actionmenuBuilder == null) {
			Context context = getContext();
			if (context == null) {
				return null;
			}
			actionmenuBuilder = new MenuBuilder(context);

			MenuItem item = actionmenuBuilder.add(0, R.id.action_refresh, 0,
					R.string.action_refresh);
			item.setIcon(R.drawable.ic_refresh_white_24dp);

			SubMenu subMenuForFile = actionmenuBuilder.addSubMenu(0,
					R.id.menu_group_context, 0, R.string.sideactions_peers_header);
			new MenuInflater(context).inflate(R.menu.menu_torrent_connections,
					subMenuForFile);

		}

		return actionmenuBuilder;
	}

	@Override
	protected boolean prepareContextMenu(Menu menu) {
		super.prepareContextMenu(menu);
		onPrepareOptionsMenu(menu);
		return true;
	}

	@Override
	public SortableRecyclerAdapter getMainAdapter() {
		return null; // adapter isn't SortableRecyclerAdapter yet
	}

	@Override
	public void pageActivated() {
		super.pageActivated();

		if (adapter != null) {
			adapter.getFilter().refilter(true);
		}
	}
}
