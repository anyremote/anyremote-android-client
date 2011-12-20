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
import android.widget.TextView;
import anyremote.client.android.util.ProtocolMessage;
import anyremote.client.android.util.TextHandler;
import anyremote.client.android.R;

public class TextScreen extends arActivity  {

	static final int ADD     = 1;
	static final int REPLACE = 2;

	String      title;
	TextView    text;
	TextHandler evHandler; 	
	boolean isLog = false;

	Vector<String> defMenu = new Vector<String>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);  

		setContentView(R.layout.text_form);
		setTitle("");

		Intent  intent = getIntent();
		String subid   = intent.getStringExtra("SUBID");

		defMenu.add("Back");

		text = (TextView) findViewById(R.id.text_form);			

		if (subid.equals("replace") || subid.equals("clear")) {
			anyRemote.protocol.textContent = "";
		} 

		if (subid.equals("__LOG__")) {	// Special case for log view	
			text.setText(anyRemote.logData); 
			prefix = "Log"; // log stuff
			isLog = true;
		} else {
			evHandler = new TextHandler(this);
			anyRemote.protocol.setTextHandler(evHandler);
			anyRemote.protocol.setFullscreen(this);

			registerForContextMenu(text);

			text.setText(anyRemote.protocol.textContent);
			prefix = "TextScreen"; // log stuff
			log("onCreate");
		}
		text.setMovementMethod(new ScrollingMovementMethod());

		setFont();
		setTextColor();
		setBackground();
		callMenuUpdate();
		setTitle(anyRemote.protocol.textTitle);

		popup();
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
		
        if (anyRemote.status == anyRemote.DISCONNECTED) {
        	log("onResume no connection");
        	doFinish("");
        }
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

	private void doFinish(String action) {

		final Intent intent = new Intent();  
		intent.putExtra(anyRemote.ACTION, action);	    
		anyRemote.protocol.setTextHandler(null);

		if (isLog) {
			intent.putExtra(anyRemote.SWITCHTO, anyRemote.NO_FORM);		
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
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.clear();
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) { 
		menu.clear();

		for(int i = 0;i<menuItems.size();i++) {
			menu.add(menuItems.elementAt(i));
		}
		return true;
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

		if (isLog) return; 

		//log("handleEvent "+" "+data.stage+" "+ data.tokens);
		
		if (data.tokens.size() == 0) {
			return;
		}
		
		if (data.stage == ProtocolMessage.FULL || data.stage == ProtocolMessage.FIRST) {
			
			Integer id  = (Integer) data.tokens.elementAt(0);
			
			if (handleCommonCommand(id, data.tokens)) {
				return;
			}
			
			processData(data.tokens);
		} else if (data.stage == ProtocolMessage.INTERMED || data.stage == ProtocolMessage.LAST) {
			boolean addVisible = setString(ADD, (String) data.tokens.elementAt(0));
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

	public boolean setString(int mode, String newStr) {

		if (isLog) return true;

		//log("setString " + mode + ">"+newStr);

		boolean addVisible = true;
		if (mode == REPLACE) {
			text.setText("");
		}
		text.append(newStr);

		anyRemote.protocol.textContent = text.getText().toString();

		return true;
	}

	public void processData(Vector vR) {   // message = add|replace|show|clear,title,long_text
		if (isLog) return;

		String oper = (String) vR.elementAt(1);
		//log("processData >" + oper + "< " + vR.size());

		if (oper.equals("clear")) {

			text.setText("");

		} else if (oper.equals("add") || 

				oper.equals("replace")) {
			int op = REPLACE;
			if (oper.equals("add")) {
				op = ADD;
			}

			if (!((String) vR.elementAt(2)).equals("SAME")) {
				anyRemote.protocol.textTitle = (String) vR.elementAt(2);
				setTitle(anyRemote.protocol.textTitle);
			}

			boolean addVisible = setString(op, (String) vR.elementAt(3));

		} else if (oper.equals("fg")) {

			anyRemote.protocol.textFrgr = anyRemote.parseColor(
					(String) vR.elementAt(2),
					(String) vR.elementAt(3),
					(String) vR.elementAt(4));
			setTextColor();

		} else if (oper.equals("bg")) {

			anyRemote.protocol.textBkgr = anyRemote.parseColor(
					(String) vR.elementAt(2),
					(String) vR.elementAt(3),
					(String) vR.elementAt(4));
			setBackground();

		} else if (oper.equals("font")) {

			setFontParams(vR);
			setFont();

		} else if (oper.equals("wrap")) {

			// not supported

		} else if (oper.equals("close")) {

			if (vR.size() > 2 && ((String) vR.elementAt(2)).equals("clear")) {
				text.setText("");
			}
			doFinish("");
			return;

		} else if (!oper.equals("show")) {
			return; // seems command improperly formed
		}
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

	private void setFontParams(Vector defs) {

		boolean bold   = false;
		boolean italic = false;
		float   size   = Dispatcher.SIZE_MEDIUM; 

		int start = 2;
		while(start<defs.size()) {
			String spec = (String) defs.elementAt(start);
			if (spec.equals("plain")) {
				//style = Font.STYLE_PLAIN;
			} else if (spec.equals("bold")) {
				bold = true;
			} else if (spec.equals("italic")) {
				italic = true;
			} else if (spec.equals("underlined")) {
				//style = (style == Font.STYLE_PLAIN ? Font.STYLE_UNDERLINED : style|Font.STYLE_UNDERLINED);
			} else if (spec.equals("small")) {
				size = Dispatcher.SIZE_SMALL;
			} else if (spec.equals("medium")) {
				size = Dispatcher.SIZE_MEDIUM;
			} else if (spec.equals("large")) {
				size = Dispatcher.SIZE_LARGE;
			} else if (spec.equals("monospace")) {
				//face  = Font.FACE_MONOSPACE;
			} else if (spec.equals("system")) {
				//face  = Font.FACE_SYSTEM;
			} else if (spec.equals("proportional")) {
				//face  = Font.FACE_PROPORTIONAL;
				//} else {
				//	controller.showAlert("Incorrect font "+spec);
			}
			start++;
		}

		if (bold && italic) {
			anyRemote.protocol.textTFace = Typeface.defaultFromStyle(Typeface.BOLD_ITALIC);
		} else if (bold) {
			anyRemote.protocol.textTFace = Typeface.defaultFromStyle(Typeface.BOLD);
		} else if (italic) {
			anyRemote.protocol.textTFace = Typeface.defaultFromStyle(Typeface.ITALIC);
		} else {
			anyRemote.protocol.textTFace = Typeface.defaultFromStyle(Typeface.NORMAL);
		}
		anyRemote.protocol.textFSize = size;
	}
}
