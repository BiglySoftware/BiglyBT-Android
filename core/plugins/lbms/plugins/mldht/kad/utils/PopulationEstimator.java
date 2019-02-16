package lbms.plugins.mldht.kad.utils;

import java.io.PrintStream;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.*;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.Prefix;
import lbms.plugins.mldht.kad.DHT.LogLevel;
import lbms.plugins.mldht.kad.Key.DistanceOrder;

/**
 * @author The_8472, Damokles
 *
 */
public class PopulationEstimator {

	static final int						KEYSPACE_BITS					= Key.KEY_BITS;
	static final double						KEYSPACE_SIZE					= Math.pow(2, KEYSPACE_BITS);
	
	// try to close in to a rough estimate fast
	static final double						DISTANCE_INITIAL_WEIGHT			= 0.1;
	static final int						INITIAL_WEIGHT_COUNT			= 20;
	// apply less weight to individual estimates later on
	static final double						DISTANCE_WEIGHT					= 0.03;

	
	private double						averageNodeDistanceExp2			= KEYSPACE_BITS;
	private int							updateCount						= 0;
	private List<PopulationListener>	listeners						= new ArrayList<PopulationListener>(1);

	private static final int			MAX_RECENT_LOOKUP_CACHE_SIZE	= 40;
	private Deque<Prefix>					recentlySeenPrefixes			= new LinkedList<Prefix>();

	public long getEstimate () {
		/* corrective term chosen based on simulations for updates with the 8 closest nodes
		 * it is accurate within +- 1% for sets of the 8+-3 closest nodes
		 */
		return (long) (Math.pow(2, KEYSPACE_BITS - averageNodeDistanceExp2 + 0.6180339));
	}
	
	public double getRawDistanceEstimate() {
		return averageNodeDistanceExp2;
	}

	public void setInitialRawDistanceEstimate(double initialValue) {
		averageNodeDistanceExp2 = initialValue;
		if(averageNodeDistanceExp2 > KEYSPACE_BITS)
			averageNodeDistanceExp2 = KEYSPACE_BITS;
	}

	public void update (SortedSet<Key> neighbors) {
		// need at least 3 distance values (thus 4 IDs) to calculate the median
		if(neighbors.size() < 4)
			return;
		
		double[] distances = new double[neighbors.size() - 1];
		
		DHT.log("Estimator: new node group of "+neighbors.size(), LogLevel.Debug);
		Prefix prefix = Prefix.getCommonPrefix(neighbors);
		
		synchronized (recentlySeenPrefixes)
		{
			for(Prefix oldPrefix : recentlySeenPrefixes)
			{
				if(oldPrefix.isPrefixOf(prefix))
				{
					/*
					 * displace old entry, narrower entries will also replace
					 * wider ones, to clean out accidents like prefixes covering
					 * huge fractions of the keyspace
					 */
					recentlySeenPrefixes.remove(oldPrefix);
					recentlySeenPrefixes.addLast(prefix);
					return;
				}
				// new prefix is wider than the old one, return but do not displace
				if(prefix.isPrefixOf(oldPrefix))
					return;
			}

			// no match found => add
			recentlySeenPrefixes.addLast(prefix);
			if(recentlySeenPrefixes.size() > MAX_RECENT_LOOKUP_CACHE_SIZE)
				recentlySeenPrefixes.removeFirst();
		}
		
		Key previous = null;
		int i = 0;
		for (Key entry : neighbors) {
			if (previous == null) {
				previous = entry;
				continue;
			}

			byte[] rawDistance = previous.distance(entry).getHash();

			double distance = 0;
			
			int nonZeroBytes = 0;
			for (int j = 0; j < Key.SHA1_HASH_LENGTH; j++) {
				if (rawDistance[j] == 0) {
					continue;
				}
				if (nonZeroBytes == 8) {
					break;
				}
				nonZeroBytes++;
				distance += (rawDistance[j] & 0xFF)
						* Math.pow(2, KEYSPACE_BITS - (j + 1) * 8);
			}
			
			//distance = new BigInteger(rawDistance).doubleValue();

			/*
			 * weighted average of the exponents (since single results can be
			 * off by several orders of magnitude -> logarithm dampens that
			 * exponentially)
			 */
			
			distance = Math.log(distance) /Math.log(2);

			DHT.log("Estimator: distance value #"+updateCount+": " + distance + " avg:" + averageNodeDistanceExp2, LogLevel.Debug);
			
			distances[i++] = distance;

			previous = entry;
		}
		
		double weight;
		
		Arrays.sort(distances);

		weight = updateCount < INITIAL_WEIGHT_COUNT ? DISTANCE_INITIAL_WEIGHT : DISTANCE_WEIGHT;
		updateCount++;
		
		// use a weighted 2-element median for max. accuracy 
		double middle = (distances.length - 1.0) / 2.0 ;
		int idx1 = (int) Math.floor(middle);
		int idx2 = (int) Math.ceil(middle);
		double middleWeight = middle - idx1;
		double median = distances[idx1] * (1.0 - middleWeight) + distances[idx2] * middleWeight;
		
		synchronized (PopulationEstimator.class)
		{
			// exponential average of the mean value
			averageNodeDistanceExp2 = median * weight + averageNodeDistanceExp2 * (1. - weight);
		}
		
		DHT.log("Estimator: new estimate:"+getEstimate(), LogLevel.Info);
		
		fireUpdateEvent();
	}

