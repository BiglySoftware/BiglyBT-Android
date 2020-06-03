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

/**
 * @author Damokles
 *
 */
public class DatabaseStats {
	private int	keyCount;
	private int	itemCount;

	/**
	 * @return the itemCount
	 */
	public int getItemCount () {
		return itemCount;
	}

	/**
	 * @return the keyCount
	 */
	public int getKeyCount () {
		return keyCount;
	}

	/**
	 * @param itemCount the itemCount to set
	 */
	protected void setItemCount (int itemCount) {
		this.itemCount = (itemCount >= 0) ? itemCount : 0;
	}

	/**
	 * @param keyCount the keyCount to set
	 */
	protected void setKeyCount (int keyCount) {
		this.keyCount = (keyCount >= 0) ? keyCount : 0;
	}
}
