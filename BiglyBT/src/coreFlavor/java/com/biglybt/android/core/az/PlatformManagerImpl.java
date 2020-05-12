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

package com.biglybt.android.core.az;

import java.io.File;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.BiglyBTApp;
import com.biglybt.core.Core;
import com.biglybt.core.util.SystemProperties;
import com.biglybt.pif.platform.PlatformManagerException;
import com.biglybt.platform.*;

import android.os.Environment;
import android.util.Log;

import androidx.annotation.Keep;

@Keep
public class PlatformManagerImpl
	implements PlatformManager
{
	private final Set<PlatformManagerCapabilities> capabilities = new HashSet<>();

	public PlatformManagerImpl() {
		capabilities.add(PlatformManagerCapabilities.GetUserDataDirectory);
	}

	@Override
	public int getPlatformType() {
		return (PT_OTHER);
	}

	@Override
	public String getVersion()

			throws PlatformManagerException {
		return ("1");
	}

	@Override
	public boolean hasCapability(PlatformManagerCapabilities capability) {
		return (capabilities.contains(capability));
	}

	@Override
	public boolean isAdditionalFileTypeRegistered(String name, String type)

			throws PlatformManagerException {
		return (true);
	}

	@Override
	public void registerAdditionalFileType(String name, String description,
																				 String type, String content_type)

			throws PlatformManagerException {
	}

	@Override
	public void unregisterAdditionalFileType(String name, String type)

			throws PlatformManagerException {
	}

	@Override
	public void showFile(String file_name)

			throws PlatformManagerException {
		unsupported();
	}

	@Override
	public File getLocation(long location_id)

			throws PlatformManagerException {
		if (location_id == LOC_USER_DATA) {

			return (new File(getUserDataDirectory()));

		} else if (location_id == LOC_DOWNLOADS) {

			return Environment.getExternalStoragePublicDirectory(
					Environment.DIRECTORY_DOWNLOADS);
		} else if (location_id == LOC_DOCUMENTS) {

			return Environment.getExternalStoragePublicDirectory(
					Environment.DIRECTORY_DOWNLOADS);
		} else if (location_id == LOC_MUSIC) {

			return Environment.getExternalStoragePublicDirectory(
					Environment.DIRECTORY_MUSIC);
		} else if (location_id == LOC_VIDEO) {

			return Environment.getExternalStoragePublicDirectory(
					Environment.DIRECTORY_MOVIES);

		} else {

			return (null);
		}
	}

	@Override
	public String getComputerName() {
		return AndroidUtils.getFriendlyDeviceName();
	}

	@Override
	public void startup(Core core)

			throws PlatformManagerException {
	}

	@Override
	public String getUserDataDirectory()

			throws PlatformManagerException {
		return (SystemProperties.getApplicationPath());
	}

	@Override
	public boolean isApplicationRegistered()

			throws PlatformManagerException {
		return (true);
	}

	@Override
	public void registerApplication()

			throws PlatformManagerException {
	}

	@Override
	public String getApplicationCommandLine()

			throws PlatformManagerException {
		unsupported();

		return (null);
	}

	@Override
	public File getVMOptionFile()

			throws PlatformManagerException {
		unsupported();

		return (null);
	}

	@Override
	public String[] getExplicitVMOptions()

			throws PlatformManagerException {
		unsupported();

		return (null);
	}

	@Override
	public void setExplicitVMOptions(String[] options)

			throws PlatformManagerException {
	}

	@Override
	public boolean getRunAtLogin()

			throws PlatformManagerException {
		unsupported();

		return (false);
	}

	@Override
	public void setRunAtLogin(boolean run)

			throws PlatformManagerException {
		unsupported();
	}

	@Override
	public int getShutdownTypes() {
		return (0);
	}

	@Override
	public void shutdown(int type)

			throws PlatformManagerException {
		unsupported();
	}

	@Override
	public void setPreventComputerSleep(boolean prevent_it)

			throws PlatformManagerException {

	}

	@Override
	public boolean getPreventComputerSleep() {
		return (false);
	}

	@Override
	public void createProcess(String command_line, boolean inherit_handles)

			throws PlatformManagerException {

	}

	@Override
	public void performRecoverableFileDelete(String file_name)

			throws PlatformManagerException {

	}

	@Override
	public void setTCPTOSEnabled(boolean enabled)

			throws PlatformManagerException {

	}

	@Override
	public void copyFilePermissions(String from_file_name, String to_file_name)

			throws PlatformManagerException {

	}

	@Override
	public boolean testNativeAvailability(String name)

			throws PlatformManagerException {
		unsupported();

		return (false);
	}

	@Override
	public void traceRoute(InetAddress interface_address, InetAddress target,
												 PlatformManagerPingCallback callback)

			throws PlatformManagerException {
		unsupported();
	}

	@Override
	public void ping(InetAddress interface_address, InetAddress target,
									 PlatformManagerPingCallback callback)

			throws PlatformManagerException {
		unsupported();
	}

	@Override
	public int getMaxOpenFiles()

			throws PlatformManagerException {
		unsupported();

		return (0);
	}

	@Override
	public void dispose() {
	}

	@Override
	public void addListener(PlatformManagerListener listener) {
	}

	@Override
	public void removeListener(PlatformManagerListener listener) {
	}

	@Override
	public void requestUserAttention(int type, Object data)

			throws PlatformManagerException {
		unsupported();
	}

	@Override
	public Class<?> loadClass(ClassLoader loader, String class_name)

			throws PlatformManagerException {
		try {
			String dex_path = "";

			if (loader instanceof URLClassLoader) {

				URLClassLoader ucl = (URLClassLoader) loader;

				URL[] urls = ucl.getURLs();

				if (AndroidUtils.DEBUG) {
					Log.i("Core", class_name + "] urls=" + Arrays.toString(urls));
				}

				for (URL u : urls) {

					File f = new File(u.toURI());

					if (f.exists()) {

						dex_path += (dex_path.length() == 0 ? "" : File.separator)
								+ f.getAbsolutePath();

					} else {

						dex_path = "";

						Log.e("Core", "Can't resolve url '" + u + "'");

						break;
					}
				}
			} else {
				if (AndroidUtils.DEBUG) {
					Log.d("Core",
							class_name + " load is " + loader.getClass().getSimpleName());
				}
			}

			if (dex_path.length() > 0 && AndroidUtils.DEBUG) {
				Log.w("Core", class_name + "] URLClassLoader; dex_path=" + dex_path);
			}

			return loader.loadClass(class_name);

		} catch (Throwable e) {

			throw new PlatformManagerException("load of '" + class_name + "' failed",
					e);
		}
	}

	private static void unsupported()

			throws PlatformManagerException {
		throw (new PlatformManagerException("Unsupported"));
	}
}
