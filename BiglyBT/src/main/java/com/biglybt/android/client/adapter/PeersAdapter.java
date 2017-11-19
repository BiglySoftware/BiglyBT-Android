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

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.R;
import com.biglybt.android.client.TransmissionVars;
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
import android.widget.*;

public class PeersAdapter
	extends BaseAdapter
	implements Filterable
{

	static class ViewHolder
	{
		TextView tvIP;

		TextView tvName;

		TextView tvProgress;

		TextView tvUlRate;

		TextView tvDlRate;

		TextView tvCC;

		public String peerID = "";

		public long torrentID = -1;
	}

	public static class ViewHolderFlipValidator
		implements FlipValidator
	{
		private final ViewHolder holder;

		private final String peerID;

		private final long torrentID;

		public ViewHolderFlipValidator(ViewHolder holder, long torrentID,
				String peerID) {
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
	final Object mLock = new Object();

	@Thunk
	Comparator<? super Map<?, ?>> comparator;

	@Thunk
	String[] sortFieldIDs;

	@Thunk
	Boolean[] sortOrderAsc;

	@Thunk
	long torrentID;

	private final TextViewFlipper flipper;

	public PeersAdapter(Context context, String remoteProfileID) {
		this.context = context;
		this.remoteProfileID = remoteProfileID;
		flipper = TextViewFlipper.create();
		displayList = new ArrayList<>();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		return getView(position, convertView, parent, false);
	}

	public void refreshView(int position, View view, ListView listView) {
		getView(position, view, listView, true);
	}

	private View getView(int position, View convertView, ViewGroup parent,
			boolean requireHolder) {
		View rowView = convertView;
		if (rowView == null) {
			if (requireHolder) {
				return null;
			}
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(
					Context.LAYOUT_INFLATER_SERVICE);
			assert inflater != null;
			rowView = inflater.inflate(R.layout.row_peers_list, parent, false);
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
			filter = new PeerFilter();
		}
		return filter;
	}

	@Thunk
	@NonNull
	Session getSession() {
		return SessionManager.getSession(remoteProfileID, null, null);
	}

	public class PeerFilter
		extends Filter
	{

		private int filterMode;

		private CharSequence constraint;

		public void setFilterMode(int filterMode) {
			this.filterMode = filterMode;
			filter(constraint);
		}

		@Override
		protected FilterResults performFiltering(CharSequence constraint) {
			this.constraint = constraint;
			if (AndroidUtils.DEBUG) {
				System.out.println("performFIlter Start");
			}
			FilterResults results = new FilterResults();

			Session session = getSession();

			boolean hasConstraint = constraint != null && constraint.length() > 0;

			Map<?, ?> torrent = session.torrent.getCachedTorrent(torrentID);
			List<?> listPeers = MapUtils.getMapList(torrent,
					TransmissionVars.FIELD_TORRENT_PEERS, null);
			if (listPeers == null || listPeers.size() == 0) {
				//System.out.println("performFIlter noPeers " + torrent);

				return results;
			}
			if (!hasConstraint && filterMode < 0) {
				synchronized (mLock) {
					results.values = torrent;
					results.count = listPeers.size();
				}
				if (AndroidUtils.DEBUG) {
					System.out.println("doall=" + results.count);
				}
			}
			if (AndroidUtils.DEBUG) {
				System.out.println("performFIlter End");
			}
			return results;
		}

		@Override
		protected void publishResults(CharSequence constraint,
				FilterResults results) {
			{
				Session session = getSession();
				synchronized (mLock) {
					Map<?, ?> torrent = session.torrent.getCachedTorrent(torrentID);
					if (torrent == null) {
						return;
					}
					List<?> listPeers = MapUtils.getMapList(torrent,
							TransmissionVars.FIELD_TORRENT_PEERS, null);
					//					System.out.println("listPeers=" + listPeers);
					if (listPeers == null) {
						return;
					}
					if (AndroidUtils.DEBUG) {
						System.out.println("listPeers=" + listPeers.size());
					}
					displayList = new ArrayList<>(listPeers);

					doSort();
				}
				notifyDataSetChanged();
			}
		}

	}

	public void setSort(String[] fieldIDs, Boolean[] sortOrderAsc) {
		synchronized (mLock) {
			sortFieldIDs = fieldIDs;
			Boolean[] order;
			if (sortOrderAsc == null) {
				order = new Boolean[sortFieldIDs.length];
				Arrays.fill(order, Boolean.FALSE);
			} else if (sortOrderAsc.length != sortFieldIDs.length) {
				order = new Boolean[sortFieldIDs.length];
				Arrays.fill(order, Boolean.FALSE);
				System.arraycopy(sortOrderAsc, 0, order, 0, sortOrderAsc.length);
			} else {
				order = sortOrderAsc;
			}
			this.sortOrderAsc = order;
			comparator = null;
		}
		doSort();
		notifyDataSetChanged();
	}

	public void setSort(Comparator<? super Map<?, ?>> comparator) {
		synchronized (mLock) {
			this.comparator = comparator;
		}
		doSort();
		notifyDataSetChanged();
	}

	@Thunk
	void doSort() {
		if (comparator == null && sortFieldIDs == null) {
			return;
		}
		synchronized (mLock) {

			Collections.sort(displayList, new Comparator<Object>() {
				@SuppressWarnings({
					"unchecked",
					"rawtypes"
				})
				@Override
				public int compare(Object lhs, Object rhs) {
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
				}
			});
		}
	}

	@Override
	public int getCount() {
		return displayList.size();
	}

	@Override
	public Map<?, ?> getItem(int position) {
		return (Map<?, ?>) displayList.get(position);
	}

	public void setTorrentID(long id) {
		this.torrentID = id;
		getFilter().filter("");
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	public void clearList() {
		synchronized (mLock) {
			displayList.clear();
		}
		notifyDataSetChanged();
	}

	public void refreshList() {
		getFilter().filter("");
	}
}
