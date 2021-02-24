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

import android.app.Dialog;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.SparseIntArray;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.biglybt.android.client.*;
import com.biglybt.android.client.AndroidUtilsUI.AlertDialogBuilder;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.util.Thunk;

import java.util.ArrayList;
import java.util.List;

public class DialogFragmentRefreshInterval
	extends DialogFragmentBase
{
	private static final String TAG = "RefreshIntervalDialog";

	private NumberPicker npInterval;

	private NumberPicker npIntervalMobile;

	private final SparseIntArray mapPosToSecs = new SparseIntArray();

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
		Session session = SessionManager.findOrCreateSession(this, null);
		if (session == null) {
			AnalyticsTracker.getInstance(this).logError("session null", TAG);
			return super.onCreateDialog(savedInstanceState);
		}

		RemoteProfile remoteProfile = session.getRemoteProfile();

		AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				requireActivity(), R.layout.dialog_refresh_interval);

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
		int pos;
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

		String[] displayedValues = values.toArray(new String[0]);
		npInterval.setSaveFromParentEnabled(false);
		npInterval.setSaveEnabled(false);
		npInterval.setMinValue(0);
		npInterval.setMaxValue(displayedValues.length - 1);
		npInterval.setDisplayedValues(displayedValues);
		npInterval.setValue(initialValue);

		boolean showIntervalMobile = BiglyBTApp.getNetworkState().hasMobileDataCapability();

		tvInterval.setVisibility(showIntervalMobile ? View.VISIBLE : View.GONE);
		View groupIntervalMobile = view.findViewById(
				R.id.group_refresh_interval_mobile);
		if (groupIntervalMobile != null) {
			groupIntervalMobile.setVisibility(
					showIntervalMobile ? View.VISIBLE : View.GONE);
		}
		if (showIntervalMobile) {
			values.add(0, getString(R.string.rp_update_interval_mobile_same));
			displayedValues = values.toArray(new String[0]);

			npIntervalMobile.setSaveFromParentEnabled(false);
			npIntervalMobile.setSaveEnabled(false);
			npIntervalMobile.setMinValue(0);
			npIntervalMobile.setMaxValue(values.size() - 1);
			npIntervalMobile.setDisplayedValues(displayedValues);
			npIntervalMobile.setValue(initialValueMobile);
		}

		View buttonArea = view.findViewById(R.id.group_buttons);
		boolean hasButtonArea = buttonArea != null;
		if (hasButtonArea) {
			if (AndroidUtils.isTV(getContext())) {
				buttonArea.setVisibility(View.VISIBLE);
				Button btnSet = view.findViewById(R.id.range_set);
				if (btnSet != null) {
					btnSet.setOnClickListener(v -> {
						save();
						dismissDialog();
					});
				}

				Button btnCancel = view.findViewById(R.id.range_cancel);
				if (btnCancel != null) {
					btnCancel.setOnClickListener(v -> dismissDialog());
				}

				// Next Focus right goes to cancel button, so force to set button
				if (showIntervalMobile) {
					npIntervalMobile.setNextFocusRightId(R.id.range_set);
				} else {
					npInterval.setNextFocusRightId(R.id.range_set);
				}

			} else {
				buttonArea.setVisibility(View.GONE);
				hasButtonArea = false;
			}
		}

		builder.setTitle(R.string.rp_update_interval);

		if (!hasButtonArea) {
			// Add action buttons
			builder.setPositiveButton(android.R.string.ok, (dialog, id) -> save());
			builder.setNegativeButton(android.R.string.cancel,
					(dialog, id) -> cancelDialog());
		}

		AlertDialog dialog = builder.create();
		Window window = dialog.getWindow();
		if (window != null) {
			window.setSoftInputMode(
					WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		}

		return dialog;
	}

	@Override
	public void onStart() {
		super.onStart();
		Session session = SessionManager.findOrCreateSession(this, null);
		if (session == null) {
			this.dismissAllowingStateLoss();
		}
	}

	@Thunk
	void save() {
		Session session = SessionManager.findOrCreateSession(this, null);
		if (session == null) {
			return;
		}
		RemoteProfile remoteProfile = session.getRemoteProfile();

		int pos = npInterval.getValue();
		int interval = mapPosToSecs.get(pos);
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
			int intervalMobile = mapPosToSecs.get(posMobile - 1);
			boolean enabledMobile = intervalMobile != 0;
			remoteProfile.setUpdateIntervalMobileEnabled(enabledMobile);
			if (enabledMobile) {
				remoteProfile.setUpdateIntervalMobile(intervalMobile);
			}
		}
		session.saveProfile();

		session.updateSessionSettings(session.getSessionSettingsClone());
	}
}
