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

package com.vuze.android.remote.activity;

import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;

import com.vuze.android.remote.*;
import com.vuze.android.remote.adapter.ProfileArrayAdapter;
import com.vuze.android.remote.dialog.DialogFragmentAbout;
import com.vuze.android.remote.dialog.DialogFragmentGenericRemoteProfile;
import com.vuze.android.remote.dialog.DialogFragmentGenericRemoteProfile.GenericRemoteProfileListener;
import com.vuze.android.remote.dialog.DialogFragmentVuzeRemoteProfile;
import com.vuze.android.remote.rpc.RPC;
import com.vuze.util.JSONUtils;

/**
 * Profile Selector screen and Main Intent
 */
public class IntentHandler
	extends AppCompatActivityM
	implements GenericRemoteProfileListener
{

	private static final String TAG = "ProfileSelector";

	private ListView listview;

	private AppPreferences appPreferences;

	private ProfileArrayAdapter adapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "OnCreate");
		}
		AndroidUtilsUI.onCreate(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_intent_handler);

		final Intent intent = getIntent();

		if (handleIntent(intent, savedInstanceState)) {
			return;
		}

		listview = (ListView) findViewById(R.id.lvRemotes);
		assert listview != null;
		listview.setItemsCanFocus(false);

		adapter = new ProfileArrayAdapter(this);

		listview.setAdapter(adapter);

		if (AndroidUtils.DEBUG) {
			Log.d("TUX1", "DS: " + intent.getDataString());
		}

		listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, final View view,
					int position, long id) {
				Object item = parent.getItemAtPosition(position);

				if (item instanceof RemoteProfile) {
					RemoteProfile remote = (RemoteProfile) item;
					boolean isMain = IntentHandler.this.getIntent().getData() != null;
					new RemoteUtils(IntentHandler.this).openRemote(remote, isMain);
					if (isMain) {
						finish();
					}
				}
			}

		});

		Toolbar toolBar = (Toolbar) findViewById(R.id.actionbar);
		if (toolBar != null) {
			setSupportActionBar(toolBar);
		}

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayShowHomeEnabled(true);
			actionBar.setDisplayUseLogoEnabled(true);
			actionBar.setIcon(R.drawable.ic_launcher);
		}

		registerForContextMenu(listview);
	}

	private boolean handleIntent(Intent intent, Bundle savedInstanceState) {
		boolean forceProfileListOpen = (intent.getFlags()
				& Intent.FLAG_ACTIVITY_CLEAR_TOP) > 0;

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "ForceOpen? " + forceProfileListOpen);
			Log.d(TAG, "IntentHandler intent = " + intent);
		}

		appPreferences = VuzeRemoteApp.getAppPreferences();

		Uri data = intent.getData();
		if (data != null) {
			try {
				// check for vuze://remote//*
				String scheme = data.getScheme();
				String host = data.getHost();
				String path = data.getPath();
				if ("vuze".equals(scheme) && "remote".equals(host) && path != null
						&& path.length() > 1) {
					String ac = path.substring(1);
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "got ac '" + ac + "' from " + data);
					}

					intent.setData(null);
					if (ac.equals("cmd=advlogin")) {
						DialogFragmentGenericRemoteProfile dlg = new DialogFragmentGenericRemoteProfile();
						AndroidUtils.showDialog(dlg, getSupportFragmentManager(),
								"GenericRemoteProfile");
						forceProfileListOpen = true;
					} else if (ac.length() < 100) {
						RemoteProfile remoteProfile = new RemoteProfile("vuze", ac);
						new RemoteUtils(this).openRemote(remoteProfile, true);
						finish();
						return true;
					}
				}

				// check for http[s]://remote.vuze.com/ac=*
				if (host != null && host.equals("remote.vuze.com")
						&& data.getQueryParameter("ac") != null) {
					String ac = data.getQueryParameter("ac");
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "got ac '" + ac + "' from " + data);
					}
					intent.setData(null);
					if (ac.length() < 100) {
						RemoteProfile remoteProfile = new RemoteProfile("vuze", ac);
						new RemoteUtils(this).openRemote(remoteProfile, true);
						finish();
						return true;
					}
				}
			} catch (Exception e) {
				if (AndroidUtils.DEBUG) {
					e.printStackTrace();
				}
			}
		}

		if (!forceProfileListOpen) {
			int numRemotes = getRemotesWithLocal().length;
			if (numRemotes == 0) {
				// New User: Send them to Login (Account Creation)
				Intent myIntent = new Intent(Intent.ACTION_VIEW, null, this,
						LoginActivity.class);
				myIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION
						| Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);

				startActivity(myIntent);
				finish();
				return true;
			} else if (numRemotes == 1 || intent.getData() == null) {
				try {
					RemoteProfile remoteProfile = appPreferences.getLastUsedRemote();
					if (remoteProfile != null) {
						if (savedInstanceState == null) {
							new RemoteUtils(this).openRemote(remoteProfile, true);
							finish();
							return true;
						}
					} else {
						Log.d(TAG, "Has Remotes, but no last remote");
					}
				} catch (Throwable t) {
					if (AndroidUtils.DEBUG) {
						Log.e(TAG, "onCreate", t);
					}
					VuzeEasyTracker.getInstance(this).logError(t);
				}
			}
		}
		return false;
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onNewIntent " + intent);
		}
		setIntent(intent);
		if (handleIntent(intent, null)) {
			return;
		}
	}

	private RemoteProfile[] getRemotesWithLocal() {
		RemoteProfile[] remotes = appPreferences.getRemotes();

		if (RPC.isLocalAvailable()) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "Local Vuze Detected");
			}

			boolean alreadyAdded = false;
			for (RemoteProfile remoteProfile : remotes) {
				if ("localhost".equals(remoteProfile.getHost())) {
					alreadyAdded = true;
					break;
				}
			}
			if (!alreadyAdded) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Adding localhost profile..");
				}
				RemoteProfile localProfile = new RemoteProfile(
						RemoteProfile.TYPE_NORMAL);
				localProfile.setHost("localhost");
				localProfile.setNick(
						getString(R.string.local_name, android.os.Build.MODEL));
				RemoteProfile[] newRemotes = new RemoteProfile[remotes.length + 1];
				newRemotes[0] = localProfile;
				System.arraycopy(remotes, 0, newRemotes, 1, remotes.length);
				remotes = newRemotes;
			}
		}
		return remotes;
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (adapter != null) {
			RemoteProfile[] remotesWithLocal = getRemotesWithLocal();
			adapter.addRemotes(remotesWithLocal);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).activityStart(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		VuzeEasyTracker.getInstance(this).activityStop(this);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_bottom);
		ActionBarToolbarSplitter.buildActionBar(this, null,
				R.menu.menu_intenthandler, menu, toolbar);

		getMenuInflater().inflate(R.menu.menu_intenthandler_top, menu);
		return true;
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onPrepareOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		ActionBarToolbarSplitter.prepareToolbar(menu,
				(Toolbar) findViewById(R.id.toolbar_bottom));

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		int itemId = item.getItemId();
		if (itemId == R.id.action_add_profile) {
			Intent myIntent = new Intent(getIntent());
			myIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			myIntent.setClass(IntentHandler.this, LoginActivity.class);
			startActivity(myIntent);
			return true;
		} else if (itemId == R.id.action_adv_login) {
			return AndroidUtils.showDialog(new DialogFragmentGenericRemoteProfile(),
					getSupportFragmentManager(), "GenericRemoteProfile");
		} else if (itemId == R.id.action_about) {
			return AndroidUtils.showDialog(new DialogFragmentAbout(),
					getSupportFragmentManager(), "About");
		} else if (itemId == R.id.action_export_prefs) {
			appPreferences.exportPrefs(this);
		} else if (itemId == R.id.action_import_prefs) {
			AndroidUtils.openFileChooser(this, "application/octet-stream",
					TorrentViewActivity.FILECHOOSER_RESULTCODE);
		}

		return super.onOptionsItemSelected(item);
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
			appPreferences.importPrefs(this, uri);
			adapter.refreshList();
		}
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onCreateContextMenu(android.view.ContextMenu, android.view.View, android.view.ContextMenu.ContextMenuInfo)
	 */
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) menuInfo;
		Object item = listview.getItemAtPosition(adapterMenuInfo.position);

		if (item instanceof RemoteProfile) {
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.menu_context_intenthandler, menu);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem menuitem) {
		ContextMenuInfo menuInfo = menuitem.getMenuInfo();
		AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) menuInfo;

		Object item = listview.getItemAtPosition(adapterMenuInfo.position);

		if (!(item instanceof RemoteProfile)) {
			return super.onContextItemSelected(menuitem);
		}

		final RemoteProfile remoteProfile = (RemoteProfile) item;

		int itemId = menuitem.getItemId();
		if (itemId == R.id.action_edit_pref) {
			editProfile(remoteProfile);
			return true;
		} else if (itemId == R.id.action_delete_pref) {
			new AlertDialog.Builder(this).setTitle("Remove Profile?").setMessage(
					"Configuration settings for profile '" + remoteProfile.getNick()
							+ "' will be deleted.").setPositiveButton("Remove",
									new DialogInterface.OnClickListener() {
										public void onClick(DialogInterface dialog, int which) {
											appPreferences.removeRemoteProfile(remoteProfile.getID());
											adapter.refreshList();
										}
									}).setNegativeButton(android.R.string.cancel,
											new DialogInterface.OnClickListener() {
												public void onClick(DialogInterface dialog, int which) {
												}
											}).setIcon(android.R.drawable.ic_dialog_alert).show();
			return true;
		}
		return super.onContextItemSelected(menuitem);
	}

	public void editProfile(RemoteProfile remoteProfile) {
		DialogFragment dlg = remoteProfile.getRemoteType() == RemoteProfile.TYPE_LOOKUP
				? new DialogFragmentVuzeRemoteProfile()
				: new DialogFragmentGenericRemoteProfile();
		Bundle args = new Bundle();
		Map<?, ?> profileAsMap = remoteProfile.getAsMap(false);
		String profileAsJSON = JSONUtils.encodeToJSON(profileAsMap);
		args.putSerializable("remote.json", profileAsJSON);
		dlg.setArguments(args);
		AndroidUtils.showDialog(dlg, getSupportFragmentManager(),
				"GenericRemoteProfile");
	}

	public void profileEditDone(RemoteProfile oldProfile,
			RemoteProfile newProfile) {
		adapter.refreshList();
	}
}
