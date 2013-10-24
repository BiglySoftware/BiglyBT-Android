package com.vuze.android.remote.activity;

import java.util.*;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.*;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.aelitis.azureus.util.JSONUtils;
import com.vuze.android.remote.*;
import com.vuze.android.remote.dialog.DialogFragmentGenericRemoteProfile;
import com.vuze.android.remote.dialog.DialogFragmentGenericRemoteProfile.GenericRemoteProfileListener;

public class IntentHandler
	extends FragmentActivity
	implements GenericRemoteProfileListener
{

	private ListView listview;

	private ArrayList<Object> list;

	private AppPreferences appPreferences;

	private ArrayAdapter<Object> adapter;

	private ArrayList<Object> newList;

	public class ListItem
	{
		private RemoteProfile remoteProfile;

		public ListItem(RemoteProfile profile) {
			this.remoteProfile = profile;
		}

		public RemoteProfile getRemoteProfile() {
			return remoteProfile;
		}

		@Override
		public String toString() {
			return remoteProfile.getNick();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_intent_handler);

		final Intent intent = getIntent();

		boolean forceOpen = (intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) > 0;

		if (AndroidUtils.DEBUG) {
  		System.out.println("ForceOpen? " + forceOpen);
  		System.out.println("IntentHandler intent = " + intent);
		}

		appPreferences = new AppPreferences(getApplicationContext());

		Uri data = intent.getData();
		if (data != null) {
			if (data.getScheme().equals("vuze") && data.getHost().equals("remote")
					&& data.getPath().length() > 1) {
				String ac = data.getPath().substring(1);
				intent.setData(null);
				if (ac.length() < 100) {
					new RemoteUtils(this).openRemote("vuze", ac, true, true);
					finish();
					return;
				}
			}
		}

		RemoteProfile[] remotes = appPreferences.getRemotes();

		if (!forceOpen) {
			if (remotes.length == 0) {
				// New User: Send them to Login (Account Creation)
				Intent myIntent = new Intent();
				myIntent.setClass(this, LoginActivity.class);
				myIntent.setAction(Intent.ACTION_VIEW);
				myIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION
						| Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);

				startActivity(myIntent);
				finish();
				return;
			} else if (remotes.length == 1 || intent.getData() == null) {
				try {
					RemoteProfile remoteProfile = appPreferences.getLastUsedRemote();
					if (remoteProfile != null) {
						if (savedInstanceState == null) {
							new RemoteUtils(this).openRemote(remoteProfile, true, true);
							finish();
							return;
						}
					} else {
						System.err.println("Has Remotes, but no last remote");
					}
				} catch (Throwable t) {
					if (AndroidUtils.DEBUG) {
						t.printStackTrace();
					}
					VuzeEasyTracker.getInstance(this).logError(this, t);
				}
			}
		}

		listview = (ListView) findViewById(R.id.lvRemotes);

		list = makeList(remotes);

		adapter = new ArrayAdapter<Object>(this, android.R.layout.simple_list_item_1, list);
		listview.setAdapter(adapter);

		listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, final View view,
					int position, long id) {
				Object item = parent.getItemAtPosition(position);

				if (item instanceof String) {
					Intent myIntent = new Intent(intent);
					myIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
					myIntent.setClass(IntentHandler.this, LoginActivity.class);
					myIntent.putExtra("com.vuze.android.remote.login.ac", "");

					startActivity(myIntent);
				} else if (item instanceof ListItem) {
					RemoteProfile remote = ((ListItem) item).getRemoteProfile();
					if (remote != null) {
						new RemoteUtils(IntentHandler.this).openRemote(remote, true, false);
					}
				}
			}

		});

		registerForContextMenu(listview);
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

	private ArrayList<Object> makeList(RemoteProfile[] remotes) {
		Arrays.sort(remotes, new Comparator<RemoteProfile>() {
			public int compare(RemoteProfile lhs, RemoteProfile rhs) {
				long diff = rhs.getLastUsedOn() - lhs.getLastUsedOn();
				return diff > 0 ? 1 : diff < 0 ? -1 : 0;
			}
		});

		// TODO: We could show last used date if we used a nice layout..
		ArrayList<Object> list = new ArrayList<Object>(remotes.length);
		for (RemoteProfile remoteProfile : remotes) {
			list.add(new ListItem(remoteProfile));
		}
		list.add("<New Remote>");

		return list;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) menuInfo;
		Object item = listview.getItemAtPosition(adapterMenuInfo.position);

		if (item instanceof ListItem) {
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.menu_context_intenthandler, menu);
			MenuItem menuItem = menu.findItem(R.id.action_edit_pref);
			String host = ((ListItem) item).getRemoteProfile().getHost();
			menuItem.setEnabled(host != null && host.length() > 0);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem menuitem) {
		ContextMenuInfo menuInfo = menuitem.getMenuInfo();
		AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) menuInfo;

		Object item = listview.getItemAtPosition(adapterMenuInfo.position);

		if (!(item instanceof ListItem)) {
			return super.onContextItemSelected(menuitem);
		}

		final RemoteProfile remoteProfile = ((ListItem) item).getRemoteProfile();

		switch (menuitem.getItemId()) {
			case R.id.action_edit_pref:
				DialogFragmentGenericRemoteProfile dlg = new DialogFragmentGenericRemoteProfile();
				Bundle args = new Bundle();
				Map<?, ?> profileAsMap = remoteProfile.getAsMap(false);
				String profileAsJSON = JSONUtils.encodeToJSON(profileAsMap);
				args.putSerializable("remote.json", profileAsJSON);
				dlg.setArguments(args);
				dlg.show(getSupportFragmentManager(), "GenericRemoteProfile");

				return true;
			case R.id.action_delete_pref:
				new AlertDialog.Builder(this).setTitle("Remove Profile?").setMessage(
						"Configuration settings for profile '" + remoteProfile.getNick()
								+ "' will be deleted.").setPositiveButton("Remove",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								appPreferences.removeRemoteProfile(remoteProfile.getID());
								refreshList();
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

	@Override
	public void profileEditDone(RemoteProfile oldProfile, RemoteProfile newProfile) {
		refreshList();
	}

	private void refreshList() {
		RemoteProfile[] remotes = appPreferences.getRemotes();
		newList = makeList(remotes);
		list.clear();
		list.addAll(newList);
		adapter.notifyDataSetChanged();
	}

}
