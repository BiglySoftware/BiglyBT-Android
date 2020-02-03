/*
 * Copyright (c) Azureus Software, Inc, All Rights Reserved.
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
 */

/*
 * File    : DisplayFormatters.java
 * Created : 07-Oct-2003
 * By      : gardnerpar
 *
 * Azureus - a Java Bittorrent client
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License.
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

package com.biglybt.util;

/**
 * @author gardnerpar
 *
 */

import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Arrays;

import com.biglybt.android.client.R;

import android.content.res.Resources;

@SuppressWarnings("ALL")
public class DisplayFormatters
{
	public static final String INFINITY_STRING = "\u221E"; // "oo";pa  

	final private static boolean ROUND_NO = true;

	//final private static boolean ROUND_YES = false;
	final private static boolean TRUNCZEROS_NO = false;
	//final private static boolean TRUNCZEROS_YES = true;

	final public static int UNIT_B = 0;

	final public static int UNIT_KB = 1;

	final public static int UNIT_MB = 2;

	final public static int UNIT_GB = 3;

	final public static int UNIT_TB = 4;

	final private static int UNITS_PRECISION[] = {
		0, // B
		1, //KB
		2, //MB
		2, //GB
		3 //TB
	};

	final private static NumberFormat[] cached_number_formats = new NumberFormat[20];

	private static NumberFormat percentage_format;

	private static String[] units;

	private static String[] units_bits;

	private static String[] units_rate;

	private static String[] units_base10;

	private static String per_sec;

	private static boolean use_si_units = false;

	private static boolean force_si_values = true;

	private static boolean use_units_rate_bits = false;

	private static boolean not_use_GB_TB = false;

	private static int unitsStopAt = (not_use_GB_TB) ? UNIT_MB : UNIT_TB;

	//private static int message_text_state = 0;

	private static char decimalSeparator;

	private static NumberFormat numberFormatInstance;

	static {
/*
		COConfigurationManager.addAndFireParameterListeners( 
				new String[]{
					"config.style.useSIUnits",
					"config.style.forceSIValues",
					"config.style.useUnitsRateBits",
					"config.style.doNotUseGB",
				},
				new ParameterListener()
				{
					public void
					parameterChanged(
						String	x )
					{
						use_si_units 		= COConfigurationManager.getBooleanParameter("config.style.useSIUnits");
						force_si_values 		= COConfigurationManager.getBooleanParameter("config.style.forceSIValues");
						use_units_rate_bits = COConfigurationManager.getBooleanParameter("config.style.useUnitsRateBits");
			            not_use_GB_TB 		= COConfigurationManager.getBooleanParameter("config.style.doNotUseGB");
			            
			            unitsStopAt = (not_use_GB_TB) ? UNIT_MB : UNIT_TB;

						setUnits();
					}
				});

	COConfigurationManager.addListener(
		new COConfigurationListener()
		{
			public void 
			configurationSaved() 
			{
				setUnits();
				loadMessages();
				
			}

		});
		
		COConfigurationManager.addAndFireParameterListeners( 
				new String[]{ 
						"config.style.dataStatsOnly", 
						"config.style.separateProtDataStats" 
				},
				new ParameterListener()
				{
					public void
					parameterChanged(
						String	x )
					{
						separate_prot_data_stats = COConfigurationManager.getBooleanParameter("config.style.separateProtDataStats");
						data_stats_only			 = COConfigurationManager.getBooleanParameter("config.style.dataStatsOnly");
					}
				});
*/
		setUnits();

		/*
		loadMessages();
		*/
	}

