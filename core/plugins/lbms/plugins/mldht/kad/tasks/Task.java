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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import lbms.plugins.mldht.kad.*;
import lbms.plugins.mldht.kad.DHT.LogLevel;
import lbms.plugins.mldht.kad.messages.MessageBase;

/**
 * Performs a task on K nodes provided by a KClosestNodesSearch.
 * This is a base class for all tasks.
 *
 * @author Damokles
 */
public abstract class Task implements RPCCallListener {

	protected Set<KBucketEntry>			visited;		// nodes visited, can't use a HashSet here since we can't compute a good hashcode for KBucketEntries
	protected SortedSet<KBucketEntry>	todo;			// nodes todo
	protected Node						node;

	protected Key						targetKey;

	protected String					info;
	protected RPCServerBase				rpc;
	
	private AtomicInteger				outstandingRequestsExcludingStalled = new AtomicInteger();
	private AtomicInteger				outstandingRequests = new AtomicInteger();
	private int							sentReqs;
	private int							recvResponses;
	private int							failedReqs;
	private int							taskID;
	private boolean						taskFinished;
	private boolean						queued;
	private List<TaskListener>			listeners;
	private ScheduledFuture<?>			timeoutTimer;

	/**
	 * Create a task.
	 * @param rpc The RPC server to do RPC calls
	 * @param node The node
	 */
	Task (Key target, RPCServerBase rpc, Node node) {
		this.targetKey = target;
		this.rpc = rpc;
		this.node = node;
		queued = true;
		todo = new TreeSet<KBucketEntry>(new KBucketEntry.DistanceOrder(targetKey));
		visited = new KBucketEntry.BucketSet();
		taskFinished = false;
	}

