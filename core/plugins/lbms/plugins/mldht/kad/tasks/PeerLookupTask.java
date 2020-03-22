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
package lbms.plugins.mldht.kad.tasks;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import lbms.plugins.mldht.kad.*;
import lbms.plugins.mldht.kad.DHT.DHTtype;
import lbms.plugins.mldht.kad.KBucketEntry.DistanceOrder;
import lbms.plugins.mldht.kad.messages.*;
import lbms.plugins.mldht.kad.messages.MessageBase.Method;
import lbms.plugins.mldht.kad.utils.AddressUtils;
import lbms.plugins.mldht.kad.utils.PackUtil;

/**
 * @author Damokles
 *
 */
public class PeerLookupTask extends Task {

	private boolean							scrapeOnly;
	private boolean							noSeeds;
	private boolean							fastLookup;
	
	// nodes which have answered with tokens
	private List<KBucketEntryAndToken>		announceCanidates;
	private AnnounceResponseHandler			announceHandler;
	private ScrapeResponseHandler			scrapeHandler;

	private Set<PeerAddressDBItem>			returnedItems;
	private SortedSet<KBucketEntryAndToken>	closestSet;
	
	private int								validReponsesSinceLastClosestSetModification;
	AnnounceNodeCache						cache;



	public PeerLookupTask (RPCServerBase rpc, Node node,
			Key info_hash) {
		super(info_hash, rpc, node);
		announceCanidates = new ArrayList<KBucketEntryAndToken>(20);
		returnedItems = new HashSet<PeerAddressDBItem>();

		this.closestSet = new TreeSet<KBucketEntryAndToken>(new KBucketEntry.DistanceOrder(targetKey));
		cache = rpc.getDHT().getCache();
		// register key even before the task is started so the cache can already accumulate entries
		cache.register(targetKey);

		DHT.logDebug("PeerLookupTask started: " + getTaskID());
	}

	public void setScrapeHandler(ScrapeResponseHandler scrapeHandler) {
		this.scrapeHandler = scrapeHandler;
	}
	
	public void setAnounceHandler(AnnounceResponseHandler announceHandler) {
		this.announceHandler = announceHandler;
	}
	public void setNoSeeds(boolean avoidSeeds) {
		noSeeds = avoidSeeds;
	}
	
	public void setFastLookup(boolean isFastLookup) {
		if(!isQueued())
			throw new IllegalStateException("cannot change lookup mode after startup");
		fastLookup = isFastLookup;
	}

	public void setScrapeOnly(boolean scrapeOnly) {
		this.scrapeOnly = scrapeOnly;
	}
	
