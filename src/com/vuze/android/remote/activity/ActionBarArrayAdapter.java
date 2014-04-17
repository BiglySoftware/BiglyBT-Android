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

package com.vuze.android.remote.activity;

import java.util.Comparator;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.vuze.android.remote.*;

public class ActionBarArrayAdapter
	extends ArrayAdapter<RemoteProfile>
{

	private Context context;

	public ActionBarArrayAdapter(Context context) {
		super(context, R.layout.row_actionbar_selector);
		this.context = context;
	}

	public void addRemotes(RemoteProfile[] initialList) {
		setNotifyOnChange(false);
		clear();
		for (RemoteProfile remoteProfile : initialList) {
			add(remoteProfile);
		}
		setNotifyOnChange(true);
		notifyDataSetChanged();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rowView = inflater.inflate(R.layout.row_actionbar_selected, parent,
				false);
		TextView tvLine1 = (TextView) rowView.findViewById(R.id.profilerow_alias);
		TextView tvLine2 = (TextView) rowView.findViewById(R.id.profilerow_since);

		tvLine1.setText(R.string.app_name);

		final RemoteProfile profile = getItem(position);
		tvLine2.setText(profile.getNick());

		return rowView;
	}

	@Override
	public View getDropDownView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rowView = inflater.inflate(R.layout.row_actionbar_selector, parent,
				false);
		TextView tvLine1 = (TextView) rowView.findViewById(R.id.profilerow_alias);
		TextView tvLine2 = (TextView) rowView.findViewById(R.id.profilerow_since);

		final RemoteProfile profile = getItem(position);
		tvLine1.setText(profile.getNick());
		long lastUsedOn = profile.getLastUsedOn();
		if (lastUsedOn == 0) {
			tvLine2.setText(R.string.last_used_never);
		} else {
			String since = DateUtils.getRelativeDateTimeString(context, lastUsedOn,
					DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS * 2, 0).toString();
			String s = context.getResources().getString(R.string.last_used_ago, since);
			tvLine2.setText(s);
		}

		return rowView;
	}

	public int refreshList(RemoteProfile currentProfile) {
		AppPreferences appPreferences = VuzeRemoteApp.getAppPreferences();

		setNotifyOnChange(false);

		RemoteProfile[] remotes = appPreferences.getRemotes();
		clear();
		
		for (RemoteProfile remoteProfile : remotes) {
			add(remoteProfile);
		}
		sort(new Comparator<RemoteProfile>() {
			public int compare(RemoteProfile lhs, RemoteProfile rhs) {
				long diff = rhs.getLastUsedOn() - lhs.getLastUsedOn();
				return diff > 0 ? 1 : diff < 0 ? -1 : 0;
			}
		});
		int pos = -1;
		for (int i = 0; i < getCount(); i++) {
			if (getItem(i).getID().equals(currentProfile.getID())) {
				pos = i;
				break;
			}
		}

		setNotifyOnChange(true);
		notifyDataSetChanged();
		return pos;
	}

	@Override
	public long getItemId(int position) {
		return getItem(position).getID().hashCode();
	}
}