	public static void setUnits() {
		// (1) http://physics.nist.gov/cuu/Units/binary.html
		// (2) http://www.isi.edu/isd/LOOM/documentation/unit-definitions.text

		/*
		 * So, Android has com.android.internal.R.string.byteShort etc.. 
		 * probably not SI and we'd have to reflect to get them and fallback if
		 * not found..
		 */

		units = new String[unitsStopAt + 1];
		units_bits = new String[unitsStopAt + 1];
		units_rate = new String[unitsStopAt + 1];
		if (use_si_units) {
			// fall through intentional
			switch (unitsStopAt) {
				case UNIT_TB:
					units[UNIT_TB] = getUnit("TiB");
					units_bits[UNIT_TB] = getUnit("Tibit");
					units_rate[UNIT_TB] = (use_units_rate_bits) ? getUnit("Tibit")
							: getUnit("TiB");
				case UNIT_GB:
					units[UNIT_GB] = getUnit("GiB");
					units_bits[UNIT_GB] = getUnit("Gibit");
					units_rate[UNIT_GB] = (use_units_rate_bits) ? getUnit("Gibit")
							: getUnit("GiB");
				case UNIT_MB:
					units[UNIT_MB] = getUnit("MiB");
					units_bits[UNIT_MB] = getUnit("Mibit");
					units_rate[UNIT_MB] = (use_units_rate_bits) ? getUnit("Mibit")
							: getUnit("MiB");
				case UNIT_KB:
					// can be upper or lower case k
					units[UNIT_KB] = getUnit("KiB");
					units_bits[UNIT_KB] = getUnit("Kibit");

					// can be upper or lower case k, upper more consistent
					units_rate[UNIT_KB] = (use_units_rate_bits) ? getUnit("Kibit")
							: getUnit("KiB");
				case UNIT_B:
					units[UNIT_B] = getUnit("B");
					units_bits[UNIT_B] = getUnit("bit");
					units_rate[UNIT_B] = (use_units_rate_bits) ? getUnit("bit")
							: getUnit("B");
			}
		} else {
			switch (unitsStopAt) {
				case UNIT_TB:
					units[UNIT_TB] = getUnit("TB");
					units_bits[UNIT_TB] = getUnit("Tbit");
					units_rate[UNIT_TB] = (use_units_rate_bits) ? getUnit("Tbit")
							: getUnit("TB");
				case UNIT_GB:
					units[UNIT_GB] = getUnit("GB");
					units_bits[UNIT_GB] = getUnit("Gbit");
					units_rate[UNIT_GB] = (use_units_rate_bits) ? getUnit("Gbit")
							: getUnit("GB");
				case UNIT_MB:
					units[UNIT_MB] = getUnit("MB");
					units_bits[UNIT_MB] = getUnit("Mbit");
					units_rate[UNIT_MB] = (use_units_rate_bits) ? getUnit("Mbit")
							: getUnit("MB");
				case UNIT_KB:
					// yes, the k should be lower case
					units[UNIT_KB] = getUnit("kB");
					units_bits[UNIT_KB] = getUnit("kbit");
					units_rate[UNIT_KB] = (use_units_rate_bits) ? getUnit("kbit")
							: getUnit("kB");
				case UNIT_B:
					units[UNIT_B] = getUnit("B");
					units_bits[UNIT_B] = getUnit("bit");
					units_rate[UNIT_B] = (use_units_rate_bits) ? getUnit("bit")
							: getUnit("B");
			}
		}

		per_sec = "/s"; // getResourceString("Formats.units.persec", "/s");

		units_base10 = new String[] {
			getUnit(use_units_rate_bits ? "bit" : "B"),
			getUnit(use_units_rate_bits ? "kbit" : "KB"),
			getUnit(use_units_rate_bits ? "Mbit" : "MB"),
			getUnit(use_units_rate_bits ? "Gbit" : "GB"),
			getUnit(use_units_rate_bits ? "Tbit" : "TB")
		};

		for (int i = 0; i <= unitsStopAt; i++) {
			units[i] = units[i];
			units_rate[i] = units_rate[i] + per_sec;
		}

		Arrays.fill(cached_number_formats, null);

		percentage_format = NumberFormat.getPercentInstance();
		percentage_format.setMinimumFractionDigits(1);
		percentage_format.setMaximumFractionDigits(1);

		decimalSeparator = new DecimalFormatSymbols().getDecimalSeparator();
	}

