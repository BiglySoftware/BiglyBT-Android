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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.metasearch.Engine;
import com.biglybt.core.tag.*;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFactory;
import com.biglybt.core.util.*;
import com.biglybt.core.vuzefile.VuzeFile;
import com.biglybt.core.vuzefile.VuzeFileComponent;
import com.biglybt.core.vuzefile.VuzeFileHandler;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.torrent.TorrentImpl;
import com.biglybt.util.MapUtils;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.*;
import com.biglybt.pif.torrent.*;

import static com.aelitis.azureus.plugins.xmwebui.StaticUtils.*;
import static com.aelitis.azureus.plugins.xmwebui.TransmissionVars.*;

public class TorrentMethods
{
	XMWebUIPlugin plugin;

	private final PluginInterface pi;

	protected TorrentMethods(XMWebUIPlugin plugin, PluginInterface pi) {
		this.plugin = plugin;
		this.pi = pi;
	}

	public void add(List<MagnetDownload> magnet_downloads, final Map args,
			Map result, boolean xmlEscape)

			throws IOException, DownloadException, TextualException {
		/*
		   Request arguments:
		
		   key                  | value type & description
		   ---------------------+-------------------------------------------------
		   "cookies"            | string      pointer to a string of one or more cookies.
		   "download-dir"       | string      path to download the torrent to
		   "filename"           | string      filename or URL of the .torrent file
		   "metainfo"           | string      base64-encoded .torrent content
		   "paused"             | boolean     if true, don't start the torrent
		   "peer-limit"         | number      maximum number of peers
		   "bandwidthPriority"  | number      torrent's bandwidth tr_priority_t 
		   "files-wanted"       | array       indices of file(s) to download
		   "files-unwanted"     | array       indices of file(s) to not download
		   "priority-high"      | array       indices of high-priority file(s)
		   "priority-low"       | array       indices of low-priority file(s)
		   "priority-normal"    | array       indices of normal-priority file(s)
		
		   Either "filename" OR "metainfo" MUST be included.
		   All other arguments are optional.
		
		 	additional vuze specific parameters
			
			 "vuze_category"	| string (optional category name)
			 "vuze_tags"		| array  (optional list of tags)
			 "name"	        | string (optional friendly name to use instead of url/hash)
			 
		   The format of the "cookies" should be NAME=CONTENTS, where NAME is the
		   cookie name and CONTENTS is what the cookie should contain.
		   Set multiple cookies like this: "name1=content1; name2=content2;" etc. 
		   <http://curl.haxx.se/libcurl/c/curl_easy_setopt.html#CURLOPTCOOKIE>
		
		   Response arguments: on success, a "torrent-added" object in the
		                       form of one of 3.3's tr_info objects with the
		                       fields for id, name, and hashString.
		 */
		plugin.checkUpdatePermissions();

		Map<String, Object> mapTorrent = null;

		if (args.containsKey("metainfo")) {

			// .remove to increase chances of metainfo being GC'd if needed
			byte[] metainfoBytes = decodeBase64(((String) args.remove("metainfo")));

			BDecoder decoder = new BDecoder();
			decoder.setVerifyMapOrder(true);
			mapTorrent = decoder.decodeByteArray(metainfoBytes);

			VuzeFileHandler vfh = VuzeFileHandler.getSingleton();

			if (vfh != null) {

				VuzeFile vf = vfh.loadVuzeFile(mapTorrent);

				if (vf != null) {

					VuzeFileComponent[] comps = vf.getComponents();

					for (VuzeFileComponent comp : comps) {

						if (comp.getType() != VuzeFileComponent.COMP_TYPE_METASEARCH_TEMPLATE) {

							throw (new TextualException(
									"Unsupported Vuze File component type: "
											+ comp.getTypeName()));
						}
					}

					vfh.handleFiles(new VuzeFile[] {
						vf
					}, VuzeFileComponent.COMP_TYPE_METASEARCH_TEMPLATE);

					String added_templates = "";

					for (VuzeFileComponent comp : comps) {

						if (comp.isProcessed()) {

							Engine e = (Engine) comp.getData(
									Engine.VUZE_FILE_COMPONENT_ENGINE_KEY);

							if (e != null) {

								added_templates += (added_templates.isEmpty() ? "" : ", ")
										+ e.getName();
							}
						}
					}

					if (added_templates.length() == 0) {

						throw (new TextualException("No search template(s) added"));

					} else {

						throw (new TextualException(
								"Installed search template(s): " + added_templates));
					}
				}
			}
		}

		Torrent torrent = null;
		DownloadStub download = null;

		String url = (String) args.get("filename");

		final boolean add_stopped = getBoolean(args.get("paused"));

		String download_dir = (String) args.get(TR_PREFS_KEY_DOWNLOAD_DIR);

		final File file_Download_dir = download_dir == null ? null
				: FileUtil.newFile(download_dir);

		// peer-limit not used
		//getNumber(args.get("peer-limit"), 0);

		// bandwidthPriority not used
		//getNumber(args.get("bandwidthPriority"), TR_PRI_NORMAL);

		final DownloadWillBeAddedListener add_listener = addedDL -> {
			int numFiles = addedDL.getDiskManagerFileCount();
			List files_wanted = getList(args.get("files-wanted"));
			List files_unwanted = getList(args.get("files-unwanted"));

			boolean[] toDelete = new boolean[numFiles]; // all false

			int numWanted = files_wanted.size();
			if (numWanted != 0 && numWanted != numFiles) {
				// some wanted -- so, set all toDelete and reset ones in list
				Arrays.fill(toDelete, true);
				for (Object oWanted : files_wanted) {
					int idx = StaticUtils.getNumber(oWanted, -1).intValue();
					if (idx >= 0 && idx < numFiles) {
						toDelete[idx] = false;
					}
				}
			}
			for (Object oUnwanted : files_unwanted) {
				int idx = StaticUtils.getNumber(oUnwanted, -1).intValue();
				if (idx >= 0 && idx < numFiles) {
					toDelete[idx] = true;
				}
			}

			for (int i = 0; i < toDelete.length; i++) {
				if (toDelete[i]) {
					addedDL.getDiskManagerFileInfo(i).setDeleted(true);
				}
			}

			List priority_high = getList(args.get("priority-high"));
			for (Object oHighPriority : priority_high) {
				int idx = StaticUtils.getNumber(oHighPriority, -1).intValue();
				if (idx >= 0 && idx < numFiles) {
					addedDL.getDiskManagerFileInfo(idx).setNumericPriority(
							DiskManagerFileInfo.PRIORITY_HIGH);
				}
			}
			List priority_low = getList(args.get("priority-low"));
			for (Object oLowPriority : priority_low) {
				int idx = StaticUtils.getNumber(oLowPriority, -1).intValue();
				if (idx >= 0 && idx < numFiles) {
					addedDL.getDiskManagerFileInfo(idx).setNumericPriority(
							DiskManagerFileInfo.PRIORITY_LOW);
				}
			}
			// don't need priority-normal if they are normal by default.

			Boolean sequential = getBoolean(args.get(FIELD_TORRENT_SEQUENTIAL), null);
			if (sequential != null) {
				addedDL.setFlag(Download.FLAG_SEQUENTIAL_DOWNLOAD, sequential);
			}

			// handle initial categories/tags

			try {
				String vuze_category = (String) args.get("vuze_category");

				if (vuze_category != null) {

					vuze_category = vuze_category.trim();

					if (vuze_category.length() > 0) {

						TorrentAttribute ta_category = pi.getTorrentManager().getAttribute(
								TorrentAttribute.TA_CATEGORY);

						addedDL.setAttribute(ta_category, vuze_category);
					}
				}

				List<String> vuze_tags = (List<String>) args.get("vuze_tags");

				if (vuze_tags != null) {

					TagManager tm = TagManagerFactory.getTagManager();

					if (tm.isEnabled()) {

						TagType tt = tm.getTagType(TagType.TT_DOWNLOAD_MANUAL);

						for (String tag_name : vuze_tags) {

							addTagToDownload(addedDL, tag_name, tt);
						}
					}
				}
			} catch (Throwable e) {

				e.printStackTrace();
			}
		};

		boolean duplicate = false;

		if (mapTorrent != null) {
			try {
				// Note: adding the torrent will cause another deserialize
				//       because the core saves the TOTorrent to disk, and then
				//       reads it.
				TOTorrent toTorrent = TOTorrentFactory.deserialiseFromMap(mapTorrent);
				torrent = new TorrentImpl(pi, toTorrent);

				com.biglybt.pif.download.DownloadManager dm = pi.getDownloadManager();
				download = dm.getDownload(torrent);
				duplicate = download != null;

			} catch (Throwable e) {

				e.printStackTrace();

				//System.err.println("decode of " + new String(Base64.encode(metainfoBytes), "UTF8"));

				throw (new IOException(
						"torrent download failed: " + StaticUtils.getCausesMesssages(e)));
			}
		} else if (url == null) {

			throw (new IOException("url missing"));

		} else {

			url = url.trim().replaceAll(" ", "%20");

			// hack due to core bug - have to add a bogus arg onto magnet uris else they fail to parse

			String lc_url = url.toLowerCase(Locale.US);

			if (lc_url.startsWith("magnet:")) {

				url += "&dummy_param=1";

			} else if (!lc_url.startsWith("http")) {

				url = UrlUtils.parseTextForURL(url, true, true);
			}

			byte[] hashFromMagnetURI = getHashFromMagnetURI(url);
			if (hashFromMagnetURI != null) {
				com.biglybt.pif.download.DownloadManager dm = pi.getDownloadManager();
				download = dm.getDownload(hashFromMagnetURI);
				duplicate = download != null;
			}

			if (download == null) {
				URL torrent_url;
				try {
					torrent_url = new URL(url);
				} catch (MalformedURLException mue) {
					throw new TextualException("The torrent URI was not valid");
				}

				try {
					TorrentManager torrentManager = pi.getTorrentManager();

					final TorrentDownloader dl = torrentManager.getURLDownloader(
							torrent_url, null, null);

					Object cookies = args.get("cookies");

					if (cookies != null) {

						dl.setRequestProperty("URL_Cookie", cookies);
					}

					boolean is_magnet = torrent_url.getProtocol().equalsIgnoreCase(
							"magnet");

					if (is_magnet) {

						TimerEvent magnet_event = null;

						final Object[] f_result = {
							null
						};

						try {
							final AESemaphore sem = new AESemaphore("magnetsem");
							final URL f_torrent_url = torrent_url;
							final String f_name = (String) args.get("name");
							magnet_event = SimpleTimer.addEvent("magnetcheck",
									SystemTime.getOffsetTime(10 * 1000),
									new TimerEventPerformer() {
										@Override
										public void perform(TimerEvent event) {
											synchronized (f_result) {

												if (f_result[0] != null) {

													return;
												}

												MagnetDownload magnet_download = new MagnetDownload(
														plugin, f_torrent_url, f_name);

												byte[] hash = magnet_download.getTorrentHash();

												synchronized (magnet_downloads) {

													boolean duplicate = false;

													Iterator<MagnetDownload> it = magnet_downloads.iterator();

													while (it.hasNext()) {

														MagnetDownload md = it.next();

														if (hash.length > 0
																&& Arrays.equals(hash, md.getTorrentHash())) {

															if (md.getError() == null) {

																duplicate = true;

																magnet_download = md;

																break;

															} else {

																it.remove();

																plugin.addRecentlyRemoved(md);
															}
														}
													}

													if (!duplicate) {

														magnet_downloads.add(magnet_download);
													}
												}

												f_result[0] = magnet_download;
											}

											sem.release();
										}
									});

							new AEThread2("magnetasync") {
								@Override
								public void run() {
									try {
										Torrent torrent = dl.download(Constants.DEFAULT_ENCODING);

										synchronized (f_result) {

											if (f_result[0] == null) {

												f_result[0] = torrent;

											} else {

												MagnetDownload md = (MagnetDownload) f_result[0];

												boolean already_removed;

												synchronized (magnet_downloads) {

													already_removed = !magnet_downloads.remove(md);
												}

												if (!already_removed) {

													plugin.addRecentlyRemoved(md);

													plugin.addTorrent(torrent, file_Download_dir,
															add_stopped, add_listener);
												}
											}
										}
									} catch (Throwable e) {

										synchronized (f_result) {

											if (f_result[0] == null) {

												f_result[0] = e;

											} else {

												MagnetDownload md = (MagnetDownload) f_result[0];

												md.setError(e);
											}
										}
									} finally {

										sem.release();
									}
								}
							}.start();

							sem.reserve();

							Object res;

							synchronized (f_result) {

								res = f_result[0];
							}

							if (res instanceof Torrent) {

								torrent = (Torrent) res;

							} else if (res instanceof Throwable) {

								throw ((Throwable) res);

							} else {

								download = (MagnetDownload) res;
								torrent = null;
							}
						} finally {

							if (magnet_event != null) {

								magnet_event.cancel();
							}
						}
					} else {

						torrent = dl.download(Constants.DEFAULT_ENCODING);
					}
				} catch (Throwable e) {

					e.printStackTrace();

					throw (new IOException(StaticUtils.getCausesMesssages(e)));
				}
			}
		}

		if (download == null) {

			download = plugin.addTorrent(torrent, file_Download_dir, add_stopped,
					add_listener);
		}

		Map<String, Object> torrent_details = new HashMap<>();

		torrent_details.put("id", plugin.getID(download, true));
		torrent_details.put("name", xmlEscape
				? StaticUtils.escapeXML(download.getName()) : download.getName());
		torrent_details.put(FIELD_TORRENT_HASH_STRING,
				ByteFormatter.encodeString(download.getTorrentHash()));

		result.put(duplicate ? "torrent-duplicate" : "torrent-added",
				torrent_details);
	}

