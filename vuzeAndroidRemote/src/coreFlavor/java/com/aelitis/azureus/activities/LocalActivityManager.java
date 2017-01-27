/*
 * Copyright (c) Azureus Software, Inc, All Rights Reserved.
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
 */


package com.aelitis.azureus.activities;

import java.util.Map;

import com.aelitis.azureus.core.util.AZ3Functions;

public class
LocalActivityManager 
{
	public static void
	addLocalActivity(
		String															uid,
		String															icon_id,
		String															name,
		String[]														actions,
		Class<? extends AZ3Functions.provider.LocalActivityCallback>	callback,
		Map<String,String>												callback_data )
	{
	}
	
	public interface
	LocalActivityCallback
		extends AZ3Functions.provider.LocalActivityCallback
	{
	}
}
