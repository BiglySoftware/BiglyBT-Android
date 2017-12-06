/*
 * Created on Aug 26, 2010
 * Created by Paul Gardner
 * 
 * Copyright 2010 Vuze, Inc.  All rights reserved.
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



package com.vuze.client.plugins.utp.core;

import java.io.File;

import com.biglybt.core.util.Constants;
import com.biglybt.pif.PluginInterface;

import com.vuze.client.plugins.utp.UTPPlugin;

public class 
UTPInterface 
{
	private static final boolean DEBUG_DLLS = false;
	
	public static boolean
	load(
		UTPCallback			callback )
	{
		File plugin_user_dir 	= callback.getPluginUserDir();
		File plugin_install_dir = callback.getPluginInstallDir();
		
		File log_file = null;
		
		try{
			log_file = new File( new File( plugin_user_dir.getParentFile().getParentFile(), "logs" ), "uTP_native.log" );
		
			if ( log_file.exists()){
				
				if ( log_file.length() > 1*1024*1024L ){
					
					File bak = new File( log_file.getAbsolutePath() + ".bak" );
					
					bak.delete();
					
					log_file.renameTo( bak );
				}
			}
		}catch( Throwable e ){
			
			callback.log( "Failed to tidy up old log files", e );
		}
		
		try{
			
			if ( Constants.isWindows ){
			
				File dll_dir;
				
				if ( System.getProperty( "os.arch", "" ).contains( "64" )){
					
					dll_dir = new File( plugin_install_dir, "x64" );
					
				}else{
					
					dll_dir = new File( plugin_install_dir, "win32" );
				}
			
				if ( DEBUG_DLLS ){
				
					System.load( new File( dll_dir, "msvcr100d.dll").getAbsolutePath());
					
				}else{
					
					System.load( new File( dll_dir, "msvcr100.dll").getAbsolutePath());
				}
				
				System.load( new File( dll_dir, "utp.dll").getAbsolutePath());
				
			}else if ( Constants.isOSX ){
				
				System.load(new File( plugin_install_dir, "libutp.jnilib" ).getAbsolutePath());
				
			}else{
				
				throw( new Exception( "Unsupported platform" ));
			}
			
			initialise( Constants.IS_CVS_VERSION?log_file.getAbsolutePath():null, callback );
			
			return( true );
			
		}catch( Throwable e ){
			
			callback.log( "Failed to load dll", e );
			
			return( false );
		}
	}
	
	private static native void
	initialise(
		String				log_file,
		UTPCallback			callback )
	
		throws UTPInterfaceException;
	
	public static native void
	checkTimeouts();
	
	public static native long[] 
	connect(
		String		to_address,
		int			to_port )
	
		throws UTPInterfaceException;
	
	public static native boolean
	receive(
		String		from_address,
		int			from_port,
		byte[]		data,
		int			length )
	
		throws UTPInterfaceException;
	
	public static native boolean
	write(
		long		utp_socket,
		int			avail_bytes )
	
		throws UTPInterfaceException;
	
	public static native void
	receiveBufferDrained(
		long		utp_socket )
	
		throws UTPInterfaceException;
		
	public static native void
	close(
		long		utp_socket )
	
		throws UTPInterfaceException;
	
	
	public static native void
	setSocketOptions(
		long		fd )
	
		throws UTPInterfaceException;
}
