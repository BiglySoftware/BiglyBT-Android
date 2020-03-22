package org.gudy.bouncycastle.crypto;

/**
 * the foundation class for the hard exceptions thrown by the crypto packages.
 */
public class CryptoException
    extends Exception
{

    /**
     * base constructor.
     */
    public CryptoException()
    {
    }

    /**
     * create a ClientCryptoException with the given message.
     *
     * @param message the message to be carried with the exception.
     */
    public CryptoException(
        String  message)
    {
        super(message);
    }
}