	private static void addTagToDownload(Download download, Object tagToAdd,
			TagType tt) {
		Tag tag = null;
		if (tagToAdd instanceof String) {
			String tagNameToAdd = ((String) tagToAdd).trim();

			if (tagNameToAdd.length() == 0) {
				return;
			}
			tag = tt.getTag(tagNameToAdd, true);

			if (tag == null) {
				try {
					tag = tt.createTag(tagNameToAdd, true);
				} catch (Throwable e) {
					Debug.out(e);
				}
			}
		} else if (tagToAdd instanceof Number) {
			// Tag UID is (TagType << 32) | (Tag ID)
			// .intValue is stripping the TagType, which makes getTag(Tag ID) work
			tag = tt.getTag(((Number) tagToAdd).intValue());
		}

		if (tag != null) {
			tag.addTaggable(PluginCoreUtils.unwrap(download));
		}
	}

	public void reannounce(Map args, Map result)
			throws IOException {
		plugin.checkUpdatePermissions();

		Object ids = args.get("ids");

		List<DownloadStub> downloads = plugin.getDownloads(ids, false);

		for (DownloadStub download_stub : downloads) {

			try {
				download_stub.destubbify().requestTrackerAnnounce();

			} catch (Throwable e) {

				Debug.out("Failed to reannounce '" + download_stub.getName() + "'", e);
			}
		}
	}