	public void addListener (PopulationListener l) {
		listeners.add(l);
	}

	public void removeListener (PopulationListener l) {
		listeners.remove(l);
	}

	private void fireUpdateEvent () {
		long estimated = getEstimate();
		for (int i = 0; i < listeners.size(); i++) {
			listeners.get(i).populationUpdated(estimated);
		}
	}
	

	
	public static void main(String[] args) throws Exception {
		
		PrintStream out = new PrintStream("dump.txt");
		
		Random rand = new Random();
		NumberFormat formatter = NumberFormat.getNumberInstance(Locale.GERMANY);
		formatter.setMaximumFractionDigits(30);
		
		PopulationEstimator estimator = new PopulationEstimator();
		
		List<Key> keyspace = new ArrayList<Key>(5000000);
		for(int i = 0;i< 5000;i++)
			keyspace.add(Key.createRandomKey());
		Collections.sort(keyspace);
		
		for(int i=0;i<1000;i++)
		{
			for(int j=0;j<3;j++)
			{
				Key target = Key.createRandomKey();
				int idx = Math.min(keyspace.size() - 1, Math.abs(Collections.binarySearch(keyspace, target)));
				TreeSet<Key> closestSet = new TreeSet<Key>(new Key.DistanceOrder(target));

				int sizeGoal = 5 + rand.nextInt(4);
				
				/*
				for (int k = 0; k < sizeGoal*4; k++)
				{
					if (idx - k >= 0)
						closestSet.add(keyspace.get(idx - k));
					if (idx + k < keyspace.size())
						closestSet.add(keyspace.get(idx + k));
				}*/

				//int sizeGoal = (int) (6 + Math.pow(rand.nextDouble(), 3) * 20);
				//int sizeGoal = keyspace.size() / 1000;
				
				closestSet.addAll(keyspace);
				
				while (closestSet.size() > sizeGoal)
					closestSet.remove(closestSet.last());
				
				
				//estimator.update(closestSet);
				double[] distances = new double[closestSet.size() - 1];
				
				Key previous = null;
				int k = 0;
				for (Key entry : closestSet) {
					if (previous == null) {
						previous = entry;
						continue;
					}

					byte[] rawDistance = previous.distance(entry).getHash();

					double distance = 0;
					
					distance = new BigInteger(rawDistance).doubleValue();

					distance = Math.log(distance) /Math.log(2);

					distances[k++] = distance;

					previous = entry;
				}
				
				Arrays.sort(distances);
				
				// use a weighted 2-element median for max. accuracy 
				double middle = (distances.length - 1.0) / 2.0 ;
				int idx1 = (int) Math.floor(middle);
				int idx2 = (int) Math.ceil(middle);
				double middleWeight = middle - idx1;
				double median = distances[idx1] * (1.0 - middleWeight) + distances[idx2] * middleWeight;
				
				out.println(
					distances.length+"\t"+
					keyspace.size()+"\t"+
					formatter.format(median)
				);

		
				
//				List<Double> distances = new ArrayList<Double>(closestSet.size());
//				Key prev = null;
//				for (Key k : closestSet)
//				{
//					/*
//					if (prev == null)
//					{
//						prev = k;
//						continue;
//					}*/
//					distances.add(Math.log(new BigInteger(k.distance(target).getHash()).doubleValue()) / Math.log(2));
//					prev = k;
//				}
//				
//				Collections.sort(distances);			
//				
//				int pick = rand.nextInt(distances.size());
//				
//				//for(pick=0;pick<distances.size();pick++)
//					out.println(
//						distances.size()+"\t"+
//						pick+"\t"+
//						formatter.format(distances.get(pick))+"\t"+
//						formatter.format(1.0 - Math.abs((1.0*pick+1)/ (1.0+distances.size()) - 0.5) * 2)+"\t"+
//						keyspace.size()+"\t"+
//						formatter.format(160-Math.log(keyspace.size())/Math.log(2))
//					);
//				out.println();
				
			}
			
			
			int newGoal = (int) (keyspace.size() * 1.008);
			System.out.println(i+": "+newGoal);
			while(keyspace.size() < newGoal)
				keyspace.add(Key.createRandomKey());
			Collections.sort(keyspace);
		}
		
	}
}
