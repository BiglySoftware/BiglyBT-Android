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

package com.biglybt.android.client.adapter;

import java.text.NumberFormat;
import java.util.*;

import com.biglybt.android.adapter.DelayedFilter;
import com.biglybt.android.client.*;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.android.util.MapUtils;
import com.biglybt.android.util.TextViewFlipper;
import com.biglybt.android.util.TextViewFlipper.FlipValidator;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.Thunk;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filterable;
import android.widget.TextView;

public class PeersAdapter
	extends BaseAdapter
	implements Filterable
{
	public interface PeerFilterCommunication
		extends SessionGetter, DelayedFilter.PerformingFilteringListener
	{
		void doSort();

		void notifyDataSetChanged();

		void setDisplayList(List<Object> list);
	}

	static class ViewHolder
	{
		TextView tvIP;

		TextView tvName;

		TextView tvProgress;

		TextView tvUlRate;

		TextView tvDlRate;

		TextView tvCC;

		@NonNull
		public String peerID = "";

		public long torrentID = -1;
	}

	public static class ViewHolderFlipValidator
		implements FlipValidator
	{
		private final ViewHolder holder;

		private final String peerID;

		private final long torrentID;

		ViewHolderFlipValidator(@NonNull ViewHolder holder, long torrentID,
				@NonNull String peerID) {
			this.holder = holder;
			this.torrentID = torrentID;
			this.peerID = peerID;
		}

		@Override
		public boolean isStillValid() {
			return torrentID == holder.torrentID && holder.peerID.equals(peerID);
		}
	}

	private final Context context;

	private final String remoteProfileID;

	private PeerFilter filter;

	/** List of they keys of all entries displayed, in the display order */
	@Thunk
	List<Object> displayList;

	@Thunk
	@NonNull
	final Object mLock = new Object();

	@Thunk
	Comparator<? super Map<?, ?>> comparator;

	@Thunk
	String[] sortFieldIDs;

	@Thunk
	@NonNull
	Boolean[] sortOrderAsc = new Boolean[0];

	@Thunk
	long torrentID;

	@NonNull
	private final TextViewFlipper flipper;

	public PeersAdapter(@NonNull Context context,
			@NonNull String remoteProfileID) {
		this.context = context;
		this.remoteProfileID = remoteProfileID;
		flipper = TextViewFlipper.create();
		displayList = new ArrayList<>();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View rowView = convertView;
		if (rowView == null) {
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(
					Context.LAYOUT_INFLATER_SERVICE);
			assert inflater != null;
			rowView = inflater.inflate(R.layout.row_peers_list, parent, false);
			if (rowView == null) {
				return null;
			}
			ViewHolder viewHolder = new ViewHolder();

			viewHolder.tvName = rowView.findViewById(R.id.peerrow_client);
			viewHolder.tvCC = rowView.findViewById(R.id.peerrow_cc);
			viewHolder.tvDlRate = rowView.findViewById(R.id.peerrow_dl);
			viewHolder.tvIP = rowView.findViewById(R.id.peerrow_ip);
			viewHolder.tvProgress = rowView.findViewById(R.id.peerrow_pct);
			viewHolder.tvUlRate = rowView.findViewById(R.id.peerrow_ul);

			rowView.setTag(viewHolder);
		}

		ViewHolder holder = (ViewHolder) rowView.getTag();
		if (holder == null) {
			return null;
		}

		Map<?, ?> item = getItem(position);

		String peerID = MapUtils.getMapString(item,
				TransmissionVars.FIELD_PEERS_ADDRESS, "");
		ViewHolderFlipValidator validator = new ViewHolderFlipValidator(holder,
				torrentID, peerID);
		boolean animateFlip = validator.isStillValid();
		holder.peerID = peerID;
		holder.torrentID = torrentID;

		if (holder.tvName != null) {
			flipper.changeText(
					holder.tvName, MapUtils.getMapString(item,
							TransmissionVars.FIELD_PEERS_CLIENT_NAME, "??"),
					animateFlip, validator);
		}
		if (holder.tvCC != null) {
			flipper.changeText(holder.tvCC,
					MapUtils.getMapString(item, TransmissionVars.FIELD_PEERS_CC, ""),
					animateFlip, validator);
		}
		if (holder.tvUlRate != null) {
			long rateUpload = MapUtils.getMapLong(item,
					TransmissionVars.FIELD_PEERS_RATE_TO_PEER_BPS, 0);

			String s = rateUpload > 0
					? "\u25B2 "
							+ DisplayFormatters.formatByteCountToKiBEtcPerSec(rateUpload)
					: "";
			flipper.changeText(holder.tvUlRate, s, animateFlip, validator);
		}
		if (holder.tvDlRate != null) {
			long rateDownload = MapUtils.getMapLong(item,
					TransmissionVars.FIELD_PEERS_RATE_TO_CLIENT_BPS, 0);

			String s = rateDownload > 0
					? "\u25BC "
							+ DisplayFormatters.formatByteCountToKiBEtcPerSec(rateDownload)
					: "";
			flipper.changeText(holder.tvDlRate, s, animateFlip, validator);
		}
		float pctDone = MapUtils.getMapFloat(item,
				TransmissionVars.FIELD_PEERS_PROGRESS, 0f);
		if (holder.tvProgress != null) {
			NumberFormat format = NumberFormat.getPercentInstance();
			format.setMaximumFractionDigits(1);
			String s = format.format(pctDone);
			flipper.changeText(holder.tvProgress, s, animateFlip, validator);
		}

		if (holder.tvIP != null) {
			String s = MapUtils.getMapString(item,
					TransmissionVars.FIELD_PEERS_ADDRESS, "??");
			flipper.changeText(holder.tvIP, s, animateFlip, validator);
		}

		return rowView;
	}

	@Override
	public PeerFilter getFilter() {
		if (filter == null) {
			filter = new PeerFilter(torrentID, new PeerFilterCommunication() {
				@Override
				public void performingFilteringChanged(
						@DelayedFilter.FilterState int filterState,
						@DelayedFilter.FilterState int oldState) {
				}

				@Override
				public void doSort() {
					PeersAdapter.this.doSort();
				}

				@Override
				public void notifyDataSetChanged() {
					PeersAdapter.this.notifyDataSetChanged();
				}

				@Override
				public void setDisplayList(List<Object> list) {
					displayList = list;
				}

				@Override
				public Session getSession() {
					return PeersAdapter.this.getSession();
				}
			}, mLock);
		}
		return filter;
	}

	@Thunk
	@NonNull
	Session getSession() {
		return SessionManager.getSession(remoteProfileID, null, null);
	}

	public static class PeerFilter
		extends DelayedFilter
	{

		private final long torrentID;

		private final PeerFilterCommunication comm;

		private final Object mLock;

		PeerFilter(long torrentID, @NonNull PeerFilterCommunication comm,
				@NonNull Object mLock) {
			super(comm);
			this.torrentID = torrentID;
			this.comm = comm;
			this.mLock = mLock;
		}

		@Override
		protected FilterResults performFiltering2(CharSequence constraint) {
			this.constraint = constraint == null ? "" : constraint.toString();
			FilterResults results = new FilterResults();

			Session session = comm.getSession();
			if (session == null) {
				return results;
			}

			boolean hasConstraint = constraint != null && constraint.length() > 0;

			Map<?, ?> torrent = session.torrent.getCachedTorrent(torrentID);
			List<?> listPeers = MapUtils.getMapList(torrent,
					TransmissionVars.FIELD_TORRENT_PEERS, null);
			if (listPeers == null || listPeers.size() == 0) {
				//System.out.println("performFilter noPeers " + torrent);

				return results;
			}
			if (!hasConstraint) {
				synchronized (mLock) {
					results.values = torrent;
					results.count = listPeers.size();
				}
			}
			return results;
		}

		@Override
		protected boolean publishResults2(CharSequence constraint,
				FilterResults results) {
			Session session = comm.getSession();
			if (session == null) {
				return true;
			}
			synchronized (mLock) {
				Map<?, ?> torrent = session.torrent.getCachedTorrent(torrentID);
				if (torrent == null) {
					return true;
				}
				List<?> listPeers = MapUtils.getMapList(torrent,
						TransmissionVars.FIELD_TORRENT_PEERS, null);
				//					System.out.println("listPeers=" + listPeers);
				if (listPeers == null) {
					return true;
				}
				if (AndroidUtils.DEBUG) {
					System.out.println("listPeers=" + listPeers.size());
				}
				comm.setDisplayList(new ArrayList<>(listPeers));

				comm.doSort();
			}
			comm.notifyDataSetChanged();
			return true;
		}

	}

	@Thunk
	void doSort() {
		if (comparator == null && sortFieldIDs == null || displayList == null) {
			return;
		}
		synchronized (mLock) {

			Collections.sort(displayList, (lhs, rhs) -> {
				Map<?, ?> mapLHS = (Map) lhs;
				Map<?, ?> mapRHS = (Map) rhs;

				if (mapLHS == null || mapRHS == null) {
					return 0;
				}

				if (sortFieldIDs == null) {
					return comparator.compare(mapLHS, mapRHS);
				} else {
					for (int i = 0; i < sortFieldIDs.length; i++) {
						String fieldID = sortFieldIDs[i];
						Comparable oLHS = (Comparable) mapLHS.get(fieldID);
						Comparable oRHS = (Comparable) mapRHS.get(fieldID);
						if (oLHS == null || oRHS == null) {
							if (oLHS != oRHS) {
								return oLHS == null ? -1 : 1;
							} // else == drops to next sort field

						} else {
							int comp = sortOrderAsc[i] ? oLHS.compareTo(oRHS)
									: oRHS.compareTo(oLHS);
							if (comp != 0) {
								return comp;
							} // else == drops to next sort field
						}
					}

					return 0;
				}
			});
		}
	}

	@Override
	public int getCount() {
		return displayList == null ? 0 : displayList.size();
	}

	@Override
	public Map<?, ?> getItem(int position) {
		return displayList == null ? null : (Map<?, ?>) displayList.get(position);
	}

	public void setTorrentID(long torrentID, boolean alwaysRefilter) {
		if (torrentID != this.torrentID) {
			this.torrentID = torrentID;
			//resetFilter();
			getFilter().refilter();
		} else if (alwaysRefilter) {
			getFilter().refilter();
		}
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	public void clearList() {
		synchronized (mLock) {
			if (displayList != null) {
				displayList.clear();
			}
		}
		notifyDataSetChanged();
	}
}
