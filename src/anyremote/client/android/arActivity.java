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

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Menu;
import anyremote.client.android.R;
import anyremote.client.android.util.ProtocolMessage;

public class arActivity extends Activity 
		implements DialogInterface.OnDismissListener,
				   DialogInterface.OnCancelListener {

	protected Vector<String> menuItems;
	protected String prefix = "";	
	private boolean skipDismissEditDialog = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		menuItems = new Vector<String>();
		//callMenuUpdate(); do this from inherited classes
	}

	public void log(String msg) {
		anyRemote._log(prefix,msg);
	}
	
	public void handleEvent(ProtocolMessage data) {
		log("handleEvent "+" "+data.stage+" "+ data.tokens);
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

    Vector<String> getMenu() {
		return menuItems;
	}

	public void processMenu(Vector vR) {
		processMenu(vR, null, null);
	}

	public void processMenu(Vector vR, Vector persistent, Vector<String> screenDefaultMenu) {
		String oper  = (String) vR.elementAt(1); 

		if (oper.equals("clear")) {

			cleanMenu(persistent);  	    

		} else if (oper.equals("add") || oper.equals("replace")) {

			if (oper.equals("replace")) {

				cleanMenu(persistent);

				for (int idx=0;idx<screenDefaultMenu.size();idx++) {
					//log("add menu item "+screenDefaultMenu.elementAt(idx));
					menuItems.add(screenDefaultMenu.elementAt(idx));
				}
			}

			addMenu(vR, persistent);
		}
	}

	void cleanMenu(Vector persistent) {
		menuItems.clear();
		if (persistent != null) persistent.clear();
	}

	void callMenuUpdate()  { // Add predefined menu items
	}

	void addMenu(Vector from, Vector to) {   	
		for (int idx=2;idx<from.size();idx++) {
			String item = (String) from.elementAt(idx);
			if (item.length() > 0) {
				if (to != null) to.add((String) from.elementAt(idx));
				menuItems.add((String) from.elementAt(idx));
			}
		}
	}

	void restorePersistentMenu(Vector from) {
		//log("restorePersistentMenu");
		if (from == null) {
			return;
		}

		for (int idx=0;idx<from.size();idx++) {   	
			//log("restorePersistentMenu "+(String) from.elementAt(idx));
			menuItems.add((String) from.elementAt(idx));
		}
	}

	//
	// Edit field stuff
	// 

	void setupEditField(int id, String caption, String label, String defvalue) {    	

		anyRemote.protocol.efCaption = caption;
		anyRemote.protocol.efLabel   = label;
		anyRemote.protocol.efValue   = defvalue;
		anyRemote.protocol.efId      = id;

		if (id != -1) {
		    showDialog(id);
		}
	}

	// Got result from EditForm dialog ("Ok"/"Cancel" was pressed)
	@Override
	public void onDismiss (DialogInterface dialog) {

		log("onDismiss");
		if (skipDismissEditDialog) {
			skipDismissEditDialog = false;
			return;
		}

		handleEditFieldResult(anyRemote.protocol.efId, "Ok", ((EditFieldDialog) dialog).getValue());
		
		setupEditField(-1, "", "", ""); // reset values
	}

	// Handle "Cancel" press in EditFieldDialog
	@Override
	public void onCancel(DialogInterface dialog) { 
		log("onCancel");

		skipDismissEditDialog = true;

		handleEditFieldResult(anyRemote.protocol.efId, "Cancel", "");
		
		setupEditField(-1, "", "", ""); // reset values
	}

	public void  handleEditFieldResult(int id, String button, String value) {
		// override in child classes
		switch(id){
		case Dispatcher.CMD_EDIT_FORM_IP:
		case Dispatcher.CMD_EDIT_FORM_BT:
		case Dispatcher.CMD_EDIT_FORM_NAME:
		case Dispatcher.CMD_EDIT_FORM_PASS:
			log("handleEditFormvalue improper case");
			break;
		case Dispatcher.CMD_GETPASS:
		case Dispatcher.CMD_EFIELD:
			anyRemote.protocol.handleEditFieldResult(id, button, value);
		}
	}

	// Show Edit field dialog 
	@Override
	protected Dialog onCreateDialog(int id) {

		switch(id){
		case Dispatcher.CMD_EDIT_FORM_IP:
		case Dispatcher.CMD_EDIT_FORM_BT:
		case Dispatcher.CMD_EDIT_FORM_NAME:
		case Dispatcher.CMD_EDIT_FORM_PASS:
		case Dispatcher.CMD_GETPASS:
		case Dispatcher.CMD_EFIELD:
			return new EditFieldDialog(this);
		}
		return null;
	}

	// Setup "Enter address" dialog
	@Override
	protected void onPrepareDialog(int id, Dialog d) {

		if (d == null) return;

		switch(id){

		case Dispatcher.CMD_EDIT_FORM_IP:

			((EditFieldDialog) d).setupEField(getResources().getString(R.string.enter_ip_item),
					getResources().getString(R.string.enter_ip_item),
					getResources().getString(R.string.default_ip), 
					true);
			d.setOnDismissListener(this);
			d.setOnCancelListener (this);

			break;

		case Dispatcher.CMD_EDIT_FORM_BT:

			((EditFieldDialog) d).setupEField(getResources().getString(R.string.enter_bt_item),
					getResources().getString(R.string.enter_bt_item),
					getResources().getString(R.string.default_bt), 
					true);
			d.setOnDismissListener(this);
			d.setOnCancelListener (this);

			break;

		case Dispatcher.CMD_EDIT_FORM_NAME:

			((EditFieldDialog) d).setupEField(getResources().getString(R.string.enter_item_name),
					getResources().getString(R.string.enter_item_name),
					anyRemote.protocol.efValue, 
					true);
			d.setOnDismissListener(this);
			d.setOnCancelListener (this);

			break;

		case Dispatcher.CMD_EDIT_FORM_PASS:

			((EditFieldDialog) d).setupEField(getResources().getString(R.string.enter_item_pass),
					getResources().getString(R.string.enter_item_pass),
					"", 
					true);
			d.setOnDismissListener(this);
			d.setOnCancelListener (this);

			break;

		case Dispatcher.CMD_GETPASS:

			((EditFieldDialog) d).setupEField(getResources().getString(R.string.label_pass),
					getResources().getString(R.string.enter_pass),
					"", false);
			d.setOnDismissListener(this);
			d.setOnCancelListener (this);
			break;

		case Dispatcher.CMD_EFIELD:

			((EditFieldDialog) d).setupEField(anyRemote.protocol.efCaption, 
					                          anyRemote.protocol.efLabel, 
					                          anyRemote.protocol.efValue, false);
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
	public boolean handleCommonCommand(int id, Vector tokens) {
		
		boolean processed = false;
		
        if (id == Dispatcher.CMD_CLOSE) {
        	
        	log("handleCommonCommand CMD_CLOSE");
  			doFinish("close");
  			processed = true;
  			
  		} if (id == Dispatcher.CMD_MENU) {
			
			processMenu(tokens);
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
	
	public void popup() {
		anyRemote.popup(this, anyRemote.protocol.popupState, anyRemote.protocol.popupMsg.toString());
	}
}
