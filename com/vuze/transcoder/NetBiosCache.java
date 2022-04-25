/**
 * Created on Sep 12, 2011
 *
 * Copyright 2011 Vuze, LLC.  All rights reserved.
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License only.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA 
 */
 
package com.vuze.transcoder;

import java.util.*;

import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.AsyncDispatcher;

import jcifs.netbios.NbtAddress;

/**
 * @author TuxPaper
 * @created Sep 12, 2011
 *
 */
public class NetBiosCache
{
	static private Map<String, String> mapIpToName = new HashMap<String, String>();
	
	static private AsyncDispatcher 	dispatcher = new AsyncDispatcher( "NetBiosCache" );
	
	public static String 
	getNetBiosName(
		String ip) 
	{
		AESemaphore sem = new AESemaphore("NetBiosCache");

		synchronized( mapIpToName ){
			
			String name = mapIpToName.get(ip);
			
			if ( name != null ){
				
				return( name );
			}
			
			if ( dispatcher.getQueueSize() > 32 ){
				
				return( "" );
			}
						
			dispatcher.dispatch(()->{
				String result = "";
				
				try{
						// seen this hang up, hence the async code
					
					NbtAddress[] allByAddress = NbtAddress.getAllByAddress(ip);
					
					if ( allByAddress != null && allByAddress.length > 0 ){
						
							// could check for code 00
						
						result = allByAddress[0].getHostName();
						
						if ( result == null ){
							
							result = "";
						}
					}
				}catch( Throwable t ){
				
				}finally{
					
					synchronized( mapIpToName ){
						
						mapIpToName.put( ip, result );
					}
					
					sem.release();
				}
			});
		}
		
		sem.reserve( 20*1000 );

		synchronized( mapIpToName ){
			
			String name = mapIpToName.get(ip);
			
			if ( name == null ){
				
				name = "";
			}
			
			return( name );
		}
	}
}
