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
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.view.ActionMode;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

public class DialogFragmentGiveback
{
	// TODO: No Google Stuff, so we can assume it's not on Google Play Store
	// and perhaps show donation/bitcoin links
	public static void openDialog(final FragmentActivity activity,
			final FragmentManager fm, final boolean userInvoked,
			final String source) {

		AndroidUtilsUI.popupContextMenu(activity, new ActionMode.Callback() {
			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu) {
				activity.getMenuInflater().inflate(R.menu.menu_giveback, menu);
				return true;
			}

			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				return true;
			}

			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
				int itemId = item.getItemId();
				switch (itemId) {
					case R.id.action_giveback_contribute:
						AndroidUtilsUI.openURL(activity,
								"https://android.biglybt.com/contribute",
								activity.getString(R.string.menu_contribute));
						break;
					case R.id.action_giveback_vote:
						AndroidUtilsUI.openURL(activity, "https://vote.biglybt.com/android",
								activity.getString(R.string.menu_contribute));
						break;
					case R.id.action_giveback_donate:
						AndroidUtilsUI.openURL(activity,
								"https://android.biglybt.com/donate",
								activity.getString(R.string.menu_contribute));
						break;
				}
				return true;
			}

			@Override
			public void onDestroyActionMode(ActionMode mode) {

			}
		}, activity.getString(R.string.giveback_title));
	}

	public static boolean handleActivityResult(int requestCode, int resultCode,
			Intent intent) {
		return false;
	}
}