	public void remove(List<MagnetDownload> magnet_downloads, Map args,
			Map result)
			throws IOException {
		/*
		Request arguments:
		
		string                     | value type & description
		---------------------------+-------------------------------------------------
		"ids"                      | array      torrent list, as described in 3.1
		"delete-local-data"        | boolean    delete local data. (default: false)
		
		Response arguments: none
		 */
		plugin.checkUpdatePermissions();

		Object ids = args.get("ids");

		boolean delete_data = getBoolean(args.get("delete-local-data"));

		List<DownloadStub> downloads = plugin.getDownloads(ids, true);

		for (DownloadStub download_stub : downloads) {

			try {
				if (download_stub instanceof MagnetDownload) {

					synchronized (magnet_downloads) {

						magnet_downloads.remove(download_stub);
					}

					plugin.addRecentlyRemoved(download_stub);

				} else {
					Download download = download_stub.destubbify();

					int state = download.getState();

					if (state != Download.ST_STOPPED) {

						download.stop();
					}

					if (delete_data) {

						download.remove(true, true);

					} else {

						download.remove();
					}

					plugin.addRecentlyRemoved(download);
				}
			} catch (Throwable e) {

				Debug.out("Failed to remove download '" + download_stub.getName() + "'",
						e);
			}
		}
	}

