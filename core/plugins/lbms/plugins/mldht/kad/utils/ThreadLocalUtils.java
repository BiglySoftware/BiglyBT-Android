package lbms.plugins.mldht.kad.utils;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;

import com.biglybt.core.util.SHA1;

public class ThreadLocalUtils {

	public static ThreadLocal<Random> randTL = new ThreadLocal<Random>();
	public static ThreadLocal<SHA1> sha1TL = new ThreadLocal<SHA1>();

	public static Random getThreadLocalRandom() {
		Random rand = randTL.get();
		if(rand == null)
		{
			try
			{
				rand = SecureRandom.getInstance("SHA1PRNG");
			} catch (NoSuchAlgorithmException e)
			{
				rand = new SecureRandom();
			}
			randTL.set(rand);
		}
		return rand;
	}
	
	public static SHA1 getThreadLocalSHA1() {
		SHA1 hash = sha1TL.get();
		if(hash == null)
		{
			hash = new SHA1();
			sha1TL.set(hash);
		}
		return hash;
	}
	
}
