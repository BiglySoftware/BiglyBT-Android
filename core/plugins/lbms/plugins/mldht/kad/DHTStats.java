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

/**
 * @author Damokles
 *
 */
public class DHTStats {

	private DatabaseStats	dbStats;

	private RPCStats		rpcStats;

	private long			startedTimestamp;

	/// number of peers in the routing table
	private int				numPeers;
	/// Number of running tasks
	private int				numTasks;

	private int				numReceivedPackets;

	private int				numSentPackets;

	private int				numRpcCalls;

	/**
	 * @return the num_peers
	 */
	public int getNumPeers () {
		return numPeers;
	}

	/**
	 * @return the num_tasks
	 */
	public int getNumTasks () {
		return numTasks;
	}

	/**
	 * @return the num_received_packets
	 */
	public int getNumReceivedPackets () {
		return numReceivedPackets;
	}

	/**
	 * @return the num_sent_packets
	 */
	public int getNumSentPackets () {
		return numSentPackets;
	}

	/**
	 * @return the numRpcCalls
	 */
	public int getNumRpcCalls () {
		return numRpcCalls;
	}

	/**
	 * @return the dbStats
	 */
	public DatabaseStats getDbStats () {
		return dbStats;
	}

	/**
	 * @return the rpcStats
	 */
	public RPCStats getRpcStats () {
		return rpcStats;
	}

	/**
	 * @return the startedTimestamp
	 */
	public long getStartedTimestamp () {
		return startedTimestamp;
	}

	/**
	 * @param num_peers the num_peers to set
	 */
	protected void setNumPeers (int num_peers) {
		this.numPeers = num_peers;
	}

	/**
	 * @param num_tasks the num_tasks to set
	 */
	protected void setNumTasks (int num_tasks) {
		this.numTasks = num_tasks;
	}

	/**
	 * @param num_received_packets the num_received_packets to set
	 */
	protected void setNumReceivedPackets (int num_received_packets) {
		this.numReceivedPackets = num_received_packets;
	}

	/**
	 * @param num_sent_packets the num_sent_packets to set
	 */
	protected void setNumSentPackets (int num_sent_packets) {
		this.numSentPackets = num_sent_packets;
	}

	/**
	 * @param numRpcCalls the numRpcCalls to set
	 */
	protected void setNumRpcCalls (int numRpcCalls) {
		this.numRpcCalls = numRpcCalls;
	}

	/**
	 * @param dbStats the dbStats to set
	 */
	protected void setDbStats (DatabaseStats dbStats) {
		this.dbStats = dbStats;
	}

	/**
	 * @param rpcStats the rpcStats to set
	 */
	protected void setRpcStats (RPCStats rpcStats) {
		this.rpcStats = rpcStats;
	}

	protected void resetStartedTimestamp () {
		startedTimestamp = System.currentTimeMillis();
	}
}
