/*
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

public class TextualException
	extends Exception
{

	/**
	 * 
	 */
	private static final long serialVersionUID = -3302508436945014676L;

	public TextualException() {
	}

	public TextualException(String arg0) {
		super(arg0);

	}

	public TextualException(Throwable arg0) {
		super(arg0);

	}

	public TextualException(String arg0, Throwable arg1) {
		super(arg0, arg1);

	}

}
