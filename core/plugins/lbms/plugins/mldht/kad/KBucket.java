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

import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArraySet;

import lbms.plugins.mldht.kad.DHT.LogLevel;
import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.messages.PingRequest;
import lbms.plugins.mldht.kad.messages.MessageBase.Type;
import lbms.plugins.mldht.kad.tasks.Task;

/**
 * A KBucket is just a list of KBucketEntry objects.
 *
 * The list is sorted by time last seen :
 * The first element is the least recently seen, the last
 * the most recently seen.
 *
 * @author Damokles
 */
public class KBucket implements Externalizable {

	private static final long	serialVersionUID	= -5507455162198975209L;
	
	/**
	 * use {@link #insertOrRefresh}, {@link #sortedInsert} or {@link #removeEntry} to handle this<br>
	 * using copy-on-write semantics for this list, referencing it is safe if you make local copy 
	 */
	private volatile List<KBucketEntry>	entries;
	// replacements are synchronized on entries, not on replacementBucket! using a liked list since we use it as queue, not as stack
	private ConcurrentLinkedQueue<KBucketEntry>	replacementBucket;
	
	private Node						node;
	private Map<Key,KBucketEntry>		pendingPings;
	private long						last_modified;
	private Task						refresh_task;
	
	public KBucket () {
		entries = new ArrayList<KBucketEntry>(); // using arraylist here since reading/iterating is far more common than writing.
		replacementBucket = new ConcurrentLinkedQueue<KBucketEntry>();
		// .size() is called on every insert, it's rarely used and is limited to 2... so use a cow set for high throughput
		pendingPings = new ConcurrentSkipListMap<Key, KBucketEntry>();
	}

	public KBucket (Node node) {
		this();
		//last_modified = System.currentTimeMillis();
		this.node = node;
	}

	/**
	 * Notify bucket of new incoming packet from a node, perform update or insert existing nodes where appropriate
	 * @param entry The entry to insert
	 */
	public void insertOrRefresh (KBucketEntry entry) {
		if (entry == null)
			return;
		
		List<KBucketEntry> entriesRef = entries;
		
		int idx = entriesRef.indexOf(entry);
		if (idx != -1)
		{
			// If in the list, move it to the end
			final KBucketEntry oldEntry = entriesRef.get(idx);
			final KBucketEntry newEntry = entry;
			if (!oldEntry.getAddress().equals(entry.getAddress()))
			{
				// node changed address, check if old node is really dead to prevent impersonation
				pingEntry(oldEntry, new RPCCallListener() {
					 
					@Override
					public void onTimeout(RPCCallBase c) {
						modifyMainBucket(oldEntry, newEntry);
						DHT.log("Node "+oldEntry.getID()+" changed address from "+oldEntry.getAddress()+" to "+newEntry.getAddress(), LogLevel.Info);
					}
					
					@Override
					public void onStall(RPCCallBase c) {}
					
					@Override
					public void onResponse(RPCCallBase c, MessageBase rsp) {
						DHT.log("New node "+newEntry.getAddress()+" claims same Node ID ("+oldEntry.getID()+") as "+oldEntry.getAddress()+" ; node dropped as this might be an impersonation attack", LogLevel.Error);
					}
				});
				return;
			}
			
			// only refresh the last seen time if it already exists
			oldEntry.mergeTimestamps(newEntry);
			adjustTimerOnInsert(oldEntry);
			return;
		}
		
		
		if (entriesRef.size() < DHTConstants.MAX_ENTRIES_PER_BUCKET)
		{
			// insert if not already in the list and we still have room
			modifyMainBucket(null,entry);
			return;
		}

		if (replaceBadEntry(entry))
			return;

		// older entries displace younger ones (this code path is mostly used on changing node IDs)
		KBucketEntry youngest = entriesRef.get(entriesRef.size()-1);
		
		if (youngest.getCreationTime() > entry.getCreationTime())
		{
			modifyMainBucket(youngest,entry);
			// it was a useful entry, see if we can use it to replace something questionable
			pingQuestionable(youngest);
			return;
		}
		
		pingQuestionable(entry);
	}
	
	
	/**
	 * mostly meant for internal use or transfering entries into a new bucket.
	 * to update a bucket properly use {@link #insertOrRefresh(KBucketEntry)} 
	 */
	public void modifyMainBucket(KBucketEntry toRemove, KBucketEntry toInsert) {
	
		synchronized (this)
		{ // we're synchronizing all modifications, therefore we can freely reference the old entry list, it will not be modified concurrently
			if(entries.contains(toInsert))
				return;
			
			List<KBucketEntry> newEntries = new ArrayList<KBucketEntry>(entries);
			boolean removed = false;
			boolean added = false;
			
			// removal never violates ordering constraint, no checks required
			if(toRemove != null)
				removed = newEntries.remove(toRemove);
			
			

			if(toInsert != null)
			{
				int oldSize = newEntries.size();
				boolean wasFull = oldSize >= DHTConstants.MAX_ENTRIES_PER_BUCKET;
				KBucketEntry youngest = oldSize > 0 ? newEntries.get(oldSize-1) : null;
				boolean unorderedInsert = youngest != null && toInsert.getCreationTime() < youngest.getCreationTime();
				added = !wasFull || unorderedInsert;
				if(added)
				{
					newEntries.add(toInsert);
					adjustTimerOnInsert(toInsert);
				} else
				{
					insertInReplacementBucket(toInsert);
				}
				
				if(unorderedInsert)
					Collections.sort(newEntries,KBucketEntry.AGE_ORDER);
				
				if(wasFull && added)
					while(newEntries.size() > DHTConstants.MAX_ENTRIES_PER_BUCKET)
						insertInReplacementBucket(newEntries.remove(newEntries.size()-1));
			}
			
			// make changes visible
			if(added || removed)
				entries = newEntries;
		}
	}

