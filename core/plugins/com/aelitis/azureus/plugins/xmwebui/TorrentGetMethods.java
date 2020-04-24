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
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.*;

import org.gudy.bouncycastle.util.encoders.Base64;

import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.download.DownloadManagerStats;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.peer.*;
import com.biglybt.core.peer.util.PeerUtils;
import com.biglybt.core.peermanager.piecepicker.PiecePicker;
import com.biglybt.core.tag.*;
import com.biglybt.core.tracker.TrackerPeerSource;
import com.biglybt.core.tracker.client.*;
import com.biglybt.core.util.*;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.plugin.startstoprules.defaultplugin.DefaultRankCalculator;
import com.biglybt.plugin.startstoprules.defaultplugin.StartStopRulesDefaultPlugin;
import com.biglybt.util.JSONUtils;
import com.biglybt.util.MapUtils;
import com.biglybt.util.PlayUtils;

import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.*;
import com.biglybt.pif.download.DownloadStub.DownloadStubFile;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.tracker.web.TrackerWebPageRequest;

import static com.aelitis.azureus.plugins.xmwebui.StaticUtils.*;
import static com.aelitis.azureus.plugins.xmwebui.TransmissionVars.*;

@SuppressWarnings({
	"rawtypes",
	"unused"
})
public class TorrentGetMethods
{
	private static final String PREFIX_FILES_HC = "files-hc-";

	private final static Map<String, Map<Long, String>> session_torrent_info_cache = new HashMap<>();

	private static int[] getFileIndexes(Map args, long download_id) {
		Object file_ids = args.get("file-indexes-" + download_id);
		int[] file_indexes = null;
		if (file_ids instanceof Number) {
			file_indexes = new int[] {
				((Number) file_ids).intValue()
			};
		} else if (file_ids instanceof List) {
			List listFileIDs = (List) file_ids;
			file_indexes = new int[listFileIDs.size()];
			for (int i = 0; i < listFileIDs.size(); i++) {
				Object o = listFileIDs.get(i);
				if (o instanceof Number) {
					file_indexes[i] = ((Number) o).intValue();
				}
			}
		}
		return file_indexes;
	}

	static void get(XMWebUIPlugin plugin,
			TrackerWebPageRequest request, String session_id, Map args,
			Map<String, Object> result) {

		// When "file_indexes" key is present, returns:
		// NOTE: Array position does not equal file index!  Use "index" key!
		// {
		// 	torrents : [
		//               {
		//                 <key> : <value>, 
		//                 files : 
		//                         [ 
		//                           { 
		//                             "index": <file-index>, 
		//                             <other-fields>: <other-values>
		//                           },
		//                          <more file maps>
		//                         ]
		//                },
		//               <more keys> : <more values>
		//             ]
		// }

		//noinspection unchecked
		List<String> fields = (List<String>) args.get(ARG_FIELDS);

		if (fields == null) {

			fields = new ArrayList<>();
		}

		Object ids = args.get("ids");

		boolean is_recently_active = plugin.handleRecentlyRemoved(session_id, args,
				result);

		List<DownloadStub> downloads = plugin.getDownloads(ids, true);

		//noinspection unchecked
		List<String> file_fields = (List<String>) args.get(
				ARG_TORRENT_GET_FILE_FIELDS);
		if (file_fields != null) {
			Collections.sort(file_fields);
		}

		Map<Long, Map<String, Object>> torrent_info = new LinkedHashMap<>();

		String agent = MapUtils.getMapString(request.getHeaders(), "User-Agent",
				"");
		boolean xmlEscape = agent.startsWith("Mozilla/");

		for (DownloadStub download_stub : downloads) {
			if (download_stub.isStub()) {
				method_Torrent_Get_Stub(plugin, request, args, fields, torrent_info,
						download_stub, file_fields, xmlEscape);
			} else {
				method_Torrent_Get_NonStub(plugin, request, args, fields, torrent_info,
						(Download) download_stub, file_fields, xmlEscape);
			}
		} // for downloads

		if (is_recently_active) {

			// just return the latest diff for this session
			// we could possibly, in theory, update the cache for all calls to this method, not just the 'recently active' calls
			// but I don't trust the client enough atm to behave correctly

			synchronized (session_torrent_info_cache) {

				if (session_torrent_info_cache.size() > 8) {

					session_torrent_info_cache.clear();
				}

				// Android: minSDK 24
//				Map<Long, String> torrent_info_cache = session_torrent_info_cache.computeIfAbsent(
//						session_id, k -> new HashMap<>());
				Map<Long, String> torrent_info_cache = session_torrent_info_cache.get(session_id);
				if (torrent_info_cache == null) {
					torrent_info_cache = new HashMap<>();
					session_torrent_info_cache.put(session_id, torrent_info_cache);
				}

				List<Long> same = new ArrayList<>();

				for (Map.Entry<Long, Map<String, Object>> entry : torrent_info.entrySet()) {

					long id = entry.getKey();
					Map<String, Object> torrent = entry.getValue();

					String current = JSONUtils.encodeToJSON(torrent);

					String prev = torrent_info_cache.get(id);

					if (prev != null && prev.equals(current)) {

						same.add(id);

					} else {

						torrent_info_cache.put(id, current);
					}
				}

				if (same.size() > 0) {

					// System.out.println( "same info: " + same.size() + " of " + torrent_info.size());

					for (long id : same) {

						torrent_info.remove(id);
					}
				}
			}
		}

		String format = MapUtils.getMapString(args, "format", "objects");

		if (format.equalsIgnoreCase("table")) {
			List<Collection<Object>> torrents = new ArrayList<>();

			result.put("torrents", torrents);

			boolean first = true;

			for (Map<String, Object> mapTorrent : torrent_info.values()) {
				Collection<Object> values = mapTorrent.values();
				if (first) {
					torrents.add(new ArrayList<>(mapTorrent.keySet()));
					first = false;
				}
				torrents.add(values);
			}
		} else {
			List<Map> torrents = new ArrayList<>();

			result.put("torrents", torrents);

			torrents.addAll(torrent_info.values());
		}
	}