	private static String getUnit(String key) {

		return key;
		//return (" " + getResourceString("Formats.units." + key, key));
	}

/*
	
	private static String	PeerManager_status_finished;
	private static String	PeerManager_status_finishedin;
	private static String	Formats_units_alot;
	private static String	discarded;
	private static String	ManagerItem_waiting;
	private static String	ManagerItem_initializing;
	private static String	ManagerItem_allocating;
	private static String	ManagerItem_checking;
	private static String	ManagerItem_finishing;
	private static String	ManagerItem_ready;
	private static String	ManagerItem_downloading;
	private static String	ManagerItem_seeding;
	private static String	ManagerItem_superseeding;
	private static String	ManagerItem_stopping;
	private static String	ManagerItem_stopped;
	private static String	ManagerItem_paused;
	private static String	ManagerItem_queued;
	private static String	ManagerItem_error;
	private static String	ManagerItem_forced;
	private static String	ManagerItem_moving;
	
	private static String	yes;
	private static String	no;
	
	public static void
	loadMessages()
	{
		PeerManager_status_finished 	= getResourceString( "PeerManager.status.finished", "Finished" );
		PeerManager_status_finishedin	= getResourceString( "PeerManager.status.finishedin", "Finished in" );
		Formats_units_alot				= getResourceString( "Formats.units.alot", "A lot" );
		discarded						= getResourceString( "discarded", "discarded" );
		ManagerItem_waiting				= getResourceString( "ManagerItem.waiting", "waiting" );
		ManagerItem_initializing		= getResourceString( "ManagerItem.initializing", "initializing" );
		ManagerItem_allocating			= getResourceString( "ManagerItem.allocating", "allocating" );
		ManagerItem_checking			= getResourceString( "ManagerItem.checking", "checking" );
		ManagerItem_finishing			= getResourceString( "ManagerItem.finishing", "finishing" );
		ManagerItem_ready				= getResourceString( "ManagerItem.ready", "ready" );
		ManagerItem_downloading			= getResourceString( "ManagerItem.downloading", "downloading" );
		ManagerItem_seeding				= getResourceString( "ManagerItem.seeding", "seeding" );
		ManagerItem_superseeding		= getResourceString( "ManagerItem.superseeding", "superseeding" );
		ManagerItem_stopping			= getResourceString( "ManagerItem.stopping", "stopping" );
		ManagerItem_stopped				= getResourceString( "ManagerItem.stopped", "stopped" );
		ManagerItem_paused				= getResourceString( "ManagerItem.paused", "paused" );
		ManagerItem_queued				= getResourceString( "ManagerItem.queued", "queued" );
		ManagerItem_error				= getResourceString( "ManagerItem.error", "error" );
		ManagerItem_forced				= getResourceString( "ManagerItem.forced", "forced" );
		ManagerItem_moving				= getResourceString( "ManagerItem.moving", "moving" );
		yes								= getResourceString( "GeneralView.yes", "Yes" );
		no								= getResourceString( "GeneralView.no", "No" );
	}

	private static String getResourceString(String key, String def) {
		if ( message_text_state == 0 ){
			
				// this fooling around is to permit the use of this class in the absence of the (large) overhead
				// of resource bundles
			
			try{
				MessageText.class.getName();
				
				message_text_state	= 1;
				
			}catch( Throwable e ){
				
				message_text_state	= 2;
			}
		}
		
		if ( message_text_state == 1 ){
			
			return( MessageText.getString( key ));
			
		}else{
			
		return (def);
		}
	}

	public static String
	getYesNo(
		boolean	b )
	{
		return( b?yes:no );
	}
	*/

	public static String getRateUnit(int unit_size) {
		return units_rate[unit_size];
	}

	public static String getUnit(int unit_size) {
		return units[unit_size];
	}

	public static String getRateUnitBase10(int unit_size) {
		return units_base10[unit_size] + per_sec;
	}

	public static String getUnitBase10(int unit_size) {
		return units_base10[unit_size];
	}

	public static boolean isRateUsingBits() {
		return (use_units_rate_bits);
	}

	public static String formatByteCountToKiBEtc(int n) {
		return (formatByteCountToKiBEtc((long) n));
	}

	public static String formatByteCountToKiBEtc(long n) {
		return (formatByteCountToKiBEtc(n, false, TRUNCZEROS_NO));
	}

	public static String formatByteCountToKiBEtc(long n, boolean bTruncateZeros) {
		return (formatByteCountToKiBEtc(n, false, bTruncateZeros));
	}

	public static String formatByteCountToKiBEtc(long n, boolean rate,
			boolean bTruncateZeros) {
		return formatByteCountToKiBEtc(n, rate, bTruncateZeros, -1);
	}

	public static String formatByteCountToKiBEtc(long n, boolean rate,
			boolean bTruncateZeros, int precision) {
		double dbl = (rate && use_units_rate_bits) ? n * 8 : n;

		int unitIndex = UNIT_B;

		long div = force_si_values ? 1024 : (use_si_units ? 1024 : 1000);

		while (dbl >= div && unitIndex < unitsStopAt) {

			dbl /= div;
			unitIndex++;
		}

		if (precision < 0) {
			precision = UNITS_PRECISION[unitIndex];
		}

		// round for rating, because when the user enters something like 7.3kbps
		// they don't want it truncated and displayed as 7.2  
		// (7.3*1024 = 7475.2; 7475/1024.0 = 7.2998;  trunc(7.2998, 1 prec.) == 7.2
		//
		// Truncate for rest, otherwise we get complaints like:
		// "I have a 1.0GB torrent and it says I've downloaded 1.0GB.. why isn't 
		//  it complete? waaah"

		return formatDecimal(dbl, precision, bTruncateZeros, rate)
				+ (rate ? units_rate[unitIndex] : units[unitIndex]);
	}

