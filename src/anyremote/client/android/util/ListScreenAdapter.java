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

import android.widget.ArrayAdapter;
import android.widget.ImageView.ScaleType;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.LayoutInflater;
import anyremote.client.android.anyRemote;
import anyremote.client.android.R;

public class ListScreenAdapter extends ArrayAdapter<ListItem> {

	
	private ArrayList<ListItem> items;
	private String  icon;
	private Context context;
	private int selectedPosition = -1; //0;
	private boolean customTextColor = false;
	private boolean customBackColor = false;	
	private int textColor = -1; //Color.rgb(255,255,255);
	private int backColor = -1; //Color.rgb(0,0,0);
	private float fSize   = -1;
		
	public ListScreenAdapter(Context context, int textViewResourceId, ArrayList<ListItem> items) {
		super(context, textViewResourceId, items);
		this.context = context;
		synchronized (items) {
			this.items   = items;
		}
	}
	
	public void update(ArrayList<ListItem> data, String defIcon) {
		clear();
		synchronized (items) {
		   	for(ListItem item : data) {
	    	    items.add(item);
	        }
		   	icon = defIcon;
		}
	   	//anyRemote._log("ListScreenAdapter","update #"+items.size());
	}

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
    	
    	//anyRemote._log("ListScreenAdapter","getView "+position);
    	
      	final View v;
    	if (convertView == null) {
    		LayoutInflater vi = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    		v = vi.inflate(R.layout.list_form_item, null);
    	} else {
    		v = convertView;
      	}
 
       	int bColor =  (position == selectedPosition ? textColor : backColor);
       	int tColor =  (position == selectedPosition ? backColor : textColor);
    	
    	ImageView im = (ImageView) 	v.findViewById(R.id.list_item_icon);
    	TextView txt = (TextView)   v.findViewById(R.id.list_item_text);

    	im.setAdjustViewBounds(true);
    	im.setMaxHeight(2+(int) txt.getTextSize());    	
    	im.setMaxWidth (2+(int) txt.getTextSize());
    	im.setScaleType(ScaleType.CENTER_INSIDE);
    	
    	synchronized (items) {
    		
    		String icon_name = (icon.length() > 0 && !icon.equals("none") ? 
    				            icon : items.get(position).icon);
    		
	    	Bitmap iconBM = (icon_name == null ?  
	    			         null : anyRemote.getIconBitmap(im.getResources(),icon_name));
	    	if (iconBM == null) {
	     		im.setVisibility(View.GONE);
	    	} else {
	     		im.setVisibility(View.VISIBLE);
	     	    im.setImageBitmap(iconBM);
	     	    if (customTextColor && customBackColor) {
	     	        im.setBackgroundColor(bColor);
	     	    }
	    	}
	    	//anyRemote._log("ListScreenAdapter","getView "+items.get(position).text);
	    	
	    	txt.setText(items.get(position).text);
	    	txt.setVisibility(View.VISIBLE);
	    	v.setVisibility(View.VISIBLE);
    	}
    	
    	if (fSize > 0) {
 	        txt.setTextSize(fSize);
	    }
      	if (customTextColor && customBackColor) {
    	    txt.setBackgroundColor(bColor);
            txt.setTextColor(tColor);
    	}

    	return v;
    }
    
	public int size() {
		return items.size();
	}
   
    public void setFont(float size) {
    	fSize = size;
	}
   
    public void setSelectedPosition(int position) {
	    selectedPosition = position;
	}
    
    public void setTextColor(int color) {
       	customTextColor = true;
   	    textColor = color;
    }
    
    public void setBackColor(int color) {
    	customBackColor = true;
    	backColor = color;
    }
  
    public void clear() {
    	synchronized (items) {
     	    items.clear();
    	}
    }
}
