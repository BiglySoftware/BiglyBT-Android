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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public interface IAnalyticsTracker
{

	void setLastViewName(String lastViewName);

	void activityResume(Activity activity);

	void fragmentResume(@NonNull Fragment fragment);

	void activityPause(Activity activity);

	void fragmentPause(Fragment fragment);

	void logEvent(String event);

	void logError(String s, @Nullable String page);

	void logError(Throwable e);

	void logError(Throwable e, String extra);

	void logCrash(Throwable e, Thread thread);

	void logError(@NonNull Throwable t, @NonNull StackTraceElement[] stackTrace);

	void logErrorNoLines(Throwable e);

	void sendEvent(String category, String action, @Nullable String label,
			@Nullable Long value);

	void registerExceptionReporter(Context applicationContext);

	void setClientVersion(String clientVersion);

	void setDensity(int densityDpi);

	void setDeviceName(String deviceName);

	void setDeviceType(String s);

	void setRPCVersion(String rpcVersion);

	void setRemoteTypeName(String remoteTypeName);

	void setScreenInches(double screenInches);

	void stop();
}