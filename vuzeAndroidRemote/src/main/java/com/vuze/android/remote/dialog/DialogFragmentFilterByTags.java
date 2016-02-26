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

package com.vuze.android.remote.dialog;

import java.util.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TabHost;
import android.widget.TextView;

import com.vuze.android.remote.*;
import com.vuze.android.remote.AndroidUtils.ValueStringArray;
import com.vuze.android.remote.dialog.DialogFragmentFilterBy.FilterByDialogListener;
import com.vuze.android.remote.fragment.SessionInfoGetter;
import com.vuze.android.remote.spanbubbles.SpanTags;
import com.vuze.android.remote.spanbubbles.SpanTags.SpanTagsListener;
import com.vuze.util.MapUtils;

public class DialogFragmentFilterByTags
	extends DialogFragment
{
	private static final String TAG = "FilterBy";

	private Map mapSelectedTag;

	private FilterByDialogListener mListener;

	private ValueStringArray filterByList;

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		SessionInfo sessionInfo = getSessionInfo();
		List<Map<?, ?>> tags = sessionInfo == null ? null : sessionInfo.getTags();
		if (tags != null && tags.size() > 0) {
			TreeMap<String, Long> map = new TreeMap<>();
			for (Object o : tags) {
				if (o instanceof Map) {
					Map<?, ?> mapTag = (Map<?, ?>) o;
					long uid = MapUtils.getMapLong(mapTag, "uid", 0);
					String name = MapUtils.getMapString(mapTag, "name", "??");
					int type = MapUtils.getMapInt(mapTag, "type", 0);
					if (type == 3) {
						// type-name will be "Manual" :(
						name = "Tag: " + name;
					} else {
						String typeName = MapUtils.getMapString(mapTag, "type-name", null);
						if (typeName != null) {
							name = typeName + ": " + name;
						}
					}
					map.put(name, uid);
				}
			}

			long[] vals = new long[map.size()];
			String[] strings = map.keySet().toArray(new String[map.keySet().size()]);
			for (int i = 0; i < vals.length; i++) {
				vals[i] = map.get(strings[i]);
			}

			filterByList = new ValueStringArray(vals, strings);
		}

		if (filterByList == null) {
			filterByList = AndroidUtils.getValueStringArray(getResources(),
					R.array.filterby_list);
		}

		AndroidUtils.AlertDialogBuilder alertDialogBuilder = AndroidUtils.createAlertDialogBuilder(
				getActivity(), R.layout.dialog_filter_by);

		View view = alertDialogBuilder.view;
		AlertDialog.Builder builder = alertDialogBuilder.builder;

		// get our tabHost from the xml
		TabHost tabHost = (TabHost) view.findViewById(R.id.filterby_tabhost);
		tabHost.setup();

		// create tab 1
		TabHost.TabSpec spec1 = tabHost.newTabSpec("tab1");
		spec1.setIndicator("States");
		spec1.setContent(R.id.filterby_sv_state);
		tabHost.addTab(spec1);
		//create tab2
		TabHost.TabSpec spec2 = tabHost.newTabSpec("tab2");
		spec2.setIndicator("Tags");
		spec2.setContent(R.id.filterby_tv_tags);
		tabHost.addTab(spec2);

		TextView tvState = (TextView) view.findViewById(R.id.filterby_tv_state);
		tvState.setMovementMethod(LinkMovementMethod.getInstance());

		TextView tvTags = (TextView) view.findViewById(R.id.filterby_tv_tags);
		tvTags.setMovementMethod(LinkMovementMethod.getInstance());

		builder.setTitle(R.string.filterby_title);

		// Add action buttons
		builder.setPositiveButton(R.string.action_filterby,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						if (mapSelectedTag == null) {
							return;
						}
						long uidSelected = MapUtils.getMapLong(mapSelectedTag, "uid", -1);
						String name = MapUtils.getMapString(mapSelectedTag, "name", "??");

						mListener.filterBy(uidSelected, name, true);
					}
				});
		builder.setNegativeButton(android.R.string.cancel,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						DialogFragmentFilterByTags.this.getDialog().cancel();
					}
				});

		List<Map<?, ?>> manualTags = new ArrayList<>();
		List<Map<?, ?>> stateTags = new ArrayList<>();

		if (sessionInfo != null) {
			// Dialog never gets called wehn getTags has no tags
			List<Map<?, ?>> allTags = sessionInfo.getTags();
			if (allTags != null) {
				for (Map<?, ?> mapTag : allTags) {
					int type = MapUtils.getMapInt(mapTag, "type", 0);
					switch (type) {
						case 0:
						case 1:
						case 2:
							stateTags.add(mapTag);
							break;
						case 3: // manual
							manualTags.add(mapTag);
							break;
					}
				}
			}
		}

		SpanTagsListener l = new SpanTagsListener() {
			@Override
			public void tagClicked(Map mapTag, String name) {
				mapSelectedTag = mapTag;
				// todo: long click, don't exit
				long uidSelected = MapUtils.getMapLong(mapSelectedTag, "uid", -1);
				mListener.filterBy(uidSelected, name, true);
				DialogFragmentFilterByTags.this.getDialog().dismiss();
			}

			@Override
			public int getTagState(Map mapTag, String name) {
				if (mapSelectedTag == null) {
					return SpanTags.TAG_STATE_UNSELECTED;
				}
				long uidSelected = MapUtils.getMapLong(mapSelectedTag, "uid", -1);
				if (uidSelected == -1) {
					return SpanTags.TAG_STATE_UNSELECTED;
				}
				long uidQuery = MapUtils.getMapLong(mapTag, "uid", -1);
				return uidQuery == uidSelected ? SpanTags.TAG_STATE_SELECTED
						: SpanTags.TAG_STATE_UNSELECTED;
			}
		};
		SpanTags spanTags = new SpanTags(getActivity(), sessionInfo, tvTags, l);
		spanTags.setTagMaps(manualTags);
		spanTags.setShowIcon(false);
		spanTags.updateTags();

		SpanTags spanState = new SpanTags(getActivity(), sessionInfo, tvState, l);
		spanState.setTagMaps(stateTags);
		spanState.setShowIcon(false);
		spanState.updateTags();

		return builder.create();
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		Fragment targetFragment = getTargetFragment();
		if (targetFragment instanceof FilterByDialogListener) {
			mListener = (FilterByDialogListener) targetFragment;
		} else if (activity instanceof FilterByDialogListener) {
			mListener = (FilterByDialogListener) activity;
		} else {
			Log.e(TAG, "No Target Fragment " + targetFragment);
		}

	}

	@Override
	public void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).fragmentStart(this, TAG);
	}

	@Override
	public void onStop() {
		super.onStop();
		VuzeEasyTracker.getInstance(this).fragmentStop(this);
	}

	private SessionInfo getSessionInfo() {
		FragmentActivity activity = getActivity();
		if (activity instanceof SessionInfoGetter) {
			SessionInfoGetter sig = (SessionInfoGetter) activity;
			return sig.getSessionInfo();
		}

		Bundle arguments = getArguments();
		if (arguments == null) {
			return null;
		}
		String profileID = arguments.getString(SessionInfoManager.BUNDLE_KEY);
		if (profileID == null) {
			return null;
		}
		return SessionInfoManager.getSessionInfo(profileID, activity);
	}
}
