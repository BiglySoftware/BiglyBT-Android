/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.android.client.dialog;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.android.client.*;
import com.biglybt.android.client.AndroidUtilsUI.AlertDialogBuilder;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionManager;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.util.SparseIntArray;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;

public class DialogFragmentRefreshInterval
	extends DialogFragmentBase
{
	private static final String TAG = "RefreshIntervalDialog";

	private NumberPicker npInterval;

	private NumberPicker npIntervalMobile;

	private String remoteProfileID;

	private boolean showIntervalMobile;

	private final SparseIntArray mapPosToSecs = new SparseIntArray();

	private int pos;

	public static void openDialog(FragmentManager fm, String remoteProfileID) {
		DialogFragment dlg = new DialogFragmentRefreshInterval();
		Bundle bundle = new Bundle();
		bundle.putString(SessionManager.BUNDLE_KEY, remoteProfileID);
		dlg.setArguments(bundle);
		AndroidUtilsUI.showDialog(dlg, fm, TAG);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Bundle arguments = getArguments();
		remoteProfileID = arguments.getString(SessionManager.BUNDLE_KEY);

		Session session = SessionManager.getSession(remoteProfileID, null, null);
		RemoteProfile remoteProfile = session.getRemoteProfile();

		AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				getActivity(), R.layout.dialog_refresh_interval);

		View view = alertDialogBuilder.view;
		AlertDialog.Builder builder = alertDialogBuilder.builder;
		Resources resources = getResources();

		npInterval = view.findViewById(R.id.npUpdateInterval);
		npIntervalMobile = view.findViewById(R.id.npUpdateIntervalMobile);
		TextView tvInterval = view.findViewById(R.id.tvUpdateInterval);

		long updateInterval = remoteProfile.getUpdateInterval();
		long updateIntervalMobile = remoteProfile.getUpdateIntervalMobile();
		int initialValue = remoteProfile.isUpdateIntervalEnabled() ? 2 : 0;
		int initialValueMobile = remoteProfile.isUpdateIntervalMobileSeparate()
				? remoteProfile.isUpdateIntervalMobileEnabled() ? 3 : 1 : 0;

		List<String> values = new ArrayList<>();
		mapPosToSecs.put(values.size(), 0);
		values.add(resources.getString(R.string.manual_refresh));

		int[] seconds = {
			1,
			2,
			3,
			4,
			5,
			10,
			15,
			20,
			30,
			40,
			50,
			60,
			90
		};
		for (int i : seconds) {
			String s = resources.getQuantityString(R.plurals.seconds, i, i);
			pos = values.size();
			mapPosToSecs.put(pos, i);
			if (initialValue != 0 && i <= updateInterval) {
				initialValue = pos;
			}
			if (initialValueMobile > 1 && i <= updateIntervalMobile) {
				initialValueMobile = pos;
			}
			values.add(s);
		}
		int[] minutes = {
			2,
			3,
			4,
			5,
			10
		};
		for (int i : minutes) {
			String s = resources.getQuantityString(R.plurals.minutes, i, i);
			pos = values.size();
			mapPosToSecs.put(pos, i * 60);
			if (i * 60 <= updateInterval) {
				initialValue = pos;
			}
			if (initialValueMobile > 1 && i * 60 <= updateIntervalMobile) {
				initialValueMobile = pos;
			}
			values.add(s);
		}

		String[] displayedValues = values.toArray(new String[values.size()]);
		npInterval.setMinValue(0);
		npInterval.setMaxValue(displayedValues.length - 1);
		npInterval.setDisplayedValues(displayedValues);
		npInterval.setValue(initialValue);

		showIntervalMobile = BiglyBTApp.getNetworkState().hasMobileDataCapability();

		tvInterval.setText(showIntervalMobile
				? R.string.rp_update_interval_nonmobile : R.string.rp_update_interval);
		View groupIntervalMobile = view.findViewById(
				R.id.group_refresh_interval_mobile);
		if (groupIntervalMobile != null) {
			groupIntervalMobile.setVisibility(
					showIntervalMobile ? View.VISIBLE : View.GONE);
		}
		if (showIntervalMobile) {
			values.add(0, "Same as non-mobile");
			displayedValues = values.toArray(new String[values.size()]);

			npIntervalMobile.setMinValue(0);
			npIntervalMobile.setMaxValue(values.size() - 1);
			npIntervalMobile.setDisplayedValues(displayedValues);
			npIntervalMobile.setValue(initialValueMobile);
		}

		View buttonArea = view.findViewById(R.id.group_buttons);
		boolean hasButtonArea = buttonArea != null;
		if (hasButtonArea) {
			if (AndroidUtils.isTV()) {
				buttonArea.setVisibility(View.VISIBLE);
				Button btnSet = view.findViewById(R.id.range_set);
				if (btnSet != null) {
					btnSet.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							save();
							DialogFragmentRefreshInterval.this.getDialog().dismiss();
						}
					});
				}

				Button btnCancel = view.findViewById(R.id.range_cancel);
				if (btnCancel != null) {
					btnCancel.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							DialogFragmentRefreshInterval.this.getDialog().dismiss();
						}
					});
				}

			} else {
				buttonArea.setVisibility(View.GONE);
				hasButtonArea = false;
			}
		}

		builder.setTitle(R.string.rp_update_interval);

		if (!hasButtonArea) {
			// Add action buttons
			builder.setPositiveButton(android.R.string.ok,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int id) {

							save();
						}
					});
			builder.setNegativeButton(android.R.string.cancel,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int id) {
							DialogFragmentRefreshInterval.this.getDialog().cancel();
						}
					});
		}

		AlertDialog dialog = builder.create();
		Window window = dialog.getWindow();
		if (window != null) {
			window.setSoftInputMode(
					WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		}

		return dialog;
	}

	private void save() {
		Session session = SessionManager.getSession(remoteProfileID, null, null);
		RemoteProfile remoteProfile = session.getRemoteProfile();

		int pos = npInterval.getValue();
		Integer interval = mapPosToSecs.get(pos);
		boolean enabled = interval != 0;
		remoteProfile.setUpdateIntervalEnabled(enabled);
		if (enabled) {
			remoteProfile.setUpdateInterval(interval);
		}

		int posMobile = npIntervalMobile.getValue();
		if (posMobile == 0) {
			// Same
			remoteProfile.setUpdateIntervalEnabledSeparate(false);
		} else {
			remoteProfile.setUpdateIntervalEnabledSeparate(true);
			Integer intervalMobile = mapPosToSecs.get(posMobile - 1);
			boolean enabledMobile = intervalMobile != 0;
			remoteProfile.setUpdateIntervalMobileEnabled(enabledMobile);
			if (enabledMobile) {
				remoteProfile.setUpdateIntervalMobile(intervalMobile);
			}
		}
		session.saveProfile();

		session.triggerSessionSettingsChanged();
	}

	@Override
	public String getLogTag() {
		return TAG;
	}
}
