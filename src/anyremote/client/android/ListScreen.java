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
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import anyremote.client.android.util.ListHandler;
import anyremote.client.android.util.ListItem;
import anyremote.client.android.util.ListScreenAdapter;
import anyremote.client.android.util.ProtocolMessage;
import anyremote.client.android.R;

public class ListScreen extends arActivity 
implements OnItemClickListener,
AdapterView.OnItemSelectedListener,
KeyEvent.Callback {

	ListView          list;
	ListScreenAdapter dataSource;
	ListHandler       evHandler;

	boolean skipDismissEditDialog = false;
	String  listBufferedItem;
	int selectedPosition = -1;
	private boolean customTextColor = false;
	private boolean customBackColor = false;   

	Vector<String> defMenu = new Vector<String>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		prefix = "ListScreen"; // log stuff
		log("onCreate");

		Intent  intent = getIntent();
		String subid   = intent.getStringExtra("SUBID");

		dataSource = new ListScreenAdapter(this, R.layout.list_form_item, anyRemote.protocol.listContent);
		if (subid.equals("replace") || subid.equals("clear")) {
			log("onCreate clear content");
		    dataSource.clear();
		}

		defMenu.add("Back");

		evHandler = new ListHandler(this);
		anyRemote.protocol.setListHandler(evHandler);
		anyRemote.protocol.setFullscreen(this);

		setContentView(R.layout.list_form);

		list = (ListView) findViewById(R.id.list_form);	

		registerForContextMenu(list);

		list.setAdapter(dataSource); 

		list.setOnItemSelectedListener(this);
		list.setOnItemClickListener   (this); 

		callMenuUpdate();
		setTitle(anyRemote.protocol.listTitle);

		//list.setBackgroundColor(Color.rgb(0,0,0));
		
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

		/*switch (item.getItemId()) {
     		case R.id.connect_to:
      	        break;
        }*/


		return super.onContextItemSelected(item);
	}

	private void doFinish(String action) {

		log("doFinish");

		anyRemote.protocol.setListHandler(null);

		final Intent intent = new Intent();  
		intent.putExtra(anyRemote.SWITCHTO, anyRemote.CONTROL_FORM);

		setResult(RESULT_OK, intent);
		log("doFinish finish");
		finish();  	
	}

	/*@Override
	public void onBackPressed() {  	
	    //doFinish("Back");
		//super.onBackPressed();
		final String itemText = (selectedPosition == -1 ? "" : dataSource.getItem(selectedPosition).text);
	    commandAction("Back", itemText, selectedPosition);
	}*/

	// Selection handlers
	@Override
	public void onItemSelected(AdapterView<?> parentView, View childView, int position, long id) {
		select(position);
	}

	@Override
	public void onNothingSelected(AdapterView<?> parentView) {
		//log("onNothingSelected");
		//select(-1);
		//select(selectedPosition);
	}

	public void select(int position) {
		//log("select "+position);
		selectedPosition = position;
		
		dataSource.setSelectedPosition(selectedPosition);
		if (customTextColor && customBackColor) {
			dataSource.notifyDataSetChanged();
		}
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

		log("onOptionsItemSelected "+selectedPosition);

		final String itemText = (selectedPosition == -1 ? "" : dataSource.getItem(selectedPosition).text);

		commandAction(item.getTitle().toString(), itemText, selectedPosition);
		return true;
	}

	public void commandAction(String command, String value, int pos) {

		//if (command.equals("Back")) {
		//     ar.protocol.queueCommand("Back(0,)");
		//} else {
		anyRemote.protocol.queueCommand(command + 
				"(" + String.valueOf(pos+1) + 
				"," + value + ")");
		//}
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

			boolean listUseIconsStream = false;
			if (((Integer) data.tokens.elementAt(0)).intValue() == Dispatcher.CMD_ICONLIST) {
				listUseIconsStream = true;
			}

			processList(data.tokens, listUseIconsStream, data.stage);

		} else  if (data.stage == ProtocolMessage.INTERMED || data.stage == ProtocolMessage.LAST) {
			addToList(data.tokens, 0, (data.stage == ProtocolMessage.LAST));
		}

	}	

	// list|iconlist, add|replace|close|clear|show[,Title,item1,...itemN]
	// or 
	// menu,item1,...itemN
	//controller.showAlert("processData() " +stage); 
	public void processList(Vector vR, boolean useIcons, int stage) {	
		//log("processList"); 

		String oper  = (String) vR.elementAt(1); 

		if (oper.equals("clear")) {

			listCleanUp();

		} else if (oper.equals("close")) {

			if (vR.size() > 2 && ((String) vR.elementAt(2)).equals("clear")) {
				listCleanUp();
			}
			doFinish("");
			return;

		} else if (oper.equals("fg")) {

			dataSource.setTextColor(anyRemote.parseColor(
					(String) vR.elementAt(2),
					(String) vR.elementAt(3),
					(String) vR.elementAt(4)));
			customTextColor = true;
		} else if (oper.equals("bg")) {

			int color = anyRemote.parseColor(
					(String) vR.elementAt(2),
					(String) vR.elementAt(3),
					(String) vR.elementAt(4));
			dataSource.setBackColor(color);
			list.setBackgroundColor(color);
			customBackColor = true;

		} else if (oper.equals("font")) {

			//dataSource.setFont(ar.getFontBySpec(vR, 1, dataSource.getDefaultTextSize()));

		} else if (oper.equals("select")) {

			try { 
				int i = Integer.parseInt((String) vR.elementAt(2))-1;
				if (i>0) {
					list.setSelection(i);
					select(i);
				}
			} catch(Exception z) { }

		} else if (oper.equals("add") || oper.equals("replace")) {

			String title = (String) vR.elementAt(2);

			if (oper.equals("replace")) {
				listCleanUp();
			}
			if (!title.equals("SAME")) {
				setTitle(title);
				anyRemote.protocol.listTitle = title;
			}

			addToList(vR, 3, (stage == ProtocolMessage.FULL));

		} else if (!oper.equals("show")) {
			return;	// Seems command was improperly formed		
		}
		//log("processData DONE"); 
	}

	public void addToList(Vector vR, int start, boolean fullCmd) {

		//log("addToList "+vR); 

		int end = vR.size();
		if (!fullCmd) {
			end -= 1;
		}

		for (int idx=start;idx<end;idx++) {

			String item = (String) vR.elementAt(idx);
			if (start == 0 && idx == 0) {
				item = listBufferedItem + item;
				listBufferedItem = "";
			}
			if (!item.equals("") && ! (item.length() == 1 && item.charAt(0) == '\n')) {
				addWithIcon(item); 
			}
		}

		if (!fullCmd) {
			listBufferedItem = (String) vR.elementAt(end);
		}

		dataSource.notifyDataSetChanged();
	}

	public void addWithIcon(String content) {

		ListItem item = new ListItem();

		int idx = content.indexOf(":");
		if (idx > 0) {
			item.icon = content.substring(0,idx).trim();
			item.text = content.substring(idx+1).trim().replace('\r', ',');
		} else {
			item.icon = null;
			item.text = content;
		}
		anyRemote.protocol.listContent.add(item);
		//dataSource.add(item);
	}	

	public void listCleanUp() {
		dataSource.clear();
		selectedPosition = -1;		
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

			final String itemText = (selectedPosition == -1 ? "" : dataSource.getItem(selectedPosition).text);
			commandAction("Push", itemText, selectedPosition);		
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_BACK) {

			final String itemText = (selectedPosition == -1 ? "" : dataSource.getItem(selectedPosition).text);
			commandAction("Back", itemText, selectedPosition);
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

