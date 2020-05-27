package org.gudy.bouncycastle.jce.provider;

import java.security.cert.PolicyNode;
import java.util.*;

public class PKIXPolicyNode
    implements PolicyNode
{
	protected List       children;
	protected int        depth;
	protected Set        expectedPolicies;
	protected PolicyNode parent;
	protected Set        policyQualifiers;
	protected String     validPolicy;
	protected boolean    critical;

	/*
	 *
	 *  CONSTRUCTORS
	 *
	 */

	public PKIXPolicyNode(
        List       _children,
        int        _depth,
        Set        _expectedPolicies,
        PolicyNode _parent,
        Set        _policyQualifiers,
        String     _validPolicy,
        boolean    _critical)
    {
		children         = _children;
		depth            = _depth;
		expectedPolicies = _expectedPolicies;
		parent           = _parent;
		policyQualifiers = _policyQualifiers;
		validPolicy      = _validPolicy;
		critical         = _critical;
	}

	public void addChild(
        PKIXPolicyNode _child)
    {
		children.add(_child);
		_child.setParent(this);
	}

	@Override
	public Iterator getChildren()
    {
		return children.iterator();
	}

	@Override
	public int getDepth()
    {
		return depth;
	}

	@Override
	public Set getExpectedPolicies()
    {
		return expectedPolicies;
	}

	@Override
	public PolicyNode getParent()
    {
		return parent;
	}

	@Override
	public Set getPolicyQualifiers()
    {
		return policyQualifiers;
	}

	@Override
	public String getValidPolicy()
    {
		return validPolicy;
	}

	public boolean hasChildren()
    {
		return !children.isEmpty();
	}

	@Override
	public boolean isCritical()
    {
		return critical;
	}

	public void removeChild(PKIXPolicyNode _child)
    {
		children.remove(_child);
	}

	public void setCritical(boolean _critical)
    {
		critical = _critical;
	}

	public void setParent(PKIXPolicyNode _parent)
    {
		parent = _parent;
	}

	public String toString()
    {
		return toString("");
	}

	public String toString(String _indent)
    {
		StringBuilder _buf = new StringBuilder();
		_buf.append(_indent);
		_buf.append(validPolicy);
		_buf.append(" {\n");

		for(int i = 0; i < children.size(); i++) {
			_buf.append(((PKIXPolicyNode)children.get(i)).toString(_indent + "    "));
		}

		_buf.append(_indent);
		_buf.append("}\n");
		return _buf.toString();
	}

	@Override
	public Object clone()
    {
		return copy();
	}

	public PKIXPolicyNode copy()
    {
		HashSet _expectedPolicies = new HashSet();
		Iterator _iter = expectedPolicies.iterator();
		while (_iter.hasNext())
        {
			_expectedPolicies.add(new String((String)_iter.next()));
		}

		HashSet _policyQualifiers = new HashSet();
		_iter = policyQualifiers.iterator();
		while (_iter.hasNext())
        {
			_policyQualifiers.add(new String((String)_iter.next()));
		}

		PKIXPolicyNode _node = new PKIXPolicyNode(new ArrayList(),
		                                          depth,
		                                          _expectedPolicies,
		                                          null,
		                                          _policyQualifiers,
		                                          new String(validPolicy),
		                                          critical);

		_iter = children.iterator();
		while (_iter.hasNext())
        {
			PKIXPolicyNode _child = ((PKIXPolicyNode)_iter.next()).copy();
			_child.setParent(_node);
			_node.addChild(_child);
		}

		return _node;
	}
}