	public boolean isScrapeOnly() {
		return scrapeOnly;
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.Task#callFinished(lbms.plugins.mldht.kad.RPCCall, lbms.plugins.mldht.kad.messages.MessageBase)
	 */
	@Override
	void callFinished (RPCCallBase c, MessageBase rsp) {

		if (c.getMessageMethod() != Method.GET_PEERS) {
			return;
		}

		// it is either a GetPeersNodesRsp or a GetPeersValuesRsp
		GetPeersResponse gpr;
		if (rsp instanceof GetPeersResponse) {
			gpr = (GetPeersResponse) rsp;
		} else {
			return;
		}
		
		
		for (DHTtype type : DHTtype.values())
		{
			byte[] nodes = gpr.getNodes(type);
			if (nodes == null)
				continue;
			int nval = nodes.length / type.NODES_ENTRY_LENGTH;
			if (type == rpc.getDHT().getType())
			{
				synchronized (todo)
				{
					for (int i = 0; i < nval; i++)
					{
						// add node to todo list
						KBucketEntry e = PackUtil.UnpackBucketEntry(nodes, i * type.NODES_ENTRY_LENGTH, type);
						if(!AddressUtils.isBogon(e.getAddress()) && !node.allLocalIDs().contains(e.getID()) && !visited.contains(e))
							todo.add(e);
					}
				}

			} else
			{
				for (int i = 0; i < nval; i++)
				{
					KBucketEntry e = PackUtil.UnpackBucketEntry(nodes, i * type.NODES_ENTRY_LENGTH, type);
					DHT.getDHT(type).addDHTNode(e.getAddress().getAddress().getHostAddress(), e.getAddress().getPort());
				}
			}
		}

		List<DBItem> items = gpr.getPeerItems();
		boolean newItem = false;
		//if(items.size() > 0)
		//	System.out.println("unique:"+new HashSet<DBItem>(items).size()+" all:"+items.size()+" ver:"+gpr.getVersion()+" entries:"+items);
		for (DBItem item : items)
		{
			if(!(item instanceof PeerAddressDBItem))
				continue;
			PeerAddressDBItem it = (PeerAddressDBItem) item;
			// also add the items to the returned_items list
			if(!AddressUtils.isBogon(it)){
				if (returnedItems.add(it)){
					newItem = true;
				}
			}
		}
		
		KBucketEntry entry = new KBucketEntry(rsp.getOrigin(), rsp.getID());
		
		cache.add(entry);
		
		KBucketEntryAndToken toAdd = new KBucketEntryAndToken(entry, gpr.getToken());

		// if someone has peers he might have filters, collect for scrape
		if(!items.isEmpty())
		{
			if(scrapeHandler != null){
				scrapeHandler.addGetPeersRespone(gpr);
			}
			
			if (announceHandler != null && newItem ){
				announceHandler.itemsUpdated( this );
			}
		}
		
		if (gpr.getToken() != null)
		{ // add the peer who responded to the closest nodes list, so we can do an announce
			synchronized (announceCanidates)
			{
				announceCanidates.add(toAdd);
			}
		}
		
		// if we scrape we don't care about tokens.
		// otherwise we're only done if we have found the closest nodes with tokens
		if(scrapeOnly || gpr.getToken() != null)
		{
			synchronized (closestSet)
			{
				closestSet.add(toAdd);
				if (closestSet.size() > DHTConstants.MAX_ENTRIES_PER_BUCKET)
				{
					KBucketEntryAndToken last = closestSet.last();
					closestSet.remove(last);
					if (toAdd == last)
					{
						validReponsesSinceLastClosestSetModification++;
					} else
					{
						validReponsesSinceLastClosestSetModification = 0;
					}
				}
			}
		}
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.Task#callTimeout(lbms.plugins.mldht.kad.RPCCall)
	 */
	@Override
	void callTimeout (RPCCallBase c) {
		cache.removeEntry(c.getExpectedID());
	}
	
	@Override
	boolean canDoRequest() {
		if(scrapeOnly)
			return getNumOutstandingRequestsExcludingStalled() < DHTConstants.MAX_CONCURRENT_REQUESTS_LOWPRIO;
		return super.canDoRequest();
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.Task#update()
	 */
	@Override
	void update () {
		synchronized (todo) {
			// check if the cache has any closer nodes after the initial query
			todo.addAll(cache.get(targetKey, 3, visited));
			
			// go over the todo list and send get_peers requests
			// until we have nothing left
			while (!todo.isEmpty() && canDoRequest() && validReponsesSinceLastClosestSetModification < DHTConstants.MAX_CONCURRENT_REQUESTS) {
				KBucketEntry e = todo.first();
				todo.remove(e);
				// only send a findNode if we haven't already visited the node
				if (!visited.contains(e)) {
					// send a findNode to the node
					GetPeersRequest gpr = new GetPeersRequest(targetKey);
					gpr.setWant4(rpc.getDHT().getType() == DHTtype.IPV4_DHT || DHT.getDHT(DHTtype.IPV4_DHT).getNode().getNumEntriesInRoutingTable() < DHTConstants.BOOTSTRAP_IF_LESS_THAN_X_PEERS);
					gpr.setWant6(rpc.getDHT().getType() == DHTtype.IPV6_DHT || DHT.getDHT(DHTtype.IPV6_DHT).getNode().getNumEntriesInRoutingTable() < DHTConstants.BOOTSTRAP_IF_LESS_THAN_X_PEERS);
					gpr.setDestination(e.getAddress());
					gpr.setScrape(true);
					gpr.setNoSeeds(noSeeds);
					rpcCall(gpr,e.getID());
					visited.add(e);
				}
			}
		}
		
		int waitingFor = fastLookup ? getNumOutstandingRequestsExcludingStalled() : getNumOutstandingRequests();
		
		if (todo.isEmpty() && waitingFor == 0 && !isFinished()) {
			done();
		} else if(waitingFor == 0 && validReponsesSinceLastClosestSetModification >= DHTConstants.MAX_CONCURRENT_REQUESTS)
		{	// found all closest nodes, we're done
			done();
		}
	}

	@Override
	protected void done() {
		super.done();
	
		// feed the estimator if we have usable results
		if(validReponsesSinceLastClosestSetModification >= DHTConstants.MAX_CONCURRENT_REQUESTS)
			synchronized (closestSet)
			{
				SortedSet<Key> toEstimate = new TreeSet<Key>();
				for(KBucketEntryAndToken e : closestSet)
					toEstimate.add(e.getID());
				rpc.getDHT().getEstimator().update(toEstimate);
			}
		
		//System.out.println(returned_items);
		//System.out.println("overall:"+returnedItems.size());
	}
	
	public List<KBucketEntryAndToken> getAnnounceCanidates() {
		if(fastLookup)
			throw new IllegalStateException("cannot use fast lookups for announces");
		return announceCanidates;
	}


	/**
	 * @return the returned_items
	 */
	public Set<PeerAddressDBItem> getReturnedItems () {
		return Collections.unmodifiableSet(returnedItems);
	}

	/**
	 * @return the info_hash
	 */
	public Key getInfoHash () {
		return targetKey;
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.Task#start()
	 */
	@Override
	public
	void start () {
		//delay the filling of the todo list until we actually start the task
		KClosestNodesSearch kns = new KClosestNodesSearch(targetKey,
				DHTConstants.MAX_ENTRIES_PER_BUCKET * 4,rpc.getDHT());

		kns.fill();
		todo.addAll(kns.getEntries());
		
		// re-register once we actually started
		cache.register(targetKey);
		todo.addAll(cache.get(targetKey,DHTConstants.MAX_CONCURRENT_REQUESTS * 2,Collections.EMPTY_SET));

		super.start();
	}
}
