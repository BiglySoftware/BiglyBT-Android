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
 */

package com.vuze.android.remote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

public class CampaignTrackingReceiver
	extends BroadcastReceiver
{

	public CampaignTrackingReceiver() {
	}

	/* (non-Javadoc)
	 * @see android.content.BroadcastReceiver#onReceive(android.content.Context, android.content.Intent)
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		try {
  		int available = GooglePlayServicesUtil.isGooglePlayServicesAvailable(VuzeRemoteApp.getContext());
  		Log.d("VET", "PlayAvail? " + available + "/" + VuzeRemoteApp.getContext());
  		BroadcastReceiver receiver;
  		if (available == ConnectionResult.SUCCESS) {
  			receiver = new com.google.android.gms.analytics.CampaignTrackingReceiver();
  		} else {
  			receiver = new com.google.analytics.tracking.android.CampaignTrackingReceiver();
  		}
  
  		Log.d("VET", "BroadcastReceiver#onReceive " + intent);
  		if (intent != null) {
  			Bundle extras = intent.getExtras();
  			if (extras != null) {
  				StringBuffer sb = new StringBuffer();
  				for (String key : extras.keySet()) {
  					sb.append(key);
  					sb.append('=');
  					sb.append(extras.get(key));
  					sb.append(", ");
  				}
  				Log.d("VET", "BroadcastReceiver#onReceive. Extras=" + sb.toString());
  			}
  			intent.getPackage();
  		}
  		receiver.onReceive(context, intent);
		} catch (Throwable t) {
			Log.e("VET", "INSTALL_REFERRER", t);
		}
	}
}
