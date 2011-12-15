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

import android.os.Handler;
import android.os.Message;
import android.widget.Toast;
import anyremote.client.android.anyRemote;
import anyremote.client.android.R;

public class ViewHandler extends Handler {
	anyRemote client;
	
	public ViewHandler(anyRemote client) {
		this.client = client;
	}

	@Override
	public void handleMessage(Message msg) {

		switch(msg.what){
		
		case anyRemote.CONNECTED:
			
			anyRemote._log("ViewHandler", "handleMessage: CONNECTED!");
			//Toast.makeText(client, R.string.connection_successful, Toast.LENGTH_SHORT).show();
			client.handleEvent(anyRemote.CONNECTED);
			break;
			
		case anyRemote.DISCONNECTED:
			
			anyRemote._log("ViewHandler", "handleMessage: DISCONNECTED!");
			//Toast.makeText(client, R.string.connection_failed, Toast.LENGTH_SHORT).show();
			client.handleEvent(anyRemote.DISCONNECTED);
			break;			
		}
	}
}
