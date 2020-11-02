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
package lbms.plugins.mldht.kad.utils;

import java.util.Arrays;
import java.util.Collections;

import lbms.plugins.mldht.kad.DHTConstants;
import lbms.plugins.mldht.kad.RPCCall;
import lbms.plugins.mldht.kad.RPCCallBase;
import lbms.plugins.mldht.kad.RPCCallListener;
import lbms.plugins.mldht.kad.messages.MessageBase;

public class ResponseTimeoutFilter {
	
	public static final int		NUM_SAMPLES			= 256;
	public static final int		QUANTILE_INDEX		= (int) (NUM_SAMPLES * 0.9f);
	
	
	final long[] rttRingbuffer = new long[NUM_SAMPLES];
	int bufferIndex;
	long targetTimeoutMillis;
	
	
	public ResponseTimeoutFilter() {
		reset();		
	}
	
	public void reset() {
		targetTimeoutMillis = DHTConstants.RPC_CALL_TIMEOUT_MAX;
		Arrays.fill(rttRingbuffer, DHTConstants.RPC_CALL_TIMEOUT_MAX);
	}
	
	
	public void registerCall(final RPCCallBase call) {
		call.addListener(new RPCCallListener() {
			@Override
			public void onTimeout(RPCCallBase c) {}
			
			@Override
			public void onStall(RPCCallBase c) {}
			
			@Override
			public void onResponse(RPCCallBase c, MessageBase rsp) {
				 update(c.getRTT());
			}
		});
	}
	
	private void update(long newRTT) {
		rttRingbuffer[bufferIndex++] = newRTT;
		bufferIndex %= NUM_SAMPLES;
		// update target timeout every 16 packets
		if((bufferIndex & 0x0F) == 0)
		{
			long[] sortableBuffer = rttRingbuffer.clone();
			Arrays.sort(sortableBuffer);
			targetTimeoutMillis = sortableBuffer[QUANTILE_INDEX];			
		}
	}
	
	public long getStallTimeout() {
		long timeout = Math.max(DHTConstants.RPC_CALL_TIMEOUT_MIN, targetTimeoutMillis);
		//System.out.println(timeout);
		return  timeout;
	}
}
