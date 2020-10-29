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
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
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
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.config.ConfigKeys.*;
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

import net.grandcentrix.tray.TrayPreferences;

import java.io.File;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
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

		// Params created by plugins will override any ConfigurationDefaults we set
		// So we explicitly write the value until the user manually changes it
		Map<String, Object> mapForcedDefaultIDs = new HashMap();
		mapForcedDefaultIDs.put(
				"Plugin.DHT Tracker.dhttracker.tracklimitedwhenonline", false);
		mapForcedDefaultIDs.put("Plugin.DHT Tracker.dhttracker.enable_alt", false);
		mapForcedDefaultIDs.put("Plugin.aercm.rcm.config.max_results", 100);
		mapForcedDefaultIDs.put("Plugin.mlDHT.backupOnly", true);

		CorePrefs corePrefs = CorePrefs.getInstance();
		preCoreInit(corePrefs, core_root, mapForcedDefaultIDs);

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

		//Core defaults must be set before initializing COConfigurationManager,
		// since COConfigurationManager.initialize uses some params
		@NonNull
		final ConfigurationDefaults def = ConfigurationDefaults.getInstance();

		def.addParameter(ConfigKeys.File.BCFG_SAVE_TORRENT_FILES, true);

		def.addParameter(StartupShutdown.BCFG_START_IN_LOW_RESOURCE_MODE, true);
		def.addParameter("DHT.protocol.version.min", 51);
		def.addParameter("network.tcp.enable_safe_selector_mode", false);

		def.addParameter(TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY,
				false);
		def.addParameter(
				TransferSpeedValidator.AUTO_UPLOAD_SEEDING_ENABLED_CONFIGKEY, false);
		def.addParameter(TransferSpeedValidator.UPLOAD_CONFIGKEY, 25);
		def.addParameter(TransferSpeedValidator.DOWNLOAD_CONFIGKEY, 0);

		def.addParameter("tagmanager.enable", TAG_MANAGER_ENABLE);
		def.addParameter("speedmanager.enable", SPEED_MANAGER_ENABLE);
		def.addParameter(Stats.BCFG_LONG_TERM_STATS_ENABLE, LONG_TERM_STATS_ENABLE);
		def.addParameter("rcm.overall.enabled", RCM_ENABLE);

		def.addParameter(IPFilter.BCFG_IP_FILTER_ENABLED, IP_FILTER_ENABLE);
		def.addParameter(IPFilter.BCFG_IP_FILTER_BANNING_PERSISTENT, false); // user has no way of removing bans atm so don't persist them for safety

		def.addParameter("dht.net.cvs_v4.enable", false);
		def.addParameter("dht.net.main_v6.enable", false);

		def.addParameter(Connection.BCFG_LISTEN_PORT_RANDOMIZE_ENABLE, true);
		def.addParameter(Connection.ICFG_NETWORK_TCP_READ_SELECT_TIME, 500);
		def.addParameter(Connection.ICFG_NETWORK_TCP_READ_SELECT_MIN_TIME, 500);
		def.addParameter(Connection.ICFG_NETWORK_TCP_WRITE_SELECT_TIME, 500);
		def.addParameter(Connection.ICFG_NETWORK_TCP_WRITE_SELECT_MIN_TIME, 500);
		def.addParameter("network.tcp.connect.select.time", 500);
		def.addParameter("network.tcp.connect.select.min.time", 500);

		def.addParameter("network.udp.poll.time", 100);

		def.addParameter("network.utp.poll.time", 100);

		def.addParameter("network.control.read.idle.time", 100);
		def.addParameter("network.control.write.idle.time", 100);

		def.addParameter(ConfigKeys.File.BCFG_DISKMANAGER_PERF_CACHE_ENABLE, true);
		def.addParameter(ConfigKeys.File.ICFG_DISKMANAGER_PERF_CACHE_SIZE, 2);
		def.addParameter(ConfigKeys.File.BCFG_DISKMANAGER_PERF_CACHE_FLUSHPIECES,
				false);
		def.addParameter(ConfigKeys.File.BCFG_DISKMANAGER_PERF_CACHE_ENABLE_READ,
				false);

		def.addParameter("diskmanager.perf.read.maxthreads", 2);
		def.addParameter(ConfigKeys.File.ICFG_DISKMANAGER_PERF_READ_MAXMB, 2);
		def.addParameter("diskmanager.perf.write.maxthreads", 2);
		def.addParameter(ConfigKeys.File.ICFG_DISKMANAGER_PERF_WRITE_MAXMB, 2);

		// Hash Checking Strategy: CPU/Disk Friendly
		def.addParameter(ConfigKeys.File.ICFG_DISKMANAGER_HASHCHECKING_STRATEGY, 0);

		def.addParameter("peermanager.schedule.time", 500);

		def.addParameter(Tracker.BCFG_TRACKER_CLIENT_SCRAPE_STOPPED_ENABLE, false);
		def.addParameter(Tracker.ICFG_TRACKER_CLIENT_CLOSEDOWN_TIMEOUT, 5);
		def.addParameter(Tracker.ICFG_TRACKER_CLIENT_NUMWANT_LIMIT, 10);

		// Having IgnoreFiles slows down torrent move
		def.addParameter(ConfigKeys.File.SCFG_FILE_TORRENT_IGNORE_FILES, "");
		def.addParameter(ConfigKeys.File.BCFG_DISABLE_SAVE_INTERIM_DOWNLOAD_STATE,
				true);

		// Adding extensions to incomplete files might allow us to skip OS media scan
		// However, seeing ".bbt!" on files is bad UI and needs to be fixed up first
