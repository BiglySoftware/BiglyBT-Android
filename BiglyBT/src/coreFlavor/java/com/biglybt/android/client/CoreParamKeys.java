/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.android.client;

/**
 * Created by TuxPaper on 12/15/17.
 */

public class CoreParamKeys
{
	public static final String BPARAM_PROXY_ENABLE_TRACKERS = "Enable.Proxy";

	/**
	 * false- http proxy;  true - SOCKS proxy.
	 */
	public static final String BPARAM_PROXY_ENABLE_SOCKS = "Enable.SOCKS";

	public static final String SPARAM_PROXY_HOST = "Proxy.Host";

	public static final String SPARAM_PROXY_PORT = "Proxy.Port";

	public static final String SPARAM_PROXY_USER = "Proxy.Username";

	public static final String SPARAM_PROXY_PW = "Proxy.Password";

	public static final String BPARAM_PROXY_TRACKER_DNS_DISABLE = "Proxy.SOCKS.Tracker.DNS.Disable";

	/** Only Outgoing. Must be SOCKS */
	public static final String BPARAM_PROXY_DATA_ENABLE = "Proxy.Data.Enable";

	/** When BPARAM_PROXY_DATA_ENABLE, hide ports from tracker announces **/
	public static final String BPARAM_PROXY_DATA_INFORM = "Proxy.Data.SOCKS.inform";
	
	public static final String SPARAM_PROXY_DATA_SOCKS_VER = "Proxy.Data.SOCKS.version";
	
	/** Same proxy settings (host/port/name/pw) as BPARAM_PROXY_ENABLE_TRACKERS */
	public static final String BPARAM_PROXY_DATA_SAME = "Proxy.Data.Same";

	public static final String SPARAM_XMWEBUI_BIND_IP = "Plugin.xmwebui.Bind IP";

	public static final String SPARAM_XMWEBUI_PW_DISABLED_WHITELIST = "Plugin.xmwebui.Password Disabled Whitelist";

	public static final String BPARAM_XMWEBUI_UPNP_ENABLE = "Plugin.xmwebui.UPnP Enable";

	public static final String BPARAM_XMWEBUI_PW_ENABLE = "Plugin.xmwebui.Password Enable";

	public static final String SPARAM_XMWEBUI_PW = "Plugin.xmwebui.Password";

	public static final String SPARAM_XMWEBUI_USER = "Plugin.xmwebui.User";

	public static final String BPARAM_XMWEBUI_PAIRING_AUTO_AUTH = "Plugin.xmwebui.Pairing Auto Auth";

	public static final String BPARAM_ENFORCE_BIND_IP = "Enforce Bind IP";

	public static final String BPARAM_CHECK_BIND_IP_ONSTART = "Check Bind IP On Start";

	public static final String BPARAM_BIND_IP = "Bind IP";
}
