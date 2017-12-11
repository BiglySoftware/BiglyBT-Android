package com.aelitis.azureus.plugins.xmwebui;

/**
 * Variables converted from https://trac.transmissionbt.com/browser/trunk/libtransmission/transmission.h
 * Comments also from .h file
 * 
 * Not sure if I need it, but here's the header of transmission.h:
 * 
   * Copyright (c) Transmission authors and contributors
   *
   * Permission is hereby granted, free of charge, to any person obtaining a
   * copy of this software and associated documentation files (the "Software"),
   * to deal in the Software without restriction, including without limitation
   * the rights to use, copy, modify, merge, publish, distribute, sublicense,
   * and/or sell copies of the Software, and to permit persons to whom the
   * Software is furnished to do so, subject to the following conditions:
   *
   * The above copyright notice and this permission notice shall be included in
   * all copies or substantial portions of the Software.
   *
   * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
   * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
   * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
   * DEALINGS IN THE SOFTWARE. 
 */
public class TransmissionVars
{
	/** we won't (announce,scrape) this torrent to this tracker because
	 * the torrent is stopped, or because of an error, or whatever */
	public static final int TR_TRACKER_INACTIVE = 0;

	/** we will (announce,scrape) this torrent to this tracker, and are
	 * waiting for enough time to pass to satisfy the tracker's interval */
	public static final int TR_TRACKER_WAITING = 1;

	/** it's time to (announce,scrape) this torrent, and we're waiting on a
	 * a free slot to open up in the announce manager */
	public static final int TR_TRACKER_QUEUED = 2;

	/** we're (announcing,scraping) this torrent right now */
	public static final int TR_TRACKER_ACTIVE = 3;

	/////////////////////////////////////////////////////////////////////////////

	public static final String TR_PREFS_KEY_ALT_SPEED_ENABLED = "alt-speed-enabled";

	public static final String TR_PREFS_KEY_ALT_SPEED_UP_KBps = "alt-speed-up";

	public static final String TR_PREFS_KEY_ALT_SPEED_DOWN_KBps = "alt-speed-down";

	public static final String TR_PREFS_KEY_ALT_SPEED_TIME_BEGIN = "alt-speed-time-begin";

	public static final String TR_PREFS_KEY_ALT_SPEED_TIME_ENABLED = "alt-speed-time-enabled";

	public static final String TR_PREFS_KEY_ALT_SPEED_TIME_END = "alt-speed-time-end";

	public static final String TR_PREFS_KEY_ALT_SPEED_TIME_DAY = "alt-speed-time-day";

	public static final String TR_PREFS_KEY_BIND_ADDRESS_IPV4 = "bind-address-ipv4";

	public static final String TR_PREFS_KEY_BIND_ADDRESS_IPV6 = "bind-address-ipv6";

	public static final String TR_PREFS_KEY_BLOCKLIST_ENABLED = "blocklist-enabled";

	public static final String TR_PREFS_KEY_BLOCKLIST_URL = "blocklist-url";

	public static final String TR_PREFS_KEY_MAX_CACHE_SIZE_MB = "cache-size-mb";

	public static final String TR_PREFS_KEY_DHT_ENABLED = "dht-enabled";

	public static final String TR_PREFS_KEY_UTP_ENABLED = "utp-enabled";

	public static final String TR_PREFS_KEY_LPD_ENABLED = "lpd-enabled";

	public static final String TR_PREFS_KEY_DOWNLOAD_QUEUE_SIZE = "download-queue-size";

	public static final String TR_PREFS_KEY_DOWNLOAD_QUEUE_ENABLED = "download-queue-enabled";

	public static final String TR_PREFS_KEY_PREFETCH_ENABLED = "prefetch-enabled";

	public static final String TR_PREFS_KEY_DOWNLOAD_DIR = "download-dir";

	public static final String TR_PREFS_KEY_ENCRYPTION = "encryption";

	public static final String TR_PREFS_KEY_IDLE_LIMIT = "idle-seeding-limit";

	public static final String TR_PREFS_KEY_IDLE_LIMIT_ENABLED = "idle-seeding-limit-enabled";

	public static final String TR_PREFS_KEY_INCOMPLETE_DIR = "incomplete-dir";

	public static final String TR_PREFS_KEY_INCOMPLETE_DIR_ENABLED = "incomplete-dir-enabled";

	public static final String TR_PREFS_KEY_MSGLEVEL = "message-level";

	public static final String TR_PREFS_KEY_PEER_LIMIT_GLOBAL = "peer-limit-global";

	public static final String TR_PREFS_KEY_PEER_LIMIT_TORRENT = "peer-limit-per-torrent";

	public static final String TR_PREFS_KEY_PEER_PORT = "peer-port";

