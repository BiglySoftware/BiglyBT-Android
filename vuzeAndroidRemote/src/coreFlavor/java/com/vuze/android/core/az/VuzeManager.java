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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.impl.TransferSpeedValidator;
import org.gudy.azureus2.core3.global.GlobalManager;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.logging.*;
import org.gudy.azureus2.core3.logging.impl.LoggerImpl;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.plugins.*;
import org.gudy.azureus2.plugins.update.*;
import org.gudy.azureus2.update.CorePatchChecker;
import org.gudy.azureus2.update.UpdaterUpdateChecker;

import com.aelitis.azureus.core.*;
import com.aelitis.azureus.core.download.DownloadManagerEnhancer;
import com.aelitis.azureus.core.vuzefile.VuzeFile;
import com.aelitis.azureus.core.vuzefile.VuzeFileComponent;
import com.aelitis.azureus.core.vuzefile.VuzeFileHandler;
import com.aelitis.azureus.util.InitialisationFunctions;
import com.vuze.android.remote.CorePrefs;
import com.vuze.util.JSONUtils;
import com.vuze.util.MapUtils;
import com.vuze.util.Thunk;

import android.util.Log;

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

	private static final String TAG = "Core";

	private static final LogIDs[] DEBUG_CORE_LOGGING_TYPES = CorePrefs.DEBUG_CORE
			? new LogIDs[] {
				LogIDs.CORE
			} : null;

	private static class MyOutputStream extends OutputStream
	{
		protected final StringBuffer buffer = new StringBuffer(1024);

		String lastLine = "";

		final int type;

		public MyOutputStream(int type) {
			this.type = type;
		}

		public void write(int data) {
			char c = (char) data;

			if (c == '\n') {
				String s = buffer.toString();
				if (!lastLine.equals(s) && !s.startsWith("(HTTPLog")) {
					Log.println(type, "System", s);
					lastLine = s;
				}
				buffer.setLength(0);
			} else if (c != '\r') {
				buffer.append(c);
			}
		}

		public void write(byte b[], int off, int len) {
			for (int i = off; i < off + len; i++) {
				int d = b[i];
				if (d < 0)
					d += 256;
				write(d);
			}
		}
	}

	@Thunk
	static boolean is_closing = false;

	public static boolean
	isShuttingDown() {
		return (is_closing);
	}

	@Thunk
	final AzureusCore azureus_core;

	// C:\Projects\adt-bundle-windows\sdk\platform-tools>dx --dex --output fred.jar azutp_0.3.0.jar

	public VuzeManager(
		File core_root) {

		if (AzureusCoreFactory.isCoreAvailable()) {
			azureus_core = AzureusCoreFactory.getSingleton();
			if (CorePrefs.DEBUG_CORE) {
				Log.w(TAG, "Core already available, using. isStarted? " + azureus_core.isStarted() + "; isShuttingDown? " + isShuttingDown());
			}
			if (isShuttingDown()) {
				return;
			}

			azureus_core.addLifecycleListener(new AzureusCoreLifecycleAdapter()
			{
				@Override
				public void started(AzureusCore azureus_core) {
					coreStarted();
				}

				@Override
				public void componentCreated(AzureusCore core,
					AzureusCoreComponent component) {
					if (component instanceof GlobalManager) {

						if (DownloadManagerEnhancer.getSingleton() == null) {
							InitialisationFunctions.earlyInitialisation(core);
						}
					}
				}

				@Override
				public void stopping(AzureusCore core) {
					is_closing = true;
				}
			});

			if (!azureus_core.isStarted()) {
				coreInit();
			}
			return;
		}

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

		// core tries to access debug_1.log.  This normally isn't a problem, except
		// on some Android devices, accessing a file that doesn't exist (File.length)
		// spews warnings to stdout, which mess up out initialization phase
		File logs = new File(core_root, "logs");
		if (!logs.exists()) {
			logs.mkdirs();
			File boo = new File(logs, "debug_1.log");
			try {
				boo.createNewFile();
			} catch (IOException e) {
			}
		}

		System.setProperty("skip.shutdown.nondeamon.check", "1");

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
		//COConfigurationManager.resetToDefaults();

		fixupLogger();


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
		COConfigurationManager.setParameter("diskmanager.perf.cache.flushpieces", false);
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
					coreStarted();
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
		coreInit();
	}

	private void fixupLogger() {
		// On some Android devices, File.delete, File.length will write to stdout/err
		// This causes our core logger to stackoverflow.
		// Hack by forcing Logger init here, and taking back stdout/err

		try {

			Logger.doRedirects(); // makes sure loggerImpl is there
			Field fLoggerImpl = Logger.class.getDeclaredField("loggerImpl");
			OurLoggerImpl ourLogger = new OurLoggerImpl();
			fLoggerImpl.setAccessible(true);
			fLoggerImpl.set(null, ourLogger);

			System.setErr(new PrintStream(new MyOutputStream(Log.ERROR)));
			System.setOut(new PrintStream(new MyOutputStream(Log.WARN)));

		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}


		try {
			Field diag_logger = Debug.class.getDeclaredField("diag_logger");
			diag_logger.setAccessible(true);
			Object diag_logger_object = diag_logger.get(null);
			Method setForced = AEDiagnosticsLogger.class
				.getDeclaredMethod("setForced", boolean.class);

			setForced.setAccessible(true);
			setForced.invoke(diag_logger_object, false);
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
	}

	@Thunk
	void coreInit() {
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

	@Thunk
	void coreStarted() {
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

	@Thunk
	static void preinstallPlugins() {
		File plugins_dir = new File(SystemProperties.getUserPath(), "plugins");
		File fileInstalledPlugins = new File(plugins_dir, "installed_plugins.txt");
		Map<String, Object> installedPlugins = new HashMap<>(5);
		try {
			String s = FileUtil.readFileAsString(fileInstalledPlugins, -1);
			Map<String, Object> map = JSONUtils.decodeJSON(s);
			installedPlugins.putAll(map);

			if (CorePrefs.DEBUG_CORE) {
				Log.d("Core", installedPlugins.size() + " plugins already installed");
			}
		} catch (Exception e) {
		}

		boolean writeInstalledPluginsTxt = false;

		ClassLoader classLoader = VuzeFile.class.getClassLoader();
		for (String resource : plugin_resources) {

			Map mapCheckFiles = MapUtils.getMapMap(installedPlugins, resource, null);
			if (mapCheckFiles != null && mapCheckFiles.size() > 0) {
				try {
					boolean skip = true;
					for (Object o : mapCheckFiles.keySet()) {
						File file = new File(o.toString());
						Object val = mapCheckFiles.get(o);
						if (!(val instanceof Number)) {
							continue;
						}
						long size = ((Number) val).longValue();
						if ((size == 0 && !file.exists())
								|| (size > 0 && file.length() != size)) {
							skip = false;
							if (CorePrefs.DEBUG_CORE) {
								Log.d("Core", "Plugin needs re-installing: " + resource + " -- "
										+ file.getName() + " not correct size");
							}
							break;
						}
					}
					if (skip) {
						continue;
					}
				} catch (Throwable t) {
					if (CorePrefs.DEBUG_CORE) {
						Log.e("Core", "Error checking .vuze installed plugins", t);
					}
				}
			} else {
				if (CorePrefs.DEBUG_CORE) {
					Log.d("Core", "Load " + resource);
				}
			}

			mapCheckFiles = new HashMap(4);

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

							File plugin_dir = new File(plugins_dir, id);

							plugin_dir.mkdirs();

							if (CorePrefs.DEBUG_CORE) {
								Log.d("Core", "Copying " + resource + " to " + plugin_dir);
							}

							if (jar) {

								File fileDest = new File(plugin_dir,
										id + "_" + version + ".jar");
								FileUtil.copyFile(new ByteArrayInputStream(plugin_file),
										fileDest);

								mapCheckFiles.put(fileDest.getAbsolutePath(),
										fileDest.length());

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

										if (name.endsWith(".jar")) {
											mapCheckFiles.put(entry_file.getAbsolutePath(),
													entry_file.length());
										}
									}
								} finally {

									zis.close();
								}
							}

							if (mapCheckFiles.size() == 0) {
								mapCheckFiles.put(plugin_dir.getAbsolutePath(), 0);
							}

						} catch (Throwable e) {
							Log.e("Core", "Failed to load plugin " + resource, e);
						}
					}
				}
			} catch (Throwable e) {

				Log.e("Core", "Failed to load .vuze file: " + resource, e);

			} finally {

				try {
					is.close();

				} catch (Throwable e) {

					Debug.out(e);
				}
			}

			installedPlugins.put(resource, mapCheckFiles);
			writeInstalledPluginsTxt = true;
		}

		if (writeInstalledPluginsTxt) {
			FileUtil.writeStringAsFile(fileInstalledPlugins,
					JSONUtils.encodeToJSON(installedPlugins));
		} else {
			if (CorePrefs.DEBUG_CORE) {
				Log.d("Core", "All .vuze plugins are installed");
			}
		}
	}

	@Thunk
	void
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

	public class OurLoggerImpl extends LoggerImpl
	{
		@Override
		public void addListener(ILogEventListener aListener) {
		}

		@Override
		public void addListener(ILogAlertListener l) {
		}

		@Override
		public void allowLoggingToStdErr(boolean allowed) {
		}

		@Override
		public void doRedirects() {
		}

		@Override
		public PrintStream getOldStdErr() {
			return System.err;
		}

		@Override
		public void init() {
		}

		@Override
		public boolean isEnabled() {
			return DEBUG_CORE_LOGGING_TYPES != null;
		}

		@Override
		public void log(LogAlert alert) {
			int type = alert.entryType == LogAlert.LT_ERROR ? Log.ERROR : alert.entryType == LogAlert.LT_INFORMATION ? Log.INFO : Log.WARN;
			Log.println(type, "LogAlert", alert.text);
		}

		@Override
		public void log(LogEvent event) {
			log(event.logID, event.entryType, event.text, event.err);
		}

		private void log(LogIDs logID, int entryType, String text, Throwable err) {
			if (DEBUG_CORE_LOGGING_TYPES == null) {
				return;
			}
			boolean found = DEBUG_CORE_LOGGING_TYPES.length == 0;
			if (!found) {
				for (LogIDs id : DEBUG_CORE_LOGGING_TYPES) {
					if (id == logID) {
						found = true;
						break;
					}
				}
			}
			if (!found) {
				return;
			}
			int type = entryType == LogEvent.LT_ERROR ? Log.ERROR : entryType == LogEvent.LT_INFORMATION ? Log.INFO : Log.WARN;
			Log.println(type, logID.toString(), text);
			if (err != null && entryType == LogEvent.LT_ERROR){
				Log.e(logID.toString(), null, err);
			}
		}

		public OurLoggerImpl() {
		}

		@Override
		public void logTextResource(LogAlert alert) {
			int type = alert.entryType == LogAlert.LT_ERROR ? Log.ERROR : alert.entryType == LogAlert.LT_INFORMATION ? Log.INFO : Log.WARN;
			Log.println(type, "LogAlert", MessageText.getString(alert.text));
		}

		@Override
		public void logTextResource(LogAlert alert, String[] params) {
			int type = alert.entryType == LogAlert.LT_ERROR ? Log.ERROR : alert.entryType == LogAlert.LT_INFORMATION ? Log.INFO : Log.WARN;
			Log.println(type, "LogAlert", MessageText.getString(alert.text, params));
		}

		@Override
		public void logTextResource(LogEvent event) {
			log(event.logID, event.entryType, MessageText.getString(event.text), event.err);
		}

		@Override
		public void logTextResource(LogEvent event, String[] params) {
			String text;
			if (MessageText.keyExists(event.text)) {
				text = MessageText.getString(event.text, params);
			} else {
				text = "!" + event.text + "(" + Arrays.toString(params) + ")!";
			}
			log(event.logID, event.entryType, text, event.err);
		}

		@Override
		public void removeListener(ILogEventListener aListener) {
		}

		@Override
		public void removeListener(ILogAlertListener l) {
		}
	}

}
