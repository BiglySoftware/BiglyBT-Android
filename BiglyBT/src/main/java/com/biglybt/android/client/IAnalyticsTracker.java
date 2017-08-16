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
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;

public interface IAnalyticsTracker
{

	void activityResume(Activity activity);

	void activityResume(Activity rcmActivity, String name);

	void fragmentResume(Fragment fragment, String name);

	void activityPause(Activity activity);

	void fragmentPause(Fragment fragment);

	void set(String key, String value);

	void logError(String s, @Nullable String page);

	void logError(Throwable e);

	void logError(Throwable e, String extra);

	void logErrorNoLines(Throwable e);

	void sendEvent(String category, String action, @Nullable String label,
			@Nullable Long value);

	void registerExceptionReporter(Context applicationContext);

	void stop();
}