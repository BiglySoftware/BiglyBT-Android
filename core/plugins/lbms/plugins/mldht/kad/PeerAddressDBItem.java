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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import lbms.plugins.mldht.kad.DHT.DHTtype;

public class PeerAddressDBItem extends DBItem {
	
	
	boolean seed;
	
	public static PeerAddressDBItem createFromAddress(InetAddress addr, int port, boolean isSeed) {
		byte[] tdata = new byte[addr.getAddress().length + 2];
		ByteBuffer bb = ByteBuffer.wrap(tdata);
		bb.put(addr.getAddress());
		bb.putShort((short) port);
		return new PeerAddressDBItem(tdata, isSeed);
	}
	
	public PeerAddressDBItem(byte[] data, boolean isSeed) {
		super(data);
		if(data.length != DHTtype.IPV4_DHT.ADDRESS_ENTRY_LENGTH && data.length != DHTtype.IPV6_DHT.ADDRESS_ENTRY_LENGTH)
			throw new IllegalArgumentException("byte array length does not match ipv4 or ipv6 raw InetAddress+Port length");
		seed = isSeed;
	}
	
	public InetAddress getInetAddress() {
		try
		{
			if (item.length == DHTtype.IPV4_DHT.ADDRESS_ENTRY_LENGTH)
				return InetAddress.getByAddress(Arrays.copyOf(item, 4));
			if (item.length == DHTtype.IPV6_DHT.ADDRESS_ENTRY_LENGTH)
				return InetAddress.getByAddress(Arrays.copyOf(item, 16));
		} catch (UnknownHostException e)
		{
			// should not happen
			e.printStackTrace();
		}
		
		return null;
	}
	
	public String getAddressAsString() {
		return getInetAddress().getHostAddress();
	}
	
	public Class<? extends InetAddress> getAddressType() {
		if(item.length == DHTtype.IPV4_DHT.ADDRESS_ENTRY_LENGTH)
			return DHTtype.IPV4_DHT.PREFERRED_ADDRESS_TYPE;
		if(item.length == DHTtype.IPV6_DHT.ADDRESS_ENTRY_LENGTH)
			return DHTtype.IPV6_DHT.PREFERRED_ADDRESS_TYPE;
		return null;
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof PeerAddressDBItem)
		{
			PeerAddressDBItem other = (PeerAddressDBItem) obj;
			if(other.item.length != item.length)
				return false;
			for(int i=0;i<item.length-2;i++)
				if(other.item[i] != item[i])
					return false;
			return true;
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return Arrays.hashCode(Arrays.copyOf(item, item.length-2));
	}

	public String toString() {
		return super.toString()+" addr:"+new InetSocketAddress(getAddressAsString(), getPort())+" seed:"+seed;
	}
	
	public int getPort() {
		if (item.length == DHTtype.IPV4_DHT.ADDRESS_ENTRY_LENGTH)
			return (item[4] & 0xFF) << 8 | (item[5] & 0xFF);
		if (item.length == DHTtype.IPV6_DHT.ADDRESS_ENTRY_LENGTH)
			return (item[16] & 0xFF) << 8 | (item[17] & 0xFF);
		return 0;
	}
	
	public boolean isSeed() {
		return seed;
	}
	
}
