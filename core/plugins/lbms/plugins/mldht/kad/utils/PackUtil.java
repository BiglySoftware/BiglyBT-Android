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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.List;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.KBucketEntry;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.DHT.DHTtype;

/**
 * @author Damokles
 *
 */
public class PackUtil {
	
	/**
	 * Packs a BucketEntry into the provided buffer.
	 *
	 * @param e Entry to insert
	 * @param buffer must be at least 26bytes per Entry
	 * @param off Offset to use
	 * @throws IllegalArgumentException if buffer is too small
	 */
	public static void PackBucketEntry (KBucketEntry e, byte[] buffer, int off, DHTtype type) {
		// first check size
		if (off + type.NODES_ENTRY_LENGTH > buffer.length) {
			throw new IllegalArgumentException("Not enough room in buffer");
		}
		ByteBuffer bb = ByteBuffer.wrap(buffer, off, type.NODES_ENTRY_LENGTH);

		InetSocketAddress addr = e.getAddress();
		if(type == DHTtype.IPV6_DHT && addr.getAddress() instanceof Inet4Address)
			throw new IllegalArgumentException("Attempting to serialize an IPv4 bucket entry into nodes6 buffer");
		// copy ID, IP address and port into the buffer
		bb.put(e.getID().getHash());
		bb.put(addr.getAddress().getAddress());
		//bt::WriteUint32(ptr,20,addr.ipAddress().IPv4Addr());
		bb.putShort((short) addr.getPort());
	}

	/**
	 * Unpacks a Entry from a byte array
	 *
	 * @param buffer byte array with serialized entry
	 * @param off the Offset to use
	 * @return deserialized Entry
	 * @throws IllegalArgumentException if buffer is to small
	 */
	public static KBucketEntry UnpackBucketEntry (byte[] buffer, int off, DHTtype type) {
		if (off + type.NODES_ENTRY_LENGTH > buffer.length) {
			throw new IllegalArgumentException("Not enough room in buffer");
		}
		ByteBuffer bb = ByteBuffer.wrap(buffer, off, type.NODES_ENTRY_LENGTH);

		byte[] key = new byte[20];
		bb.get(key);

		byte[] inetaddr = new byte[type.NODES_ENTRY_LENGTH - 20 - 2];
		bb.get(inetaddr);

		InetSocketAddress addr = null;
		//UnknownHostException shouldn't occur since IP is provided
		try {

			addr = new InetSocketAddress(InetAddress.getByAddress(inetaddr),
					bb.getShort() & 0xFFFF); // & 0xFFFF is used to get an unsigned short
		} catch (UnknownHostException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

		return new KBucketEntry(addr, new Key(key), 0);
	}
}
