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

import android.os.IBinder;
import android.util.Log;

import com.biglybt.android.client.service.BiglyBTServiceCore;
import com.biglybt.android.client.service.BiglyBTServiceInit;
import com.biglybt.android.util.BiglyCoreUtils;

/**
 * Created by TuxPaper on 2/12/19.
 */
public class BiglyCoreFlavorUtils
{
	public static IBiglyCoreInterface getCoreInterface() {
		BiglyBTServiceInit service = BiglyCoreUtils.getBiglyBTService();
		if (!(service instanceof BiglyBTServiceCore)) {
			if (AndroidUtils.DEBUG) {
				Log.w("CoreFUtils",
						"getCoreInterface: service not BiglyBTServiceCore; " + service);
			}
			return null;
		}
		IBiglyCoreInterface coreInterface = ((BiglyBTServiceCore) service).getCoreInterface();
		if (coreInterface == null) {
			if (AndroidUtils.DEBUG) {
				Log.w("CoreFUtils", "getCoreInterface: coreInterface null");
			}
			return null;
		}
		IBinder iBinder = coreInterface.asBinder();
		// Hopefully prevent DeadObjectException (although binder could die after we do this test, but before usage)
		if (iBinder != null && !iBinder.pingBinder()) {
			if (AndroidUtilsUI.isUIThread()) {
				if (AndroidUtils.DEBUG) {
					AnalyticsTracker.getInstance().logError(
							"getCoreInterface: pingBinder failed and on UI Thread",
							"pingBinderFailed");
				}
			} else {
				if (AndroidUtils.DEBUG) {
					Log.w("CoreFUtils", "getCoreInterface: pingBinder failed");
				}
				BiglyCoreUtils.waitForCore(null);
			}
		}
		return coreInterface;
	}
}
