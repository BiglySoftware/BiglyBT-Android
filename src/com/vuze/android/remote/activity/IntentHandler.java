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
import com.vuze.android.remote.DialogFragmentGenericRemotePreferences.GenericRemoteProfileListener;

public class IntentHandler
	extends FragmentActivity
	implements GenericRemoteProfileListener
{

	private ListView listview;

	private ArrayList list;

	private AppPreferences appPreferences;

	public class ListItem
	{
		RemotePreferences pref;

		public ListItem(RemotePreferences pref) {
			this.pref = pref;
		}

		public RemotePreferences getRemotePreferences() {
			return pref;
		}

		@Override
		public String toString() {
			return pref.getNick();
		}
	}

	@SuppressWarnings("rawtypes")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_intent_handler);

		final Intent intent = getIntent();

		boolean forceOpen = (intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) > 0;

		System.out.println("ForceOpen? " + forceOpen);
		System.out.println("IntentHandler intent = " + intent);

		appPreferences = new AppPreferences(getApplicationContext());

		Uri data = intent.getData();
		if (data != null) {
			if (data.getScheme().equals("vuze") && data.getHost().equals("remote")
					&& data.getPath().length() > 1) {
				String ac = data.getPath().substring(1);
				intent.setData(null);
				if (ac.length() < 100) {
					new RemoteUtils(this).openRemote("vuze", ac, false, true);
					return;
				}
			}
		}

		RemotePreferences[] remotes = appPreferences.getRemotes();

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
					RemotePreferences remotePrefs = appPreferences.getLastUsedRemote();
					if (remotePrefs != null) {
						String user = remotePrefs.getUser();
						String ac = remotePrefs.getAC();

						if (savedInstanceState == null) {
							new RemoteUtils(this).openRemote(user, ac, false, true);
							finish();
							return;
						}
					} else {
						System.err.println("Has Remotes, but no last remote");
					}
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		}

		listview = (ListView) findViewById(R.id.lvRemotes);

		Arrays.sort(remotes, new Comparator<RemotePreferences>() {
			public int compare(RemotePreferences lhs, RemotePreferences rhs) {
				long diff = rhs.getLastUsedOn() - lhs.getLastUsedOn();
				return diff > 0 ? 1 : diff < 0 ? -1 : 0;
			}
		});

		// TODO: We could show last used date if we used a nice layout..
		list = new ArrayList(remotes.length);
		for (RemotePreferences remotePref : remotes) {
			list.add(new ListItem(remotePref));
		}
		list.add("<New Remote>");

		final ArrayAdapter adapter = new ArrayAdapter(this,
				android.R.layout.simple_list_item_1, list);
		listview.setAdapter(adapter);

		listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, final View view,
					int position, long id) {
				Object item = parent.getItemAtPosition(position);

				if (item instanceof String) {
					Intent myIntent = new Intent(intent);
					myIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
					myIntent.setClassName("com.vuze.android.remote",
							LoginActivity.class.getName());
					myIntent.putExtra("com.vuze.android.remote.login.ac", "");

					startActivity(myIntent);
				} else if (item instanceof ListItem) {
					RemotePreferences remote = ((ListItem) item).getRemotePreferences();
					if (remote != null) {
						new RemoteUtils(IntentHandler.this).openRemote(remote, false, false);
					}
				}
			}

		});

		registerForContextMenu(listview);
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
			String host = ((ListItem) item).getRemotePreferences().getHost();
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

		final RemotePreferences remotePreferences = ((ListItem) item).getRemotePreferences();

		switch (menuitem.getItemId()) {
			case R.id.action_edit_pref:
				DialogFragmentGenericRemotePreferences dlg = new DialogFragmentGenericRemotePreferences();
				Bundle args = new Bundle();
				Map prefsAsMap = remotePreferences.getAsMap();
				String prefsAsJSON = JSONUtils.encodeToJSON(prefsAsMap);
				args.putSerializable("remote.json", prefsAsJSON);
				dlg.setArguments(args);
				dlg.show(getSupportFragmentManager(), "GenericRemotePreferenced");

				return true;
			case R.id.action_delete_pref:
				new AlertDialog.Builder(this).setTitle("Remove Profile?").setMessage(
						"Configuration settings for profile '"
								+ remotePreferences.getNick() + "' will be deleted.").setPositiveButton(
						"Remove", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								appPreferences.removeRemotePref(remotePreferences.getNick());
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
	public void profileEditDone(RemotePreferences profile) {
		// TODO: update list
	}

}
