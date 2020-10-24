/*
 *    This file is part of mlDHT. 
 * 
 *    mlDHT is free software: you can redistribute it and/or modify 
 *    it under the terms of the GNU General Public License as published by 
 *    the Free Software Foundation, either version 2 of the License, or 
 *    (at your option) any later version. 
 * 
 *    mlDHT is distributed in the hope that it will be useful, 
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of 
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 *    GNU General Public License for more details. 
 * 
 *    You should have received a copy of the GNU General Public License 
 *    along with mlDHT.  If not, see <http://www.gnu.org/licenses/>. 
 */
package lbms.plugins.mldht.kad;

import java.net.InetSocketAddress;
import java.util.*;

import lbms.plugins.mldht.kad.DHT.DHTtype;
import lbms.plugins.mldht.kad.Node.RoutingTableEntry;
import lbms.plugins.mldht.kad.utils.PackUtil;

/**
 * @author Damokles
 *
 */
public class KClosestNodesSearch {
	private Key							targetKey;
	private List<KBucketEntry>			entries;
	private int							max_entries;
	private DHT							owner;
	private Comparator<KBucketEntry> comp;

	/**
	 * Constructor sets the key to compare with
	 * @param key The key to compare with
	 * @param max_entries The maximum number of entries can be in the map
	 * @return
	 */
	public KClosestNodesSearch (Key key, int max_entries, DHT owner) {
		this.targetKey = key;
		this.owner = owner;
		this.max_entries = max_entries;
		this.comp = new KBucketEntry.DistanceOrder(key);
		entries = new ArrayList<KBucketEntry>(max_entries + DHTConstants.MAX_ENTRIES_PER_BUCKET);
	}

	/**
	 * @return the Target key of the search
	 */
	public Key getSearchTarget () {
		return targetKey;
	}

	/**
	 * @return the number of entries
	 */
	public int getNumEntries () {
		return entries.size();
	}

	public void fill()
	{
		fill(false);
	}
	
	/**
	 * @return true if we're done
	 */
	private boolean insertBucket(KBucket bucket) {
		Key farthest = entries.size() > 0 ? entries.get(entries.size()-1).getID() : null;
		List<KBucketEntry> bucketEntries = bucket.getEntries();
		KBucketEntry e;
		for(int i=0,n=bucketEntries.size();i<n;i++)
		{
			e = bucketEntries.get(i);
			if(!e.isBad())
				entries.add(e);
		}
		Collections.sort(entries,comp);
		for(int i=entries.size()-1;i>=max_entries;i--)
			entries.remove(i);
		if(entries.size() > 0 && farthest == entries.get(entries.size()-1).getID())
			return true;
		return false;
		
	}
	
	public void fill(boolean includeOurself) {
		List<RoutingTableEntry> table = owner.getNode().getBuckets();
		int center = Node.findIdxForId(table, targetKey);
		boolean reachedMin;
		boolean reachedMax;
		reachedMax = reachedMin = insertBucket(table.get(center).getBucket());
		for(int i=1;!reachedMax && !reachedMin;i++)
		{
			reachedMin = reachedMin || center-i < 0 || insertBucket(table.get(center-i).getBucket());
			reachedMax = reachedMax || center+i >= table.size() || insertBucket(table.get(center+i).getBucket());
		}
		
		
		if(		includeOurself ){
			
			RPCServer srv = owner.getRandomServer();
			
			if ( 	srv != null &&
					srv.getPublicAddress() != null && 
					entries.size() < max_entries ){
			
				InetSocketAddress sockAddr = new InetSocketAddress(srv.getPublicAddress(), srv.getPort());
				entries.add(new KBucketEntry(sockAddr, srv.getDerivedID()));
			}
		}
	}

	public boolean isFull () {
		return entries.size() >= max_entries;
	}

	
	/**
	 * Packs the results in a byte array.
	 *
	 * @return the encoded results.
	 */
	public byte[] pack () {
		if(entries.size() == 0)
			return null;
		int entryLength = owner.getType().NODES_ENTRY_LENGTH;
		
		byte[] buffer = new byte[entries.size() * entryLength];
		int max_items = buffer.length / 26;
		int j = 0;

		for (KBucketEntry e : entries) {
			if (j >= max_items) {
				break;
			}
			PackUtil.PackBucketEntry(e, buffer, j * entryLength, owner.getType());
			j++;
		}
		return buffer;
	}

	/**
	 * @return a unmodifiable List of the entries
	 */
	public List<KBucketEntry> getEntries () {
		return Collections.unmodifiableList(entries);
	}
}
