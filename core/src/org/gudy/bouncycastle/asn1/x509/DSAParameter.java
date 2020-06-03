package org.gudy.bouncycastle.asn1.x509;

import java.math.BigInteger;
import java.util.Enumeration;

import org.gudy.bouncycastle.asn1.*;

public class DSAParameter
    extends ASN1Encodable
{
    DERInteger      p, q, g;

    public static DSAParameter getInstance(
        ASN1TaggedObject obj,
        boolean          explicit)
    {
        return getInstance(ASN1Sequence.getInstance(obj, explicit));
    }

    public static DSAParameter getInstance(
        Object obj)
    {
        if(obj == null || obj instanceof DSAParameter)
        {
            return (DSAParameter)obj;
        }

        if(obj instanceof ASN1Sequence)
        {
            return new DSAParameter((ASN1Sequence)obj);
        }

        throw new IllegalArgumentException("Invalid DSAParameter: " + obj.getClass().getName());
    }

    public DSAParameter(
        BigInteger  p,
        BigInteger  q,
        BigInteger  g)
    {
        this.p = new DERInteger(p);
        this.q = new DERInteger(q);
        this.g = new DERInteger(g);
    }

    public DSAParameter(
        ASN1Sequence  seq)
    {
        if (seq.size() != 3)
        {
            throw new IllegalArgumentException("Bad sequence size: " + seq.size());
        }

        Enumeration     e = seq.getObjects();

        p = DERInteger.getInstance(e.nextElement());
        q = DERInteger.getInstance(e.nextElement());
        g = DERInteger.getInstance(e.nextElement());
    }

    public BigInteger getP()
    {
        return p.getPositiveValue();
    }

    public BigInteger getQ()
    {
        return q.getPositiveValue();
    }

    public BigInteger getG()
    {
        return g.getPositiveValue();
    }

    @Override
    public DERObject toASN1Object()
    {
        ASN1EncodableVector  v = new ASN1EncodableVector();

        v.add(p);
        v.add(q);
        v.add(g);

        return new DERSequence(v);
    }
}
