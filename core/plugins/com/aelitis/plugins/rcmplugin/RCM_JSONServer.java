/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.aelitis.plugins.rcmplugin;

import java.util.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.utils.Utilities;
import com.biglybt.pif.utils.search.*;

import com.biglybt.core.content.*;
import com.biglybt.util.MapUtils;

public final class RCM_JSONServer
	implements Utilities.JSONServer
{
	private final RCMPlugin rcmPlugin;

	RCM_JSONServer(RCMPlugin rcmPlugin) {
		this.rcmPlugin = rcmPlugin;
	}

	private Map<String, SearchInstance> mapSearchInstances = new HashMap<String, SearchInstance>();

	private Map<String, Map> mapSearchResults = new HashMap<String, Map>();

	private List<String> methods = new ArrayList<String>();
	{
		methods.add("rcm-is-enabled");
		methods.add("rcm-get-list");
		methods.add("rcm-lookup-start");
		methods.add("rcm-lookup-remove");
		methods.add("rcm-remove");
		methods.add("rcm-set-read");
		methods.add("rcm-lookup-get-results");
		methods.add("rcm-set-enabled");
		methods.add("rcm-create-subscription");
	}

	@Override
	public String getName() {
		return ("SwarmDiscoveries");
	}

	@Override
	public List<String> getSupportedMethods() {
		return (methods);
	}

	@Override
	public Map call(String method, Map args)

			throws PluginException {
		if (rcmPlugin.isDestroyed()) {

			throw (new PluginException("Plugin unloaded"));
		}

		Map<String, Object> result = new HashMap<String, Object>();

		if (method.equals("rcm-is-enabled")) {

			result.put("enabled", rcmPlugin.isRCMEnabled());
			result.put("sources", rcmPlugin.getSourcesList());
			result.put("is-all-sources", rcmPlugin.isAllSources());
			result.put("is-default-sources", rcmPlugin.isDefaultSourcesList());
			result.put("ui-enabled", rcmPlugin.isUIEnabled());

		} else if (method.equals("rcm-get-list")) {

			if (rcmPlugin.isRCMEnabled() && rcmPlugin.isUIEnabled()) {
				rpcGetList(result, args);
			} else {
				throw (new PluginException("RCM not enabled"));
			}

		} else if (method.equals("rcm-set-enabled")) {

			boolean enable = MapUtils.getMapBoolean(args, "enable", false);
			boolean all = MapUtils.getMapBoolean(args, "all-sources", false);
			if (enable) {
				rcmPlugin.setRCMEnabled(enable);
			}

			rcmPlugin.setSearchEnabled(enable);
			rcmPlugin.setUIEnabled(enable);

			rcmPlugin.setFTUXBeenShown(true);

			if (all) {
				rcmPlugin.setToAllSources();
			} else {
				rcmPlugin.setToDefaultSourcesList();
			}

		} else if (method.equals("rcm-lookup-start")) {

			if (rcmPlugin.isRCMEnabled() && rcmPlugin.isUIEnabled()) {
				rpcLookupStart(result, args);
			} else {
				throw (new PluginException("RCM not enabled"));
			}

		} else if (method.equals("rcm-lookup-remove")) {

			if (rcmPlugin.isRCMEnabled() && rcmPlugin.isUIEnabled()) {
				rpcLookupRemove(result, args);
			} else {
				throw (new PluginException("RCM not enabled"));
			}

		} else if (method.equals("rcm-remove")) {

			if (rcmPlugin.isRCMEnabled() && rcmPlugin.isUIEnabled()) {
				rpcRemove(result, args);
			} else {
				throw (new PluginException("RCM not enabled"));
			}

		} else if (method.equals("rcm-set-read")) {

			if (rcmPlugin.isRCMEnabled() && rcmPlugin.isUIEnabled()) {
				rpcSetRead(result, args);
			} else {
				throw (new PluginException("RCM not enabled"));
			}

		} else if (method.equals("rcm-lookup-get-results")) {

			if (rcmPlugin.isRCMEnabled() && rcmPlugin.isUIEnabled()) {
				rpcLookupGetResults(result, args);
			} else {
				throw (new PluginException("RCM not enabled"));
			}

		} else if (method.equals("rcm-create-subscription")) {

			if (rcmPlugin.isRCMEnabled() && rcmPlugin.isUIEnabled()) {
				rpcCreateSubscription(result, args);
			} else {
				throw (new PluginException("RCM not enabled"));
			}

		} else {

			throw (new PluginException("Unsupported method"));
		}

		return (result);
	}

	public void unload() {
		if (mapSearchResults != null) {
			mapSearchResults.clear();
		}
		if (mapSearchInstances != null) {
			for (SearchInstance si : mapSearchInstances.values()) {
				try {
					si.cancel();
				} catch (Throwable t) {
				}
			}
			mapSearchInstances.clear();
		}
	}

	private Map<String, Object> relatedContentToMap(RelatedContent item) {
		HashMap<String, Object> map = new HashMap<String, Object>();

		long changedLocallyOn = item.getChangedLocallyOn();

		map.put("changedOn", changedLocallyOn);
		map.put("hash", ByteFormatter.encodeString(item.getHash()));
		map.put("lastSeenSecs", item.getLastSeenSecs());
		map.put("peers", item.getLeechers());
		map.put("level", item.getLevel());
		map.put("publishDate", item.getPublishDate());
		map.put("rank", item.getRank());
		map.put("relatedToHash",
				ByteFormatter.encodeString(item.getRelatedToHash()));
		map.put("seeds", item.getSeeds());
		map.put("size", item.getSize());
		map.put("tags", item.getTags());
		map.put("title", item.getTitle());
		map.put("tracker", item.getTracker());
		map.put("unread", item.isUnread());
		// can't find a good uid, hashcode will do for session
		map.put("id", item.hashCode());
		return map;
	}

	protected void rpcGetList(Map result, Map args)
			throws PluginException {
		long since = MapUtils.getMapLong(args, "since", 0);
		long until = 0;

		List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
		result.put("related", list);
		try {
			RelatedContentManager manager = RelatedContentManager.getSingleton();
			RelatedContent[] relatedContent = manager.getRelatedContent();

			for (RelatedContent item : relatedContent) {
				if (!rcmPlugin.isVisible(item)) {
					continue;
				}

				long changedLocallyOn = item.getChangedLocallyOn();
				if (changedLocallyOn < since) {
					continue;
				}
				if (changedLocallyOn > until) {
					until = changedLocallyOn;
				}

				Map map = relatedContentToMap(item);
				list.add(map);
			}
		} catch (Exception e) {
			throw new PluginException(e);
		}

		result.put("until", until);
	}

	protected void rpcLookupGetResults(Map result, Map args)
			throws PluginException {
		// TODO: filter by "since"
		long since = MapUtils.getMapLong(args, "since", 0);

		Map map = mapSearchResults.get(MapUtils.getMapString(args, "lid", null));
		if (map == null) {
			throw new PluginException("No results for Lookup ID");
		}
		result.putAll(map);
	}

	protected void rpcSetRead(Map result, Map args)
			throws PluginException {

		boolean unread = !MapUtils.getMapBoolean(args, "read", true);

		String lid = MapUtils.getMapString(args, "lid", null);

		if (!args.containsKey("ids")) {
			throw new PluginException("No ids");
		}

		List<Integer> ids = new ArrayList<Integer>();
		Object oIDs = args.get("ids");
		if (oIDs instanceof List) {
			List list = (List) oIDs;
			for (Object item : list) {
				ids.add(((Number) item).intValue());
			}
		} else if (oIDs instanceof Number) {
			ids.add(((Number) oIDs).intValue());
		}

		List<Integer> successes = new ArrayList<Integer>();
		result.put("success", successes);
		result.put("failure", ids);

		if (lid != null) {
			SearchInstance searchInstance = mapSearchInstances.get(lid);
			Map map = mapSearchResults.get(lid);
			if (map != null) {
				List listResults = (List) map.get("results");
				if (listResults == null) {
					return;
				}
				for (Object o : listResults) {
					Map mapRC = (Map) o;
					int rcID = MapUtils.getMapInt(mapRC, "id", 0);
					for (Iterator<Integer> iterator = ids.iterator(); iterator.hasNext();) {
						Integer id = iterator.next();
						if (rcID != id) {
							continue;
						}

						successes.add(id);
						map.put("unread", unread);

						iterator.remove();
						if (ids.size() == 0) {
							return;
						}
					} // for ids
				}
			}
			return;
		}

		try {
		
			RelatedContentManager manager = RelatedContentManager.getSingleton();
			RelatedContent[] relatedContent = manager.getRelatedContent();
			for (RelatedContent rc : relatedContent) {
				for (Iterator<Integer> iterator = ids.iterator(); iterator.hasNext();) {
					Integer id = iterator.next();
					if (rc.hashCode() == id) {
						rc.setUnread(unread);
						successes.add(id);

						iterator.remove();

						if (ids.size() == 0) {
							return;
						}
					}
				}
			}
		} catch (ContentException e) {
			throw new PluginException(e);
		}
	}

	protected void rpcRemove(Map result, Map args)
			throws PluginException {

		if (!args.containsKey("ids")) {
			throw new PluginException("No ids");
		}

		List<Integer> ids = new ArrayList<Integer>();
		Object o = args.get("ids");
		if (o instanceof List) {
			List list = (List) o;
			for (Object item : list) {
				ids.add(((Number) item).intValue());
			}
		} else if (o instanceof Number) {
			ids.add(((Number) o).intValue());
		}

		List<Integer> successes = new ArrayList<Integer>();
		result.put("success", successes);
		result.put("failure", ids);

		try {
			RelatedContentManager manager = RelatedContentManager.getSingleton();
			RelatedContent[] relatedContent = manager.getRelatedContent();
			for (RelatedContent rc : relatedContent) {
				for (Iterator<Integer> iterator = ids.iterator(); iterator.hasNext();) {
					Integer id = iterator.next();
					if (rc.hashCode() == id) {
						successes.add(id);
						rc.delete();

						iterator.remove();

						if (ids.size() == 0) {
							return;
						}
					}
				}
			}
		} catch (ContentException e) {
			throw new PluginException(e);
		}
	}

	protected void rpcLookupRemove(Map result, Map args)
			throws PluginException {
		String lid = MapUtils.getMapString(args, "lid", null);
		if (lid == null) {
			throw new PluginException("No Lookup ID");
		}

		mapSearchInstances.remove(lid);
		mapSearchResults.remove(lid);
	}

	protected void rpcLookupStart(Map result, Map args)
			throws PluginException {
		String searchTerm = MapUtils.getMapString(args, "search-term", null);
		String lookupByTorrent = MapUtils.getMapString(args, "torrent-hash", null);
		long lookupBySize = MapUtils.getMapLong(args, "file-size", 0);

		String[] networks = new String[] {
			AENetworkClassifier.AT_PUBLIC
		};
		String net_str = RCMPlugin.getNetworkString(networks);
		try {
			RelatedContentManager manager = RelatedContentManager.getSingleton();

			if (searchTerm != null) {
				final String lookupID = Integer.toHexString(
						(searchTerm + net_str).hashCode());

				result.put("lid", lookupID);

				//SearchInstance searchInstance = mapSearchInstances.get(searchID);

				Map<String, Object> parameters = new HashMap<String, Object>();
				parameters.put(SearchProvider.SP_SEARCH_TERM, searchTerm);

				//if ( networks != null && networks.length > 0 ){
				//parameters.put( SearchProvider.SP_NETWORKS, networks );
				//}

				Map map = mapSearchResults.get(lookupID);
				if (map == null) {
					map = new HashMap();
					mapSearchResults.put(lookupID, map);
				}
				int activeSearches = MapUtils.getMapInt(map, "active-searches", 0);
				map.put("active-searches", ++activeSearches);
				map.put("complete", activeSearches > 0 ? false : true);
				SearchInstance searchRCM = manager.searchRCM(parameters,
						new SearchObserver() {

							@Override
							public void resultReceived(SearchInstance search,
							                           SearchResult result) {
								synchronized (mapSearchResults) {
									Map map = mapSearchResults.get(lookupID);
									if (map == null) {
										return;
									}

									List list = MapUtils.getMapList(map, "results", null);
									if (list == null) {
										list = new ArrayList();
										map.put("results", list);
									}

									SearchRelatedContent src = new SearchRelatedContent(result);

									Map mapResult = relatedContentToMap(src);

									list.add(mapResult);

								}
							}

							@Override
							public Object getProperty(int property) {
								// TODO Auto-generated method stub
								return null;
							}

							@Override
							public void complete() {
								synchronized (mapSearchResults) {
									Map map = mapSearchResults.get(lookupID);
									if (map == null) {
										return;
									}
									int activeSearches = MapUtils.getMapInt(map,
											"active-searches", 0);
									if (activeSearches > 0) {
										activeSearches--;
									}
									map.put("active-searches", activeSearches);
									map.put("complete", activeSearches > 0 ? false : true);
								}
							}

							@Override
							public void cancelled() {
								complete();
							}
						});
			} else if (lookupByTorrent != null || lookupBySize > 0) {

				final String lookupID = lookupByTorrent != null ? lookupByTorrent
						: Integer.toHexString(
								(String.valueOf(lookupBySize) + net_str).hashCode());

				result.put("lid", lookupID);

				Map map = mapSearchResults.get(lookupID);
				if (map == null) {
					map = new HashMap();
					mapSearchResults.put(lookupID, map);
				}
				int activeSearches = MapUtils.getMapInt(map, "active-searches", 0);
				map.put("active-searches", ++activeSearches);
				map.put("complete", activeSearches > 0 ? false : true);
				RelatedContentLookupListener l = new RelatedContentLookupListener() {

					@Override
					public void lookupStart() {
					}

					@Override
					public void lookupFailed(ContentException error) {
						lookupComplete();
					}

					@Override
					public void lookupComplete() {
						synchronized (mapSearchResults) {
							Map map = mapSearchResults.get(lookupID);
							if (map == null) {
								return;
							}
							int activeSearches = MapUtils.getMapInt(map, "active-searches",
									0);
							if (activeSearches > 0) {
								activeSearches--;
							}
							map.put("active-searches", activeSearches);
							map.put("complete", activeSearches > 0 ? false : true);
						}
					}

					@Override
					public void contentFound(RelatedContent[] content) {
						synchronized (mapSearchResults) {
							Map map = mapSearchResults.get(lookupID);
							if (map == null) {
								return;
							}

							List list = MapUtils.getMapList(map, "results", null);
							if (list == null) {
								list = new ArrayList();
								map.put("results", list);
							}

							for (RelatedContent item : content) {
								Map<String, Object> mapResult = relatedContentToMap(item);
								list.add(mapResult);
							}

						}
					}
				};
				if (lookupByTorrent != null) {
					byte[] hash = ByteFormatter.decodeString(lookupByTorrent);
					manager.lookupContent(hash, networks, l);
				} else if (lookupBySize > 0) {
					manager.lookupContent(lookupBySize, l);
				}

			} else {
				throw new PluginException("No search-term, torrent-hash or file-size");
			}
		} catch (Exception e) {
			throw new PluginException(e);
		}
	}

	private void rpcCreateSubscription(Map result, Map args)
			throws PluginException {
		String expression = MapUtils.getMapString(args, "expression", null);
		boolean is_popularity = expression == null;
		final String name = is_popularity ? MessageText.getString("rcm.pop")
				: ("'" + expression + "'");
		String subscription_name = MessageText.getString("rcm.search.provider")
				+ ": " + name;
		SearchProvider sp = rcmPlugin.getSearchProvider();
		String[] networks = AENetworkClassifier.AT_NETWORKS;

		Map<String, Object> properties = new HashMap<String, Object>();

		properties.put(SearchProvider.SP_SEARCH_NAME, subscription_name);
		properties.put(SearchProvider.SP_SEARCH_TERM, expression);
		properties.put(SearchProvider.SP_NETWORKS, networks);

		try {
			rcmPlugin.getPluginInterface().getUtilities().getSubscriptionManager().requestSubscription(
					sp, properties);

		} catch (Throwable e) {

			throw new PluginException(e);
		}

		// Probably successful
		result.put("success", true);
	}
}