	private static void method_Torrent_Get_NonStub(XMWebUIPlugin plugin,
			TrackerWebPageRequest request, Map<String, Object> args,
			List<String> fields, Map<Long, Map<String, Object>> torrent_info,
			Download download, List<String> file_fields, boolean xmlEscape) {

		Torrent t = download.getTorrent();

		if (t == null) {
			// Can't do this.. download is a nullstate, which doesn't store
			// Attributes, and getID relies on that
			//t = new TorrentBlank(download);
			return;
		}

		long download_id = plugin.getID(download, true);

		DownloadManager core_download = PluginCoreUtils.unwrap(download);

		PEPeerManager 	pm = core_download.getPeerManager();
		DiskManager 	dm = core_download.getDiskManager();

		DownloadStats stats = download.getStats();

		Map<String, Object> torrent = new HashMap<>(fields.size() + 8);

		torrent_info.put(download_id, torrent);

		int peers_from_us = 0;
		int peers_to_us = 0;
		 
		
		if ( fields.contains( "peersGettingFromUs" ) || fields.contains( "peersSendingToUs" )) {

			if (pm != null) {
	
				List<PEPeer> peers = pm.getPeers();
	
				for (PEPeer peer : peers) {
	
					PEPeerStats pstats = peer.getStats();
	
					if (pstats.getDataReceiveRate() > 0) {
	
						peers_to_us++;
					}
	
					if (pstats.getDataSendRate() > 0) {
	
						peers_from_us++;
					}
				}
			}
		}
				
		long haveValid			= 0;
		long desiredAvailable 	= 0;
		
		if ( fields.contains(FIELD_TORRENT_DESIRED_AVAILABLE) || fields.contains(FIELD_TORRENT_HAVE_VALID )) {

			DiskManagerPiece[] dmPieces_maybe_null;
			
			if ( dm == null ){
				
				// boo, too expensive to do this (user reporting 100% CPU, meh
				
				// dmPieces_maybe_null = core_download.getDiskManagerPiecesSnapshot();
					
				dmPieces_maybe_null = null;
				
			}else{
				
				dmPieces_maybe_null = dm.getPieces();
			}
			
				
			if ( dmPieces_maybe_null == null ){
				
				DownloadManagerStats core_stats = core_download.getStats();
				
				haveValid = Math.max( 0, core_stats.getSizeExcludingDND() - core_stats.getRemainingExcludingDND());
						
			}else{
				
				int[] availability;
				
				if ( pm == null ){
					
					availability = null;
					
				}else{
				
					PiecePicker piecePicker = pm.getPiecePicker();
					
					if ( piecePicker != null ){
						
						availability = piecePicker.getAvailability();
						
					}else{
						
						availability = null;
					}
				}
	
				for ( int i=0;i<dmPieces_maybe_null.length;i++){
					
					DiskManagerPiece piece = dmPieces_maybe_null[i];
					
					if ( piece.isSkipped()){
						
						continue;
					}
					
					long pieceLength = piece.getLength();
					
					if ( piece.isDone()){
						
						haveValid += pieceLength;
						
					}else{
						
						if ( availability != null && availability[i] >= 1 ){
						
							desiredAvailable += pieceLength;
						}
					}
				}
			}
		}
		
		//noinspection unchecked
		List<String> peer_fields = (List<String>) args.get(
				ARG_TORRENT_GET_PEER_FIELDS);
		if (peer_fields != null) {
			Collections.sort(peer_fields);
		}

		for (String field : fields) {

			Object value = null;

			switch (field) {
				case FIELD_TORRENT_DATE_ACTIVITY:
					// RPC v0
					// activityDate                | number                      | tr_stat
					value = torrentGet_activityDate(core_download, false);

					break;
				case "activityDateRelative":
					// RPC v0
					// activityDate                | number                      | tr_stat
					value = torrentGet_activityDate(core_download, true);

					break;
				case FIELD_TORRENT_DATE_ADDED:
					// RPC v0
					// addedDate                   | number                      | tr_stat
					/** When the torrent was first added. */
					value = core_download.getDownloadState().getLongParameter(
							DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME) / 1000;

					break;
				case FIELD_TORRENT_ANNOUNCEURL:
					// Removed in RPC v7

					value = t.getAnnounceURL().toExternalForm();

					break;
				case FIELD_TORRENT_BANDWITH_PRIORITY:
					// RPC v5: Not Supported
					// bandwidthPriority           | number                      | tr_priority_t
					/** torrent's bandwidth priority. */
					value = TR_PRI_NORMAL;

					break;
				case FIELD_TORRENT_COMMENT:
					// RPC v0
					// comment                     | string                      | tr_info

					value = t.getComment();

					break;
				case FIELD_TORRENT_CORRUPT_EVER:
					// RPC v0 TODO: Do we want just hash fails?
					// corruptEver                 | number                      | tr_stat
					/**
					 * Byte count of all the corrupt data you've ever downloaded for
					 * this torrent. If you're on a poisoned torrent, this number can
					 * grow very large. 
					 */
					value = stats.getDiscarded() + stats.getHashFails();

					break;
				case FIELD_TORRENT_CREATOR:
					// RPC v0
					// creator                     | string                      | tr_info
					value = t.getCreatedBy();

					break;
				case FIELD_TORRENT_DATE_CREATED:

					// RPC v0
					// dateCreated                 | number                      | tr_info
					value = t.getCreationDate();

					break;
				case FIELD_TORRENT_DESIRED_AVAILABLE:
					
					// desiredAvailable            | number                      | tr_stat
					/**
					 * Byte count of all the piece data we want and don't have yet,
					 * but that a connected peer does have. [0...leftUntilDone] 
					 */
					
					value = desiredAvailable;
					
					break;
				case FIELD_TORRENT_DATE_DONE:
					// RPC v0
					// doneDate                    | number                      | tr_stat
					/** When the torrent finished downloading. */
					if (core_download.isDownloadComplete(false)) {
						value = core_download.getDownloadState().getLongParameter(
								DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME) / 1000;
					} else {
						// TODO: Verify what value to send when not complete
						value = 0;
					}

					break;
				case FIELD_TORRENT_DOWNLOAD_DIR:
					// RPC v4
					// downloadDir                 | string                      | tr_torrent

					if (t.isSimpleTorrent()) {
						value = new File(download.getSavePath()).getParent();
					} else {
						value = download.getSavePath();
					}

					break;
				case FIELD_TORRENT_DOWNLOADED_EVER:
					// RPC v0
					// downloadedEver              | number                      | tr_stat

					/**
					 * Byte count of all the non-corrupt data you've ever downloaded
					 * for this torrent. If you deleted the files and downloaded a second
					 * time, this will be 2*totalSize.. 
					 */
					value = stats.getDownloaded();

					break;
				case FIELD_TORRENT_DOWNLOAD_LIMIT:
				case TR_PREFS_KEY_DSPEED_KBps:
					// RPC v5 (alternate is from 'set' prior to v5 -- added for rogue clients)
					// downloadLimit               | number                      | tr_torrent

					/** maximum download speed (KBps) */
					value = download.getMaximumDownloadKBPerSecond();

					break;
				case FIELD_TORRENT_DOWNLOAD_LIMITED:
				case TR_PREFS_KEY_DSPEED_ENABLED:
					// RPC v5 (alternate is from 'set' prior to v5 -- added for rogue clients)
					// downloadLimited             | boolean                     | tr_torrent

					/** true if "downloadLimit" is honored */
					value = download.getDownloadRateLimitBytesPerSecond() > 0;

					break;
				case FIELD_TORRENT_ERROR:
					// RPC v0
					// error                       | number                      | tr_stat
					/** Defines what kind of text is in errorString. TR_STAT_* */

					value = torrentGet_error(core_download, download);

					break;
				case FIELD_TORRENT_ERROR_STRING:
					// RPC v0
					// errorString                 | string                      | tr_stat

					value = torrentGet_errorString(core_download, download);

					break;
				case FIELD_TORRENT_ETA:
					// RPC v0
					// eta                         | number                      | tr_stat

					value = torrentGet_eta(core_download, download, stats);

					break;
				case FIELD_TORRENT_ETA_IDLE:
					// RPC v15
					/** If seeding, number of seconds left until the idle time limit is reached. */
					// TODO: No idea what etaIdle description means! What happens at idle time?

					value = TR_ETA_UNKNOWN;

					break;
				case FIELD_TORRENT_FILES:
					// RPC v0

					String host = (String) request.getHeaders().get("host");

					value = torrentGet_files(plugin, torrent, host, download, download_id,
							file_fields, args);

					// One hash for all files.  This won't work when our file list is a partial
					//if (value instanceof Collection) {
					//	torrent.put("files-hc", longHashSimpleList((Collection<?>) value));
					//}

					break;
				case FIELD_TORRENT_FILESTATS:
					// RPC v5

					value = torrentGet_fileStats(download, file_fields, args);

					break;
				case FIELD_TORRENT_HASH_STRING:
					// RPC v0
					// hashString                  | string                      | tr_info
					value = ByteFormatter.encodeString(t.getHash());

					break;
				case FIELD_TORRENT_HAVE_UNCHECKED:
					// haveUnchecked               | number                      | tr_stat
					/** Byte count of all the partial piece data we have for this torrent.
					 As pieces become complete, this value may decrease as portions of it
					 are moved to `corrupt' or `haveValid'. */
					// TODO: set when ST_CHECKING?
					value = 0;

					break;
				case FIELD_TORRENT_HAVE_VALID:
					// haveValid                   | number                      | tr_stat
					/** Byte count of all the checksum-verified data we have for this torrent.
					 */
					
					value = haveValid;

					break;
				case FIELD_TORRENT_HONORS_SESSION_LIMITS:
					// TODO RPC v5
					// honorsSessionLimits         | boolean                     | tr_torrent
					/** true if session upload limits are honored */
					value = false;

					break;
				case FIELD_TORRENT_ID:
					// id                          | number                      | tr_torrent
					value = download_id;

					break;
				case "isFinished":
					// RPC v9: TODO
					// isFinished                  | boolean                     | tr_stat
					/** A torrent is considered finished if it has met its seed ratio.
					 As a result, only paused torrents can be finished. */

					value = false;

					break;
				case "isPrivate":
					// RPC v0
					// isPrivate                   | boolean                     | tr_torrent
					value = t.isPrivate();

					break;
				case "isStalled":
					// RPC v14
					// isStalled                   | boolean                     | tr_stat

					value = torrentGet_isStalled(download);

					break;
				case FIELD_TORRENT_LABELS: {
					// RPC v16
					List<String> listTags = new ArrayList<>();

					TagManager tm = TagManagerFactory.getTagManager();

					List<Tag> tags = tm.getTagsForTaggable(core_download);
					if (tags != null) {
						for (Tag tag : tags) {
							listTags.add(tag.getTagName());
						}
					}

					value = listTags;

					break;
				}
				case "leechers":
					// Removed in RPC v7
					value = pm == null ? 0 : pm.getNbPeers();

					break;
				case FIELD_TORRENT_LEFT_UNTIL_DONE:
					// RPC v0
					// leftUntilDone               | number                      | tr_stat

					/** Byte count of how much data is left to be downloaded until we've got
					 all the pieces that we want. [0...tr_info.sizeWhenDone] */

					value = core_download.getStats().getRemainingExcludingDND();

					break;
				case "magnetLink":
					// TODO RPC v7
					// magnetLink                  | number                      | n/a
					// NOTE: I assume spec is wrong and it's a string..

					value = UrlUtils.getMagnetURI(download);

					break;
				case "manualAnnounceTime":
					// manualAnnounceTime          | number                      | tr_stat
					// spec is time_t, although it should be relative time. :(

					value = torrentGet_manualAnnounceTime(core_download);

					break;
				case "maxConnectedPeers":
					// maxConnectedPeers           | number                      | tr_torrent
					// TODO: Some sort of Peer Limit (tr_torrentSetPeerLimit )
					value = 0;

					break;
				case "metadataPercentComplete":
					// RPC v7: TODO
					// metadataPercentComplete     | double                      | tr_stat
					/**
					 * How much of the metadata the torrent has.
					 * For torrents added from a .torrent this will always be 1.
					 * For magnet links, this number will from from 0 to 1 as the metadata is downloaded.
					 * Range is [0..1] 
					 */
					// RPC v7
					value = 1.0f;

					break;
				case "name":

					value = download.getName();

					break;
				case "peer-limit":
					// peer-limit                  | number                      | tr_torrent
					// TODO
					/** how many peers this torrent can connect to */
					value = -1;

					break;
				case FIELD_TORRENT_PEERS:
					// RPC v2

					value = torrentGet_peers(core_download, peer_fields);

					break;
				case "peersConnected":
					// peersConnected              | number                      | tr_stat

					/** Number of peers that we're connected to */
					value = pm == null ? 0 : pm.getNbPeers() + pm.getNbSeeds();

					break;
				case "peersFrom":

					value = torrentGet_peersFrom(pm);

					break;
				case "peersGettingFromUs":
					// peersGettingFromUs          | number                      | tr_stat

					value = peers_from_us;

					break;
				case "peersSendingToUs":
					// peersSendingToUs            | number                      | tr_stat

					value = peers_to_us;

					break;
				case FIELD_TORRENT_PERCENT_DONE:
					// RPC v5
					// percentDone                 | double                      | tr_stat
					/**
					 * How much has been downloaded of the files the user wants. This differs
					 * from percentComplete if the user wants only some of the torrent's files.
					 * Range is [0..1]
					 */

					value = core_download.getStats().getPercentDoneExcludingDND()
							/ 1000.0f;

					break;
				case "pieces":
					// RPC v5
					value = torrentGet_pieces(core_download);
					break;
				case "pieceCount":
					// pieceCount                  | number                      | tr_info
					value = t.getPieceCount();

					break;
				case "pieceSize":
					// pieceSize                   | number                      | tr_info
					value = t.getPieceSize();

					break;
				case FIELD_TORRENT_PRIORITIES:

					value = torrentGet_priorities(download);

					break;
				case FIELD_TORRENT_POSITION:
					// RPC v14
					// "queuePosition"       | number     position of this torrent in its queue [0...n)

					value = core_download.getPosition();

					break;
				case FIELD_TORRENT_RATE_DOWNLOAD:
					// rateDownload (B/s)          | number                      | tr_stat
					value = stats.getDownloadAverage();

					break;
				case FIELD_TORRENT_RATE_UPLOAD:
					// rateUpload (B/s)            | number                      | tr_stat
					value = stats.getUploadAverage();

					break;
				case "recheckProgress":
					// recheckProgress             | double                      | tr_stat
					value = torrentGet_recheckProgress(core_download, stats);

					break;
				case FIELD_TORRENT_SECONDS_DOWNLOADING:
					// secondsDownloading          | number                      | tr_stat
					/** Cumulative seconds the torrent's ever spent downloading */
					value = stats.getSecondsDownloading();

					break;
				case FIELD_TORRENT_SECONDS_SEEDING:
					// secondsSeeding              | number                      | tr_stat
					/** Cumulative seconds the torrent's ever spent seeding */
					// TODO: Want "only seeding" time, or seeding time (including downloading time)? 
					value = stats.getSecondsOnlySeeding();

					break;
				case "seedIdleLimit":
					// RPC v10
					// "seedIdleLimit"       | number     torrent-level number of minutes of seeding inactivity
					value = (int) stats.getSecondsSinceLastUpload() / 60;

					break;
				case "seedIdleMode":
					// RPC v10: Not used, always TR_IDLELIMIT_GLOBAL
					// "seedIdleMode"        | number     which seeding inactivity to use.  See tr_inactvelimit
					value = TR_IDLELIMIT_GLOBAL;

					break;
				case "seedRatioLimit":
					// RPC v5
					// "seedRatioLimit"      | double     torrent-level seeding ratio

					value = COConfigurationManager.getFloatParameter("Stop Ratio");

					break;
				case "seedRatioMode":
					// RPC v5: Not used, always Global
					// seedRatioMode               | number                      | tr_ratiolimit
					value = TR_RATIOLIMIT_GLOBAL;

					break;
				case FIELD_TORRENT_SIZE_WHEN_DONE:
					// sizeWhenDone                | number                      | tr_stat
					/**
					 * Byte count of all the piece data we'll have downloaded when we're done,
					 * whether or not we have it yet. This may be less than tr_info.totalSize
					 * if only some of the torrent's files are wanted.
					 * [0...tr_info.totalSize] 
					 **/
					value = core_download.getStats().getSizeExcludingDND();

					break;
				case FIELD_TORRENT_DATE_STARTED:
					/** When the torrent was last started. */
					value = stats.getTimeStarted() / 1000;

					break;
				case FIELD_TORRENT_STATUS:

					value = torrentGet_status(download);

					break;
				case FIELD_TORRENT_IS_FORCED:
					
					value = download.isForceStart();
					
					break;
				case "trackers":

					String agent = MapUtils.getMapString(request.getHeaders(),
							"User-Agent", "");
					boolean hack = agent.contains("httpok"); // Torrnado

					value = torrentGet_trackers(core_download, hack);

					break;
				case "trackerStats":
					// RPC v7

					value = torrentGet_trackerStats(core_download);

					break;
				case "totalSize":

					value = t.getSize();

					break;
				case "torrentFile":
					// torrentFile                 | string                      | tr_info
					/** Path to torrent **/
					value = core_download.getTorrentFileName();

					break;
				case FIELD_TORRENT_UPLOADED_EVER:
					// uploadedEver                | number                      | tr_stat
					value = stats.getUploaded();

					break;
				case "uploadLimit":
				case TR_PREFS_KEY_USPEED_KBps:
					// RPC v5 (alternate is from 'set' prior to v5 -- added for rogue clients)

					/** maximum upload speed (KBps) */
					int bps = download.getUploadRateLimitBytesPerSecond();
					value = bps <= 0 ? bps : (bps < 1024 ? 1 : bps / 1024);

					break;
				case "uploadLimited":
				case TR_PREFS_KEY_USPEED_ENABLED:
					// RPC v5 (alternate is from 'set' prior to v5 -- added for rogue clients)

					/** true if "uploadLimit" is honored */
					value = download.getUploadRateLimitBytesPerSecond() > 0;

					break;
				case FIELD_TORRENT_UPLOAD_RATIO:
					// uploadRatio                 | double                      | tr_stat
					int shareRatio = stats.getShareRatio();
					value = shareRatio <= 0 ? shareRatio : stats.getShareRatio() / 1000.0;

					break;
				case FIELD_TORRENT_WANTED:

					value = torrentGet_wanted(download);

					break;
				case "webseeds":
					value = torrentGet_webSeeds(t);

					break;
				case "webseedsSendingToUs":
					value = torrentGet_webseedsSendingToUs(core_download);

					break;
				case "trackerSeeds": {
					// Vuze Specific?
					DownloadScrapeResult scrape = download.getLastScrapeResult();
					value = (long) (scrape == null ? 0 : scrape.getSeedCount());

					break;
				}
				case "trackerLeechers": {
					// Vuze Specific?
					DownloadScrapeResult scrape = download.getLastScrapeResult();
					value = (long) (scrape == null ? 0 : scrape.getNonSeedCount());

					break;
				}
				case "speedLimitDownload":
					// Vuze Specific?
					value = (long) download.getDownloadRateLimitBytesPerSecond();
					break;
				case "speedLimitUpload":
					// Vuze Specific?
					value = (long) download.getUploadRateLimitBytesPerSecond();
					break;
				case "seeders":
					// Removed in RPC v7
					value = pm == null ? -1 : pm.getNbSeeds();

					break;
				case "swarmSpeed":
					// Removed in RPC v7
					value = core_download.getStats().getTotalAveragePerPeer();
					break;
				case "announceResponse": {
					// Removed in RPC v7

					TRTrackerAnnouncer trackerClient = core_download.getTrackerClient();
					if (trackerClient != null) {
						value = trackerClient.getStatusString();
					} else {
						value = "";
					}

					break;
				}
				case "lastScrapeTime":
					// Unsure of wanted format
					// Removed in v7

					value = core_download.getTrackerTime();

					break;
				case "scrapeURL":
					// Removed in v7
					value = "";
					TRTrackerScraperResponse trackerScrapeResponse = core_download.getTrackerScrapeResponse();
					if (trackerScrapeResponse != null) {
						URL url = trackerScrapeResponse.getURL();
						if (url != null) {
							value = url.toString();
						}
					}

					break;
				case "nextAnnounceTime":
				case "nextScrapeTime": {
					// Removed in v7

					// Unsure of wanted format
					TRTrackerAnnouncer trackerClient = core_download.getTrackerClient();
					if (trackerClient != null) {
						value = trackerClient.getTimeUntilNextUpdate();
					} else {
						value = 0;
					}

					break;
				}
				case "downloadLimitMode":
				case "uploadLimitMode":
					// RPC < v5 -- Not supported -- ignore

					break;
				case "downloaders":
				case "lastAnnounceTime":
				case "scrapeResponse":
				case "timesCompleted":
					// RPC < v7 -- Not Supported -- ignore

					break;
				case "peersKnown":
					// RPC < v13 -- Not Supported -- ignore

					break;
				case FIELD_TORRENT_FILE_COUNT:
					// azRPC

					value = core_download.getNumFileInfos();

					break;
				case "speedHistory":
					// azRPC

					DownloadManagerStats core_stats = core_download.getStats();
					core_stats.setRecentHistoryRetention(true);

					// TODO
					// [0] send [1] receive [2] swarm
					int[][] recentHistory = core_stats.getRecentHistory();
					long now = SystemTime.getCurrentTime();

					long sinceSecs = getNumber(args.get("speedHistorySinceSecs"),
							0).longValue();

					long since = now - (sinceSecs * 1000);

					long curEntryTime = now - (recentHistory.length * 1000);

					List<Map> listHistory = new ArrayList<>();
					for (int[] ints : recentHistory) {
						if (curEntryTime > since) {
							Map<String, Object> mapHistory = new HashMap<>(3);
							mapHistory.put("timestamp", curEntryTime);
							mapHistory.put("upload", ints[0]);
							mapHistory.put("download", ints[1]);
							mapHistory.put("swarm", ints[2]);

							listHistory.add(mapHistory);
						}

						curEntryTime += 1000;
					}

					value = listHistory;

					/*
					 * [
					 *   {
					 *   	upload: <upload speed>
					 *   	download: <dl speed>
					 *   	swarm: <swarm avg speed>
					 *   }
					 * }
					 */
					break;
				case FIELD_TORRENT_TAG_UIDS: {
					// azRPC
					List<Long> listTags = new ArrayList<>();

					TagManager tm = TagManagerFactory.getTagManager();

					List<Tag> tags = tm.getTagsForTaggable(core_download);
					if (tags == null || tags.isEmpty()) {
						Category catAll = CategoryManager.getCategory(Category.TYPE_ALL);
						if (catAll != null) {
							listTags.add(catAll.getTagUID());
						}
						Category catUncat = CategoryManager.getCategory(
								Category.TYPE_UNCATEGORIZED);
						if (catUncat != null) {
							listTags.add(catUncat.getTagUID());
						}
					} else {
						for (Tag tag : tags) {
							listTags.add(tag.getTagUID());
						}
					}

					value = listTags;

					break;
				}
				
				case FIELD_TORRENT_SEQUENTIAL: {
					value = download.getFlag(Download.FLAG_SEQUENTIAL_DOWNLOAD);
					break;
				}
				
				default:
					if (plugin.trace_param.getValue()) {
						plugin.log("Unhandled get-torrent field: " + field);
					}
					break;
			}

			if (value != null) {

				if (xmlEscape && (value instanceof String)) {

					value = escapeXML((String) value);
				}
				torrent.put(field, value);
			}
		} // for fields		
	}