	/**
	 * Get the number of entries.
	 *
	 * @return The number of entries in this Bucket
	 */
	public int getNumEntries () {
		return entries.size();
	}

	/**
	 * @return the entries
	 */
	public List<KBucketEntry> getEntries () {
		return new ArrayList<KBucketEntry>(entries);
	}
	
	public List<KBucketEntry> getReplacementEntries() {
		return new ArrayList<KBucketEntry>(replacementBucket);
	}

	/**
	 * Checks if this bucket contains an entry.
	 *
	 * @param entry Entry to check for
	 * @return true if found
	 */
	public boolean contains (KBucketEntry entry) {
		return entries.contains(entry);
	}

	/**
	 * A peer failed to respond
	 * @param addr Address of the peer
	 */
	public boolean onTimeout (InetSocketAddress addr) {
		List<KBucketEntry> entriesRef = entries;
		for (int i = 0, n=entriesRef.size(); i < n; i++)
		{
			KBucketEntry e = entriesRef.get(i);
			if (e.getAddress() == addr)
			{
				e.signalRequestTimeout();
				//only removes the entry if it is bad
				removeEntry(e, false);
				return true;
			}
		}
		return false;
	}

	/**
	 * Check if the bucket needs to be refreshed
	 *
	 * @return true if it needs to be refreshed
	 */
	public boolean needsToBeRefreshed () {
		long now = System.currentTimeMillis();
		if(refresh_task != null && refresh_task.isFinished())
			refresh_task = null;
		

		return (now - last_modified > DHTConstants.BUCKET_REFRESH_INTERVAL
				&& refresh_task == null && entries.size() > 0);
	}

	/**
	 * Resets the last modified for this Bucket
	 */
	public void updateRefreshTimer () {
		last_modified = System.currentTimeMillis();
	}
	

	public String toString() {
		return "entries: "+entries+" replacements: "+replacementBucket;
	}

	private void adjustTimerOnInsert(KBucketEntry entry)
	{
		if(entry.getLastSeen() > last_modified) {
			last_modified = entry.getLastSeen();
		}
	}

