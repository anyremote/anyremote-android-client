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

import java.io.InputStream;
import java.io.OutputStream;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
//import android.os.Looper;
import anyremote.client.android.anyRemote;
import anyremote.client.android.util.ISocket;
import anyremote.client.android.util.UserException;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.UUID;


public class BTSocket implements ISocket {

	private InputStream is;
	private OutputStream os;
	private BluetoothSocket sock;

	private boolean isClosed = false;

	// service UUID
	private final UUID USE_UUID = UUID.fromString(
			//"00001101-0000-0000-0000-00000000ABCD");
			"00001101-0000-1000-8000-00805F9B34FB"); // SPP
			//"40BA0016-474D-4071-8359-F4B94FA1CAD7");
	/**
	 * Create a new Bluetooth client socket for the given host.
	 * 
	 * @param host
	 *            device mac bluetooth address
	 * @throws UserException
	 *             if setting up the socket and connection fails
	 */
	public BTSocket(String host) throws UserException {

        anyRemote._log("BTSocket",host);
        
		//try {
        //    Looper.prepare();
		//} catch (RuntimeException e) {}
	     
		anyRemote._log("BTSocket","start");
		int attempts = 0;
		
		while (true) {
		  try {
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            anyRemote._log("BTSocket","got BluetoothAdapter");
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(host);
            anyRemote._log("BTSocket","got BluetoothDevice");
			this.sock = device.createRfcommSocketToServiceRecord(USE_UUID);
			
			//Method m = device.getClass().getMethod("createRfcommSocket", new Class[] {int.class});
			//this.sock = (BluetoothSocket) m.invoke(device, 1);

            anyRemote._log("BTSocket","got createRfcommSocketToServiceRecord");
            this.sock.connect();
            anyRemote._log("BTSocket","connected");
            isClosed = false;
		  } catch (SecurityException e) {
			anyRemote._log("BTSocket","SecurityException "+e);
			throw new UserException("Connection Error",
					"Not allowed to connect to " + host);
		  } catch (IOException e) {
			anyRemote._log("BTSocket","IOException "+e);
			if (attempts > 10) {
			    throw new UserException("Connection Error",
					"IO error while setting up the connection to " + host);
			}
			attempts++;
		  } catch (Exception e) {
			anyRemote._log("BTSocket","Exception "+e);
			if (attempts > 10) {
		     	throw new UserException("Connection Error",
					"Error while setting up the connection to " + host);
			}
			attempts++;
	      }
		  if (!isClosed) break;
		}
		anyRemote._log("BTSocket","setup streams");
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
		anyRemote._log("BTSocket","CONNECTED");
	}

	@Override
	public void close() {
        if (isClosed) return;
        isClosed = true;
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

	@Override
	public InputStream getInputStream() {
		return is;
	}

	@Override
	public OutputStream getOutputStream() {
		return os;
	}
}
