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

import java.io.Serializable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;

import lbms.plugins.mldht.kad.utils.ThreadLocalUtils;

/**
 * @author Damokles
 *
 */
public class Key implements Comparable<Key>, Serializable {
	
	/**
	 * sorts the closest entries to the head, the furthest to the tail
	 */
	public static final class DistanceOrder implements Comparator<Key> {
		
		final Key target;
		public DistanceOrder(Key target) {
			this.target = target;
		}
		
		
		@Override
		public int compare(Key o1, Key o2) {
			return target.threeWayDistance(o1, o2);
			//return target.distance(o1).compareTo(target.distance(o2));
		}
	}
	
	public static final Key MIN_KEY;
	public static final Key MAX_KEY;
	
	static {
		MIN_KEY = new Key();
		MAX_KEY = new Key();
		Arrays.fill(MAX_KEY.hash, (byte)0xFF); 
	}

	private static final long	serialVersionUID	= -1180893806923345652L;
	public static final int		SHA1_HASH_LENGTH	= 20;
	public static final int		KEY_BITS			= SHA1_HASH_LENGTH * 8;
	protected byte[]			hash				= new byte[SHA1_HASH_LENGTH];

	/**
	 * A Key in the DHT.
	 *
	 * Key's in the distributed hash table are just SHA-1 hashes.
	 * Key provides all necesarry operators to be used as a value.
	 */
	protected Key () {
	}

	/**
	 * Clone constructor
	 *
	 * @param k Key to clone
	 */
	public Key (Key k) {
		System.arraycopy(k.hash, 0, hash, 0, SHA1_HASH_LENGTH);
	}
	
