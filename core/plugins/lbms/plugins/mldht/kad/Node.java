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
import java.util.concurrent.ConcurrentHashMap;

import com.biglybt.core.util.Debug;
import com.biglybt.core.util.LightHashMap;

import lbms.plugins.mldht.DHTConfiguration;
import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.messages.MessageBase.Type;
import lbms.plugins.mldht.kad.tasks.NodeLookup;
import lbms.plugins.mldht.kad.tasks.PingRefreshTask;
import lbms.plugins.mldht.kad.tasks.Task;
import lbms.plugins.mldht.kad.tasks.TaskListener;
import lbms.plugins.mldht.kad.utils.AddressUtils;


/**
 * @author Damokles
 *
 */
public class Node {
	
	public static final class RoutingTableEntry implements Comparable<RoutingTableEntry> {
		
		public RoutingTableEntry(Prefix prefix, KBucket bucket) { 
			this.prefix = prefix;
			this.bucket = bucket;
		}
		
		public final Prefix prefix;
		private KBucket bucket;
		
		public KBucket getBucket() {
			return bucket;
		}
		
		@Override
		public int compareTo(RoutingTableEntry o) {
			return prefix.compareTo(o.prefix);
		}
	}

	private Object routingTableCoWLock = new Object();
	private volatile List<RoutingTableEntry> routingTable = new ArrayList<RoutingTableEntry>();
	private DHT dht;
	private int num_receives;
	
	private int numReceivesAtLastCheck;
	private long timeOfLastPingCheck;
	private long timeOfLastReceiveCountChange;
	private long timeOfRecovery;
	private boolean survivalMode;
	private int num_entries;
	private ConcurrentHashMap<Key, RPCServer> usedIDs = new ConcurrentHashMap<Key, RPCServer>();
	private volatile Map<InetSocketAddress,RoutingTableEntry> knownNodes = new HashMap<InetSocketAddress, RoutingTableEntry>();
	
	private static Map<String,Serializable> dataStore;

	/**
	 * @param srv
	 */
	public Node(DHT dht) {
		this.dht = dht;
		num_receives = 0;
		num_entries = 0;
		
		routingTable.add(new RoutingTableEntry(new Prefix(), new KBucket(this)));		
	}

	/**
	 * An RPC message was received, the node must now update
	 * the right bucket.
	 * @param dh_table The DHT
	 * @param msg The message
	 */
	void recieved (DHTBase dh_table, MessageBase msg) {
		
		KBucketEntry newEntry = new KBucketEntry(msg.getOrigin(), msg.getID());
		newEntry.setVersion(msg.getVersion());
		
		
		boolean nodeIDchanged = false;
		// to avoid scanning all buckets on each incoming packet we use a cache of Addresses we have in our buckets
		// this is inaccurate, but good enough to catch most node ID changes
		RoutingTableEntry cachedEntry = knownNodes.get(newEntry.getAddress()); 
		if(cachedEntry != null)
			nodeIDchanged = cachedEntry.bucket.checkForIDChange(msg);

		if(!nodeIDchanged)
		{
			if(msg.getType() == Type.RSP_MSG)
			{
				RoutingTableEntry entry = findBucketForId(msg.getID());
				entry.bucket.notifyOfResponse(msg);
			}
			
			insertEntry(newEntry,false);
		}
			
		


		num_receives++;
		
	}
	
	
	public void insertEntry (KBucketEntry entry, boolean internalInsert) {
		if(usedIDs.containsKey(entry.getID()) || AddressUtils.isBogon(entry.getAddress()))
			return;
		
		Key nodeID = entry.getID();
		
		RoutingTableEntry tableEntry = findBucketForId(nodeID);
		while(tableEntry.bucket.getNumEntries() >= DHTConstants.MAX_ENTRIES_PER_BUCKET && tableEntry.prefix.getDepth() < Key.KEY_BITS - 1)
		{
			boolean isLocalBucket = false;
			for(Key k : allLocalIDs())
				isLocalBucket |= tableEntry.prefix.isPrefixOf(k);
			if(!isLocalBucket)
				break;
			
			splitEntry(tableEntry);
			tableEntry = findBucketForId(nodeID);
		}
		
		int oldSize = tableEntry.bucket.getNumEntries();
		
		if(internalInsert)
			tableEntry.bucket.modifyMainBucket(null,entry);
		else
			tableEntry.bucket.insertOrRefresh(entry);
		
		// add delta to the global counter. inaccurate, but will be rebuilt by the bucket checks
		num_entries += tableEntry.bucket.getNumEntries() - oldSize;
		
	}
	
