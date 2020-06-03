package org.gudy.bouncycastle.asn1.x509;

import java.util.Enumeration;

import org.gudy.bouncycastle.asn1.*;

/**
 * <pre>
 *    PrivateKeyUsagePeriod ::= SEQUENCE {
 *      notBefore       [0]     GeneralizedTime OPTIONAL,
 *      notAfter        [1]     GeneralizedTime OPTIONAL }
 * </pre>
 */
public class PrivateKeyUsagePeriod
    extends ASN1Encodable
{
    public static PrivateKeyUsagePeriod getInstance(Object obj)
    {
        if (obj instanceof PrivateKeyUsagePeriod)
        {
            return (PrivateKeyUsagePeriod)obj;
        }

        if (obj instanceof ASN1Sequence)
        {
            return new PrivateKeyUsagePeriod((ASN1Sequence)obj);
        }

        if (obj instanceof X509Extension)
        {
            return getInstance(X509Extension.convertValueToObject((X509Extension)obj));
        }

        throw new IllegalArgumentException("unknown object in getInstance");
    }

    private DERGeneralizedTime _notBefore, _notAfter;

    private PrivateKeyUsagePeriod(ASN1Sequence seq)
    {
        Enumeration en = seq.getObjects();
        while (en.hasMoreElements())
        {
            ASN1TaggedObject tObj = (ASN1TaggedObject)en.nextElement();

            if (tObj.getTagNo() == 0)
            {
                _notBefore = DERGeneralizedTime.getInstance(tObj, false);
            }
            else if (tObj.getTagNo() == 1)
            {
                _notAfter = DERGeneralizedTime.getInstance(tObj, false);
            }
        }
    }

    public DERGeneralizedTime getNotBefore()
    {
        return _notBefore;
    }

    public DERGeneralizedTime getNotAfter()
    {
        return _notAfter;
    }

    @Override
    public DERObject toASN1Object()
    {
        ASN1EncodableVector v = new ASN1EncodableVector();

        if (_notBefore != null)
        {
            v.add(new DERTaggedObject(false, 0, _notBefore));
        }
        if (_notAfter != null)
        {
            v.add(new DERTaggedObject(false, 1, _notAfter));
        }

        return new DERSequence(v);
    }
}