	/**
	 * Pings an entry and notifies the listener of the result.
	 *
	 * @param entry entry to ping
	 * @return true if the ping was sent, false if there already is an outstanding ping for that entry
	 */
	private boolean pingEntry(final KBucketEntry entry, RPCCallListener listener)
	{
		// don't ping if there already is an outstanding ping
		if(pendingPings.containsKey(entry.getID())) {
	        return false;
        }

		RPCServer server = node.getDHT().getRandomServer();
		
		if ( server == null ){
			return( false );
		}
		
		PingRequest p = new PingRequest();
		p.setDestination(entry.getAddress());
		RPCCall c = server.doCall(p);
		c.setExpectedID(entry.getID());
		if (c != null) {
			pendingPings.put(entry.getID(),entry);
			c.addListener(listener);
			c.addListener(new RPCCallListener() {
				@Override
				public void onTimeout(RPCCallBase c) {
					pendingPings.remove(entry.getID());
				}
				
				// performance doesn't matter much here, ignore stalls
				@Override
				public void onStall(RPCCallBase c) {}
				
				@Override
				public void onResponse(RPCCallBase c, MessageBase rsp) {
					pendingPings.remove(entry.getID());
				}
			});
			return true;
		}
		return false;
	}
	
	private void pingQuestionable (final KBucketEntry replacement_entry) {
		if (pendingPings.size() >= 2) {
			insertInReplacementBucket(replacement_entry);
			return;
		}

		// we haven't found any bad ones so try the questionable ones
		for (final KBucketEntry toTest : entries)
		{
			if (toTest.isQuestionable() && pingEntry(toTest, new RPCCallListener() {
				@Override
				public void onTimeout(RPCCallBase c) {
					modifyMainBucket(toTest, replacement_entry);
					// we could replace this one, try another one.
					KBucketEntry nextReplacementEntry;
					nextReplacementEntry = getYoungestReplacementEntry();
					if (nextReplacementEntry != null && !replaceBadEntry(nextReplacementEntry))
						pingQuestionable(nextReplacementEntry);
				}

				// performance doesn't matter much here. ignore stall
				@Override
				public void onStall(RPCCallBase c) {}

				@Override
				public void onResponse(RPCCallBase c, MessageBase rsp) {
					// it's alive, check another one
					if (!replaceBadEntry(replacement_entry))
					{
						pingQuestionable(replacement_entry);
					}
				}
			}))
			{
				return;
			}
		}

		//save the entry if all are good
		insertInReplacementBucket(replacement_entry);

	}

	/**
	 * Tries to instert entry by replacing a bad entry.
	 *
	 * @param entry Entry to insert
	 * @return true if replace was successful
	 */
	private boolean replaceBadEntry (KBucketEntry entry) {
		List<KBucketEntry> entriesRef = entries; 
		for (int i = 0,n=entriesRef.size();i<n;i++) {
			KBucketEntry e = entriesRef.get(i);
			if (e.isBad()) {
				// bad one get rid of it
				modifyMainBucket(e, entry);
				return true;
			}
		}
		return false;
	}
	
	private KBucketEntry getYoungestReplacementEntry()
	{
		// fetches the last element from the queue. since it's not double-ended we have to iterate through it
		for(Iterator<KBucketEntry> it = replacementBucket.iterator();it.hasNext();)
		{
			KBucketEntry e = it.next();
			if(!it.hasNext())
			{
				it.remove();
				return e;
			}
		}
		return null;
	}
	
	private void insertInReplacementBucket(KBucketEntry entry)
	{
		if(entry == null)
			return;
		
		// start counting from 1 since we'll add 1 entry
		int size = 1; 
		
		//if it is already inserted remove it and add it to the end
		for(Iterator<KBucketEntry> it = replacementBucket.iterator();it.hasNext();)
		{
			KBucketEntry oldEntry = it.next();
			if(oldEntry.equals(entry))
			{
				it.remove();
				oldEntry.mergeTimestamps(entry);
				replacementBucket.add(oldEntry);
				return;
			}
			size++;
		}
		
		replacementBucket.add(entry);
		
		while(size-- > DHTConstants.MAX_ENTRIES_PER_BUCKET)
			replacementBucket.poll(); //remove the least recently seen one
	}
	
