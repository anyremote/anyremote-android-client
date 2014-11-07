//
// anyRemote android client
// a bluetooth/wi-fi remote control for Linux.
//
// Copyright (C) 2011-2014 Mikhail Fedotov <anyremote@mail.ru>
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

import android.app.Activity;
import android.app.Dialog;
import android.os.Handler;
import android.os.Message;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.Menu;
import anyremote.client.android.R;
import anyremote.client.android.util.InfoMessage;

public class arActivity extends Activity 
		implements DialogInterface.OnDismissListener,
				   DialogInterface.OnCancelListener,
				   Handler.Callback {

	protected String prefix = "";	
	private boolean skipDismissEditDialog = false;
	protected boolean exiting = false;
	protected int privateMenu = anyRemote.NO_FORM;
	
	public boolean handleMessage(Message msg) {
		handleEvent((InfoMessage) msg.obj);
		return true;
	}
	
	/*@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}
	
	@Override
	protected void onPause() {
		anyRemote.runningCount--;
		super.onPause();
	}

	@Override
	protected void onResume() {
		anyRemote.runningCount++;
		super.onResume();
	}*/

	public void log(String msg) {
		anyRemote._log(prefix,msg);
	}
	
	public void handleEvent(InfoMessage data) {
		log("handleEvent "+" "+data.stage+" "+ data.id);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
     	menu.clear();
    	return true;
    }
	 
    @Override
	public boolean onPrepareOptionsMenu(Menu menu) { 
    	
    	menu.clear();
    	
    	if (privateMenu == anyRemote.LOG_FORM) { // LOG screen
    		menu.add(getString(R.string.clear_log_item));
			menu.add(getString(R.string.report_bug_item));
			menu.add(getString(R.string.back_item));
    	} else if (privateMenu == anyRemote.MOUSE_FORM) { // Mouse screen
		    menu.add(getString(R.string.back_item));
        } else if (privateMenu == anyRemote.KEYBOARD_FORM) { // Mouse screen
            menu.add(getString(R.string.back_item));
            menu.add(getString(R.string.escape));
            menu.add(getString(R.string.enter));
            menu.add(getString(R.string.backspace));
            menu.add(getString(R.string.alt_f4));
    	} else if (privateMenu == anyRemote.WEB_FORM) { // Web screen
		    menu.add(getString(R.string.disconnect_item));
     	} else {
    		Vector<String> menuItems = anyRemote.protocol.getMenu();

	    	if (menuItems != null) {
			    for(int i = 0;i<menuItems.size();i++) {
				    menu.add(menuItems.elementAt(i));
			    }
		    }
	    }
  		return true;
	}
    
	public void addContextMenu(Menu menu) { 
  
		Vector<String> menuItems = anyRemote.protocol.getMenu();
    	if (menuItems != null) {
		    for(int i = 0;i<menuItems.size();i++) {
			    menu.add(menuItems.elementAt(i));
		    }
	    }
 	}

	//
	// Edit field stuff
	// 

	void setupEditField(int id, String caption, String label, String defvalue) {    	
		
		log("setupEditField "+id+" "+caption);
		
		anyRemote.protocol.efCaption = caption;
		anyRemote.protocol.efLabel   = label;
		anyRemote.protocol.efValue   = defvalue;
		anyRemote.protocol.efId      = id;

		if (id > Dispatcher.CMD_NO) {
		    showDialog(id);
		}
	}

	// Got result from EditForm dialog ("Ok"/"Cancel" was pressed)
	//@Override
	public void onDismiss (DialogInterface dialog) {

		log("onDismiss");
		
		if (skipDismissEditDialog) {
			skipDismissEditDialog = false;
			return;
		}

		handleEditFieldResult(anyRemote.protocol.efId, "Ok", ((EditFieldDialog) dialog).getValue());
		
		setupEditField(Dispatcher.CMD_NO, "", "", ""); // reset values
	}

	// Handle "Cancel" press in EditFieldDialog
	//@Override
	public void onCancel(DialogInterface dialog) { 
		log("onCancel");

		skipDismissEditDialog = true;

		handleEditFieldResult(anyRemote.protocol.efId, "Cancel", "");
		
		setupEditField(Dispatcher.CMD_NO, "", "", ""); // reset values
	}

	public void handleEditFieldResult(int id, String button, String value) {
		
		// override in child classes
		switch(id){
			case Dispatcher.CMD_GETPASS:
			case Dispatcher.CMD_EFIELD:
				anyRemote.protocol.handleEditFieldResult(id, button, value);
			default:
				log("handleEditFormResult improper case");
				break;
		}
	}

	// Show Edit field dialog 
	@Override
	protected Dialog onCreateDialog(int id) {
		//log("onCreateDialog "+id);
		switch(id){
			case Dispatcher.CMD_GETPASS:
			case Dispatcher.CMD_EFIELD:
				return new EditFieldDialog(this);
		}
		return null;
	}

	// Setup "Enter address" dialog
	@Override
	protected void onPrepareDialog(int id, Dialog d) {
		//log("onPrepareDialog "+id);
		if (d == null) return;

		switch(id){
			case Dispatcher.CMD_GETPASS:
	
				((EditFieldDialog) d).setupEField(getResources().getString(R.string.label_pass),
						getResources().getString(R.string.enter_pass),
						"");
				d.setOnDismissListener(this);
				d.setOnCancelListener (this);
				break;
	
			case Dispatcher.CMD_EFIELD:
	
				((EditFieldDialog) d).setupEField(anyRemote.protocol.efCaption, 
						                          anyRemote.protocol.efLabel, 
						                          anyRemote.protocol.efValue);
				d.setOnDismissListener(this);
				d.setOnCancelListener (this);
				break;
		}		
	}
	
	//
	// Handle common operations:
	// 	Set(menu,...)
	//	Set(editfield, ...)
	//	Get(pass)
	//  Set(fullscreen,...)
	//  Set(popup,...)
	//  Set(*,close)
	//
	public boolean handleCommonCommand(int id) {
		
		boolean processed = false;
		
        if (id == Dispatcher.CMD_CLOSE) {
        	
        	log("handleCommonCommand CMD_CLOSE");
  			doFinish("close");
  			processed = true;
  
        } else if (id == Dispatcher.CMD_EFIELD) {

			showDialog(id);
			processed = true;
			
		} else if (id == Dispatcher.CMD_GETPASS) {
			
			setupEditField(id, "", "", ""); 
			processed = true;
			
		} else if (id == Dispatcher.CMD_FSCREEN)  {   	
			
			anyRemote.protocol.setFullscreen(this);
			processed = true;
			
		} else if (id == Dispatcher.CMD_POPUP) {
				
		    popup();
			processed = true;
		}
		
		return processed;
	}

	protected void doFinish(String reason) {
		log("doFinish "+reason);	
	}
	
	protected void showLog() {
	    log("showLog");
	    final Intent showLog = new Intent(getBaseContext(), TextScreen.class);
	    showLog.putExtra("SUBID", "__LOG__");
	    startActivity(showLog); 
	}
	
    protected void showMouse() {
        log("showMouse");
        final Intent showM = new Intent(getBaseContext(), MouseScreen.class);
        startActivity(showM); 
    }
 
    protected void showKbd() {
        log("showKbd");
        final Intent showK = new Intent(getBaseContext(), KeyboardScreen.class);
        startActivity(showK); 
    }
	
	public void hidePopup() {
		anyRemote.popup(this, false, true, "");
	}

	public void checkPopup() {
		if (anyRemote.protocol.popupState) {
		    anyRemote.popup(this, true, false, anyRemote.protocol.popupMsg.toString());
		}
	}

	public void popup() {
		anyRemote.popup(this, anyRemote.protocol.popupState, true, anyRemote.protocol.popupMsg.toString());
	}
}
