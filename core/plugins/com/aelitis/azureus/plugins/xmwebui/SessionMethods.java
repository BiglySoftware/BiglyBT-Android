/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.aelitis.azureus.plugins.xmwebui;

import java.io.File;
import java.io.IOException;
import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.config.impl.TransferSpeedValidator;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.history.DownloadHistoryManager;
import com.biglybt.core.ipfilter.IpFilter;
import com.biglybt.core.ipfilter.IpFilterManagerFactory;
import com.biglybt.core.ipfilter.impl.IpFilterAutoLoaderImpl;
import com.biglybt.core.stats.transfer.OverallStats;
import com.biglybt.core.stats.transfer.StatsFactory;
import com.biglybt.core.tag.*;
import com.biglybt.core.util.*;
import com.biglybt.core.versioncheck.VersionCheckClient;
import com.biglybt.plugin.dht.DHTPlugin;
import com.biglybt.util.MapUtils;

import com.biglybt.pif.PluginConfig;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.tracker.web.TrackerWebPageRequest;

import static com.aelitis.azureus.plugins.xmwebui.StaticUtils.canAdd;
import static com.aelitis.azureus.plugins.xmwebui.TransmissionVars.*;

public class SessionMethods
{
	private static long lastVerserverCheck;

	private static final List<String> listSupports;

	static {
		// TODO: have a new method, such as "rpc-supports-list" which returns
		//       a full list of methods (map), fields (map in methods), features (map)
		// @formatter:off
		listSupports = Arrays.asList(
			"rpc:receive-gzip",
			"field:files-hc",
			"method:tags-add",
			"method:tags-set",
			"method:tags-get-list",
			"field:torrent-set-name",
			"method:subscription-get",
			"method:subscription-add",
			"method:subscription-remove",
			"method:subscription-set",
			"method:vuze-plugin-get-list",
			"method:tags-lookup-start",
			"method:tags-lookup-get-results",
			"method:vuze-search-start",
			"method:vuze-search-get-results",
			"method:torrent-rename-path",
			"method:i18n-get-text",
			"torrent-add:torrent-duplicate",
			"field:session:active-queue-size",
			"field:torrent-get:peer-fields",
			"field:torrent-get:eta",
			"field:torrent-get:isForced",
			"field:torrent-get:files:eta",
			"field:torrent:sequential", // -set and -get and -add
			"method:config-get",
			"method:config-set", 
			"method:config-action", 
			"field:torrent-set:files-dnd",
			"field:torrent-set:files-delete"
		);
		// @formatter:on
	}

