package com.vuze.android.remote;

import java.util.*;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class IntentHandler
	extends Activity
{

	@SuppressWarnings("rawtypes")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		System.out.println("IntentHandler intent = " + getIntent());

		AppPreferences appPreferences = new AppPreferences(getApplicationContext());

		RemotePreferences[] remotes = appPreferences.getRemotes();

		if (remotes.length == 0) {
			// New User: Send them to Login (Account Creation)
			Intent myIntent = new Intent(getIntent());
			myIntent.setClassName("com.vuze.android.remote",
					"com.vuze.android.remote.LoginActivity");
			myIntent.setAction(Intent.ACTION_VIEW);
			myIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			myIntent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);

			startActivity(myIntent);
			finish();
			return;
		} else if (remotes.length == 1) {

			try {
				RemotePreferences remotePrefs = appPreferences.getLastUsedRemote();
				if (remotePrefs != null) {
					String user = remotePrefs.getUser();
					String ac = remotePrefs.getAC();

					if (savedInstanceState == null) {
						new RemoteUtils(this, appPreferences).openRemote(user, ac, false);
						finish();
						return;
					}
				}
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}

		setContentView(R.layout.activity_intent_handler);
		// Show the Up button in the action bar.
		setupActionBar();

		final ListView listview = (ListView) findViewById(R.id.lvRemotes);

		Arrays.sort(remotes, new Comparator<RemotePreferences>() {
			public int compare(RemotePreferences lhs, RemotePreferences rhs) {
				long diff = lhs.getLastUsedOn() - rhs.getLastUsedOn();
				return diff > 0 ? 1 : diff < 0 ? -1 : 0;
			}
		});

		// TODO: We could show last used date if we used a nice layout..
		final List<String> list = new ArrayList<String>(remotes.length);
		for (RemotePreferences remotePref : remotes) {
			list.add(remotePref.getName());
		}
		list.add("<New Remote>");

		final ArrayAdapter adapter = new ArrayAdapter(this,
				android.R.layout.simple_list_item_1, list);
		listview.setAdapter(adapter);

		listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, final View view,
					int position, long id) {
				final String item = (String) parent.getItemAtPosition(position);
				System.out.println(item);

				if (position == list.size() - 1) {
					Intent myIntent = new Intent(getIntent());
					myIntent.setClassName("com.vuze.android.remote",
							"com.vuze.android.remote.LoginActivity");

					startActivity(myIntent);
					finish();
				}
			}

		});

	}

	/**
	 * Set up the {@link android.app.ActionBar}, if the API is available.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupActionBar() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			getActionBar().setDisplayHomeAsUpEnabled(true);
		}
	}

}
