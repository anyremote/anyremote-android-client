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

package anyremote.client.android.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;

public class IPSocket implements ISocket {

	public static final int PORT_DEFAULT = 5000;
	private final InputStream is;
	private final OutputStream os;
	private final java.net.Socket sock;

	
	public IPSocket(String host, int port) throws UserException {

		try {
			this.sock = new java.net.Socket(host, port);
		} catch (UnknownHostException e) {
			throw new UserException("Connection Error", e.getMessage());
		} catch (SecurityException e) {
			throw new UserException("Connection Error", e.getMessage());
		} catch (IOException e) {
			throw new UserException("Connection Error", e.getMessage());
		}

		try {
			is = sock.getInputStream();
		} catch (IOException e) {
			try {
				sock.close();
			} catch (IOException e1) {
			}
			throw new UserException("Connecting failed",
					"IO Error while opening streams.", e);
		}

		try {
			os = sock.getOutputStream();
		} catch (IOException e) {
			try {
				is.close();
				sock.close();
			} catch (IOException e1) {
			}
			throw new UserException("Connecting failed",
					"IO Error while opening streams.", e);
		}

	}

	//public boolean isConnected() {
	//	return sock.isConnected();
	//}
	
	public void close() {
		try {
			sock.close();
		} catch (IOException e) {
		}
		try {
			os.close();
		} catch (IOException e) {
		}
		try {
			is.close();
		} catch (IOException e) {
		}
	}

	public InputStream getInputStream() {
		return is;
	}

	public OutputStream getOutputStream() {
		return os;
	}

}