	public static void method_Session_Get(XMWebUIPlugin plugin,
			PluginInterface plugin_interface, TrackerWebPageRequest request,
			Map<String, Object> args, Map<String, Object> result) {
		//noinspection unchecked
		List<String> fields = (List<String>) args.get(ARG_FIELDS);
		boolean all = fields == null || fields.size() == 0;
		if (!all) {
			// sort so we can't use Collections.binarySearch
			Collections.sort(fields);
		}

		PluginConfig pc = plugin_interface.getPluginconfig();

		String save_dir = pc.getCoreStringParameter(
				PluginConfig.CORE_PARAM_STRING_DEFAULT_SAVE_PATH);
		int tcp_port = pc.getCoreIntParameter(
				PluginConfig.CORE_PARAM_INT_INCOMING_TCP_PORT);
		//int		up_limit_normal 	= pc.getCoreIntParameter( PluginConfig.CORE_PARAM_INT_MAX_UPLOAD_SPEED_KBYTES_PER_SEC );
		//int		up_limit_seedingOnly 	= pc.getCoreIntParameter( PluginConfig.CORE_PARAM_INT_MAX_UPLOAD_SPEED_SEEDING_KBYTES_PER_SEC );
		int up_limit = pc.getUnsafeIntParameter(
				TransferSpeedValidator.getActiveUploadParameter(
						CoreFactory.getSingleton().getGlobalManager()));
		int down_limit = pc.getCoreIntParameter(
				PluginConfig.CORE_PARAM_INT_MAX_DOWNLOAD_SPEED_KBYTES_PER_SEC);
		int glob_con = pc.getCoreIntParameter(
				PluginConfig.CORE_PARAM_INT_MAX_CONNECTIONS_GLOBAL);
		int tor_con = pc.getCoreIntParameter(
				PluginConfig.CORE_PARAM_INT_MAX_CONNECTIONS_PER_TORRENT);

		//boolean auto_speed_on = pc.getCoreBooleanParameter( PluginConfig.CORE_PARAM_BOOLEAN_AUTO_SPEED_ON ) ||
		//						pc.getCoreBooleanParameter( PluginConfig.CORE_PARAM_BOOLEAN_AUTO_SPEED_SEEDING_ON );

		boolean require_enc = COConfigurationManager.getBooleanParameter(
				"network.transport.encrypted.require");

		float stop_ratio = COConfigurationManager.getFloatParameter("Stop Ratio");

		IpFilter ipFilter = IpFilterManagerFactory.getSingleton().getIPFilter();
		String filter_url = COConfigurationManager.getStringParameter(
				"Ip Filter Autoload File", "");
		final PluginInterface dht_pi = plugin_interface.getPluginManager().getPluginInterfaceByClass(
				DHTPlugin.class);
		DHTPlugin dht = dht_pi == null ? null : (DHTPlugin) dht_pi.getPlugin();

		PluginInterface piUTP = plugin_interface.getPluginManager().getPluginInterfaceByID(
				"azutp");
		boolean hasUTP = piUTP != null && piUTP.getPluginState().isOperational()
				&& piUTP.getPluginconfig().getPluginBooleanParameter("utp.enabled",
						true);

		if (canAdd(TR_PREFS_KEY_BLOCKLIST_ENABLED, fields, all)) {
			result.put(TR_PREFS_KEY_BLOCKLIST_ENABLED, ipFilter.isEnabled());
		}
		if (canAdd(TR_PREFS_KEY_BLOCKLIST_URL, fields, all)) {
			result.put(TR_PREFS_KEY_BLOCKLIST_URL, filter_url);
		}
		// RPC v5, but no constant!
		if (canAdd(TR_PREFS_KEY_BLOCKLIST_SIZE, fields, all)) {
			// number     number of rules in the blocklist
			result.put(TR_PREFS_KEY_BLOCKLIST_SIZE, ipFilter.getNbRanges());
		}
		if (canAdd(TR_PREFS_KEY_MAX_CACHE_SIZE_MB, fields, all)) {
			result.put(TR_PREFS_KEY_MAX_CACHE_SIZE_MB, 0); // TODO
		}
		if (canAdd(TR_PREFS_KEY_DHT_ENABLED, fields, all)) {
			result.put(TR_PREFS_KEY_DHT_ENABLED,
					dht != null && (dht.isInitialising() || dht.isEnabled()));
		}
		if (canAdd(TR_PREFS_KEY_UTP_ENABLED, fields, all)) {
			result.put(TR_PREFS_KEY_UTP_ENABLED, hasUTP);
		}
		if (canAdd(TR_PREFS_KEY_LPD_ENABLED, fields, all)) {
			result.put(TR_PREFS_KEY_LPD_ENABLED, false);
		}
		if (canAdd(TR_PREFS_KEY_DOWNLOAD_DIR, fields, all)) {
			result.put(TR_PREFS_KEY_DOWNLOAD_DIR, save_dir);
		}
		// RPC 12 to 14
		if (canAdd(TR_PREFS_KEY_DOWNLOAD_DIR_FREE_SPACE, fields, all)) {
			long space = save_dir == null ? -1
					: FileUtil.getUsableSpace(FileUtil.newFile(save_dir));
			result.put(TR_PREFS_KEY_DOWNLOAD_DIR_FREE_SPACE, space);
		}

		if (canAdd(TR_PREFS_KEY_DSPEED_KBps, fields, all)) {
			result.put(TR_PREFS_KEY_DSPEED_KBps,
					down_limit > 0 ? down_limit : pc.getUnsafeIntParameter(
							"config.ui.speed.partitions.manual.download.last"));
		}
		if (canAdd(TR_PREFS_KEY_DSPEED_ENABLED, fields, all)) {
			result.put(TR_PREFS_KEY_DSPEED_ENABLED, down_limit != 0);
		}
		if (canAdd(TR_PREFS_KEY_ENCRYPTION, fields, all)) {
			// string     "required", "preferred", "tolerated"
			result.put(TR_PREFS_KEY_ENCRYPTION,
					require_enc ? "required" : "preferred");
		}
		if (canAdd(TR_PREFS_KEY_IDLE_LIMIT, fields, all)) {
			result.put(TR_PREFS_KEY_IDLE_LIMIT, 30); //TODO
		}
		if (canAdd(TR_PREFS_KEY_IDLE_LIMIT_ENABLED, fields, all)) {
			result.put(TR_PREFS_KEY_IDLE_LIMIT_ENABLED, false);//TODO
		}
		if (canAdd(TR_PREFS_KEY_INCOMPLETE_DIR, fields, all)) {
			result.put(TR_PREFS_KEY_INCOMPLETE_DIR, save_dir);
		}
		if (canAdd(TR_PREFS_KEY_INCOMPLETE_DIR_ENABLED, fields, all)) {
			result.put(TR_PREFS_KEY_INCOMPLETE_DIR_ENABLED, false);//TODO
		}
		//result.put(TR_PREFS_KEY_MSGLEVEL, TR_MSG_INF ); // Not in Spec

		final int maxDownloads = pc.getCoreIntParameter(
				PluginConfig.CORE_PARAM_INT_MAX_DOWNLOADS);
		if (canAdd(TR_PREFS_KEY_DOWNLOAD_QUEUE_SIZE, fields, all)) {
			result.put(TR_PREFS_KEY_DOWNLOAD_QUEUE_SIZE, maxDownloads);
		}
		if (canAdd(TR_PREFS_KEY_DOWNLOAD_QUEUE_ENABLED, fields, all)) {
			result.put(TR_PREFS_KEY_DOWNLOAD_QUEUE_ENABLED, maxDownloads > 0);
		}
		if (canAdd(TR_PREFS_KEY_PEER_LIMIT_GLOBAL, fields, all)) {
			result.put(TR_PREFS_KEY_PEER_LIMIT_GLOBAL, glob_con);
		}
		if (canAdd(TR_PREFS_KEY_PEER_LIMIT_TORRENT, fields, all)) {
			result.put(TR_PREFS_KEY_PEER_LIMIT_TORRENT, tor_con);
		}
		if (canAdd(TR_PREFS_KEY_PEER_PORT, fields, all)) {
			result.put(TR_PREFS_KEY_PEER_PORT, tcp_port);
		}
		if (canAdd(TR_PREFS_KEY_PEER_PORT_RANDOM_ON_START, fields, all)) {
			result.put(TR_PREFS_KEY_PEER_PORT_RANDOM_ON_START,
					pc.getUnsafeBooleanParameter("Listen.Port.Randomize.Enable"));
		}
		//result.put(TR_PREFS_KEY_PEER_PORT_RANDOM_LOW, 49152 ); // Not in Spec
		//result.put(TR_PREFS_KEY_PEER_PORT_RANDOM_HIGH, 65535 ); // Not in Spec
		//result.put(TR_PREFS_KEY_PEER_SOCKET_TOS, TR_DEFAULT_PEER_SOCKET_TOS_STR ); //TODO
		if (canAdd(TR_PREFS_KEY_PEX_ENABLED, fields, all)) {
			result.put(TR_PREFS_KEY_PEX_ENABLED, true); //TODO
		}
		if (canAdd(TR_PREFS_KEY_PORT_FORWARDING, fields, all)) {
			result.put(TR_PREFS_KEY_PORT_FORWARDING, false); //TODO
		}
		//result.put(TR_PREFS_KEY_PREALLOCATION, TR_PREALLOCATE_SPARSE ); //TODO
		//result.put(TR_PREFS_KEY_PREFETCH_ENABLED, DEFAULT_PREFETCH_ENABLED ); //TODO
		if (canAdd(TR_PREFS_KEY_QUEUE_STALLED_ENABLED, fields, all)) {
			result.put(TR_PREFS_KEY_QUEUE_STALLED_ENABLED, true); //TODO
		}
		if (canAdd(TR_PREFS_KEY_QUEUE_STALLED_MINUTES, fields, all)) {
			result.put(TR_PREFS_KEY_QUEUE_STALLED_MINUTES, 30); //TODO
		}
		if (canAdd(TR_PREFS_KEY_RATIO, fields, all)) {
			result.put(TR_PREFS_KEY_RATIO, 2.0); //TODO (wrong key?)
		}
		if (canAdd(TR_PREFS_KEY_RATIO_ENABLED, fields, all)) {
			result.put(TR_PREFS_KEY_RATIO_ENABLED, false); //TODO (wrong key?)
		}
		if (canAdd(TR_PREFS_KEY_RENAME_PARTIAL_FILES, fields, all)) {
			result.put(TR_PREFS_KEY_RENAME_PARTIAL_FILES, true); //TODO
		}
		//result.put(TR_PREFS_KEY_RPC_AUTH_REQUIRED, false ); // Not in Spec
		//String bindIP = pc.getPluginStringParameter(WebPlugin.CONFIG_BIND_IP);
		//result.put(TR_PREFS_KEY_RPC_BIND_ADDRESS, bindIP == null || bindIP.length() == 0 ? "0.0.0.0" : bindIP );
		//result.put(TR_PREFS_KEY_RPC_ENABLED, false ); // Not in Spec
		//result.put(TR_PREFS_KEY_RPC_PASSWORD, "" ); // Not in Spec
		//result.put(TR_PREFS_KEY_RPC_USERNAME, "" ); // Not in Spec
		//result.put(TR_PREFS_KEY_RPC_WHITELIST, TR_DEFAULT_RPC_WHITELIST ); // Not in Spec
		//result.put(TR_PREFS_KEY_RPC_WHITELIST_ENABLED, true ); // Not in Spec
		//result.put(TR_PREFS_KEY_RPC_PORT, atoi( TR_DEFAULT_RPC_PORT_STR ) ); // Not in Spec
		//result.put(TR_PREFS_KEY_RPC_URL, TR_DEFAULT_RPC_URL_STR ); // Not in Spec
		//result.put(TR_PREFS_KEY_SCRAPE_PAUSED_TORRENTS, true ); // Not in Spec
		if (canAdd(TR_PREFS_KEY_SCRIPT_TORRENT_DONE_FILENAME, fields, all)) {
			result.put(TR_PREFS_KEY_SCRIPT_TORRENT_DONE_FILENAME, ""); //TODO
		}
		if (canAdd(TR_PREFS_KEY_SCRIPT_TORRENT_DONE_ENABLED, fields, all)) {
			result.put(TR_PREFS_KEY_SCRIPT_TORRENT_DONE_ENABLED, false); //TODO
		}

		final int maxActive = pc.getCoreIntParameter(
				PluginConfig.CORE_PARAM_INT_MAX_ACTIVE);
		final int maxSeedsSortOf = maxDownloads - maxActive;
		if (canAdd(TR_PREFS_KEY_SEED_QUEUE_SIZE, fields, all)) {
			result.put(TR_PREFS_KEY_SEED_QUEUE_SIZE, maxSeedsSortOf);
		}
		if (canAdd(TR_PREFS_KEY_SEED_QUEUE_ENABLED, fields, all)) {
			result.put(TR_PREFS_KEY_SEED_QUEUE_ENABLED, true);
		}
		if (canAdd(TR_PREFS_KEY_ACTIVE_QUEUE_SIZE, fields, all)) {
			result.put(TR_PREFS_KEY_ACTIVE_QUEUE_SIZE, maxActive);
		}

		if (canAdd(TR_PREFS_KEY_ALT_SPEED_ENABLED, fields, all)) {
			result.put(TR_PREFS_KEY_ALT_SPEED_ENABLED, false); //TODO
		}
		if (canAdd(TR_PREFS_KEY_ALT_SPEED_UP_KBps, fields, all)) {
			result.put(TR_PREFS_KEY_ALT_SPEED_UP_KBps, 50); //TODO
		}
		if (canAdd(TR_PREFS_KEY_ALT_SPEED_DOWN_KBps, fields, all)) {
			result.put(TR_PREFS_KEY_ALT_SPEED_DOWN_KBps, 50); //TODO
		}
		if (canAdd(TR_PREFS_KEY_ALT_SPEED_TIME_BEGIN, fields, all)) {
			result.put(TR_PREFS_KEY_ALT_SPEED_TIME_BEGIN, 540); /* 9am */ //TODO
		}
		if (canAdd(TR_PREFS_KEY_ALT_SPEED_TIME_ENABLED, fields, all)) {
			result.put(TR_PREFS_KEY_ALT_SPEED_TIME_ENABLED, false); //TODO
		}
		if (canAdd(TR_PREFS_KEY_ALT_SPEED_TIME_END, fields, all)) {
			result.put(TR_PREFS_KEY_ALT_SPEED_TIME_END, 1020); /* 5pm */ //TODO
		}
		if (canAdd(TR_PREFS_KEY_ALT_SPEED_TIME_DAY, fields, all)) {
			result.put(TR_PREFS_KEY_ALT_SPEED_TIME_DAY, TR_SCHED_ALL); //TODO
		}
		if (canAdd(TR_PREFS_KEY_USPEED_KBps, fields, all)) {
			result.put(TR_PREFS_KEY_USPEED_KBps,
					up_limit > 0 ? up_limit : pc.getUnsafeIntParameter(
							"config.ui.speed.partitions.manual.upload.last"));
		}
		if (canAdd(TR_PREFS_KEY_USPEED_ENABLED, fields, all)) {
			result.put(TR_PREFS_KEY_USPEED_ENABLED, up_limit != 0);
		}
		//result.put(TR_PREFS_KEY_UMASK, 022 ); // Not in Spec
		if (canAdd(TR_PREFS_KEY_UPLOAD_SLOTS_PER_TORRENT, fields, all)) {
			result.put(TR_PREFS_KEY_UPLOAD_SLOTS_PER_TORRENT, 14); //TODO
		}
		//result.put(TR_PREFS_KEY_BIND_ADDRESS_IPV4, TR_DEFAULT_BIND_ADDRESS_IPV4 ); //TODO
		//result.put(TR_PREFS_KEY_BIND_ADDRESS_IPV6, TR_DEFAULT_BIND_ADDRESS_IPV6 ); //TODO

		if (canAdd("config-dir", fields, all)) {
			result.put("config-dir", ""); //TODO
		}

		boolean startStopped = COConfigurationManager.getBooleanParameter(
				"Default Start Torrents Stopped");
		if (canAdd(TR_PREFS_KEY_START, fields, all)) {
			result.put(TR_PREFS_KEY_START, !startStopped); //TODO
		}

		boolean renamePartial = COConfigurationManager.getBooleanParameter(
				"Rename Incomplete Files");
		if (canAdd(TR_PREFS_KEY_RENAME_PARTIAL_FILES, fields, all)) {
			result.put(TR_PREFS_KEY_RENAME_PARTIAL_FILES, renamePartial);
		}

		if (canAdd(TR_PREFS_KEY_TRASH_ORIGINAL, fields, all)) {
			result.put(TR_PREFS_KEY_TRASH_ORIGINAL, false); //TODO
		}

		// "port" was used until RPC v5  (now "peer-port")
		if (canAdd("port", fields, all)) {
			result.put("port", (long) tcp_port); // number     port number
		}
		if (canAdd("rpc-version", fields, all)) {
			result.put("rpc-version", 16L); // number     the current RPC API version
		}
		if (canAdd("rpc-version-minimum", fields, all)) {
			result.put("rpc-version-minimum", 6L); // number     the minimum RPC API version supported
		}
		if (canAdd("seedRatioLimit", fields, all)) {
			result.put("seedRatioLimit", (double) stop_ratio); // double     the default seed ratio for torrents to use
		}
		if (canAdd("seedRatioLimited", fields, all)) {
			result.put("seedRatioLimited", stop_ratio > 0); // boolean    true if seedRatioLimit is honored by default
		}
		if (canAdd("session-id", fields, all)) {
			result.put("session-id", plugin.getSessionID(request));
		}

		if (canAdd("version", fields, all)) {
			String version = plugin_interface.getPluginVersion();
			result.put("version", version == null ? "Source" : version); // string
		}
		if (canAdd("az-rpc-version", fields, all)) {
			result.put("az-rpc-version", XMWebUIPlugin.VUZE_RPC_VERSION);
		}
		if (canAdd("az-version", fields, all)) {
			result.put("az-version", "5.7.5.0"); // string
		}
		if (canAdd("biglybt-version", fields, all)) {
			result.put("biglybt-version", Constants.getCurrentVersion()); // string
		}
		if (canAdd("rpc-i2p-address", fields, all)) {
			result.put("rpc-i2p-address",
					pc.getPluginStringParameter("webui.i2p_dest"));
		}
		if (canAdd("rpc-tor-address", fields, all)) {
			result.put("rpc-tor-address",
					pc.getPluginStringParameter("webui.tor_dest"));
		}
		if (canAdd("az-content-port", fields, all)) {
			result.put("az-content-port", getMediaServerActivePort(plugin_interface));
		}

		if (canAdd("rpc-supports", fields, all)) {

			List<String> list = new ArrayList<>(listSupports);
			synchronized (plugin.json_server_method_lock) {
				for (String key : plugin.json_server_methods.keySet()) {
					list.add("method:" + key);
				}
			}

			result.put("rpc-supports", list);
		}

		if (canAdd("az-message", fields, all)) {
			if (lastVerserverCheck == 0
					|| SystemTime.getCurrentTime() - lastVerserverCheck > 864000L) {
				lastVerserverCheck = SystemTime.getCurrentTime();
				Map decoded = VersionCheckClient.getSingleton().getVersionCheckInfo(
						"xmw");
				String userMessage = getUserMessage(plugin, decoded);
				if (userMessage != null) {
					result.put("az-message", userMessage);
				}
			}
		}
		//result.put("az-message", "This is a test message with a  <A HREF=\"http://www.biglybt.com\">Link</a>");

	}

