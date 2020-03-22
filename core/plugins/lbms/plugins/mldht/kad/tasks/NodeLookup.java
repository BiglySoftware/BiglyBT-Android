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

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import lbms.plugins.mldht.kad.*;
import lbms.plugins.mldht.kad.DHT.DHTtype;
import lbms.plugins.mldht.kad.Key.DistanceOrder;
import lbms.plugins.mldht.kad.Node.RoutingTableEntry;
import lbms.plugins.mldht.kad.messages.*;
import lbms.plugins.mldht.kad.messages.MessageBase.Method;
import lbms.plugins.mldht.kad.messages.MessageBase.Type;
import lbms.plugins.mldht.kad.utils.AddressUtils;
import lbms.plugins.mldht.kad.utils.PackUtil;

/**
 * @author Damokles
 *
 */
public class NodeLookup extends Task {
	private int						validReponsesSinceLastClosestSetModification;
	SortedSet<Key>			closestSet;
	private Map<MessageBase, Key>	lookupMap;
	private boolean forBootstrap = false;
	
	public NodeLookup (Key node_id, RPCServerBase rpc, Node node, boolean isBootstrap) {
		super(node_id, rpc, node);
		forBootstrap = isBootstrap;
		this.closestSet = new TreeSet<Key>(new Key.DistanceOrder(targetKey));
		this.lookupMap = new HashMap<MessageBase, Key>();
	}

	@Override
	void update () {
		// go over the todo list and send find node calls
		// until we have nothing left
		synchronized (todo) {

			while (todo.size() > 0 && canDoRequest() && validReponsesSinceLastClosestSetModification < DHTConstants.MAX_CONCURRENT_REQUESTS) {
				KBucketEntry e = todo.first();
				todo.remove(e);
				// only send a findNode if we haven't allready visited the node
				if (!visited.contains(e)) {
					// send a findNode to the node
					FindNodeRequest fnr = new FindNodeRequest(targetKey);
					fnr.setWant4(rpc.getDHT().getType() == DHTtype.IPV4_DHT || DHT.getDHT(DHTtype.IPV4_DHT).getNode() != null && DHT.getDHT(DHTtype.IPV4_DHT).getNode().getNumEntriesInRoutingTable() < DHTConstants.BOOTSTRAP_IF_LESS_THAN_X_PEERS);
					fnr.setWant6(rpc.getDHT().getType() == DHTtype.IPV6_DHT || DHT.getDHT(DHTtype.IPV6_DHT).getNode() != null && DHT.getDHT(DHTtype.IPV6_DHT).getNode().getNumEntriesInRoutingTable() < DHTConstants.BOOTSTRAP_IF_LESS_THAN_X_PEERS);
					fnr.setDestination(e.getAddress());
					synchronized (lookupMap) {
						lookupMap.put(fnr, e.getID());
					}
					rpcCall(fnr,e.getID());
					visited.add(e);
				}
				// remove the entry from the todo list
			}
		}

		if (todo.size() == 0 && getNumOutstandingRequests() == 0
				&& !isFinished()) {
			done();
		} else if (getNumOutstandingRequests() == 0 && validReponsesSinceLastClosestSetModification >= DHTConstants.MAX_CONCURRENT_REQUESTS) {
			done(); // quit after 10 nodes responsed
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

			MessageBase b = c.getRequest();
			synchronized (lookupMap) {
				if (lookupMap.containsKey(b)) {
					synchronized (closestSet) {
						Key toAdd = lookupMap.remove(b);
						closestSet.add(toAdd);
						if (closestSet.size() > DHTConstants.MAX_ENTRIES_PER_BUCKET) {
							Key last = closestSet.last();
							closestSet.remove(last);
							if (toAdd == last) {
								validReponsesSinceLastClosestSetModification++;
							} else {
								validReponsesSinceLastClosestSetModification = 0;
							}
						}
					}
				}
			}
			
			FindNodeResponse fnr = (FindNodeResponse) rsp;
			
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
							if (!AddressUtils.isBogon(e.getAddress()) && !node.allLocalIDs().contains(e.getID()) && !todo.contains(e) && !visited.contains(e))
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
	void callTimeout (RPCCallBase c) {

	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.Task#start()
	 */
	@Override
	public
	void start () {
		int added = 0;

		// if we're bootstrapping start from the bucket that has the greatest possible distance from ourselves so we discover new things along the (longer) path
		Key knsTargetKey = forBootstrap ? targetKey.getDerivedKey(0xFFFFFFFF) : targetKey;
		
		// delay the filling of the todo list until we actually start the task
		KClosestNodesSearch kns = new KClosestNodesSearch(knsTargetKey, 3 * DHTConstants.MAX_ENTRIES_PER_BUCKET, rpc.getDHT());
		kns.fill();
		todo.addAll(kns.getEntries());
		

		super.start();
	}

	@Override
	protected void done () {
		super.done();

		rpc.getDHT().getEstimator().update(new TreeSet<Key>(closestSet));
	}
}
