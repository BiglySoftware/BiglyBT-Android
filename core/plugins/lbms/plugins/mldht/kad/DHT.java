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

import java.io.File;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.biglybt.core.util.SystemTime;

import lbms.plugins.mldht.DHTConfiguration;
import lbms.plugins.mldht.kad.Node.RoutingTableEntry;
import lbms.plugins.mldht.kad.messages.*;
import lbms.plugins.mldht.kad.messages.ErrorMessage.ErrorCode;
import lbms.plugins.mldht.kad.tasks.*;
import lbms.plugins.mldht.kad.utils.AddressUtils;
import lbms.plugins.mldht.kad.utils.PopulationEstimator;
import lbms.plugins.mldht.kad.utils.ThreadLocalUtils;
import lbms.plugins.mldht.kad.utils.Token;

/**
 * @author Damokles
 * 
 */
public class DHT implements DHTBase {
	
	public static enum DHTtype {
		IPV4_DHT("IPv4",20+4+2, 4+2, Inet4Address.class,20+8),
		IPV6_DHT("IPv6",20+16+2, 16+2, Inet6Address.class,40+8);
		
		public final int							HEADER_LENGTH;
		public final int 							NODES_ENTRY_LENGTH;
		public final int							ADDRESS_ENTRY_LENGTH;
		public final Class<? extends InetAddress>	PREFERRED_ADDRESS_TYPE;
		public final String 						shortName;
		private DHTtype(String shortName, int nodeslength, int addresslength, Class<? extends InetAddress> addresstype, int header) {
			this.shortName = shortName;
			this.NODES_ENTRY_LENGTH = nodeslength;
			this.PREFERRED_ADDRESS_TYPE = addresstype;
			this.ADDRESS_ENTRY_LENGTH = addresslength;
			this.HEADER_LENGTH = header;
		}

	}


	private static DHTLogger				logger;
	private static LogLevel					logLevel	= LogLevel.Info;

	private static ScheduledThreadPoolExecutor	scheduler;
	private static ThreadGroup					executorGroup;
	
	static {
		executorGroup = new ThreadGroup("mlDHT");
		initStatics();
	}

	public static void
	initStatics()
	{
		createScheduler();
		createLogger();
	}
	
	private static void
	createScheduler()
	{
		int threads = Math.max(Runtime.getRuntime().availableProcessors(),2);
		scheduler = new ScheduledThreadPoolExecutor(threads, new ThreadFactory() {
			@Override
			public Thread newThread (Runnable r) {
				Thread t = new Thread(executorGroup, r, "mlDHT Executor");

				t.setDaemon(true);
				return t;
			}
		});
		scheduler.setCorePoolSize(threads);
		scheduler.setMaximumPoolSize(threads*2);
		scheduler.setKeepAliveTime(20, TimeUnit.SECONDS);
		scheduler.allowCoreThreadTimeOut(true);
	}
	
	private static void
	createLogger()
	{
		logger = new DHTLogger() {
			@Override
			public void log (String message) {
				System.out.println(message);
			};
			@Override
			public void log (Throwable e) {
				e.printStackTrace();
			}
		};
	}
	
	private boolean							running;
	private boolean							stopped;
	
	private long							server_create_counter;
	private long							last_rpc_create;

	private boolean							bootstrapping;
	private long							lastBootstrap;

	private DHTConfiguration				config;
	private Node							node;
	private List<RPCServer>					servers = new CopyOnWriteArrayList<RPCServer>();
	private Database						db;
	private TaskManager						tman;
	private File							table_file;
	private boolean							useRouterBootstrapping;

	private List<DHTStatsListener>			statsListeners;
	private List<DHTStatusListener>			statusListeners;
	private List<DHTIndexingListener>		indexingListeners;
	private DHTStats						stats;
	private DHTStatus						status;
	private PopulationEstimator				estimator;
	private AnnounceNodeCache				cache;
	
	private RPCStats						serverStats;

	private final DHTtype					type;
	private List<ScheduledFuture<?>>		scheduledActions = new ArrayList<ScheduledFuture<?>>();
	
	
	static Map<DHTtype,DHT> dhts; 