	public static String formatByteCountToKiBEtc(long n, boolean rate,
			boolean bTruncateZeros, int precision, int minUnit) {
		double dbl = (rate && use_units_rate_bits) ? n * 8 : n;

		int unitIndex = UNIT_B;

		long div = force_si_values ? 1024 : (use_si_units ? 1024 : 1000);

		while (dbl >= div && unitIndex < unitsStopAt) {

			dbl /= div;
			unitIndex++;
		}

		while (unitIndex < minUnit) {
			dbl /= div;
			unitIndex++;
		}
		if (precision < 0) {
			precision = UNITS_PRECISION[unitIndex];
		}

		// round for rating, because when the user enters something like 7.3kbps
		// they don't want it truncated and displayed as 7.2  
		// (7.3*1024 = 7475.2; 7475/1024.0 = 7.2998;  trunc(7.2998, 1 prec.) == 7.2
		//
		// Truncate for rest, otherwise we get complaints like:
		// "I have a 1.0GB torrent and it says I've downloaded 1.0GB.. why isn't 
		//  it complete? waaah"

		return formatDecimal(dbl, precision, bTruncateZeros, rate)
				+ (rate ? units_rate[unitIndex] : units[unitIndex]);
	}

/*	
	public static boolean
	isDataProtSeparate()
	{
		return( separate_prot_data_stats );
	}
	
	public static String
	formatDataProtByteCountToKiBEtc(
		long	data,
		long	prot )
	{
		if ( separate_prot_data_stats ){
			if ( data == 0 && prot == 0 ){
				return( formatByteCountToKiBEtc(0));
			}else if ( data == 0 ){
				return( "(" + formatByteCountToKiBEtc( prot) + ")");
			}else if ( prot == 0 ){
				return( formatByteCountToKiBEtc( data ));
			}else{
				return(formatByteCountToKiBEtc(data)+" ("+ formatByteCountToKiBEtc(prot)+")");
			}
		}else if ( data_stats_only ){
			return( formatByteCountToKiBEtc( data ));
		}else{
			return( formatByteCountToKiBEtc( prot + data ));
		}
	}
	
	public static String
	formatDataProtByteCountToKiBEtcPerSec(
		long	data,
		long	prot )
	{
		if ( separate_prot_data_stats ){
			if ( data == 0 && prot == 0 ){
				return(formatByteCountToKiBEtcPerSec(0));
			}else if ( data == 0 ){
				return( "(" + formatByteCountToKiBEtcPerSec( prot) + ")");
			}else if ( prot == 0 ){
				return( formatByteCountToKiBEtcPerSec( data ));
			}else{
				return(formatByteCountToKiBEtcPerSec(data)+" ("+ formatByteCountToKiBEtcPerSec(prot)+")");
			}
		}else if ( data_stats_only ){
			return( formatByteCountToKiBEtcPerSec( data ));
		}else{	
			return( formatByteCountToKiBEtcPerSec( prot + data ));
		}
	}
*/
	public static String formatByteCountToKiBEtcPerSec(long n) {
		return (formatByteCountToKiBEtc(n, true, TRUNCZEROS_NO));
	}

	public static String formatByteCountToKiBEtcPerSec(long n,
			boolean bTruncateZeros) {
		return (formatByteCountToKiBEtc(n, true, bTruncateZeros));
	}

	// base 10 ones

	public static String formatByteCountToBase10KBEtc(long n) {
		if (use_units_rate_bits) {
			n *= 8;
		}

		if (n < 1000) {

			return n + units_base10[UNIT_B];

		} else if (n < 1000 * 1000) {

			return (n / 1000) + "." + ((n % 1000) / 100) + units_base10[UNIT_KB];

		} else if (n < 1000L * 1000L * 1000L || not_use_GB_TB) {

			return (n / (1000L * 1000L)) + "."
					+ ((n % (1000L * 1000L)) / (1000L * 100L)) + units_base10[UNIT_MB];

		} else if (n < 1000L * 1000L * 1000L * 1000L) {

			return (n / (1000L * 1000L * 1000L)) + "."
					+ ((n % (1000L * 1000L * 1000L)) / (1000L * 1000L * 100L))
					+ units_base10[UNIT_GB];

		} else if (n < 1000L * 1000L * 1000L * 1000L * 1000L) {

			return (n / (1000L * 1000L * 1000L * 1000L)) + "."
					+ ((n % (1000L * 1000L * 1000L * 1000L))
							/ (1000L * 1000L * 1000L * 100L))
					+ units_base10[UNIT_TB];
		} else {

			return "a lot";
		}
	}

	public static String formatByteCountToBase10KBEtcPerSec(long n) {
		return (formatByteCountToBase10KBEtc(n) + per_sec);
	}