	private static void method_Torrent_Get_Stub(XMWebUIPlugin plugin,
			TrackerWebPageRequest request, Map args, List<String> fields,
			Map<Long, Map<String, Object>> torrent_info, DownloadStub download_stub,
			List<String> file_fields, boolean xmlEscape) {

		Map<String, Object> torrent = new LinkedHashMap<>();

		long download_id = plugin.getID(download_stub, true);

		torrent_info.put(download_id, torrent);

		boolean is_magnet_download = download_stub instanceof MagnetDownload;

		long status = 0;
		long error = TR_STAT_OK;
		String error_str = "";
		String created_by = "";
		long create_date = 0;
		float md_comp = 1.0f;

		if (is_magnet_download) {

			MagnetDownload md = (MagnetDownload) download_stub;

			TagManager tm = TagManagerFactory.getTagManager();

			Throwable e = md.getError();

			if (e == null) {

				status = 4;

				md_comp = 0.0f;

				if (fields.contains(FIELD_TORRENT_TAG_UIDS)) {
					List<Long> listTags = new ArrayList<>();
					Tag tag = getTagFromState(Download.ST_DOWNLOADING, false);
					if (tag != null) {
						listTags.add(tag.getTagUID());
					}
					// 7, "tag.type.ds.act"
					tag = tm.getTagType(TagType.TT_DOWNLOAD_STATE).getTag(7);
					if (tag != null) {
						listTags.add(tag.getTagUID());
					}

					torrent.put(FIELD_TORRENT_TAG_UIDS, listTags);
				}

			} else {

				status = 0;

				error = TR_STAT_LOCAL_ERROR;

				Throwable temp = e;

				while (temp.getCause() != null) {

					temp = temp.getCause();
				}

				String last_msg = temp.getMessage();

				if (last_msg != null && last_msg.length() > 0) {

					error_str = last_msg;

				} else {

					error_str = getCausesMesssages(e);
				}

				String magnet_url = md.getMagnetURL().toExternalForm();

				int pos = error_str.indexOf(magnet_url);

				// tidy up the most common error messages by removing magnet uri from them and
				// trimming prefix of 'Error:'

				if (pos != -1) {

					error_str = error_str.substring(0, pos)
							+ error_str.substring(pos + magnet_url.length());
				}

				error_str = error_str.trim();

				pos = error_str.indexOf("rror:");

				if (pos != -1) {

					error_str = error_str.substring(pos + 5).trim();
				}

				if (error_str.length() > 0) {

					// probably not great for right-left languages but derp

					error_str = Character.toUpperCase(error_str.charAt(0))
							+ error_str.substring(1);
				}

				if (fields.contains(FIELD_TORRENT_TAG_UIDS)) {
					List<Long> listTags = new ArrayList<>();
					Tag tag = getTagFromState(Download.ST_STOPPED, true);
					if (tag != null) {
						listTags.add(tag.getTagUID());
					}

					torrent.put(FIELD_TORRENT_TAG_UIDS, listTags);
				}
			}

			created_by = "Vuze";
			create_date = md.getCreateTime() / 1000;

		}

		long size = download_stub.getTorrentSize();

		//System.out.println( fields );

		// @formatter:off
		Object[][] stub_defs = {
				{ FIELD_TORRENT_DATE_ACTIVITY, 0 },
				{ "activityDateRelative",0 },
				{ FIELD_TORRENT_DATE_ADDED, is_magnet_download?create_date:0 },
				{ FIELD_TORRENT_COMMENT, is_magnet_download?"Metadata Download": "Download Archived" },
				{ FIELD_TORRENT_CORRUPT_EVER, 0 },
				{ FIELD_TORRENT_CREATOR, created_by },
				{ FIELD_TORRENT_DATE_CREATED, create_date },
				{ FIELD_TORRENT_DESIRED_AVAILABLE, 0 },
				//{ "downloadDir", "" },
				{ FIELD_TORRENT_DOWNLOADED_EVER, 0 },
				{ FIELD_TORRENT_ERROR, error },
				{ FIELD_TORRENT_ERROR_STRING, error_str },
				{ FIELD_TORRENT_ETA, TR_ETA_NOT_AVAIL },
				//{ "fileStats", "" },
				//{ "files", "" },
				//{ FIELD_TORRENT_HASH, "" },
				{ FIELD_TORRENT_HAVE_UNCHECKED, 0 },
				//{ "haveValid", "" },
				//{ "id", "" },
				{ "isFinished", !is_magnet_download },
				{ "isPrivate", false },
				{ "isStalled", false },
				{ FIELD_TORRENT_LEFT_UNTIL_DONE, is_magnet_download?size:0 },	// leftUntilDone is used to mark downloads as incomplete
				{ "metadataPercentComplete",md_comp },
				//{ "name", "" },
				{ FIELD_TORRENT_PEERS, new ArrayList() },
				{ "peersConnected", 0 },
				{ "peersGettingFromUs", 0 },
				{ "peersSendingToUs", "" },
				{ FIELD_TORRENT_PERCENT_DONE, is_magnet_download?0.0f:100.0f },
				{ "pieceCount", 1 },
				{ "pieceSize", size==0?1:size },
				{ FIELD_TORRENT_POSITION, 0 },
				{ FIELD_TORRENT_RATE_DOWNLOAD, 0 },
				{ FIELD_TORRENT_RATE_UPLOAD, 0 },
				{ "recheckProgress", 0.0f },
				{ "seedRatioLimit", 1.0f },
				{ "seedRatioMode", TR_RATIOLIMIT_GLOBAL },
				//{ "sizeWhenDone", "" },
				{ FIELD_TORRENT_DATE_STARTED, is_magnet_download?create_date:0 },
				{ FIELD_TORRENT_STATUS, status },
				//{ "totalSize", "" },
				{ "trackerStats", new ArrayList() },
				{ "trackers", new ArrayList() },
				{ FIELD_TORRENT_UPLOAD_RATIO, 0.0f },
				{ FIELD_TORRENT_UPLOADED_EVER, 0 },
				{ "webseedsSendingToUs", 0 },
				{ "torrentFile", "" }
		};
		// @formatter:on

		Map<String, Object> stub_def_map = new HashMap<>();

		for (Object[] d : stub_defs) {
			stub_def_map.put((String) d[0], d[1]);
		}

		for (String field : fields) {

			Object value = stub_def_map.get(field);

			switch (field) {
				case "id":

					value = download_id;

					break;
				case FIELD_TORRENT_DOWNLOAD_DIR:

					value = download_stub.getSavePath();

					break;
				case FIELD_TORRENT_FILES:

					String host = (String) request.getHeaders().get("host");

					value = torrentGet_files_stub(download_stub, download_id, file_fields,
							args);

					break;
				case FIELD_TORRENT_FILE_COUNT:

					value = download_stub.getStubFiles().length;

					break;
				case FIELD_TORRENT_FILESTATS:
					// RPC v5

					value = torrentGet_fileStats_stub(download_stub, file_fields, args);

					break;
				case FIELD_TORRENT_HASH_STRING:

					value = ByteFormatter.encodeString(download_stub.getTorrentHash());

					break;
				case FIELD_TORRENT_HAVE_VALID:

					value = is_magnet_download ? 0 : size;

					break;
				case FIELD_TORRENT_NAME:

					value = download_stub.getName();

					break;
				case FIELD_TORRENT_SIZE_WHEN_DONE:

					value = size;

					break;
				case "totalSize":

					value = size;

					break;
				case FIELD_TORRENT_TAG_UIDS:
					// azRPC
					//noinspection unchecked
					List<Long> listTags = MapUtils.getMapList(torrent, field,
							new ArrayList<>());

					TagManager tm = TagManagerFactory.getTagManager();

					if (listTags.size() == 0) {
						Tag tag = getTagFromState(Download.ST_STOPPED, !is_magnet_download);
						if (tag != null) {
							listTags.add(tag.getTagUID());
						}

						//  9, "tag.type.ds.inact"
						tag = tm.getTagType(TagType.TT_DOWNLOAD_STATE).getTag(9);
						if (tag != null) {
							listTags.add(tag.getTagUID());
						}
					}

					// 11, "tag.type.ds.incomp"
					// 10, incomplete
					Tag tag = tm.getTagType(TagType.TT_DOWNLOAD_STATE).getTag(
							is_magnet_download ? 11 : 10);
					if (tag != null) {
						listTags.add(tag.getTagUID());
					}

					Category catAll = CategoryManager.getCategory(Category.TYPE_ALL);
					if (catAll != null) {
						listTags.add(catAll.getTagUID());
					}
					Category catUncat = CategoryManager.getCategory(
							Category.TYPE_UNCATEGORIZED);
					if (catUncat != null) {
						listTags.add(catUncat.getTagUID());
					}

					value = listTags;
					break;
			}

			if (value != null) {

				if (xmlEscape && (value instanceof String)) {

					value = escapeXML((String) value);
				}

				torrent.put(field, value);

			} else {
				if (plugin.trace_param.getValue()) {
					plugin.log("Unknown stub field: " + field);
				}
			}
		}
	}

