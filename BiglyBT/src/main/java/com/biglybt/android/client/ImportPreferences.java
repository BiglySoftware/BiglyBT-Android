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

import java.util.Map;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;

import net.grandcentrix.tray.TrayPreferences;
import net.grandcentrix.tray.core.SharedPreferencesImport;

public class ImportPreferences
	extends TrayPreferences
{
	private static final String SHARED_PREFERENCES_FILE_NAME = "AndroidRemote";
	private static final String MODULE = "BiglyBT";

	public ImportPreferences(@NonNull Context context) {
		super(context, MODULE, 1);
	}

	// Called only once when the module was created
	@Override
	protected void onCreate(int initialVersion) {
		super.onCreate(initialVersion);

		SharedPreferences sharedPreferences = BiglyBTApp.applicationContext.getSharedPreferences(
				SHARED_PREFERENCES_FILE_NAME, Activity.MODE_PRIVATE);
		final Map<String, ?> all = sharedPreferences.getAll();

		SharedPreferencesImport[] imports = new SharedPreferencesImport[all.size()];
		int i = 0;
		for (String key : all.keySet()) {
			imports[i++] = new SharedPreferencesImport(getContext(),
					SHARED_PREFERENCES_FILE_NAME, key, key);
		}

		// Finally migrate it
		migrate(imports);
	}
}