	public static void method_Session_Set(XMWebUIPlugin plugin,
			PluginInterface plugin_interface, Map<String, Object> args)
			throws IOException {

		plugin.checkUpdatePermissions();

		PluginConfig pc = plugin_interface.getPluginconfig();
/*
 "download-queue-size"            | number     | max number of torrents to download at once (see download-queue-enabled)
 "download-queue-enabled"         | boolean    | if true, limit how many torrents can be downloaded at once
 "dht-enabled"                    | boolean    | true means allow dht in public torrents
 "encryption"                     | string     | "required", "preferred", "tolerated"
 "idle-seeding-limit"             | number     | torrents we're seeding will be stopped if they're idle for this long
 "idle-seeding-limit-enabled"     | boolean    | true if the seeding inactivity limit is honored by default
 "incomplete-dir"                 | string     | path for incomplete torrents, when enabled
 "incomplete-dir-enabled"         | boolean    | true means keep torrents in incomplete-dir until done
 "lpd-enabled"                    | boolean    | true means allow Local Peer Discovery in public torrents
 "peer-limit-global"              | number     | maximum global number of peers
 "peer-limit-per-torrent"         | number     | maximum global number of peers
 "pex-enabled"                    | boolean    | true means allow pex in public torrents
 "peer-port"                      | number     | port number
 "peer-port-random-on-start"      | boolean    | true means pick a random peer port on launch
 "port-forwarding-enabled"        | boolean    | true means enabled
 "queue-stalled-enabled"          | boolean    | whether or not to consider idle torrents as stalled
 "queue-stalled-minutes"          | number     | torrents that are idle for N minuets aren't counted toward seed-queue-size or download-queue-size
 "rename-partial-files"           | boolean    | true means append ".part" to incomplete files
 "script-torrent-done-filename"   | string     | filename of the script to run
 "script-torrent-done-enabled"    | boolean    | whether or not to call the "done" script
 "seedRatioLimit"                 | double     | the default seed ratio for torrents to use
 "seedRatioLimited"               | boolean    | true if seedRatioLimit is honored by default
 "seed-queue-size"                | number     | max number of torrents to uploaded at once (see seed-queue-enabled)
 "seed-queue-enabled"             | boolean    | if true, limit how many torrents can be uploaded at once
 "speed-limit-down"               | number     | max global download speed (KBps)
 "speed-limit-down-enabled"       | boolean    | true means enabled
 "speed-limit-up"                 | number     | max global upload speed (KBps)
 "speed-limit-up-enabled"         | boolean    | true means enabled
 "start-added-torrents"           | boolean    | true means added torrents will be started right away
 "trash-original-torrent-files"   | boolean    | true means the .torrent file of added torrents will be deleted
 "utp-enabled"                    | boolean    | true means allow utp
 */
		for (Map.Entry<String, Object> arg : args.entrySet()) {

			String key = arg.getKey();
			Object val = arg.getValue();
			try {
				if (key.startsWith("alt-speed")) {
					// TODO:
					// "alt-speed-down"                 | number     | max global download speed (KBps)
					// "alt-speed-enabled"              | boolean    | true means use the alt speeds
					// "alt-speed-time-begin"           | number     | when to turn on alt speeds (units: minutes after midnight)
					// "alt-speed-time-enabled"         | boolean    | true means the scheduled on/off times are used
					// "alt-speed-time-end"             | number     | when to turn off alt speeds (units: same)
					// "alt-speed-time-day"             | number     | what day(s) to turn on alt speeds (look at tr_sched_day)
					// "alt-speed-up"                   | number     | max global upload speed (KBps)

				} else if (key.equals(TR_PREFS_KEY_BLOCKLIST_URL)) {
					// "blocklist-url"                  | string     | location of the blocklist to use for "blocklist-update"
					IpFilter ipFilter = IpFilterManagerFactory.getSingleton().getIPFilter();
					COConfigurationManager.setParameter("Ip Filter Autoload File",
							(String) val);
					COConfigurationManager.setParameter(
							IpFilterAutoLoaderImpl.CFG_AUTOLOAD_LAST, 0);
					try {
						ipFilter.reload();
					} catch (Exception e) {
						e.printStackTrace();
					}

				} else if (key.equals(TR_PREFS_KEY_BLOCKLIST_ENABLED)) {
					// "blocklist-enabled"              | boolean    | true means enabled
					plugin_interface.getIPFilter().setEnabled(
							StaticUtils.getBoolean(val));

				} else if (key.equals(TR_PREFS_KEY_MAX_CACHE_SIZE_MB)) {
					// "cache-size-mb"                  | number     | maximum size of the disk cache (MB)
					// umm.. not needed

				} else if (key.equals(TR_PREFS_KEY_DOWNLOAD_DIR)) {
					// "download-dir"                   | string     | default path to download torrents

					String dir = (String) val;

					String save_dir = pc.getCoreStringParameter(
							PluginConfig.CORE_PARAM_STRING_DEFAULT_SAVE_PATH);
					if (!save_dir.equals(dir)) {

						pc.setCoreStringParameter(
								PluginConfig.CORE_PARAM_STRING_DEFAULT_SAVE_PATH, dir);
					}

				} else if (key.equals(TR_PREFS_KEY_DOWNLOAD_QUEUE_SIZE)) {
					final int maxActive = pc.getCoreIntParameter(
							PluginConfig.CORE_PARAM_INT_MAX_ACTIVE);
					int newMaxDL = StaticUtils.getNumber(val).intValue();
					if (newMaxDL > maxActive) {
						pc.setCoreIntParameter(PluginConfig.CORE_PARAM_INT_MAX_ACTIVE,
								newMaxDL);
					}
					pc.setCoreIntParameter(PluginConfig.CORE_PARAM_INT_MAX_DOWNLOADS,
							newMaxDL);

				} else if (key.equals(TR_PREFS_KEY_DOWNLOAD_QUEUE_ENABLED)) {
					int max = StaticUtils.getBoolean(val)
							? ConfigurationDefaults.getInstance().getIntParameter(
									"max downloads")
							: 0;
					pc.setCoreIntParameter(PluginConfig.CORE_PARAM_INT_MAX_DOWNLOADS,
							max);

				} else if (key.equals(TR_PREFS_KEY_SEED_QUEUE_SIZE)) {
					int maxSeeds = StaticUtils.getNumber(val).intValue();

					final int maxActive = pc.getCoreIntParameter(
							PluginConfig.CORE_PARAM_INT_MAX_ACTIVE);
					int newMax = maxActive - maxSeeds;
					if (newMax < 0) {
						pc.setCoreIntParameter(PluginConfig.CORE_PARAM_INT_MAX_ACTIVE,
								maxSeeds);
					} else {
						pc.setCoreIntParameter(PluginConfig.CORE_PARAM_INT_MAX_ACTIVE,
								newMax);
					}

				} else if (key.equals(TR_PREFS_KEY_SEED_QUEUE_ENABLED)) {
					// TODO

				} else if (key.equals(TR_PREFS_KEY_ACTIVE_QUEUE_SIZE)) {
					int maxActive = StaticUtils.getNumber(val).intValue();
					pc.setCoreIntParameter(PluginConfig.CORE_PARAM_INT_MAX_ACTIVE,
							maxActive);

				} else if (key.equals(TR_PREFS_KEY_START)) {

					COConfigurationManager.setParameter("Default Start Torrents Stopped",
							!StaticUtils.getBoolean(val));

				} else if (key.equals(TR_PREFS_KEY_RENAME_PARTIAL_FILES)) {

					COConfigurationManager.setParameter("Rename Incomplete Files",
							StaticUtils.getBoolean(val));

				} else if (key.equals(TR_PREFS_KEY_DSPEED_ENABLED)
						|| key.equals(FIELD_TORRENT_DOWNLOAD_LIMITED)) {

					int down_limit = pc.getCoreIntParameter(
							PluginConfig.CORE_PARAM_INT_MAX_DOWNLOAD_SPEED_KBYTES_PER_SEC);

					boolean enable = StaticUtils.getBoolean(val);

					if (!enable && down_limit != 0) {

						down_limit = 0;

						pc.setCoreIntParameter(
								PluginConfig.CORE_PARAM_INT_MAX_DOWNLOAD_SPEED_KBYTES_PER_SEC,
								down_limit);
					} else if (enable && down_limit == 0) {
						int lastRate = pc.getUnsafeIntParameter(
								"config.ui.speed.partitions.manual.download.last");
						if (lastRate <= 0) {
							lastRate = 10;
						}
						pc.setCoreIntParameter(
								PluginConfig.CORE_PARAM_INT_MAX_DOWNLOAD_SPEED_KBYTES_PER_SEC,
								lastRate);
					}
				} else if (key.equals(TR_PREFS_KEY_DSPEED_KBps)
						|| key.equals(FIELD_TORRENT_DOWNLOAD_LIMIT)) {

					int down_limit = pc.getCoreIntParameter(
							PluginConfig.CORE_PARAM_INT_MAX_DOWNLOAD_SPEED_KBYTES_PER_SEC);

					int limit = StaticUtils.getNumber(val).intValue();

					if (limit != down_limit) {

						pc.setCoreIntParameter(
								PluginConfig.CORE_PARAM_INT_MAX_DOWNLOAD_SPEED_KBYTES_PER_SEC,
								limit);
					}
				} else if (key.equals(TR_PREFS_KEY_USPEED_ENABLED)
						|| key.equals("uploadLimited")) {
					boolean enable = StaticUtils.getBoolean(val);

					// turn off auto speed for both normal and seeding-only mode
					// this will reset upload speed to what it was before it was on
					pc.setCoreBooleanParameter(
							PluginConfig.CORE_PARAM_BOOLEAN_AUTO_SPEED_ON, false);
					pc.setCoreBooleanParameter(
							PluginConfig.CORE_PARAM_BOOLEAN_AUTO_SPEED_SEEDING_ON, false);

					int up_limit = pc.getCoreIntParameter(
							PluginConfig.CORE_PARAM_INT_MAX_UPLOAD_SPEED_KBYTES_PER_SEC);
					int up_limit_seeding = pc.getCoreIntParameter(
							PluginConfig.CORE_PARAM_INT_MAX_UPLOAD_SPEED_SEEDING_KBYTES_PER_SEC);

					if (!enable) {
						pc.setCoreIntParameter(
								PluginConfig.CORE_PARAM_INT_MAX_UPLOAD_SPEED_KBYTES_PER_SEC, 0);
						pc.setCoreIntParameter(
								PluginConfig.CORE_PARAM_INT_MAX_UPLOAD_SPEED_SEEDING_KBYTES_PER_SEC,
								0);
					} else if (up_limit == 0 || up_limit_seeding == 0) {
						int lastRate = pc.getUnsafeIntParameter(
								"config.ui.speed.partitions.manual.upload.last");
						if (lastRate <= 0) {
							lastRate = 10;
						}
						pc.setCoreIntParameter(
								PluginConfig.CORE_PARAM_INT_MAX_UPLOAD_SPEED_KBYTES_PER_SEC,
								lastRate);
						pc.setCoreIntParameter(
								PluginConfig.CORE_PARAM_INT_MAX_UPLOAD_SPEED_SEEDING_KBYTES_PER_SEC,
								lastRate);
					}
				} else if (key.equals(TR_PREFS_KEY_USPEED_KBps)
						|| key.equals("uploadLimit")) {

					// turn off auto speed for both normal and seeding-only mode
					// this will reset upload speed to what it was before it was on
					pc.setCoreBooleanParameter(
							PluginConfig.CORE_PARAM_BOOLEAN_AUTO_SPEED_ON, false);
					pc.setCoreBooleanParameter(
							PluginConfig.CORE_PARAM_BOOLEAN_AUTO_SPEED_SEEDING_ON, false);

					int limit = StaticUtils.getNumber(val).intValue();

					pc.setCoreIntParameter(
							PluginConfig.CORE_PARAM_INT_MAX_UPLOAD_SPEED_KBYTES_PER_SEC,
							limit);
					pc.setCoreIntParameter(
							PluginConfig.CORE_PARAM_INT_MAX_UPLOAD_SPEED_SEEDING_KBYTES_PER_SEC,
							limit);
				} else if (key.equals(
						TransmissionVars.TR_PREFS_KEY_PEER_PORT_RANDOM_ON_START)) {

					boolean random = StaticUtils.getBoolean(val);

					pc.setUnsafeBooleanParameter("Listen.Port.Randomize.Enable", random);

				} else if (key.equals(TransmissionVars.TR_PREFS_KEY_PEER_PORT)
						|| key.equals("port")) {

					int port = StaticUtils.getNumber(val).intValue();

					pc.setCoreIntParameter(PluginConfig.CORE_PARAM_INT_INCOMING_TCP_PORT,
							port);
					pc.setCoreIntParameter(PluginConfig.CORE_PARAM_INT_INCOMING_UDP_PORT,
							port);
				} else if (key.equals(TR_PREFS_KEY_ENCRYPTION)) {

					String value = (String) val;

					boolean required = value.equals("required");

					COConfigurationManager.setParameter(
							"network.transport.encrypted.require", required);
				} else if (key.equals("seedRatioLimit")) {
					// RPC v5

					float ratio = StaticUtils.getNumber(val).floatValue();

					COConfigurationManager.setParameter("Stop Ratio", ratio);

				} else if (key.equals("seedRatioLimited")) {
					// RPC v5

					boolean limit = StaticUtils.getBoolean(val);

					float ratio;
					if (limit) {
						// 2f is made up; sharing is caring
						if (args.containsKey("seedRatioLimit")) {
							ratio = StaticUtils.getNumber(args.get("seedRatioLimit"),
									2f).floatValue();
						} else {
							ratio = 2f;
						}
					} else {
						ratio = 0f;
					}

					COConfigurationManager.setParameter("Stop Ratio", ratio);

				} else {

					if (plugin.trace_param.getValue()) {
						plugin.log("Unhandled session-set field: " + key);
					}
				}
			} catch (Throwable t) {
				Debug.out(key + ":" + val, t);
			}
		}
	}

