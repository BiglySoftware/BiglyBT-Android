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
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Prefix extends Key {
	
	/**
	 * identifies the first bit of a key that has to be equal to be considered as covered by this prefix
	 * -1 = prefix matches whole keyspace
	 * 0 = 0th bit must match
	 * 1 = ...
	 */
	int depth = -1;
	
	public Prefix() {
		super();
	}
	
	public Prefix(Prefix p) {
		super(p);
		depth = p.depth; 
	}
	
	
	/**
	 *
	 * @param Key to be checked
	 * @return true if this Prefix covers the provided key
	 */
	public boolean isPrefixOf(Key k)
	{
		return bitsEqual(this, k, depth);
	}
	
	public Prefix splitPrefixBranch(boolean highBranch) {
		Prefix branch = new Prefix(this);
		int branchDepdth = ++branch.depth;
		if(highBranch)
			branch.hash[branchDepdth / 8] |=  0x80 >> (branchDepdth % 8);
		else
			branch.hash[branchDepdth / 8] &= ~(0x80 >> (branchDepdth % 8));
		
				
		return branch;
	}
	
	public Prefix getParentPrefix() {
		if(depth == -1)
			return this;
		Prefix parent = new Prefix(this);
		int oldDepth = parent.depth--;
		// set last bit to zero
		parent.hash[oldDepth / 8] &= ~(0x80 >> (oldDepth % 8));
		return parent;
	}
	
	public boolean isSiblingOf(Prefix otherPrefix)
	{
		if(depth != otherPrefix.depth)
			return false;
		
		return bitsEqual(this, otherPrefix, depth-1);
	}
	
	/**
	 * @return true if the first bits up to the Nth bit of both keys are equal
	 * 
	 * <pre>
	 *  n = -1 => no bits have to match
	 *  n = 0  => byte 0, MSB has to match
	 * </pr>
	 */
	private static boolean bitsEqual(Key k1, Key k2, int n)
	{
		if(n < 0)
			return true;
		
		// check all complete bytes preceding the bit we want to check
		for(int i=0;i<n/8;i++)
		{
			if(k1.hash[i] != k2.hash[i])
				return false;
		}
		
		// check the bits
		int mask = (0xFF80 >> n % 8) & 0xFF;
		
		return (k1.hash[n / 8] & mask) == (k2.hash[n / 8] & mask);
	}
	
	private static void copyBits(Key source, Key destination, int depth)
	{
		if(depth < 0)
			return;
		
		byte[] data = destination.hash;
		
		// copy over all complete bytes
		for (int i = 0; i < depth / 8; i++)
			data[i] = source.hash[i];
		
		int idx = depth / 8;
		int mask = 0xFF80 >> depth % 8;
		
		// mask out the part we have to copy over from the last prefix byte
		data[idx] &= ~mask;
		// copy the bits from the last byte
		data[idx] |= source.hash[idx] & mask;
	}
	
	public int getDepth() {
		return depth;
	}
	
	@Override
	public String toString() {
		if(depth == -1)
			return "all";
		StringBuilder builder = new StringBuilder(20);
		for(int i=0;i<=depth;i++)
			builder.append((hash[i/8] & (0x80 >> (i % 8))) != 0 ? '1' : '0');
		builder.append("...");
		return builder.toString();
			
			
	}
	
	/**
	 * Generates a random Key that has falls under this prefix  
	 */
	public Key createRandomKeyFromPrefix() {
		// first generate a random one
		Key key = Key.createRandomKey();
		
		copyBits(this, key, depth);

		return key;
	}
	
	/*
	public Key lowestKey() {
		Key k = new Key();
		copyBits(this, k, depth);
		
		return k;
	}
	
	public Key highestKey() {
		Key k = new Key(Key.MAX_KEY);
		copyBits(this, k, depth);
		
		return k;
	}*/
	
	public static Prefix getCommonPrefix(Collection<Key> keys)
	{
		Key first = Collections.min(keys);
		Key last = Collections.max(keys);

		Prefix prefix = new Prefix();
		byte[] newHash = prefix.hash;

		outer: for(int i=0;i<SHA1_HASH_LENGTH;i++)
		{
			if(first.hash[i] == last.hash[i])
			{
				newHash[i] = first.hash[i];
				prefix.depth += 8;
				continue;
			}
			// first differing byte
			newHash[i] = (byte)(first.hash[i] & last.hash[i]);
			for(int j=0;j<8;j++)
			{
				int mask = 0x80 >> j;
				// find leftmost differing bit and then zero out all following bits
				if(((first.hash[i] ^ last.hash[i]) & mask) != 0)
				{
					newHash[i] = (byte)(newHash[i] & ~(0xFF >> j));
					break outer;
				}
				
				prefix.depth++;
			}
		}
		return prefix;
	}
	
	public static void main(String[] args) {
		Prefix p = new Prefix();
		p.hash[0] = (byte) 0x30;
		p.depth = 3;
		
		Key k = new Key();
		k.hash[0] = (byte) 0x37;
		
		System.out.println(p);
		System.out.println(p.isPrefixOf(k));
		
	
	}
	
}
