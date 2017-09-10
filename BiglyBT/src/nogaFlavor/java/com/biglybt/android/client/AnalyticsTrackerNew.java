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

package com.biglybt.android.client;

import android.app.Activity;
import android.content.Context;
import android.support.v4.app.Fragment;

public class AnalyticsTrackerNew
	implements IAnalyticsTracker
{
	public AnalyticsTrackerNew(Context ctx) {
	}

	@Override
	public void activityResume(Activity activity) {

	}

	@Override
	public void activityResume(Activity rcmActivity, String name) {

	}

	@Override
	public void fragmentResume(Fragment fragment, String name) {

	}

	@Override
	public void activityPause(Activity activity) {

	}

	@Override
	public void fragmentPause(Fragment fragment) {

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
	public void stop() {

	}
}