	private void splitEntry(RoutingTableEntry entry) {
		synchronized (routingTableCoWLock)
		{
			List<RoutingTableEntry> newTable = new ArrayList<Node.RoutingTableEntry>(routingTable);
			// check if we haven't entered the sync block after some other thread that did the same split operation
			if(!newTable.contains(entry))
				return;
			
			newTable.remove(entry);
			newTable.add(new RoutingTableEntry(entry.prefix.splitPrefixBranch(false), new KBucket(this)));
			newTable.add(new RoutingTableEntry(entry.prefix.splitPrefixBranch(true), new KBucket(this)));
			Collections.sort(newTable);
			routingTable = newTable;
			for(KBucketEntry e : entry.bucket.getEntries())
				insertEntry(e, true);
			for(KBucketEntry e : entry.bucket.getReplacementEntries())
				insertEntry(e, true);
		}
		
	}
	
	public static int findIdxForId(List<RoutingTableEntry> table, Key id) {
        int lowerBound = 0;
        int upperBound = table.size()-1;

        while (lowerBound <= upperBound) {
            int pivotIdx = (lowerBound + upperBound) >>> 1;
            Prefix pivot = table.get(pivotIdx).prefix;

            if(pivot.isPrefixOf(id))
            	return pivotIdx;

            if (pivot.compareTo(id) < 0)
           		lowerBound = pivotIdx + 1;
           	else
           		upperBound = pivotIdx - 1;
        }
        throw new IllegalStateException("This shouldn't happen, really");
	}
	
	public RoutingTableEntry findBucketForId(Key id) {
		List<RoutingTableEntry> table = routingTable;
		return table.get(findIdxForId(table, id));
	}

	/**
	 * @return OurID
	 */
	public Key getRootID () {
		if(dataStore != null)
			return (Key)dataStore.get("commonKey");
		// return a fake key if we're not initialized yet
		return Key.MIN_KEY;
	}
	
	public Set<Key> allLocalIDs() {
		// Sidestep covariance in J8, which returns KeySetView instead of Set.  Needed for Android
		return ((Map) usedIDs).keySet();
	}
	
	public DHT getDHT() {
		return dht;
	}

	/**
	 * Increase the failed queries count of the bucket entry we sent the message to
	*/
	void onTimeout (RPCCallBase call) {
		// don't timeout anything if we don't have a connection
		if(survivalMode)
			return;
		
		InetSocketAddress dest = call.getRequest().getDestination();
		
		if(call.getExpectedID() != null)
		{
			findBucketForId(call.getExpectedID()).bucket.onTimeout(dest);
		} else {
			RoutingTableEntry entry = knownNodes.get(dest);
			if(entry != null)
				entry.bucket.onTimeout(dest);
		}
			
	}
	
	public boolean isInSurvivalMode() {
		return survivalMode;
	}
	
	void removeServer(RPCServer server)
	{
		if(server.getDerivedID() != null)
			usedIDs.remove(server.getDerivedID(),server);
	}
	
	Key registerServer(RPCServer server)
	{
		int idx = 0;
		Key k = null;
		
		while(true)
		{
			k = getRootID().getDerivedKey(idx);
			if(usedIDs.putIfAbsent(k,server) == null)
				break;
			idx++;
		}

		return k;
	}
	
	
	

