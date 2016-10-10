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

package com.vuze.android.remote;

import java.util.Map;

import android.app.Activity;
import android.content.Context;
import android.support.v4.app.Fragment;

public class VuzeEasyTrackerNew
	implements IVuzeEasyTracker
{
	public VuzeEasyTrackerNew(Context ctx) {
	}

	@Override
	public void activityStart(Activity activity) {

	}

	@Override
	public void screenStart(String name) {

	}

	@Override
	public void fragmentStart(Fragment fragment, String name) {

	}

	@Override
	public void activityStop(Activity activity) {

	}

	@Override
	public void fragmentStop(Fragment fragment) {

	}

	@Override
	public String get(String key) {
		return null;
	}

	@Override
	public void send(Map<String, String> params) {

	}

	@Override
	public void set(String key, String value) {

	}

	@Override
	public void logError(String s, String page) {

	}

	@Override
	public void logError(Throwable e) {

	}

	@Override
	public void logError(Throwable e, String extra) {

	}

	@Override
	public void logErrorNoLines(Throwable e) {

	}

	@Override
	public void sendEvent(String category, String action, String label,
			Long value) {

	}

	@Override
	public void registerExceptionReporter(Context applicationContext) {

	}

	@Override
	public void setClientID(String rt) {

	}

	@Override
	public void setPage(String rt) {

	}

	@Override
	public void stop() {

	}
}