	/**
	 * The last time we uploaded or downloaded piece data on this torrent. 
	 */
	private static Object torrentGet_activityDate(DownloadManager download,
			boolean relative) {
		int state = download.getState();
		if (state == DownloadManager.STATE_SEEDING
				|| state == DownloadManager.STATE_DOWNLOADING) {
			int r = download.getStats().getTimeSinceLastDataReceivedInSeconds();
			int s = download.getStats().getTimeSinceLastDataSentInSeconds();
			long l;
			if (r > 0 && s > 0) {
				l = Math.min(r, s);
			} else if (r < 0) {
				l = s;
			} else {
				l = r;
			}
			if (relative) {
				return -l;
			}
			return (SystemTime.getCurrentTime() / 1000) - l;
		}

		DownloadManagerState downloadState = download.getDownloadState();
		long timestamp = downloadState.getLongParameter(
				DownloadManagerState.PARAM_DOWNLOAD_LAST_ACTIVE_TIME);
		if (timestamp != 0) {
			return timestamp / 1000;
		}

		timestamp = downloadState.getLongParameter(
				DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME);
		if (timestamp != 0) {
			return timestamp / 1000;
		}

		timestamp = downloadState.getLongParameter(
				DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME);
		if (timestamp != 0) {
			return timestamp / 1000;
		}

		return 0;
	}

