package com.vuze.android.remote;

import java.io.Serializable;

public class SessionSettings
	implements Serializable
{

	private static final long serialVersionUID = -9104780845902843703L;

	private boolean dlIsAuto;

	private boolean ulIsAuto;

	private boolean refreshIntervalIsEnabled;

	private long dlSpeed;

	private long ulSpeed;

	private long refreshInterval;

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

	public boolean isRefreshIntervalIsEnabled() {
		return refreshIntervalIsEnabled;
	}

	public void setRefreshIntervalEnabled(boolean refreshIntervalIsEnabled) {
		this.refreshIntervalIsEnabled = refreshIntervalIsEnabled;
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

	public long getRefreshInterval() {
		return refreshInterval;
	}

	public void setRefreshInterval(long refreshInterval) {
		this.refreshInterval = refreshInterval;
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
