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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Map;

import lbms.plugins.mldht.kad.PeerAddressDBItem;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadAnnounceResult;
import com.biglybt.pif.download.DownloadAnnounceResultPeer;

/**
 * @author Damokles
 *
 */
public class DHTAnnounceResult implements DownloadAnnounceResult {

	private Download						dl;
	private Collection<PeerAddressDBItem>				peers;
	private DownloadAnnounceResultPeer[]	resultPeers;
	int delay;
	int scrapeSeeds;
	int scrapePeers;

	public DHTAnnounceResult (Download dl, Collection<PeerAddressDBItem> peers, int delay) {
		this.dl = dl;
		this.peers = peers;
		this.delay = delay;
	}

	/**
	 * Converts the DBItems into DHTPeers
	 */
	private void convertPeers () {
		resultPeers = new DownloadAnnounceResultPeer[peers.size()];
		
		
		int i = 0;		
		for(PeerAddressDBItem it : peers)
			resultPeers[i++] = new DHTPeer(it);
		
	}

	public void setScrapeSeeds(int scrapeSeeds) {
		this.scrapeSeeds = scrapeSeeds;
	}

	public void setScrapePeers(int scrapePeers) {
		this.scrapePeers = scrapePeers;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.download.DownloadAnnounceResult#getDownload()
	 */
	@Override
	public Download getDownload () {
		return dl;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.download.DownloadAnnounceResult#getError()
	 */
	@Override
	public String getError () {
		return null;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.download.DownloadAnnounceResult#getExtensions()
	 */
	@Override
	public Map getExtensions () {
		return null;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.download.DownloadAnnounceResult#getNonSeedCount()
	 */
	@Override
	public int getNonSeedCount () {
		return scrapePeers;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.download.DownloadAnnounceResult#getPeers()
	 */
	@Override
	public DownloadAnnounceResultPeer[] getPeers () {
		if (resultPeers == null) {
			convertPeers();
		}
		return resultPeers;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.download.DownloadAnnounceResult#getReportedPeerCount()
	 */
	@Override
	public int getReportedPeerCount () {
		return 0;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.download.DownloadAnnounceResult#getResponseType()
	 */
	@Override
	public int getResponseType () {
		return DownloadAnnounceResult.RT_SUCCESS;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.download.DownloadAnnounceResult#getSeedCount()
	 */
	@Override
	public int getSeedCount () {
		return scrapeSeeds;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.download.DownloadAnnounceResult#getTimeToWait()
	 */
	@Override
	public long getTimeToWait () {
		return delay;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.download.DownloadAnnounceResult#getURL()
	 */
	@Override
	public URL getURL () {
		try
		{
			return new URL("dht","mldht","announce");
		} catch (MalformedURLException e)
		{
			e.printStackTrace();
			return null;
		}
	}

}
