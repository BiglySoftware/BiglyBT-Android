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

import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.messages.MessageBase.Method;

/**
 * @author Damokles
 *
 */
public interface RPCCallBase {

	/**
	 * Called when a queued call gets started. Starts the timeout timer.
	 */
	public void start ();

	/**
	 * Called by the server if a response is received.
	 * @param rsp
	 */
	public void response (MessageBase rsp);

	/**
	 * Add a listener for this call
	 * @param cl The listener
	 */
	public void addListener (RPCCallListener cl);

	/**
	 * Remove a listener for this call
	 * @param cl The listener
	 */
	public void removeListener (RPCCallListener cl);

	/**
	 * @return Message Method
	 */
	public Method getMessageMethod ();

	/// Get the request sent
	public MessageBase getRequest ();

	/**
	 * @return the queued
	 */
	public boolean isQueued ();

	/**
	 * @return -1 if there is no response yet or it has timed out. The round trip time in milliseconds otherwise
	 */
	public long getRTT();

	public boolean wasStalled();
	
	public void setExpectedID(Key id);
	
	public boolean matchesExpectedID(Key id);
	
	public Key getExpectedID();
}