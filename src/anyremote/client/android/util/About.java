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

package anyremote.client.android.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Html;
import android.text.util.Linkify;
import android.graphics.Color;
import android.widget.TextView;

public class About extends Dialog {

	private static Context mContext = null;

	public About(Context context) {
		super(context);
		mContext = context;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		
		setContentView(anyremote.client.android.R.layout.about);
		
		TextView tv = (TextView) findViewById(anyremote.client.android.R.id.legal_text);
		
		tv.setText(Html.fromHtml(readRawTextFile(anyremote.client.android.R.raw.legal)));
		tv = (TextView) findViewById(anyremote.client.android.R.id.info_text);
		tv.setText(Html.fromHtml(readRawTextFile(anyremote.client.android.R.raw.info)));
		tv.setLinkTextColor(Color.WHITE);
		
		Linkify.addLinks(tv, Linkify.ALL);
	}

	public static String readRawTextFile(int id) {
		
		InputStream inputStream = mContext.getResources().openRawResource(id);
		
		InputStreamReader in = new InputStreamReader(inputStream);
		
		BufferedReader buf = new BufferedReader(in);
		
		String line;
		StringBuilder text = new StringBuilder();
		
		try {
			while ((line = buf.readLine()) != null)
				text.append(line);
		} catch (IOException e) {
			return null;
		}
		return text.toString();
	}
}
