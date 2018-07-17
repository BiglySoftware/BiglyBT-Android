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
package lbms.plugins.mldht.azureus;

import lbms.plugins.mldht.kad.PeerAddressDBItem;

import com.biglybt.pif.download.DownloadAnnounceResultPeer;

/**
 * @author Damokles
 *
 */
public class DHTPeer implements DownloadAnnounceResultPeer {

	private static final String	PEER_SOURCE	= "DHT";

	private String				addr = "localhost";
	private int					port = 0;

	protected DHTPeer (PeerAddressDBItem item) {
		addr = item.getAddressAsString();
		port = item.getPort();
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.download.DownloadAnnounceResultPeer#getAddress()
	 */
	@Override
	public String getAddress () {
		return addr;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.download.DownloadAnnounceResultPeer#getPeerID()
	 */
	@Override
	public byte[] getPeerID () {
		return null;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.download.DownloadAnnounceResultPeer#getPort()
	 */
	@Override
	public int getPort () {
		return port;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.download.DownloadAnnounceResultPeer#getProtocol()
	 */
	@Override
	public short getProtocol () {
		return DownloadAnnounceResultPeer.PROTOCOL_NORMAL;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.download.DownloadAnnounceResultPeer#getSource()
	 */
	@Override
	public String getSource () {
		return PEER_SOURCE;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.download.DownloadAnnounceResultPeer#getUDPPort()
	 */
	@Override
	public int getUDPPort () {
		return 0;
	}

}
