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

import java.text.NumberFormat;
import java.util.*;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.vuze.android.remote.*;
import com.vuze.android.remote.TextViewFlipper.FlipValidator;
import com.vuze.util.DisplayFormatters;
import com.vuze.util.MapUtils;

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
		private ViewHolder holder;

		private String peerID;

		private long torrentID;

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

	private Context context;

	private PeerFilter filter;

	/** List of they keys of all entries displayed, in the display order */
	private List<Object> displayList;

	public Object mLock = new Object();

	private Comparator<? super Map<?, ?>> comparator;


	private String[] sortFieldIDs;

	private Boolean[] sortOrderAsc;

	private SessionInfo sessionInfo;

	private long torrentID;

	private TextViewFlipper flipper;

	public PeersAdapter(Context context) {
		this.context = context;
		flipper = new TextViewFlipper(R.anim.anim_field_change);
		displayList = new ArrayList<>();
	}

	public void setSessionInfo(SessionInfo sessionInfo) {
		this.sessionInfo = sessionInfo;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		return getView(position, convertView, parent, false);
	}

	public void refreshView(int position, View view, ListView listView) {
		getView(position, view, listView, true);
	}

	public View getView(int position, View convertView, ViewGroup parent,
			boolean requireHolder) {
		View rowView = convertView;
		if (rowView == null) {
			if (requireHolder) {
				return null;
			}
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			rowView = inflater.inflate(R.layout.row_peers_list, parent, false);
			ViewHolder viewHolder = new ViewHolder();

			viewHolder.tvName = (TextView) rowView.findViewById(R.id.peerrow_client);
			viewHolder.tvCC = (TextView) rowView.findViewById(R.id.peerrow_cc);
			viewHolder.tvDlRate = (TextView) rowView.findViewById(R.id.peerrow_dl);
			viewHolder.tvIP = (TextView) rowView.findViewById(R.id.peerrow_ip);
			viewHolder.tvProgress = (TextView) rowView.findViewById(R.id.peerrow_pct);
			viewHolder.tvUlRate = (TextView) rowView.findViewById(R.id.peerrow_ul);

			rowView.setTag(viewHolder);
		}

		ViewHolder holder = (ViewHolder) rowView.getTag();

		Map<?, ?> item = getItem(position);

		String peerID = MapUtils.getMapString(item, "address", "");
		ViewHolderFlipValidator validator = new ViewHolderFlipValidator(holder,
				torrentID, peerID);
		boolean animateFlip = validator.isStillValid();
		holder.peerID = peerID;
		holder.torrentID = torrentID;

		if (holder.tvName != null) {
			flipper.changeText(holder.tvName,
					MapUtils.getMapString(item, "clientName", "??"), animateFlip,
					validator);
		}
		if (holder.tvCC != null) {
			flipper.changeText(holder.tvCC, MapUtils.getMapString(item, "cc", ""),
					animateFlip, validator);
		}
		if (holder.tvUlRate != null) {
			long rateUpload = MapUtils.getMapLong(item, "rateToPeer", 0);

			String s = rateUpload > 0 ? "\u25B2 "
					+ DisplayFormatters.formatByteCountToKiBEtcPerSec(rateUpload) : "";
			flipper.changeText(holder.tvUlRate, s, animateFlip, validator);
		}
		if (holder.tvDlRate != null) {
			long rateDownload = MapUtils.getMapLong(item, "rateToClient", 0);

			String s = rateDownload > 0 ? "\u25BC "
					+ DisplayFormatters.formatByteCountToKiBEtcPerSec(rateDownload) : "";
			flipper.changeText(holder.tvDlRate, s, animateFlip, validator);
		}
		float pctDone = MapUtils.getMapFloat(item, "progress", 0f);
		if (holder.tvProgress != null) {
			NumberFormat format = NumberFormat.getPercentInstance();
			format.setMaximumFractionDigits(1);
			String s = format.format(pctDone);
			flipper.changeText(holder.tvProgress, s, animateFlip, validator);
		}

		if (holder.tvIP != null) {
			String s = MapUtils.getMapString(item, "address", "??");
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

			if (sessionInfo == null) {
				if (AndroidUtils.DEBUG) {
					System.out.println("noSessionInfo");
				}

				return results;
			}

			boolean hasConstraint = constraint != null && constraint.length() > 0;

			Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
			List<?> listPeers = MapUtils.getMapList(torrent, "peers", null);
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
			} else {
				/*
				// might need to be LinkedHashMap to keep order if filter must be by order
				Map<?, Map<?, ?>> mapCopy = new HashMap<Object, Map<?, ?>>(mapOriginal);

				System.out.println("doSome2=" + mapCopy.size());

				if (hasConstraint) {
					String constraintString = constraint.toString().toLowerCase();

					synchronized (mLock) {
						for (Iterator iterator = mapCopy.keySet().iterator(); iterator.hasNext();) {
							Object key = iterator.next();
							Map map = mapCopy.get(key);

							String name = MapUtils.getMapString(map, TransmissionVars.FIELD_TORRENT_NAME, "").toLowerCase();
							if (!name.contains(constraintString)) {
								iterator.remove();
							}
						}
					}
				}

				System.out.println("doSome2=" + mapCopy.size());

				results.values = mapCopy.keySet();
				results.count = mapCopy.size();
				*/
			}
			if (AndroidUtils.DEBUG) {
				System.out.println("performFIlter End");
			}
			return results;
		}

		@Override
		protected void publishResults(CharSequence constraint, FilterResults results) {
			{
				synchronized (mLock) {
					Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
					if (torrent == null) {
						return;
					}
					List<?> listPeers = MapUtils.getMapList(torrent, "peers", null);
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

	private void doSort() {
		if (sessionInfo == null) {
			return;
		}
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

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getCount()
	 */
	@Override
	public int getCount() {
		return displayList.size();
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItem(int)
	 */
	@Override
	public Map<?, ?> getItem(int position) {
		if (sessionInfo == null) {
			return new HashMap<String, Object>();
		}
		return (Map<?, ?>) displayList.get(position);
	}

	public void setTorrentID(long id) {
		this.torrentID = id;
		getFilter().filter("");
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItemId(int)
	 */
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