	public void verify(Map args, Map result)
			throws IOException {
		plugin.checkUpdatePermissions();

		Object ids = args.get("ids");

		List<DownloadStub> downloads = plugin.getDownloads(ids, false);

		for (DownloadStub download_stub : downloads) {

			try {
				Download download = download_stub.destubbify();

				int state = download.getState();

				if (state != Download.ST_STOPPED) {

					download.stop();
				}

				download.recheckData();

			} catch (Throwable e) {
			}
		}
	}

	public void stop(Map args, Map result)
			throws IOException {
		plugin.checkUpdatePermissions();

		Object ids = args.get("ids");

		List<DownloadStub> downloads = plugin.getDownloads(ids, false);

		for (DownloadStub download_stub : downloads) {

			if (!download_stub.isStub()) {

				try {
					Download download = download_stub.destubbify();

					int state = download.getState();

					if (state != Download.ST_STOPPED) {

						download.stop();
					}
				} catch (Throwable e) {
				}
			}
		}
	}

	public void start(Map args, Map result)
			throws IOException {
		plugin.checkUpdatePermissions();

		Object ids = args.get("ids");

		List<DownloadStub> downloads = plugin.getDownloads(ids, false);

		for (DownloadStub download_stub : downloads) {

			try {
				Download download = download_stub.destubbify();

				int state = download.getState();
				if (state == Download.ST_ERROR) {
					// Stop on an error torrent should stop it immediately
					download.stop();
				}

				if (download.isForceStart()) {
					download.setForceStart(false);
				}

				if (state != Download.ST_DOWNLOADING && state != Download.ST_SEEDING) {

					download.restart();
				}
			} catch (Throwable e) {
			}
		}
	}