	public synchronized static Map<DHTtype, DHT> createDHTs() {
		if(dhts == null)
		{
			dhts = new EnumMap<DHTtype,DHT>(DHTtype.class);
			
			dhts.put(DHTtype.IPV4_DHT, new DHT(DHTtype.IPV4_DHT));
			dhts.put(DHTtype.IPV6_DHT, new DHT(DHTtype.IPV6_DHT));
		}
		
		return dhts;
	}
	
	public static DHT getDHT(DHTtype type)
	{
		return dhts.get(type);
	}

	private DHT(DHTtype type) {
		this.type = type;
		
		stats = new DHTStats();
		status = DHTStatus.Stopped;
		statsListeners = new ArrayList<DHTStatsListener>(2);
		statusListeners = new ArrayList<DHTStatusListener>(2);
		indexingListeners = new ArrayList<DHTIndexingListener>();
		estimator = new PopulationEstimator();
	}
	
	@Override
	public void ping (PingRequest r) {
		if (!isRunning()) {
			return;
		}

		// ignore requests we get from ourself
		if (node.allLocalIDs().contains(r.getID())) {
			return;
		}

		PingResponse rsp = new PingResponse(r.getMTID());
		rsp.setDestination(r.getOrigin());
		r.getServer().sendMessage(rsp);
		node.recieved(this, r);
	}

	@Override
	public void findNode (FindNodeRequest r) {
		if (!isRunning()) {
			return;
		}

		// ignore requests we get from ourself
		if (node.allLocalIDs().contains(r.getID())) {
			return;
		}

		node.recieved(this, r);
		// find the K closest nodes and pack them

		KClosestNodesSearch kns4 = null; 
		KClosestNodesSearch kns6 = null;
		
		// add our local address of the respective DHT for cross-seeding, but not for local requests
		if(r.doesWant4()) {
			kns4 = new KClosestNodesSearch(r.getTarget(), DHTConstants.MAX_ENTRIES_PER_BUCKET, getDHT(DHTtype.IPV4_DHT));
			kns4.fill(DHTtype.IPV4_DHT != type);
		}
		if(r.doesWant6()) {
			kns6 = new KClosestNodesSearch(r.getTarget(), DHTConstants.MAX_ENTRIES_PER_BUCKET, getDHT(DHTtype.IPV6_DHT));
			kns6.fill(DHTtype.IPV6_DHT != type);
		}


		FindNodeResponse fnr = new FindNodeResponse(r.getMTID(), kns4 != null ? kns4.pack() : null,kns6 != null ? kns6.pack() : null);
		fnr.setOrigin(r.getOrigin());
		r.getServer().sendMessage(fnr);
	}

	@Override
	public void response (MessageBase r) {
		if (!isRunning()) {
			return;
		}

		node.recieved(this, r);
	}

	@Override
	public void getPeers (GetPeersRequest r) {
		if (!isRunning()) {
			return;
		}

		// ignore requests we get from ourself
		if (node.allLocalIDs().contains(r.getID())) {
			return;
		}

		node.recieved(this, r);
		
		List<DBItem> dbl = db.sample(r.getInfoHash(), 50,type, r.isNoSeeds());
		for(DHTIndexingListener listener : indexingListeners)
		{
			List<PeerAddressDBItem> toAdd = listener.incomingPeersRequest(r.getInfoHash(), r.getOrigin().getAddress(), r.getID());
			if(dbl == null && !toAdd.isEmpty())
				dbl = new ArrayList<DBItem>();
			if(dbl != null && !toAdd.isEmpty())
				dbl.addAll(toAdd);
		}
			

		// generate a token
		Token token = db.genToken(r.getOrigin().getAddress(), r
				.getOrigin().getPort(), r.getInfoHash());

		KClosestNodesSearch kns4 = null; 
		KClosestNodesSearch kns6 = null;
		
		// add our local address of the respective DHT for cross-seeding, but not for local requests
		if(r.doesWant4()) {
			kns4 = new KClosestNodesSearch(r.getTarget(), DHTConstants.MAX_ENTRIES_PER_BUCKET, getDHT(DHTtype.IPV4_DHT));
			kns4.fill(DHTtype.IPV4_DHT != type);
		}
		if(r.doesWant6()) {
			kns6 = new KClosestNodesSearch(r.getTarget(), DHTConstants.MAX_ENTRIES_PER_BUCKET, getDHT(DHTtype.IPV6_DHT));
			kns6.fill(DHTtype.IPV6_DHT != type);
		}

		
		GetPeersResponse resp = new GetPeersResponse(r.getMTID(), 
			kns4 != null ? kns4.pack() : null,
			kns6 != null ? kns6.pack() : null,
			db.insertForKeyAllowed(r.getInfoHash()) ? token : null);
		
		if(r.isScrape())
		{
			resp.setScrapePeers(db.createScrapeFilter(r.getInfoHash(), false));
			resp.setScrapeSeeds(db.createScrapeFilter(r.getInfoHash(), true));			
		}

		
		resp.setPeerItems(dbl);
		resp.setDestination(r.getOrigin());
		r.getServer().sendMessage(resp);
	}

