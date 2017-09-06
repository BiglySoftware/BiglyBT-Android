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

import com.biglybt.android.client.dialog.DialogFragmentGiveback;

import android.content.Intent;

/**
 * Created by TuxPaper on 8/15/17.
 */

public class ActivityResultHandler
{
	public final static int FILECHOOSER_RESULTCODE = 1;

	public final static int PURCHASE_RESULTCODE = 2;

	public final static int PATHCHOOSER_RESULTCODE = 3;

	public ActivityResultHandler() {

	}

	public boolean onActivityResult(int requestCode, int resultCode,
			Intent intent) {
		if (DialogFragmentGiveback.iabHelper != null) {
			if (DialogFragmentGiveback.iabHelper.handleActivityResult(requestCode,
					resultCode, intent)) {
				return true;
			}
		}
		return false;
	}
}