	/**
	 * Print the BITS/second in an international format.
	 * @param n - always formatted using SI (i.e. decimal) prefixes
	 * @return String in an internationalized format.
	 */
	public static String formatByteCountToBitsPerSec(long n) {
		double dbl = n * 8;

		int unitIndex = UNIT_B;

		long div = 1000;

		while (dbl >= div && unitIndex < unitsStopAt) {

			dbl /= div;
			unitIndex++;
		}

		int precision = UNITS_PRECISION[unitIndex];

		return (formatDecimal(dbl, precision, true, true) + units_bits[unitIndex]
				+ per_sec);
	}

/*    
public static String
formatETA(long eta) 
{
	return( formatETA( eta, false ));
}

private static final SimpleDateFormat abs_df = new SimpleDateFormat( "yyyy/MM/dd HH:mm:ss" );

public static String
formatETA(long eta,boolean abs ) 
{
	if (eta == 0) return PeerManager_status_finished;
	if (eta == -1) return "";
	if (eta > 0){
		if ( abs && !(eta == Constants.CRAPPY_INFINITY_AS_INT || eta >= Constants.CRAPPY_INFINITE_AS_LONG )){
		
			long now 	= SystemTime.getCurrentTime();
			long then 	= now + eta*1000;
			
			if ( eta > 5*60 ){
				
				then = (then/(60*1000))*(60*1000);
			}
			
			String	str1 = abs_df.format(new Date( now ));
			String	str2 = abs_df.format(new Date( then ));

			int	len = Math.min(str1.length(), str2.length())-2;
			
			int	diff_at = len;
			
			for ( int i=0; i<len; i++){
				
				char	c1 = str1.charAt( i );
				
				if ( c1 != str2.charAt(i)){
					
					diff_at = i;
					
					break;
				}
			}
			
			String	res;
			
			if ( diff_at >= 11 ){
				
				res = str2.substring( 11 );
				
			}else if ( diff_at >= 5 ){
				
				res = str2.substring( 5 );
				
			}else{
				
				res = str2;
			}
			
			return( res  );
			
		}else{
			return TimeFormatter.format(eta);
		}
	}

	return PeerManager_status_finishedin + " " + TimeFormatter.format(eta * -1);
}


	public static String
	formatDownloaded(
		DownloadManagerStats	stats )
	{
		long	total_discarded = stats.getDiscarded();
		long	total_received 	= stats.getTotalGoodDataBytesReceived();

		if(total_discarded == 0){

			return formatByteCountToKiBEtc(total_received);

		}else{

			return formatByteCountToKiBEtc(total_received) + " ( " + 
					DisplayFormatters.formatByteCountToKiBEtc(total_discarded) + " " + 
					discarded + " )";
		}
	}

	public static String
	formatHashFails(
		DownloadManager		download_manager )
	{
		TOTorrent	torrent = download_manager.getTorrent();
		
		if ( torrent != null ){
			
			long bad = download_manager.getStats().getHashFailBytes();
	
					// size can exceed int so ensure longs used in multiplication
	
			long count = bad / (long)torrent.getPieceLength();
	
			String result = count + " ( " + formatByteCountToKiBEtc(bad) + " )";
	
			return result;
	  	}

		return "";
	}

	public static String
	formatDownloadStatus(
		DownloadManager		manager )
	{
		int state = manager.getState();

		String	tmp = "";

		switch (state) {
			case DownloadManager.STATE_QUEUED:
				tmp = ManagerItem_queued;
				break;

			case DownloadManager.STATE_DOWNLOADING:
				tmp = ManagerItem_downloading;
				break;

			case DownloadManager.STATE_SEEDING:{

				DiskManager diskManager = manager.getDiskManager();

				if ( diskManager != null ){
						
					int	mp = diskManager.getMoveProgress();
					
					if ( mp != -1 ){
						
						tmp = ManagerItem_moving + ": "	+ formatPercentFromThousands( mp );
						
					}else{
						int done = diskManager.getCompleteRecheckStatus();
	
						if ( done != -1 ){
	
							tmp = ManagerItem_seeding + " + " + ManagerItem_checking + ": "	+ formatPercentFromThousands(done);
						}
					}
				}
				
				if ( tmp == "" ){
					
					if (manager.getPeerManager() != null && manager.getPeerManager().isSuperSeedMode()) {
					
						tmp = ManagerItem_superseeding;
						
					}else{
						
						tmp = ManagerItem_seeding;
					}
				}
				
				break;
			}
			case DownloadManager.STATE_STOPPED:
				tmp = manager.isPaused() ? ManagerItem_paused : ManagerItem_stopped;
				break;

			case DownloadManager.STATE_ERROR:
				tmp = ManagerItem_error + ": " + manager.getErrorDetails();
				break;

			case DownloadManager.STATE_WAITING:
				tmp = ManagerItem_waiting;
				break;

			case DownloadManager.STATE_INITIALIZING:
				tmp = ManagerItem_initializing;
				break;

			case DownloadManager.STATE_INITIALIZED:
				tmp = ManagerItem_initializing;
				break;

			case DownloadManager.STATE_ALLOCATING:{
				tmp = ManagerItem_allocating;
				DiskManager diskManager = manager.getDiskManager();
				if (diskManager != null){		
					tmp += ": " + formatPercentFromThousands( diskManager.getPercentDone());
				}
				break;
			}
			case DownloadManager.STATE_CHECKING:
				tmp = ManagerItem_checking + ": "
						+ formatPercentFromThousands(manager.getStats().getCompleted());
				break;

			case DownloadManager.STATE_FINISHING:
				tmp = ManagerItem_finishing;
				break;

			case DownloadManager.STATE_READY:
				tmp = ManagerItem_ready;
				break;

			case DownloadManager.STATE_STOPPING:
				tmp = ManagerItem_stopping;
				break;

			default:
				tmp = String.valueOf(state);
		}

		if (manager.isForceStart() &&
		    (state == DownloadManager.STATE_SEEDING ||
		     state == DownloadManager.STATE_DOWNLOADING))
			tmp = ManagerItem_forced + " " + tmp;
		return( tmp );
	}

	public static String
	formatDownloadStatusDefaultLocale(
		DownloadManager		manager )
	{
		int state = manager.getState();

		String	tmp = "";

		DiskManager	dm = manager.getDiskManager();
		
		switch (state) {
		  case DownloadManager.STATE_WAITING :
			tmp = MessageText.getDefaultLocaleString("ManagerItem.waiting");
			break;
		  case DownloadManager.STATE_INITIALIZING :
			  tmp = MessageText.getDefaultLocaleString("ManagerItem.initializing");
			  break;
		  case DownloadManager.STATE_INITIALIZED :
			  tmp = MessageText.getDefaultLocaleString("ManagerItem.initializing");
			  break;
		  case DownloadManager.STATE_ALLOCATING :
			tmp = MessageText.getDefaultLocaleString("ManagerItem.allocating");
			break;
		  case DownloadManager.STATE_CHECKING :
			tmp = MessageText.getDefaultLocaleString("ManagerItem.checking");
			break;
		  case DownloadManager.STATE_FINISHING :
		    tmp = MessageText.getDefaultLocaleString("ManagerItem.finishing");
		    break;
   case DownloadManager.STATE_READY :
			tmp = MessageText.getDefaultLocaleString("ManagerItem.ready");
			break;
		  case DownloadManager.STATE_DOWNLOADING :
			tmp = MessageText.getDefaultLocaleString("ManagerItem.downloading");
			break;
		  case DownloadManager.STATE_SEEDING :
		  	if (dm != null && dm.getCompleteRecheckStatus() != -1 ) {
		  		int	done = dm.getCompleteRecheckStatus();
				  
				if ( done == -1 ){
				  done = 1000;
				}
				  
		  		tmp = MessageText.getDefaultLocaleString("ManagerItem.seeding") + " + " + 
		  				MessageText.getDefaultLocaleString("ManagerItem.checking") +
		  				": " + formatPercentFromThousands( done );
		  	}
		  	else if(manager.getPeerManager()!= null && manager.getPeerManager().isSuperSeedMode()){

		  		tmp = MessageText.getDefaultLocaleString("ManagerItem.superseeding");
		  	}
		  	else {
		  		tmp = MessageText.getDefaultLocaleString("ManagerItem.seeding");
		  	}
		  	break;
		  case DownloadManager.STATE_STOPPING :
		  	tmp = MessageText.getDefaultLocaleString("ManagerItem.stopping");
		  	break;
		  case DownloadManager.STATE_STOPPED :
			tmp = MessageText.getDefaultLocaleString(manager.isPaused()?"ManagerItem.paused":"ManagerItem.stopped"); 
			break;
		  case DownloadManager.STATE_QUEUED :
			tmp = MessageText.getDefaultLocaleString("ManagerItem.queued"); 
			break;
		  case DownloadManager.STATE_ERROR :
			tmp = MessageText.getDefaultLocaleString("ManagerItem.error").concat(": ").concat(manager.getErrorDetails()); //$NON-NLS-1$ //$NON-NLS-2$
			break;
			default :
			tmp = String.valueOf(state);
		}

		return( tmp );
	}

	public static String
	trimDigits(
		String		str,
		int			num_digits )
	{
		char[] 	chars 	= str.toCharArray();
		String 	res 	= "";
		int		digits 	= 0;
		
		for (int i=0;i<chars.length;i++){
			char c = chars[i];
			if ( Character.isDigit(c)){
				digits++;
				if ( digits <= num_digits ){
					res += c;
				}
			}else if ( c == '.' && digits >= 3 ){
									
			}else{
				res += c;
			}
		}
		
		return( res );
	}
*/
	public static String formatPercentFromThousands(int thousands) {

		return percentage_format.format(thousands / 1000.0);
	}

/*

public static String formatTimeStamp(long time) {
StringBuffer sb = new StringBuffer();
Calendar calendar = Calendar.getInstance();
calendar.setTimeInMillis(time);
sb.append('[');
sb.append(formatIntToTwoDigits(calendar.get(Calendar.DAY_OF_MONTH)));
sb.append('.');
sb.append(formatIntToTwoDigits(calendar.get(Calendar.MONTH)+1));	// 0 based
sb.append('.');
sb.append(calendar.get(Calendar.YEAR));
sb.append(' ');
sb.append(formatIntToTwoDigits(calendar.get(Calendar.HOUR_OF_DAY)));
sb.append(':');
sb.append(formatIntToTwoDigits(calendar.get(Calendar.MINUTE)));
sb.append(':');
sb.append(formatIntToTwoDigits(calendar.get(Calendar.SECOND)));
sb.append(']');
return sb.toString();
}

public static String formatIntToTwoDigits(int n) {
return n < 10 ? "0".concat(String.valueOf(n)) : String.valueOf(n);
}

private static String formatDate(long date, String format) {
	  if (date == 0) {return "";}
	  SimpleDateFormat temp = new SimpleDateFormat(format);
	  return temp.format(new Date(date));
}

public static String formatDate(long date) {
	return formatDate(date, "dd-MMM-yyyy HH:mm:ss");
}

public static String formatDateShort(long date) {
	  return formatDate(date, "MMM dd, HH:mm");
}

public static String formatDateNum(long date) {
	  return formatDate(date, "yyyy-MM-dd HH:mm:ss");
}

//
// These methods will be exposed in the plugin API.
//

public static String formatCustomDateOnly(long date) {
	  if (date == 0) {return "";}
	  return formatDate(date, "dd-MMM-yyyy");
}

public static String formatCustomTimeOnly(long date) {
	  return formatCustomTimeOnly(date, true);
}
	
public static String formatCustomTimeOnly(long date, boolean with_secs) {
	  if (date == 0) {return "";}
	  return formatDate(date, (with_secs) ? "HH:mm:ss" : "HH:mm");
}

public static String formatCustomDateTime(long date) {
	  if (date == 0) {return "";}
	  return formatDate(date);
}

//
// End methods
//

public static String
formatTime(
long    time )
{
return( TimeFormatter.formatColon( time / 1000 ));
}

*/
	/**
	 * Format a real number to the precision specified.  Does not round the number
	 * or truncate trailing zeros.
	 * 
	 * @param value real number to format
	 * @param precision # of digits after the decimal place
	 * @return formatted string
	 */
	public static String formatDecimal(double value, int precision) {
		return formatDecimal(value, precision, TRUNCZEROS_NO, ROUND_NO);
	}

