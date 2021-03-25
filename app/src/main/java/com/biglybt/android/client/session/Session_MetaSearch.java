
/*
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

package com.biglybt.android.client.session;

import android.os.Handler;
import android.text.format.DateUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.biglybt.android.client.*;
import com.biglybt.android.client.rpc.TransmissionRPC;
import com.biglybt.android.util.MapUtils;
import com.biglybt.util.Thunk;

import java.io.Serializable;
import java.util.*;

/**
 * TODO: Clear search results cache sometime
 */
public class Session_MetaSearch
	implements TransmissionRPC.MetaSearchResultsListener
{

	public static final String TAG = "Session_MetaSearch";

	private static final long CACHE_FOR_MS =
			AndroidUtils.DEBUG ? DateUtils.MINUTE_IN_MILLIS * 2 : DateUtils.DAY_IN_MILLIS;

	public interface MetaSearchResultsListener {
		void onMetaSearchGotEngines(SearchResult searchResult);

		void onMetaSearchGotResults(SearchResult searchResult);
	}

	public static class MetaSearchEnginesInfo
			implements Serializable
	{
		@NonNull
		public final String uid;

		public String name;

		public boolean completed;

		public int count;

		public String iconURL;

		public String error;

		public MetaSearchEnginesInfo(@NonNull String uid) {
			this.uid = uid;
		}

		MetaSearchEnginesInfo(@NonNull String uid, String name,
				@Nullable String iconURL, boolean completed) {
			this.name = name;
			this.iconURL = iconURL;
			this.completed = completed;
			this.uid = uid;
		}

		@Override
		public boolean equals(@Nullable Object obj) {
			return (obj instanceof MetaSearchEnginesInfo) && uid.equals(((MetaSearchEnginesInfo) obj).uid);
		}
	}

	public static class SearchResult {
		final public String query;

		final Serializable searchID;

		/**
		 * &lt;Engine UID, {@link MetaSearchEnginesInfo}>
		 */
		final public Map<String, MetaSearchEnginesInfo> mapEngines = new HashMap<>();

		/**
		 * &lt;HashString, Map of Fields>
		 */
		@Thunk
		final public Map<String, Map<String, Object>> mapResults = new HashMap<>();

		public long maxSize;

		public boolean complete;

		Runnable cleanupRunnable;

		long searchStartedOn = System.currentTimeMillis();

		final public Map<String, Object> mapExtras = new HashMap<>();

		SearchResult(String query, Serializable searchID) {
			this.query = query;
			this.searchID = searchID;
		}

		/**
		 * moves cleanup time forward
		 */
		public void touch() {
			if (cleanupRunnable == null) {
				return;
			}
			Handler workerHandler = OffThread.getWorkerHandler();
			workerHandler.removeCallbacks(cleanupRunnable);
			workerHandler.postDelayed(cleanupRunnable, CACHE_FOR_MS);
		}
	}

	@Thunk
	@NonNull
	final Session session;

	/**
	 * &lt;SearchID, {@link SearchResult}>
	 */
	private final Map<Serializable, SearchResult> mapAllSearches = new HashMap<>();

	/**
	 * &lt;Search Term, List&lt;{@link MetaSearchResultsListener}>>
	 */
	private final Map<String, List<MetaSearchResultsListener>> mapResultsListeners = new HashMap<>();

	Session_MetaSearch(@NonNull Session session) {
		this.session = session;
	}

	public void search(@NonNull String searchString,
			@NonNull MetaSearchResultsListener l) {
		synchronized (mapResultsListeners) {
			List<MetaSearchResultsListener> listeners = mapResultsListeners.computeIfAbsent(
					searchString, s -> new ArrayList<>());
			if (!listeners.contains(l)) {
				listeners.add(l);
			}
		}

		synchronized (mapAllSearches) {
			for (SearchResult result : mapAllSearches.values()) {
				if (!result.query.equals(searchString)) {
					continue;
				}
				result.touch();
				if (!result.mapEngines.isEmpty()) {
					l.onMetaSearchGotEngines(result);
					if (!result.mapResults.isEmpty() || result.complete) {
						l.onMetaSearchGotResults(result);
					}
				}
				return;
			}
		}

		session._executeRpc(
				rpc -> rpc.startMetaSearch(searchString, Session_MetaSearch.this));
	}

	public void removeListener(String searchString, MetaSearchResultsListener l) {
		synchronized (mapResultsListeners) {
			List<MetaSearchResultsListener> listeners = mapResultsListeners.get(
					searchString);

			if (listeners == null) {
				return;
			}
			listeners.remove(l);
			if (listeners.isEmpty()) {
				mapResultsListeners.remove(searchString);
			}
		}
	}

	@Override
	public boolean onMetaSearchGotEngines(String searchString,
			Serializable searchID, List<Map<String, Object>> engines) {

		SearchResult searchResult = new SearchResult(searchString, searchID);
		synchronized (mapAllSearches) {
			mapAllSearches.put(searchID, searchResult);
		}

		searchResult.mapEngines.put("",
				new MetaSearchEnginesInfo("", BiglyBTApp.getContext().getString(R.string.metasearch_engine_all),
						null, true));

		for (Map<String, Object> mapEngine : engines) {
			String name = MapUtils.getMapString(mapEngine, "name", null);
			if (name != null) {
				String uid = MapUtils.getMapString(mapEngine, "id", name);
				String favicon = MapUtils.getMapString(mapEngine,
						TransmissionVars.FIELD_SUBSCRIPTION_FAVICON, name);
				searchResult.mapEngines.put(uid,
						new MetaSearchEnginesInfo(uid, name, favicon, false));
			}
		}

		searchResult.cleanupRunnable = () -> {
			synchronized (mapResultsListeners) {
				List<MetaSearchResultsListener> listeners = mapResultsListeners.get(
						searchString);
				if (listeners == null || listeners.isEmpty()) {
					mapResultsListeners.remove(searchString);
				} else {
					// reschedule
					searchResult.touch();
					return;
				}
			}
			synchronized (mapAllSearches) {
				mapAllSearches.remove(searchID);
			}
		};
		OffThread.getWorkerHandler().postDelayed(searchResult.cleanupRunnable,
				CACHE_FOR_MS);

		synchronized (mapResultsListeners) {
			List<MetaSearchResultsListener> listeners = mapResultsListeners.get(
					searchResult.query);
			if (listeners != null) {
				for (MetaSearchResultsListener listener : listeners) {
					listener.onMetaSearchGotEngines(searchResult);
				}
			}
		}
		return true;
	}

	@Override
	public boolean onMetaSearchGotResults(Serializable searchID,
			List<Map<String, Object>> engines, boolean complete) {

		SearchResult searchResult = mapAllSearches.get(searchID);
		if (searchResult == null) {
			return false;
		}

		searchResult.complete = complete;

		for (Object oEngine : engines) {
			if (!(oEngine instanceof Map)) {
				continue;
			}
			//noinspection rawtypes
			Map mapEngine = (Map) oEngine;

			String engineID = MapUtils.getMapString(mapEngine, "id", null);

			MetaSearchEnginesInfo engineInfo = searchResult.mapEngines.get(engineID);

			if (engineInfo == null) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "no engine info for Engine " + engineID + " for '"
							+ searchResult.query + "'");
				}
				continue;
			}

			engineInfo.completed = MapUtils.getMapBoolean(mapEngine,
					TransmissionVars.FIELD_SEARCHRESULT_COMPLETE, false);
			engineInfo.error = MapUtils.getMapString(mapEngine, "error", null);
			if (engineInfo.error != null) {
				engineInfo.count = -1;
			}

			//noinspection RawTypeCanBeGeneric,rawtypes
			List listResults = MapUtils.getMapList(mapEngine, "results", null);
			if (listResults == null || listResults.isEmpty()) {
				continue;
			}
			int numAdded = 0;
			for (Object oResult : listResults) {
				if (!(oResult instanceof Map)) {
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "onMetaSearchGotResults: NOT A MAP: " + oResult);
					}
					continue;
				}

				//noinspection unchecked
				Map<String, Object> mapResult = fixupResultMap(
						(Map<String, Object>) oResult);

				searchResult.maxSize = Math.max(MapUtils.getMapLong(mapResult,
						TransmissionVars.FIELD_SEARCHRESULT_SIZE, 0), searchResult.maxSize);

				String hash = MapUtils.getMapString(mapResult,
						TransmissionVars.FIELD_SEARCHRESULT_HASH, null);
				if (hash == null) {
					hash = MapUtils.getMapString(mapResult,
							TransmissionVars.FIELD_SEARCHRESULT_URL, null);
				}
				if (hash == null) {
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "onMetaSearchGotResults: No hash for " + mapResult);
					}
					continue;
				}

				numAdded++;

				mapResult.put(TransmissionVars.FIELD_SEARCHRESULT_ENGINE_ID, engineID);
				Map<String, Object> mapExisting = searchResult.mapResults.get(hash);
				if (mapExisting == null) {
					searchResult.mapResults.put(hash, mapResult);
					continue;
				}

				// remove so compare can work
				//noinspection unchecked
				List<Map<String, Object>> others = (List<Map<String, Object>>) mapExisting
						.remove("others");
				if (mapResult.equals(mapExisting)) {
					mapExisting.put("others", others);
					continue;
				}

				boolean doAdd = true;
				if (others == null) {
					others = new ArrayList<>();
				} else {
					for (Map<String, Object> other : others) {
						if (mapResult.equals(other)) {
							doAdd = false;
							break;
						}
					}
				}
				if (doAdd) {
					mapExisting.put("others", others);
					others.add(mapResult);
					mapExisting.put(TransmissionVars.FIELD_LAST_UPDATED,
							System.currentTimeMillis());
				}
			}
			engineInfo.count += numAdded;
		}

		synchronized (mapResultsListeners) {
			List<MetaSearchResultsListener> listeners =
					complete ? mapResultsListeners.remove(searchResult.query) :
							mapResultsListeners.get(searchResult.query);
			if (listeners != null) {
				for (MetaSearchResultsListener listener : listeners) {
					listener.onMetaSearchGotResults(searchResult);
				}
			}
		}

		return true;
	}

	/**
	 * Unfortunately, the search results map returns just about everything in
	 * Strings, including numbers.
	 */
	private static Map<String, Object> fixupResultMap(
			Map<String, Object> mapResult) {
		final String[] IDS_LONG = {
				TransmissionVars.FIELD_SEARCHRESULT_PUBLISHDATE,
				TransmissionVars.FIELD_SEARCHRESULT_PEERS,
				TransmissionVars.FIELD_SEARCHRESULT_SIZE,
				TransmissionVars.FIELD_SEARCHRESULT_SEEDS,
		};
		final String[] IDS_FLOAT = {
				TransmissionVars.FIELD_SEARCHRESULT_RANK,
		};

		for (String id : IDS_LONG) {
			Object o = mapResult.get(id);
			if (o instanceof String) {
				try {
					Long l = Long.valueOf((String) o);
					mapResult.put(id, l);
				} catch (Throwable ignore) {
				}
			}
		}

		for (String id : IDS_FLOAT) {
			Object o = mapResult.get(id);
			if (o instanceof String) {
				try {
					Double d = Double.valueOf((String) o);
					mapResult.put(id, d);
				} catch (Throwable ignore) {
				}
			}
		}

		return mapResult;
	}

	public List<String> getCachedSearchTerms() {
		// can't use mapResultsListeners's key, since the entry is removed when activity stops

		List<String> list = new ArrayList<>();

		synchronized (mapAllSearches) {
			for (SearchResult searchResult : mapAllSearches.values()) {
				list.add(searchResult.query);
			}
		}
		return list;
	}

	public void removeCachedTerm(String newName) {
		synchronized (mapAllSearches) {
			for (Iterator<SearchResult> iter = mapAllSearches.values()
					.iterator(); iter.hasNext(); ) {
				SearchResult searchResult = iter.next();
				if (searchResult.query.equals(newName)) {
					iter.remove();
				}
			}
		}

		synchronized (mapResultsListeners) {
			mapResultsListeners.remove(newName);
		}
	}

	public void destroy() {

		synchronized (mapAllSearches) {
			Handler workerHandler = OffThread.getWorkerHandler();
			for (SearchResult searchResult : mapAllSearches.values()) {
				if (searchResult.cleanupRunnable != null) {
					workerHandler.removeCallbacks(searchResult.cleanupRunnable);
				}
			}
			mapAllSearches.clear();
		}

		synchronized (mapResultsListeners) {
			mapResultsListeners.clear();
		}
	}
}