	/**
	 * Check if a buckets needs to be refreshed, and refresh if necesarry.
	 */
	public void doBucketChecks (long now) {
		
		/*
		for(RoutingTableEntry e : routingTable)
			System.out.println("\t"+e.prefix);
		System.out.println("----------------------");
		*/
		
		// don't do pings too often if we're not receiving anything (connection might be dead)
		if(num_receives != numReceivesAtLastCheck)
		{
			if(survivalMode)
			{
				if(timeOfRecovery == 0)
				{
					// received a packet! ping entries but don't exist survival mode yet
					timeOfRecovery = now;
					timeOfLastPingCheck = 0;
				}
					
				if(now - timeOfRecovery > DHTConstants.REACHABILITY_RECOVERY)
				{
					// ok, enough time passed, we should have recovered live nodes by now, exit survival mode
					survivalMode = false;
					timeOfRecovery = 0;
				}				
			}

			timeOfLastReceiveCountChange = now;
			numReceivesAtLastCheck = num_receives;
			
		} else if(now - timeOfLastReceiveCountChange > DHTConstants.REACHABILITY_TIMEOUT)
		{
			// haven't seen a packet for too long
			// perform heroics to maintain the routing table from now on
			survivalMode = true;
			for(RPCServer server : dht.getServers())
				server.getTimeoutFilter().reset();
			timeOfRecovery = 0;
		}
		
		// don't spam the checks if we're not receiving anything.
		// we don't want to cause stray packets somewhere in a network
		if(survivalMode && now - timeOfLastPingCheck > DHTConstants.BOOTSTRAP_MIN_INTERVAL)
			return;
		timeOfLastPingCheck = now;

		synchronized (routingTableCoWLock)
		{
			// perform bucket merge operations where possible
			for(int i=1;i<routingTable.size();i++)
			{
				RoutingTableEntry e1 = routingTable.get(i-1);
				RoutingTableEntry e2 = routingTable.get(i);

				if(e1.prefix.isSiblingOf(e2.prefix))
				{
					// uplift siblings if the other one is dead
					if(e1.getBucket().getNumEntries() == 0)
					{
						List<RoutingTableEntry> newTable = new ArrayList<Node.RoutingTableEntry>(routingTable);
						newTable.remove(e1);
						newTable.remove(e2);
						newTable.add(new RoutingTableEntry(e2.prefix.getParentPrefix(), e2.getBucket()));
						Collections.sort(newTable);
						routingTable = newTable;
						i--;continue;
					}

					if(e2.getBucket().getNumEntries() == 0)
					{
						List<RoutingTableEntry> newTable = new ArrayList<Node.RoutingTableEntry>(routingTable);
						newTable.remove(e1);
						newTable.remove(e2);
						newTable.add(new RoutingTableEntry(e1.prefix.getParentPrefix(), e1.getBucket()));
						Collections.sort(newTable);
						routingTable = newTable;
						i--;continue;

					}
					
					// check if the buckets can be merged without losing entries
					if(e1.getBucket().getNumEntries() + e2.getBucket().getNumEntries() < DHTConstants.MAX_ENTRIES_PER_BUCKET)
					{
						List<RoutingTableEntry> newTable = new ArrayList<Node.RoutingTableEntry>(routingTable);
						newTable.remove(e1);
						newTable.remove(e2);
						newTable.add(new RoutingTableEntry(e1.prefix.getParentPrefix(), new KBucket(this)));
						Collections.sort(newTable);
						routingTable = newTable;
						// no need to carry over replacements. there shouldn't be any, otherwise the bucket(s) would be full
						for(KBucketEntry e : e1.bucket.getEntries())
							insertEntry(e, true);
						for(KBucketEntry e : e2.bucket.getEntries())
							insertEntry(e, true);
						i--;continue;
					}
				}

			}

		}
		
		int newEntryCount = 0;
		
		for (RoutingTableEntry e : routingTable) {
			KBucket b = e.bucket;

			List<KBucketEntry> entries = b.getEntries();

			// remove boostrap nodes from our buckets
			boolean wasFull = b.getNumEntries() >= DHTConstants.MAX_ENTRIES_PER_BUCKET;
			boolean allBad = true;
			for (KBucketEntry entry : entries)
			{
				if (wasFull && DHTConstants.BOOTSTRAP_NODE_ADDRESSES.contains(entry.getAddress()))
					b.removeEntry(entry, true);
				if(allLocalIDs().contains(entry.getID()))
					b.removeEntry(entry, true);
				allBad &= entry.isBad();
				
				
			}

			// clean out buckets full of bad nodes. merge operations will do the rest
			if(!survivalMode && allBad)
			{
				e.bucket = new KBucket(this);
				continue;
			}
				
			
			if (b.needsToBeRefreshed())
			{
				// if the bucket survived that test, ping it
				DHT.logDebug("Refreshing Bucket: " + e.prefix);
				// the key needs to be the refreshed
				PingRefreshTask nl = dht.refreshBucket(b);
				if (nl != null)
				{
					b.setRefreshTask(nl);
					nl.setInfo("Refreshing Bucket #" + e.prefix);
				}

			} else if(!survivalMode)
			{
				// only replace 1 bad entry with a replacement bucket entry at a time (per bucket)
				b.checkBadEntries();
			}
			
			newEntryCount += e.bucket.getNumEntries();


		}
		
		num_entries = newEntryCount;
		
		rebuildAddressCache();
	}
	
