//
// anyRemote android client
// a bluetooth/wi-fi remote control for Linux.
//
// Copyright (C) 2011 Mikhail Fedotov <anyremote@mail.ru>
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA. 
//

package anyremote.client.android;


import java.util.TimerTask;

import anyremote.client.android.Connection;
import anyremote.client.android.util.BTSocket;
import anyremote.client.android.util.IPSocket;
import anyremote.client.android.util.ISocket;
import anyremote.client.android.util.UserException;


public class ConnectTask extends TimerTask {
	public final static int WIFI = 0;
	public final static int BLUETOOTH = 1;

	private int type;
	private String host;
	private Dispatcher connectionListener;


	public ConnectTask(int type, String host, Dispatcher connectionListener) {
		this.type               = type;
		this.host               = host;
		this.connectionListener = connectionListener;
	}

	@Override
	public void run() {

		ISocket s = null;

		if (type == WIFI) {

			// Create a socket. The socket creation should be done in an extra thread 
			// to not block the UI.

			try {

				int split = host.lastIndexOf(":");

				if (split < 8 || // socket://
					split >= host.length() - 2) {
					return;    // format control failed
				}

				String hostname = host.substring(9, split);
				String sport = host.substring(split + 1).trim();

				if (sport.charAt(sport.length() - 1) == '\n') {
					sport = sport.substring(0, sport.length() - 1);
				}
				
				int  port = Integer.valueOf(sport);

				anyRemote._log("ConnectTask", "connectWifi connection is "+hostname+" : " + port);
				s = new IPSocket(hostname, port);

			} catch (UserException e) {

				// tell the view that we have no connection
				anyRemote._log("ConnectTask", "run UserException " + e.getDetails());
				
			    anyRemote.sendGlobal(anyRemote.DISCONNECTED, e.getDetails());

				return;
			}
		}

		if (type == BLUETOOTH) {

			// Create a socket. The socket creation should be done in an extra thread 
			// to not block the UI.

			try {
				s = new BTSocket(host.substring(8)); // strip btspp://
			} catch (UserException e) {
				// tell the view that we have no connection
				anyRemote._log("ConnectTask", "run UserException " + e.getDetails());
			    anyRemote.sendGlobal(anyRemote.DISCONNECTED, e.getDetails());
				return;
			} catch (Exception e) {
				anyRemote._log("ConnectTask", "run Exception " + e.getMessage());
			    anyRemote.sendGlobal(anyRemote.DISCONNECTED, e.getMessage());
				return;
			}
		}

		if (s == null) {
			anyRemote._log("ConnectTask", "NULL socket ");
		    anyRemote.sendGlobal(anyRemote.DISCONNECTED, "can not obtain socket");
			return;
		}

		// Given the socket and the client info, we can set up a connection. A
		// connection cares about exchanging initial messages between client and
		// server. If a connections has been established it provides a Protocol
		// class which can be used to interact with the server. A
		// connection automatically creates it's own thread, so this call
		// returns immediately.
		new Connection(s, connectionListener);
	}

}