	public Key (String hex)
	{
	    if(hex.length() != 40)
	    	throw new IllegalArgumentException("Hex String must have 40 bytes");
	    
	    for (int i = 0; i < hex.length(); i += 2)
	        hash[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i+1), 16));

	}

	/**
	 * Creates a Key with this hash
	 *
	 * @param hash the SHA1 hash, has to be 20 bytes
	 */
	public Key (byte[] hash) {
		if (hash.length != SHA1_HASH_LENGTH) {
			throw new IllegalArgumentException(
					"Invalid Hash must be 20bytes, was: " + hash.length);
		}
		System.arraycopy(hash, 0, this.hash, 0, SHA1_HASH_LENGTH);
	}

	/*
	 * compares Keys according to their natural distance
	 */
	@Override
	public int compareTo (Key o) {
		for (int i = 0,n=hash.length; i < n; i++) {
			//needs & 0xFF since bytes are signed in Java
			//so we must convert to int to compare it unsigned
			int byte1 = hash[i] & 0xFF;
			int byte2 = o.hash[i] & 0xFF; 

			if (byte1 == byte2)
				continue;
			if (byte1 < byte2)
				return -1;
			return 1;
		}
		return 0;
	}
	
	/**
	 * Compares the distance of two keys relative to this one using the XOR metric
	 * 
	 * @return -1 if k1 is closer to this key, 0 if k1 and k2 are equidistant, 1 if k2 is closer
	 */
	public int threeWayDistance(Key k1, Key k2)
	{
		for (int i = 0,n=hash.length; i < n; i++) {
			if(k1.hash[i] == k2.hash[i])
				continue;
			//needs & 0xFF since bytes are signed in Java
			//so we must convert to int to compare it unsigned
			int byte1 = (k1.hash[i] ^ hash[i]) & 0xFF;
			int byte2 = (k2.hash[i] ^ hash[i]) & 0xFF; 
			
			if (byte1 < byte2)
				return -1;
			return 1;
		}
		return 0;
	}


	public boolean equals (Object o) {
		if(this == o)
			return true;
		if(o instanceof Key)
		{
			Key otherKey = (Key) o;
			for(int i=0,n=hash.length;i<n;i++)
				if(hash[i] != otherKey.hash[i])
					return false;
			return true;
		}
		return false;
	}

	/**
	 * @return the hash
	 */
	public byte[] getHash () {
		return hash.clone();
	}
	
	public Key getDerivedKey(int idx) {
		Key k = new Key(this);
		byte[] data = k.hash;
		for(int i=0;i<32;i++)
			if(((0x01 << i) & idx) != 0)
				data[i/8] ^= 0x80 >> (i % 8);
		
		return k;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode () {
		return (hash[0] ^ hash[1] ^ hash[2] ^ hash[3] ^ hash[4]) << 24
				| (hash[5] ^ hash[6] ^ hash[7] ^ hash[8] ^ hash[9]) << 16
				| (hash[10] ^ hash[11] ^ hash[12] ^ hash[13] ^ hash[14]) << 8
				| (hash[15] ^ hash[16] ^ hash[17] ^ hash[18] ^ hash[19]);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString () {
		return toString(true);
	}
	
	public String toString(boolean nicePrint)
	{
		StringBuilder b = new StringBuilder(nicePrint ? 44 : 40);
		for (int i = 0; i < hash.length; i++) {
			if (nicePrint && i % 4 == 0 && i > 0) {
				b.append(' ');
			}
			int nibble = (hash[i] & 0xF0) >> 4;
			b.append((char)(nibble < 0x0A ? '0'+nibble : 'A'+nibble-10 ));
			nibble = hash[i] & 0x0F;
			b.append((char)(nibble < 0x0A ? '0'+nibble : 'A'+nibble-10 ));
		}
		return b.toString();		
	}

	/**
	 * Returns the approximate distance of this key to the other key.
	 *
	 * Distance is simplified by returning the index of the first different Bit.
	 *
	 * @param id Key to compare to.
	 * @return integer marking the different bits of the keys
	 */
	public int findApproxKeyDistance (Key id) {

		// XOR our id and the sender's ID
		Key d = Key.distance(id, this);
		// now use the first on bit to determine which bucket it should go in

		int bit_on = 0xFF;
		byte[] data_hash = d.getHash();
		for (int i = 0; i < 20; i++) {
			// get the byte
			int b = data_hash[i] & 0xFF;
			// no bit on in this byte so continue
			if (b == 0) {
				continue;
			}

			for (int j = 0; j < 8; j++) {
				if ((b & (0x80 >> j)) != 0) {
					// we have found the bit
					bit_on = i * 8 + j;
					return 159 - bit_on;
				}
			}
		}
		return 0;
	}

	/**
	 * Calculates the distance between two Keys.
	 *
	 * The distance is basically a XOR of both key hashes.
	 *
	 * @param x
	 * @return new Key (this.hash ^ x.hash);
	 */
	public Key distance (Key x) {

		return distance(this, x);
	}
	
	/**
	 * calculates log2(this - otherKey % 2^161).<br />
	 * To get the natural distance for ascending key order this should be the successive element of otherKey 
	 */
	public double naturalDistance(Key otherKey) {
		return Math.log(new BigInteger(1,hash).subtract(new BigInteger(1, otherKey.hash)).mod(new BigInteger(1,MAX_KEY.hash).add(new BigInteger("1"))).doubleValue())/Math.log(2);		
	}


	/**
	 * Calculates the distance between two Keys.
	 *
	 * The distance is basically a XOR of both key hashes.
	 *
	 * @param a
	 * @param b
	 * @return new Key (a.hash ^ b.hash);
	 */
	public static Key distance (Key a, Key b) {
		Key x = new Key();
		for (int i = 0; i < a.hash.length; i++) {
			x.hash[i] = (byte) (a.hash[i] ^ b.hash[i]);
		}
		return x;
	}
	
	/**
	 * Creates a random Key
	 *
	 * @return newly generated random Key
	 */
	public static Key createRandomKey () {
		Key x = new Key();
		ThreadLocalUtils.getThreadLocalRandom().nextBytes(x.hash);
		return x;
	}
	
	public static void main(String[] args) {
		/*
		Key target = new Key();
		target.hash[0] = (byte) 0xF0;
		Key test1 = new Key();
		test1.hash[0] = (byte) 0x80;
		Key test2 = new Key();
		test2.hash[0] = 0x03;
		
		System.out.println(test1.compareTo(test2));
		System.out.println(target.distance(test1).compareTo(target.distance(test2)));
		System.out.println(target.threeWayDistance(test1, test2));
		*/
		
		/*
		
		// simulation to check that natural order != xor order 
		Random rand = new Random();
		
		for(int i=0;i<10000;i++)
		{
			ArrayList<Key> keys = new ArrayList<Key>();
			for(int j=0;j<100000;j++)
				keys.add(Key.createRandomKey());
			Collections.sort(keys);
			for(int j=1;j<keys.size();j++)
				if(keys.get(j-1).equals(keys.get(j)))
				{
					j--;
					keys.remove(j);
				}
			Key[] keysArray = keys.toArray(new Key[keys.size()]); 
			
			
			for(int j=0;j<1000;j++)
			{
				Key target = Key.createRandomKey();
				int closestSetSize = rand.nextInt(12); 
				 
				
				TreeSet<Key> referenceClosestSet = new TreeSet<Key>(new DistanceOrder(target));
				referenceClosestSet.addAll(keys);
				
				List<Key> closestSet1 = new ArrayList<Key>();
				for(Key closest : referenceClosestSet)
				{
					if(closestSet1.size() == closestSetSize)
						break;
					closestSet1.add(closest);
				}
					
				List<Key> closestSet2 = new ArrayList<Key>();
				
				int startIdx = Arrays.binarySearch(keysArray, target);
				if(startIdx < 0)
					startIdx = startIdx*-1 - 1;
				
				closestSet2.add(keysArray[startIdx]);
				
				for(int k = 1;closestSet2.size() < closestSetSize;k++)
				{
					if(startIdx-k >= 0)
						closestSet2.add(keysArray[startIdx-k]);
					if(startIdx+k < keysArray.length)
						closestSet2.add(keysArray[startIdx+k]);
				}
				
				Collections.sort(closestSet2,new DistanceOrder(target));
				if(closestSet2.size() > closestSetSize)
					closestSet2.subList(closestSetSize, closestSet2.size()).clear();

				
				for(int k=0;k<closestSet1.size();k++)
					System.out.print(Arrays.binarySearch(keysArray, closestSet1.get(k))+" ");
				System.out.println();
				
				for(int k=0;k<closestSet2.size();k++)
					System.out.print(Arrays.binarySearch(keysArray, closestSet2.get(k))+" ");
				System.out.println("\n");				
				
				if(!closestSet1.equals(closestSet2))
					System.out.println("damn");


				
				
			}
			 
			
		}*/
		
		/*
		
		// simulation to determine the error introduced by natural order iteration vs. xor order
		
		try
		{
			Random rand = new Random();
			TreeMap<Double, Integer> binningNat = new TreeMap<Double, Integer>();
			TreeMap<Double, Integer> binningXor = new TreeMap<Double, Integer>();
			ArrayList<Key> keyspace = new ArrayList<Key>(5000000);
			for (int i = 0; i < 5000000; i++)
				keyspace.add(Key.createRandomKey());
			Collections.sort(keyspace);
			for (int i = 1; i < keyspace.size(); i++)
			{
				Key prev = keyspace.get(i - 1);
				Key curr = keyspace.get(i);
				Key xorDist = prev.distance(curr);
				double l2xor = 160 - Math.log(new BigInteger(1, xorDist.hash).doubleValue()) / Math.log(2);
				double l2nat = 160 - Math.log(new BigInteger(1,curr.hash).subtract(new BigInteger(1, prev.hash)).doubleValue())/Math.log(2);
				
				double roundedX = ((int)(l2xor*10))/10.0;
				double roundedN = ((int)(l2nat*10))/10.0;
				
				if(binningNat.containsKey(roundedN))
					binningNat.put(roundedN, binningNat.get(roundedN)+1);
				else
					binningNat.put(roundedN, 1);
				
				if(binningXor.containsKey(roundedX))
					binningXor.put(roundedX, binningXor.get(roundedX)+1);
				else
					binningXor.put(roundedX, 1);
			}
			
			System.out.println("natural");
			for(Map.Entry<Double, Integer> e : binningNat.entrySet()) {
				System.out.println(e.getKey()+"\t"+e.getValue());
			}
			System.out.println("xor");
			for(Map.Entry<Double, Integer> e : binningXor.entrySet()) {
				System.out.println(e.getKey()+"\t"+e.getValue());
			}		} catch (Exception e)
		{
			e.printStackTrace();
		}
		*/
		
		/* // checking some binary arithmetic 		
		byte b1 = (byte) 0x7F;
		byte b2 = (byte) 0x80;
		
		int res = (b2 & 0xFF) - (b1 & 0xFF);
		
		System.out.println(res);
		
		if(res > 0)
			System.out.println("b2 > b1");
		if(res < 0)
			System.out.println("b2 < b1");
		*/
		
		// checking some bigint arithmetic
		
		Key k1 = new Key(MIN_KEY);
		k1.hash[19] = (byte) 0x80;
		Key k2 = new Key(MIN_KEY);
		
		System.out.println(Math.log(k1.naturalDistance(k2))/Math.log(2));
		
		
		
	}
}