	public static final String TR_PREFS_KEY_PEER_PORT_RANDOM_ON_START = "peer-port-random-on-start";

	public static final String TR_PREFS_KEY_PEER_PORT_RANDOM_LOW = "peer-port-random-low";

	public static final String TR_PREFS_KEY_PEER_PORT_RANDOM_HIGH = "peer-port-random-high";

	public static final String TR_PREFS_KEY_PEER_SOCKET_TOS = "peer-socket-tos";

	public static final String TR_PREFS_KEY_PEER_CONGESTION_ALGORITHM = "peer-congestion-algorithm";

	public static final String TR_PREFS_KEY_PEX_ENABLED = "pex-enabled";

	public static final String TR_PREFS_KEY_PORT_FORWARDING = "port-forwarding-enabled";

	public static final String TR_PREFS_KEY_PREALLOCATION = "preallocation";

	public static final String TR_PREFS_KEY_RATIO = "ratio-limit";

	public static final String TR_PREFS_KEY_RATIO_ENABLED = "ratio-limit-enabled";

	public static final String TR_PREFS_KEY_RENAME_PARTIAL_FILES = "rename-partial-files";

	public static final String TR_PREFS_KEY_RPC_AUTH_REQUIRED = "rpc-authentication-required";

	public static final String TR_PREFS_KEY_RPC_BIND_ADDRESS = "rpc-bind-address";

	public static final String TR_PREFS_KEY_RPC_ENABLED = "rpc-enabled";

	public static final String TR_PREFS_KEY_RPC_PASSWORD = "rpc-password";

	public static final String TR_PREFS_KEY_RPC_PORT = "rpc-port";

	public static final String TR_PREFS_KEY_RPC_USERNAME = "rpc-username";

	public static final String TR_PREFS_KEY_RPC_URL = "rpc-url";

	public static final String TR_PREFS_KEY_RPC_WHITELIST_ENABLED = "rpc-whitelist-enabled";

	public static final String TR_PREFS_KEY_SCRAPE_PAUSED_TORRENTS = "scrape-paused-torrents-enabled";

	public static final String TR_PREFS_KEY_SCRIPT_TORRENT_DONE_FILENAME = "script-torrent-done-filename";

	public static final String TR_PREFS_KEY_SCRIPT_TORRENT_DONE_ENABLED = "script-torrent-done-enabled";

	public static final String TR_PREFS_KEY_SEED_QUEUE_SIZE = "seed-queue-size";

	public static final String TR_PREFS_KEY_SEED_QUEUE_ENABLED = "seed-queue-enabled";

	public static final String TR_PREFS_KEY_RPC_WHITELIST = "rpc-whitelist";

	public static final String TR_PREFS_KEY_QUEUE_STALLED_ENABLED = "queue-stalled-enabled";

	public static final String TR_PREFS_KEY_QUEUE_STALLED_MINUTES = "queue-stalled-minutes";

	public static final String TR_PREFS_KEY_DSPEED_KBps = "speed-limit-down";

	public static final String TR_PREFS_KEY_DSPEED_ENABLED = "speed-limit-down-enabled";

	public static final String TR_PREFS_KEY_USPEED_KBps = "speed-limit-up";

	public static final String TR_PREFS_KEY_USPEED_ENABLED = "speed-limit-up-enabled";

	public static final String TR_PREFS_KEY_UMASK = "umask";

	public static final String TR_PREFS_KEY_UPLOAD_SLOTS_PER_TORRENT = "upload-slots-per-torrent";

	public static final String TR_PREFS_KEY_START = "start-added-torrents";

	public static final String TR_PREFS_KEY_TRASH_ORIGINAL = "trash-original-torrent-files";

	/////////////////////////////////////////////////////////////////////////////

	public static final long TR_SCHED_SUN = (1 << 0);

	public static final long TR_SCHED_MON = (1 << 1);

	public static final long TR_SCHED_TUES = (1 << 2);

	public static final long TR_SCHED_WED = (1 << 3);

	public static final long TR_SCHED_THURS = (1 << 4);

	public static final long TR_SCHED_FRI = (1 << 5);

	public static final long TR_SCHED_SAT = (1 << 6);

	public static final long TR_SCHED_WEEKDAY = (TR_SCHED_MON | TR_SCHED_TUES
			| TR_SCHED_WED | TR_SCHED_THURS | TR_SCHED_FRI);

	public static final long TR_SCHED_WEEKEND = (TR_SCHED_SUN | TR_SCHED_SAT);

	public static final long TR_SCHED_ALL = (TR_SCHED_WEEKDAY | TR_SCHED_WEEKEND);

	//////////////////////////////////////////////////////////////////////////////

	public static final long TR_PRI_LOW = -1;

