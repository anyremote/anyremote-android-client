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

import java.util.ArrayList;

import android.os.Bundle;
import android.os.Handler;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.GestureDetector.OnGestureListener;
import android.view.GestureDetector;
import anyremote.client.android.util.ListScreenAdapter;
import anyremote.client.android.util.ListItem;
import anyremote.client.android.util.InfoMessage;
import anyremote.client.android.util.ProtocolMessage;
import anyremote.client.android.R;

public class ListScreen extends arActivity 
						implements OnItemClickListener,
								   AdapterView.OnItemSelectedListener,
								   KeyEvent.Callback,
								   OnGestureListener {

	ListView          list;
	ListScreenAdapter dataSource;
	Dispatcher.ArHandler hdlLocalCopy;
	ArrayList<ListItem> listItems;

	boolean skipDismissEditDialog = false;
	
	private GestureDetector gestureScanner;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		prefix = "ListScreen"; // log stuff
		log("onCreate");
			
		listItems  = new ArrayList<ListItem>();
		dataSource = new ListScreenAdapter(this, R.layout.list_form_item, listItems);
		
		hdlLocalCopy = new Dispatcher.ArHandler(anyRemote.LIST_FORM, new Handler(this));
		anyRemote.protocol.addMessageHandler(hdlLocalCopy);

		setContentView(R.layout.list_form);

		list = (ListView) findViewById(R.id.list_form);	
		list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		list.setAdapter(dataSource); 
		list.setOnItemSelectedListener(this);
		list.setOnItemClickListener   (this); 
		
		gestureScanner = new GestureDetector(this);
		
		registerForContextMenu(list);
	}
	
	@Override
	protected void onPause() {
		log("onPause");
		
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
        
		//log("onResume UPDATE DATA SOURCE");
		dataSource.update(anyRemote.protocol.listContent);
		//log("onResume notifyDataSetChanged");
		dataSource.notifyDataSetChanged();
        
		redraw();
		popup();
		
		exiting = false;	
	}
	
	@Override
	protected void onDestroy() {	
		log("onDestroy");	
	   	anyRemote.protocol.removeMessageHandler(hdlLocalCopy);
	   	super.onDestroy();
	}

	//@Override
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
			addContextMenu(menu);
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
	    exiting = true;
	    finish();  	
	}

	/*@Override
	public void onBackPressed() {  	
		final String itemText = (selectedPosition == -1 ? "" : dataSource.getItem(selectedPosition).text);
	    commandAction(anyRemote.protocol.context.getString(R.string.back_item), itemText, selectedPosition);
	}*/

	// Selection handlers
	//@Override
	public void onItemSelected(AdapterView<?> parentView, View childView, int position, long id) {
		anyRemote.protocol.listSelectPos = position;
		selectUpdate();
	}

	//@Override
	public void onNothingSelected(AdapterView<?> parentView) {
		anyRemote.protocol.listSelectPos = -1;
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
    public boolean dispatchTouchEvent(MotionEvent ev) { 
		//log("dispatchTouchEvent");
        super.dispatchTouchEvent(ev); 
        return gestureScanner.onTouchEvent(ev); 
	}

	@Override
    public boolean onTouchEvent(MotionEvent me) {
        return gestureScanner.onTouchEvent(me);
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		log("onOptionsItemSelected "+anyRemote.protocol.listSelectPos);

		final String itemText = (anyRemote.protocol.listSelectPos == -1 ? "" : dataSource.getItem(anyRemote.protocol.listSelectPos).text);

		commandAction(item.getTitle().toString(), itemText, anyRemote.protocol.listSelectPos);
		
		return true;
	}
	
	@Override
	protected void onUserLeaveHint() {
		log("onUserLeaveHint");
		if (!exiting && anyRemote.protocol.messageQueueSize() == 0) {
			log("onUserLeaveHint - make disconnect");
			anyRemote.protocol.disconnect(false);
		}
	}

	public void commandAction(String command, String value, int pos) {
		
		if (command.equals(anyRemote.protocol.context.getString(R.string.back_item))) {
			command = "Back";  // avoid national alphabets
		}

		anyRemote.protocol.queueCommand(command + 
				"(" + String.valueOf(pos+1) + "," + value + ")");
	}

	public void handleEvent(InfoMessage data) {

		log("handleEvent " + Dispatcher.cmdStr(data.id) + " " + data.stage);

		if (data.stage == ProtocolMessage.FULL || data.stage == ProtocolMessage.FIRST) {
			
			if (handleCommonCommand(data.id)) {
				return;
			}
				
			// get info about data update
			if (data.id == Dispatcher.CMD_LIST_UPDATE) {
				log("handleEvent UPDATE DATA SOURCE");
				dataSource.update(anyRemote.protocol.listContent);
				log("handleEvent notifyDataSetChanged");
				dataSource.notifyDataSetChanged();
			}
			
			// update all visuals
			redraw();
			
		} else  if (data.stage == ProtocolMessage.INTERMED || data.stage == ProtocolMessage.LAST) {
			dataSource.update(anyRemote.protocol.listContent);
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
			//log("handleEvent setBackColor "+anyRemote.protocol.listBkgr);
			dataSource.setBackColor(anyRemote.protocol.listBkgr);
			list.setBackgroundColor(anyRemote.protocol.listBkgr);			
		}
		if (anyRemote.protocol.listCustomTextColor) {
			//log("handleEvent setTextColor "+anyRemote.protocol.listText);
			dataSource.setTextColor(anyRemote.protocol.listText);
		}
		selectUpdate();
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) { 

		switch (keyCode) {
		
		    case KeyEvent.KEYCODE_CALL:  
	        case KeyEvent.KEYCODE_SEARCH:
				final String itemTextC = (anyRemote.protocol.listSelectPos == -1 ? "" : 
					                     dataSource.getItem(anyRemote.protocol.listSelectPos).text);
				commandAction("Push", itemTextC, anyRemote.protocol.listSelectPos);		
				return true;

	        case KeyEvent.KEYCODE_BACK:
	    	  
				final String itemTextB = (anyRemote.protocol.listSelectPos == -1 ? "" : 
					                     dataSource.getItem(anyRemote.protocol.listSelectPos).text);
				commandAction("Back", itemTextB, anyRemote.protocol.listSelectPos);
				return true;
				
		    case KeyEvent.KEYCODE_VOLUME_UP:  
		    	if (anyRemote.protocol.listSelectPos > 0) {
		    		anyRemote.protocol.listSelectPos--;
		    	}
		    	dataSource.setSelectedPosition(anyRemote.protocol.listSelectPos);
		    	list.setSelection(anyRemote.protocol.listSelectPos);
		    	//selectUpdate();
	        	return true;
	    	
	        case KeyEvent.KEYCODE_VOLUME_DOWN:
	        	if (anyRemote.protocol.listSelectPos < dataSource.size() - 1) {
	        		anyRemote.protocol.listSelectPos++;
	        	}
	        	//log("onKeyUp KEYCODE_VOLUME_DOWN "+anyRemote.protocol.listSelectPos);
	        	dataSource.setSelectedPosition(anyRemote.protocol.listSelectPos);
	        	list.setSelection(anyRemote.protocol.listSelectPos);
	        	//selectUpdate();
	        	return true;  
		}
		return false;
	}

	@Override
	public boolean onKeyDown (int keyCode, KeyEvent event) { 

		//log("onKeyDown "+keyCode);
		switch (keyCode) {
		
		    case KeyEvent.KEYCODE_CALL:  
            case KeyEvent.KEYCODE_SEARCH:
		    case KeyEvent.KEYCODE_VOLUME_UP:  
            case KeyEvent.KEYCODE_VOLUME_DOWN:
           	return true;
		} 
		return false;
	}
    public boolean onDown(MotionEvent e) {
        //log("onDown");
        return true;
    }
   
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        //log("onFling " + e1.getX() + " " + e1.getY() + " " 
        //		       + e2.getX() + " " + e2.getY() + " " 
        //		       + velocityX + " " + velocityY);
        try {
             // right to left swipe
            if (e1.getX() - e2.getX() > anyRemote.SWIPE_MIN_DISTANCE && 
            		   Math.abs(velocityX) > anyRemote.SWIPE_THRESHOLD_VELOCITY) {
                commandAction("ListSlideLeft","",-1);
            } else if (e2.getX() - e1.getX() > anyRemote.SWIPE_MIN_DISTANCE && 
            		   Math.abs(velocityX) > anyRemote.SWIPE_THRESHOLD_VELOCITY) {
            	commandAction("ListSlideRight","",-1);
            } else {
             	return false;
            }
        } catch (Exception e) {
            // nothing
        }

        return true;
    }
   
    public void onLongPress(MotionEvent e) {
        //log("onLongPress");
    }
   
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        //log("onScroll");
        return true;
    }
   
    public void onShowPress(MotionEvent e) {
        //log("onShowPress");
    }    
   
    public boolean onSingleTapUp(MotionEvent e) {
        //log("onSingleTapUp");
        return true;
    }
 }