	@Override
	public void announce (AnnounceRequest r) {
		if (!isRunning()) {
			return;
		}

		// ignore requests we get from ourself
		if (node.allLocalIDs().contains(r.getID())) {
			return;
		}

		node.recieved(this, r);
		// first check if the token is OK
		Token token = r.getToken();
		if (!db.checkToken(token, r.getOrigin().getAddress(), r
				.getOrigin().getPort(), r.getInfoHash())) {
			logDebug("DHT Received Announce Request with invalid token.");
			sendError(r, ErrorCode.ProtocolError.code, "Invalid Token");
			return;
		}

		logDebug("DHT Received Announce Request, adding peer to db: "
				+ r.getOrigin().getAddress());

		// everything OK, so store the value
		PeerAddressDBItem item = PeerAddressDBItem.createFromAddress(r.getOrigin().getAddress(), r.getPort(), r.isSeed());
		if(!AddressUtils.isBogon(item))
			db.store(r.getInfoHash(), item);

		// send a proper response to indicate everything is OK
		AnnounceResponse rsp = new AnnounceResponse(r.getMTID());
		rsp.setOrigin(r.getOrigin());
		r.getServer().sendMessage(rsp);
	}

	@Override
	public void error (ErrorMessage r) {
		DHT.logError("Error [" + r.getCode() + "] from: " + r.getOrigin()
				+ " Message: \"" + r.getMessage() + "\"");
	}

