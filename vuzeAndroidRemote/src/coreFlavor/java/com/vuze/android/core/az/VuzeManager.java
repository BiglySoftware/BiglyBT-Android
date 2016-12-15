/*
 * Created on Jan 18, 2013
 * Created by Paul Gardner
 * 
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License only.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.vuze.android.core.az;

import java.io.*;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.impl.TransferSpeedValidator;
import org.gudy.azureus2.core3.global.GlobalManager;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.plugins.*;
import org.gudy.azureus2.plugins.update.*;
import org.gudy.azureus2.update.CorePatchChecker;
import org.gudy.azureus2.update.UpdaterUpdateChecker;

import com.aelitis.azureus.core.*;
import com.aelitis.azureus.core.vuzefile.VuzeFile;
import com.aelitis.azureus.core.vuzefile.VuzeFileComponent;
import com.aelitis.azureus.core.vuzefile.VuzeFileHandler;
import com.aelitis.azureus.util.InitialisationFunctions;

public class
VuzeManager
{
	private static final String UI_NAME = "ac";    // Android Core


	private static final boolean RCM_ENABLE = true;    // tux has started using this

	private static final boolean LONG_TERM_STATS_ENABLE = false;

	private static final boolean SPEED_MANAGER_ENABLE = false;

	private static final boolean TAG_MANAGER_ENABLE = true;    // tux has started using these

	private static final boolean IP_FILTER_ENABLE = false;

	private static final boolean UPNPAV_PUBLISH_TO_LAN = true;

	private static final boolean SUBSCRIPTIONS_ENABLE = true;    // tux has started using this, 2016/10/25

	private static final String[] plugin_resources = {
		"com/vuze/android/core/az/plugins/aercm-res_0.5.18.vuze",
		"com/vuze/android/core/az/plugins/azupnpav-res_0.5.6.vuze",
		"com/vuze/android/core/az/plugins/azutp-res_0.5.6.vuze",
		"com/vuze/android/core/az/plugins/mlDHT-res_1.5.8.vuze",
		"com/vuze/android/core/az/plugins/xmwebui-res_0.6.4.vuze",
	};

	private static boolean is_closing;

	public static boolean
	isShuttingDown() {
		return (is_closing);
	}

	private AzureusCore azureus_core;

	// C:\Projects\adt-bundle-windows\sdk\platform-tools>dx --dex --output fred.jar azutp_0.3.0.jar

	public VuzeManager(
		File core_root) {
		try {
			System.setProperty("android.os.build.version.release",
				android.os.Build.VERSION.RELEASE);
			System.setProperty("android.os.build.version.sdk_int",
				String.valueOf(android.os.Build.VERSION.SDK_INT));

		} catch (Throwable e) {

			System.err.println(
				"Not running in an Android environment, not setting associated system properties");
		}

		core_root.mkdirs();

		System.setProperty("azureus.config.path", core_root.getAbsolutePath());
		System.setProperty("azureus.install.path", core_root.getAbsolutePath());
		System.setProperty("azureus.time.use.raw.provider", "1");

		System.setProperty("az.factory.platformmanager.impl",
			"com.vuze.android.core.az.PlatformManagerImpl");
		System.setProperty("az.factory.dnsutils.impl",
			"com.vuze.android.core.az.DNSProvider");
		System.setProperty("az.factory.internat.bundle",
			"org.gudy.azureus2.ui.none.internat.MessagesBundle");

		if (!SUBSCRIPTIONS_ENABLE) {
			System.setProperty("az.factory.subscriptionmanager.impl", "");
		}

		System.setProperty("az.factory.devicemanager.impl", "");

		System.setProperty("az.thread.pool.naming.enable", "false");
		System.setProperty("az.xmwebui.skip.ssl.hack", "true");
		System.setProperty("az.logging.save.debug", "false");
		System.setProperty("az.logging.keep.ui.history", "false");

		COConfigurationManager.initialise();

		COConfigurationManager.setParameter("ui", UI_NAME);

		COConfigurationManager.setParameter("Save Torrent Files", true);

		new File(COConfigurationManager.getStringParameter("Default save path"))
			.mkdirs();
		new File(COConfigurationManager
			.getStringParameter("General_sDefaultTorrent_Directory")).mkdirs();

		boolean ENABLE_LOGGING = false;

		COConfigurationManager.setParameter("Logger.Enabled", ENABLE_LOGGING);

		COConfigurationManager.setParameter("Logging Enable", ENABLE_LOGGING);
		COConfigurationManager.setParameter("Logging Dir", "C:\\temp");

		COConfigurationManager.setParameter("Start In Low Resource Mode", true);
		COConfigurationManager.setParameter("DHT.protocol.version.min", 51);

		COConfigurationManager
			.setParameter(TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY,
				false);
		COConfigurationManager.setParameter(
			TransferSpeedValidator.AUTO_UPLOAD_SEEDING_ENABLED_CONFIGKEY, false);
		COConfigurationManager
			.setIntDefault(TransferSpeedValidator.UPLOAD_CONFIGKEY, 25);
		COConfigurationManager
			.setIntDefault(TransferSpeedValidator.DOWNLOAD_CONFIGKEY, 0);

		COConfigurationManager
			.setParameter("tagmanager.enable", TAG_MANAGER_ENABLE);
		COConfigurationManager
			.setParameter("speedmanager.enable", SPEED_MANAGER_ENABLE);
		COConfigurationManager
			.setParameter("long.term.stats.enable", LONG_TERM_STATS_ENABLE);
		COConfigurationManager.setParameter("rcm.overall.enabled", RCM_ENABLE);

		COConfigurationManager.setParameter("Ip Filter Enabled", IP_FILTER_ENABLE);
		COConfigurationManager.setParameter("Ip Filter Banning Persistent",
			false);  // user has no way of removing bans atm so don't persist them for safety

		COConfigurationManager
			.setParameter("Plugin.azupnpav.upnpmediaserver.enable_publish",
				UPNPAV_PUBLISH_TO_LAN);

		COConfigurationManager.setParameter("dht.net.cvs_v4.enable", false);
		COConfigurationManager.setParameter("dht.net.main_v6.enable", false);

		COConfigurationManager.setParameter("network.tcp.read.select.time", 500);
		COConfigurationManager
			.setParameter("network.tcp.read.select.min.time", 500);
		COConfigurationManager.setParameter("network.tcp.write.select.time", 500);
		COConfigurationManager
			.setParameter("network.tcp.write.select.min.time", 500);
		COConfigurationManager.setParameter("network.tcp.connect.select.time", 500);
		COConfigurationManager
			.setParameter("network.tcp.connect.select.min.time", 500);

		COConfigurationManager.setParameter("network.udp.poll.time", 100);

		COConfigurationManager.setParameter("network.utp.poll.time", 100);


		COConfigurationManager.setParameter("network.control.read.idle.time", 100);
		COConfigurationManager.setParameter("network.control.write.idle.time", 100);

		COConfigurationManager.setParameter("diskmanager.perf.cache.enable", true);
		COConfigurationManager.setParameter("diskmanager.perf.cache.size", 2);
		COConfigurationManager
			.setParameter("diskmanager.perf.cache.enable.read", false);

		COConfigurationManager.setParameter("diskmanager.perf.read.maxthreads", 2);
		COConfigurationManager.setParameter("diskmanager.perf.read.maxmb", 2);
		COConfigurationManager.setParameter("diskmanager.perf.write.maxthreads", 2);
		COConfigurationManager.setParameter("diskmanager.perf.write.maxmb", 2);


		COConfigurationManager.setParameter("peermanager.schedule.time", 500);

		PluginManagerDefaults defaults = PluginManager.getDefaults();

		defaults.setDefaultPluginEnabled(PluginManagerDefaults.PID_BUDDY, false);
		defaults
			.setDefaultPluginEnabled(PluginManagerDefaults.PID_SHARE_HOSTER, false);
		defaults.setDefaultPluginEnabled(PluginManagerDefaults.PID_RSS, false);
		defaults
			.setDefaultPluginEnabled(PluginManagerDefaults.PID_NET_STATUS, false);

//	    preinstallPlugins();

		/*
		ConsoleInput.registerPluginCommand( ConsoleDebugCommand.class );
		*/

		// core set Plugin.DHT.dht.logging true boolean
		// core log on 'Distributed DB'

		azureus_core = AzureusCoreFactory.create();

		azureus_core.addLifecycleListener(
			new AzureusCoreLifecycleAdapter()
			{
				@Override
				public void
				started(
					AzureusCore azureus_core) {
					// disable the core component updaters otherwise they block plugin
					// updates

					PluginManager pm = azureus_core.getPluginManager();

					PluginInterface pi = pm
						.getPluginInterfaceByClass(CorePatchChecker.class);

					if (pi != null) {

						pi.getPluginState().setDisabled(true);
					}

					pi = pm.getPluginInterfaceByClass(UpdaterUpdateChecker.class);

					if (pi != null) {

						pi.getPluginState().setDisabled(true);
					}

					pm.getDefaultPluginInterface().addListener(
						new PluginAdapter()
						{

							@Override
							public void initializationComplete() {
								initComplete();
							}

							@Override
							public void
							closedownInitiated() {
							}

							@Override
							public void
							closedownComplete() {
							}
						});
				}

				public void
				componentCreated(
					AzureusCore core,
					AzureusCoreComponent component) {
					if (component instanceof GlobalManager) {

						InitialisationFunctions.earlyInitialisation(core);
					}
				}

				@Override
				public void
				stopping(
					AzureusCore core) {
					is_closing = true;
				}
			});

		new AEThread2("CoreInit")
		{
			public void
			run() {
				preinstallPlugins();
				azureus_core.start();
/*
				COConfigurationManager.setParameter( "Telnet_iPort", 57006 );
				COConfigurationManager.setParameter( "Telnet_sAllowedHosts", "127.0.0.1,192.168.1.5" );

				UIConst.UIS = new HashMap();

				UIConst.setAzureusCore( azureus_core );

				UIConst.startUI( "telnet", null );
*/
			}
		}.start();
	}

	private void
	preinstallPlugins() {
		ClassLoader classLoader = VuzeFile.class.getClassLoader();
		for (String resource : plugin_resources) {

			InputStream is = classLoader.getResourceAsStream(resource);

			try {
				VuzeFileHandler vfh = VuzeFileHandler.getSingleton();

				VuzeFile vf = vfh.loadVuzeFile(is);

				VuzeFileComponent[] comps = vf.getComponents();

				for (int j = 0; j < comps.length; j++) {

					VuzeFileComponent comp = comps[j];

					if (comp.getType() == VuzeFileComponent.COMP_TYPE_PLUGIN) {

						try {
							Map content = comp.getContent();

							String id = new String((byte[]) content.get("id"), "UTF-8");
							String version = new String((byte[]) content.get("version"),
								"UTF-8");
							boolean jar = ((Long) content.get("is_jar")).longValue() == 1;

							byte[] plugin_file = (byte[]) content.get("file");

							File plugin_dir = new File(
								new File(SystemProperties.getUserPath(), "plugins"), id);

							plugin_dir.mkdirs();

							if (jar) {

								FileUtil.copyFile(new ByteArrayInputStream(plugin_file),
									new File(plugin_dir, id + "_" + version + ".jar"));

							} else {

								ZipInputStream zis = new ZipInputStream(
									new ByteArrayInputStream(plugin_file));

								try {
									while (true) {

										ZipEntry entry = zis.getNextEntry();

										if (entry == null) {

											break;
										}

										if (entry.isDirectory()) {

											continue;
										}

										String name = entry.getName();

										FileOutputStream entry_os = null;
										File entry_file = null;

										if (!name.endsWith("/")) {

											entry_file = new File(plugin_dir,
												name.replace('/', File.separatorChar));

											entry_file.getParentFile().mkdirs();

											entry_os = new FileOutputStream(entry_file);
										}

										try {
											byte[] buffer = new byte[65536];

											while (true) {

												int len = zis.read(buffer);

												if (len <= 0) {

													break;
												}

												if (entry_os != null) {

													entry_os.write(buffer, 0, len);
												}
											}
										} finally {

											if (entry_os != null) {

												entry_os.close();
											}
										}
									}
								} finally {

									zis.close();
								}
							}
						} catch (Throwable e) {

							Debug.out(e);
						}
					}
				}
			} catch (Throwable e) {

				Debug.out("Failed to load .vuze file: " + resource, e);

			} finally {

				try {
					is.close();

				} catch (Throwable e) {

					Debug.out(e);
				}
			}
		}
	}

	private void
	initComplete() {
		//checkUpdates();
	}

	private void
	checkUpdates() {
		PluginManager pm = azureus_core.getPluginManager();

		UpdateManager update_manager = pm.getDefaultPluginInterface()
			.getUpdateManager();

		final UpdateCheckInstance checker = update_manager
			.createUpdateCheckInstance();

		checker.addListener(
			new UpdateCheckInstanceListener()
			{
				public void
				cancelled(
					UpdateCheckInstance instance) {

				}

				public void
				complete(
					UpdateCheckInstance instance) {
					Update[] updates = instance.getUpdates();

					for (int i = 0; i < updates.length; i++) {

						Update update = updates[i];

						System.out.println("Update available for '" + update.getName() +
							"', new version = " + update.getNewVersion());

						String[] descs = update.getDescription();

						for (int j = 0; j < descs.length; j++) {

							System.out.println("\t" + descs[j]);
						}
					}

					checker.cancel();
				}
			});

		checker.start();
	}

	public AzureusCore
	getCore() {
		return (azureus_core);
	}
}
