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
package lbms.plugins.mldht.kad.messages;

import java.util.*;

import lbms.plugins.mldht.kad.Key;

/**
 * @author Damokles
 *
 */
public abstract class AbstractLookupRequest extends MessageBase {

	protected Key	target;
	private boolean want4;
	private boolean want6;

	/**
	 * @param id
	 * @param info_hash
	 */
	public AbstractLookupRequest (Key target, Method m) {
		super(new byte[] {(byte) 0xFF}, m, Type.REQ_MSG);
		this.target = target;
	}
	
	@Override
	public Map<String, Object> getInnerMap() {
		Map<String, Object> inner = new TreeMap<String, Object>();
		inner.put("id", id.getHash());
		inner.put(targetBencodingName(), target.getHash());
		List<String> want = new ArrayList<String>(2);
		if(want4)
			want.add("n4");
		if(want6)
			want.add("n6");
		inner.put("want",want);

		return inner;

	}

	protected abstract String targetBencodingName();

	/**
	 * @return the info_hash
	 */
	public Key getTarget () {
		return target;
	}

	public boolean doesWant4() {
		return want4;
	}
	
	public void decodeWant(List<byte[]> want) {
		if(want == null)
			return;
		
		List<String> wants = new ArrayList<String>(2);
		for(byte[] bytes : want)
			wants.add(new String(bytes));
		
		want4 |= wants.contains("n4");
		want6 |= wants.contains("n6");
	}
	
	public void setWant4(boolean want4) {
		this.want4 = want4;
	}

	public boolean doesWant6() {
		return want6;
	}

	public void setWant6(boolean want6) {
		this.want6 = want6;
	}
	
	public String toString() {
		//return super.toString() + "targetKey:"+target+" ("+(160-DHT.getSingleton().getOurID().findApproxKeyDistance(target))+")";
		return super.toString() + "targetKey:"+target;
	}
}
