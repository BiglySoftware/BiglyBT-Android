/*
 * File    : FileManagerFactory.java
 * Created : 12-Feb-2004
 * By      : parg
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

package com.biglybt.core.diskmanager.file;

/**
 * @author parg
 *
 */

import com.biglybt.core.diskmanager.file.impl.FMFileManagerImpl;


public class
FMFileManagerFactory
{
	private static FMFileManager instance;

	public static FMFileManager
	getSingleton()
	{
		if (instance != null) {
			return instance;
		}
		String className = System.getProperty("az.factory.FMFileManager.impl");
		if (className != null) {
			try {
				instance = (FMFileManager) Class.forName(className).newInstance();
				return  instance;
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
		instance = FMFileManagerImpl.getSingleton();
		return instance;
	}
}
