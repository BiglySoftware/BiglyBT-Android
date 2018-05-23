/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.android.client;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/**
 * Created by TuxPaper on 5/10/18.
 */
public class AppLifecycleCallbacks
		implements
		Application.ActivityLifecycleCallbacks
{
	private int resumed;
	private int paused;
	private int started;
	private int stopped;

	@Override
	public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
	}

	@Override
	public void onActivityDestroyed(Activity activity) {
	}

	@Override
	public void onActivityResumed(Activity activity) {
		resumed++;
	}

	@Override
	public void onActivityPaused(Activity activity) {
		paused++;
	}

	@Override
	public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
	}

	@Override
	public void onActivityStarted(Activity activity) {
		started++;
	}

	@Override
	public void onActivityStopped(Activity activity) {
		stopped++;
	}

  public boolean isApplicationVisible() {
      return started > stopped;
  }

  public boolean isApplicationInForeground() {
      return resumed > paused;
  }
}