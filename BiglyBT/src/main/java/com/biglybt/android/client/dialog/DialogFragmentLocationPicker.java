package com.biglybt.android.client.dialog;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.biglybt.android.TargetFragmentFinder;
import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.AndroidUtilsUI.AlertDialogBuilder;
import com.biglybt.android.client.R;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionManager;

import java.util.ArrayList;
import java.util.List;

public class DialogFragmentLocationPicker
	extends DialogFragmentAbstractLocationPicker
{
	public static void openDialogChooser(String defaultDir,
			Session session, FragmentManager fm) {
		openDialogChooser(null, defaultDir, session, fm, null);
	}

	public static void openDialogChooser(String callbackID, String defaultDir,
			Session session, FragmentManager fm, Fragment targetFragment) {

		DialogFragmentAbstractLocationPicker dlg = new DialogFragmentLocationPicker();
		Bundle bundle = new Bundle();
		bundle.putString(SessionManager.BUNDLE_KEY,
				session.getRemoteProfile().getID());

		bundle.putString(KEY_DEFAULT_DIR, defaultDir);
		bundle.putString(KEY_CALLBACK_ID, callbackID);

		List<String> saveHistory = session.getRemoteProfile().getSavePathHistory();

		ArrayList<String> history = new ArrayList<>(saveHistory.size() + 1);
		if (defaultDir != null && defaultDir.length() > 0) {
			history.add(defaultDir);
		}

		for (String s : saveHistory) {
			if (!history.contains(s)) {
				history.add(s);
			}
		}
		bundle.putStringArrayList(KEY_HISTORY, history);
		dlg.setArguments(bundle);
		if (targetFragment != null) {
			dlg.setTargetFragment(targetFragment, 0);
		}
		AndroidUtilsUI.showDialog(dlg, fm, TAG);
	}

	@Override
	protected void okClicked(Session session, String location) {
		triggerLocationChanged(location);

		dismissDialog();
	}

	@Override
	protected void onCreateBuilder(AlertDialogBuilder alertDialogBuilder) {
		alertDialogBuilder.builder.setTitle(R.string.default_save_location);
	}

}