	/** Defines what kind of text is in errorString. TR_STAT_* */
	private static Object torrentGet_error(DownloadManager core_download,
			Download download) {
		Object value;
		/** Defines what kind of text is in errorString. */
		String str = download.getErrorStateDetails();

		if (str != null && str.length() > 0) {
			value = TR_STAT_LOCAL_ERROR;
		} else {
			value = 0;
			TRTrackerAnnouncer tracker_client = core_download.getTrackerClient();

			if (tracker_client != null) {
				TRTrackerAnnouncerResponse x = tracker_client.getBestAnnouncer().getLastResponse();
				if (x != null) {
					if (x.getStatus() == TRTrackerAnnouncerResponse.ST_REPORTED_ERROR) {
						value = TR_STAT_TRACKER_ERROR;
					}
				}
			} else {
				DownloadScrapeResult x = download.getLastScrapeResult();
				if (x != null) {
					if (x.getResponseType() == DownloadScrapeResult.RT_ERROR) {
						String status = x.getStatus();

						if (status != null && status.length() > 0) {

							value = TR_STAT_TRACKER_ERROR;
						}
					}
				}
			}
		}
		return value;
	}

	private static Object torrentGet_errorString(DownloadManager core_download,
			Download download) {
		Object value;

		String str = download.getErrorStateDetails();

		if (str != null && str.length() > 0) {
			value = str;
		} else {
			value = "";
			TRTrackerAnnouncer tracker_client = core_download.getTrackerClient();

			if (tracker_client != null) {
				TRTrackerAnnouncerResponse x = tracker_client.getBestAnnouncer().getLastResponse();
				if (x != null) {
					if (x.getStatus() == TRTrackerAnnouncerResponse.ST_REPORTED_ERROR) {
						value = x.getStatusString();
					}
				}
			} else {
				DownloadScrapeResult x = download.getLastScrapeResult();
				if (x != null) {
					if (x.getResponseType() == DownloadScrapeResult.RT_ERROR) {
						value = x.getStatus();
					}
				}
			}
		}
		return value;
	}

	/**
	 * If downloading, estimated number of seconds left until the torrent is done.
	 * If seeding, estimated number of seconds left until seed ratio is reached. 
	 */
	private static Object torrentGet_eta(DownloadManager core_download,
			Download download, DownloadStats stats) {
		Object value;

		int state = download.getState();
		switch (state) {
			case Download.ST_DOWNLOADING:
				long eta_secs = core_download.getStats().getSmoothedETA();
				//long eta_secs = stats.getETASecs();

				if (eta_secs == -1) {
					value = TR_ETA_NOT_AVAIL;
				} else if (eta_secs >= 315360000000L) {
					value = TR_ETA_UNKNOWN;
				} else {
					value = eta_secs;
				}
				break;
			case Download.ST_SEEDING:
				// TODO: secs left until SR met
				value = TR_ETA_NOT_AVAIL;
				break;
			default:
				value = TR_ETA_NOT_AVAIL;
				break;
		}

		return value;
	}

	private static Object torrentGet_fileStats(Download download,
			List<String> file_fields, Map args) {
		// | a file's non-constant properties.    |
		// | array of tr_info.filecount objects,  |
		// | each containing:                     |
		// +-------------------------+------------+
		// | bytesCompleted          | number     | tr_torrent
		// | wanted                  | boolean    | tr_info
		// | priority                | number     | tr_info
		List<Map> stats_list = new ArrayList<>();

		DiskManagerFileInfo[] files = download.getDiskManagerFileInfo();

		for (DiskManagerFileInfo file : files) {
			Map<String, Object> map = new TreeMap<>();

			stats_list.add(map);

			torrentGet_fileStats(map, file_fields, file);
		}

		return stats_list;
	}

	private static void torrentGet_fileStats(Map<String, Object> map,
			List<String> sortedFields, DiskManagerFileInfo file) {
		boolean all = sortedFields == null || sortedFields.size() == 0;

		if (canAdd(FIELD_FILESTATS_BYTES_COMPLETED, sortedFields, all)) {
			map.put(FIELD_FILESTATS_BYTES_COMPLETED, file.getDownloaded());
		}
		if (canAdd(FIELD_FILESTATS_WANTED, sortedFields, all)) {
			map.put(FIELD_FILESTATS_WANTED, !file.isSkipped());
		}

		if (canAdd(FIELD_FILESTATS_PRIORITY, sortedFields, all)) {
			map.put(FIELD_FILESTATS_PRIORITY,
					convertVuzePriority(file.getNumericPriority()));
		}
	}

	private static Object torrentGet_fileStats_stub(DownloadStub download_stub,
			List<String> file_fields, Map args) {
		DownloadStubFile[] stubFiles = download_stub.getStubFiles();

		List<Map> stats_list = new ArrayList<>();

		for (DownloadStubFile stubFile : stubFiles) {

			Map<String, Object> map = new TreeMap<>();
			stats_list.add(map);

			torrentGet_fileStats_stub(map, null, stubFile);
		}

		return stats_list;
	}

	private static void torrentGet_fileStats_stub(Map<String, Object> map,
			List<String> sortedFields, DownloadStubFile sf) {
		long len = sf.getLength();

		boolean all = sortedFields == null || sortedFields.size() == 0;

		if (canAdd(FIELD_FILESTATS_BYTES_COMPLETED, sortedFields, all)) {

			long downloaded = len < 0 ? 0 : len;

			map.put(FIELD_FILESTATS_BYTES_COMPLETED, downloaded);
		}
		if (canAdd(FIELD_FILESTATS_WANTED, sortedFields, all)) {
			map.put(FIELD_FILESTATS_WANTED, len >= 0);
		}

		if (canAdd(FIELD_FILESTATS_PRIORITY, sortedFields, all)) {
			map.put(FIELD_FILESTATS_PRIORITY, convertVuzePriority(0));
		}
	}

	private static void torrentGet_file_stub(Map<String, Object> map,
			List<String> sortedFields, DownloadStubFile sf) {
		long len = sf.getLength();

		long downloaded;

		if (len < 0) {

			downloaded = 0;
			len = -len;

		} else {

			downloaded = len;
		}

		boolean all = sortedFields == null || sortedFields.size() == 0;

		if (canAdd(FIELD_FILESTATS_BYTES_COMPLETED, sortedFields, all)) {
			map.put(FIELD_FILESTATS_BYTES_COMPLETED, downloaded); // this must be a spec error...
		}
		if (canAdd(FIELD_FILES_LENGTH, sortedFields, all)) {
			map.put(FIELD_FILES_LENGTH, len);
		}
		if (canAdd(FIELD_FILES_NAME, sortedFields, all)) {
			map.put(FIELD_FILES_NAME, sf.getFile().getName());
		}
		if (sortedFields != null
				&& Collections.binarySearch(sortedFields, FIELD_FILES_FULL_PATH) >= 0) {
			map.put(FIELD_FILES_FULL_PATH, sf.getFile().toString());
		}
	}

	private static Object torrentGet_files(XMWebUIPlugin plugin,
			Map<String, Object> mapParent, String host, Download download,
			long download_id, List<String> file_fields, Map<String, Object> args) {
		// | array of objects, each containing:   |
		// +-------------------------+------------+
		// | bytesCompleted          | number     | tr_torrent
		// | length                  | number     | tr_info
		// | name                    | string     | tr_info
		// | index                   | number
		// | hc                      | number     | hashcode to be later used to suppress return of file map

		//noinspection RawTypeCanBeGeneric
		List file_list = new ArrayList<>();

		// Skip files that match these hashcodes
		List listHCs = getMapList(args, PREFIX_FILES_HC + download_id, ',', null);

		DiskManagerFileInfo[] files = download.getDiskManagerFileInfo();

		int[] file_indexes = getFileIndexes(args, download_id);

		boolean mapPerFile = MapUtils.getMapBoolean(args, "mapPerFile", true);

		String baseURL = MapUtils.getMapString(args, "base-url", null);

		boolean all = file_fields == null || file_fields.size() == 0;
		if (!all) {
			// sort so we can't use Collections.binarySearch
			Collections.sort(file_fields);
		}

		boolean didFileKeys = false;
		if (file_indexes == null || file_indexes.length == 0) {
			// We don't need to include the index if returning all files (file index request is null and there's no files-hc-*)
			boolean addIndex = !all
					|| (file_fields != null
							&& Collections.binarySearch(file_fields, FIELD_FILES_INDEX) >= 0)
					|| listHCs != null;

			for (int i = 0; i < files.length; i++) {
				DiskManagerFileInfo file = files[i];

				SortedMap<String, Object> map = new TreeMap<>();

				if (addIndex) {
					map.put(FIELD_FILES_INDEX, i);
				}

				torrentGet_files(plugin, map, file_fields, host, baseURL, download,
						file);
				if (file_fields != null && file_fields.size() > 0) {
					torrentGet_fileStats(map, file_fields, file);
				}

				if (mapPerFile) {
					//noinspection unchecked
					hashAndAdd(map, file_list, listHCs, i);
				} else {
					//noinspection unchecked
					if (hashAndAddAsCollection(map, file_list, listHCs, i)
							&& !didFileKeys) {
						mapParent.put("fileKeys", new ArrayList<>(map.keySet()));
						didFileKeys = true;
					}
				}
			}
		} else {
			for (int i = 0; i < file_indexes.length; i++) {
				int file_index = file_indexes[i];
				if (file_index < 0 || file_index >= files.length) {
					continue;
				}

				SortedMap<String, Object> map = new TreeMap<>();

				map.put(FIELD_FILES_INDEX, file_index);
				DiskManagerFileInfo fileInfo = files[file_index];
				torrentGet_fileStats(map, file_fields, fileInfo);
				torrentGet_files(plugin, map, file_fields, host, baseURL, download,
						fileInfo);

				if (mapPerFile) {
					//noinspection unchecked
					hashAndAdd(map, file_list, listHCs, i);
				} else {
					//noinspection unchecked
					if (hashAndAddAsCollection(map, file_list, listHCs, i)
							&& !didFileKeys) {
						mapParent.put("fileKeys", new ArrayList<>(map.keySet()));
						didFileKeys = true;
					}
				}
			}
		}

		return file_list;
	}