//		coreDefaults.addParameter(ConfigKeys.File.BCFG_RENAME_INCOMPLETE_FILES, true);
//		coreDefaults.addParameter(ConfigKeys.File.SCFG_RENAME_INCOMPLETE_FILES_EXTENSION, ".bbt!");

		// Moving unwanted files that
		// are required for full piece downloading will hopefully result in less
		// people complaining that we are "downloading files I didn't ask for"
		// TODO: Disabled until we have a UI that better handles 
//		def.addParameter(ConfigKeys.File.BCFG_ENABLE_SUBFOLDER_FOR_DND_FILES, true);
//		def.addParameter(ConfigKeys.File.SCFG_SUBFOLDER_FOR_DND_FILES, ".dnd_bbt!");
//		def.addParameter(ConfigKeys.File.BCFG_USE_INCOMPLETE_FILE_PREFIX, true);

		// CPU Intensive and we already check completed pieces as we download
		def.addParameter(ConfigKeys.File.BCFG_CHECK_PIECES_ON_COMPLETION, false);
		def.addParameter(ConfigKeys.File.ICFG_FILE_SAVE_PEERS_MAX, 50);

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
			} else if (o instanceof Long) {
				COConfigurationManager.setParameter(cachedParamName, ((Long) o) == 1);
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

		// When user sets configs that we overwrite on reboot, mark a flag so
		// we don't set that config on reboot.
		COConfigurationManager.addParameterListener(
				mapForcedDefaultIDs.keySet().toArray(new String[0]), parameterName -> {
					TrayPreferences prefs = BiglyBTApp.getAppPreferences().getPreferences();
					prefs.put("android.skipset." + parameterName, true);
				});

		//COConfigurationManager.resetToDefaults();
		//COConfigurationManager.setParameter("Plugin.aercm.rcm.ui.enable", false);

		if (CorePrefs.DEBUG_CORE) {
			// in release mode, this method and OurLoggerImpl will be removed by R8 Shrinking (Proguard)
			fixupLogger();
		}

		COConfigurationManager.setParameter("ui", UI_NAME);

		FileUtil.newFile(COConfigurationManager.getStringParameter(
				ConfigKeys.File.SCFG_DEFAULT_SAVE_PATH)).mkdirs();
		FileUtil.newFile(COConfigurationManager.getStringParameter(
				ConfigKeys.File.SCFG_GENERAL_DEFAULT_TORRENT_DIRECTORY)).mkdirs();

		boolean ENABLE_LOGGING = false;

		COConfigurationManager.setParameter(Logging.BCFG_LOGGER_ENABLED,
				ENABLE_LOGGING);

		COConfigurationManager.setParameter(Logging.BCFG_LOGGING_ENABLE,
				ENABLE_LOGGING);
		COConfigurationManager.setParameter(Logging.SCFG_LOGGING_DIR, "C:\\temp");
		COConfigurationManager.setParameter("Logger.DebugFiles.Enabled", false);

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
			@NonNull File biglybtCoreConfigRoot,
			Map<String, Object> mapForcedDefaultIDs) {
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
			writeLine(fw,
					paramToCustom("Plugin.xmwebui.Port", RPC.LOCAL_BIGLYBT_PORT));
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

				TrayPreferences prefs = BiglyBTApp.getAppPreferences().getPreferences();
				for (String key : mapForcedDefaultIDs.keySet()) {
					boolean skipSet = prefs.getBoolean("android.skipset." + key, false);
					if (skipSet) {
						continue;
					}
					Object val = mapForcedDefaultIDs.get(key);
					if (val instanceof Boolean) {
						writeLine(fw, paramToCustom(key, (Boolean) val));
					} else if (val instanceof String) {
						writeLine(fw, paramToCustom(key, (String) val));
					} else if (val instanceof Long || val instanceof Integer) {
						writeLine(fw, paramToCustom(key, ((Number) val).longValue()));
					}

				}
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

	@NonNull
	private static String paramToCustom(@NonNull String key, long l) {
		return key.replace(" ", "\\ ") + "=long:" + l;
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
			File destDir = FileUtil.newFile(SystemProperties.getUserPath());

			// Unzip plugins.zip and/or copy "plugins/*"
			// Note: f-droid doesn't allow zip files
			AssetManager assets = BiglyBTApp.getContext().getAssets();
			String[] list = assets.list("");
			Arrays.sort(list);
			if (Arrays.binarySearch(list, "plugins.zip") >= 0) {
				InputStream inputStream = assets.open("plugins.zip");
				FileUtils.unzip(inputStream, destDir, false);
			}

			// for clean copy
			//FileUtil.recursiveDeleteNoCheck(new File(destDir, "plugins"));
			copyAssetDir(assets, "plugins", destDir, new byte[2048], true);

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

	private static boolean DEBUG_COPY_ASSET = false;

	private static void copyAssetDir(@NonNull AssetManager assets,
			String assetDir, File destDir, byte[] buf, boolean skipIfSame)
			throws IOException {
		String[] list = assets.list(assetDir);
		for (String file : list) {
			String assetFile = assetDir + "/" + file;
			String[] sub_files = assets.list(assetFile);
			File destFile = new File(destDir, assetFile);
			if (sub_files.length == 0) {
				if (skipIfSame && destFile.exists()) {
					long destLength = destFile.length();

					long length = 0;
					char d = ' ';
					try {
						AssetFileDescriptor fd = assets.openFd(assetFile);
						length = fd.getLength();
						fd.close();
					} catch (FileNotFoundException e) {
						if (DEBUG_COPY_ASSET) {
							d = '*';
						}
						// compressed
						InputStream in = assets.open(assetFile);
						length = in.available();
						// available() might be an under count, but in my experience is 
						// correct for asset files. I suppose there's a tiny chance that 
						// available() will be an under count AND match the existing file 
						// length.
						if (length != destLength) {
							if (DEBUG_COPY_ASSET) {
								d = '!';
							}
							int size;
							while (length <= destLength && (size = in.read(buf)) > 0) {
								length += size;
							}
							// note: length won't be accurate if over destLength
						}
						in.close();
					}
					if (length > 0 && length == destLength) {
						if (DEBUG_COPY_ASSET) {
							Log.d(TAG,
									"copyAssetDir: skip " + d + assetFile + "(" + length + ")");
						}
						continue;
					}

					if (DEBUG_COPY_ASSET) {
						Log.d(TAG, "copyAssetDir: copy " + d + assetFile + " (" + length
								+ " <> " + destFile.length() + ")");
					}
				}

				InputStream in = assets.open(assetFile);
				OutputStream out = new FileOutputStream(destFile);

				int len;
				while ((len = in.read(buf)) > 0)
					out.write(buf, 0, len);
				in.close();
				out.close();
			} else {
				if (!destFile.exists()) {
					destFile.mkdirs();
				}
				copyAssetDir(assets, assetFile, destDir, buf, skipIfSame);
			}
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
