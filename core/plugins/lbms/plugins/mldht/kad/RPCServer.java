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

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import lbms.plugins.mldht.kad.DHT.LogLevel;
import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.messages.MessageDecoder;
import lbms.plugins.mldht.kad.messages.PingRequest;
import lbms.plugins.mldht.kad.messages.MessageBase.Type;
import lbms.plugins.mldht.kad.utils.AddressUtils;
import lbms.plugins.mldht.kad.utils.ByteWrapper;
import lbms.plugins.mldht.kad.utils.ResponseTimeoutFilter;
import lbms.plugins.mldht.kad.utils.ThreadLocalUtils;

import com.biglybt.core.util.BDecoder;

/**
 * @author The_8472, Damokles
 *
 */
public class RPCServer implements Runnable, RPCServerBase {
	
	static Map<InetAddress,RPCServer> interfacesInUse = new HashMap<InetAddress, RPCServer>(); 
	
	private DatagramSocket							sock;
	private RPCServerListener						serverListener;
	private DHT										dh_table;
	private ConcurrentMap<ByteWrapper, RPCCallBase>	calls;
	private Queue<RPCCallBase>						call_queue;
	private volatile boolean						running;
	private Thread									thread;
	private int										numReceived;
	private int										numSent;
	private int										port;
	private RPCStats								stats;
	private ResponseTimeoutFilter					timeoutFilter;
	
	private Key										derivedId;

	public RPCServer (DHT dh_table, int port, RPCStats stats, RPCServerListener serverListener ) {
		this.port = port;
		this.dh_table = dh_table;
		this.serverListener = serverListener;
		timeoutFilter = new ResponseTimeoutFilter();
		calls = new ConcurrentHashMap<ByteWrapper, RPCCallBase>(80,0.75f,3);
		call_queue = new ConcurrentLinkedQueue<RPCCallBase>();
		this.stats = stats;
		
		start();
	}
	
	@Override
	public DHT getDHT()
	{
		return dh_table;
	}
	
	@Override
	public boolean 
	isRunning() 
	{
		return( dh_table.isRunning());
	}
	
	private boolean createSocket() 
	{
		if(sock != null){
			sock.close();
		}
		
		synchronized (interfacesInUse)
		{
			interfacesInUse.values().remove(this);
			
			InetAddress addr = null;
			
			try {
				LinkedList<InetAddress> addrs = AddressUtils.getAvailableAddrs(dh_table.getConfig().allowMultiHoming(), dh_table.getType().PREFERRED_ADDRESS_TYPE);
				addrs.removeAll(interfacesInUse.keySet());
				addr = addrs.peekFirst();
				
				timeoutFilter.reset();
				
				if (addr == null){
				
					if ( sock != null ){
						sock.close();
					}
					destroy();
					return( false );
				}
				
				sock = new DatagramSocket(null);
				sock.setReuseAddress(true);
				sock.bind(new InetSocketAddress(addr, port));

				interfacesInUse.put(addr, this);
				return true;
			} catch (Exception e) {
				if(sock != null)
					sock.close();
				destroy();
				return false;
			}
		}

	}
	
