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

import java.util.HashMap;
import java.util.Map;

import jcifs.netbios.NbtAddress;

/**
 * @author TuxPaper
 * @created Sep 12, 2011
 *
 */
public class NetBiosCache
{
	static private Map<String, String> mapIpToName = new HashMap<String, String>();
	
	public static String getNetBiosName(String ip) {
		synchronized (mapIpToName) {
			String name = mapIpToName.get(ip);
			if (name != null) {
				return name;
			}
			
			try {
  			NbtAddress[] allByAddress = NbtAddress.getAllByAddress(ip);
  			if (allByAddress != null && allByAddress.length > 0) {
  				// could check for code 00
  				name = allByAddress[0].getHostName();
  			}
			} catch (Throwable t) {
				// ignore
			}

			if (name == null) {
				name = "";
			}
			mapIpToName.put(ip, name);
			return name;
		}
	}
}
