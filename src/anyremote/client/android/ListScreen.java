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
import android.os.Bundle;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import anyremote.client.android.util.ListScreenAdapter;
import anyremote.client.android.util.ProtocolMessage;
import anyremote.client.android.util.arHandler;
import anyremote.client.android.R;

public class ListScreen extends arActivity 
						implements OnItemClickListener,
								   AdapterView.OnItemSelectedListener,
								   KeyEvent.Callback {

	ListView          list;
	ListScreenAdapter dataSource;
	Dispatcher.ArHandler hdlLocalCopy;

	boolean skipDismissEditDialog = false;

	Vector<String> defMenu = new Vector<String>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		prefix = "ListScreen"; // log stuff
		log("onCreate");

		dataSource = new ListScreenAdapter(this, R.layout.list_form_item, anyRemote.protocol.listContent);
		
		hdlLocalCopy = new Dispatcher.ArHandler(anyRemote.LIST_FORM, new arHandler(this));
		anyRemote.protocol.addMessageHandler(hdlLocalCopy);

		setContentView(R.layout.list_form);

		list = (ListView) findViewById(R.id.list_form);	
		list.setAdapter(dataSource); 
		list.setOnItemSelectedListener(this);
		list.setOnItemClickListener   (this); 
		
		registerForContextMenu(list);

		defMenu.add("Back");
		callMenuUpdate();
	}
	
	@Override
	protected void onPause() {
		log("onPause");
		
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
        
		redraw();
		popup();
	}
	
	@Override
	protected void onDestroy() {	
		log("onDestroy");	
	   	anyRemote.protocol.removeMessageHandler(hdlLocalCopy);
	   	super.onDestroy();
	}

	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {	
		//log("onItemClick "+arg2);
		//list.setSelection(arg2);
		//select(arg2);

		final String itemText = (arg2 == -1 ? "" : dataSource.getItem(arg2).text);
		commandAction("Push", itemText, arg2);		
	}

	// Handle long-click
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		if (v.getId() == R.id.list_form) {
			final AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
			final TextView tv= (TextView) info.targetView.findViewById(R.id.list_item_text);

			menu.setHeaderTitle(tv.getText());

			for(int i = 0;i<menuItems.size();i++) {   	    	
				menu.add(menuItems.elementAt(i));
			}     	
		}
	}

	// Handle context menu, opened by long-click
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

		final String itemText = dataSource.getItem(info.position).text;
		commandAction(item.getTitle().toString(), itemText, info.position);

		return super.onContextItemSelected(item);
	}

	@Override
	protected void doFinish(String action) {

		log("doFinish");
		finish();  	
	}

	/*@Override
	public void onBackPressed() {  	
		final String itemText = (selectedPosition == -1 ? "" : dataSource.getItem(selectedPosition).text);
	    commandAction("Back", itemText, selectedPosition);
	}*/

	// Selection handlers
	@Override
	public void onItemSelected(AdapterView<?> parentView, View childView, int position, long id) {
		anyRemote.protocol.listSelectPos = position;
		selectUpdate();
	}

	@Override
	public void onNothingSelected(AdapterView<?> parentView) {
		//log("onNothingSelected");
		//select(-1);
		//select(selectedPosition);
	}

	public void selectUpdate() {
		if (anyRemote.protocol.listSelectPos > 0) {
			dataSource.setSelectedPosition(anyRemote.protocol.listSelectPos);		
			if (anyRemote.protocol.listCustomTextColor && anyRemote.protocol.listCustomBackColor) {
				dataSource.notifyDataSetChanged();
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		log("onOptionsItemSelected "+anyRemote.protocol.listSelectPos);

		final String itemText = (anyRemote.protocol.listSelectPos == -1 ? "" : dataSource.getItem(anyRemote.protocol.listSelectPos).text);

		commandAction(item.getTitle().toString(), itemText, anyRemote.protocol.listSelectPos);
		
		return true;
	}

	public void commandAction(String command, String value, int pos) {

		anyRemote.protocol.queueCommand(command + 
				"(" + String.valueOf(pos+1) + "," + value + ")");
	}

	public void handleEvent(ProtocolMessage data) {

		log("handleEvent "+" "+data.stage+" "+ data.tokens);
	
		if (data.tokens.size() == 0) {
			return;
		}

		if (data.stage == ProtocolMessage.FULL || data.stage == ProtocolMessage.FIRST) {

			Integer id  = (Integer) data.tokens.elementAt(0);
			
			if (handleCommonCommand(id, data.tokens)) {
				return;
			}
			
			if (data.tokens.size() > 1 &&
				(id == Dispatcher.CMD_LIST ||
			     id == Dispatcher.CMD_ICONLIST)) {
				
				// update all visuals
				redraw();
				
				// get info about data update
				if ((Boolean) data.tokens.elementAt(1)) {
					dataSource.notifyDataSetChanged();
				}
			}
		} else  if (data.stage == ProtocolMessage.INTERMED || data.stage == ProtocolMessage.LAST) {
			dataSource.notifyDataSetChanged();
		}
		
		// on update data
		// + set title
		// + select by index, if it >= 0

	}	
	
	// update all visuals
	void redraw()  {
		
		anyRemote.protocol.setFullscreen(this);
		
		setTitle(anyRemote.protocol.listTitle);
		
		if (anyRemote.protocol.listFSize > 0) {
		    dataSource.setFont(anyRemote.protocol.listFSize);
		}
		if (anyRemote.protocol.listCustomBackColor) {
			log("handleEvent setBackColor "+anyRemote.protocol.listBkgr);
			dataSource.setBackColor(anyRemote.protocol.listBkgr);
			list.setBackgroundColor(anyRemote.protocol.listBkgr);			
		}
		if (anyRemote.protocol.listCustomTextColor) {
			log("handleEvent setTextColor "+anyRemote.protocol.listText);
			dataSource.setTextColor(anyRemote.protocol.listText);
		}
		selectUpdate();
	}
	
	void callMenuUpdate()  { // Add predefined menu items

		menuItems.add("Back");
		restorePersistentMenu(anyRemote.protocol.listMenu);
	}

	public void processMenu(Vector vR) {
		processMenu(vR, anyRemote.protocol.listMenu, defMenu);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) { 

		if (keyCode == KeyEvent.KEYCODE_CALL ||
		    keyCode == KeyEvent.KEYCODE_SEARCH) {

			final String itemText = (anyRemote.protocol.listSelectPos == -1 ? "" : dataSource.getItem(anyRemote.protocol.listSelectPos).text);
			commandAction("Push", itemText, anyRemote.protocol.listSelectPos);		
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_BACK) {

			final String itemText = (anyRemote.protocol.listSelectPos == -1 ? "" : dataSource.getItem(anyRemote.protocol.listSelectPos).text);
			commandAction("Back", itemText, anyRemote.protocol.listSelectPos);
			return true;
		}
		return false;
	}

	@Override
	public boolean onKeyDown (int keyCode, KeyEvent event) { 

		//log("onKeyDown "+keyCode);
		if (keyCode == KeyEvent.KEYCODE_CALL ||
		    keyCode == KeyEvent.KEYCODE_SEARCH) {
			return true;
		} 
		return false;
	}
}

