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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import lbms.plugins.mldht.kad.utils.BitVector;

import static java.lang.Math.*;


public class BloomFilterBEP33 implements Comparable<BloomFilterBEP33>, Cloneable {

	private final static int m = 256 * 8;
	private final static int k = 2; 

	
	MessageDigest sha1;
	BitVector filter;
	
	public BloomFilterBEP33() {
		filter = new BitVector(m);
		
		try
		{
			sha1 = MessageDigest.getInstance("SHA1");
		} catch (NoSuchAlgorithmException e)
		{
			e.printStackTrace();
		}
	}
	
	public BloomFilterBEP33(byte[] serializedFilter) {
		filter = new BitVector(m,serializedFilter);
	}
	
    public void insert(InetAddress addr) {
        
        byte[] hash = sha1.digest(addr.getAddress());
        
        int index1 = (hash[0]&0xFF) | (hash[1]&0xFF) << 8;
        int index2 = (hash[2]&0xFF) | (hash[3]&0xFF) << 8;

        // truncate index to m (11 bits required)
        index1 %= m;
        index2 %= m;

        // set bits at index1 and index2
        filter.set(index1);
        filter.set(index2);
    }
	
	
	@Override
	protected BloomFilterBEP33 clone() {
		BloomFilterBEP33 newFilter = null;
		try
		{
			newFilter = (BloomFilterBEP33) super.clone();
		} catch (CloneNotSupportedException e)
		{
			// never happens
		}
		newFilter.filter = new BitVector(filter);		
		return newFilter;		
	}
	
	@Override
	public int compareTo(BloomFilterBEP33 o) {
		return (int) (size()-o.size());
	}

	
	public int size() {
		// number of expected 0 bits = m * (1 − 1/m)^(k*size)

		double c = filter.bitcount();
		double size = log1p(-c/m) / (k * logB());
		return (int) size;
	}
	
	public static int unionSize(Collection<BloomFilterBEP33> filters)
	{
		BitVector[] vectors = new BitVector[filters.size()];
		int i = 0;
		for(BloomFilterBEP33 f : filters)
			vectors[i++] = f.filter;
		
		double c = BitVector.unionAndCount(vectors);
		return (int) (log1p(-c/m) / (k * logB()));
	}
	
	public byte[] serialize() {
		return filter.getSerializedFormat();
	}

	
	// the logarithm of the base used for various calculations
	private static double logB() {
		return log1p(-1.0/m);
	}

	
	public static void main(String[] args) throws Exception {
		
		BloomFilterBEP33 bf = new BloomFilterBEP33();
		//2001:DB8::
		for(int i=0;i<1000;i++)
		{
			bf.insert(InetAddress.getByAddress(new byte[] {0x20,0x01,0x0D,(byte) 0xB8,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,(byte) (i>>8 & 0xFF),(byte) (i & 0xFF)}));
		}
		
		for(int i=0;i<256;i++)
		{
			bf.insert(InetAddress.getByAddress(new byte[] {(byte) 192,0,2,(byte) i}));
		}
		
		System.out.println(bf.filter.toString());
		System.out.println(bf.filter.bitcount());
		System.out.println(bf.size());
	
	}

	
	
	
}
