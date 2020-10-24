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

import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadScrapeResult;

public class DHTScrapeResult implements DownloadScrapeResult {
	
	private Download download;
	private int seedCount;
	private int peerCount;
	private long scrapeStartTime;
	
	public DHTScrapeResult(Download dl, int seeds, int peers) {
		download = dl;
		seedCount = seeds;
		peerCount = peers;
	}
	
	
	@Override
	public Download getDownload() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getNextScrapeStartTime() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getNonSeedCount() {
		return peerCount;
	}

	@Override
	public int getResponseType() {
		return DownloadScrapeResult.RT_SUCCESS;
	}

	void setScrapeStartTime(long time) {
		scrapeStartTime = time;
	}
	
	
	@Override
	public long getScrapeStartTime() {
		return scrapeStartTime;
	}

	@Override
	public int getSeedCount() {
		return seedCount;
	}

	@Override
	public String getStatus() {
		return null;
	}

	@Override
	public URL getURL() {
		try
		{
			return new URL("dht","mldht","scrape");
		} catch (MalformedURLException e)
		{
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public void setNextScrapeStartTime(long nextScrapeStartTime) {
	// TODO Auto-generated method stub
	}
}
