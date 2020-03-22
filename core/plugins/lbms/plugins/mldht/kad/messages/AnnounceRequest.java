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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.DHTConstants;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.utils.Token;

/**
 * @author Damokles
 *
 */
public class AnnounceRequest extends GetPeersRequest {

	protected int		port;
	boolean				isSeed;
	protected Token		token;

	/**
	 * @param id
	 * @param info_hash
	 * @param port
	 * @param token
	 */
	public AnnounceRequest (Key info_hash, int port, Token token) {
		super(info_hash);
		this.port = port;
		this.token = token;
		this.method = Method.ANNOUNCE_PEER;
	}

	public boolean isSeed() {
		return isSeed;
	}

	public void setSeed(boolean isSeed) {
		this.isSeed = isSeed;
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.messages.GetPeersRequest#apply(lbms.plugins.mldht.kad.DHT)
	 */
	@Override
	public void apply (DHT dh_table) {
		dh_table.announce(this);
	}
	
	
	@Override
	public Map<String, Object> getInnerMap() {
		Map<String, Object> inner = new TreeMap<String, Object>();

		inner.put("id", id.getHash());
		inner.put("info_hash", target.getHash());
		inner.put("port", port);
		inner.put("token", token.getValue());
		inner.put("seed", Long.valueOf(isSeed ? 1 : 0));

		return inner;
	}


	/**
	 * @return the token
	 */
	public Token getToken () {
		return token;
	}
	
	public int getPort() {
		return port;
	}
}
