/*
 * $Id: JSONArray.java,v 1.1 2007-06-05 00:43:56 tuxpaper Exp $
 * Created on 2006-4-10
 */
package org.json.simple;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;


/**
 * @author FangYidong<fangyidong@yahoo.com.cn>
 */
public class JSONArray extends ArrayList {
	public JSONArray() {
		super();
	}

	public JSONArray(Collection arg0) {
		super(arg0);
	}

	public JSONArray(int initialCapacity) {
		super(initialCapacity);
	}

	public String toString(){
		ItemList list=new ItemList();
		
		Iterator iter=iterator();
		
		while(iter.hasNext()){
			Object value=iter.next();				
			if(value instanceof String){
				list.add("\""+JSONObject.escape((String)value)+"\"");
			}
			else
				list.add(String.valueOf(value));
		}
		return "["+list.toString()+"]";
	}
		
}
