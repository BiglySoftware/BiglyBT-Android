package com.biglybt.android.client.dialog;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.android.TargetFragmentFinder;
import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.AndroidUtilsUI.AlertDialogBuilder;
import com.biglybt.android.client.R;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionManager;

import android.os.Bundle;

import androidx.fragment.app.FragmentManager;

public class DialogFragmentLocationPicker
	extends DialogFragmentAbstractLocationPicker
{
	public static void openDialogChooser(String defaultDir, Session session,
		FragmentManager fm) {

		DialogFragmentAbstractLocationPicker dlg = new DialogFragmentLocationPicker();
		Bundle bundle = new Bundle();
		bundle.putString(SessionManager.BUNDLE_KEY,
			session.getRemoteProfile().getID());

		bundle.putString(KEY_DEFAULT_DIR, defaultDir);

		List<String> saveHistory = session.getRemoteProfile().getSavePathHistory();

		ArrayList<String> history = new ArrayList<>(saveHistory.size() + 1);
		if (defaultDir != null) {
			history.add(defaultDir);
		}

		for (String s : saveHistory) {
			if (!history.contains(s)) {
				history.add(s);
			}
		}
		bundle.putStringArrayList(KEY_HISTORY, history);
		dlg.setArguments(bundle);
		AndroidUtilsUI.showDialog(dlg, fm, TAG);
	}

	@Override
	protected void okClicked(Session session, String location) {
		LocationPickerListener listener = new TargetFragmentFinder<LocationPickerListener>(
			LocationPickerListener.class).findTarget(this,
			requireContext());
		if (listener != null) {
			listener.locationChanged(location);
		}

		dismissDialog();
	}

	@Override
	protected void onCreateBuilder(AlertDialogBuilder alertDialogBuilder) {
		alertDialogBuilder.builder.setTitle(R.string.default_save_location);
	}

}
