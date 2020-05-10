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

import com.biglybt.core.pairing.impl.PairingManagerImpl.UIAdapter;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.config.BooleanParameter;

public final class PairingUIAdapter
	implements UIAdapter
{
	@Override
	public void initialise(PluginInterface pi, BooleanParameter icon_enable) {
	}

	@Override
	public void recordRequest(String name, String ip, boolean good) {
	}

	@Override
	public char[] getSRPPassword() {
		return null;
	}
}
