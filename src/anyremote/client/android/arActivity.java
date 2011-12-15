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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import anyremote.client.android.R;

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
			if (to != null) to.add((String) from.elementAt(idx));
			menuItems.add((String) from.elementAt(idx));
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

	void openEditField(int id, String caption, String label, String defvalue) {    	

		anyRemote.efCaption = caption;
		anyRemote.efLabel   = label;
		anyRemote.efValue   = defvalue;
		anyRemote.efId      = id; 

		showDialog(id);
	}

	// Got result from EditForm dialog ("Ok"/"Cancel" was pressed)
	@Override
	public void onDismiss (DialogInterface dialog) {

		log("onDismiss");
		if (skipDismissEditDialog) {
			skipDismissEditDialog = false;
			return;
		}

		handleEditFieldResult(anyRemote.efId, "Ok", ((EditFieldDialog) dialog).getValue());

		anyRemote.efId      = -1;
		anyRemote.efCaption = "";
		anyRemote.efLabel   = "";
		anyRemote.efValue   = "";
	}

	// Handle "Cancel" press in EditFieldDialog
	@Override
	public void onCancel(DialogInterface dialog) { 
		log("onCancel");

		skipDismissEditDialog = true;

		handleEditFieldResult(anyRemote.efId, "Cancel", "");

		anyRemote.efId      = -1;
		anyRemote.efCaption = "";
		anyRemote.efLabel   = "";
		anyRemote.efValue   = "";
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
					anyRemote.efValue, 
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

			((EditFieldDialog) d).setupEField(anyRemote.efCaption, anyRemote.efLabel, anyRemote.efValue, false);
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
	//  Set(wait,...)
	//
	public boolean handleCommonCommand(int id, Vector tokens) {
		
		boolean processed = false;
		
		if (id == Dispatcher.CMD_MENU) {
			
			processMenu(tokens);
			processed = true;
			
		} else if (id == Dispatcher.CMD_EFIELD) {
		
			openEditField(id,
					(String) tokens.elementAt(1),
					(String) tokens.elementAt(2),
					(String) tokens.elementAt(3));	
			processed = true;
			
		} else if (id == Dispatcher.CMD_GETPASS) {
			
			openEditField(id,"","","");
			processed = true;
			
		} else if (id == Dispatcher.CMD_FSCREEN)  {   	
			
			anyRemote.protocol.setFullscreen((String) tokens.elementAt(1), this);
			processed = true;
			
		} else if (id == Dispatcher.CMD_POPUP) {
				
		    showPopup(tokens);
			processed = true;
		}
		
		return processed;
	}
	
	public void dismissPopup() {
		anyRemote.protocol.popupState = false;
		anyRemote.protocol.popupMsg.delete(0, anyRemote.protocol.popupMsg.length());
	}
	
	public void showPopup(Vector tokens) {
		
		String op = (String) tokens.elementAt(1);
		
		dismissPopup();
		
		if (op.equals("show")) { 

			anyRemote.protocol.popupState = true;
			
			for (int i=2;i<tokens.size();i++) {
				if (i != 2) {
					anyRemote.protocol.popupMsg.append(", ");
				}
				anyRemote.protocol.popupMsg.append((String) tokens.elementAt(i));
			}
		}
		
		popup();
	}
	
	public void popup() {
		anyRemote.popup(this, anyRemote.protocol.popupState, anyRemote.protocol.popupMsg.toString());
	}
}
