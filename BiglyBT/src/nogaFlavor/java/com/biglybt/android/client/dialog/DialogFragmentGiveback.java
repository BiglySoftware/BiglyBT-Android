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

package com.biglybt.android.client.dialog;

import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.R;

import android.content.Intent;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;

public class DialogFragmentGiveback
{
	// TODO: No Google Stuff, so we can assume it's not on Google Play Store
	// and perhaps show donation/bitcoin links
	public static void openDialog(final FragmentActivity activity,
			final FragmentManager fm, final boolean userInvoked,
			final String source) {

		AndroidUtilsUI.showDialog(activity, R.string.giveback_title,
				R.string.giveback_no_google);
	}

	public static boolean handleActivityResult(int requestCode, int resultCode,
			Intent intent) {
		return false;
	}
}
