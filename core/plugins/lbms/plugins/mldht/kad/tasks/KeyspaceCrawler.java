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

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

import lbms.plugins.mldht.kad.*;
import lbms.plugins.mldht.kad.DHT.DHTtype;
import lbms.plugins.mldht.kad.Node.RoutingTableEntry;
import lbms.plugins.mldht.kad.messages.FindNodeRequest;
import lbms.plugins.mldht.kad.messages.FindNodeResponse;
import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.messages.MessageBase.Method;
import lbms.plugins.mldht.kad.messages.MessageBase.Type;
import lbms.plugins.mldht.kad.utils.PackUtil;

/**
 * @author The 8472
 *
 */
public class KeyspaceCrawler extends Task {
	
	Set<InetSocketAddress> responded = new HashSet<InetSocketAddress>();
	
	KeyspaceCrawler (RPCServerBase rpc, Node node) {
		super(Key.createRandomKey(),rpc, node);
		setInfo("Exhaustive Keyspace Crawl");
	}

	@Override
	synchronized void update () {
		// go over the todo list and send find node calls
		// until we have nothing left
		synchronized (todo) {

			while (todo.size() > 0 && canDoRequest()) {
				KBucketEntry e = todo.first();
				todo.remove(e);
				// only send a findNode if we haven't allready visited the node
				if (!visited.contains(e)) {
					// send a findNode to the node
					FindNodeRequest fnr;

					fnr = new FindNodeRequest(Key.createRandomKey());
					fnr.setWant4(rpc.getDHT().getType() == DHTtype.IPV4_DHT || DHT.getDHT(DHTtype.IPV4_DHT).getNode().getNumEntriesInRoutingTable() < DHTConstants.BOOTSTRAP_IF_LESS_THAN_X_PEERS);
					fnr.setWant6(rpc.getDHT().getType() == DHTtype.IPV6_DHT || DHT.getDHT(DHTtype.IPV6_DHT).getNode().getNumEntriesInRoutingTable() < DHTConstants.BOOTSTRAP_IF_LESS_THAN_X_PEERS);
					fnr.setDestination(e.getAddress());
					rpcCall(fnr,e.getID());


					if(canDoRequest())
					{
						fnr = new FindNodeRequest(e.getID());
						fnr.setWant4(rpc.getDHT().getType() == DHTtype.IPV4_DHT || DHT.getDHT(DHTtype.IPV4_DHT).getNode().getNumEntriesInRoutingTable() < DHTConstants.BOOTSTRAP_IF_LESS_THAN_X_PEERS);
						fnr.setWant6(rpc.getDHT().getType() == DHTtype.IPV6_DHT || DHT.getDHT(DHTtype.IPV6_DHT).getNode().getNumEntriesInRoutingTable() < DHTConstants.BOOTSTRAP_IF_LESS_THAN_X_PEERS);
						fnr.setDestination(e.getAddress());
						rpcCall(fnr,e.getID());						
					}
					
					synchronized (visited) {
						visited.add(e);
					}
				}
				// remove the entry from the todo list
			}
		}

		if (todo.size() == 0 && getNumOutstandingRequests() == 0
				&& !isFinished()) {
			done();
		} 
	}

	@Override
	void callFinished (RPCCallBase c, MessageBase rsp) {
		if (isFinished()) {
			return;
		}

		// check the response and see if it is a good one
		if (rsp.getMethod() == Method.FIND_NODE
				&& rsp.getType() == Type.RSP_MSG) {

			FindNodeResponse fnr = (FindNodeResponse) rsp;
			
			responded.add(fnr.getOrigin());
			
			for (DHTtype type : DHTtype.values())
			{
				byte[] nodes = fnr.getNodes(type);
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
							if (!node.allLocalIDs().contains(e.getID()) && !todo.contains(e) && !visited.contains(e))
							{
								todo.add(e);
							}
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


		}
	}
	
	@Override
	boolean canDoRequest() {
		return getNumOutstandingRequestsExcludingStalled() < DHTConstants.MAX_CONCURRENT_REQUESTS * 5;
	}
	
	@Override
	public
	void kill() {
		// do nothing to evade safeties
	}
	

	@Override
	void callTimeout (RPCCallBase c) {

	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.Task#start()
	 */
	@Override
	public
	void start() {
		int added = 0;

		// delay the filling of the todo list until we actually start the task
		
		outer: for (RoutingTableEntry bucket : node.getBuckets())
			for (KBucketEntry e : bucket.getBucket().getEntries())
				if (!e.isBad())
				{
					todo.add(e);
					added++;
				}
		super.start();
	}

	@Override
	protected void done () {
		super.done();
		System.out.println("crawled "+visited.size()+" nodes, seen "+responded.size());
	}
}