	public int getPort() {
		return port;
	}
	
	
	/**
	 * @return external addess, if known (only ipv6 for now)
	 */
	public InetAddress getPublicAddress() {
		if(sock.getLocalAddress() instanceof Inet6Address && !sock.getLocalAddress().isAnyLocalAddress())
			return sock.getLocalAddress();
		return null;
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCServerBase#run()
	 */
	@Override
	public void run() {
		try{
			int delay = 1;
			
			byte[] buffer = new byte[DHTConstants.RECEIVE_BUFFER_SIZE];
			
			while (running)
			{
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				
				try
				{
					if(sock.isClosed())
					{ // don't try to receive on a closed socket, attempt to create a new one instead.
						Thread.sleep(delay * 100);
						if(delay < 256)
							delay <<= 1;
						if(createSocket())
							continue;
						else
							break;
					}
					
					sock.receive(packet);
				} catch (Exception e)
				{
					if (running)
					{
							// see occasional socket closed errors here, no idea why...
						
						if ( 	delay != 1 || 
								e.getMessage() == null ||
								!e.getMessage().toLowerCase().contains( "socket closed" )){
						
							DHT.log(e, LogLevel.Error);
						}
						
						sock.close();
					}
					continue;
				}
				
				try
				{
					handlePacket(packet);
					if(delay > 1)
						delay--;
				} catch (Exception e)
				{
					if (running)
						DHT.log(e, LogLevel.Error);
				}
				
			}
			// we fell out of the loop, make sure everything is cleaned up
			destroy();
			DHT.logInfo("Stopped RPC Server");
		}catch( Throwable e ){
			DHT.log(e, LogLevel.Fatal );
		}
	}
	
	public Key getDerivedID() {
		return derivedId;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see lbms.plugins.mldht.kad.RPCServerBase#start()
	 */
	@Override
	public void start() {
		if(!createSocket())
			return;
		
		running = true;
		
		DHT.logInfo("Starting RPC Server");
		
		// reserve an ID
		derivedId = dh_table.getNode().registerServer(this);
		
		// make ourselves known once everything is ready
		dh_table.addServer(this);
		
		// start thread after registering so the DHT can handle incoming packets properly
		thread = new Thread(this, "mlDHT RPC Thread "+dh_table.getType());
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.setDaemon(true);
		thread.start();
		

	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCServerBase#stop()
	 */
	@Override
	public void destroy () {
		if(running)
			DHT.logInfo("Stopping RPC Server");
		running = false;
		dh_table.removeServer(this);
		Node node = dh_table.getNode();
		if ( node != null ){
			node.removeServer(this);
		}
		synchronized (interfacesInUse)
		{
			interfacesInUse.values().remove(this);
		}
		if(sock != null)
			sock.close();
		thread = null;
		
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCServerBase#doCall(lbms.plugins.mldht.kad.messages.MessageBase)
	 */
	@Override
	public RPCCall doCall (MessageBase msg) {
		
		RPCCall c = new RPCCall(this, msg);
		
		
		while(true)
		{
			
			if(calls.size() >= DHTConstants.MAX_ACTIVE_CALLS)
			{
				DHT.logInfo("Queueing RPC call, no slots available at the moment");				
				call_queue.add(c);
				break;
			}
			short mtid = (short)ThreadLocalUtils.getThreadLocalRandom().nextInt();
			if(calls.putIfAbsent(new ByteWrapper(mtid),c) == null)
			{
				dispatchCall(c, mtid);
				break;
			}
		}


		return c;
	}
	
	private final RPCCallListener rpcListener = new RPCCallListener() {
		
		@Override
		public void onTimeout(RPCCallBase c) {
			ByteWrapper w = new ByteWrapper(c.getRequest().getMTID());
			stats.addTimeoutMessageToCount(c.getRequest());
			calls.remove(w);
			dh_table.timeout(c);
			doQueuedCalls();
		}
		
		@Override
		public void onStall(RPCCallBase c) {}
		@Override
		public void onResponse(RPCCallBase c, MessageBase rsp) {
			serverListener.replyReceived( rsp.getOrigin());
		}
	}; 
	
	private void dispatchCall(RPCCallBase call, short mtid)
	{
		MessageBase msg = call.getRequest();
		msg.setMTID(mtid);
		sendMessage(msg);
		call.addListener(rpcListener);
		timeoutFilter.registerCall(call);
		call.start();
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCServerBase#ping(lbms.plugins.mldht.kad.Key, java.net.InetSocketAddress)
	 */
	@Override
	public void ping (InetSocketAddress addr) {
		PingRequest pr = new PingRequest();
		pr.setID(derivedId);
		pr.setDestination(addr);
		doCall(pr);
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCServerBase#findCall(byte)
	 */
	@Override
	public RPCCallBase findCall (byte[] mtid) {
		return calls.get(new ByteWrapper(mtid));
	}

	/// Get the number of active calls
	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCServerBase#getNumActiveRPCCalls()
	 */
	@Override
	public int getNumActiveRPCCalls () {
		return calls.size();
	}

	/**
	 * @return the numReceived
	 */
	@Override
	public int getNumReceived () {
		return numReceived;
	}

	/**
	 * @return the numSent
	 */
	@Override
	public int getNumSent () {
		return numSent;
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCServerBase#getStats()
	 */
	@Override
	public RPCStats getStats () {
		return stats;
	}
	
	// we only decode in the listening thread, so reused the decoder
	private BDecoder decoder = new BDecoder();

	private void handlePacket (DatagramPacket p) {
		numReceived++;
		stats.addReceivedBytes(p.getLength() + dh_table.getType().HEADER_LENGTH);
		// ignore port 0, can't respond to them anyway and responses to requests from port 0 will be useless too
		if(p.getPort() == 0)
			return;

		if (DHT.isLogLevelEnabled(LogLevel.Verbose)) {
			try {
				DHT.logVerbose(new String(p.getData(), 0, p.getLength(),
						"UTF-8"));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		try {
			Map<String, Object> bedata = decoder.decodeByteArray(p.getData(), 0, p.getLength() , false);
			MessageBase msg = MessageDecoder.parseMessage(bedata, this);
			if (msg != null) {
				if(DHT.isLogLevelEnabled(LogLevel.Debug))
					DHT.logDebug("RPC received message ["+p.getAddress().getHostAddress()+"] "+msg.toString());
				stats.addReceivedMessageToCount(msg);
				msg.setOrigin(new InetSocketAddress(p.getAddress(), p.getPort()));
				msg.setServer(this);
				msg.apply(dh_table);
				// erase an existing call
				if (msg.getType() == Type.RSP_MSG
						&& calls.containsKey(new ByteWrapper(msg.getMTID()))) {
					RPCCallBase c = calls.get(new ByteWrapper(msg.getMTID()));
					if(c.getRequest().getDestination().equals(msg.getOrigin()))
					{
						// delete the call, but first notify it of the response
						c.response(msg);
						calls.remove(new ByteWrapper(msg.getMTID()));
						doQueuedCalls();						
					} else
						DHT.logInfo("Response source ("+msg.getOrigin()+") mismatches request destination ("+c.getRequest().getDestination()+"); ignoring response");
				}
			} else
			{
				try {
					DHT.logDebug("RPC received message [" + p.getAddress().getHostAddress() + "] Decode failed msg was:"+new String(p.getData(), 0, p.getLength(),"UTF-8"));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

				
		} catch (IOException e) {
			DHT.log(e, LogLevel.Debug);
		}
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCServerBase#sendMessage(lbms.plugins.mldht.kad.messages.MessageBase)
	 */
	@Override
	public void sendMessage (MessageBase msg) {
		try {
			if(msg.getID() == null)
				msg.setID(getDerivedID());
			stats.addSentMessageToCount(msg);
			send(msg.getDestination(), msg.encode());
			if(DHT.isLogLevelEnabled(LogLevel.Debug))
				DHT.logDebug("RPC send Message: [" + msg.getDestination().getAddress().getHostAddress() + "] "+ msg.toString());
		} catch (IOException e) {
			System.out.print(sock.getLocalAddress()+" -> "+msg.getDestination()+" ");
			e.printStackTrace();
		}
	}
	
	@Override
	public ResponseTimeoutFilter getTimeoutFilter() {
		return timeoutFilter;
	}

	private void send (InetSocketAddress addr, byte[] msg) throws IOException {
		if (!sock.isClosed()) {
			DatagramPacket p = new DatagramPacket(msg, msg.length);
			p.setSocketAddress(addr);
			try
			{
				sock.send(p);
			} catch (BindException e)
			{
				if(NetworkInterface.getByInetAddress(sock.getLocalAddress()) == null)
				{
					createSocket();
					sock.send(p);
				} else
				{
					throw e;
				}
			}
			stats.addSentBytes(msg.length + dh_table.getType().HEADER_LENGTH);
			numSent++;
		}
	}

	private void doQueuedCalls () {
		while (call_queue.peek() != null && calls.size() < DHTConstants.MAX_ACTIVE_CALLS) {
			RPCCallBase c;

			if((c = call_queue.poll()) == null)
				return;

			short mtid = 0;
			do
			{
				mtid = (short)ThreadLocalUtils.getThreadLocalRandom().nextInt();
			} while (calls.putIfAbsent(new ByteWrapper(mtid), c) != null);

			dispatchCall(c, mtid);
		}
	}

	public void closeSocket() {
		if (sock != null && !sock.isClosed()) {
			sock.close();
		}
	}
}
