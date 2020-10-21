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

import lbms.plugins.mldht.kad.utils.Token;

/**
 * @author Damokles
 *
 */
public class KBucketEntryAndToken extends KBucketEntry {

	private Token		token;

	public KBucketEntryAndToken (KBucketEntry kbe, Token token) {
		super(kbe);
		this.token = token;
	}

	/**
	 * @return the token
	 */
	public Token getToken () {
		return token;
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.KBucketEntry#equals(java.lang.Object)
	 */
	@Override
	public boolean equals (Object obj) {
		if (obj instanceof KBucketEntryAndToken) {
			KBucketEntryAndToken kbet = (KBucketEntryAndToken) obj;
			if (super.equals(obj))
				return token.equals(kbet.token);
			return false;
		}
		return super.equals(obj);
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.KBucketEntry#hashCode()
	 */
	@Override
	public int hashCode () {
		return super.hashCode() ^ token.hashCode();
	}
}