	public static final long TR_PRI_NORMAL = 0; /* since NORMAL is 0, memset initializes nicely */

	public static final long TR_PRI_HIGH = 1;

	//////////////////////////////////////////////////////////////////////////////
	//tr_stat_errtype;

	/* everything's fine */
	public static final long TR_STAT_OK = 0;

	/* when we anounced to the tracker, we got a warning in the response */
	public static final long TR_STAT_TRACKER_WARNING = 1;

	/* when we anounced to the tracker, we got an error in the response */
	public static final long TR_STAT_TRACKER_ERROR = 2;

	/* local trouble, such as disk full or permissions error */
	public static final long TR_STAT_LOCAL_ERROR = 3;

	//////////////////////////////////////////////////////////////////////////////
	//tr_idlelimit;
	/* follow the global settings */
	public static final long TR_IDLELIMIT_GLOBAL    = 0;
	/* override the global settings, seeding until a certain idle time */
	public static final long TR_IDLELIMIT_SINGLE    = 1;
	/* override the global settings, seeding regardless of activity */
	public static final long TR_IDLELIMIT_UNLIMITED = 2;
	
	//////////////////////////////////////////////////////////////////////////////
	//tr_ratiolimit;
  /* follow the global settings */
	public static final long TR_RATIOLIMIT_GLOBAL    = 0;
  /* override the global settings, seeding until a certain ratio */
	public static final long TR_RATIOLIMIT_SINGLE    = 1;
  /* override the global settings, seeding regardless of ratio */
	public static final long TR_RATIOLIMIT_UNLIMITED = 2;

	
	public static final long TR_ETA_NOT_AVAIL = -1;
	public static final long TR_ETA_UNKNOWN = -2;

	//////////////////////////////////////////////////////////////////////////////

	public static final String FIELD_TORRENT_WANTED = "wanted";

	public static final String FIELD_TORRENT_PRIORITIES = "priorities";

	public static final String FIELD_TORRENT_FILE_COUNT = "fileCount";

	public static final String FIELD_TORRENT_ETA = "eta";

	public static final String FIELD_TORRENT_ERROR_STRING = "errorString";

	public static final String FIELD_TORRENT_ERROR = "error";

	public static final String FIELD_TORRENT_STATUS = "status";

	public static final String FIELD_TORRENT_RATE_DOWNLOAD = "rateDownload";

	public static final String FIELD_TORRENT_RATE_UPLOAD = "rateUpload";

	public static final String FIELD_TORRENT_SIZE_WHEN_DONE = "sizeWhenDone";

	public static final String FIELD_TORRENT_PERCENT_DONE = "percentDone";

	public static final String FIELD_TORRENT_NAME = "name";

	public static final String FIELD_TORRENT_ID = "id";

	public static final String FIELD_TORRENT_FILES_PRIORITY = "priority";

	public static final String FIELD_TORRENT_POSITION = "queuePosition";

	public static final String FIELD_TORRENT_UPLOAD_RATIO = "uploadRatio";

	public static final String FIELD_TORRENT_DATE_ADDED = "addedDate";

	public static final String FIELD_TORRENT_HASH = "hashString";

	//////////////////////////////////////////////////////////////////////////////

	public static final String TR_SESSION_STATS_ACTIVE_TORRENT_COUNT = "activeTorrentCount";

	public static final String TR_SESSION_STATS_PAUSED_TORRENT_COUNT = "pausedTorrentCount";

	public static final String TR_SESSION_STATS_DOWNLOAD_SPEED = "downloadSpeed";

	public static final String TR_SESSION_STATS_UPLOAD_SPEED = "uploadSpeed";

	public static final String TR_SESSION_STATS_TORRENT_COUNT = "torrentCount";	

	public static final String TR_SESSION_STATS_CURRENT = "current-stats";	

	public static final String TR_SESSION_STATS_CUMULATIVE = "cumulative-stats";	

	//////////////////////////////////////////////////////////////////////////////
	
	public static final String FIELD_FILESTATS_BYTES_COMPLETED = "bytesCompleted";	
	public static final String FIELD_FILESTATS_WANTED = "wanted";
	public static final String FIELD_FILESTATS_PRIORITY = "priority";
	public static final String FIELD_FILES_LENGTH = "length";
	public static final String FIELD_FILES_NAME = "name";
	public static final String FIELD_FILES_CONTENT_URL = "contentURL";
	public static final String FIELD_FILES_FULL_PATH = "fullPath";
	
	//////////////////////////////////////////////////////////////////////////////
	
	public static long convertVuzePriority(int priority) {
		return priority == 0 ? TransmissionVars.TR_PRI_NORMAL
				: priority < 0 ? TransmissionVars.TR_PRI_LOW
						: TransmissionVars.TR_PRI_HIGH;
	}

