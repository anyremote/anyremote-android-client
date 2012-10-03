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

package anyremote.client.android.util;

import java.util.ArrayList;
import java.util.Vector;

import android.widget.ArrayAdapter;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.view.LayoutInflater;
import anyremote.client.android.R;

public class AddressAdapter extends ArrayAdapter<Address> {

	private Vector<Address> items;
	Context context;

	public AddressAdapter(Context context, int textViewResourceId, Vector<Address> items) {
		super(context, textViewResourceId, items);
		this.context = context;
		this.items   = items;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		final View v;
		if (convertView == null) {
			LayoutInflater vi = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			v = vi.inflate(R.layout.search_list_item, null);
		} else {
			v = convertView;
		}

		TextView txt = (TextView) v.findViewById(R.id.search_list_item);
		
		Address a = items.get(position);
		txt.setText(a.name);

		return v;
	}
	
	public int size() {
		return items.size();
	}
	
	public Address getItem(String name) {
		for (int i=0;i<items.size();i++) {
			Address a = items.get(i);
			if (name.compareTo(a.name) == 0) {
				return a;
			}
		}
		return null;
 	}
	
	public void remove(String name) {
		for (int i=0;i<items.size();i++) {
			Address a = items.get(i);
			if (name.compareTo(a.name) == 0) {
				remove(a);
				return;
			}
		}
    }

	public boolean addIfNew(String name, String host, String pass) {
		if (name == null || host == null) {
			return false;
		}

		for (int i=0;i<items.size();i++) {
			Address a = items.get(i);
			if (name.compareTo(a.name) == 0) {
				return false;
			}
		}
		
		Address a = new Address();
		a.name = name;
		a.URL  = host;
		a.pass = pass;		
		add(a);
		
		return true;
	}
}