	private static void torrentGet_files(XMWebUIPlugin plugin,
			Map<String, Object> obj, List<String> sortedFields, String host,
			String baseURL, Download download, DiskManagerFileInfo file) {
		boolean all = sortedFields == null || sortedFields.size() == 0;
		File realFile = null;
		if (canAdd(FIELD_FILESTATS_BYTES_COMPLETED, sortedFields, all)) {
			obj.put(FIELD_FILESTATS_BYTES_COMPLETED, file.getDownloaded()); // this must be a spec error...
		}
		if (canAdd(FIELD_FILES_LENGTH, sortedFields, all)) {
			obj.put(FIELD_FILES_LENGTH, file.getLength());
		}
		if (canAdd(FIELD_FILES_NAME, sortedFields, all)) {
			Torrent torrent = download.getTorrent();
			boolean simpleTorrent = torrent != null && torrent.isSimpleTorrent();
			realFile = file.getFile(true);

			if (simpleTorrent) {
				obj.put(FIELD_FILES_NAME, realFile.getName());
			} else {
				String absolutePath = realFile.getAbsolutePath();
				String savePath = download.getSavePath();
				if (absolutePath.startsWith(savePath)) {
					// TODO: .dnd_az parent..
					//String dnd_sf = dm.getDownloadState().getAttribute( DownloadManagerState.AT_DND_SUBFOLDER );

					// + 1 to remove the dir separator
					obj.put(FIELD_FILES_NAME,
							absolutePath.substring(savePath.length() + 1));
				} else {
					obj.put(FIELD_FILES_NAME, absolutePath);
				}
			}
		}

		// Vuze specific, don't clutter transmission clients with these (they don't
		// have sortedFields param)
		if (sortedFields != null) {
			boolean showAllVuze = sortedFields.size() == 0;

			if (canAdd(FIELD_FILES_CONTENT_URL, sortedFields, showAllVuze)) {
				String s = "";
				URL f_stream_url = PlayUtils.getMediaServerContentURL(file);
				if (f_stream_url != null) {
					s = adjustURL(host, f_stream_url);
					if (baseURL != null && s.startsWith(baseURL)) {
						s = s.substring(baseURL.length());
					}
				}
				obj.put(FIELD_FILES_CONTENT_URL, s);
			}

			if (canAdd(FIELD_FILES_FULL_PATH, sortedFields, showAllVuze)) {
				if (realFile == null) {
					realFile = file.getFile(true);
				}
				obj.put(FIELD_FILES_FULL_PATH, realFile.toString());
			}
			
			if (canAdd("eta", sortedFields, showAllVuze)) {
				long eta = -1;
				try {
					com.biglybt.core.disk.DiskManagerFileInfo coreFileInfo = PluginCoreUtils.unwrap(file);
					eta = coreFileInfo.getETA();
				} catch (DownloadException e) {
				}
				obj.put("eta", eta);
			}
		}

		if (realFile != null) {
			synchronized (plugin.referenceKeeper) {
				plugin.referenceKeeper.put(SystemTime.getCurrentTime(), realFile);
			}
		}
	}

	private static Object torrentGet_files_stub(DownloadStub download_stub,
			long download_id, List<String> file_fields, Map args) {
		DownloadStubFile[] stubFiles = download_stub.getStubFiles();

		List<Map> file_list = new ArrayList<>();

		// Skip files that match these hashcodes
		List listHCs = getMapList(args, PREFIX_FILES_HC + download_id, ',', null);

		int[] file_indexes = getFileIndexes(args, download_id);

		if (file_indexes == null || file_indexes.length == 0) {

			for (int i = 0; i < stubFiles.length; i++) {
				DownloadStubFile sf = stubFiles[i];

				SortedMap<String, Object> map = new TreeMap<>();

				map.put(FIELD_FILES_INDEX, i);

				torrentGet_file_stub(map, file_fields, sf);
				if (file_fields != null && file_fields.size() > 0) {
					torrentGet_fileStats_stub(map, file_fields, sf);
				}

				hashAndAdd(map, file_list, listHCs, i);
			}
		} else {
			for (int i = 0; i < file_indexes.length; i++) {
				int file_index = file_indexes[i];
				if (file_index < 0 || file_index >= stubFiles.length) {
					continue;
				}

				SortedMap<String, Object> map = new TreeMap<>();

				file_list.add(map);

				map.put(FIELD_FILES_INDEX, file_index);
				DownloadStubFile file = stubFiles[file_index];
				torrentGet_fileStats_stub(map, file_fields, file);
				torrentGet_file_stub(map, file_fields, file);

				hashAndAdd(map, file_list, listHCs, i);
			}
		}

		return file_list;
	}

	/**
	 * True if the torrent is running, but has been idle for long enough
	 * to be considered stalled.
	 */
	private static Object torrentGet_isStalled(Download download) {
		Object value = false;
		int state = download.getState();
		if (state == Download.ST_SEEDING || state == Download.ST_DOWNLOADING) {
			DefaultRankCalculator calc = StartStopRulesDefaultPlugin.getRankCalculator(
					download);
			if (calc != null) {
				value = (state == Download.ST_SEEDING && !calc.getActivelySeeding())
						|| (state == Download.ST_DOWNLOADING
								&& !calc.getActivelyDownloading());
			}
		}
		return value;
	}

	/**
	 * time when one or more of the torrent's trackers will
	 * allow you to manually ask for more peers,
	 * or 0 if you can't 
	 */
	private static Object torrentGet_manualAnnounceTime(DownloadManager manager) {
		// See ScrapeInfoView's updateButton logic
		Object value;
		TRTrackerAnnouncer trackerClient = manager.getTrackerClient();
		if (trackerClient != null) {

			value = Math.max(SystemTime.getCurrentTime() / 1000,
					trackerClient.getLastUpdateTime()
							+ TRTrackerAnnouncer.REFRESH_MINIMUM_SECS);

		} else {
			// Technically the spec says "ask for more peers" which suggests
			// we don't need to handle scrape -- but let's do it anyway

			TRTrackerScraperResponse sr = manager.getTrackerScrapeResponse();

			if (sr == null) {

				value = 0;

			} else {

				value = Math.max(SystemTime.getCurrentTime() / 1000,
						sr.getScrapeStartTime() / 1000
								+ TRTrackerScraper.REFRESH_MINIMUM_SECS);
			}
		}

		return value;
	}

