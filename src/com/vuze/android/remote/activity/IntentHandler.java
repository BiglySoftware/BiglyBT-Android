package com.vuze.android.remote.activity;

import java.util.*;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.vuze.android.remote.*;

public class IntentHandler
	extends Activity
{

	@SuppressWarnings("rawtypes")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_intent_handler);

		final Intent intent = getIntent();
		
		boolean forceOpen = (intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) > 0;
		
		System.out.println("ForceOpen? " + forceOpen);
		System.out.println("IntentHandler intent = " + intent);

		final AppPreferences appPreferences = new AppPreferences(getApplicationContext());

		RemotePreferences[] remotes = appPreferences.getRemotes();

		if (!forceOpen) {
  		if (remotes.length == 0) {
  			System.out.println("A");
  			// New User: Send them to Login (Account Creation)
  			Intent myIntent = new Intent(intent);
  			myIntent.setClassName("com.vuze.android.remote",
  					LoginActivity.class.getName());
  			myIntent.setAction(Intent.ACTION_VIEW);
  			myIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
  			myIntent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
  
  			startActivity(myIntent);
  			finish();
  			return;
  		} else if (remotes.length == 1 || intent.getData() == null) {
  			System.out.println("B");
  
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
		}

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

				if (position == list.size() - 1) {
					Intent myIntent = new Intent(intent);
					myIntent.setClassName("com.vuze.android.remote",
							LoginActivity.class.getName());
					myIntent.putExtra("com.vuze.android.remote.login.ac", "");

					startActivity(myIntent);
				} else {
					RemotePreferences remote = appPreferences.getRemote(item);
					if (remote != null) {
						// TODO: should pass remote!
						new RemoteUtils(IntentHandler.this).openRemote(remote.getUser(), remote.getAC(), false);
					}
				}
			}

		});

	}
}
