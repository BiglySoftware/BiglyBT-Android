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
import java.util.*;

import lbms.plugins.mldht.kad.*;
import lbms.plugins.mldht.kad.DHT.DHTtype;
import lbms.plugins.mldht.kad.utils.Token;

/**
 * @author Damokles
 *
 */
public class GetPeersResponse extends MessageBase {

	private Token			token;
	private byte[]			nodes;
	private byte[]			nodes6;
	private byte[]			scrapeSeeds;
	private byte[]			scrapePeers;

	private List<DBItem>	items;

	/**
	 * @param mtid
	 * @param id
	 * @param nodes
	 * @param token
	 */
	public GetPeersResponse (byte[] mtid, byte[] nodes, byte[] nodes6, Token token) {
		super(mtid, Method.GET_PEERS, Type.RSP_MSG);
		this.nodes = nodes;
		this.nodes6 = nodes6;
		this.token = token;
	}
	
	
	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.messages.MessageBase#apply(lbms.plugins.mldht.kad.DHT)
	 */
	@Override
	public void apply (DHT dh_table) {
		dh_table.response(this);
	}
	
	@Override
	public Map<String, Object> getInnerMap() {
		Map<String, Object> innerMap = new TreeMap<String, Object>();
		innerMap.put("id", id.getHash());
		if(token != null)
			innerMap.put("token", token.getValue());
		if(nodes != null)
			innerMap.put("nodes", nodes);
		if(nodes6 != null)
			innerMap.put("nodes6", nodes6);
		if(items != null && !items.isEmpty()) {
			List<byte[]> itemsList = new ArrayList<byte[]>(items.size());
			for (DBItem item : items) {
				itemsList.add(item.getData());
			}
			innerMap.put("values", itemsList);
		}

		if(scrapePeers != null && scrapeSeeds != null)
		{
			innerMap.put("BFpe", scrapePeers);
			innerMap.put("BFse", scrapeSeeds);
		}

		return innerMap;
	}

	public byte[] getNodes(DHTtype type)
	{
		if(type == DHTtype.IPV4_DHT)
			return nodes;
		if(type == DHTtype.IPV6_DHT)
			return nodes6;
		return null;
	}
	
	public void setPeerItems(List<DBItem> items) {
		this.items = items;
	}

	public List<DBItem> getPeerItems () {
		return items == null ? (List<DBItem>)Collections.EMPTY_LIST : Collections.unmodifiableList(items);
	}
	
	public BloomFilterBEP33 getScrapeSeeds() {
		if(scrapeSeeds != null)
			return new BloomFilterBEP33(scrapeSeeds);
		return null;
	}


	public void setScrapeSeeds(BloomFilterBEP33 scrapeSeeds) {
		this.scrapeSeeds = scrapePeers != null ? scrapeSeeds.serialize() : null;
	}


	public BloomFilterBEP33 getScrapePeers() {
		if(scrapePeers != null)
			return new BloomFilterBEP33(scrapePeers);
		return null;
	}


	public void setScrapePeers(BloomFilterBEP33 scrapePeers) {
		this.scrapePeers = scrapePeers != null ? scrapePeers.serialize() : null;
	}

	public Token getToken () {
		return token;
	}

	
	public String toString() {
		return super.toString() + "contains: "+ (nodes != null ? (nodes.length/DHTtype.IPV4_DHT.NODES_ENTRY_LENGTH)+" nodes" : "") + (nodes6 != null ? (nodes6.length/DHTtype.IPV6_DHT.NODES_ENTRY_LENGTH)+" nodes6" : "") + (items != null ? (items.size())+" values" : "") ;
	}
}
