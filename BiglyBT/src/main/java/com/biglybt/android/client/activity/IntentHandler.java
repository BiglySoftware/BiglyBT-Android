/*
 * Copyright (c) Azureus Software, Inc, All Rights Reserved.
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
 */

package com.biglybt.android.client.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.fragment.app.FragmentManager;

import com.biglybt.android.client.*;
import com.biglybt.android.client.dialog.DialogFragmentGenericRemoteProfile;
import com.biglybt.android.client.dialog.DialogFragmentGenericRemoteProfile.GenericRemoteProfileListener;
import com.biglybt.android.client.fragment.ProfileSelectorFragment;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.client.session.RemoteProfileFactory;

/**
 * Profile Selector screen and Main Intent
 */
public class IntentHandler
	extends ThemedActivity
	implements GenericRemoteProfileListener
{

	private static final String TAG = "IntentHandler";

	private boolean openAfterEdit;

	private boolean noSavedInstanceState;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		noSavedInstanceState = savedInstanceState == null;
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_splash);
		handleIntent(getIntent());
	}

	private void handleIntent(Intent intent) {
		AndroidUtilsUI.runOffUIThread(() -> {
			boolean handled = handleIntent2(intent);
			if (!handled) {
				// .commit will send it over to UI thread for us
				FragmentManager fm = getSupportFragmentManager();
				if (fm.isDestroyed() || fm.findFragmentByTag("PSF") != null) {
					return;
				}
				fm.beginTransaction().add(R.id.fragment_container,
						new ProfileSelectorFragment(), "PSF").commit();
			}
		});
	}

	private boolean handleIntent2(Intent intent) {
		boolean forceProfileListOpen = false;

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "IntentHandler intent.data = " + intent.getData());
		}

		AppPreferences appPreferences = BiglyBTApp.getAppPreferences();

		Uri data = intent.getData();
		if (data != null) {
			try {
				// check for vuze://remote//*
				String scheme = data.getScheme();
				String host = data.getHost();
				String path = data.getPath();
				if (("vuze".equals(scheme) || "biglybt".equals(scheme))
						&& "remote".equals(host) && path != null && path.length() > 1) {
					String ac = path.substring(1);
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "got ac '" + ac + "' from " + data);
					}

					intent.setData(null);
					if ("cmd=advlogin".equals(ac)) {
						// postDelayed fixes timing bug where focused textbox doesn't
						// react to delete/left/right buttons on soft keyboard.
						// probably due to inflating fragment_container at the same time
						AndroidUtilsUI.postDelayed(() -> {
							DialogFragmentGenericRemoteProfile dlg = new DialogFragmentGenericRemoteProfile();
							AndroidUtilsUI.showDialog(dlg, getSupportFragmentManager(),
									DialogFragmentGenericRemoteProfile.TAG);
						});
						forceProfileListOpen = true;
					} else if (data.getQueryParameter("h") != null) {
						String remoteHost = data.getQueryParameter("h");
						String portString = data.getQueryParameter("p");
						String user = data.getQueryParameter("u");
						String pw = data.getQueryParameter("ac");
						String reqPW = data.getQueryParameter("reqPW");

						RemoteProfile remoteProfile = RemoteProfileFactory.create(
								RemoteProfile.TYPE_NORMAL);
						remoteProfile.setUser(user);
						remoteProfile.setAC(pw);
						remoteProfile.setHost(remoteHost);
						try {
							remoteProfile.setPort(Integer.parseInt(portString));
						} catch (Throwable ignored) {
						}
						openAfterEdit = true;
						RemoteUtils.editProfile(remoteProfile, getSupportFragmentManager(),
								"1".equals(reqPW));
						return false;
					} else if (ac.length() < 100) {
						RemoteProfile remoteProfile = RemoteProfileFactory.create("vuze",
								ac);
						return RemoteUtils.openRemote(this, remoteProfile, true, true);
					}
				}

				// check for http[s]://remote.vuze.com/ac=*
				if ("remote.vuze.com".equals(host)
						&& data.getQueryParameter("ac") != null) {
					String ac = data.getQueryParameter("ac");
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "got ac '" + ac + "' from " + data);
					}
					intent.setData(null);
					if (ac.length() < 100) {
						RemoteProfile remoteProfile = RemoteProfileFactory.create("vuze",
								ac);
						return RemoteUtils.openRemote(this, remoteProfile, true, true);
					}
				}
			} catch (Exception e) {
				if (AndroidUtils.DEBUG) {
					e.printStackTrace();
				}
			}
		}

		if (!forceProfileListOpen) {
			boolean clearTop = (intent.getFlags()
					& Intent.FLAG_ACTIVITY_CLEAR_TOP) > 0;

			if (AndroidUtils.DEBUG) {
				Log.d(TAG,
						"handleIntent: forceProfileListOpen = false; clearTop=" + clearTop);
			}

			if (!appPreferences.hasRemotes()) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "handleIntent: noRemotes, go to LoginScreen");
				}
				// New User: Send them to Login (Account Creation)
				LoginActivity.launch(this);
				return true;
			}

			if (!clearTop && noSavedInstanceState) {
				RemoteProfile remoteProfile = appPreferences.getLastUsedRemote();
				if (remoteProfile == null) {
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "No last remote");
					}
					return false;
				}

				if (intent.getData() == null
						|| appPreferences.getRemotes().length == 1) {
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "handleIntent: getRemotes.length="
								+ appPreferences.getRemotes().length);
					}
					try {
						return RemoteUtils.openRemote(this, remoteProfile, true, true);
					} catch (Throwable t) {
						AnalyticsTracker.getInstance(this).logError(t);
					}
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
		handleIntent(intent);
	}

	@Override
	public void profileEditDone(RemoteProfile oldProfile,
			RemoteProfile newProfile) {
		if (openAfterEdit) {
			RemoteUtils.openRemote(this, newProfile, true, true);
		}
	}
}
