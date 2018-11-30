/*
 * Created on Mar 1, 2013
 * Created by Paul Gardner
 * 
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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

package com.aelitis.azureus.plugins.xmwebui.client.rpc;

@SuppressWarnings( "serial" )
public class 
XMRPCClientException 
	extends Exception
{
	public static final int	ET_GENERIC				= 1;
	public static final int	ET_BAD_ACCESS_CODE		= 2;
	public static final int	ET_NO_BINDING			= 3;
	public static final int	ET_CRYPTO_FAILED		= 4;
	public static final int	ET_FEATURE_DISABLED		= 5;
	
	private int type = ET_GENERIC;
	
	public
	XMRPCClientException(
		int		_type )
	{
		super( "Error type " + _type );
		
		type	= _type;
	}
	
	public
	XMRPCClientException(
		int		_type,
		String	_message )
	{
		super( "Error type " + _type + (_message==null?"":( ": " + _message )));
		
		type	= _type;
	}
	
	public
	XMRPCClientException(
		String	str )
	{
		super( str );
	}
	
	public 
	XMRPCClientException(
		String		str,
		Throwable	cause )
	{
		super( str, cause );
	}
	
	public int
	getType()
	{
		return( type );
	}
	
	protected void
	setType(
		int	t )
	{
		type	= t;
	}
}
