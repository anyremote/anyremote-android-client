//
// anyRemote android client
// a bluetooth/wi-fi remote control for Linux.
//
// Copyright (C) 2011-2016 Mikhail Fedotov <anyremote@mail.ru>
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
//

package anyremote.client.android;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import anyremote.client.android.R;

public class AddressDialog extends Dialog implements OnClickListener {
	
	Button   okButton;
	Button   cancelButton;
	EditText name = null;
	EditText addr;
	EditText pass;
	CheckBox auto;

	public AddressDialog(Context context) {
		super(context);
	}
	
	public void setupDialog(String n, String a, String p, boolean autoConnect) {
    	
    	if (name == null) {
    		setupDialog();		
    	}
    	setTitle(R.string.label_addr);
    	
     	name.setText(n);
     	addr.setText(a);
     	pass.setText(p);
     	auto.setChecked(autoConnect);
    }
	
	private void setupDialog() {
		
	    okButton     = (Button) findViewById(R.id.editAddrButtonOk);
	    cancelButton = (Button) findViewById(R.id.editAddrButtonCancel);
	    name    = (EditText) findViewById(R.id.edit_name);
	    addr    = (EditText) findViewById(R.id.edit_addr);
	    pass    = (EditText) findViewById(R.id.edit_pass);
	    auto    = (CheckBox) findViewById(R.id.autoConnect);
	    
	    // setup listener
	    okButton.setOnClickListener(this);
	    cancelButton.setOnClickListener(this);
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setContentView(R.layout.peer_edit);
	
    	if (name == null) {
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
	
	public String getPeerName(){
		return name.getText().toString();
	}
	
	public String getPeerAddress(){
		return addr.getText().toString();
	}
	
	public String getPeerPassword(){
		return pass.getText().toString();
	}
	
	public boolean getPeerAutoConnect(){
		return auto.isChecked();
	}
}