	/**
	 * @param rpc The RPC server to do RPC calls
	 * @param node The node
	 * @param info info that should be displayed to the user, eg. download name on announce task
	 */
	Task (Key target, RPCServerBase rpc, Node node, String info) {
		this(target, rpc, node);
		this.info = info;
	}
	
	
	public RPCServerBase getRPC() {
		return rpc;
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCCallListener#onResponse(lbms.plugins.mldht.kad.RPCCall, lbms.plugins.mldht.kad.messages.MessageBase)
	 */
	@Override
	public void onResponse (RPCCallBase c, MessageBase rsp) {
		if(!c.wasStalled())
			outstandingRequestsExcludingStalled.decrementAndGet();
		outstandingRequests.decrementAndGet();

		recvResponses++;

		if (!isFinished()) {
			callFinished(c, rsp);

			if (canDoRequest() && !isFinished()) {
				update();
			}
		}
	}
	
	@Override
	public void onStall(RPCCallBase c)
	{
		outstandingRequestsExcludingStalled.decrementAndGet();
		
		if(!isFinished())
			callStalled(c);
			
		
		if (canDoRequest() && !isFinished()) {
			update();
		}
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCCallListener#onTimeout(lbms.plugins.mldht.kad.RPCCall)
	 */
	@Override
	public void onTimeout (RPCCallBase c) {
		
		if(!c.wasStalled())
			outstandingRequestsExcludingStalled.decrementAndGet();
		outstandingRequests.decrementAndGet();

		failedReqs++;

		if (!isFinished()) {
			callTimeout(c);

			if (canDoRequest() && !isFinished()) {
				update();
			}
		}
	}

	/**
	 *  Start the task, to be used when a task is queued.
	 */
	public void start () {
		if (queued) {
			DHT.logDebug("Starting Task: " + this.getClass().getSimpleName()
					+ " TaskID:" + taskID);
			queued = false;
			startTimeout();
			try
			{
				update();
			} catch (Exception e)
			{
				DHT.log(e, LogLevel.Error);
			}
		}
	}

	/**
	 * Will continue the task, this will be called every time we have
	 * rpc slots available for this task. Should be implemented by derived classes.
	 */
	abstract void update ();

	/**
	 * A call is finished and a response was received.
	 * @param c The call
	 * @param rsp The response
	 */
	abstract void callFinished (RPCCallBase c, MessageBase rsp);
	
	/**
	 * A call hasn't timed out yet but is estimated to be unlikely to finish, it will either time out or finish after this event has occured  
	 */
	void callStalled(RPCCallBase c) {}

	/**
	 * A call timedout
	 * @param c The call
	 */
	abstract void callTimeout (RPCCallBase c);

	/**
	 * Do a call to the rpc server, increments the outstanding_reqs variable.
	 * @param req THe request to send
	 * @return true if call was made, false if not
	 */
	boolean rpcCall (MessageBase req, Key expectedID) {
		if (!canDoRequest()) {
			return false;
		}

		RPCCallBase c = rpc.doCall(req);
		c.setExpectedID(expectedID);
		c.addListener(this);
		outstandingRequestsExcludingStalled.incrementAndGet();
		outstandingRequests.incrementAndGet();
		
		sentReqs++;
		return true;
	}

	/// See if we can do a request
	boolean canDoRequest () {
		return rpc.isRunning() && outstandingRequestsExcludingStalled.get() < DHTConstants.MAX_CONCURRENT_REQUESTS;
	}
	
	boolean hasUnfinishedRequests() {
		return outstandingRequests.get() > 0;
	}

	/// Is the task finished
	public boolean isFinished () {
		return taskFinished;
	}

	/// Set the task ID
	void setTaskID (int tid) {
		taskID = tid;
	}

	/// Get the task ID
	public int getTaskID () {
		return taskID;
	}

	/**
	 * @return the Count of Failed Requests
	 */
	public int getFailedReqs () {
		return failedReqs;
	}

	/**
	 * @return the Count of Received Responses
	 */
	public int getRecvResponses () {
		return recvResponses;
	}

	/**
	 * @return the Count of Sent Requests
	 */
	public int getSentReqs () {
		return sentReqs;
	}

	public int getTodoCount () {
		return todo.size();
	}

	/**
	 * @return the targetKey
	 */
	public Key getTargetKey () {
		return targetKey;
	}

	/**
	 * @return the info
	 */
	public String getInfo () {
		return info;
	}

	/**
	 * @param info the info to set
	 */
	public void setInfo (String info) {
		this.info = info;
	}

	public void addToTodo (KBucketEntry e) {
		synchronized (todo) {
			todo.add(e);
		}
	}

	/**
	 * @return number of requests that this task is actively waiting for 
	 */
	public int getNumOutstandingRequestsExcludingStalled () {
		return outstandingRequestsExcludingStalled.get();
	}
	
	/**
	 * @return number of requests that still haven't reached their final state but might have stalled
	 */
	public int getNumOutstandingRequests() {
		return outstandingRequests.get();
	}

	public boolean isQueued () {
		return queued;
	}

	/// Kills the task
	public void kill () {
		finished();
	}

	/**
	 * Starts the Timeout Timer
	 */
	private void startTimeout () {
		timeoutTimer = DHT.getScheduler().schedule(new Runnable() {
			/* (non-Javadoc)
			 * @see java.lang.Runnable#run()
			 */
			@Override
			public void run () {
				if (!taskFinished) {
					DHT.logDebug("Task was Killed by Timeout. TaskID: "
							+ taskID);
					kill();
				}
			}
		}, DHTConstants.TASK_TIMEOUT, TimeUnit.MILLISECONDS);
	}

	/**
	 * Add a node to the todo list
	 * @param ip The ip or hostname of the node
	 * @param port The port
	 */
	public void addDHTNode (InetAddress ip, int port) {
		InetSocketAddress addr = new InetSocketAddress(ip, port);
		synchronized (todo) {
			todo.add(new KBucketEntry(addr, Key.createRandomKey()));
		}
	}

	/**
	 * The task is finsihed.
	 * @param t The Task
	 */
	private void finished () {
		synchronized (this)
		{
			if(taskFinished)
				return;
			taskFinished = true;
		}
		
		DHT.logDebug("Task finished: " + getTaskID());
		if (timeoutTimer != null) {
			timeoutTimer.cancel(false);
		}
		if (listeners != null) {
			for (TaskListener tl : listeners) {
				tl.finished(this);
			}
		}
	}

	protected void done () {
			finished();
	}

	public void addListener (TaskListener listener) {
		if (listeners == null) {
			listeners = new ArrayList<TaskListener>(1);
		}
		// listener is added after the task already terminated, thus it won't get the event, trigger it manually
		if(taskFinished)
			listener.finished(this);
		listeners.add(listener);
	}

	public void removeListener (TaskListener listener) {
		if (listeners != null) {
			listeners.remove(listener);
		}
	}
}
