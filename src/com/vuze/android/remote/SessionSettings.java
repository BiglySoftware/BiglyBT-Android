package com.vuze.android.remote;

import java.io.Serializable;

public class SessionSettings
	implements Serializable
{

	private static final long serialVersionUID = -9104780845902843703L;

	private boolean dlIsAuto;

	private boolean ulIsAuto;

	private boolean refreshIntervalIsAuto;

	private long dlSpeed;

	private long ulSpeed;

	private long refreshInterval;

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

	public boolean isRefreshIntervalIsAuto() {
		return refreshIntervalIsAuto;
	}

	public void setRefreshIntervalIsAuto(boolean refreshIntervalIsAuto) {
		this.refreshIntervalIsAuto = refreshIntervalIsAuto;
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

}
