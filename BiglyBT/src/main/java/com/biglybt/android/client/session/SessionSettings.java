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

	public static SessionSettings createFromRPC(Map<?, ?> map) {
		SessionSettings settings = new SessionSettings();
		settings.setDLIsManual(MapUtils.getMapBoolean(map,
				TransmissionVars.TR_PREFS_KEY_DSPEED_ENABLED, true));
		settings.setULIsManual(MapUtils.getMapBoolean(map,
				TransmissionVars.TR_PREFS_KEY_USPEED_ENABLED, true));
		settings.setDownloadDir(MapUtils.getMapString(map,
				TransmissionVars.TR_PREFS_KEY_DOWNLOAD_DIR, null));

		settings.setManualDlSpeed(
				MapUtils.getMapLong(map, TransmissionVars.TR_PREFS_KEY_DSPEED_KBps, 0));
		settings.setManualUlSpeed(
				MapUtils.getMapLong(map, TransmissionVars.TR_PREFS_KEY_USPEED_KBps, 0));
		return settings;
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
		if (diffSettings == null || isDlManual() != diffSettings.isDlManual()) {
			changes.put(TransmissionVars.TR_PREFS_KEY_DSPEED_ENABLED, isDlManual());
		}
		if (diffSettings == null || isUlManual() != diffSettings.isUlManual()) {
			changes.put(TransmissionVars.TR_PREFS_KEY_USPEED_ENABLED, isUlManual());
		}
		if (diffSettings == null || getManualUlSpeed() != diffSettings.getManualUlSpeed()) {
			changes.put(TransmissionVars.TR_PREFS_KEY_USPEED_KBps, getManualUlSpeed());
		}
		if (diffSettings == null || getManualDlSpeed() != diffSettings.getManualDlSpeed()) {
			changes.put(TransmissionVars.TR_PREFS_KEY_DSPEED_KBps, getManualDlSpeed());
		}
		if (diffSettings == null
				|| getDownloadDir() != diffSettings.getDownloadDir()) {
			changes.put(TransmissionVars.TR_PREFS_KEY_DOWNLOAD_DIR, getDownloadDir());
		}
		return changes;
	}
}
