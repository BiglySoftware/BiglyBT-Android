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

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.util.Log;

import androidx.annotation.NonNull;

import com.biglybt.android.client.*;
import com.biglybt.android.client.rpc.RPC;
import com.biglybt.android.client.service.BiglyBTService;
import com.biglybt.android.util.FileUtils;
import com.biglybt.android.util.MapUtils;
import com.biglybt.android.util.NetworkState;
import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys.Connection;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.config.impl.TransferSpeedValidator;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.*;
import com.biglybt.core.logging.impl.LoggerImpl;
import com.biglybt.core.security.SESecurityManager;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginManager;
import com.biglybt.pif.PluginManagerDefaults;
import com.biglybt.util.Thunk;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

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

	private static final String TAG = "Core";

	@Thunk
	static final LogIDs[] DEBUG_CORE_LOGGING_TYPES = CorePrefs.DEBUG_CORE
			? new LogIDs[] {
				LogIDs.CORE
			} : null;

	private static final String DEFAULT_WEBUI_PW_DISABLED_WHITELIST = "localhost,127.0.0.1,[::1],$";

	private static final String DEFAULT_WEBUI_PW_LAN_ONLY = DEFAULT_WEBUI_PW_DISABLED_WHITELIST
			+ ",192.168.0.0-192.168.255.255,10.0.0.0-10.255.255.255,172.16.0.0-172.31.255.255";

	private static class MyOutputStream
		extends OutputStream
	{
		protected final StringBuffer buffer = new StringBuffer(1024);

		@NonNull
		String lastLine = "";

		final int type;

		public MyOutputStream(int type) {
			this.type = type;
		}

		@Override
		public void write(int data) {
			char c = (char) data;

			if (c == '\n') {
				String s = buffer.toString();
				if (!lastLine.equals(s) && !s.startsWith("(HTTPLog")) { //NON-NLS
					Log.println(type, "System", s);
					lastLine = s;
				}
				buffer.setLength(0);
			} else if (c != '\r') {
				buffer.append(c);
			}
		}

		@SuppressWarnings("MethodDoesntCallSuperMethod")
		@Override
		public void write(@NonNull byte[] b, int off, int len) {
			for (int i = off; i < off + len; i++) {
				int d = b[i];
				if (d < 0)
					d += 256;
				write(d);
			}
		}
	}

	@Thunk
	final Core core;

	@NonNull
	private final BiglyBTService service;

	private boolean bindToLocalHost = false;

	private int bindToLocalHostReasonID = R.string.core_noti_sleeping;

	public BiglyBTManager(@NonNull File core_root,
			@NonNull BiglyBTService service) {
		this.service = service;
		CorePrefs corePrefs = CorePrefs.getInstance();
		preCoreInit(corePrefs, core_root);

		if (CoreFactory.isCoreAvailable()) {
			core = CoreFactory.getSingleton();
			if (CorePrefs.DEBUG_CORE) {
				Log.w(TAG,
						"Core already available, using. isStarted? " + core.isStarted());
			}

			if (!core.isStarted()) {
				coreInit();
			}
			return;
		}

		COConfigurationManager.initialise();
		// custom config will be now applied

		// When user changes a bind setting, cache it.  We overwrite them when
		// "bindToLocalHost" (sleeping), and need to restore them when not sleeping
		String[] bindIDs = {
			Connection.BCFG_ENFORCE_BIND_IP,
			Connection.BCFG_CHECK_BIND_IP_ON_START,
			Connection.SCFG_BIND_IP
		};
		ParameterListener bindParamListener = parameterName -> {
			String cachedParamName = "android." + parameterName;
			Object o = COConfigurationManager.getParameter(parameterName);
			if (o instanceof Boolean) {
				COConfigurationManager.setParameter(cachedParamName, (boolean) o);
			} else if (o instanceof byte[]) {
				COConfigurationManager.setParameter(cachedParamName, (byte[]) o);
			}
		};
		COConfigurationManager.addParameterListener(bindIDs, bindParamListener);
		// User of older version may have set bind ip.  Ensure it gets cached.
		String cachedBindIP = COConfigurationManager.getStringParameter(
				Connection.SCFG_BIND_IP);
		if (!cachedBindIP.isEmpty() && !cachedBindIP.equals("127.0.0.1")) {
			for (String bindID : bindIDs) {
				bindParamListener.parameterChanged(bindID);
			}
		}

		//COConfigurationManager.resetToDefaults();
		//COConfigurationManager.setParameter("Plugin.aercm.rcm.ui.enable", false);

		@NonNull
		final ConfigurationDefaults coreDefaults = ConfigurationDefaults.getInstance();

		if (CorePrefs.DEBUG_CORE) {
			// in release mode, this method and OurLoggerImpl will be removed by R8 Shrinking (Proguard)
			fixupLogger();
		}

		COConfigurationManager.setParameter("ui", UI_NAME);

		coreDefaults.addParameter("Save Torrent Files", true);

		FileUtil.newFile(COConfigurationManager.getStringParameter(
				"Default save path")).mkdirs();
		FileUtil.newFile(COConfigurationManager.getStringParameter(
				"General_sDefaultTorrent_Directory")).mkdirs();

		boolean ENABLE_LOGGING = false;

		COConfigurationManager.setParameter("Logger.Enabled", ENABLE_LOGGING);

		COConfigurationManager.setParameter("Logging Enable", ENABLE_LOGGING);
		COConfigurationManager.setParameter("Logging Dir", "C:\\temp");
		COConfigurationManager.setParameter("Logger.DebugFiles.Enabled", false);

		coreDefaults.addParameter("Start In Low Resource Mode", true);
		coreDefaults.addParameter("DHT.protocol.version.min", 51);
		coreDefaults.addParameter("network.tcp.enable_safe_selector_mode", false);

		coreDefaults.addParameter(
				TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY, false);
		coreDefaults.addParameter(
				TransferSpeedValidator.AUTO_UPLOAD_SEEDING_ENABLED_CONFIGKEY, false);
		coreDefaults.addParameter(TransferSpeedValidator.UPLOAD_CONFIGKEY, 25);
		coreDefaults.addParameter(TransferSpeedValidator.DOWNLOAD_CONFIGKEY, 0);

		coreDefaults.addParameter("tagmanager.enable", TAG_MANAGER_ENABLE);
		coreDefaults.addParameter("speedmanager.enable", SPEED_MANAGER_ENABLE);
		coreDefaults.addParameter("long.term.stats.enable", LONG_TERM_STATS_ENABLE);
		coreDefaults.addParameter("rcm.overall.enabled", RCM_ENABLE);

		coreDefaults.addParameter("Ip Filter Enabled", IP_FILTER_ENABLE);
		coreDefaults.addParameter("Ip Filter Banning Persistent", false); // user has no way of removing bans atm so don't persist them for safety

		// Ensure plugins are enabled..
		COConfigurationManager.setParameter("PluginInfo.aercm.enabled", true);
		COConfigurationManager.setParameter("PluginInfo.azutp.enabled", true);
		COConfigurationManager.setParameter("PluginInfo.azbpmagnet.enabled", true);
		COConfigurationManager.setParameter("PluginInfo.azbpupnp.enabled", true);

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

		coreDefaults.addParameter("dht.net.cvs_v4.enable", false);
		coreDefaults.addParameter("dht.net.main_v6.enable", false);

		coreDefaults.addParameter("Listen.Port.Randomize.Enable", true);
		coreDefaults.addParameter("network.tcp.read.select.time", 500);
		coreDefaults.addParameter("network.tcp.read.select.min.time", 500);
		coreDefaults.addParameter("network.tcp.write.select.time", 500);
		coreDefaults.addParameter("network.tcp.write.select.min.time", 500);
		coreDefaults.addParameter("network.tcp.connect.select.time", 500);
		coreDefaults.addParameter("network.tcp.connect.select.min.time", 500);

		coreDefaults.addParameter("network.udp.poll.time", 100);

		coreDefaults.addParameter("network.utp.poll.time", 100);

		coreDefaults.addParameter("network.control.read.idle.time", 100);
		coreDefaults.addParameter("network.control.write.idle.time", 100);

		coreDefaults.addParameter("diskmanager.perf.cache.enable", true);
		coreDefaults.addParameter("diskmanager.perf.cache.size", 2);
		coreDefaults.addParameter("diskmanager.perf.cache.flushpieces", false);
		coreDefaults.addParameter("diskmanager.perf.cache.enable.read", false);

		coreDefaults.addParameter("diskmanager.perf.read.maxthreads", 2);
		coreDefaults.addParameter("diskmanager.perf.read.maxmb", 2);
		coreDefaults.addParameter("diskmanager.perf.write.maxthreads", 2);
		coreDefaults.addParameter("diskmanager.perf.write.maxmb", 2);

		// Hash Checking Strategy: CPU/Disk Friendly
		coreDefaults.addParameter("diskmanager.hashchecking.strategy", 0);

		coreDefaults.addParameter("peermanager.schedule.time", 500);

		coreDefaults.addParameter("Tracker Client Scrape Stopped Enable", false);
		coreDefaults.addParameter("Tracker Client Closedown Timeout", 5);
		coreDefaults.addParameter("Tracker Client Numwant Limit", 10);

		PluginManagerDefaults defaults = PluginManager.getDefaults();

		defaults.setDefaultPluginEnabled(PluginManagerDefaults.PID_BUDDY, false);
		defaults.setDefaultPluginEnabled(PluginManagerDefaults.PID_SHARE_HOSTER,
				false);
		defaults.setDefaultPluginEnabled(PluginManagerDefaults.PID_RSS, false);
		defaults.setDefaultPluginEnabled(PluginManagerDefaults.PID_NET_STATUS,
				false);

		preinstallPlugins();

		/*
		ConsoleInput.registerPluginCommand( ConsoleDebugCommand.class );
		*/

		// core set Plugin.DHT.dht.logging true boolean
		// core log on 'Distributed DB'

		core = CoreFactory.create();

		coreInit();
		// remove me
		SESecurityManager.getAllTrustingTrustManager();
	}

	private void preCoreInit(@NonNull CorePrefs corePrefs,
			@NonNull File biglybtCoreConfigRoot) {
		File biglybtCustomDir = new File(biglybtCoreConfigRoot, "custom");
		biglybtCustomDir.mkdirs();

		System.setProperty("bdecoder.new", "1");
		try {
			System.setProperty("android.os.build.version.release", //NON-NLS
					android.os.Build.VERSION.RELEASE);
			System.setProperty("android.os.build.version.sdk_int", //NON-NLS
					String.valueOf(android.os.Build.VERSION.SDK_INT));

		} catch (Throwable e) {

			System.err.println(
					"Not running in an Android environment, not setting associated system properties");
		}

		// core tries to access debug_1.log.  This normally isn't a problem, except
		// on some Android devices, accessing a file that doesn't exist (File.length)
		// spews warnings to stdout, which mess up out initialization phase
		File logs = new File(biglybtCoreConfigRoot, "logs"); //NON-NLS
		if (!logs.exists()) {
			logs.mkdirs();
			File boo = new File(logs, "debug_1.log"); //NON-NLS
			try {
				boo.createNewFile();
			} catch (IOException e) {
			}
		}

		if (BiglyBTManager.DEBUG_CORE_LOGGING_TYPES != null
				&& BiglyBTManager.DEBUG_CORE_LOGGING_TYPES.length == 0) {
			System.setProperty("DIAG_TO_STDOUT", "1");
		}

		System.setProperty("az.force.noncvs", "1");
		System.setProperty("skip.shutdown.nondeamon.check", "1");
		System.setProperty("skip.shutdown.fail.killer", "1");
		System.setProperty("skip.dns.spi.test", "1");
		if (CorePrefs.DEBUG_CORE) {
			System.setProperty("log.missing.messages", "1");
			System.setProperty("DIAG_TO_STDOUT", "1");
		}
		System.setProperty("skip.loggers.enabled.cvscheck", "1");
		System.setProperty("skip.loggers.setforced", "1");

		System.setProperty(SystemProperties.SYSPROP_CONFIG_PATH,
				biglybtCoreConfigRoot.getAbsolutePath());
		System.setProperty(SystemProperties.SYSPROP_INSTALL_PATH,
				biglybtCoreConfigRoot.getAbsolutePath());
		System.setProperty("azureus.time.use.raw.provider", "1");

		System.setProperty("az.factory.platformmanager.impl",
				PlatformManagerImpl.class.getName());
		System.setProperty("az.factory.dnsutils.impl", DNSProvider.class.getName());
		System.setProperty("az.factory.internat.bundle",
				"com.biglybt.ui.android.internat.MessagesBundle");
		System.setProperty("com.biglybt.ui.swt.core.pairing.PMSWTImpl",
				PairingUIAdapter.class.getName());
		System.setProperty("az.factory.ClientRestarter.impl",
				ClientRestarterImpl.class.getName());

		if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
			System.setProperty("az.FileHandling.impl",
					AndroidFileHandler.class.getName());
		}

		System.setProperty("az.factory.devicemanager.impl", "");

		System.setProperty("az.thread.pool.naming.enable", "false");
		System.setProperty("az.xmwebui.skip.ssl.hack", "true");
		System.setProperty("az.logging.save.debug", "false");
		System.setProperty("az.logging.keep.ui.history", "false");

		try {
			File configFile = new File(biglybtCustomDir, "BiglyBT_Start.config");
			FileWriter fw = new FileWriter(configFile, false);

			writeLine(fw, paramToCustom("Send Version Info", false));

			NetworkState networkState = BiglyBTApp.getNetworkState();
			bindToLocalHost = false;
			if (corePrefs.getPrefOnlyPluggedIn()
					&& !AndroidUtils.isPowerConnected(BiglyBTApp.getContext())) {
				bindToLocalHost = true;
				bindToLocalHostReasonID = R.string.core_noti_sleeping_battery;
			} else if (!corePrefs.getPrefAllowCellData()
					&& networkState.isOnlineMobile()) {
				bindToLocalHost = true;
				bindToLocalHostReasonID = R.string.core_noti_sleeping_oncellular;
			} else if (!networkState.isOnline()) {
				bindToLocalHost = true;
				bindToLocalHostReasonID = R.string.core_noti_sleeping;
			}

			// Remote Access config not shown in "Full Settings", so we don't need to
			// remember previous settings
			fw.write("Plugin.xmwebui.Port=long:" + RPC.LOCAL_BIGLYBT_PORT + "\n");
			CoreRemoteAccessPreferences raPrefs = corePrefs.getRemoteAccessPreferences();
			if (raPrefs.allowLANAccess) {
				writeLine(fw, paramToCustom(CoreParamKeys.SPARAM_XMWEBUI_BIND_IP, ""));
				writeLine(fw,
						paramToCustom(CoreParamKeys.SPARAM_XMWEBUI_PW_DISABLED_WHITELIST,
								DEFAULT_WEBUI_PW_LAN_ONLY));
			} else {
				writeLine(fw,
						paramToCustom(CoreParamKeys.SPARAM_XMWEBUI_BIND_IP, "127.0.0.1"));
				writeLine(fw,
						paramToCustom(CoreParamKeys.SPARAM_XMWEBUI_PW_DISABLED_WHITELIST,
								DEFAULT_WEBUI_PW_DISABLED_WHITELIST));
			}
			writeLine(fw, paramToCustom("Plugin.xmwebui.xmwebui.trace",
					CorePrefs.DEBUG_CORE && false));
			writeLine(fw,
					paramToCustom(CoreParamKeys.BPARAM_XMWEBUI_UPNP_ENABLE, false));
			writeLine(fw,
					paramToCustom(CoreParamKeys.BPARAM_XMWEBUI_PW_ENABLE, raPrefs.reqPW));
			writeLine(fw,
					paramToCustom(CoreParamKeys.SPARAM_XMWEBUI_USER, raPrefs.user));
			writeLine(fw,
					paramToCustom(CoreParamKeys.SPARAM_XMWEBUI_PW, raPrefs.getSHA1pw()));
			writeLine(fw,
					paramToCustom(CoreParamKeys.BPARAM_XMWEBUI_PAIRING_AUTO_AUTH, false));

			if (bindToLocalHost) {
				writeLine(fw, paramToCustom(Connection.BCFG_ENFORCE_BIND_IP, true));
				writeLine(fw,
						paramToCustom(Connection.BCFG_CHECK_BIND_IP_ON_START, true));
				writeLine(fw, paramToCustom(Connection.SCFG_BIND_IP, "127.0.0.1"));
				writeLine(fw, paramToCustom("PluginInfo.azextseed.enabled", false));
				writeLine(fw, paramToCustom("PluginInfo.mldht.enabled", false));
				if (CorePrefs.DEBUG_CORE) {
					logd("buildCustomFile: setting binding to localhost only");
				}
			} else {
				if (CorePrefs.DEBUG_CORE) {
					logd("buildCustomFile: clearing binding");
				}

				// Restore user-set bindings, (false, false, "", if no defaults) 
				Map<String, Object> mapCurrent = null;
				File fileBiglyBTConfig = FileUtil.newFile(biglybtCoreConfigRoot,
						"biglybt.config");
				if (fileBiglyBTConfig.exists()) {
					// Note: this will instantiate AndroidFileHandler
					FileInputStream fis = FileUtil.newFileInputStream(fileBiglyBTConfig);
					// using static method BDecoder.decode triggers StringInterner 
					// which triggers SimpleTimer (in a AEThread2) 
					// which triggers ThreadPool 
					// which calls ConfigurationManager.getInstance() 
					// which loads config..
					// which we don't want
					//mapCurrent = BDecoder.decode(new BufferedInputStream(fis));
					mapCurrent = new BDecoder().decodeStream(new BufferedInputStream(fis),
							false);
					fis.close();
				}

				writeLine(fw,
						paramToCustom(Connection.BCFG_ENFORCE_BIND_IP,
								MapUtils.getMapBoolean(mapCurrent,
										"android." + Connection.BCFG_ENFORCE_BIND_IP, false)));
				writeLine(fw,
						paramToCustom(Connection.BCFG_CHECK_BIND_IP_ON_START,
								MapUtils.getMapBoolean(mapCurrent,
										"android." + Connection.BCFG_CHECK_BIND_IP_ON_START,
										false)));
				writeLine(fw,
						paramToCustom(Connection.SCFG_BIND_IP, MapUtils.getMapString(
								mapCurrent, "android." + Connection.SCFG_BIND_IP, "")));
				writeLine(fw, paramToCustom("PluginInfo.azextseed.enabled", true));
				writeLine(fw, paramToCustom("PluginInfo.mldht.enabled", true));
			}
			fw.close();

			if (CorePrefs.DEBUG_CORE) {
				// Note: this will instantiate AndroidFileHandler
				logd("buildCustomFile: " + FileUtil.readFileAsString(configFile, -1));
			}
		} catch (IOException e) {
			loge("buildCustomFile: ", e);
		}

	}

	@SuppressLint("LogConditional")
	@Thunk
	void logd(String s) {
		Log.d(TAG, service.getLogHeader() + s);
	}

	@SuppressLint("LogConditional")
	private void loge(String s, Throwable t) {
		if (t == null) {
			Log.e(TAG, service.getLogHeader() + s);
		} else {
			Log.e(TAG, service.getLogHeader() + s, t);
		}
	}

	@NonNull
	private static String paramToCustom(@NonNull String key, byte[] val) {
		return key.replace(" ", "\\ ") + "=byte[]:"
				+ ByteFormatter.encodeString(val);
	}

	private static void writeLine(@NonNull Writer writer, @NonNull String s)
			throws IOException {
		writer.write(s);
		writer.write('\n');
	}

	@NonNull
	private static String paramToCustom(@NonNull String key, String s) {
		return key.replace(" ", "\\ ") + "=string:" + s;
	}

	@NonNull
	private static String paramToCustom(@NonNull String key, boolean b) {
		return key.replace(" ", "\\ ") + "=bool:" + (b ? "true" : "false");
	}

	private static void fixupLogger() {
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
		AEThread2.createAndStartDaemon("CoreInit", core::start);
	}

	@Thunk
	static void preinstallPlugins() {
		// Copy <assets>/plugins to <userpath>/plugins
		// (<userpath> is usually "<internal storage>/.biglybt")

		try {
			if (CorePrefs.DEBUG_CORE) {
				Log.d("Core", "unzip plugins.zip");
			}
			InputStream inputStream = BiglyBTApp.getContext().getAssets().open(
					"plugins.zip");
			File destDir = FileUtil.newFile(SystemProperties.getUserPath());
			FileUtils.unzip(inputStream, destDir, false);

			if (!UPNPMS_ENABLE) {
				removeDir(new File(new File(destDir, "plugins"), "azupnpav"));
			}
			if (CorePrefs.DEBUG_CORE) {
				Log.d("Core", "unzip plugins.zip done");
			}
		} catch (IOException e) {
			Log.e(TAG, "preinstallPlugins: ", e);
		}
	}

	private static void removeDir(@NonNull File dir) {
		try {
			if (!dir.isDirectory()) {
				return;
			}

			File[] files = dir.listFiles();
			if (files != null) {
				for (File file : files) {
					if (file.isDirectory()) {
						removeDir(file);
					} else {
						file.delete();
					}
				}
			}

			dir.delete();
		} catch (Exception ignore) {
		}
	}

