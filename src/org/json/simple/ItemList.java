/*
 * $Id: ItemList.java,v 1.2 2009-03-15 22:12:18 parg Exp $
 * Created on 2006-3-24
 */
package org.json.simple;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * 管理用分隔符分开的一组item.分隔符两边一定是一个item.每个item两边不能是空白符.
 * 例如：
 * |a:b:c| => |a|,|b|,|c|
 * |:| => ||,||
 * |a:| => |a|,||
 * @author FangYidong<fangyidong@yahoo.com.cn>
 */
public class ItemList {
	private String sp=",";
	List<String> items=new ArrayList<String>();
	
	
	public ItemList(){}
	
	/**
	 * 
	 * @param s 分隔符隔开的一个字符串序列
	 */
	public ItemList(String s){
		this.split(s,sp,items);
	}
	/**
	 * 
	 * @param s 分隔符隔开的一个字符串序列
	 * @param sp 分隔符
	 */
	public ItemList(String s,String sp){
		this.sp=s;
		this.split(s,sp,items);
	}
	
	/**
	 * 
	 * @param s
	 * @param sp
	 * @param isMultiToken sp是否为多分隔符
	 */
	public ItemList(String s,String sp,boolean isMultiToken){
		split(s,sp,items,isMultiToken);
	}
	
	public List<String> getItems(){
		return this.items;
	}
	
	public String[] getArray(){
		return (String[])this.items.toArray(new String[items.size()]);
	}
	
	public void split(String s,String sp,List<String> append,boolean isMultiToken){
		if(s==null || sp==null)
			return;
		if(isMultiToken){
			StringTokenizer tokens=new StringTokenizer(s,sp);
			while(tokens.hasMoreTokens()){
				append.add(tokens.nextToken().trim());
			}
		}
		else{
			this.split(s,sp,append);
		}
	}
	
	public void split(String s,String sp,List<String> append){
		if(s==null || sp==null)
			return;
		int pos=0;
		int prevPos=0;
		do{
			prevPos=pos;
			pos=s.indexOf(sp,pos);
			if(pos==-1)
				break;
			append.add(s.substring(prevPos,pos).trim());
			pos+=sp.length();
		}while(pos!=-1);
		append.add(s.substring(prevPos).trim());
	}
	
	/**
	 * 设置分隔符.
	 * @param sp 分隔符
	 */
	public void setSP(String sp){
		this.sp=sp;
	}
	
	/**
	 * 加入单个item.
	 * @param i 加入的位置(之前)
	 * @param item
	 */
	public void add(int i,String item){
		if(item==null)
			return;
		items.add(i,item.trim());
	}
	/**
	 * 加入单个item.
	 * @param item
	 */
	public void add(String item){
		if(item==null)
			return;
		items.add(item.trim());
	}
	
	/**
	 * 加一组item.
	 * @param list 另外的list
	 */
	public void addAll(ItemList list){
		items.addAll(list.items);
	}
	
	/**
	 * 加一组item.
	 * @param s 分隔符隔开的一个字符串序列
	 */
	public void addAll(String s){
		this.split(s,sp,items);
	}
	
	/**
	 * 加一组item.
	 * @param s 分隔符隔开的一个字符串序列
	 * @param sp 分隔符
	 */
	public void addAll(String s,String sp){
		this.split(s,sp,items);
	}
	
	public void addAll(String s,String sp,boolean isMultiToken){
		this.split(s,sp,items,isMultiToken);
	}
	
	/**
	 * 获得第i个item. 0-based.
	 * @param i
	 * @return
	 */
	public String get(int i){
		return (String)items.get(i);
	}
	
	/**
	 * 获得item数.
	 * @return
	 */
	public int size(){
		return items.size();
	}
	/**
	 * 用分隔符分隔的表示.
	 */
	public String toString(){
		return toString(sp);
	}
	
	/**
	 * 用分隔符分隔的表示.
	 * @param sp 结果用该分隔符分隔.
	 * @return
	 */
	public String toString(String sp){
		StringBuffer sb=new StringBuffer();
		
		for(int i=0;i<items.size();i++){
			if(i==0)
				sb.append(items.get(i));
			else{
				sb.append(sp);
				sb.append(items.get(i));
			}
		}
		return sb.toString();

	}
	
	/**
	 * 清空所有item.
	 */
	public void clear(){
		items.clear();
	}
	
	/**
	 * 复位.清空数据，并恢复所有默认值.
	 */
	public void reset(){
		sp=",";
		items.clear();
	}
}