	private void rebuildAddressCache() {
		Map<InetSocketAddress, RoutingTableEntry> newKnownMap = new LightHashMap<InetSocketAddress, RoutingTableEntry>(num_entries);
		List<RoutingTableEntry> table = routingTable;
		for(int i=0,n=table.size();i<n;i++)
		{
			RoutingTableEntry entry = table.get(i);
			List<KBucketEntry> entries = entry.bucket.getEntries();
			for(int j=0,m=entries.size();j<m;j++)
				newKnownMap.put(entries.get(j).getAddress(), entry);
		}
		
		knownNodes = newKnownMap;
	}

	/**
	 * Check if a buckets needs to be refreshed, and refresh if necesarry
	 *
	 * @param dh_table
	 */
	public void fillBuckets (DHTBase dh_table) {

		for (int i = 0;i<routingTable.size();i++) {
			RoutingTableEntry entry = routingTable.get(i);

			if (entry.bucket.getNumEntries() < DHTConstants.MAX_ENTRIES_PER_BUCKET) {
				DHT.logDebug("Filling Bucket: " + entry.prefix);

				NodeLookup nl = dh_table.fillBucket(entry.prefix.createRandomKeyFromPrefix(), entry.bucket);
				if (nl != null) {
					entry.bucket.setRefreshTask(nl);
					nl.setInfo("Filling Bucket #" + entry.prefix);
				}
			}
		}
	}

	/**
	 * Saves the routing table to a file
	 *
	 * @param file to save to
	 * @throws IOException
	 */
	void saveTable (File file, boolean forClose) throws IOException {
		if(dataStore == null){
			return;
		}
		
		ObjectOutputStream oos = null;
		
		File tempFile = new File(file.getPath()+".tmp");
		
		try {
			oos = new ObjectOutputStream(new FileOutputStream(tempFile));
			HashMap<String,Serializable> tableMap = new HashMap<String, Serializable>();
			
			dataStore.put("table"+dht.getType().name(), tableMap);
			
			tableMap.put("oldKey", getRootID());
			
			KBucket[] bucket = new KBucket[routingTable.size()];
			for(int i=0;i<bucket.length;i++)
				bucket[i] = routingTable.get(i).bucket;
				
			tableMap.put("bucket", bucket);
			tableMap.put("log2estimate", dht.getEstimator().getRawDistanceEstimate());
			tableMap.put("timestamp", System.currentTimeMillis());
			
			oos.writeObject(dataStore);
			oos.close();
			
			if(!file.exists() || file.delete())
				tempFile.renameTo(file);

		} finally {
			if (oos != null) {
				oos.close();
			}
		}
		
		if ( forClose ){
			dataStore = null;
		}
	}
	
