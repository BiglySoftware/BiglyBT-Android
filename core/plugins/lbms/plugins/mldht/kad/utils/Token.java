/*
 *    This file is part of mlDHT. 
 * 
 *    mlDHT is free software: you can redistribute it and/or modify 
 *    it under the terms of the GNU General Public License as published by 
 *    the Free Software Foundation, either version 2 of the License, or 
 *    (at your option) any later version. 
 * 
 *    mlDHT is distributed in the hope that it will be useful, 
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of 
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 *    GNU General Public License for more details. 
 * 
 *    You should have received a copy of the GNU General Public License 
 *    along with mlDHT.  If not, see <http://www.gnu.org/licenses/>. 
 */
package lbms.plugins.mldht.kad.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.util.BEncoder;

public abstract class 
Token 
{
	public abstract Object
	getValue();
	
	public static class
	OurToken
		extends Token
	{
		private final byte[]	value;
		private final int		hash;
		
		public 
		OurToken(
			byte[] _value)
		{
			value 	= _value;			
			hash 	= Arrays.hashCode((byte[])value );
		}
		
		@Override
		public Object
		getValue()
		{
			return( value );
		}
		
		@Override
		public int hashCode() {
			return hash;
		}
		
		@Override
		public boolean 
		equals(Object obj) 
		{
			if ( obj instanceof OurToken ){
				
				byte[] other_value = ((OurToken)obj).value;
				
				return( Arrays.equals(value, other_value ));

			}else{
				
				return( false );
			}	
		}
	}
	
	public static class
	TheirToken
		extends Token
	{
		private final Object	value;
		private final int		hash;
		
		public 
		TheirToken(
			Object _value)
		{
			value = _value;
			
			if ( value instanceof byte[] ){
			
				hash = Arrays.hashCode((byte[])value );
				
			}else{
				
				Map temp = new HashMap();
				
				temp.put( "v", value );
				
				int _hash;
				
				try{
					_hash = Arrays.hashCode( BEncoder.encode( temp ));
					
				}catch( Throwable e ){
					
					_hash = 0;
				}
				
				hash = _hash;
			}
		}
		
		@Override
		public Object
		getValue()
		{
			return( value );
		}
		
		@Override
		public int hashCode() {
			return hash;
		}
		
		@Override
		public boolean 
		equals(Object obj) 
		{
			if ( obj instanceof TheirToken ){
				
				Object other_value = ((TheirToken)obj).value;
				
				if ( value instanceof byte[] && other_value instanceof byte[]){
					
					return( Arrays.equals( (byte[])value, (byte[])other_value ));
					
				}else{
					
					return( BEncoder.objectsAreIdentical( value, other_value ));
				}
			}else{
				
				return( false );
			}		
		}
	}
}