	@Override
	public void timeout (RPCCallBase r) {
		if (isRunning()) {
			node.onTimeout(r);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see lbms.plugins.mldht.kad.DHTBase#addDHTNode(java.lang.String, int)
	 */
	@Override
	public void addDHTNode (String host, int hport) {
		if (!isRunning()) {
			return;
		}
		InetSocketAddress addr = new InetSocketAddress(host, hport);

		if (!addr.isUnresolved() && !AddressUtils.isBogon(addr)) {
			if(!type.PREFERRED_ADDRESS_TYPE.isInstance(addr.getAddress()) || node.getNumEntriesInRoutingTable() > DHTConstants.BOOTSTRAP_IF_LESS_THAN_X_PEERS)
				return;
			RPCServer server = getRandomServer();
			
			if ( server != null ){
				server.ping(addr);
			}
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see lbms.plugins.mldht.kad.DHTBase#announce(byte[], int)
	 */
	@Override
	public PeerLookupTask createPeerLookup (byte[] info_hash) {
		if (!isRunning()) {
			return null;
		}
		RPCServer server = getRandomServer();
		if ( server == null ){
			return( null );
		}
		
		Key id = new Key(info_hash);

		PeerLookupTask lookupTask = new PeerLookupTask(server, node, id);

		return lookupTask;
	}
	
	@Override
	public AnnounceTask announce(PeerLookupTask lookup, boolean isSeed, int btPort) {
		if (!isRunning()) {
			return null;
		}
		
		// reuse the same server to make sure our tokens are still valid
		AnnounceTask announce = new AnnounceTask(lookup.getRPC(), node, lookup.getInfoHash(), btPort);
		announce.setSeed(isSeed);
		for (KBucketEntryAndToken kbe : lookup.getAnnounceCanidates())
		{
			announce.addToTodo(kbe);
		}

		tman.addTask(announce);

		return announce;
	}
	
	

	@Override
	public PingRefreshTask refreshBuckets (List<RoutingTableEntry> buckets,
	                                       boolean cleanOnTimeout) {
		RPCServer server = getRandomServer();
		if ( server == null ){
			return( null );
		}
		PingRefreshTask prt = new PingRefreshTask(server, node, buckets,
				cleanOnTimeout);

		tman.addTask(prt, true);
		return prt;
	}
	
	public DHTConfiguration getConfig() {
		return config;
	}
	
	public AnnounceNodeCache getCache() {
		return cache;
	}
	
	public List<RPCServer> getServers() {
		return servers;
	}
	
	public RPCServer getRandomServer() {
		RPCServer srv = null;
		
		if ( servers != null ){
			while(true)
			{
				int s = servers.size();
				if(s < 1)
					break;
				try
				{
					srv = servers.get(ThreadLocalUtils.getThreadLocalRandom().nextInt(s));
					break;
				} catch (IndexOutOfBoundsException e)
				{
					// ignore and retry as array was concurrently modified
				}
			}
		}
		
		return srv;
	}
	
	void addServer(RPCServer toAdd)
	{
		servers.add(toAdd);
	}
	
	void removeServer(RPCServer toRemove)
	{
		servers.remove(toRemove);
	}

	public PopulationEstimator getEstimator() {
		return estimator;
	}

	public DHTtype getType() {
		return type;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see lbms.plugins.mldht.kad.DHTBase#getStats()
	 */
	@Override
	public DHTStats getStats () {
		return stats;
	}

	/**
	 * @return the status
	 */
	public DHTStatus getStatus () {
		return status;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see lbms.plugins.mldht.kad.DHTBase#isRunning()
	 */
	@Override
	public boolean isRunning () {
		return running && !stopped && servers.size() > 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see lbms.plugins.mldht.kad.DHTBase#portRecieved(java.lang.String, int)
	 */
	@Override
	public void portRecieved (String ip, int port) {
		if (!isRunning()) {
			return;
		}
		RPCServer server = getRandomServer();
		if ( server == null ){
			return;
		}
		PingRequest r = new PingRequest();
		r.setOrigin(new InetSocketAddress(ip, port));
		server.doCall(r);

	}
	
	
	private int getPort() {
		int port = config.getListeningPort();
		if(port < 1 || port > 65535)
			port = 49001;
		return port;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see lbms.plugins.mldht.kad.DHTBase#start(java.lang.String, int)
	 */
	@Override
	public void start (DHTConfiguration config, final RPCServerListener serverListener)
			throws SocketException {
		if (running || stopped) {
			return;
		}

		this.config = config;
		useRouterBootstrapping = !config.noRouterBootstrap();

		setStatus(DHTStatus.Initializing);
		stats.resetStartedTimestamp();

		table_file = config.getNodeCachePath();
		Node.initDataStore(config);

		logInfo("Starting DHT on port " + getPort());
		resolveBootstrapAddresses();
		
		serverStats = new RPCStats();

		
		cache = new AnnounceNodeCache();
		stats.setRpcStats(serverStats);
		node = new Node(this);
		db = new Database();
		stats.setDbStats(db.getStats());
		tman = new TaskManager();
		running = true;
		
		scheduledActions.add(scheduler.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				try{
					// maintenance that should run all the time, before the first queries
					tman.removeFinishedTasks(DHT.this);
	
					if (running && hasStatsListeners())
						onStatsUpdate();
				}catch( Throwable e ){
					log(e, LogLevel.Fatal);
				}
			}	
		}, 5000, DHTConstants.DHT_UPDATE_INTERVAL, TimeUnit.MILLISECONDS));

		// initialize as many RPC servers as we need 
		for(int i = 0;i< AddressUtils.getAvailableAddrs(config.allowMultiHoming(), type.PREFERRED_ADDRESS_TYPE).size();i++)
			new RPCServer(this, getPort(),serverStats, serverListener);
		
		
		bootstrapping = true;
		node.loadTable(new Runnable() {
			@Override
			public void run () {
				started(serverListener);				
			}
		});


		
//		// does 10k random lookups and prints them to a file for analysis
//		scheduler.schedule(new Runnable() {
//			//PrintWriter		pw;
//			TaskListener	li	= new TaskListener() {
//									public synchronized void finished(Task t) {
//										NodeLookup nl = ((NodeLookup) t);
//										if (nl.closestSet.size() < DHTConstants.MAX_ENTRIES_PER_BUCKET)
//											return;
//										/*
//										StringBuilder b = new StringBuilder();
//										b.append(nl.targetKey.toString(false));
//										b.append(",");
//										for (Key i : nl.closestSet)
//											b.append(i.toString(false).substring(0, 12) + ",");
//										b.deleteCharAt(b.length() - 1);
//										pw.println(b);
//										pw.flush();
//										*/
//									}
//								};
//
//			public void run() {
//				if(type == DHTtype.IPV6_DHT)
//					return;
//				/*
//				try
//				{
//					pw = new PrintWriter("H:\\mldht.log");
//				} catch (FileNotFoundException e)
//				{
//					e.printStackTrace();
//				}*/
//				for (int i = 0; i < 10000; i++)
//				{
//					NodeLookup l = new NodeLookup(Key.createRandomKey(), srv, node, false);
//					if (canStartTask())
//						l.start();
//					tman.addTask(l);
//					l.addListener(li);
//					if (i == (10000 - 1))
//						l.addListener(new TaskListener() {
//							public void finished(Task t) {
//								System.out.println("10k lookups done");
//							}
//						});
//				}
//			}
//		}, 1, TimeUnit.MINUTES);
		

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see lbms.plugins.mldht.kad.DHTBase#started()
	 */
	protected void started ( final RPCServerListener serverListener) {
		if ( stopped ){
			return;
		}
		
		bootstrapping = false;
		bootstrap();
		
		/*
		if(type == DHTtype.IPV6_DHT)
		{
			Task t = new KeyspaceCrawler(srv, node);
			tman.addTask(t);
		}*/
			
		
		
		scheduledActions.add(scheduler.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run () {
				try {
					update( serverListener );
				} catch (Throwable e) {
					log(e, LogLevel.Fatal);
				}
			}
		}, 5000, DHTConstants.DHT_UPDATE_INTERVAL, TimeUnit.MILLISECONDS));
		
		scheduledActions.add(scheduler.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				try
				{
					long now = System.currentTimeMillis();


					db.expire(now);
					cache.cleanup(now);					
				} catch (Throwable e)
				{
					log(e, LogLevel.Fatal);
				}

			}
		}, 1000, DHTConstants.CHECK_FOR_EXPIRED_ENTRIES, TimeUnit.MILLISECONDS));
		
		scheduledActions.add(scheduler.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run () {
				try {
					for(RPCServer srv : servers)
						findNode(Key.createRandomKey(), false, false, true, srv).setInfo("Random Refresh Lookup");
				} catch (Throwable e) {
					log(e, LogLevel.Fatal);
				}
				
				try {
					if(!node.isInSurvivalMode())
						node.saveTable(table_file,false);
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
		}, DHTConstants.RANDOM_LOOKUP_INTERVAL, DHTConstants.RANDOM_LOOKUP_INTERVAL, TimeUnit.MILLISECONDS));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see lbms.plugins.mldht.kad.DHTBase#stop()
	 */
	@Override
	public void stop () {
		if (!running) {
			return;
		}
		stopped();
		
		logInfo("Stopping DHT");
		for (Task t : tman.getActiveTasks()) {
			t.kill();
		}
		
		for(ScheduledFuture<?> future : scheduledActions)
			future.cancel(false);
		scheduler.getQueue().removeAll(scheduledActions);
		scheduler.shutdownNow();

		scheduledActions.clear();

		for(RPCServer s : servers)
			s.destroy();
		try {
			if ( node != null ){
				node.saveTable(table_file,true);
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
		running = false;
		
		tman = null;
		db = null;
		node = null;
		cache = null;
		setStatus(DHTStatus.Stopped);
		
		dhts = null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see lbms.plugins.mldht.kad.DHTBase#getNode()
	 */
	@Override
	public Node getNode () {
		return node;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see lbms.plugins.mldht.kad.DHTBase#getTaskManager()
	 */
	@Override
	public TaskManager getTaskManager () {
		return tman;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see lbms.plugins.mldht.kad.DHTBase#stopped()
	 */
	public void stopped () {
		
		stopped = true;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see lbms.plugins.mldht.kad.DHTBase#update()
	 */
	protected void update ( final RPCServerListener serverListener ) {
		long mono_now = SystemTime.getMonotonousTime();
		
		if ( running ){
			
			if (config.allowMultiHoming() || servers.size() < 1) {
				
					// rate limit creation of new servers when things are obviously not working (in particular ipv6 dht with no ipv6 avail)
				
				int delay = 1000;
				
				for ( int i=0;i<server_create_counter;i++){
					delay = delay*2;
					if ( delay > 60*1000 ){
						delay = 60*1000;
						break;
					}
				}
				
				if ( last_rpc_create == 0 || mono_now - last_rpc_create >= delay ){
					
					last_rpc_create = mono_now;
					
					new RPCServer(this, getPort(),serverStats,serverListener);
					
					server_create_counter++;
					
				}else{
					
					return;
				}
			}
		}
		
		if (!isRunning()) {
			return;
		}
				
		server_create_counter = 0;
		
		long now = System.currentTimeMillis();

		node.doBucketChecks(now);

		if (!bootstrapping) {
			if (node.getNumEntriesInRoutingTable() < DHTConstants.BOOTSTRAP_IF_LESS_THAN_X_PEERS) {
				bootstrap();
			} else if (now - lastBootstrap > DHTConstants.SELF_LOOKUP_INTERVAL) {
				//update stale entries
				PingRefreshTask prt = new PingRefreshTask(getRandomServer(), node, false);
				prt.setInfo("Refreshing old entries.");
				if (canStartTask(prt)) {
					prt.start();
				}
				tman.addTask(prt, true);

				//regualary search for our id to update routing table
				bootstrap();
			} else {
				setStatus(DHTStatus.Running);
			}
		}

		
	}
	
		// getting complaints from owner of IPv6 bootstrap node that they're getting a large spike in DNS
		// traffic if their servers encounter issues....
		// can't understand it myself (parg) as the code appears to limit resolves to once per 4 mins and
		// only then for poorly integrated nodes, and there ain't that many mldht plugins out there
	
	private static final Map<String,Object[]>	bn_resolver_history = new HashMap<String, Object[]>();
	
	private void resolveBootstrapAddresses() {
		List<InetSocketAddress> nodeAddresses =  new ArrayList<InetSocketAddress>();
		for(int i = 0;i<DHTConstants.BOOTSTRAP_NODES.length;i++)
		{
			String 	hostname 	= DHTConstants.BOOTSTRAP_NODES[i];
			int 	port 		= DHTConstants.BOOTSTRAP_PORTS[i];

			String		cache_key = hostname + ":" + port;
			Object[]	cache;
			
			synchronized( bn_resolver_history ){
				
				cache = bn_resolver_history.get( cache_key );
			}
			
			long	now = SystemTime.getMonotonousTime();
			
			if ( cache != null ){
				
				long last_lookup 	= (Long)cache[0];
				long consec_fails 	= (Long)cache[1]; 
				
				InetAddress[] last_result = (InetAddress[])cache[2];
				
				if ( consec_fails > 0 ){
					
					long	next_lookup = last_lookup + Math.min( 2*60*60*1000, 5*60*1000L << ( consec_fails -1 ));
					
					if ( next_lookup > now ){
						
						if ( last_result != null ){
							
							for(InetAddress addr : last_result ){
								
								nodeAddresses.add(new InetSocketAddress(addr, port));
							}
						}
						
						continue;
					}
				}
			}else{
				
				cache = new Object[]{ now, 0L, null };
			}
			
			try{ 
				InetAddress[] result = InetAddress.getAllByName(hostname);
				
				for ( InetAddress addr : result ){
				
					nodeAddresses.add(new InetSocketAddress(addr, port));
				}
				
				cache[0]	= now;
				cache[1]	= 0L;
				cache[2] 	= result;
				
			} catch ( Throwable e) {
			
				cache[1] = ((Long)cache[1]) + 1;
			}
			
			synchronized( bn_resolver_history ){
				
				bn_resolver_history.put( cache_key, cache );
			}
		}
		
		if(nodeAddresses.size() > 0)
			DHTConstants.BOOTSTRAP_NODE_ADDRESSES = nodeAddresses;
	}

	/**
	 * Initiates a Bootstrap.
	 * 
	 * This function bootstraps with router.bittorrent.com if there are less
	 * than 10 Peers in the routing table. If there are more then a lookup on
	 * our own ID is initiated. If the either Task is finished than it will try
	 * to fill the Buckets.
	 */
	public synchronized void bootstrap () {
		if (!isRunning() || bootstrapping || System.currentTimeMillis() - lastBootstrap < DHTConstants.BOOTSTRAP_MIN_INTERVAL) {
			return;
		}
		
		if (useRouterBootstrapping || node.getNumEntriesInRoutingTable() > 1) {
			
			final AtomicInteger finishCount = new AtomicInteger();
			bootstrapping = true;
			
			TaskListener bootstrapListener = new TaskListener() {
				/*
				 * (non-Javadoc)
				 * 
				 * @see lbms.plugins.mldht.kad.TaskListener#finished(lbms.plugins.mldht.kad.Task)
				 */
				@Override
				public void finished (Task t) {
					int count = finishCount.decrementAndGet();
					bootstrapping = false;
					if (count == 0 && running && node.getNumEntriesInRoutingTable() > DHTConstants.USE_BT_ROUTER_IF_LESS_THAN_X_PEERS) {
						node.fillBuckets(DHT.this);
					}
				}
			};

			logInfo("Bootstrapping...");
			lastBootstrap = System.currentTimeMillis();

			for(RPCServer srv : servers)
			{
				finishCount.incrementAndGet();
				NodeLookup nl = findNode(srv.getDerivedID(), true, true, true,srv);
				if (nl == null) {
					bootstrapping = false;
					break;
				} else if (node.getNumEntriesInRoutingTable() < DHTConstants.USE_BT_ROUTER_IF_LESS_THAN_X_PEERS) {
					if (useRouterBootstrapping) {
						resolveBootstrapAddresses();
						List<InetSocketAddress> addrs = new ArrayList<InetSocketAddress>(DHTConstants.BOOTSTRAP_NODE_ADDRESSES);
						Collections.shuffle(addrs);
						
						for (InetSocketAddress addr : addrs)
						{
							if (!type.PREFERRED_ADDRESS_TYPE.isInstance(addr.getAddress()))
								continue;
							nl.addDHTNode(addr.getAddress(),addr.getPort());
							break;
						}
					}
					nl.addListener(bootstrapListener);
					nl.setInfo("Bootstrap: Find Peers.");

					tman.removeFinishedTasks(this);

				} else {
					nl.setInfo("Bootstrap: search for ourself.");
					nl.addListener(bootstrapListener);
					tman.removeFinishedTasks(this);
				}
				
			}
		}
	}

	private NodeLookup findNode (Key id, boolean isBootstrap,
			boolean isPriority, boolean queue, RPCServer server) {
		if (!running) {
			return null;
		}

		NodeLookup at = new NodeLookup(id, server, node, isBootstrap);
		if (!queue && canStartTask(at)) {
			at.start();
		}
		tman.addTask(at, isPriority);
		return at;
	}

	/**
	 * Do a NodeLookup.
	 * 
	 * @param id The id of the key to search
	 */
	@Override
	public NodeLookup findNode (Key id) {
		RPCServer server = getRandomServer();
		if ( server == null ){
			return( null );
		}
		return findNode(id, false, false, true, server);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see lbms.plugins.mldht.kad.DHTBase#fillBucket(lbms.plugins.mldht.kad.KBucket)
	 */
	@Override
	public NodeLookup fillBucket (Key id, KBucket bucket) {
		RPCServer server = getRandomServer();
		if ( server == null ){
			return( null );
		}
		bucket.updateRefreshTimer();
		return findNode(id, false, true, true, server);
	}

	@Override
	public PingRefreshTask refreshBucket (KBucket bucket) {
		if (!isRunning()) {
			return null;
		}
		RPCServer server = getRandomServer();
		if ( server == null ){
			return( null );
		}
		PingRefreshTask prt = new PingRefreshTask(server, node, bucket, false);
		if (canStartTask(prt)) {
			prt.start();
		}
		tman.addTask(prt); // low priority, the bootstrap does a high prio one if necessary

		return prt;
	}

	public void sendError (MessageBase origMsg, int code, String msg) {
		sendError(origMsg.getOrigin(), origMsg.getMTID(), code, msg, origMsg.getServer());
	}

	public void sendError (InetSocketAddress target, byte[] mtid, int code,
			String msg, RPCServer srv) {
		ErrorMessage errMsg = new ErrorMessage(mtid, code, msg);
		errMsg.setDestination(target);
		srv.sendMessage(errMsg);
	}

	@Override
	public boolean canStartTask (Task toCheck) {
		// we can start a task if we have less then  7 runnning and
		// there are at least 16 RPC slots available
		return tman.getNumTasks() < DHTConstants.MAX_ACTIVE_TASKS * servers.size() && toCheck.getRPC().getNumActiveRPCCalls() + 16 < DHTConstants.MAX_ACTIVE_CALLS;
	}

	@Override
	public Key getOurID () {
		if (running) {
			return node.getRootID();
		}
		return null;
	}

	private boolean hasStatsListeners () {
		return !statsListeners.isEmpty();
	}

	private void onStatsUpdate () {
		stats.setNumTasks(tman.getNumTasks() + tman.getNumQueuedTasks());
		stats.setNumPeers(node.getNumEntriesInRoutingTable());
		int numSent = 0;int numReceived = 0;int activeCalls = 0;
		for(RPCServer s : servers)
		{
			numSent += s.getNumSent();
			numReceived += s.getNumReceived();
			activeCalls += s.getNumActiveRPCCalls();
		}
		stats.setNumSentPackets(numSent);
		stats.setNumReceivedPackets(numReceived);
		stats.setNumRpcCalls(activeCalls);

		for (int i = 0; i < statsListeners.size(); i++) {
			statsListeners.get(i).statsUpdated(stats);
		}
	}

	private void setStatus (DHTStatus status) {
		if (!this.status.equals(status)) {
			DHTStatus old = this.status;
			this.status = status;
			if (!statusListeners.isEmpty())
			{
				for (int i = 0; i < statusListeners.size(); i++)
				{
					statusListeners.get(i).statusChanged(status, old);
				}
			}
		}
	}

	@Override
	public void addStatsListener (DHTStatsListener listener) {
		statsListeners.add(listener);
	}

	@Override
	public void removeStatsListener (DHTStatsListener listener) {
		statsListeners.remove(listener);
	}

	public void addIndexingLinstener(DHTIndexingListener listener) {
		indexingListeners.add(listener);
	}

	public void addStatusListener (DHTStatusListener listener) {
		statusListeners.add(listener);
	}

	public void removeStatusListener (DHTStatusListener listener) {
		statusListeners.remove(listener);
	}

	/**
	 * @return the logger
	 */
	//	public static DHTLogger getLogger () {
	//		return logger;
	//	}
	/**
	 * @param logger the logger to set
	 */
	public static void setLogger (DHTLogger logger) {
		DHT.logger = logger;
	}

	/**
	 * @return the logLevel
	 */
	public static LogLevel getLogLevel () {
		return logLevel;
	}

	/**
	 * @param logLevel the logLevel to set
	 */
	public static void setLogLevel (LogLevel logLevel) {
		DHT.logLevel = logLevel;
		logger.log("Change LogLevel to: " + logLevel);
	}

	/**
	 * @return the scheduler
	 */
	public static ScheduledExecutorService getScheduler () {
		return scheduler;
	}

	public static void log (String message, LogLevel level) {
		if (level.compareTo(logLevel) < 1) { // <=
			logger.log(message);
		}
	}

	public static void log (Throwable e, LogLevel level) {
		if (level.compareTo(logLevel) < 1) { // <=
			logger.log(e);
		}
	}

	public static void logFatal (String message) {
		log(message, LogLevel.Fatal);
	}

	public static void logError (String message) {
		log(message, LogLevel.Error);
	}

	public static void logInfo (String message) {
		log(message, LogLevel.Info);
	}

	public static void logDebug (String message) {
		log(message, LogLevel.Debug);
	}

	public static void logVerbose (String message) {
		log(message, LogLevel.Verbose);
	}

	public static boolean isLogLevelEnabled (LogLevel level) {
		return level.compareTo(logLevel) < 1;
	}

	public static enum LogLevel {
		Fatal, Error, Info, Debug, Verbose
	}
}