/*
	private void checkUpdates() {
		PluginManager pm = core.getPluginManager();

		UpdateManager update_manager = pm.getDefaultPluginInterface().getUpdateManager();

		final UpdateCheckInstance checker = update_manager.createUpdateCheckInstance();

		checker.addListener(new UpdateCheckInstanceListener() {
			@Override
			public void cancelled(UpdateCheckInstance instance) {

			}

			@Override
			public void complete(UpdateCheckInstance instance) {
				Update[] updates = instance.getUpdates();

				for (Update update : updates) {

					System.out.println("Update available for '" + update.getName()
							+ "', new version = " + update.getNewVersion());

					String[] descs = update.getDescription();

					for (String desc : descs) {

						System.out.println("\t" + desc);
					}
				}

				checker.cancel();
			}
		});

		checker.start();
	}
	 */

	public Core getCore() {
		return (core);
	}

	public int getBindToLocalHostReasonID() {
		return bindToLocalHostReasonID;
	}

	public boolean isBindToLocalHost() {
		return bindToLocalHost;
	}

	public void corePrefRemAccessChanged(CoreRemoteAccessPreferences raPrefs) {
		if (CorePrefs.DEBUG_CORE) {
			logd("corePrefRemAccessChanged: " + raPrefs);
		}

		COConfigurationManager.setParameter(CoreParamKeys.SPARAM_XMWEBUI_BIND_IP,
				raPrefs.allowLANAccess ? "" : "127.0.0.1");
		COConfigurationManager.setParameter(
				CoreParamKeys.SPARAM_XMWEBUI_PW_DISABLED_WHITELIST,
				raPrefs.allowLANAccess ? DEFAULT_WEBUI_PW_LAN_ONLY
						: DEFAULT_WEBUI_PW_DISABLED_WHITELIST);
		COConfigurationManager.setParameter(CoreParamKeys.BPARAM_XMWEBUI_PW_ENABLE,
				raPrefs.reqPW);
		COConfigurationManager.setParameter(CoreParamKeys.SPARAM_XMWEBUI_USER,
				raPrefs.user);
		COConfigurationManager.setParameter(CoreParamKeys.SPARAM_XMWEBUI_PW,
				raPrefs.getSHA1pw());
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	public static class OurLoggerImpl
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
			if (alert == null) {
				return;
			}
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

		private static void log(LogIDs logID, int entryType, String text,
				Throwable err) {
			if (DEBUG_CORE_LOGGING_TYPES == null) {
				return;
			}
			if (logID == null || text == null || text.startsWith("[UPnP Core]")) {
				return;
			}
			boolean found = DEBUG_CORE_LOGGING_TYPES.length == 0 || err != null;
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
			if (alert == null) {
				return;
			}
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
