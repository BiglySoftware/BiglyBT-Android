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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.messages.MessageBase.Method;

/**
 * @author Damokles
 *
 */
public class RPCCall implements RPCCallBase {

	private MessageBase				msg;
	private RPCServer				rpc;
	private boolean					queued			= true;
	private boolean					stalled			= false;
	private List<RPCCallListener>	listeners;
	private ScheduledFuture<?>		timeoutTimer;
	private long					sentTime		= -1;
	private long					responseTime	= -1;
	private Key						expectedID;


	public RPCCall (RPCServer rpc, MessageBase msg) {
		this.rpc = rpc;
		this.msg = msg;
	}
	
	@Override
	public void setExpectedID(Key id) {
		expectedID = id;
	}
	
	@Override
	public boolean matchesExpectedID(Key id) {
		return expectedID == null || id.equals(expectedID);
	}
	
	@Override
	public Key getExpectedID() {
		return expectedID;
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCCallBase#start()
	 */
	@Override
	public void start () {
		sentTime = System.currentTimeMillis();
		queued = false;
		startTimeout();
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCCallBase#response(lbms.plugins.mldht.kad.messages.MessageBase)
	 */
	@Override
	public void response (MessageBase rsp) {
		if (timeoutTimer != null) {
			timeoutTimer.cancel(false);
		}
		responseTime = System.currentTimeMillis();
		onCallResponse(rsp);
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCCallBase#addListener(lbms.plugins.mldht.kad.RPCCallListener)
	 */
	@Override
	public synchronized void addListener (RPCCallListener cl) {
		if (listeners == null) {
			listeners = new ArrayList<RPCCallListener>(1);
		}
		listeners.add(cl);
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCCallBase#removeListener(lbms.plugins.mldht.kad.RPCCallListener)
	 */
	@Override
	public synchronized void removeListener (RPCCallListener cl) {
		if (listeners != null) {
			listeners.remove(cl);
		}
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCCallBase#getMessageMethod()
	 */
	@Override
	public Method getMessageMethod () {
		return msg.getMethod();
	}

	/// Get the request sent
	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCCallBase#getRequest()
	 */
	@Override
	public MessageBase getRequest () {
		return msg;
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCCallBase#isQueued()
	 */
	@Override
	public boolean isQueued () {
		return queued;
	}

	private void startTimeout () {
		timeoutTimer = DHT.getScheduler().schedule(new Runnable() {
			@Override
			public void run () {
				// we stalled. for accurate measurement we still need to wait out the max timeout.
				// Start a new timer for the remaining time
				long elapsed = System.currentTimeMillis() - sentTime;
				long remaining = DHTConstants.RPC_CALL_TIMEOUT_MAX - elapsed;
				if(remaining > 0)
				{
					stalled = true;
					onStall();
					timeoutTimer = DHT.getScheduler().schedule(new Runnable() {
						@Override
						public void run() {
							onCallTimeout();
						}
					}, remaining, TimeUnit.MILLISECONDS);
				} else {
					onCallTimeout();
				}
				
				
				
			}
		}, rpc.getTimeoutFilter().getStallTimeout(), TimeUnit.MILLISECONDS);
	}

	private synchronized void onCallResponse (MessageBase rsp) {
		if (listeners != null) {
			for (int i = 0; i < listeners.size(); i++) {
				listeners.get(i).onResponse(this, rsp);
			}
		}
	}

	private synchronized void onCallTimeout () {
		DHT.logDebug("RPCCall timed out ID: " + new String(msg.getMTID()));

		if (listeners != null) {
			for (int i = 0; i < listeners.size(); i++) {
				try {
					listeners.get(i).onTimeout(this);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private synchronized void onStall() {
		DHT.logDebug("RPCCall stalled ID: " + new String(msg.getMTID()));
		if (listeners != null) {
			for (int i = 0; i < listeners.size(); i++) {
				try {
					listeners.get(i).onStall(this);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}		
	}
	
	/**
	 * @return -1 if there is no response yet or it has timed out. The round trip time in milliseconds otherwise
	 */
	@Override
	public long getRTT() {
		if(sentTime == -1 || responseTime == -1)
			return -1;
		return responseTime - sentTime;
	}
	
	@Override
	public boolean wasStalled() {
		return stalled;
	}

}
