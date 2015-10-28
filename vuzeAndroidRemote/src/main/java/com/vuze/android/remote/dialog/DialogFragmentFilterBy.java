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

package com.vuze.android.remote.dialog;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

import com.vuze.android.remote.*;
import com.vuze.android.remote.AndroidUtils.ValueStringArray;
import com.vuze.android.remote.fragment.SessionInfoGetter;
import com.vuze.util.MapUtils;

public class DialogFragmentFilterBy
	extends DialogFragment
{
	private static final String TAG = "FilterBy";

	public interface FilterByDialogListener
	{
		void filterBy(long val, String item, boolean save);
	}

	public static void openFilterByDialog(Fragment fragment, String id) {
		DialogFragmentFilterBy dlg = new DialogFragmentFilterBy();
		dlg.setTargetFragment(fragment, 0);
		Bundle bundle = new Bundle();
		bundle.putString(SessionInfoManager.BUNDLE_KEY, id);
		dlg.setArguments(bundle);
		AndroidUtils.showDialog(dlg, fragment.getFragmentManager(),
				"OpenFilterDialog");
	}

	private FilterByDialogListener mListener;

	private ValueStringArray filterByList;

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		SessionInfo sessionInfo = getSessionInfo();
		List<Map<?, ?>> tags = sessionInfo == null ? null : sessionInfo.getTags();
		if (tags != null && tags.size() > 0) {
			TreeMap<String, Long> map = new TreeMap<String, Long>();
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
			String[] strings = map.keySet().toArray(new String[0]);
			for (int i = 0; i < vals.length; i++) {
				vals[i] = map.get(strings[i]);
			}

			filterByList = new ValueStringArray(vals, strings);
		}

		if (filterByList == null) {
			filterByList = AndroidUtils.getValueStringArray(getResources(),
					R.array.filterby_list);
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.filterby_title);
		builder.setItems(filterByList.strings,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						if (mListener == null) {
							return;
						}
						// quick hack to remove "Download State:".. should do something better
						mListener.filterBy(filterByList.values[which],
								filterByList.strings[which].replaceAll("Download State: ", ""),
								true);
					}
				});
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
