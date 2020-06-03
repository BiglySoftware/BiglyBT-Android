/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
 
package com.aelitis.azureus.plugins.xmwebui;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.torrent.*;

/**
 * @author TuxPaper
 * @created Feb 18, 2016
 *
 */
public class TorrentBlank
	implements Torrent
{

	private Download d;

	/**
	 * 
	 */
	public TorrentBlank(Download d) {
		this.d = d;
	}

	// @see com.biglybt.pif.torrent.Torrent#getName()
	@Override
	public String getName() {
		return d.getName();
	}

	// @see com.biglybt.pif.torrent.Torrent#getAnnounceURL()
	@Override
	public URL getAnnounceURL() {
		try {
			return new URL("http://invalid.torrent");
		} catch (MalformedURLException e) {
		}
		return null;
	}

	// @see com.biglybt.pif.torrent.Torrent#setAnnounceURL(java.net.URL)
	@Override
	public void setAnnounceURL(URL url) {
	}

	// @see com.biglybt.pif.torrent.Torrent#getAnnounceURLList()
	@Override
	public TorrentAnnounceURLList getAnnounceURLList() {
		return null;
	}

	// @see com.biglybt.pif.torrent.Torrent#getHash()
	@Override
	public byte[] getHash() {
		byte[] b = new byte[32];
		return b;
	}

	// @see com.biglybt.pif.torrent.Torrent#getSize()
	@Override
	public long getSize() {
		return 0;
	}

	// @see com.biglybt.pif.torrent.Torrent#getComment()
	@Override
	public String getComment() {
		return "";
	}

	// @see com.biglybt.pif.torrent.Torrent#setComment(java.lang.String)
	@Override
	public void setComment(String comment) {
	}

	// @see com.biglybt.pif.torrent.Torrent#getCreationDate()
	@Override
	public long getCreationDate() {
		return 0;
	}

	// @see com.biglybt.pif.torrent.Torrent#getCreatedBy()
	@Override
	public String getCreatedBy() {
		return "";
	}

	// @see com.biglybt.pif.torrent.Torrent#getPieceSize()
	@Override
	public long getPieceSize() {
		return 0;
	}

	// @see com.biglybt.pif.torrent.Torrent#getPieceCount()
	@Override
	public long getPieceCount() {
		return 0;
	}

	// @see com.biglybt.pif.torrent.Torrent#getPieces()
	@Override
	public byte[][] getPieces() {
		return new byte[0][0];
	}

	// @see com.biglybt.pif.torrent.Torrent#getFiles()
	@Override
	public TorrentFile[] getFiles() {
		return new TorrentFile[0];
	}

	// @see com.biglybt.pif.torrent.Torrent#getEncoding()
	@Override
	public String getEncoding() {
		return "utf8";
	}

	// @see com.biglybt.pif.torrent.Torrent#setEncoding(java.lang.String)
	@Override
	public void setEncoding(String encoding)
			throws TorrentEncodingException {
	}

	// @see com.biglybt.pif.torrent.Torrent#setDefaultEncoding()
	@Override
	public void setDefaultEncoding()
			throws TorrentEncodingException {
	}

	// @see com.biglybt.pif.torrent.Torrent#getAdditionalProperty(java.lang.String)
	@Override
	public Object getAdditionalProperty(String name) {
		return null;
	}

	// @see com.biglybt.pif.torrent.Torrent#removeAdditionalProperties()
	@Override
	public Torrent removeAdditionalProperties() {
		return this;
	}

	// @see com.biglybt.pif.torrent.Torrent#setPluginStringProperty(java.lang.String, java.lang.String)
	@Override
	public void setPluginStringProperty(String name, String value) {
	}

	// @see com.biglybt.pif.torrent.Torrent#getPluginStringProperty(java.lang.String)
	@Override
	public String getPluginStringProperty(String name) {
		return "";
	}

	// @see com.biglybt.pif.torrent.Torrent#setMapProperty(java.lang.String, java.util.Map)
	@Override
	public void setMapProperty(String name, Map value) {
	}

	// @see com.biglybt.pif.torrent.Torrent#getMapProperty(java.lang.String)
	@Override
	public Map getMapProperty(String name) {
		return new HashMap();
	}

	// @see com.biglybt.pif.torrent.Torrent#isDecentralised()
	@Override
	public boolean isDecentralised() {
		return false;
	}

	// @see com.biglybt.pif.torrent.Torrent#isDecentralisedBackupEnabled()
	@Override
	public boolean isDecentralisedBackupEnabled() {
		return false;
	}

	// @see com.biglybt.pif.torrent.Torrent#setDecentralisedBackupRequested(boolean)
	@Override
	public void setDecentralisedBackupRequested(boolean requested) {
	}

	// @see com.biglybt.pif.torrent.Torrent#isDecentralisedBackupRequested()
	@Override
	public boolean isDecentralisedBackupRequested() {
		return false;
	}

	// @see com.biglybt.pif.torrent.Torrent#isPrivate()
	@Override
	public boolean isPrivate() {
		return false;
	}

	// @see com.biglybt.pif.torrent.Torrent#setPrivate(boolean)
	@Override
	public void setPrivate(boolean priv) {
	}

	// @see com.biglybt.pif.torrent.Torrent#wasCreatedByUs()
	@Override
	public boolean wasCreatedByUs() {
		return false;
	}

	// @see com.biglybt.pif.torrent.Torrent#getMagnetURI()
	@Override
	public URL getMagnetURI()
			throws TorrentException {
		try {
			return new URL("http://invalid.torrent");
		} catch (MalformedURLException e) {
			throw new TorrentException(e);
		}
	}

	// @see com.biglybt.pif.torrent.Torrent#writeToMap()
	@Override
	public Map writeToMap()
			throws TorrentException {
		return new HashMap();
	}

	// @see com.biglybt.pif.torrent.Torrent#writeToFile(java.io.File)
	@Override
	public void writeToFile(File file)
			throws TorrentException {
	}

	// @see com.biglybt.pif.torrent.Torrent#writeToBEncodedData()
	@Override
	public byte[] writeToBEncodedData()
			throws TorrentException {
		return new byte[0];
	}

	// @see com.biglybt.pif.torrent.Torrent#save()
	@Override
	public void save()
			throws TorrentException {
	}

	// @see com.biglybt.pif.torrent.Torrent#setComplete(java.io.File)
	@Override
	public void setComplete(File data_dir)
			throws TorrentException {
	}

	// @see com.biglybt.pif.torrent.Torrent#isComplete()
	@Override
	public boolean isComplete() {
		return false;
	}

	// @see com.biglybt.pif.torrent.Torrent#isSimpleTorrent()
	@Override
	public boolean isSimpleTorrent() {
		return false;
	}

	// @see com.biglybt.pif.torrent.Torrent#getClone()
	@Override
	public Torrent getClone()
			throws TorrentException {
		return this;
	}

}
