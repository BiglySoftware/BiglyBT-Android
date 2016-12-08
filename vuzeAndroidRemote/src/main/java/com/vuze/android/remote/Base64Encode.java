package com.vuze.android.remote;

import java.util.Arrays;

/**
 * 
 * @version 2.2
 * @author Mikael Grev Date: 2004-aug-02 Time: 11:31:11
 * 
 * Vuze: Only encodeToString and encodeToChar
 */

@SuppressWarnings("ALL")
public class Base64Encode
{

	public static final char[] CA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

	public static final int[] IA = new int[256];
	static {
		Arrays.fill(IA, -1);
		for (int i = 0, iS = CA.length; i < iS; i++)
			IA[CA[i]] = i;
		IA['='] = 0;
	}

	// ****************************************************************************************
	// *  char[] version
	// ****************************************************************************************

	/** Encodes a raw byte array into a BASE64 <code>char[]</code> representation i accordance with RFC 2045.
	 * @param sArr The bytes to convert. If <code>null</code> or length 0 an empty array will be returned.
	 * @param lineSep Optional "\r\n" after 76 characters, unless end of file.<br>
	 * No line separator will be in breach of RFC 2045 which specifies max 76 per line but will be a
	 * little faster.
	 * @return A BASE64 encoded array. Never <code>null</code>.
	 * 
	 * @note Modified to remove lineSep param and add offset and len params
	 */
	public final static char[] encodeToChar(byte[] sArr, int offset, int sLen) {
		if (sLen <= 0 || sArr == null || offset >= sArr.length
				|| offset + sLen > sArr.length) {
			return new char[0];
		}

		int eLen = (sLen / 3) * 3; // Length of even 24-bits.
		int dLen = ((sLen - 1) / 3 + 1) << 2; // Returned character count 
		char[] dArr = new char[dLen];

		// Encode even 24-bits
		for (int s = offset, d = 0; s - offset < eLen;) {
			// Copy next three bytes into lower 24 bits of int, paying attension to sign.
			int i = (sArr[s++] & 0xff) << 16 | (sArr[s++] & 0xff) << 8
					| (sArr[s++] & 0xff);

			// Encode the int into four chars
			dArr[d++] = CA[(i >>> 18) & 0x3f];
			dArr[d++] = CA[(i >>> 12) & 0x3f];
			dArr[d++] = CA[(i >>> 6) & 0x3f];
			dArr[d++] = CA[i & 0x3f];
		}

		// Pad and encode last bits if source isn't even 24 bits.
		int left = sLen - eLen; // 0 - 2.
		if (left > 0) {
			eLen += offset;
			// Prepare the int
			int i = ((sArr[eLen] & 0xff) << 10)
					| (left == 2 ? ((sArr[sLen - 1] & 0xff) << 2) : 0);

			// Set last four chars
			dArr[dLen - 4] = CA[i >> 12];
			dArr[dLen - 3] = CA[(i >>> 6) & 0x3f];
			dArr[dLen - 2] = left == 2 ? CA[i & 0x3f] : '=';
			dArr[dLen - 1] = '=';
		}
		return dArr;
	}

	// ****************************************************************************************
	// * String version
	// ****************************************************************************************

	/** Encodes a raw byte array into a BASE64 <code>String</code> representation i accordance with RFC 2045.
	 * @param sArr The bytes to convert. If <code>null</code> or length 0 an empty array will be returned.
	 * @param lineSep Optional "\r\n" after 76 characters, unless end of file.<br>
	 * No line separator will be in breach of RFC 2045 which specifies max 76 per line but will be a
	 * little faster.
	 * @return A BASE64 encoded array. Never <code>null</code>.
	 */
	public final static String encodeToString(byte[] sArr, int offset, int len) {
		// Reuse char[] since we can't create a String incrementally anyway and StringBuffer/Builder would be slower.
		return new String(encodeToChar(sArr, offset, len));
	}
}