	public void startNow(Map args, Map result)
			throws IOException {
		plugin.checkUpdatePermissions();

		Object ids = args.get("ids");

		List<DownloadStub> downloads = plugin.getDownloads(ids, false);

		for (DownloadStub download_stub : downloads) {

			try {
				Download download = download_stub.destubbify();

				download.startDownload(true);

			} catch (Throwable e) {
			}
		}
	}

	public void renamePath(Map args, Map result)
			throws TextualException, IOException {
		/*
		Request arguments:
		
		string                           | value type & description
		---------------------------------+-------------------------------------------------
		"ids"                            | array      the torrent torrent list, as described in 3.1
		                                |            (must only be 1 torrent)
		"path"                           | string     the path to the file or folder that will be renamed
		"name"                           | string     the file or folder's new name
		
		Response arguments: "path", "name", and "id", holding the torrent ID integer
		
		
		NOTE: 
		transmission web ui puts the existing name of the torrent in "path",
		and "name" has the new torrent name.
		 */
		Object ids = args.get("ids");

		String oldName = MapUtils.getMapString(args, "path", null);
		if (oldName == null) {
			throw new TextualException("torrent-rename-path: path is missing");
		}

		String newName = MapUtils.getMapString(args, "name", "");
		if (newName.isEmpty()) {
			throw new TextualException("torrent-rename-path: new name is missing");
		}

		Core core = CoreFactory.getSingleton();
		GlobalManager gm = core.getGlobalManager();

		List<com.biglybt.core.download.DownloadManager> dms = plugin.getDownloadManagerListFromIDs(
				gm, ids);

		if (dms.size() != 1) {
			throw new TextualException(
					"torrent-rename-path must have only 1 torrent id");
		}

		com.biglybt.core.download.DownloadManager dm = dms.get(0);

		// Mostly copied from AdvRenameWindow
		boolean saveLocationIsFolder = dm.getSaveLocation().isDirectory();
		String newSavePath = FileUtil.convertOSSpecificChars(newName,
				saveLocationIsFolder);

		DownloadManagerState downloadState = dm.getDownloadState();
		downloadState.setDisplayName(newName);
		try {
			try {
				if (dm.getTorrent().isSimpleTorrent()) {

					String dnd_sf = downloadState.getAttribute(
							DownloadManagerState.AT_INCOMP_FILE_SUFFIX);

					if (dnd_sf != null) {

						dnd_sf = dnd_sf.trim();

						String existing_name = dm.getSaveLocation().getName();

						if (existing_name.endsWith(dnd_sf)) {

							if (!newSavePath.endsWith(dnd_sf)) {

								newSavePath += dnd_sf;
							}
						}
					}
				}
			} catch (Throwable e) {
			}
			dm.renameDownload(newSavePath);

			result.put("path", oldName);
			result.put("name", newName);
			result.put("id", ids);
		} catch (Exception e) {
			e.printStackTrace();

			throw (new IOException(StaticUtils.getCausesMesssages(e)));
		}
	}

