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

import android.content.Context;
import android.app.Dialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.view.View;
import android.view.View.OnClickListener;
import anyremote.client.android.R;

public class EditFieldDialog extends Dialog implements OnClickListener {
	
	Button   okButton;
	Button   cancelButton;
	EditText editField;
	TextView label;

	boolean     internal;
    
	public EditFieldDialog(Context context) {
		super(context);
	}
	
	public void setupEField(String caption, String lbl, String initValue, boolean usedBySearch) {
    	
		internal = usedBySearch;

    	if (label == null) {
    		setupDialog();		
    	}
    	
    	setTitle(caption);
    	label.setText(lbl);
    	editField.setText(initValue);
    }
	
	private void setupDialog() {
		
	   okButton     = (Button) findViewById(R.id.EditDialogButtonOk);
	   cancelButton = (Button) findViewById(R.id.EditDialogButtonCancel);
	   editField    = (EditText) findViewById(R.id.EditDialogEditText);
	   label        = (TextView) findViewById(R.id.EditDialogLabel);

	    // setup listener
	    okButton.setOnClickListener(this);
	    cancelButton.setOnClickListener(this);
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setContentView(R.layout.edit_dialog);
	
    	if (label == null) {
    		setupDialog();		
    	}
	}
	
	@Override
	public void onClick(View v) {
		
		if (v == okButton) {
			this.dismiss();
		}
		
		if (v == cancelButton) {
			this.cancel();
		}
	}
	
	public String getValue(){
		return editField.getText().toString();
	}

}
