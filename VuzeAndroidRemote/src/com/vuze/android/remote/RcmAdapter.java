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

package com.vuze.android.remote;

import java.util.*;

import org.gudy.azureus2.core3.util.DisplayFormatters;

import android.content.Context;
import android.content.res.Resources;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.aelitis.azureus.util.MapUtils;

public class RcmAdapter
	extends BaseAdapter
{
	static class ViewHolder
	{
		TextView tvName;

		TextView tvInfo;

		TextView tvTags;

		TextView tvSize;
	}

	private Context context;

	private List<String> displayList = new ArrayList<>();

	private Map<String, Map<?, ?>> mapRCMs = new HashMap<>();

	private Object mLock = new Object();

	private Resources resources;

	private int colorBGTagType0;

	private int colorFGTagType0;

	public RcmAdapter(Context context) {
		super();
		this.context = context;
		resources = context.getResources();
		colorBGTagType0 = resources.getColor(R.color.bg_tag_type_0);
		colorFGTagType0 = resources.getColor(R.color.fg_tag_type_0);
	}

	@Override
	public int getCount() {
		return displayList.size();
	}

	@Override
	public Object getItem(int position) {
		return mapRCMs.get(displayList.get(position));
	}

	@Override
	public long getItemId(int position) {
		return position;
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
			rowView = inflater.inflate(R.layout.row_rcm_list, parent, false);
			ViewHolder viewHolder = new ViewHolder();

			viewHolder.tvName = (TextView) rowView.findViewById(R.id.rcmrow_title);
			viewHolder.tvInfo = (TextView) rowView.findViewById(R.id.rcmrow_info);
			viewHolder.tvTags = (TextView) rowView.findViewById(R.id.rcmrow_tags);
			viewHolder.tvSize = (TextView) rowView.findViewById(R.id.rcmrow_size);

			rowView.setTag(viewHolder);
		}

		ViewHolder holder = (ViewHolder) rowView.getTag();

		Map<?, ?> mapRCM = (Map<?, ?>) getItem(position);

		if (holder.tvName != null) {
			String s = MapUtils.getMapString(mapRCM, "title", "");
			holder.tvName.setText(AndroidUtils.lineBreaker(s));
		}

		if (holder.tvSize != null) {
			long size = MapUtils.getMapLong(mapRCM, "size", 0);
			String s = size <= 0 ? ""
					: DisplayFormatters.formatByteCountToKiBEtc(size);
			holder.tvSize.setText(s);
		}

		if (holder.tvInfo != null) {
			long rank = MapUtils.getMapLong(mapRCM, "rank", 0);
			long numSeeds = MapUtils.getMapLong(mapRCM, "seeds", -1);
			long numPeers = MapUtils.getMapLong(mapRCM, "peers", -1);
			StringBuffer sb = new StringBuffer();

			sb.append("Discovery Strength: " + rank);

			long pubDate = MapUtils.getMapLong(mapRCM, "publishDate", 0);
			if (pubDate > 0) {
				sb.append("\n");
				sb.append("Published "
						+ DateUtils.getRelativeDateTimeString(context, pubDate,
								DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS * 2, 0).toString());
			}

			long lastSeenSecs = MapUtils.getMapLong(mapRCM, "lastSeenSecs", 0);
			if (lastSeenSecs > 0) {
				sb.append('\n');
				sb.append("Last Seen "
						+ DateUtils.getRelativeDateTimeString(context, lastSeenSecs * 1000,
								DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS * 2, 0).toString());
			}

			if (numSeeds >= 0 || numPeers >= 0) {
				sb.append('\n');

				if (numSeeds >= 0) {
					sb.append(numSeeds + " seeds");
				}
				if (numPeers >= 0) {
					if (numSeeds >= 0) {
						sb.append("\u2022 ");
					}
					sb.append(numPeers + " peers");
				}
			}

			holder.tvInfo.setText(sb);
		}

		if (holder.tvTags != null) {
			List<?> listTags = MapUtils.getMapList(mapRCM, "tags",
					Collections.EMPTY_LIST);
			if (listTags.size() == 0) {
				holder.tvTags.setVisibility(View.GONE);
			} else {
				StringBuilder sb = new StringBuilder();

				for (Object object : listTags) {
					if (sb.length() > 0) {
						sb.append(' ');
					}
					sb.append("|");
					sb.append(object.toString());
					sb.append("|");
				}

				SpannableStringBuilder ss = new SpannableStringBuilder(sb);
				String string = sb.toString();
				AndroidUtils.setSpanBubbles(ss, string, "|", holder.tvTags.getPaint(),
						colorBGTagType0, colorFGTagType0, colorBGTagType0);
				holder.tvTags.setText(ss);
				holder.tvTags.setVisibility(View.VISIBLE);
			}
		}

		return rowView;
	}

	public void updateList(List<?> listRCMs) {
		if (listRCMs == null || listRCMs.isEmpty()) {
			return;
		}
		synchronized (mLock) {
			for (Object object : listRCMs) {
				Map<?, ?> mapRCM = (Map<?, ?>) object;
				String hash = MapUtils.getMapString(mapRCM, "hash", null);

				Map<?, ?> old = mapRCMs.put(hash, mapRCM);
				if (old == null) {
					displayList.add(hash);
				}
			}
		}
		notifyDataSetChanged();
	}
}
