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

import java.util.HashMap;
import java.util.Map;

import com.biglybt.android.client.TransmissionVars;
import com.biglybt.android.util.MapUtils;

/**
 * Session settings are settings retrieved from the full client and can be
 * changed by the full client without our knowledge.  This is in contrast to
 * {@link RemoteProfile} settings, which are local to this remote client.
 * <p>
 * Typically, SessionSettings are retreived from {@link Session#getSessionSettingsClone()}
 */
public class SessionSettings
{
	private boolean dlIsManual;

	private boolean ulIsManual;

	private long dlManualSpeed;

	private long ulManualSpeed;

	private String downloadDir;

	private int peerPort;

	private boolean isRandomPeerPort;

	public static SessionSettings createFromRPC(Map<?, ?> map) {
		SessionSettings settings = new SessionSettings();
		settings.dlIsManual = MapUtils.getMapBoolean(map,
				TransmissionVars.TR_PREFS_KEY_DSPEED_ENABLED, true);
		settings.ulIsManual = MapUtils.getMapBoolean(map,
				TransmissionVars.TR_PREFS_KEY_USPEED_ENABLED, true);
		settings.downloadDir = MapUtils.getMapString(map,
				TransmissionVars.TR_PREFS_KEY_DOWNLOAD_DIR, null);

		settings.dlManualSpeed = MapUtils.getMapLong(map,
				TransmissionVars.TR_PREFS_KEY_DSPEED_KBps, 0);
		settings.ulManualSpeed = MapUtils.getMapLong(map,
				TransmissionVars.TR_PREFS_KEY_USPEED_KBps, 0);

		settings.peerPort = MapUtils.getMapInt(map,
				TransmissionVars.TR_PREFS_KEY_PEER_PORT, -1);
		settings.isRandomPeerPort = MapUtils.getMapBoolean(map,
				TransmissionVars.TR_PREFS_KEY_PEER_PORT_RANDOM_ON_START, false);

		return settings;
	}

	public int getPeerPort() {
		return peerPort;
	}

	public void setPeerPort(int peerPort) {
		this.peerPort = peerPort;
	}

	public boolean isRandomPeerPort() {
		return isRandomPeerPort;
	}

	public void setRandomPeerPort(boolean randomPeerPort) {
		isRandomPeerPort = randomPeerPort;
	}

	public boolean isDlManual() {
		return dlIsManual;
	}

	public void setDLIsManual(boolean dlIsAuto) {
		this.dlIsManual = dlIsAuto;
	}

	public boolean isUlManual() {
		return ulIsManual;
	}

	public void setULIsManual(boolean ulIsAuto) {
		this.ulIsManual = ulIsAuto;
	}

	public long getManualDlSpeed() {
		return dlManualSpeed;
	}

	public void setManualDlSpeed(long dlSpeed) {
		this.dlManualSpeed = dlSpeed;
	}

	public long getManualUlSpeed() {
		return ulManualSpeed;
	}

	public void setManualUlSpeed(long ulSpeed) {
		this.ulManualSpeed = ulSpeed;
	}

	public void setDownloadDir(String dir) {
		this.downloadDir = dir;
	}

	public String getDownloadDir() {
		return this.downloadDir;
	}

	public Map toRPC(SessionSettings diffSettings) {
		Map<String, Object> changes = new HashMap<>();
		if (diffSettings == null || dlIsManual != diffSettings.dlIsManual) {
			changes.put(TransmissionVars.TR_PREFS_KEY_DSPEED_ENABLED, dlIsManual);
		}
		if (diffSettings == null || ulIsManual != diffSettings.ulIsManual) {
			changes.put(TransmissionVars.TR_PREFS_KEY_USPEED_ENABLED, ulIsManual);
		}
		if (diffSettings == null || ulManualSpeed != diffSettings.ulManualSpeed) {
			changes.put(TransmissionVars.TR_PREFS_KEY_USPEED_KBps, ulManualSpeed);
		}
		if (diffSettings == null || dlManualSpeed != diffSettings.dlManualSpeed) {
			changes.put(TransmissionVars.TR_PREFS_KEY_DSPEED_KBps, dlManualSpeed);
		}
		final String downloadDir = this.downloadDir;
		if (downloadDir != null) {
			if (diffSettings == null
					|| !downloadDir.equals(diffSettings.downloadDir)) {
				changes.put(TransmissionVars.TR_PREFS_KEY_DOWNLOAD_DIR, downloadDir);
			}
		}
		if (diffSettings == null
				|| isRandomPeerPort != diffSettings.isRandomPeerPort) {
			changes.put(TransmissionVars.TR_PREFS_KEY_PEER_PORT_RANDOM_ON_START,
					isRandomPeerPort);
		}
		if (diffSettings == null
				|| (!isRandomPeerPort && peerPort != diffSettings.peerPort)) {
			changes.put(TransmissionVars.TR_PREFS_KEY_PEER_PORT, peerPort);
		}
		return changes;
	}
}
