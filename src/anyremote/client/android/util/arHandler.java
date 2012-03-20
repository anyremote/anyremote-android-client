//
//anyRemote android client
//a bluetooth/wi-fi remote control for Linux.
//
//Copyright (C) 2011-2012 Mikhail Fedotov <anyremote@mail.ru>
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA. 
//

package anyremote.client.android.util;

import android.os.Handler;
import android.os.Message;
import anyremote.client.android.arActivity;


public class arHandler extends Handler {

    arActivity client;
	
	public arHandler(arActivity client) {
		this.client = client;
	} 

	@Override
	public void handleMessage(Message msg) {
		client.handleEvent((ProtocolMessage) msg.obj);
	}
}