	/**
	 * Format a real number
	 *
	 * @param value real number to format
	 * @param precision max # of digits after the decimal place
	 * @param bTruncateZeros remove any trailing zeros after decimal place
	 * @param bRound Whether the number will be rounded to the precision, or
	 *                truncated off.
	 * @return formatted string
	 */
	public static String formatDecimal(double value, int precision,
			boolean bTruncateZeros, boolean bRound) {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			return INFINITY_STRING;
		}

		double tValue;
		if (bRound) {
			tValue = value;
		} else {
			// NumberFormat rounds, so truncate at precision
			if (precision == 0) {
				tValue = (long) value;
			} else {
				double shift = Math.pow(10, precision);
				tValue = ((long) (value * shift)) / shift;
			}
		}

		int cache_index = (precision << 2) + ((bTruncateZeros ? 1 : 0) << 1)
				+ (bRound ? 1 : 0);

		NumberFormat nf = null;

		if (cache_index < cached_number_formats.length) {
			nf = cached_number_formats[cache_index];
		}

		if (nf == null) {
			nf = NumberFormat.getNumberInstance();
			nf.setGroupingUsed(false); // no commas
			if (!bTruncateZeros) {
				nf.setMinimumFractionDigits(precision);
			}
			if (bRound) {
				nf.setMaximumFractionDigits(precision);
			}

			if (cache_index < cached_number_formats.length) {
				cached_number_formats[cache_index] = nf;
			}
		}

