/*
 * Created on Nov 5, 2008
 * Created by Paul Gardner
 * 
 * Copyright 2008 Vuze, Inc.  All rights reserved.
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


package com.aelitis.azureus.plugins.remsearch.test;

import java.util.*;
import java.io.*;
import java.net.*;

import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.FileUtil;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderFactory;
import com.biglybt.pifimpl.local.utils.resourcedownloader.ResourceDownloaderFactoryImpl;

import com.biglybt.util.JSONUtils;

public class 
Main 
{
	private static String[] terms = 
	{	
		"bunny", "trout",
	};
	
	protected void
	search()
	{
		try{
			
			final ResourceDownloaderFactory rdf = ResourceDownloaderFactoryImpl.getSingleton();
			
			String term = terms[new Random().nextInt(terms.length)];
			
			URL	url = new URL( "http://www.vuze.com/psearch/search?q=" + term );
			
			System.out.println( "Searching: " + url );
			
			InputStream is = rdf.create( url ).download();
			
			String str = FileUtil.readInputStreamAsString( is, 65*1024 );
			
			int	p1 = str.indexOf( '{' );
			int	p2 = str.lastIndexOf( '}' );
			
			Map map = JSONUtils.decodeJSON( str.substring(p1,p2+1));
			
			String	sid = (String)map.get( "sid" );
			
			List engines = (List)map.get( "engines" );
			
			final AESemaphore sem = new AESemaphore( "x" );
			
			for (int i=0;i<engines.size();i++){
				
				Map	engine = (Map)engines.get(i);
				
				String	eid = (String)engine.get( "id" );
				
				final URL results = new URL( "http://www.vuze.com/psearch/get-results?sid=" + sid + "&eid=" + eid );
				
				new Thread()
				{
					@Override
					public void
					run()
					{
						try{
							InputStream is = rdf.create( results ).download();

							String str = FileUtil.readInputStreamAsString( is, 65*1024 );
							
							if ( str.startsWith( "webSearch.loadResults" )){
								
							}else{
								
								throw( new Exception( "Failed: " + str ));
							}
							
						}catch( Throwable e ){
							
							e.printStackTrace();
							
						}finally{
							
							sem.release();
						}
					}
				
				}.start();
			}
			
			for (int i=0;i<engines.size();i++){

				sem.reserve();
				
				System.out.println( "Got " + (i+1) + " of " + engines.size());
			}
		}catch( Throwable e ){
			
			e.printStackTrace();
		}
	}
	
	public static void
	main(
		String[]		args )
	{
		final int	conc 			= 10;
		final int	num_searches	= 5;
		
		final Main	tester = new Main();
		
		final AESemaphore sem = new AESemaphore( "y" );
		
		long	start = System.currentTimeMillis();
		
		for (int i=0;i<conc;i++){
		
			new Thread()
			{
				@Override
				public void
				run()
				{
					try{
						for (int i=0;i<num_searches;i++){
						
							tester.search();
						}
					}finally{ 
						
						sem.release();
					}
				}
			}.start();
		}
		
		for (int i=0;i<conc;i++){

			sem.reserve();
		}
		
		long	end = System.currentTimeMillis();

		long	elapsed 	= end-start;
		long	searches	= conc*num_searches;
		
		System.out.println( "Elapsed=" + elapsed + ",searches=" + searches + ": average=" + (elapsed/searches));
	}
}
