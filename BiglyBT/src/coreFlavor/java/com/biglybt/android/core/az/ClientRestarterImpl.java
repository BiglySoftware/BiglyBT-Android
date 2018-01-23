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
package com.biglybt.android.core.az;

import com.biglybt.core.Core;
import com.biglybt.core.CoreException;
import com.biglybt.core.update.ClientRestarter;

import android.util.Log;

public class ClientRestarterImpl
	implements ClientRestarter
{
	private static final String TAG = "ClientRestarterImpl";

	protected final Core core;

	public ClientRestarterImpl(Core _core) {
		Log.d(TAG, "ClientRestarterImpl: init");
		core = _core;
	}

	@Override
	public void restart(boolean update_only) {
		Log.e(TAG, "restart triggered and ignored. update_only= " + update_only);
	}

	@Override
	public void updateNow()
			throws CoreException {
		Log.e(TAG, "updateNow triggered and ignored");
	}

}
