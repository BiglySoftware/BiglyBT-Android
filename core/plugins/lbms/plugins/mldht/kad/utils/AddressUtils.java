package lbms.plugins.mldht.kad.utils;

import java.net.*;
import java.util.Collections;
import java.util.LinkedList;

import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.util.NetUtils;

import lbms.plugins.mldht.kad.DHT.DHTtype;
import lbms.plugins.mldht.kad.PeerAddressDBItem;

public class AddressUtils {
	
	public static boolean isBogon(PeerAddressDBItem item)
	{
		return isBogon(item.getInetAddress(), item.getPort());
	}
	
	public static boolean isBogon(InetSocketAddress addr)
	{
		return isBogon(addr.getAddress(),addr.getPort());
	}
	
	public static boolean isBogon(InetAddress addr, int port)
	{
		return !(port > 0 && port <= 0xFFFF && isGlobalUnicast(addr));
	}
	
	public static boolean isGlobalUnicast(InetAddress addr)
	{
		return !(addr.isAnyLocalAddress() || addr.isLinkLocalAddress() || addr.isLoopbackAddress() || addr.isMulticastAddress() || addr.isSiteLocalAddress());
	}

	public static LinkedList<InetAddress> getAvailableAddrs(boolean multiHoming, Class<? extends InetAddress> type) {
		LinkedList<InetAddress> addrs = new LinkedList<InetAddress>();
		
		try
		{
			InetAddress[] allBindAddresses = NetworkAdmin.getSingleton().getAllBindAddresses(true);
			// When no specific interface/address is bound, getAllBindAddresses will 
			// either return 0.0.0.0 or 0:0:0:0:0:0:0:0
			if(allBindAddresses.length == 1 && allBindAddresses[0].isAnyLocalAddress()) {
				if ((allBindAddresses[0] instanceof Inet6Address) && type == Inet4Address.class) {
					allBindAddresses = new InetAddress[] { 
						InetAddress.getByAddress(new byte[] { 0,0,0,0 }) 
					};
				} else if ((allBindAddresses[0] instanceof Inet4Address) && type == Inet6Address.class) {
					allBindAddresses = new InetAddress[] { 
						InetAddress.getByAddress(new byte[] {0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0})
					};
				}
			}
			for (InetAddress inetAddress : allBindAddresses) {
				try {{
						if (type == Inet6Address.class && inetAddress instanceof Inet6Address)
						{
							Inet6Address addr = (Inet6Address) inetAddress;
							// only accept globally reachable IPv6 unicast addresses
							if (addr.isIPv4CompatibleAddress() || !isGlobalUnicast(addr))
								continue;
		
							byte[] raw = addr.getAddress();
							// prefer other addresses over teredo
							if (raw[0] == 0x20 && raw[1] == 0x01 && raw[2] == 0x00 && raw[3] == 0x00)
								addrs.addLast(addr);
							else
								addrs.addFirst(addr);
						}
						
						if(type == Inet4Address.class && inetAddress instanceof Inet4Address)
						{
							Inet4Address addr = (Inet4Address) inetAddress;
	
							// with multihoming we only accept globals
							if(multiHoming && !isGlobalUnicast(addr))
								continue;
							// without multihoming we'll accept site-local addresses too, since they could be NATed
							if(addr.isLinkLocalAddress() || addr.isLoopbackAddress())
								continue;
							
							addrs.add(addr);
						}
					}
				}catch( Throwable e ){
					// getting an NPE in JDK core here for some users (see http://forum.vuze.com/thread.jspa?threadID=100341)
				}
			}
			
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		
		if(!multiHoming)
			addrs.retainAll(Collections.singleton(addrs.peekFirst()));
		
		
		
		return addrs;
	}
	
}
