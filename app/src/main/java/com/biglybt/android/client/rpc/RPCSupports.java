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

package com.biglybt.android.client.rpc;

/**
 * Created by TuxPaper on 11/18/16.
 */

public interface RPCSupports
{
	int SUPPORTS_RCM = 0;

	int SUPPORTS_SUBSCRIPTIONS = 1;

	int SUPPORTS_TAGS = 2;

	int SUPPORTS_GZIP = 3;

	int SUPPORTS_SEARCH = 4;

	int SUPPORTS_TORRENT_RENAAME = 5;

	int SUPPORTS_FIELD_ISFORCED = 6;

	int SUPPORTS_CONFIG = 7;

	int SUPPORTS_FIELD_SEQUENTIAL = 8;
	
	int SUPPORTS_FILES_DELETE = 9;
}
