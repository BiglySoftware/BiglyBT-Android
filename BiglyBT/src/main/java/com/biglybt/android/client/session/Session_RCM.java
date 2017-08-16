/*
 * Copyright (c) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.android.client.session;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.android.client.TransmissionVars;
import com.biglybt.android.client.rpc.ReplyMapReceivedListener;
import com.biglybt.android.client.rpc.TransmissionRPC;
import com.biglybt.android.util.MapUtils;

import android.util.Log;

/**
 * RCM/Swarm Discovery methods for a {@link Session}
 *
 * Created by TuxPaper on 12/13/16.
 */

public class Session_RCM
{
	private final Session session;

	public interface RcmCheckListener
	{
		void rcmCheckEnabled(boolean enabled);

		void rcmCheckEnabledError(Exception e, String message);
	}

	public interface RcmGetListListener
	{
		void rcmListReceived(long until, List listRCM);

		void rcmListReceivedError(Exception e, String message);
	}

	Session_RCM(Session session) {
		this.session = session;
	}

	public void checkEnabled(final RcmCheckListener l) {
		session._executeRpc(new Session.RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.simpleRpcCall(TransmissionVars.METHOD_RCM_IS_ENABLED,
						new ReplyMapReceivedListener() {
							@Override
							public void rpcSuccess(String id, Map<?, ?> optionalMap) {
								if (l == null) {
									return;
								}
								boolean enabled = false;
								if (optionalMap != null && optionalMap.containsKey(
										TransmissionVars.FIELD_RCM_UI_ENABLED)) {
									enabled = MapUtils.getMapBoolean(optionalMap,
											TransmissionVars.FIELD_RCM_UI_ENABLED, false);
								}
								l.rcmCheckEnabled(enabled);
							}

							@Override
							public void rpcError(String id, Exception e) {
								if (l != null) {
									l.rcmCheckEnabledError(e, null);
								}
							}

							@Override
							public void rpcFailure(String id, String message) {
								if (l != null) {
									l.rcmCheckEnabledError(null, message);
								}
							}
						});
			}
		});
	}

	public void getList(long rcmGotUntil, final RcmGetListListener l) {
		final Map<String, Object> map = new HashMap<>();
		if (rcmGotUntil > 0) {
			map.put("since", rcmGotUntil);
		}
		session._executeRpc(new Session.RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.simpleRpcCall("rcm-get-list", map, new ReplyMapReceivedListener() {
					@Override
					public void rpcSuccess(String id, Map<?, ?> optionalMap) {
						try {
							Log.d("RCM", "rcm-get-list: " + optionalMap);
						} catch (Throwable ignored) {
						}
						if (l == null) {
							return;
						}
						long until = MapUtils.getMapLong(optionalMap, "until", 0);
						List related = MapUtils.getMapList(optionalMap, "related", null);
						l.rcmListReceived(until, related);
					}

					@Override
					public void rpcError(String id, Exception e) {
						if (l == null) {
							return;
						}
						l.rcmListReceivedError(e, null);
					}

					@Override
					public void rpcFailure(String id, String message) {
						if (l == null) {
							return;
						}

						l.rcmListReceivedError(null, message);
					}
				});
			}
		});
	}

	public void setEnabled(final boolean enable, final boolean all,
			final ReplyMapReceivedListener replyMapReceivedListener) {
		session._executeRpc(new Session.RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				Map<String, Object> map = new HashMap<>(2, 1.0f);
				map.put("enable", enable);
				if (enable) {
					map.put("all-sources", all);
				}
				rpc.simpleRpcCall(TransmissionVars.METHOD_RCM_SET_ENABLED, map,
						replyMapReceivedListener);
			}
		});
	}
}
