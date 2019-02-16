/*
 * Created on Jan 25, 2013
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



package com.aelitis.azureus.core.networkmanager.impl.utp;

import java.util.Random;

import com.biglybt.core.util.RandomUtils;
import com.biglybt.core.util.SystemTime;

public class 
UTPUtils 
{
	static long startPerformanceCounter;
	static long startGetTickCount;
	// MSVC 6 standard doesn't like division with uint64s
	static long counterPerMicrosecond;

	static long last_micros		= 0;
	static long last_counter		= 0;
	static long last_tick			= 0;
	static long frequency			= 0;
	static long monoOffset		= 0;

	static boolean bork_logged			= false;

	static Random random = new Random( RandomUtils.SECURE_RANDOM.nextLong());
	
	static int
	UTP_Random()
	{
		return( random.nextInt());
	}


	static long 
	UTP_GetMilliseconds()
	{
		return( System.currentTimeMillis());
	}


	static{		
		startGetTickCount		= System.currentTimeMillis();
		startPerformanceCounter	= System.nanoTime();
		counterPerMicrosecond 	= 1000;
	}

	static long abs64(long x) { return x < 0 ? -x : x; }

	//static long last_log_tick;
	//static int   log_count = 0;

	//static long[] counter_history = new long[16];
	//static long[] tick_history = new long[16];
	//static int   history_index;

	static long 
	UTP_GetMicroseconds()
	{
		long counter 	= SystemTime.getHighPrecisionCounter();
		long tick		= System.currentTimeMillis();


		// unfortunately, QueryPerformanceCounter is not guaranteed
		// to be monotonic. Make it so.
		long ret = (counter - startPerformanceCounter) / counterPerMicrosecond;
		// if the QPC clock leaps more than one second off GetTickCount64()
		// something is seriously fishy. Adjust QPC to stay monotonic
		long tick_diff = tick - startGetTickCount;

		//tick_history[history_index] = tick;
		//counter_history[history_index] = counter;
		//history_index++;
		//if ( history_index == 16 ){
		//	history_index = 0;
		//}

		if (abs64(ret / 100000 - tick_diff / 100) > 10 ) {
			startPerformanceCounter -= (tick_diff * 1000 - ret) * counterPerMicrosecond;
			ret = (counter - startPerformanceCounter) / counterPerMicrosecond;

			monoOffset = 0;
		}

		/*
		if ( ret < last_micros ){

			System.out.println( "micros went backwards" );
		}
		*/
		
		last_counter	= counter;
		last_tick		= tick;

		ret += monoOffset;
			
		if ( ret < last_micros ){
				
			monoOffset += (last_micros - ret );
				
			ret = last_micros;
		}
			
		last_micros		= ret;

		return ret;
	}

	public static void
	main(
		String[]	args )
	{
		while( true ){
			
			long	milli 	= UTP_GetMilliseconds();
			long	micro	= UTP_GetMicroseconds();
			
			System.out.println( milli + " / " + micro );
			
			try{
				Thread.sleep( 100 );
			}catch( Throwable e ){
				
			}
		}
	}
}
