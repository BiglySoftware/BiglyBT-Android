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

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.BiglyBTApp;
import com.biglybt.android.client.CorePrefs;
import com.biglybt.core.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.TransferSpeedValidator;
import com.biglybt.core.download.DownloadManagerEnhancer;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.*;
import com.biglybt.core.logging.impl.LoggerImpl;
import com.biglybt.core.security.SESecurityManager;
import com.biglybt.core.util.*;
import com.biglybt.pif.*;
import com.biglybt.pif.update.*;
import com.biglybt.update.CorePatchChecker;
import com.biglybt.update.UpdaterUpdateChecker;
import com.biglybt.util.InitialisationFunctions;
import com.biglybt.util.Thunk;

import android.content.res.AssetManager;
import android.util.Log;

/**
 * This class sets up and manages the Vuze Core.
 * 
 * Android specific calls should be avoided in this class
 */
public class BiglyBTManager
{

	private static final String UI_NAME = "ac"; // Android Core

	private static final boolean RCM_ENABLE = true; // tux has started using this

	private static final boolean LONG_TERM_STATS_ENABLE = false;

	private static final boolean SPEED_MANAGER_ENABLE = false;

	private static final boolean TAG_MANAGER_ENABLE = true; // tux has started using these

	private static final boolean IP_FILTER_ENABLE = false;

	private static final boolean UPNPMS_ENABLE = false;

	private static final boolean UPNPAV_PUBLISH_TO_LAN = false;

	private static final boolean SUBSCRIPTIONS_ENABLE = true; // tux has started using this, 2016/10/25

	private static final String TAG = "Core";

	@Thunk
	static final LogIDs[] DEBUG_CORE_LOGGING_TYPES = CorePrefs.DEBUG_CORE
			? new LogIDs[] {
				LogIDs.CORE
			} : null;

	private static class MyOutputStream
		extends OutputStream
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
			if (b == null) {
				return;
			}
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

	public static boolean isShuttingDown() {
		return (is_closing);
	}

	@Thunk
	final Core core;

	// C:\Projects\adt-bundle-windows\sdk\platform-tools>dx --dex --output fred.jar azutp_0.3.0.jar

