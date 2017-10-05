/*
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
package com.aelitis.azureus.plugins.xmwebui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.core.util.Base32;
import com.biglybt.core.util.RandomUtils;

import com.biglybt.core.content.ContentException;
import com.biglybt.core.content.RelatedAttributeLookupListener;
import com.biglybt.core.content.RelatedContentManager;

public class TagSearchInstance
{
	long numActiveLookups = 0;

	Map<String, List<String>> mapTags = new HashMap<String, List<String>>();

	private String sid;

	public TagSearchInstance() {
		byte[] bytes = new byte[16];

		RandomUtils.nextSecureBytes(bytes);

		sid = Base32.encode(bytes);
	}

	public void addSearch(final String hashString, byte[] torrentHash,
			String[] networks)
			throws ContentException {

		RelatedContentManager rcm = RelatedContentManager.getSingleton();
		synchronized (this) {
			numActiveLookups++;
		}
		try {
			rcm.lookupAttributes(torrentHash, networks,
					new RelatedAttributeLookupListener() {

						@Override
						public void tagFound(String tag, String network) {
							synchronized (mapTags) {
								List<String> list = mapTags.get(hashString);
								if (list == null) {
									list = new ArrayList<String>();
									mapTags.put(hashString, list);
								}
								list.add(tag);
							}
						}

						@Override
						public void lookupStart() {
						}

						@Override
						public void lookupFailed(ContentException error) {
						}

						@Override
						public void lookupComplete() {
							synchronized (TagSearchInstance.this) {
								numActiveLookups--;
							}
						}
					});
		} catch (Exception e) {
			synchronized (this) {
				numActiveLookups--;
			}
		}
	}

	public String getID() {
		return sid;
	}

	public boolean getResults(Map result) {

		result.put("id", sid);

		List<Map> torrentList = new ArrayList<Map>();
		result.put("torrents", torrentList);

		synchronized (this) {
			synchronized (mapTags) {
				for (String hash : mapTags.keySet()) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put(TransmissionVars.FIELD_TORRENT_HASH, hash);
					map.put("tags", new ArrayList<String>(mapTags.get(hash)));
					torrentList.add(map);
				}
			}
			boolean all_complete = numActiveLookups == 0;

			result.put("complete", all_complete);

			return all_complete;
		}
	}
}