	private static List torrentGet_peers(DownloadManager core_download,
			List<String> sortedFields) {
		// peers              | array of objects, each containing:   |
		// +-------------------------+------------+
		// | address                 | string     | tr_peer_stat | x
		// | clientName              | string     | tr_peer_stat | x
		// | clientIsChoked          | boolean    | tr_peer_stat | x
		// | clientIsInterested      | boolean    | tr_peer_stat | x
		// | flagStr                 | string     | tr_peer_stat | partial
		// | isDownloadingFrom       | boolean    | tr_peer_stat | x
		// | isEncrypted             | boolean    | tr_peer_stat | ?
		// | isIncoming              | boolean    | tr_peer_stat | x
		// | isUploadingTo           | boolean    | tr_peer_stat | x
		// | isUTP                   | boolean    | tr_peer_stat | x
		// | peerIsChoked            | boolean    | tr_peer_stat | x
		// | peerIsInterested        | boolean    | tr_peer_stat | x
		// | port                    | number     | tr_peer_stat | x
		// | progress                | double     | tr_peer_stat | x
		// | rateToClient (B/s)      | number     | tr_peer_stat | x
		// | rateToPeer (B/s)        | number     | tr_peer_stat | x

		List<Map> peers = new ArrayList<>();

		if (core_download == null) {
			return peers;
		}

		PEPeerManager pm = core_download.getPeerManager();
		if (pm == null) {
			return peers;
		}

		boolean all = sortedFields == null || sortedFields.size() == 0;

		List<PEPeer> peerList = pm.getPeers();
		for (PEPeer peer : peerList) {
			Map<String, Object> map = new HashMap<>();
			peers.add(map);

			final PEPeerStats stats = peer.getStats();
			boolean isDownloadingFrom = peer.isDownloadPossible()
					&& stats.getDataReceiveRate() > 0;
			// TODO FIX
			// peer.connection.getTransport().isEncrypted
			String encryption = peer.getEncryption();
			boolean isEncrypted = encryption != null && !encryption.startsWith("None")
					&& !encryption.startsWith("Plain");
			boolean isUTP = peer.getProtocol().equals("uTP");
			boolean isUploadingTo = stats.getDataSendRate() > 0;

			if (canAdd(FIELD_PEERS_ADDRESS, sortedFields, all)) {
				map.put(FIELD_PEERS_ADDRESS, peer.getIp());
			}
			if (canAdd(FIELD_PEERS_CLIENT_NAME, sortedFields, all)) {
				map.put(FIELD_PEERS_CLIENT_NAME, peer.getClient());
			}
			if (canAdd(FIELD_PEERS_CLIENT_CHOKED, sortedFields, all)) {
				map.put(FIELD_PEERS_CLIENT_CHOKED, peer.isChokedByMe());
			}
			if (canAdd(FIELD_PEERS_CLIENT_INTERESTED, sortedFields, all)) {
				map.put(FIELD_PEERS_CLIENT_INTERESTED, peer.isInterested());
			}

			if (canAdd("state", sortedFields, all)) {
				map.put("state", peer.getPeerState());
			}

			if (canAdd("source", sortedFields, all)) {
				map.put("source", peer.getPeerSource());
			}

			if (canAdd(FIELD_PEERS_FLAGSTR, sortedFields, all)) {
				// flagStr
				// "O": "Optimistic unchoke"
				// +"D": "Downloading from this peer"
				// +"d": "We would download from this peer if they'd let us"
				// +"U": "Uploading to peer"
				// "u": "We would upload to this peer if they'd ask"
				// +"K": "Peer has unchoked us, but we're not interested"
				// +"?": "We unchoked this peer, but they're not interested"
				// +"E": "Encrypted Connection"
				// +"H": "Peer was discovered through Distributed Hash Table (DHT)"
				// +"X": "Peer was discovered through Peer Exchange (PEX)"
				// +"I": "Peer is an incoming connection"
				// +"T": "Peer is connected via uTP"
				//TODO
				StringBuilder flagStr = new StringBuilder();

				if (isDownloadingFrom) {
					flagStr.append('D');
				} else if (peer.isDownloadPossible()) {
					flagStr.append("d");
				}
				if (isUploadingTo) {
					flagStr.append("U");
				}
				if (!peer.isChokingMe() && !peer.isInteresting()) {
					flagStr.append("K");
				}
				if (!peer.isChokedByMe() && !peer.isInterested()) {
					flagStr.append("?");
				}
				if (isEncrypted) {
					flagStr.append("E");
				}
				String source = peer.getPeerSource();
				switch (source) {
					case PEPeerSource.PS_DHT:
						flagStr.append('H');
						break;
					case PEPeerSource.PS_OTHER_PEER:
						flagStr.append('X');
						break;
					case PEPeerSource.PS_BT_TRACKER: // XXX PS_INCOMING
						flagStr.append('I');
						break;
				}
				if (isUTP) {
					flagStr.append("T");
				}

				map.put(FIELD_PEERS_FLAGSTR, flagStr.toString());
			}

			if (canAdd(FIELD_PEERS_CC, sortedFields, all)) {
				// code, name
				String[] countryDetails = PeerUtils.getCountryDetails(peer);
				map.put(FIELD_PEERS_CC,
						countryDetails != null && countryDetails.length > 0
								? countryDetails[0] : "");
			}

			if (canAdd(FIELD_PEERS_IS_DLING_FROM, sortedFields, all)) {
				map.put(FIELD_PEERS_IS_DLING_FROM, isDownloadingFrom);
			}
			if (canAdd(FIELD_PEERS_IS_ENCRYPTED, sortedFields, all)) {
				map.put(FIELD_PEERS_IS_ENCRYPTED, isEncrypted);
			}
			if (canAdd(FIELD_PEERS_IS_INCOMING, sortedFields, all)) {
				map.put(FIELD_PEERS_IS_INCOMING, peer.isIncoming());
			}
			if (canAdd(FIELD_PEERS_IS_ULING_TO, sortedFields, all)) {
				map.put(FIELD_PEERS_IS_ULING_TO, isUploadingTo);
			}
			// RPC v13
			if (canAdd(FIELD_PEERS_IS_UTP, sortedFields, all)) {
				map.put(FIELD_PEERS_IS_UTP, isUTP);
			}
			if (canAdd(FIELD_PEERS_PEER_CHOKED, sortedFields, all)) {
				map.put(FIELD_PEERS_PEER_CHOKED, peer.isChokingMe());
			}
			if (canAdd(FIELD_PEERS_PEER_INTERESTED, sortedFields, all)) {
				map.put(FIELD_PEERS_PEER_INTERESTED, peer.isInteresting());
			}
			// RPC v3
			if (canAdd(FIELD_PEERS_PORT, sortedFields, all)) {
				map.put(FIELD_PEERS_PORT, peer.getPort());
			}
			if (canAdd(FIELD_PEERS_PROGRESS, sortedFields, all)) {
				map.put(FIELD_PEERS_PROGRESS,
						peer.getPercentDoneInThousandNotation() / 1000.0);
			}
			if (canAdd(FIELD_PEERS_RATE_TO_CLIENT_BPS, sortedFields, all)) {
				map.put(FIELD_PEERS_RATE_TO_CLIENT_BPS, stats.getDataReceiveRate());
			}
			if (canAdd(FIELD_PEERS_RATE_TO_PEER_BPS, sortedFields, all)) {
				map.put(FIELD_PEERS_RATE_TO_PEER_BPS, stats.getDataSendRate());
			}
		}

		return peers;
	}

	private static Object torrentGet_peersFrom(PEPeerManager pm) {
		// peersFrom          | an object containing:                |
		// +-------------------------+------------+
		// | fromCache               | number     | tr_stat
		// | fromDht                 | number     | tr_stat
		// | fromIncoming            | number     | tr_stat
		// | fromLpd                 | number     | tr_stat
		// | fromLtep                | number     | tr_stat
		// | fromPex                 | number     | tr_stat
		// | fromTracker             | number     | tr_stat

		Map<String, Long> mapPeersFrom = new HashMap<>();

		if (pm == null) {
			return mapPeersFrom;
		}

		List<PEPeer> peers = pm.getPeers();

		for (PEPeer peer : peers) {

			String peerSource = peer.getPeerSource();
			if (peerSource != null) {
				switch (peerSource) {
					case PEPeerSource.PS_BT_TRACKER:
						peerSource = "fromTracker";
						break;
					case PEPeerSource.PS_DHT:
						peerSource = "fromDht";
						break;
					case PEPeerSource.PS_INCOMING:
						peerSource = "fromIncoming";
						break;
					case PEPeerSource.PS_OTHER_PEER:
						peerSource = "fromPex";
						break;
					case PEPeerSource.PS_PLUGIN:
						// TODO: better cat?
						peerSource = "fromCache";
						break;
					default:
						peerSource = "fromCache";
						break;
				}
				if (!mapPeersFrom.containsKey(peerSource)) {
					mapPeersFrom.put(peerSource, 1L);
				} else {
					mapPeersFrom.put(peerSource, mapPeersFrom.get(peerSource) + 1);
				}
			}
		}

		return mapPeersFrom;
	}

	private static Object torrentGet_pieces(DownloadManager core_download) {
		Object value = "";

		// TODO: No idea if this works
		// pieces | string             
		// | A bitfield holding pieceCount flags  | tr_torrent
		// | which are set to 'true' if we have   |
		// | the piece matching that position.    |
		// | JSON doesn't allow raw binary data,  |
		// | so this is a base64-encoded string.  |

		DiskManager dm = core_download.getDiskManager();

		if (dm != null) {
			DiskManagerPiece[] pieces = dm.getPieces();
			byte[] bits = new byte[(int) Math.ceil(pieces.length / 8.0f)];
			int pieceNo = 0;
			int bitPos = 0;
			while (pieceNo < pieces.length) {

				bits[bitPos] = 0;
				for (int i = 0; pieceNo < pieces.length && i < 8; i++) {
					boolean done = pieces[pieceNo].isDone();

					if (done) {
						bits[bitPos] |= (byte) (1 << i);
					}

					pieceNo++;
				}

				bitPos++;
			}
			try {
				value = new String(Base64.encode(bits), "UTF8");
			} catch (UnsupportedEncodingException e) {
			}
		}
		return value;
	}

	private static Object torrentGet_priorities(Download download) {
		// | an array of tr_info.filecount        | tr_info
		// | numbers. each is the tr_priority_t   |
		// | mode for the corresponding file.     |
		List<Long> list = new ArrayList<>();

		DiskManagerFileInfo[] fileInfos = download.getDiskManagerFileInfo();

		for (DiskManagerFileInfo fileInfo : fileInfos) {
			int priority = fileInfo.getNumericPriority();
			long newPriority = convertVuzePriority(priority);
			list.add(newPriority);
		}

		return list;
	}

	/**
	 * When tr_stat.activity is TR_STATUS_CHECK or TR_STATUS_CHECK_WAIT,
	 * this is the percentage of how much of the files has been 
	 * verified. When it gets to 1, the verify process is done.
	 * Range is [0..1]
	 **/
	private static Object torrentGet_recheckProgress(
			DownloadManager core_download, DownloadStats stats) {
		double x = 1;

		if (core_download.getState() == DownloadManager.STATE_CHECKING) {

			DiskManager dm = core_download.getDiskManager();

			if (dm != null) {

				x = ((double) stats.getCompleted()) / 1000;
			}
		}

		return x;
	}

