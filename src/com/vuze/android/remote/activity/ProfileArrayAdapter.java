package com.vuze.android.remote.activity;

import java.util.Comparator;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import com.vuze.android.remote.*;

public class ProfileArrayAdapter
	extends ArrayAdapter<RemoteProfile>
{

	private Context context;

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
		setNotifyOnChange(true);
		notifyDataSetChanged();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rowView = inflater.inflate(R.layout.row_profile_selector, parent,
				false);
		TextView tvNick = (TextView) rowView.findViewById(R.id.profilerow_alias);
		TextView tvSince = (TextView) rowView.findViewById(R.id.profilerow_since);
		ImageButton ibEdit = (ImageButton) rowView.findViewById(R.id.profilerow_edit);

		final RemoteProfile profile = getItem(position);
		tvNick.setText(profile.getNick());
		long lastUsedOn = profile.getLastUsedOn();
		if (lastUsedOn == 0) {
			tvSince.setText(R.string.last_used_never);
		} else {
			String since = DateUtils.getRelativeDateTimeString(context, lastUsedOn,
					DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS * 2, 0).toString();
			String s = context.getResources().getString(R.string.last_used_ago,
					since);
			tvSince.setText(s);
		}

		ibEdit.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				((IntentHandler) context).editProfile(profile);
			}
		});

		return rowView;
	}

	public void refreshList() {
		AppPreferences appPreferences = VuzeRemoteApp.getAppPreferences();

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

		notifyDataSetChanged();
	}

}
