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
import lbms.plugins.mldht.kad.messages.AnnounceRequest;
import lbms.plugins.mldht.kad.messages.GetPeersRequest;
import lbms.plugins.mldht.kad.messages.GetPeersResponse;
import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.messages.MessageBase.Method;
import lbms.plugins.mldht.kad.utils.PackUtil;

/**
 * @author Damokles
 *
 */
public class AnnounceTask extends Task {

	private int								port;
	private boolean							isSeed;
	
	public AnnounceTask (RPCServerBase rpc, Node node,
			Key info_hash, int port) {
		super(info_hash, rpc, node);
		this.port = port;

		DHT.logDebug("AnnounceTask started: " + getTaskID());
	}

	public void setSeed(boolean isSeed) {
		this.isSeed = isSeed;
	}

	@Override
	void callFinished (RPCCallBase c, MessageBase rsp) {}
	@Override
	void callTimeout (RPCCallBase c) {}
	
	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.Task#update()
	 */
	@Override
	synchronized void update () {
		synchronized (todo) {
			while (!todo.isEmpty() && canDoRequest()) {
				KBucketEntryAndToken e = (KBucketEntryAndToken) todo.first();
				todo.remove(e);
				if (!visited.contains(e)) {
					AnnounceRequest anr = new AnnounceRequest(targetKey, port, e.getToken());
					//System.out.println("sending announce to ID:"+e.getID()+" addr:"+e.getAddress());
					anr.setDestination(e.getAddress());
					anr.setSeed(isSeed);
					rpcCall(anr,e.getID());
					visited.add(e);
				}
			}
		}
		
		if (todo.isEmpty() && getNumOutstandingRequests() == 0 && !isFinished()) {
			done();
		} else if(getRecvResponses() == DHTConstants.MAX_ENTRIES_PER_BUCKET)
			done();
	}
	
	@Override
	boolean canDoRequest() {
		// a) we only announce to K nodes, not N; b) wait out the full timeout, not he adaptive one
		return getNumOutstandingRequests() < DHTConstants.MAX_ENTRIES_PER_BUCKET;
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

		super.start();
	}
}