	public static void method_Session_Stats(Map<String, Object> args,
			Map<String, Object> result) {
		/*
		string                     | value type
		---------------------------+-------------------------------------------------
		"activeTorrentCount"       | number
		"downloadSpeed"            | number
		"pausedTorrentCount"       | number
		"torrentCount"             | number
		"uploadSpeed"              | number
		---------------------------+-------------------------------+
		"cumulative-stats"         | object, containing:           |
		                          +------------------+------------+
		                          | uploadedBytes    | number     | tr_session_stats
		                          | downloadedBytes  | number     | tr_session_stats
		                          | filesAdded       | number     | tr_session_stats
		                          | sessionCount     | number     | tr_session_stats
		                          | secondsActive    | number     | tr_session_stats
		---------------------------+-------------------------------+
		"current-stats"            | object, containing:           |
		                          +------------------+------------+
		                          | uploadedBytes    | number     | tr_session_stats
		                          | downloadedBytes  | number     | tr_session_stats
		                          | filesAdded       | number     | tr_session_stats
		                          | sessionCount     | number     | tr_session_stats
		                          | secondsActive    | number     | tr_session_stats
		 */

		//noinspection unchecked
		List<String> fields = (List<String>) args.get(ARG_FIELDS);
		boolean all = fields == null || fields.size() == 0;
		if (!all) {
			// sort so we can't use Collections.binarySearch
			Collections.sort(fields);
		}

		Core core = CoreFactory.getSingleton();
		GlobalManager gm = core.getGlobalManager();
		GlobalManagerStats stats = gm.getStats();

		TagManager tm = TagManagerFactory.getTagManager();

		// < RPC v4
		if (all || Collections.binarySearch(fields,
				TR_SESSION_STATS_ACTIVE_TORRENT_COUNT) >= 0) {
			// Could use 7 or "tag.type.ds.act" for tag_active
			Tag tag = tm.getTagType(TagType.TT_DOWNLOAD_STATE).getTag(7);
			result.put(TR_SESSION_STATS_ACTIVE_TORRENT_COUNT,
					tag == null ? 0 : tag.getTaggedCount());
		}

		if (canAdd(TR_SESSION_STATS_DOWNLOAD_SPEED, fields, all)) {
			result.put(TR_SESSION_STATS_DOWNLOAD_SPEED,
					stats.getDataAndProtocolReceiveRate());
		}

		if (all || Collections.binarySearch(fields,
				TR_SESSION_STATS_PAUSED_TORRENT_COUNT) >= 0) {
			// Could use 8 or "tag.type.ds.pau" for tag_pause
			Tag tag = tm.getTagType(TagType.TT_DOWNLOAD_STATE).getTag(8);
			result.put(TR_SESSION_STATS_PAUSED_TORRENT_COUNT,
					tag == null ? 0 : tag.getTaggedCount());
		}

		if (canAdd(TR_SESSION_STATS_TORRENT_COUNT, fields, all)) {
			// XXX: This is size with low-noise torrents, which aren't normally shown
			result.put(TR_SESSION_STATS_TORRENT_COUNT,
					gm.getDownloadManagers().size());
		}

		if (canAdd(TR_SESSION_STATS_UPLOAD_SPEED, fields, all)) {
			result.put(TR_SESSION_STATS_UPLOAD_SPEED,
					stats.getDataAndProtocolSendRate());
		}

		// RPC v4
		if (canAdd(TR_SESSION_STATS_CURRENT, fields, all)) {
			Map<String, Object> current_stats = new HashMap<>();

			result.put(TR_SESSION_STATS_CURRENT, current_stats);

			current_stats.put("uploadedBytes", stats.getTotalDataBytesSent());
			current_stats.put("downloadedBytes", stats.getTotalDataBytesReceived());

			long sent = stats.getTotalDataBytesSent();
			long received = stats.getTotalDataBytesReceived();

			float ratio;

			if (received == 0) {

				ratio = (sent == 0 ? 1 : Float.MAX_VALUE);

			} else {

				ratio = ((float) sent) / received;
			}

			// Not sure where "ratio" comes from, as it's not in the spec
			current_stats.put("ratio", ratio);

			OverallStats totalStats = StatsFactory.getStats();

			current_stats.put("secondsActive", totalStats.getSessionUpTime());
			current_stats.put("filesAdded", 0);
		}

		if (canAdd(TR_SESSION_STATS_CUMULATIVE, fields, all)) {
			// RPC v4
			OverallStats totalStats = StatsFactory.getStats();

			Map<String, Object> cumulative_stats = new HashMap<>();
			result.put(TR_SESSION_STATS_CUMULATIVE, cumulative_stats);

			cumulative_stats.put("uploadedBytes", totalStats.getUploadedBytes());
			cumulative_stats.put("downloadedBytes", totalStats.getDownloadedBytes());

			long filesAdded = 0;
			final Object downloadHistoryManager = gm.getDownloadHistoryManager();
			if (downloadHistoryManager instanceof DownloadHistoryManager) {
				filesAdded = ((DownloadHistoryManager) downloadHistoryManager).getHistoryCount();
			}
			cumulative_stats.put("filesAdded", filesAdded);
			cumulative_stats.put("secondsActive", totalStats.getTotalUpTime());

			// Closest thing we have is # of times locale is set (set at startup, or when user changes languages)
			long sessionCount = COConfigurationManager.getIntParameter(
					"locale.set.complete.count");
			cumulative_stats.put("sessionCount", sessionCount);
		}
	}

	private static String getUserMessage(XMWebUIPlugin plugin,
			Map<String, Object> reply) {
		try {
			byte[] message_bytes = MapUtils.getMapByteArray(reply, "xmwebui_message",
					null);
			if (message_bytes == null || message_bytes.length == 0) {
				return null;
			}

			String message;

			try {
				message = new String(message_bytes, "UTF-8");

			} catch (Throwable e) {

				message = new String(message_bytes);
			}

			byte[] signature = MapUtils.getMapByteArray(reply, "xmwebui_message_sig",
					null);

			if (signature == null) {

				plugin.log("Signature missing from message");

				return null;
			}

			try {
				AEVerifier.verifyData(message, signature);

			} catch (Throwable e) {

				plugin.log("Message signature check failed", e);

				return null;
			}

			return message;
		} catch (Throwable e) {
			plugin.log("Failed get message", e);

			Debug.printStackTrace(e);
		}
		return null;
	}

	public static int getMediaServerActivePort(PluginInterface plugin_interface) {
		return plugin_interface.getPluginconfig().getUnsafeIntParameter(
				"Plugin.azupnpav.content_port", -1);
	}

}