	public BiglyBTManager(File core_root) {

		if (CoreFactory.isCoreAvailable()) {
			core = CoreFactory.getSingleton();
			if (CorePrefs.DEBUG_CORE) {
				Log.w(TAG,
						"Core already available, using. isStarted? "
								+ core.isStarted() + "; isShuttingDown? "
								+ isShuttingDown());
			}
			if (isShuttingDown()) {
				return;
			}

			core.addLifecycleListener(new CoreLifecycleAdapter() {
				@Override
				public void started(Core azureus_core) {
					coreStarted();
				}

				@Override
				public void componentCreated(Core core,
						CoreComponent component) {
					if (component instanceof GlobalManager) {

						if (DownloadManagerEnhancer.getSingleton() == null) {
							InitialisationFunctions.earlyInitialisation(core);
						}
					}
				}

				@Override
				public void stopping(Core core) {
					is_closing = true;
				}
			});

			if (!core.isStarted()) {
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

		System.setProperty("az.force.noncvs", "1");
		System.setProperty("skip.shutdown.nondeamon.check", "1");
		System.setProperty("skip.shutdown.fail.killer", "1");
		System.setProperty("skip.dns.spi.test", "1");
		System.setProperty("log.missing.messages", "1");
		System.setProperty("skip.loggers.enabled.cvscheck", "1");
		System.setProperty("skip.loggers.setforced", "1");

		System.setProperty("azureus.config.path", core_root.getAbsolutePath());
		System.setProperty("azureus.install.path", core_root.getAbsolutePath());
		System.setProperty("azureus.time.use.raw.provider", "1");

		System.setProperty("az.factory.platformmanager.impl",
				"com.biglybt.android.core.az.PlatformManagerImpl");
		System.setProperty("az.factory.dnsutils.impl",
				"com.biglybt.android.core.az.DNSProvider");
		System.setProperty("az.factory.internat.bundle",
				"com.biglybt.ui.none.internat.MessagesBundle");

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

		new File(COConfigurationManager.getStringParameter(
				"Default save path")).mkdirs();
		new File(COConfigurationManager.getStringParameter(
				"General_sDefaultTorrent_Directory")).mkdirs();

		boolean ENABLE_LOGGING = false;

		COConfigurationManager.setParameter("Logger.Enabled", ENABLE_LOGGING);

		COConfigurationManager.setParameter("Logging Enable", ENABLE_LOGGING);
		COConfigurationManager.setParameter("Logging Dir", "C:\\temp");
		COConfigurationManager.setParameter("Logger.DebugFiles.Enabled", false);

		COConfigurationManager.setParameter("Start In Low Resource Mode", true);
		COConfigurationManager.setParameter("DHT.protocol.version.min", 51);
		COConfigurationManager.setParameter("network.tcp.enable_safe_selector_mode",
				false);

		COConfigurationManager.setParameter(
				TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY, false);
		COConfigurationManager.setParameter(
				TransferSpeedValidator.AUTO_UPLOAD_SEEDING_ENABLED_CONFIGKEY, false);
		COConfigurationManager.setIntDefault(
				TransferSpeedValidator.UPLOAD_CONFIGKEY, 25);
		COConfigurationManager.setIntDefault(
				TransferSpeedValidator.DOWNLOAD_CONFIGKEY, 0);

		COConfigurationManager.setParameter("tagmanager.enable",
				TAG_MANAGER_ENABLE);
		COConfigurationManager.setParameter("speedmanager.enable",
				SPEED_MANAGER_ENABLE);
		COConfigurationManager.setParameter("long.term.stats.enable",
				LONG_TERM_STATS_ENABLE);
		COConfigurationManager.setParameter("rcm.overall.enabled", RCM_ENABLE);

		COConfigurationManager.setParameter("Ip Filter Enabled", IP_FILTER_ENABLE);
		COConfigurationManager.setParameter("Ip Filter Banning Persistent", false); // user has no way of removing bans atm so don't persist them for safety

		if (UPNPMS_ENABLE) {
			COConfigurationManager.setParameter(
					"Plugin.azupnpav.upnpmediaserver.enable_publish",
					UPNPAV_PUBLISH_TO_LAN);
			COConfigurationManager.setParameter(
					"Plugin.azupnpav.upnpmediaserver.enable_upnp", false);
			COConfigurationManager.setParameter(
					"Plugin.azupnpav.upnpmediaserver.stream_port_upnp", false);
			COConfigurationManager.setParameter(
					"Plugin.azupnpav.upnpmediaserver.bind.use.default", false);
			COConfigurationManager.setParameter(
					"Plugin.azupnpav.upnpmediaserver.prevent_sleep", false);
			COConfigurationManager.setParameter("PluginInfo.azupnpav.enabled", true);
		} else {
			COConfigurationManager.setParameter("PluginInfo.azupnpav.enabled", false);
		}

		COConfigurationManager.setParameter("dht.net.cvs_v4.enable", false);
		COConfigurationManager.setParameter("dht.net.main_v6.enable", false);

		COConfigurationManager.setParameter("network.tcp.read.select.time", 500);
		COConfigurationManager.setParameter("network.tcp.read.select.min.time",
				500);
		COConfigurationManager.setParameter("network.tcp.write.select.time", 500);
		COConfigurationManager.setParameter("network.tcp.write.select.min.time",
				500);
		COConfigurationManager.setParameter("network.tcp.connect.select.time", 500);
		COConfigurationManager.setParameter("network.tcp.connect.select.min.time",
				500);

		COConfigurationManager.setParameter("network.udp.poll.time", 100);

		COConfigurationManager.setParameter("network.utp.poll.time", 100);

		COConfigurationManager.setParameter("network.control.read.idle.time", 100);
		COConfigurationManager.setParameter("network.control.write.idle.time", 100);

		COConfigurationManager.setParameter("diskmanager.perf.cache.enable", true);
		COConfigurationManager.setParameter("diskmanager.perf.cache.size", 2);
		COConfigurationManager.setParameter("diskmanager.perf.cache.flushpieces",
				false);
		COConfigurationManager.setParameter("diskmanager.perf.cache.enable.read",
				false);

		COConfigurationManager.setParameter("diskmanager.perf.read.maxthreads", 2);
		COConfigurationManager.setParameter("diskmanager.perf.read.maxmb", 2);
		COConfigurationManager.setParameter("diskmanager.perf.write.maxthreads", 2);
		COConfigurationManager.setParameter("diskmanager.perf.write.maxmb", 2);

		COConfigurationManager.setParameter("peermanager.schedule.time", 500);

		PluginManagerDefaults defaults = PluginManager.getDefaults();

		defaults.setDefaultPluginEnabled(PluginManagerDefaults.PID_BUDDY, false);
		defaults.setDefaultPluginEnabled(PluginManagerDefaults.PID_SHARE_HOSTER,
				false);
		defaults.setDefaultPluginEnabled(PluginManagerDefaults.PID_RSS, false);
		defaults.setDefaultPluginEnabled(PluginManagerDefaults.PID_NET_STATUS,
				false);

//	    preinstallPlugins();

		/*
		ConsoleInput.registerPluginCommand( ConsoleDebugCommand.class );
		*/

		// core set Plugin.DHT.dht.logging true boolean
		// core log on 'Distributed DB'

		core = CoreFactory.create();

		core.addLifecycleListener(new CoreLifecycleAdapter() {
			@Override
			public void started(Core azureus_core) {
				coreStarted();
			}

			public void componentCreated(Core core,
					CoreComponent component) {
				if (component instanceof GlobalManager) {

					InitialisationFunctions.earlyInitialisation(core);
				}
			}

			@Override
			public void stopping(Core core) {
				is_closing = true;
			}
		});
		coreInit();
		// remove me
		SESecurityManager.getAllTrustingTrustManager();
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
			Method setForced = AEDiagnosticsLogger.class.getDeclaredMethod(
					"setForced", boolean.class);

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
		new AEThread2("CoreInit") {
			public void run() {
				preinstallPlugins();
				core.start();
/*
				COConfigurationManager.setParameter( "Telnet_iPort", 57006 );
				COConfigurationManager.setParameter( "Telnet_sAllowedHosts", "127.0.0.1,192.168.1.5" );

				UIConst.UIS = new HashMap();

				UIConst.setCore( azureus_core );

				UIConst.startUI( "telnet", null );
*/
			}
		}.start();
	}

	@Thunk
	void coreStarted() {
		// disable the core component updaters otherwise they block plugin
		// updates

		PluginManager pm = core.getPluginManager();

		PluginInterface pi = pm.getPluginInterfaceByClass(CorePatchChecker.class);

		if (pi != null) {

			pi.getPluginState().setDisabled(true);
		}

		pi = pm.getPluginInterfaceByClass(UpdaterUpdateChecker.class);

		if (pi != null) {

			pi.getPluginState().setDisabled(true);
		}

		pm.getDefaultPluginInterface().addListener(new PluginAdapter() {

			@Override
			public void initializationComplete() {
				initComplete();
			}

			@Override
			public void closedownInitiated() {
			}

			@Override
			public void closedownComplete() {
			}
		});
	}

	@Thunk
	static void preinstallPlugins() {
		// Copy <assets>/plugins to <userpath>/plugins
		// (<userpath> is usually "<internal storage>/.biglybt")
		File plugins_dir = new File(SystemProperties.getUserPath(), "plugins");

		AssetManager assets = BiglyBTApp.getContext().getAssets();
		copyAssetDir(assets, "plugins", plugins_dir);
	}

	private static void copyAssetDir(AssetManager assetManager, String assetDir, File destDir) {
		try {
			String[] assetPluginsFiles = assetManager.list(assetDir);
			if (assetPluginsFiles.length == 0) {
				return;
			} 
			for (String fileOrDir : assetPluginsFiles) {
				copyAssetOrDir(assetManager, assetDir + "/" +  fileOrDir, destDir);
			}
		} catch (IOException ignore) {
		}
	}

	private static void copyAssetOrDir(AssetManager assetManager, String assetOrDir, File destDir) {
		try {
			String name = new File(assetOrDir).getName();
			String[] assetPluginsFiles = assetManager.list(assetOrDir);
			if (assetPluginsFiles.length == 0) {
				// it's a file, hopefully
				InputStream inputStream = assetManager.open(assetOrDir);
				File dest = new File(destDir, name);
				destDir.mkdirs();

				FileUtil.copyFile(inputStream, dest, true);
			} else {
				File newDestDir = new File(destDir, name);
				for (String subAssetOrDir : assetPluginsFiles) {
					copyAssetOrDir(assetManager, assetOrDir + "/" + subAssetOrDir, newDestDir);
				}
			}
		} catch (IOException ignore) {
			Log.e(TAG, "copyAssetOrDir", ignore);
		}
	}

	/*
	@Thunk
	static void old_preinstallPlugins() {
		File plugins_dir = new File(SystemProperties.getUserPath(), "plugins");
		File fileInstalledPlugins = new File(plugins_dir, "installed_plugins.txt");
		Map<String, Object> installedPlugins = new HashMap<>(5);
		try {
			String s = FileUtil.readFileAsString(fileInstalledPlugins, -1);
			Map<String, Object> map = JSONUtils.decodeJSON(s);
			installedPlugins.putAll(map);

			installedPlugins.keySet().retainAll(Arrays.asList(plugin_resources));

			if (CorePrefs.DEBUG_CORE) {
				Log.d("Core", installedPlugins.size() + " plugins already installed");
			}
		} catch (Exception e) {
		}

		boolean writeInstalledPluginsTxt = false;

		ClassLoader classLoader = VuzeFile.class.getClassLoader();
		for (String resource : plugin_resources) {

			if (classLoader.getResource(resource) == null) {
				Log.w("Core", "Can't locate plugin install file " + resource);
				continue;
			}

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

			InputStream is = classLoader.getResourceAsStream(resource);

			mapCheckFiles = new HashMap(4);

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
	*/

	@Thunk
	void initComplete() {
		//checkUpdates();
	}

	private void checkUpdates() {
		PluginManager pm = core.getPluginManager();

		UpdateManager update_manager = pm.getDefaultPluginInterface().getUpdateManager();

		final UpdateCheckInstance checker = update_manager.createUpdateCheckInstance();

		checker.addListener(new UpdateCheckInstanceListener() {
			public void cancelled(UpdateCheckInstance instance) {

			}

			public void complete(UpdateCheckInstance instance) {
				Update[] updates = instance.getUpdates();

				for (int i = 0; i < updates.length; i++) {

					Update update = updates[i];

					System.out.println("Update available for '" + update.getName()
							+ "', new version = " + update.getNewVersion());

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

	public Core getCore() {
		return (core);
	}

	public class OurLoggerImpl
		extends LoggerImpl
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
			int type = alert.entryType == LogAlert.LT_ERROR ? Log.ERROR
					: alert.entryType == LogAlert.LT_INFORMATION ? Log.INFO : Log.WARN;
			Log.println(type, "LogAlert", alert.text);
			if (alert.details != null && alert.details.length() > 0) {
				Log.println(type, "LogAlert", alert.details);
			}
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
				if (!found) {
					return;
				}
			}
			int type = entryType == LogEvent.LT_ERROR ? Log.ERROR
					: entryType == LogEvent.LT_INFORMATION ? Log.INFO : Log.WARN;
			Log.println(type, logID.toString(), text);
			if (err != null && entryType == LogEvent.LT_ERROR) {
				Log.e(logID.toString(), null, err);
			}
		}

		public OurLoggerImpl() {
		}

		@Override
		public void logTextResource(LogAlert alert) {
			int type = alert.entryType == LogAlert.LT_ERROR ? Log.ERROR
					: alert.entryType == LogAlert.LT_INFORMATION ? Log.INFO : Log.WARN;
			Log.println(type, "LogAlert", MessageText.getString(alert.text));
			if (alert.details != null && alert.details.length() > 0) {
				Log.println(type, "LogAlert", alert.details);
			}
		}

		@Override
		public void logTextResource(LogAlert alert, String[] params) {
			int type = alert.entryType == LogAlert.LT_ERROR ? Log.ERROR
					: alert.entryType == LogAlert.LT_INFORMATION ? Log.INFO : Log.WARN;
			String text;
			if (MessageText.keyExists(alert.text)) {
				text = MessageText.getString(alert.text, params);
			} else {
				text = "!" + alert.text + "(" + Arrays.toString(params) + ")!";
			}
			if (alert.details != null && alert.details.length() > 0) {
				text += "\n" + alert.details;
			}
			Log.println(type, "LogAlert", text);
		}

		@Override
		public void logTextResource(LogEvent event) {
			log(event.logID, event.entryType, MessageText.getString(event.text),
					event.err);
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
