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

import java.util.Vector;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import anyremote.client.android.util.ProtocolMessage;
import anyremote.client.android.util.WinHandler;
import anyremote.client.android.R;

public class WinManager extends arActivity  {
	
	ImageView image;
	WinHandler hdlLocalCopy;
	Vector<String> defMenu = new Vector<String>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);  

		setContentView(R.layout.win_manager);
	
		Intent  intent = getIntent();
		
		image = (ImageView) findViewById(R.id.window);	
		
		defMenu.add("Back");
			
		hdlLocalCopy = new WinHandler(this);
		anyRemote.protocol.addMessageHandlerWM(hdlLocalCopy);
		    
		anyRemote.protocol.setFullscreen(this);

		prefix = "WinManager"; // log stuff
		log("onCreate");

		registerForContextMenu(image);

		redraw();
		
		popup();
	}
	
	// update all visuals
	void redraw()  {
		log("redraw");
		image.setImageBitmap(anyRemote.protocol.imScreen);
	}
	
	@Override
	protected void onPause() {
		log("onPause");
		
		dismissPopup();
		popup();
	    super.onPause();	
	}
	
	@Override
	protected void onResume() {
		log("onResume");
		super.onResume();
		
        if (anyRemote.status == anyRemote.DISCONNECTED) {
          	log("onResume no connection");
        	doFinish("");
        }
	}
	
	@Override
	protected void onDestroy() {	
		log("onDestroy");	
	   	anyRemote.protocol.removeMessageHandlerWM(hdlLocalCopy);		
	   	super.onDestroy();
	}

	// Handle long-click
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		if (v.getId() == R.id.window) {

			for(int i = 0;i<menuItems.size();i++) {   	    	
				menu.add(menuItems.elementAt(i));
			}     	
		}
	}

	// Handle context menu, opened by long-click
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		commandAction(item.getTitle().toString());  	
		return super.onContextItemSelected(item);
	}	
	
	
	@Override
	protected void doFinish(String action) {
		
		log("doFinish");
		
		final Intent intent = new Intent();  
		intent.putExtra(anyRemote.ACTION, action);	    
		
		intent.putExtra(anyRemote.SWITCHTO, anyRemote.CONTROL_FORM);	   
	
		setResult(RESULT_OK, intent);
		log("doFinish finish");
			
		finish();  	
	}

	@Override
	public void onBackPressed() { 
		commandAction("Back");
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		commandAction(item.getTitle().toString());
		return true;
	}

	public void commandAction(String command) {
		anyRemote.protocol.queueCommand(command);
	}

	// Set(text,add,title,_text_)		3+text
	// Set(text,replace,title,_text_)	3+text
	// Set(text,fg|bg,r,g,b)		6
	// Set(text,font,small|medium|large)	3
	// Set(text,close[,clear])		2 or 3
	// Set(text,wrap,on|off)		3
	// Set(text,show)			2
	public void handleEvent(ProtocolMessage data) {
		
		log("handleEvent");
		
		if (data.tokens.size() == 0) {
			return;
		}

		if (data.stage == ProtocolMessage.FULL || data.stage == ProtocolMessage.FIRST) {

			Integer id  = (Integer) data.tokens.elementAt(0);
			
			if (handleCommonCommand(id, data.tokens)) {
				return;
			}
			
			if (id == Dispatcher.CMD_IMAGE) {
				
				// update all visuals
				redraw();
			}
		} else  if (data.stage == ProtocolMessage.INTERMED || data.stage == ProtocolMessage.LAST) {
			// should not come here
			redraw();
		}
	}		

	void callMenuUpdate()  { // Add predefined menu items

		//log("callMenuUpdate "+isLog);
		menuItems.add("Back");
		restorePersistentMenu(anyRemote.protocol.textMenu);
	}

	public void processMenu(Vector vR) {
		processMenu(vR, anyRemote.protocol.textMenu, defMenu);
	}
}
