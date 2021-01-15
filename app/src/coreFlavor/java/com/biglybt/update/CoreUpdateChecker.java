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

package com.biglybt.update;

import androidx.annotation.Keep;

import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.update.UpdatableComponent;
import com.biglybt.pif.update.UpdateChecker;

@Keep
public class CoreUpdateChecker
	implements Plugin, UpdatableComponent
{

	@Override
	public void initialize(PluginInterface _plugin_interface) {
	}

	@Override
	public String getName() {
		return ("BiglyBT Core");
	}

	@Override
	public int getMaximumCheckTime() {
		return 1;
	}

	@Override
	public void checkForUpdate(final UpdateChecker checker) {
	}

}