	private static Object torrentGet_status(Download download) {
		// 1 - waiting to verify
		// 2 - verifying
		// 4 - downloading
		// 5 - queued (incomplete)
		// 8 - seeding
		// 9 - queued (complete)
		// 16 - paused

		// 2.71 - these changed!

		//TR_STATUS_STOPPED        = 0, /* Torrent is stopped */
		//TR_STATUS_CHECK_WAIT     = 1, /* Queued to check files /*
		//TR_STATUS_CHECK          = 2, /* Checking files */
		//TR_STATUS_DOWNLOAD_WAIT  = 3, /* Queued to download */
		//TR_STATUS_DOWNLOAD       = 4, /* Downloading */
		//TR_STATUS_SEED_WAIT      = 5, /* Queued to seed */
		//TR_STATUS_SEED           = 6  /* Seeding */

		final int CHECK_WAIT;
		final int CHECKING;
		final int DOWNLOADING;
		final int QUEUED_INCOMPLETE;
		final int QUEUED_COMPLETE;
		final int STOPPED;
		final int SEEDING;
		final int ERROR;

		boolean RPC_14_OR_HIGHER = true; // fields.contains( "percentDone" );

		if (RPC_14_OR_HIGHER) {

			CHECK_WAIT = 1;
			CHECKING = 2;
			DOWNLOADING = 4;
			QUEUED_INCOMPLETE = 3;
			QUEUED_COMPLETE = 5;
			STOPPED = 0;
			SEEDING = 6;
			ERROR = STOPPED;
		} else {
			CHECK_WAIT = 1;
			CHECKING = 2;
			DOWNLOADING = 4;
			QUEUED_INCOMPLETE = 5;
			QUEUED_COMPLETE = 9;
			STOPPED = 16;
			SEEDING = 8;
			ERROR = 0;
		}

		int status_int;

		int state = download.getState();

		switch (state) {
			case Download.ST_DOWNLOADING:

				status_int = DOWNLOADING;

				break;
			case Download.ST_SEEDING:

				status_int = SEEDING;

				break;

			case Download.ST_READY:
			case Download.ST_QUEUED:

				if (download.isComplete()) {

					status_int = QUEUED_COMPLETE;

				} else {

					status_int = QUEUED_INCOMPLETE;
				}
				break;
			case Download.ST_STOPPED:
			case Download.ST_STOPPING:

				status_int = STOPPED;

				break;
			case Download.ST_ERROR:

				status_int = ERROR;

				break;
			default:
				// ST_WAITING, ST_PREPARING

				DownloadManager core_download = PluginCoreUtils.unwrap(download);
				if (core_download.getState() == DownloadManager.STATE_CHECKING) {

					status_int = CHECKING;

				} else {

					status_int = CHECK_WAIT;
				}
				break;
		}

		return status_int;
	}

	private static Object torrentGet_trackerStats(DownloadManager core_download) {
		List<Map> tracker_stats = new ArrayList<>();

		List<TrackerPeerSource> trackerPeerSources = core_download.getTrackerPeerSources();

		if (trackerPeerSources == null) {
			return tracker_stats;
		}

		for (TrackerPeerSource tps : trackerPeerSources) {
			String statusString = tps.getStatusString();
			if (statusString == null) {
				statusString = "";
			}

			Map<String, Object> map = new HashMap<>(64);

			/* how many downloads this tracker knows of (-1 means it does not know) */
			map.put("downloadCount", -1); // TODO

			/* whether or not we've ever sent this tracker an announcement */
			map.put("hasAnnounced", tps.getPeers() >= 0); // TODO

			/* whether or not we've ever scraped to this tracker */
			map.put("hasScraped", false); // todo: bool);

			String name = "";
			try {
				name = tps.getName();
			} catch (Exception e) {
				// NPE at com.aelitis.azureus.pif.extseed.ExternalSeedPlugin$5.getName(ExternalSeedPlugin.java:561
			}

			String host = name;

			int type = tps.getType();
			if (type == TrackerPeerSource.TP_TRACKER) {
				URL url = tps.getURL();
				if (url != null) {
					host = url.getHost();
				}
			} else {
				final String[] js_resource_keys = {
						"SpeedView.stats.unknown",
						"label.tracker",
						"wizard.webseed.title",
						"tps.type.dht",
						"ConfigView.section.transfer.lan",
						"tps.type.pex",
						"tps.type.incoming",
						"tps.type.plugin",
				};
				if (type >= 0 && type < js_resource_keys.length) {
					host = MessageText.getString(js_resource_keys[type]);
				}
			}

			/* human-readable string identifying the tracker */
			map.put("host", host); // TODO

			/* the full announce URL */
			map.put("announce", name); // TODO

			/* the full scrape URL */
			map.put("scrape", name); // TODO

			/* Transmission uses one tracker per tier,
			 * and the others are kept as backups */
			map.put("isBackup", false); // TODO

			/* is the tracker announcing, waiting, queued, etc */
			int status = tps.getStatus();
			int state;
			if (status == tps.ST_AVAILABLE || status == tps.ST_ONLINE) {
				state = TR_TRACKER_WAITING;
			} else if (status == tps.ST_UPDATING) {
				state = TR_TRACKER_ACTIVE;
			} else if (status == tps.ST_QUEUED) {
				state = TR_TRACKER_QUEUED;
			} else {
				state = TR_TRACKER_INACTIVE;
			}
			map.put("announceState", state);

			/* is the tracker scraping, waiting, queued, etc */
			map.put("scrapeState", state);

			/* number of peers the tracker told us about last time.
			 * if "lastAnnounceSucceeded" is false, this field is undefined */
			map.put("lastAnnouncePeerCount", tps.getPeers());

			/* human-readable string with the result of the last announce.
			   if "hasAnnounced" is false, this field is undefined */
			if (statusString != null) {
				map.put("lastAnnounceResult", statusString);
			}

			/* when the last announce was sent to the tracker.
			 * if "hasAnnounced" is false, this field is undefined */
			map.put("lastAnnounceStartTime", 0); // TODO: time_t);

			/* whether or not the last announce was a success.
			   if "hasAnnounced" is false, this field is undefined */
			map.put("lastAnnounceSucceeded", tps.getPeers() >= 0);

			/* whether or not the last announce timed out. */
			map.put("lastAnnounceTimedOut", false); // TODO

			/* when the last announce was completed.
			   if "hasAnnounced" is false, this field is undefined */
			map.put("lastAnnounceTime", 0); // TODO: time_t);

			/* human-readable string with the result of the last scrape.
			 * if "hasScraped" is false, this field is undefined */
			if (statusString != null) {
				map.put("lastScrapeResult", statusString);
			}

			/* when the last scrape was sent to the tracker.
			 * if "hasScraped" is false, this field is undefined */
			map.put("lastScrapeStartTime", 0); // TODO: time_t);

			/* whether or not the last scrape was a success.
			   if "hasAnnounced" is false, this field is undefined */
			map.put("lastScrapeSucceeded", tps.getPeers() >= 0);

			/* whether or not the last scrape timed out. */
			map.put("lastScrapeTimedOut", false); // TODO: bool);

			/* when the last scrape was completed.
			   if "hasScraped" is false, this field is undefined */
			map.put("lastScrapeTime", 0); // TODO: time_t);

			/* number of leechers this tracker knows of (-1 means it does not know) */
			map.put("leecherCount", tps.getLeecherCount());

			/* when the next periodic announce message will be sent out.
			   if announceState isn't TR_TRACKER_WAITING, this field is undefined */
			map.put("nextAnnounceTime", 0); // TODO: time_t);

			/* when the next periodic scrape message will be sent out.
			   if scrapeState isn't TR_TRACKER_WAITING, this field is undefined */
			map.put("nextScrapeTime", 0); // TODO: time_t);

			/* number of seeders this tracker knows of (-1 means it does not know) */
			map.put("seederCount", tps.getSeedCount());

			/* which tier this tracker is in */
			map.put("tier", 0); // TODO: int);

			/* used to match to a tr_tracker_info */
			map.put("id", tps.hashCode());

			tracker_stats.add(map);
		}

		return tracker_stats;
	}

	private static Object torrentGet_trackers(DownloadManager core_download,
			boolean hack) {
		List<Map> trackers = new ArrayList<>();

		List<TrackerPeerSource> trackerPeerSources = core_download.getTrackerPeerSources();

		if (trackerPeerSources == null) {
			return trackers;
		}

		for (TrackerPeerSource tps : trackerPeerSources) {
			String statusString = tps.getStatusString();
			if (statusString == null) {
				statusString = "";
			}

			Map<String, Object> map = new HashMap<>();
			//trackers           | array of objects, each containing:   |
			//+-------------------------+------------+
			//| announce                | string     | tr_tracker_info
			//| id                      | number     | tr_tracker_info
			//| scrape                  | string     | tr_tracker_info
			//| tier                    | number     | tr_tracker_info

			String name;
			try {
				name = tps.getName();
			} catch (Exception e) {
				name = tps.getClass().getSimpleName();
				// NPE at com.aelitis.azureus.pif.extseed.ExternalSeedPlugin$5.getName(ExternalSeedPlugin.java:561
			}

			if (hack && !name.contains("://")) {
				name = "://" + name;
			}

			map.put("id", tps.hashCode());
			/* the full announce URL */
			map.put("announce", name); // TODO
			/* the full scrape URL */
			map.put("scrape", name); // TODO
			/* which tier this tracker is in */
			map.put("tier", 0); // TODO: int);

			trackers.add(map);
		}

		return trackers;
	}

	private static Object torrentGet_wanted(Download download) {
		// wanted             
		// | an array of tr_info.fileCount        | tr_info
		// | 'booleans' true if the corresponding |
		// | file is to be downloaded.            |
		List<Object> list = new ArrayList<>();

		DiskManagerFileInfo[] files = download.getDiskManagerFileInfo();

		for (DiskManagerFileInfo file : files) {
			list.add(!file.isSkipped());
		}

		return list;
	}

	private static Object torrentGet_webSeeds(Torrent t) {
		// webseeds           
		// | an array of strings:                 |
		// +-------------------------+------------+
		// | webseed                 | string     | tr_info
		List getright = BDecoder.decodeStrings(getURLList(t, "url-list"));
		List webseeds = BDecoder.decodeStrings(getURLList(t, "httpseeds"));

		List<String> list = new ArrayList<>();
		for (List l : new List[] {
			getright,
			webseeds
		}) {

			for (Object o : l) {
				if (o instanceof String) {
					list.add((String) o);
				}
			}
		}
		return list;
	}

	/** Number of webseeds that are sending data to us. */
	private static Object torrentGet_webseedsSendingToUs(
			DownloadManager core_download) {
		PEPeerManager peerManager = core_download.getPeerManager();
		if (peerManager == null) {
			return 0;
		}
		int numWebSeedsConnected = 0;
		List<PEPeer> peers = peerManager.getPeers();
		for (PEPeer peer : peers) {
			if (peer.getProtocol().toLowerCase().startsWith("http")) {
				numWebSeedsConnected++;
			}
		}
		return numWebSeedsConnected;
	}
}
