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
 * 
 */

package com.vuze.android.remote.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import com.vuze.android.remote.*;

public class DialogFragmentSortBy
	extends DialogFragment
{
	public interface SortByDialogListener
	{
		void flipSortOrder();

		void sortBy(String[] sortFieldIDs, Boolean[] sortOrderAsc, boolean save);
	}

	public static void open(FragmentManager fm, Fragment fragment) {
		DialogFragmentSortBy dlg = new DialogFragmentSortBy();
		dlg.setTargetFragment(fragment, -1);
		AndroidUtils.showDialog(dlg, fm, "OpenSortDialog");
	}

	private SortByDialogListener mListener;

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.sortby_title);
		builder.setItems(R.array.sortby_list,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						if (mListener == null) {
							return;
						}
						String[] sortFieldIDs = null;
						Boolean[] sortOrderAsc = null;
						switch (which) {
							case 0: // <item>Queue Order</item>
								sortFieldIDs = new String[] {
									TransmissionVars.FIELD_TORRENT_POSITION
								};
								sortOrderAsc = new Boolean[] {
									true
								};
								break;
							case 1: // <item>Activity</item>
								sortFieldIDs = new String[] {
									TransmissionVars.FIELD_TORRENT_RATE_DOWNLOAD,
									TransmissionVars.FIELD_TORRENT_RATE_UPLOAD
								};
								sortOrderAsc = new Boolean[] {
									false
								};
								break;
							case 2: // <item>Age</item>
								sortFieldIDs = new String[] {
									TransmissionVars.FIELD_TORRENT_DATE_ADDED
								};
								sortOrderAsc = new Boolean[] {
									false
								};
								break;
							case 3: // <item>Progress</item>
								sortFieldIDs = new String[] {
									TransmissionVars.FIELD_TORRENT_PERCENT_DONE
								};
								sortOrderAsc = new Boolean[] {
									false
								};
								break;
							case 4: // <item>Ratio</item>
								sortFieldIDs = new String[] {
									TransmissionVars.FIELD_TORRENT_UPLOAD_RATIO
								};
								sortOrderAsc = new Boolean[] {
									false
								};
								break;
							case 5: // <item>Size</item>
								sortFieldIDs = new String[] {
									TransmissionVars.FIELD_TORRENT_SIZE_WHEN_DONE
								};
								sortOrderAsc = new Boolean[] {
									false
								};
								break;
							case 6: // <item>State</item>
								sortFieldIDs = new String[] {
									TransmissionVars.FIELD_TORRENT_STATUS
								};
								sortOrderAsc = new Boolean[] {
									false
								};
								break;
							case 7: // <item>Reverse Sort Order</item>
								mListener.flipSortOrder();
								break;
							default:
								break;
						}
						if (sortFieldIDs != null) {
							mListener.sortBy(sortFieldIDs, sortOrderAsc, true);
						}
					}
				});
		return builder.create();
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		Fragment targetFragment = getTargetFragment();

		if (targetFragment instanceof SortByDialogListener) {
			mListener = (SortByDialogListener) targetFragment;
		} else if (activity instanceof SortByDialogListener) {
			mListener = (SortByDialogListener) activity;
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).fragmentStart(this, "SortBy");
	}

	@Override
	public void onStop() {
		super.onStop();
		VuzeEasyTracker.getInstance(this).fragmentStop(this);
	}
}