		return nf.format(tValue);
	}

	/**
	 * Attempts vaguely smart string truncation by searching for largest token and truncating that
	 */

	public static String truncateString(String str, int width) {
		int excess = str.length() - width;

		if (excess <= 0) {

			return (str);
		}

		excess += 3; // for ...

		int token_start = -1;
		int max_len = 0;
		int max_start = 0;

		for (int i = 0; i < str.length(); i++) {

			char c = str.charAt(i);

			if (Character.isLetterOrDigit(c) || c == '-' || c == '~') {

				if (token_start == -1) {

					token_start = i;

				} else {

					int len = i - token_start;

					if (len > max_len) {

						max_len = len;
						max_start = token_start;
					}
				}
			} else {

				token_start = -1;
			}
		}

		if (max_len >= excess) {

			int trim_point = max_start + max_len;

			return (str.substring(0, trim_point - excess) + "..."
					+ str.substring(trim_point));
		} else {

			return (str.substring(0, width - 3) + "...");
		}
	}

/*  	
	// Used to test fractions and displayformatter.
	// Keep until everything works okay.
	public static void main(String[] args) {
		// set decimal display to ","
		//Locale.setDefault(Locale.GERMAN);
		
		double d = 0.000003991630774821635;
		NumberFormat nf =  NumberFormat.getNumberInstance();
		nf.setMaximumFractionDigits(6);
		nf.setMinimumFractionDigits(6);
		String s = nf.format(d);
		
		System.out.println("Actual: " + d);  // Displays 3.991630774821635E-6 
		System.out.println("NF/6:   " + s);  // Displays 0.000004
		// should display 0.000003
			System.out.println("DF:     " + DisplayFormatters.formatDecimal(d , 6));
		// should display 0
			System.out.println("DF 0:   " + DisplayFormatters.formatDecimal(d , 0));
		// should display 0.000000
			System.out.println("0.000000:" + DisplayFormatters.formatDecimal(0 , 6));
		// should display 0.001
			System.out.println("0.001:" + DisplayFormatters.formatDecimal(0.001, 6, TRUNCZEROS_YES, ROUND_NO));
		// should display 0
			System.out.println("0:" + DisplayFormatters.formatDecimal(0 , 0));
		// should display 123456
			System.out.println("123456:" + DisplayFormatters.formatDecimal(123456, 0));
		// should display 123456
			System.out.println("123456:" + DisplayFormatters.formatDecimal(123456.999, 0));
			System.out.println(DisplayFormatters.formatDecimal(0.0/0, 3));
		}
*/
	public static char getDecimalSeparator() {
		return decimalSeparator;
	}

	// XXX should be i18n'd
	static final String[] TIME_SUFFIXES = {
		"s",
		"m",
		"h",
		"d",
		"y"
	};

	static final int[] TIME_RES_SHORT = {
		R.plurals.seconds_short,
		R.plurals.minutes_short,
		R.plurals.hours_short,
		R.plurals.days_short,
		R.plurals.years_short
	};

	static final int[] TIME_RES = {
		R.plurals.seconds,
		R.plurals.minutes,
		R.plurals.hours,
		R.plurals.days,
		R.plurals.years,
		R.plurals.weeks
	};

	public static String prettyFormatTimeDiffShort(Resources res,
			long time_secs) {
		return prettyFormatTimeDiff(res, time_secs, TIME_RES_SHORT, " ", 0);
	}

	public static String prettyFormatTimeDiff(Resources res, long time_secs) {
		return prettyFormatTimeDiff(res, time_secs, TIME_RES, ", ",
				R.string.time_ago);
	}

	/**
	 * Format time into two time sections, the first chunk trimmed, the second
	 * with always with 2 digits.  Sections are *d, **h, **m, **s.  Section
	 * will be skipped if 0.
	 *
	 * @param time_secs time in seconds
	 * @return Formatted time string
	 */
	public static String prettyFormatTimeDiff(Resources res, long time_secs,
			int[] TIME_RES, String sep, int resWrap) {
		if (time_secs < 0)
			return "";

		// secs, mins, hours, days, years
		int[] vals = {
			(int) time_secs % 60,
			(int) (time_secs / 60) % 60,
			(int) (time_secs / 3600) % 24,
			(int) (time_secs / 86400) % 365,
			(int) (time_secs / 31536000)
		};

		int end = vals.length - 1;
		while (vals[end] == 0 && end > 0) {
			end--;
		}

		String result;
		if (end == 3 && TIME_RES.length > 5
				&& (vals[end] >= 28 || vals[end] % 7 == 0)) {
			int weeks = vals[end] / 7;
			int resID = TIME_RES[5];
			result = res.getQuantityString(resID, weeks, weeks);
		} else {
			result = res.getQuantityString(TIME_RES[end], vals[end], vals[end]);
		}

		/* old logic removed to prefer showing consecutive units
		// skip until we have a non-zero time section
		do {
			end--;
		} while (end >= 0 && vals[end] == 0);
		*/

		end--;

		if (end >= 0) {
			if (end == 3 && TIME_RES.length > 5
					&& (vals[end] >= 28 || vals[end] % 7 == 0)) {
				int weeks = vals[end] / 7;
				int resID = TIME_RES[5];
				result += sep + res.getQuantityString(resID, weeks, weeks);
			} else {
				result += sep
						+ res.getQuantityString(TIME_RES[end], vals[end], vals[end]);
			}
		}

		if (resWrap != 0) {
			result = res.getString(resWrap, result);
		}

		return result;
	}

	public static String formatNumber(long n) {
		if (numberFormatInstance == null) {
			numberFormatInstance = NumberFormat.getNumberInstance();
		}
		return numberFormatInstance.format(n);
	}

	public static String countryCodeToEmoji(String code) {

		// offset between uppercase ascii and regional indicator symbols
		int OFFSET = 127397;

		// validate code
		if (code == null || code.length() != 2) {
			return "";
		}

		//fix for uk -> gb
		if (code.equalsIgnoreCase("uk")) {
			code = "gb";
		}

		// convert code to uppercase
		code = code.toUpperCase();

		StringBuilder emojiStr = new StringBuilder();

		//loop all characters
		for (int i = 0; i < code.length(); i++) {
			emojiStr.appendCodePoint(code.charAt(i) + OFFSET);
		}

		// return emoji
		return emojiStr.toString();
	}
}