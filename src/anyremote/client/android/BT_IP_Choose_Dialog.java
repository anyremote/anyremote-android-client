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

package anyremote.client.android;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RadioButton;

public class BT_IP_Choose_Dialog extends Dialog  implements OnClickListener {

	Button   okButton = null;
	Button   cancelButton;
	int      opId;
	
	public BT_IP_Choose_Dialog(Context context, int ident) {
		super(context);
		opId = ident;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		setContentView(R.layout.bt_ip_choose);
			
    	if (okButton == null) {
    		setupDialog();		
    	}
	}

	//@Override
    public void onClick(View v) {
		if (v == okButton) {
			this.dismiss();
		}		
		if (v == cancelButton) {
			this.cancel();
		}
	}
	
	private void setupDialog() {
		
	   setTitle(R.string.search_item);
	   
	    okButton     = (Button) findViewById(R.id.btipDialogButtonOk);
	    cancelButton = (Button) findViewById(R.id.btipDialogButtonCancel);
	    
	    if (opId == Dispatcher.CMD_NEW_ADDR_DIALOG) {
	    	setTitle(R.string.label_addr);
	    	okButton.setText(R.string.btnOk);
	    } else {
	    	setTitle(R.string.search_item);
	    	okButton.setText(R.string.search_item);
	    }

	    // setup listener
	    okButton.setOnClickListener(this);
	    cancelButton.setOnClickListener(this);
	}	
	
	public boolean isBluetooth() {
		RadioButton rb = (RadioButton) findViewById(R.id.BTCheckbox);
		return rb.isChecked();
	}
	
	public int id() {
		return opId;
	}
}
