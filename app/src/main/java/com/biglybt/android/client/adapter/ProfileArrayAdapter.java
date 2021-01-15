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

import com.biglybt.android.client.*;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.util.Thunk;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

/**
 * Profile List adapter for {IntentHandler}
 */
public class ProfileArrayAdapter
	extends ArrayAdapter<RemoteProfile>
{

	@Thunk
	Context context;

	public ProfileArrayAdapter(Context context) {
		super(context, R.layout.row_profile_selector);
		this.context = context;
	}

	public void addRemotes(RemoteProfile[] initialList) {
		setNotifyOnChange(false);
		clear();
		for (RemoteProfile remoteProfile : initialList) {
			add(remoteProfile);
		}
		sort((lhs, rhs) -> {
			long diff = rhs.getLastUsedOn() - lhs.getLastUsedOn();
			return diff > 0 ? 1 : diff < 0 ? -1 : 0;
		});
		setNotifyOnChange(true);
		notifyDataSetChanged();
	}

	@NonNull
	@Override
	public View getView(int position, View rowView, @NonNull ViewGroup parent) {
		if (rowView == null) {
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(
					Context.LAYOUT_INFLATER_SERVICE);
			rowView = inflater.inflate(R.layout.row_profile_selector, parent, false);
		}
		TextView tvNick = rowView.findViewById(R.id.profilerow_alias);
		TextView tvSince = rowView.findViewById(R.id.profilerow_since);
		ImageButton ibEdit = rowView.findViewById(R.id.profilerow_edit);

		final RemoteProfile profile = getItem(position);
		if (profile == null) {
			return rowView;
		}
		tvNick.setText(profile.getNick());
		long lastUsedOn = profile.getLastUsedOn();
		if (lastUsedOn == 0) {
			tvSince.setText(R.string.last_used_never);
		} else {
			String since = DateUtils.getRelativeDateTimeString(context, lastUsedOn,
					DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS * 2,
					0).toString();
			String s = context.getResources().getString(R.string.last_used_ago,
					since);
			tvSince.setText(s);
		}

		if (ibEdit != null) {
			ibEdit.setOnClickListener(v -> RemoteUtils.editProfile(profile,
					((AppCompatActivity) context).getSupportFragmentManager(), false));
		}

		return rowView;
	}

	public void refreshList() {
		AppPreferences appPreferences = BiglyBTApp.getAppPreferences();

		RemoteProfile[] remotes = appPreferences.getRemotes();
		clear();
		for (RemoteProfile remoteProfile : remotes) {
			add(remoteProfile);
		}
		sort((lhs, rhs) -> {
			long diff = rhs.getLastUsedOn() - lhs.getLastUsedOn();
			return diff > 0 ? 1 : diff < 0 ? -1 : 0;
		});

		notifyDataSetChanged();
	}

}
