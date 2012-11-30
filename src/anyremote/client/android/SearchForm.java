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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.Activity;
import android.os.Bundle;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.Toast;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import anyremote.client.android.R;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import anyremote.client.android.util.About;
import anyremote.client.android.util.Address;
import anyremote.client.android.util.AddressAdapter;

public class SearchForm extends arActivity 
                        implements OnItemClickListener,
                                   DialogInterface.OnDismissListener,
                                   DialogInterface.OnCancelListener,
                                   AdapterView.OnItemSelectedListener {

	static final int  BT_USE_NO      = 0;
	static final int  BT_USE_SEARCH  = 1;
	static final int  BT_USE_CONNECT = 2;

	ListView searchList;
	AddressAdapter dataSource;
	int selected = 0;

	private BluetoothAdapter mBtAdapter;

	// BT stuff
	int btUseFlag = BT_USE_NO;
	String connectTo   = "";
	String connectName = "";
	String connectPass = "";
	String connectOpts = "";
	boolean deregStateRcv = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		prefix = "SearchForm"; // log stuff
		log("onCreate");

		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.search_dialog);
		setResult(Activity.RESULT_CANCELED);

		setTitle(R.string.searchFormUnconnected);

		searchList = (ListView) findViewById(R.id.search_list);

		searchList.setOnItemClickListener(this); 

		registerForContextMenu(searchList);

		dataSource = new AddressAdapter(this, R.layout.search_list_item, anyRemote.protocol.loadPrefs());
		searchList.setAdapter(dataSource);
		searchList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		searchList.setOnItemSelectedListener(this);
		//searchList.setItemChecked(selected, true); 

		// Register for broadcasts when a device is discovered
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		this.registerReceiver(mReceiver, filter);

		// Register for broadcasts when discovery has finished
		filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		this.registerReceiver(mReceiver, filter);

		// Get the local Bluetooth adapter
		mBtAdapter = BluetoothAdapter.getDefaultAdapter();

		if (dataSource.size() == 0) { // first-time run
			Toast.makeText(this, "Press Menu ...", Toast.LENGTH_SHORT).show();
		} else {
	        Address auto = dataSource.getItem("__AUTOCONNECT__");
			if (auto != null) {
				doConnect(auto.URL, anyRemote.AUTOCONNECT_TO);
			}
		}
	}

	//@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		Address a = dataSource.getItem(arg2);
		if (a != null) {
		    doConnect(a.name,anyRemote.CONNECT_TO);
	    }
	}

	public void  handleEditFieldResult(int id, String button, String value) {
				
		if (button.equals("Cancel")) return;
		
		if (value.length() > 0 && value.charAt(value.length() - 1) == '\n') {
			value = value.substring(0, value.length() - 1);
		}
		if (value.length() > 0 && value.charAt(0) == '\n') {
			value = value.substring(1, value.length());
		}

		if (id == Dispatcher.CMD_EDIT_FORM_NAME) { 

			if (value.length() == 0) return;
			
			if (value != connectName) {

				cleanAddress(connectName);
				dataSource.remove(connectName);

				if (dataSource.addIfNew(value,connectTo,connectPass)) {
					addAddress(value,connectTo,connectPass);
				}
			}
			return;	
		}

		if (id == Dispatcher.CMD_EDIT_FORM_PASS) { 
			
			if (dataSource.addIfNew(connectName,connectTo,value)) {
			    addAddress(connectName,connectTo,value);
			}
			return;
		}

		// BT or IP address goes here
		if (value.length() == 0) return;
		
		// Format control
		if (id == Dispatcher.CMD_EDIT_FORM_BT) { 
			 
			// Samsung's does allows BT address only in capital
			StringBuffer baddr = new StringBuffer("btspp://");

			baddr.append(value.substring(8).
					replace('a','A').replace('b','B').
					replace('c','C').replace('d','D').
					replace('e','E').replace('f','F'));

			int i = 8;
			while (i<baddr.length()-3) {
				if (baddr.charAt(i) == ':') {
					baddr.deleteCharAt(i); 
				} else {
					i++;
				}
			}		       
			value = new String(baddr);	       
		}

		if (dataSource.addIfNew(value,value,"")) {
			addAddress(value,value,"");
		}
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();

		// Make sure we're not doing discovery anymore
		if (btUseFlag != BT_USE_NO) {
			cancelSearch(false);
		}

		// Unregister broadcast listeners
		unregisterReceiver(mReceiver);
		if (deregStateRcv) {
			unregisterReceiver(mBTStateReceiver);
		}
	}

	// Handle long-click
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		if (v.getId() == R.id.search_list) {
			final AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
			final TextView tv= (TextView) info.targetView.findViewById(R.id.search_list_item);

			menu.setHeaderTitle(tv.getText());
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.search_menu2, menu);
		}
	}

	// Handle context menu, opened by long-click
	@Override
	public boolean onContextItemSelected(MenuItem item) {

		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		
		Address a = dataSource.getItem(info.position);
		final String address = a.name;

		switch (item.getItemId()) {

		case R.id.connect_to:

			doConnect(address,anyRemote.CONNECT_TO);
			break;

		case R.id.autoconnect_to:

			doConnect(address,anyRemote.AUTOCONNECT_TO);
			break;
			
		case R.id.enter_item_name:

			renameAddress(address);
			break;

		case R.id.clean_item:

			cleanAddress(address);
			dataSource.remove(address);
			break;

		}
		return super.onContextItemSelected(item);
	}

	@Override
	public void onBackPressed() {

		if (btUseFlag != BT_USE_NO) {
			cancelSearch(false);
		}

		final Intent intent = new Intent();  
		intent.putExtra(anyRemote.CONNECT_TO, "");

		setResult(RESULT_OK, intent);

		finish();
		super.onBackPressed();
	}

	private void doDiscovery() {
		if (mBtAdapter == null) {
			return;
		}
		if (!mBtAdapter.isEnabled()) {
			btUseFlag = BT_USE_SEARCH;
			switchBluetoothOn();
		} else {       
			doRealDiscovery();
		}
	}

	private void doRealDiscovery() {
		log("doRealDiscovery");
		btUseFlag = BT_USE_NO;

		// Indicate scanning in the title
		setProgressBarIndeterminateVisibility(true);
		setTitle(R.string.searching);

		// If we're already discovering, stop it
		cancelSearch(true);

		// Request discover from BluetoothAdapter
		mBtAdapter.startDiscovery();
	}

	// The BroadcastReceiver that listens for discovered devices and
	// changes the title when discovery is finished
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			log("BroadcastReceiver::onReceive discovery ");

			String action = intent.getAction();

			// When discovery finds a device
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {

				// Get the BluetoothDevice object from the Intent
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				log("BroadcastReceiver::onReceive discovery GOT ONE "+device.getName()+" "+device.getAddress());

				// If it's already paired, skip it, because it's been listed already
				//if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
					if (dataSource.addIfNew(device.getName(), "btspp://"+device.getAddress(), "")) {
						//addAddress(device.getName(),"btspp://"+device.getAddress(),"");
					}
				//}

				// When discovery is finished, change the Activity title
			} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {

				log("BroadcastReceiver::onReceive discovery FINISHED");
				cancelSearch(false);

				/*if (mNewDevicesArrayAdapter.getCount() == 0) {
                    String noDevices = getResources().getText(R.string.not_found).toString();
                    mNewDevicesArrayAdapter.add(noDevices);
                }*/
			}
		}
	};


	// The BroadcastReceiver that handles BT state
	private final BroadcastReceiver mBTStateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			log("BroadcastReceiver::onReceive state");

			String stateExtra = BluetoothAdapter.EXTRA_STATE;
			int state = intent.getIntExtra(stateExtra, -1);
			switch (state) {
				case (BluetoothAdapter.STATE_TURNING_ON) : {
					setTitle(R.string.bt_turning_on);
					break;
				}
				case (BluetoothAdapter.STATE_ON) : {
					log("BroadcastReceiver::onReceive state ON");
					setTitle(R.string.bt_on);
					unregisterReceiver(this);
					deregStateRcv = false;
	
					if (btUseFlag == BT_USE_SEARCH) {
						doRealDiscovery();
					} else if (btUseFlag == BT_USE_CONNECT) {
						doRealConnect();
					}
					break;
				}
				case (BluetoothAdapter.STATE_TURNING_OFF) : {
					setTitle(R.string.bt_turning_off);
					break;
				}
				case (BluetoothAdapter.STATE_OFF) : {
					log("BroadcastReceiver::onReceive state OFF");
					setTitle(R.string.bt_off);
					unregisterReceiver(this);
					deregStateRcv = false;
					break;
				}
			}
		}
	};

	public void cancelSearch(boolean onlyCancel) { 
		log("cancelSearch");
		stopBluetoothDiscovery();
		if (!onlyCancel) {
			setProgressBarIndeterminateVisibility(false);
			setTitle(R.string.searchFormUnconnected);
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) { 
		menu.clear();
		MenuInflater mi = getMenuInflater();		
		mi.inflate(R.menu.search_menu, menu);
		return true;
	}

	// Selection handlers
	//@Override
	public void onItemSelected(AdapterView<?> parentView, View childView, int position, long id) {
		setSelected(position);
	}

	//@Override
	public void onNothingSelected(AdapterView<?> parentView) {
		setSelected(-1);
	}

	public void setSelected(int pos) {
		//log("setSelected "+pos);
		selected = pos;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch(item.getItemId()) {

		case R.id.connect_item:

			if (selected >= 0) {
				Address a = dataSource.getItem(selected);
				if (a != null) {
				    doConnect(a.name,anyRemote.CONNECT_TO);
				}
			}		    
			break;

		case R.id.autoconnect_item:

			if (selected >= 0) {
				Address a = dataSource.getItem(selected);
				if (a != null) {
					 doConnect(a.name,anyRemote.AUTOCONNECT_TO);
				}
			}			    
			break;

		case R.id.search_item:

			doDiscovery();
			break;

		case R.id.cancel_search_item:

			stopBluetoothDiscovery();
			break;

		case R.id.enter_bt_item:

			stopBluetoothDiscovery();
			setupEditField(Dispatcher.CMD_EDIT_FORM_BT, null, null, null);
			break;

		case R.id.enter_ip_item:

			stopBluetoothDiscovery();
			setupEditField(Dispatcher.CMD_EDIT_FORM_IP, null, null, null);
			break;

		case R.id.enter_item_name:

			stopBluetoothDiscovery();

			if (selected >= 0) {
				Address a = dataSource.getItem(selected);
				if (a != null) {
                    renameAddress(a.name);
				}
			}			    
			break;

		case R.id.enter_item_pass:

			stopBluetoothDiscovery();

			if (selected >= 0) {
				Address a = dataSource.getItem(selected);
				if (a != null) {
					// get URL by device name
					String url = a.URL;
					if (url == null) {
						log("onOptionsItemSelected: enter_item_pass can not get URL for "+a.name);
						return true;
					}
	
					connectPass = a.pass;
					if (connectPass == null) {
						connectPass = "";
					}
	
					connectTo   = url;
					connectName = a.name;
	
					setupEditField(Dispatcher.CMD_EDIT_FORM_PASS, null, null, null);
				}
			}			    
			break;

		case R.id.exit_item:

			doExit();
			break;

		case R.id.clean_item:

			if (selected >= 0) {
				Address a = dataSource.getItem(selected);
				if (a != null) {
					cleanAddress(a.name);
					dataSource.remove(a);
				}
			}
			break;	

		case R.id.log_item:

			stopBluetoothDiscovery();

			final Intent intentl = new Intent();
			intentl.putExtra(anyRemote.ACTION, "log");
			setResult(RESULT_OK, intentl);
			finish();
			break;	
			
	    case R.id.about_item:
	    	
	    	About about = new About(this);
	    	about.setTitle(R.string.about);
	    	about.show();	    	
		}
		
		return true;
	}
	
	public void doExit() { 
		
		stopBluetoothDiscovery();
	
		final Intent intente = new Intent();
		intente.putExtra(anyRemote.ACTION, "exit");
		setResult(RESULT_OK, intente);
		finish();
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) { 
		
		log("onKeyUp: "+keyCode);
		switch (keyCode) {
		
		    case  KeyEvent.KEYCODE_BACK:
			    doExit();
			    return true;
	        
		    case KeyEvent.KEYCODE_VOLUME_UP:  
		    	
		    	if (selected > 0) {
		    		selected--;
		    	}
		    	searchList.setSelection(selected);
	        	return true;
	        
	        case KeyEvent.KEYCODE_VOLUME_DOWN: 
	        	
	        	if (selected < dataSource.size() - 1) {
	        	    selected++;
	        	}
	        	searchList.setSelection(selected);

	        	return true;
        }
	    return false;
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) { 
		
		switch (keyCode) {
		    case KeyEvent.KEYCODE_VOLUME_UP:  
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            	return true;
		}
		return false;
	}
	
	public void stopBluetoothDiscovery() {
		log("stopBluetoothDiscovery");
		if (mBtAdapter != null && mBtAdapter.isDiscovering()) {
			log("stopBluetoothDiscovery: cancelDiscovery");
			mBtAdapter.cancelDiscovery();
		}
	}

	public void switchBluetoothOn() {
		log("switchBluetoothOn");
		String actionStateChanged = BluetoothAdapter.ACTION_STATE_CHANGED;
		String actionRequestEnable = BluetoothAdapter.ACTION_REQUEST_ENABLE;
		deregStateRcv = true;
		registerReceiver(mBTStateReceiver, new IntentFilter(actionStateChanged));
		startActivityForResult(new Intent(actionRequestEnable), 0);
	}

	public void doConnect(String address, String option) {
		log("doConnect:  "+address);

		Address a = dataSource.getItem(address);
		if (a == null) {
			log("doConnect: can not get information for "+address);
			return;
		}
		log("doConnect: host is "+a.URL);

		connectTo   = a.URL;
		connectName = address;
		connectPass = a.pass;

		connectOpts = option;

		if (connectTo.startsWith("btspp:") && mBtAdapter != null &&  !mBtAdapter.isEnabled()) {
			btUseFlag = BT_USE_CONNECT;
			switchBluetoothOn();
		} else {   
			doRealConnect();
		}
	}

	public void doRealConnect() {
		//log("doRealConnect");
		if (btUseFlag != BT_USE_NO) {
			btUseFlag = BT_USE_NO;
			cancelSearch(false);
		}

		log("doRealConnect: address is "+connectTo);
		if (connectOpts.contentEquals(anyRemote.CONNECT_TO)) {
			cleanAddress("__AUTOCONNECT__"); // clean auto-connect flag if user uses manual connect
		}

		// be sure peer is stored
		addAddress(connectName,connectTo,connectPass);
		
		final Intent intent = new Intent();  

		intent.putExtra(connectOpts, connectTo);
		intent.putExtra(anyRemote.CONN_NAME, connectName);
		intent.putExtra(anyRemote.CONN_PASS, connectPass);

		setResult(RESULT_OK, intent);
		connectTo   = "";
		connectName = "";
		connectPass = "";
		connectOpts = "";
		
		log("SearchForm::doRealConnect: finish");
		finish();
	}
	
	public void renameAddress(String address) {
		
		Address a = dataSource.getItem(address);

		if (a == null) {
			log("onOptionsItemSelected: enter_item_name can not get info for "+address);
			return;
		}

		connectName = address;
		connectTo   = a.URL;
		connectPass = a.pass;
	
		setupEditField(Dispatcher.CMD_EDIT_FORM_NAME, null, null, address);
	}

	public void cleanAddress(String name) {
		//log("cleanAddress "+name);
		anyRemote.protocol.cleanAddress(name);
	}

	// save new address in preferences
	public void addAddress(String name, String URL, String pass) {		        
		//log("addAddress "+name+"/"+URL+"/"+pass);

		/*Address a = new Address();
		a.name  = name;
		a.URL   = URL;
		a.pass  = pass;
		addressesA.add(a);*/

		anyRemote.protocol.addAddress(name, URL, pass);
	}
}
