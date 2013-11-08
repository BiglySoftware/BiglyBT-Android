package com.vuze.android.remote.activity;

import java.util.Comparator;

import org.gudy.azureus2.core3.util.DisplayFormatters;

import android.content.Context;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import com.vuze.android.remote.AppPreferences;
import com.vuze.android.remote.R;
import com.vuze.android.remote.RemoteProfile;

public class ProfileArrayAdapter
	extends ArrayAdapter<RemoteProfile>
{

	private Context context;

	public ProfileArrayAdapter(Context context, RemoteProfile[] initialList) {
		super(context, R.layout.row_profile_selector);
		this.context = context;

		for (RemoteProfile remoteProfile : initialList) {
			add(remoteProfile);
		}
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rowView = inflater.inflate(R.layout.row_profile_selector, parent,
				false);
		TextView tvNick = (TextView) rowView.findViewById(R.id.profilerow_alias);
		TextView tvSince = (TextView) rowView.findViewById(R.id.profilerow_since);
		ImageButton ibEdit = (ImageButton) rowView.findViewById(R.id.profilerow_edit);

		Object object = getItem(position);
		if (object instanceof RemoteProfile) {
			final RemoteProfile profile = (RemoteProfile) object;
			tvNick.setText(profile.getNick());
			long lastUsedOn = profile.getLastUsedOn();
			if (lastUsedOn == 0) {
				tvSince.setText(R.string.last_used_never);
			} else {
				long sinceMS = System.currentTimeMillis() - lastUsedOn;
				String since = DisplayFormatters.prettyFormat(sinceMS / 1000);
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

		}

		return rowView;
	}

	public void refreshList() {
		AppPreferences appPreferences = new AppPreferences(context);

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
