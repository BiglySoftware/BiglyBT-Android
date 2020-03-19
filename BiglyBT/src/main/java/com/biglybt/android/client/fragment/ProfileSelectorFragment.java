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

package com.biglybt.android.client.fragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import com.biglybt.android.client.*;
import com.biglybt.android.client.activity.LoginActivity;
import com.biglybt.android.client.activity.TorrentViewActivity;
import com.biglybt.android.client.adapter.ProfileArrayAdapter;
import com.biglybt.android.client.dialog.DialogFragmentAbout;
import com.biglybt.android.client.dialog.DialogFragmentGenericRemoteProfile;
import com.biglybt.android.client.dialog.DialogFragmentGiveback;
import com.biglybt.android.client.rpc.RPC;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.client.session.RemoteProfileFactory;
import com.biglybt.android.util.FileUtils;
import com.biglybt.util.Thunk;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Created by TuxPaper on 2020-01-07.
 */
public class ProfileSelectorFragment
	extends FragmentM
	implements AppPreferences.AppPreferencesChangedListener

{
	private static final String TAG = "ProfileSelector";

	private Boolean isLocalVuzeAvailable = null;

	private Boolean isLocalVuzeRemoteAvailable = null;

	private ListView listview;

	@Thunk
	ProfileArrayAdapter adapter;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		setHasOptionsMenu(true);
		return inflater.inflate(
				AndroidUtils.isTV(getContext()) ? R.layout.activity_intent_handler_tv
						: R.layout.activity_intent_handler,
				container, false);
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		AppCompatActivityM activity = (AppCompatActivityM) requireActivity();

		listview = activity.findViewById(R.id.lvRemotes);
		assert listview != null;
		listview.setItemsCanFocus(false);

		adapter = new ProfileArrayAdapter(activity);

		listview.setAdapter(adapter);

		listview.setOnItemClickListener((parent, view, position, id) -> {
			Object item = parent.getItemAtPosition(position);

			if (item instanceof RemoteProfile) {
				RemoteProfile remote = (RemoteProfile) item;
				boolean isMain = activity.getIntent().getData() != null;
				RemoteUtils.openRemote(activity, remote, isMain, isMain);
			}
		});

		Toolbar toolBar = activity.findViewById(R.id.actionbar);
		if (toolBar != null) {
			activity.setSupportActionBar(toolBar);
		}

		ActionBar actionBar = activity.getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayShowHomeEnabled(true);
			actionBar.setDisplayUseLogoEnabled(true);
			actionBar.setIcon(R.drawable.biglybt_logo_toolbar);
		}

		Button btnAdd = activity.findViewById(R.id.button_profile_add);
		if (btnAdd != null) {
			btnAdd.setOnClickListener(v -> {
				Intent myIntent = new Intent(activity.getIntent());
				myIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
				myIntent.setClass(activity, LoginActivity.class);
				startActivity(myIntent);
			});
		}

		Button btnImport = activity.findViewById(R.id.button_profile_import);
		if (btnImport != null) {
			btnImport.setOnClickListener(v -> FileUtils.openFileChooser(activity,
					this, "application/octet-stream",
					TorrentViewActivity.FILECHOOSER_RESULTCODE));
		}
		Button btnExport = activity.findViewById(R.id.button_profile_export);
		if (btnExport != null) {
			btnExport.setOnClickListener(v -> AppPreferences.exportPrefs(activity));
		}
		registerForContextMenu(listview);
	}

	@Override
	public void onPause() {
		super.onPause();
		AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
		appPreferences.removeAppPreferencesChangedListener(this);
		isLocalVuzeRemoteAvailable = null;
		isLocalVuzeAvailable = null;
	}

	@Override
	public void onResume() {
		super.onResume();

		if (adapter != null) {
			RemoteProfile[] remotesWithLocal = getRemotesWithLocal();
			adapter.addRemotes(remotesWithLocal);
		}
		AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
		appPreferences.addAppPreferencesChangedListener(this);
	}

	@Override
	public void onCreateOptionsMenu(@NonNull Menu menu,
			@NonNull MenuInflater inflater) {
		inflater.inflate(R.menu.menu_intenthandler_top, menu);
	}

	@Override
	public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v,
			ContextMenu.ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		AdapterView.AdapterContextMenuInfo adapterMenuInfo = (AdapterView.AdapterContextMenuInfo) menuInfo;
		Object item = listview.getItemAtPosition(adapterMenuInfo.position);

		if (item instanceof RemoteProfile) {
			MenuInflater inflater = requireActivity().getMenuInflater();
			inflater.inflate(R.menu.menu_context_intenthandler, menu);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		AppCompatActivityM activity = (AppCompatActivityM) requireActivity();
		int itemId = item.getItemId();
		if (itemId == R.id.action_add_profile) {
			Intent myIntent = new Intent(activity.getIntent());
			myIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			myIntent.setClass(activity, LoginActivity.class);
			startActivity(myIntent);
			return true;
		} else if (itemId == R.id.action_add_adv_profile) {
			return AndroidUtilsUI.showDialog(new DialogFragmentGenericRemoteProfile(),
					activity.getSupportFragmentManager(),
					DialogFragmentGenericRemoteProfile.TAG);
		} else if (itemId == R.id.action_about) {
			return AndroidUtilsUI.showDialog(new DialogFragmentAbout(),
					activity.getSupportFragmentManager(), "About");
		} else if (itemId == R.id.action_giveback) {
			DialogFragmentGiveback.openDialog(activity,
					activity.getSupportFragmentManager(), true, TAG);
			return true;
		} else if (itemId == R.id.action_export_prefs) {
			AppPreferences.exportPrefs(activity);
		} else if (itemId == R.id.action_import_prefs) {
			FileUtils.openFileChooser(activity, this, "application/octet-stream",
					TorrentViewActivity.FILECHOOSER_RESULTCODE);
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onContextItemSelected(MenuItem menuitem) {
		ContextMenu.ContextMenuInfo menuInfo = menuitem.getMenuInfo();
		AdapterView.AdapterContextMenuInfo adapterMenuInfo = (AdapterView.AdapterContextMenuInfo) menuInfo;

		Object item = listview.getItemAtPosition(adapterMenuInfo.position);

		if (!(item instanceof RemoteProfile)) {
			return super.onContextItemSelected(menuitem);
		}

		final RemoteProfile remoteProfile = (RemoteProfile) item;

		int itemId = menuitem.getItemId();
		if (itemId == R.id.action_edit_pref) {
			// XXX requireActivity().getSupportFragmentManager();
			RemoteUtils.editProfile(remoteProfile, requireFragmentManager(), false);
			return true;
		} else if (itemId == R.id.action_delete_pref) {
			final String message = getString(R.string.dialog_remove_profile_text,
					remoteProfile.getNick());
			new MaterialAlertDialogBuilder(requireContext()).setTitle(
					R.string.dialog_remove_profile_title).setMessage(
							message).setPositiveButton(R.string.button_remove,
									(dialog, which) -> {
										AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
										appPreferences.removeRemoteProfile(remoteProfile.getID());
									}).setNegativeButton(android.R.string.cancel,
											(dialog, which) -> {
											}).setIcon(android.R.drawable.ic_dialog_alert).show();
			return true;
		}
		return super.onContextItemSelected(menuitem);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onActivityResult: " + requestCode + "/" + resultCode);
		}
		if (requestCode == TorrentViewActivity.FILECHOOSER_RESULTCODE) {
			Uri uri = intent == null || resultCode != Activity.RESULT_OK ? null
					: intent.getData();
			if (uri == null) {
				return;
			}
			AppPreferences.importPrefs((AppCompatActivityM) requireActivity(), uri);
			return;
		}
		super.onActivityResult(requestCode, resultCode, intent);
	}

	@Override
	public void appPreferencesChanged() {
		requireActivity().runOnUiThread(() -> {
			if (adapter != null) {
				adapter.refreshList();
			}
		});
	}

	private boolean isLocalVuzeRemoteAvailable() {
		if (isLocalVuzeRemoteAvailable == null) {
			isLocalVuzeRemoteAvailable = RPC.isLocalAvailable(
					RPC.LOCAL_VUZE_REMOTE_PORT);
		}
		return isLocalVuzeRemoteAvailable;
	}

	private RemoteProfile[] getRemotesWithLocal() {
		AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
		RemoteProfile[] remotes = appPreferences.getRemotes();

		if (isLocalVuzeAvailable()) {
			remotes = addLocalRemoteToArray(remotes, RPC.LOCAL_VUZE_PORT,
					R.string.local_vuze_name);
		}
		if (isLocalVuzeRemoteAvailable()) {
			remotes = addLocalRemoteToArray(remotes, RPC.LOCAL_VUZE_REMOTE_PORT,
					R.string.local_vuze_remote_name);
		}
		return remotes;
	}

	private RemoteProfile[] addLocalRemoteToArray(RemoteProfile[] remotes,
			int port, int resNickID) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "Local BiglyBT Detected");
		}

		boolean alreadyAdded = false;
		for (RemoteProfile remoteProfile : remotes) {
			if (remoteProfile.getRemoteType() == RemoteProfile.TYPE_NORMAL
					&& "localhost".equals(remoteProfile.getHost())) {
				alreadyAdded = true;
				break;
			}
		}
		if (!alreadyAdded) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "Adding localhost profile..");
			}
			RemoteProfile localProfile = RemoteProfileFactory.create(
					RemoteProfile.TYPE_NORMAL);
			localProfile.setHost("localhost");
			localProfile.setPort(port);
			localProfile.setNick(getString(resNickID,
					BiglyBTApp.deviceName == null ? Build.MODEL : BiglyBTApp.deviceName));
			RemoteProfile[] newRemotes = new RemoteProfile[remotes.length + 1];
			newRemotes[0] = localProfile;
			System.arraycopy(remotes, 0, newRemotes, 1, remotes.length);
			remotes = newRemotes;
		}
		return remotes;
	}

	private boolean isLocalVuzeAvailable() {
		if (isLocalVuzeAvailable == null) {
			isLocalVuzeAvailable = RPC.isLocalAvailable(RPC.LOCAL_VUZE_PORT);
		}
		return isLocalVuzeAvailable;
	}

}
