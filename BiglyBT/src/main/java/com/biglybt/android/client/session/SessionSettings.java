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

package com.biglybt.android.client.session;

import java.io.Serializable;

/**
 * Session settings are settings retrieved from the full client and can be
 * changed by the full client without our knowledge.  This is in contrast to
 * {@link RemoteProfile} settings, which are local to this remote client.
 * <p>
 * Typically, SessionSettings are retreived from {@link Session#getSessionSettings()}
 */
public class SessionSettings
	implements Serializable
{

	private static final long serialVersionUID = -9104780845902843703L;

	private boolean dlIsAuto;

	private boolean ulIsAuto;

	private long dlSpeed;

	private long ulSpeed;

	private String downloadDir;

	public boolean isDLAuto() {
		return dlIsAuto;
	}

	public void setDLIsAuto(boolean dlIsAuto) {
		this.dlIsAuto = dlIsAuto;
	}

	public boolean isULAuto() {
		return ulIsAuto;
	}

	public void setULIsAuto(boolean ulIsAuto) {
		this.ulIsAuto = ulIsAuto;
	}

	public long getDlSpeed() {
		return dlSpeed;
	}

	public void setDlSpeed(long dlSpeed) {
		this.dlSpeed = dlSpeed;
	}

	public long getUlSpeed() {
		return ulSpeed;
	}

	public void setUlSpeed(long ulSpeed) {
		this.ulSpeed = ulSpeed;
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}

	public void setDownloadDir(String dir) {
		this.downloadDir = dir;
	}

	public String getDownloadDir() {
		return this.downloadDir;
	}

}
