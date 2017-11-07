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

package com.biglybt.android.client.activity;

import android.app.Activity;
import android.content.Intent;
import android.support.v4.app.FragmentActivity;

/**
 * May one day contain generic handlers for intent results
 * 
 * Created by TuxPaper on 8/15/17.
 */

public class ActivityResultHandler
{
	public final static int REQUEST_FILECHOOSER = 1;

	public final static int REQUEST_PATHCHOOSER = 3;

	public static final int REQUEST_SETTINGS = 2;

	public static final int REQUEST_VOICE = 4;

	private final FragmentActivity activity;

	// I bet something like Otto would be better
	public static onActivityResultCapture capture;

	public interface onActivityResultCapture
	{
		boolean onActivityResult(int requestCode, int resultCode, Intent intent);
	}

	public ActivityResultHandler(FragmentActivity activity) {
		this.activity = activity;
	}

	public boolean onActivityResult(int requestCode, int resultCode,
			Intent intent) {
		if (capture != null) {
			if (capture.onActivityResult(requestCode, resultCode, intent)) {
				return false;
			}
		}
		if (resultCode == Activity.RESULT_CANCELED) {
			return false;
		}
		if (requestCode == REQUEST_SETTINGS) {
		}
		return false;
	}
}
