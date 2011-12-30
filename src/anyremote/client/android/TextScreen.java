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
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import anyremote.client.android.util.ProtocolMessage;
import anyremote.client.android.util.TextHandler;
import anyremote.client.android.R;

public class TextScreen extends arActivity  {

	TextView  text;
	Dispatcher.ArHandler hdlLocalCopy;
	Vector<String> defMenu = new Vector<String>();
	boolean isLog = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);  

		setContentView(R.layout.text_form);
	
		Intent  intent = getIntent();
		String subid   = intent.getStringExtra("SUBID");

		defMenu.add("Back");

		text = (TextView) findViewById(R.id.text_form);			

		if (subid.equals("__LOG__")) {	// Special case for log view	

			prefix = "Log"; // log stuff
			isLog = true;
			log("onCreate");
			
		} else {
			
			hdlLocalCopy = new Dispatcher.ArHandler(anyRemote.TEXT_FORM, new TextHandler(this));
			anyRemote.protocol.addMessageHandler(hdlLocalCopy);
		    
			anyRemote.protocol.setFullscreen(this);

			prefix = "TextScreen"; // log stuff
			log("onCreate");
		}		
		registerForContextMenu(text);
		text.setMovementMethod(new ScrollingMovementMethod());

		redraw();
		
		popup();
	}
	
	// update all visuals
	void redraw()  {
		log("redraw");
		
		if (isLog) {
			text.setText(anyRemote.logData); 
			callMenuUpdate();
			setTitle("Log");
		} else {
			text.setText(anyRemote.protocol.textContent);
			setFont();
			setTextColor();
			setBackground();
			callMenuUpdate();
			setTitle(anyRemote.protocol.textTitle);
		}
	}
	
	@Override
	protected void onPause() {
		log("onPause");
		
		dismissPopup();
		popup();
		
		//MainLoop.disable();
	    super.onPause();	
	}
	
	@Override
	protected void onResume() {
		log("onResume");
		//MainLoop.enable();
		super.onResume();
		
        if (anyRemote.status == anyRemote.DISCONNECTED && 
        	!isLog) {
        	
        	log("onResume no connection");
        	doFinish("");
        }
	}
	
	@Override
	protected void onDestroy() {	
		log("onDestroy");	
	   	anyRemote.protocol.removeMessageHandler(hdlLocalCopy);		
	   	isLog = false;
	   	super.onDestroy();
	}

	// Handle long-click
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		if (!isLog && v.getId() == R.id.text_form) {
			menu.setHeaderTitle(anyRemote.protocol.textTitle);

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
		
		if (isLog) {
			//intent.putExtra(anyRemote.SWITCHTO, anyRemote.NO_FORM);		
		} else {
			log("doFinish");
			intent.putExtra(anyRemote.SWITCHTO, anyRemote.CONTROL_FORM);	   
		}

		setResult(RESULT_OK, intent);
		if (!isLog) {
			log("doFinish finish");
		}
		
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
		if (isLog) {
			if (command.equals("Back")) {
				doFinish("log");  // just close Log form
			} else if (command.equals("Clear log")) {
				anyRemote.logData.delete(0,anyRemote.logData.length());
				text.setText("");
			} else if (command.equals("Report bug")) {

				Intent mailIntent = new Intent(Intent.ACTION_SEND);
				mailIntent.setType("text/plain");
				mailIntent.putExtra(Intent.EXTRA_EMAIL, new String[] { "anyremote@mail.ru" });
				mailIntent.putExtra(Intent.EXTRA_SUBJECT, "anyRemote android client bugreport");
				mailIntent.putExtra(Intent.EXTRA_TEXT, anyRemote.logData.toString());

				startActivity(Intent.createChooser(mailIntent, "Bug report"));
			}
		} else {
			anyRemote.protocol.queueCommand(command);
		}
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
		
		if (isLog) return; 
		
		if (data.tokens.size() == 0) {
			return;
		}

		if (data.stage == ProtocolMessage.FULL || data.stage == ProtocolMessage.FIRST) {

			Integer id  = (Integer) data.tokens.elementAt(0);
			
			if (handleCommonCommand(id, data.tokens)) {
				return;
			}
			
			if (id == Dispatcher.CMD_TEXT) {
				
				// update all visuals
				redraw();
			}
		} else  if (data.stage == ProtocolMessage.INTERMED || data.stage == ProtocolMessage.LAST) {
			redraw();
		}
	}		

	void callMenuUpdate()  { // Add predefined menu items

		//log("callMenuUpdate "+isLog);
		menuItems.add("Back");
		if (isLog) { 
			menuItems.add("Clear log");
			menuItems.add("Report bug");
		} else {
			restorePersistentMenu(anyRemote.protocol.textMenu);
		}
	}

	public void processMenu(Vector vR) {
		processMenu(vR, anyRemote.protocol.textMenu, defMenu);
	}

	private void setTextColor() {
		text.setTextColor(anyRemote.protocol.textFrgr);
	}

	private void setBackground() {
		text.setBackgroundColor(anyRemote.protocol.textBkgr);
	}

	private void setFont() {

		TextView ttx  = (TextView) findViewById(R.id.text_form);
		ttx.setTypeface (anyRemote.protocol.textTFace);
		ttx.setTextSize (anyRemote.protocol.textFSize);
	}
}
