/*
 * Created on 23 Jun 2006
 * Created by Paul Gardner
 * Copyright (C) 2006 Aelitis, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * AELITIS, SAS au capital de 46,603.30 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package com.aelitis.azureus.core.networkmanager.impl.utp;

import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;

import com.biglybt.core.networkmanager.impl.TransportHelper;

public class 
UTPSelector 
{
	private static final int POLL_FREQUENCY	= COConfigurationManager.getIntParameter( "network.utp.poll.time", 50 );
	
	private AEThread2	thread;
	
	private List<Object[]>	ready_set	= new LinkedList<Object[]>();
	private AESemaphore		ready_sem	= new AESemaphore( "UTPSelector" );
	
	private volatile boolean destroyed;
	
	protected
	UTPSelector(
		final UTPConnectionManager		manager )
	{
		thread = 
			new AEThread2( "UTPSelector", true )
			{
				public void
				run()
				{
					boolean	quit		= false;
					long	last_poll	= 0;
					
					int		last_connection_count = 0;
					
					while( !quit ){
					
						if ( destroyed ){
							
								// one last dispatch cycle
							
							quit	= true;
						}
						
						long	now = SystemTime.getMonotonousTime();
						
						if ( now - last_poll >= POLL_FREQUENCY ){
							
							last_connection_count = manager.poll( ready_sem, now );
							
							last_poll	= now;
						}
						
						if ( ready_sem.getValue() == 0 ){
							
							manager.inputIdle();
						}
						
						if ( ready_sem.reserve( last_connection_count==0?1000:(POLL_FREQUENCY/2 ))){
							
							Object[]	entry;
							
							synchronized( ready_set ){
								
								if ( ready_set.size() == 0 ){
																		
									continue;
								}
								
								entry = ready_set.remove(0);
							}
							
						
							TransportHelper	transport 	= (TransportHelper)entry[0];
							
							TransportHelper.selectListener	listener = (TransportHelper.selectListener)entry[1];
							
							if ( listener == null ){
								
								Debug.out( "Null listener" );
								
							}else{
								
								Object	attachment = entry[2];
								
								try{
									if ( entry.length == 3 ){
										
										listener.selectSuccess( transport, attachment );
										
									}else{
										
										listener.selectFailure( transport, attachment, (Throwable)entry[3] );
										
									}
								}catch( Throwable e ){
									
									Debug.printStackTrace(e);
								}
							}
						}
					}
				}
			};
			
		thread.setPriority( Thread.MAX_PRIORITY-1 );
		
		thread.start();
	}
	
	protected void
	destroy()
	{
		synchronized( ready_set ){
			
			destroyed	= true;
		}
	}	

	protected void
	ready(
		TransportHelper						transport,
		TransportHelper.selectListener		listener,
		Object								attachment )
	{
		boolean	removed = false;
		
		synchronized( ready_set ){

			if ( destroyed ){
				
				Debug.out( "Selector has been destroyed" );
				
				throw( new RuntimeException( "Selector has been destroyed" ));
			}
			
			Iterator<Object[]>	it = ready_set.iterator();
			
			while( it.hasNext()){
			
				Object[]	entry = (Object[])it.next();
				
				if ( entry[1] == listener ){
					
					it.remove();
					
					removed	= true;
					
					break;
				}
			}
			
			ready_set.add( new Object[]{ transport, listener, attachment });
		}
		
		if ( !removed ){
			
			ready_sem.release();
		}
	}
	
	protected void
	ready(
		TransportHelper						transport,
		TransportHelper.selectListener		listener,
		Object								attachment,
		Throwable							error )
	{
		boolean	removed = false;
		
		synchronized( ready_set ){

			if ( destroyed ){
				
				Debug.out( "Selector has been destroyed" );
				
				throw( new RuntimeException( "Selector has been destroyed" ));
			}
		
			Iterator	it = ready_set.iterator();
			
			while( it.hasNext()){
			
				Object[]	entry = (Object[])it.next();
				
				if ( entry[1] == listener ){
					
					it.remove();
					
					removed	= true;
					
					break;
				}
			}
			
			ready_set.add( new Object[]{ transport, listener, attachment, error });
		}
		
		if ( !removed ){
			
			ready_sem.release();
		}
	}
	
	protected void
	cancel(
		TransportHelper						transport,
		TransportHelper.selectListener		listener )
	{
		synchronized( ready_set ){
		
			Iterator	it = ready_set.iterator();
			
			while( it.hasNext()){
			
				Object[]	entry = (Object[])it.next();
				
				if ( entry[0] == transport && entry[1] == listener ){
					
					it.remove();
										
					break;
				}
			}
		}
	}
}