	public void setLocation(Map args, Map result)
			throws IOException, DownloadException {
		/*
		Request arguments:
		
		string                     | value type & description
		---------------------------+-------------------------------------------------
		"ids"                      | array      torrent list, as described in 3.1
		"location"                 | string     the new torrent location
		"move"                     | boolean    if true, move from previous location.
		                        |            otherwise, search "location" for files
		                        |            (default: false)
		
		Response arguments: none
		 */
		plugin.checkUpdatePermissions();

		Object ids = args.get("ids");

		boolean moveData = getBoolean(args.get("move"));
		String sSavePath = (String) args.get("location");

		List<DownloadStub> downloads = plugin.getDownloads(ids, false);

		File fSavePath = FileUtil.newFile(sSavePath);

		for (DownloadStub download_stub : downloads) {

			Download download = download_stub.destubbify();

			if (moveData) {
				Torrent torrent = download.getTorrent();
				if (torrent == null || torrent.isSimpleTorrent()
						|| fSavePath.getParentFile() == null) {
					download.moveDataFiles(fSavePath);
				} else {
					download.moveDataFiles(fSavePath.getParentFile(),
							fSavePath.getName());
				}
			} else {
				com.biglybt.core.download.DownloadManager dm = PluginCoreUtils.unwrap(
						download);

				// This is copied from TorrentUtils.changeDirSelectedTorrent

				int state = dm.getState();
				if (state == com.biglybt.core.download.DownloadManager.STATE_STOPPED) {
					if (!dm.filesExist(true)) {
						state = com.biglybt.core.download.DownloadManager.STATE_ERROR;
					}
				}

				if (state == com.biglybt.core.download.DownloadManager.STATE_ERROR) {

					dm.setTorrentSaveDir(FileUtil.newFile(sSavePath), false);

					boolean found = dm.filesExist(true);
					if (!found && dm.getTorrent() != null
							&& !dm.getTorrent().isSimpleTorrent()) {
						String parentPath = fSavePath.getParent();
						if (parentPath != null) {
							dm.setTorrentSaveDir(FileUtil.newFile(parentPath), false);
							found = dm.filesExist(true);
							if (!found) {
								dm.setTorrentSaveDir(FileUtil.newFile(sSavePath), false);
							}
						}
					}

					if (found) {
						dm.stopIt(com.biglybt.core.download.DownloadManager.STATE_STOPPED,
								false, false);

						dm.setStateQueued();
					}
				}
			}
		}
	}

