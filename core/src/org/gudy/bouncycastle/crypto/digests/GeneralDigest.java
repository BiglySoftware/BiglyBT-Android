package org.gudy.bouncycastle.crypto.digests;

import org.gudy.bouncycastle.crypto.Digest;

/**
 * base implementation of MD4 family style digest as outlined in
 * "Handbook of Applied Cryptography", pages 344 - 347.
 */
public abstract class GeneralDigest
    implements Digest
{
    private byte[]  xBuf;
    private int     xBufOff;

    private long    byteCount;

	/**
	 * Standard constructor
	 */
	protected GeneralDigest()
	{
		xBuf = new byte[4];
		xBufOff = 0;
	}

	/**
	 * Copy constructor.  We are using copy constructors in place
	 * of the Object.clone() interface as this interface is not
	 * supported by J2ME.
	 */
	protected GeneralDigest(GeneralDigest t)
	{
        xBuf = new byte[t.xBuf.length];
		System.arraycopy(t.xBuf, 0, xBuf, 0, t.xBuf.length);

		xBufOff = t.xBufOff;
		byteCount = t.byteCount;
	}

    @Override
    public void update(
        byte in)
    {
        xBuf[xBufOff++] = in;

        if (xBufOff == xBuf.length)
        {
            processWord(xBuf, 0);
            xBufOff = 0;
        }

        byteCount++;
    }

    @Override
    public void update(
        byte[]  in,
        int     inOff,
        int     len)
    {
        //
        // fill the current word
        //
        while ((xBufOff != 0) && (len > 0))
        {
            update(in[inOff]);

            inOff++;
            len--;
        }

        //
        // process whole words.
        //
        while (len > xBuf.length)
        {
            processWord(in, inOff);

            inOff += xBuf.length;
            len -= xBuf.length;
            byteCount += xBuf.length;
        }

        //
        // load in the remainder.
        //
        while (len > 0)
        {
            update(in[inOff]);

            inOff++;
            len--;
        }
    }

    public void finish()
    {
        long    bitLength = (byteCount << 3);

        //
        // add the pad bytes.
        //
        update((byte)128);

        while (xBufOff != 0)
        {
            update((byte)0);
        }

        processLength(bitLength);

        processBlock();
    }

    @Override
    public void reset()
    {
        byteCount = 0;

        xBufOff = 0;
		for ( int i = 0; i < xBuf.length; i++ ) {
			xBuf[i] = 0;
		}
    }

    protected abstract void processWord(byte[] in, int inOff);

    protected abstract void processLength(long bitLength);

    protected abstract void processBlock();
}