	/**
	 * only removes one bad entry at a time to prevent sudden flushing of the routing table	
	 */
	public void checkBadEntries() {
		List<KBucketEntry> entriesRef = entries;
		KBucketEntry toRemove = null;
		
		for (int i=0,n=entriesRef.size();i<n;i++)
		{
			KBucketEntry e = entriesRef.get(i);
			if (!e.isBad())
				continue;
			toRemove = e;
			break;
		}
		if (toRemove == null)
			return;
		
		KBucketEntry replacement = getYoungestReplacementEntry();
		if (replacement != null)
			modifyMainBucket(toRemove, replacement);
	}
	
	public boolean checkForIDChange(MessageBase msg)
	{
		List<KBucketEntry> entriesRef = entries;
		// check if node changed its ID
		for (int i = 0, n = entriesRef.size(); i < n; i++)
		{
			KBucketEntry entry = entriesRef.get(i);
			// node ID change detected, reassign node to the appropriate bucket
			if (entry.getAddress().equals(msg.getOrigin()) && !entry.getID().equals(msg.getID()))
			{
				removeEntry(entry, true); //remove and replace from replacement bucket
				DHT.log("Node " + entry.getAddress() + " changed ID from " + entry.getID() + " to " + msg.getID(), LogLevel.Info);
				KBucketEntry newEntry = new KBucketEntry(entry.getAddress(), msg.getID(), entry.getCreationTime());
				newEntry.mergeTimestamps(entry);
				// insert into appropriate bucket for the new ID
				node.insertEntry(newEntry, false);
				return true;
			}
			// no node ID change detected, update last responded. insert will be invoked soon, thus we don't have to do the move-to-end stuff				
			if (msg.getType() == Type.RSP_MSG && entry.getID().equals(msg.getID()))
				entry.signalResponse();
		}
		
		return false;
	}
	
	public void notifyOfResponse(MessageBase msg)
	{
		if(msg.getType() != Type.RSP_MSG)
			return;
		List<KBucketEntry> entriesRef = entries;
		for (int i=0, n = entriesRef.size();i<n;i++)
		{
			KBucketEntry entry = entriesRef.get(i);
			
			// update last responded. insert will be invoked soon, thus we don't have to do the move-to-end stuff				
			if(entry.getID().equals(msg.getID()))
			{
				entry.signalResponse();
				return;
			}
		}
	}


	/**
	 * @param toRemove Entry to remove, if its bad
	 * @param force if true entry will be removed regardless of its state
	 */
	public void removeEntry(KBucketEntry toRemove, boolean force) {
		List<KBucketEntry> entriesRef = entries;
		if (entriesRef.contains(toRemove) && (force || toRemove.isBad()))
		{
			KBucketEntry replacement = null;
			replacement = getYoungestReplacementEntry();

			// only remove if we have a replacement or really need to
			if(replacement != null || force)
				modifyMainBucket(toRemove,replacement);					
		}
		
	}

	/**
	 * @param node the node to set
	 */
	public void setNode (Node node) {
		this.node = node;
	}

	/**
	 * @param refresh_task the refresh_task to set
	 */
	public void setRefreshTask (Task refresh_task) {
		this.refresh_task = refresh_task;
	}
	
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		Map<String,Object> serialized = (Map<String, Object>) in.readObject();
		Object obj = serialized.get("mainBucket");
		if(obj instanceof Collection<?>)
			entries.addAll((Collection<KBucketEntry>)obj);
		obj = serialized.get("replacementBucket");
		if(obj instanceof Collection<?>)
			replacementBucket.addAll((Collection<KBucketEntry>)obj);
		obj = serialized.get("lastModifiedTime");
		if(obj instanceof Long)
			last_modified = (Long)obj;
		obj = serialized.get("prefix");
		
		entries.removeAll(Collections.singleton(null));
		replacementBucket.removeAll(Collections.singleton(null));
		Collections.sort(entries, KBucketEntry.AGE_ORDER);
		//Collections.sort(replacementBucket,KBucketEntry.LAST_SEEN_ORDER);
	}
	
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		Map<String,Object> serialized = new HashMap<String, Object>();
		// put entries as any type of collection, will convert them on deserialisation
		serialized.put("mainBucket", entries);
		serialized.put("replacementBucket", replacementBucket);
		serialized.put("lastModifiedTime", last_modified);

		out.writeObject(serialized);
	}
}