	public void set(String session_id, Map args, Map result) {
		Object ids = args.get("ids");

		plugin.handleRecentlyRemoved(session_id, args, result);

		List<DownloadStub> downloads = plugin.getDownloads(ids, false);

		// RPC v5
		// Not used: Number bandwidthPriority = getNumber("bandwidthPriority", null);

		Number speed_limit_down = StaticUtils.getNumber(
				args.get(FIELD_TORRENT_DOWNLOAD_LIMIT),
				StaticUtils.getNumber(args.get(TR_PREFS_KEY_DSPEED_KBps),
						StaticUtils.getNumber(args.get("speedLimitDownload"))));
		Boolean downloadLimited = getBoolean(FIELD_TORRENT_DOWNLOAD_LIMITED, null);

		List files_wanted = (List) args.get("files-wanted");
		List files_unwanted = (List) args.get("files-unwanted");
		List files_dnd = (List) args.get("files-dnd");
		List files_delete = (List) args.get("files-delete");

		// RPC v5
		/** true if session upload limits are honored */
		// Not Used: Boolean honorsSessionLimits = getBoolean("honorsSessionLimits", null);

		// "location"            | string     new location of the torrent's content
		String location = (String) args.get("location");

		// RPC v16
		List labels = (List) args.get(FIELD_TORRENT_LABELS);

		// Not Implemented: By default, Vuze automatically adjusts mac connections per torrent based on bandwidth and seeding state
		// "peer-limit"          | number     maximum number of peers

		List priority_high = (List) args.get("priority-high");
		List priority_low = (List) args.get("priority-low");
		List priority_normal = (List) args.get("priority-normal");

		List file_infos = (List) args.get(FIELD_TORRENT_FILES);

		// RPC v14
		// "queuePosition"       | number     position of this torrent in its queue [0...n)
		Number queuePosition = StaticUtils.getNumber(FIELD_TORRENT_POSITION, null);

		// RPC v10
		// "seedIdleLimit"       | number     torrent-level number of minutes of seeding inactivity

		// RPC v10: Not used, always TR_IDLELIMIT_GLOBAL
		// "seedIdleMode"        | number     which seeding inactivity to use.  See tr_inactvelimit (OR tr_idlelimit and TR_IDLELIMIT_*)

		// RPC v5: Not Supported
		// "seedRatioLimit"      | double     torrent-level seeding ratio

		// RPC v5: Not Supported
		// "seedRatioMode"       | number     which ratio to use.  See tr_ratiolimit

		// RPC v10
		// "trackerAdd"          | array      strings of announce URLs to add
		List trackerAddList = (List) args.get("trackerAdd");

		// RPC v10: TODO
		// "trackerRemove"       | array      ids of trackers to remove
		// List trackerRemoveList = (List) args.get("trackerRemove");

		// RPC v10: TODO
		// "trackerReplace"      | array      pairs of <trackerId/new announce URLs>

		// "uploadLimit"         | number     maximum upload speed (KBps)
		Number speed_limit_up = StaticUtils.getNumber(args.get("uploadLimit"),
				StaticUtils.getNumber(args.get(TR_PREFS_KEY_USPEED_KBps),
						StaticUtils.getNumber(args.get("speedLimitUpload"))));

		// "uploadLimited"       | boolean    true if "uploadLimit" is honored
		Boolean uploadLimited = getBoolean("uploadLimited", null);

		// RPC Vuze
		// "tagAdd"             | array       array of tags to add to torrent
		List tagAddList = (List) args.get("tagAdd");
		List tagRemoveList = (List) args.get("tagRemove");

		Long l_uploaded_ever = (Long) args.get(FIELD_TORRENT_UPLOADED_EVER);
		Long l_downloaded_ever = (Long) args.get(FIELD_TORRENT_DOWNLOADED_EVER);

		long uploaded_ever = l_uploaded_ever == null ? -1 : l_uploaded_ever;
		long downloaded_ever = l_downloaded_ever == null ? -1 : l_downloaded_ever;

		Boolean sequential = getBoolean(args.get(FIELD_TORRENT_SEQUENTIAL), null);

		String name = (String) args.get("name");

		for (DownloadStub download_stub : downloads) {

			try {
				Download download = download_stub.destubbify();

				Torrent t = download.getTorrent();

				if (t == null) {

					continue;
				}

				if (location != null) {
					File file = FileUtil.newFile(location);
					if (!file.isFile()) {
						try {
							download.moveDataFiles(file);
						} catch (DownloadException e) {
							Debug.out(e);
						}
					}
				}

				if (name != null) {
					com.biglybt.core.download.DownloadManager core_download = PluginCoreUtils.unwrap(
							download);
					core_download.getDownloadState().setDisplayName(name);
				}

				if (queuePosition != null) {
					download.moveTo(queuePosition.intValue());
				}

				if (trackerAddList != null) {
					for (Object oTracker : trackerAddList) {
						if (oTracker instanceof String) {
							String aTracker = (String) oTracker;
							TorrentUtils.announceGroupsInsertFirst(PluginCoreUtils.unwrap(t),
									aTracker);
						}
					}
				}

				if (speed_limit_down != null && Boolean.TRUE.equals(downloadLimited)) {

					download.setDownloadRateLimitBytesPerSecond(
							speed_limit_down.intValue());
				} else if (Boolean.FALSE.equals(downloadLimited)) {

					download.setDownloadRateLimitBytesPerSecond(0);
				}

				if (speed_limit_up != null && Boolean.TRUE.equals(uploadLimited)) {

					download.setUploadRateLimitBytesPerSecond(speed_limit_up.intValue());
				} else if (Boolean.FALSE.equals(uploadLimited)) {

					download.setUploadRateLimitBytesPerSecond(0);
				}

				if (tagAddList != null) {
					TagManager tm = TagManagerFactory.getTagManager();

					if (tm.isEnabled()) {

						TagType tt = tm.getTagType(TagType.TT_DOWNLOAD_MANUAL);

						for (Object oTagToAdd : tagAddList) {
							if (oTagToAdd != null) {
								addTagToDownload(download, oTagToAdd, tt);
							}

						}
					}
				}

				if (tagRemoveList != null) {
					TagManager tm = TagManagerFactory.getTagManager();

					if (tm.isEnabled()) {

						TagType ttManual = tm.getTagType(TagType.TT_DOWNLOAD_MANUAL);
						TagType ttCategory = tm.getTagType(TagType.TT_DOWNLOAD_CATEGORY);

						for (Object oTagToAdd : tagRemoveList) {
							if (oTagToAdd instanceof String) {
								Tag tag = ttManual.getTag((String) oTagToAdd, true);
								if (tag != null) {
									tag.removeTaggable(PluginCoreUtils.unwrap(download));
								}
								tag = ttCategory.getTag((String) oTagToAdd, true);
								if (tag != null) {
									tag.removeTaggable(PluginCoreUtils.unwrap(download));
								}
							} else if (oTagToAdd instanceof Number) {
								int uid = ((Number) oTagToAdd).intValue();
								Tag tag = ttManual.getTag(uid);
								if (tag != null) {
									tag.removeTaggable(PluginCoreUtils.unwrap(download));
								}
								tag = ttCategory.getTag(uid);
								if (tag != null) {
									tag.removeTaggable(PluginCoreUtils.unwrap(download));
								}
							}

						}
					}
				}

				if (labels != null) {
					TagManager tm = TagManagerFactory.getTagManager();
					TagType ttManual = tm.getTagType(TagType.TT_DOWNLOAD_MANUAL);

					com.biglybt.core.download.DownloadManager download_core = PluginCoreUtils.unwrap(
							download);
					List<Tag> tags = ttManual.getTagsForTaggable(download_core);

					if (tags != null) {
						Map<String, Tag> existingLabels = new HashMap<>();
						for (Tag tag : tags) {
							existingLabels.put(tag.getTagName(), tag);
						}

						for (Object o : labels) {
							if (!(o instanceof String)) {
								continue;
							}
							String label = (String) o;
							if (existingLabels.remove(label) == null) {
								addTagToDownload(download, label, ttManual);
							}
						}

						// remaining need to be removed
						for (Tag tagToRemove : existingLabels.values()) {
							tagToRemove.removeTaggable(download_core);
						}
					}
				}

				DiskManagerFileInfo[] files = download.getDiskManagerFileInfo();

				if (files_unwanted != null) {
					boolean uncheckDeletes = plugin.getUncheckDeletes();

					for (Object o : files_unwanted) {

						int index = ((Long) o).intValue();

						if (index >= 0 && index <= files.length) {

							if (uncheckDeletes) {
								files[index].setDeleted(true);
							} else {
								files[index].setSkipped(true);
							}
						}
					}
				}

				if (files_dnd != null) {

					for (Object o : files_dnd) {

						int index = ((Long) o).intValue();

						if (index >= 0 && index <= files.length) {

							files[index].setSkipped(true);
						}
					}
				}

				if (files_delete != null) {

					for (Object o : files_delete) {

						int index = ((Long) o).intValue();

						if (index >= 0 && index <= files.length) {

							files[index].setDeleted(true);
						}
					}
				}

				if (files_wanted != null) {

					for (Object o : files_wanted) {

						int index = ((Long) o).intValue();

						if (index >= 0 && index <= files.length) {

							files[index].setSkipped(false);
						}
					}
				}

				if (priority_high != null) {

					for (Object o : priority_high) {

						int index = ((Long) o).intValue();

						if (index >= 0 && index <= files.length) {

							files[index].setNumericPriority(
									DiskManagerFileInfo.PRIORITY_HIGH);
						}
					}
				}

				if (priority_normal != null) {

					for (Object o : priority_normal) {

						int index = ((Long) o).intValue();

						if (index >= 0 && index <= files.length) {

							files[index].setNumericPriority(
									DiskManagerFileInfo.PRIORITY_NORMAL);
						}
					}
				}

				if (priority_low != null) {

					for (Object o : priority_low) {

						int index = ((Long) o).intValue();

						if (index >= 0 && index <= files.length) {

							files[index].setNumericPriority(DiskManagerFileInfo.PRIORITY_LOW);
						}
					}
				}

				if (uploaded_ever != -1 || downloaded_ever != -1) {

					// new method in 4511 B31

					try {
						download.getStats().resetUploadedDownloaded(uploaded_ever,
								downloaded_ever);

					} catch (Throwable e) {
					}
				}

				if (file_infos != null) {

					boolean paused_it = false;

					try {
						for (Object fileInfo : file_infos) {

							Map file_info = (Map) fileInfo;

							int index = ((Number) file_info.get(
									FIELD_FILES_INDEX)).intValue();

							if (index < 0 || index >= files.length) {

								throw (new IOException("File index '" + index
										+ "' invalid for '" + download.getName() + "'"));
							}

							//String	path 	= (String)file_info.get( "path" ); don't support changing this yet

							String new_name = (String) file_info.get("name"); // terminal name of the file (NOT the whole relative path+name)

							if (new_name == null || new_name.trim().length() == 0) {

								throw (new IOException("'name' is mandatory"));
							}

							new_name = new_name.trim();

							DiskManagerFileInfo file = files[index];

							File existing = file.getFile(true);

							if (existing.getName().equals(new_name)) {

								continue;
							}

							if (!download.isPaused()) {

								download.pause();

								paused_it = true;
							}

							File new_file = FileUtil.newFile(existing.getParentFile(), new_name);

							if (new_file.exists()) {

								throw (new IOException(
										"new file '" + new_file + "' already exists"));
							}

							file.setLink(new_file);
						}
					} finally {

						if (paused_it) {

							download.resume();
						}
					}
				}

				if (sequential != null) {
					download.setFlag(Download.FLAG_SEQUENTIAL_DOWNLOAD, sequential);
				}

			} catch (Throwable e) {

				Debug.out(e);
			}
		}
	}

}