	synchronized static void initDataStore(DHTConfiguration config)
	{
		File file = config.getNodeCachePath();
		
		if(dataStore != null)
			return;
		
		ObjectInputStream ois = null;
		try {
			if (!file.exists()) {
				return;
			}

			ois = new ObjectInputStream(new FileInputStream(file));
			dataStore = (Map<String, Serializable>) ois.readObject();
		} catch (Exception e)
		{
			e.printStackTrace();
		} finally {
			if(ois != null)
				try { ois.close(); } catch (IOException e) { e.printStackTrace(); }
			
			if(dataStore == null)
			{
				dataStore = new HashMap<String, Serializable>();
				dataStore.put("commonKey", Key.createRandomKey());
			}
		}
		
		if(!config.isPersistingID())
		{
			dataStore.put("commonKey", Key.createRandomKey());
		}
		
	}

	/**
	 * Loads the routing table from a file
	 *
	 * @param file
	 * @param runWhenLoaded is executed when all load operations are finished
	 * @throws IOException
	 */
	void loadTable (final Runnable runWhenLoaded) {
		boolean runDeferred = false;

		try {
			Map<String,Serializable> table = (Map<String,Serializable>)dataStore.get("table"+dht.getType().name());
			if(table == null)
				return;

			KBucket[] loadedBuckets = (KBucket[])table.get("bucket");
			Key oldID = (Key)table.get("oldKey");
			dht.getEstimator().setInitialRawDistanceEstimate((Double)table.get("log2estimate"));
			long timestamp = (Long)table.get("timestamp");



			// integrate loaded objects

			int entriesLoaded = 0;
			
			for(int i=0;i<loadedBuckets.length;i++)
			{
				KBucket b = loadedBuckets[i];
				if(b == null)
					continue;
				entriesLoaded += b.getNumEntries();
				entriesLoaded += b.getReplacementEntries().size();
				for(KBucketEntry e : b.getEntries())
					insertEntry(e,true);
				for(KBucketEntry e : b.getReplacementEntries())
					insertEntry(e,true);				
			}
			
			rebuildAddressCache();

			if (entriesLoaded > 0) {
				PingRefreshTask prt = dht.refreshBuckets(routingTable, true);
				if ( prt != null ){
					runDeferred = true;
					prt.setInfo("Pinging cached entries.");
					TaskListener bootstrapListener = new TaskListener() {
						@Override
						public void finished (Task t) {
							if (runWhenLoaded != null) {
								runWhenLoaded.run();
							}
						}
					};
					prt.addListener(bootstrapListener);
				}
			}

			DHT.logInfo("Loaded " + entriesLoaded + " from cache. Cache was "
					+ ((System.currentTimeMillis() - timestamp) / (60 * 1000))
					+ "min old. Reusing old id = " + oldID.equals(getRootID()));

			return;
		}catch( Throwable e ){
				// in case table is corrupted in some way
			
			DHT.logError( "Failed to load from cache: " + Debug.getNestedExceptionMessage(e));
			
		} finally {
			if (!runDeferred && runWhenLoaded != null) {
				runWhenLoaded.run();
			}
		}
	}

	/**
	 * Get the number of entries in the routing table
	 *
	 * @return
	 */
	public int getNumEntriesInRoutingTable () {
		return num_entries;
	}

	public List<RoutingTableEntry> getBuckets () {
		return Collections.unmodifiableList(routingTable) ;
	}

}