	//////////////////////////////////////////////////////////////////////////////
	
	public static final int TR_STATUS_STOPPED        = 0; /* Torrent is stopped */
	public static final int TR_STATUS_CHECK_WAIT     = 1; /* Queued to check files */
	public static final int TR_STATUS_CHECK          = 2; /* Checking files */
	public static final int TR_STATUS_DOWNLOAD_WAIT  = 3; /* Queued to download */
	public static final int TR_STATUS_DOWNLOAD       = 4; /* Downloading */
	public static final int TR_STATUS_SEED_WAIT      = 5; /* Queued to seed */
	public static final int TR_STATUS_SEED           = 6; /* Seeding */

	//////////////////////////////////////////////////////////////////////////////

	public static final String FIELD_SUBSCRIPTION_NAME = "name";
	public static final String FIELD_SUBSCRIPTION_ADDEDON = "addedDate";
	public static final String FIELD_SUBSCRIPTION_ASSOCIATION_COUNT = "associationCount";
	public static final String FIELD_SUBSCRIPTION_POPULARITY = "popularity";
	public static final String FIELD_SUBSCRIPTION_CATEGORY = "category";
	public static final String FIELD_SUBSCRIPTION_CREATOR = "creator";
	public static final String FIELD_SUBSCRIPTION_ENGINE_NAME = "engineName";
	public static final String FIELD_SUBSCRIPTION_ENGINE_TYPE = "engineType";
	public static final String FIELD_SUBSCRIPTION_HIGHEST_VERSION = "highestVersion";
	public static final String FIELD_SUBSCRIPTION_NAME_EX = "nameEx";
	public static final String FIELD_SUBSCRIPTION_QUERY_KEY = "queryKey";
	public static final String FIELD_SUBSCRIPTION_REFERER = "referer";
	public static final String FIELD_SUBSCRIPTION_TAG_UID = "tagUID";
	public static final String FIELD_SUBSCRIPTION_URI = "uri";
	public static final String FIELD_SUBSCRIPTION_ANONYMOUS = "anonymous";
	public static final String FIELD_SUBSCRIPTION_AUTO_DL_SUPPORTED = "autoDLSupported";
	public static final String FIELD_SUBSCRIPTION_AUTO_DOWNLOAD = "autoDownlaod";
	public static final String FIELD_SUBSCRIPTION_MINE = "mine";
	public static final String FIELD_SUBSCRIPTION_PUBLIC = "public";
	public static final String FIELD_SUBSCRIPTION_IS_SEARCH_TEMPLATE = "isSearchTemplate";
	public static final String FIELD_SUBSCRIPTION_SUBSCRIBED = "subscribed";
	public static final String FIELD_SUBSCRIPTION_UPDATEABLE = "updateable";
	public static final String FIELD_SUBSCRIPTION_SHAREABLE = "shareable";
	public static final String FIELD_SUBSCRIPTION_RESULTS_COUNT = "resultsCount";
	public static final String FIELD_SUBSCRIPTION_RESULTS = "results";
	public static final String FIELD_SUBSCRIPTION_ENGINE = "engine";
	public static final String FIELD_SUBSCRIPTION_ENGINE_URL = "url";
	public static final String FIELD_SUBSCRIPTION_ENGINE_NAMEX = "nameEx";
	public static final String FIELD_SUBSCRIPTION_ENGINE_AUTHMETHOD = "authMethod";
	public static final String FIELD_SUBSCRIPTION_ENGINE_LASTUPDATED = "lastUpdated";
	public static final String FIELD_SUBSCRIPTION_ENGINE_SOURCE = "source";

	public static final String FIELD_SUBSCRIPTION_RESULT_UID = "u";
	public static final String FIELD_SUBSCRIPTION_RESULT_ISREAD = "subs_is_read";

	public static final String FIELD_TAG_NAME = "name";
	public static final String FIELD_TAG_COUNT = "count";
	public static final String FIELD_TAG_TYPE = "type";
	public static final String FIELD_TAG_TYPENAME = "type-name";
	public static final String FIELD_TAG_CATEGORY_TYPE = "category-type";
	public static final String FIELD_TAG_UID = "uid";
	public static final String FIELD_TAG_ID = "id";
	public static final String FIELD_TAG_COLOR = "color";
	public static final String FIELD_TAG_CANBEPUBLIC = "canBePublic";
	public static final String FIELD_TAG_PUBLIC = "public";
	public static final String FIELD_TAG_VISIBLE = "visible";
	public static final String FIELD_TAG_GROUP = "group";
	public static final String FIELD_TAG_AUTO_ADD = "auto_add";
	public static final String FIELD_TAG_AUTO_REMOVE = "auto_remove";

}
