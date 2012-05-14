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

import java.util.ArrayList;
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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import anyremote.client.android.R;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
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

	ArrayList<String> addresses;
	Vector<Address> addressesA;
	int selected = -1;

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

		loadPrefs();

		addresses = new ArrayList<String>();
		for (int i = 0;i< addressesA.size();i++) {		
			addresses.add(addressesA.elementAt(i).name);
		}

		dataSource = new AddressAdapter(this, R.layout.search_list_item, addresses);
		searchList.setAdapter(dataSource);

		searchList.setOnItemSelectedListener(this);

		// Register for broadcasts when a device is discovered
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		this.registerReceiver(mReceiver, filter);

		// Register for broadcasts when discovery has finished
		filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		this.registerReceiver(mReceiver, filter);

		// Get the local Bluetooth adapter
		mBtAdapter = BluetoothAdapter.getDefaultAdapter();
		
		if (addresses.size() == 0) { // first-time run
			Toast.makeText(this, "Press Menu ...", Toast.LENGTH_SHORT).show();
		}
	}

	//@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {		
		doConnect(dataSource.getItem(arg2),anyRemote.CONNECT_TO);
	}

	public void loadPrefs() {

		addressesA = anyRemote.protocol.loadPrefs();
		for( int i = 0; i < addressesA.size(); i++) {
			if (addressesA.elementAt(i).name.compareTo("__AUTOCONNECT__") == 0) {
				doConnect(addressesA.elementAt(i).URL, anyRemote.AUTOCONNECT_TO);
			}
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

				if (dataSource.addIfNew(value)) {
					addAddress(value,connectTo,connectPass);
				}
			}
			return;	
		}

		if (id == Dispatcher.CMD_EDIT_FORM_PASS) { 

			addAddress(connectName,connectTo,value);
			return;
		}

		// BT or IP address goes here
		if (value.length() == 0) return;
		
		// Format control
		if (id == Dispatcher.CMD_EDIT_FORM_BT) { // value.startsWith("btspp:"))
			 
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

		if (dataSource.addIfNew(value)) {
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
		this.unregisterReceiver(mReceiver);
		if (deregStateRcv) {
			this.unregisterReceiver(mBTStateReceiver);
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

		final String address = dataSource.getItem(info.position);

		switch (item.getItemId()) {

		case R.id.connect_to:

			doConnect(address,anyRemote.CONNECT_TO);
			break;

		case R.id.autoconnect_to:

			doConnect(address,anyRemote.AUTOCONNECT_TO);
			break;

		case R.id.remove_address:

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
				if (dataSource.addIfNew(device.getName())) {
					addAddress(device.getName(),"btspp://"+device.getAddress(),"");
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
				final String address = dataSource.getItem(selected);
				doConnect(address,anyRemote.CONNECT_TO);
			}		    
			break;

		case R.id.autoconnect_item:

			if (selected >= 0) {
				final String address = dataSource.getItem(selected);
				doConnect(address,anyRemote.AUTOCONNECT_TO);
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
				final String address = dataSource.getItem(selected);

				// get URL by device name
				String url = getURL(address);
				if (url == null) {
					log("onOptionsItemSelected: enter_item_name can not get URL for "+address);
					return true;
				}

				connectPass = getPass(address);
				if (connectPass == null) {
					connectPass = "";
				}

				connectTo   = url;
				connectName = address;

				setupEditField(Dispatcher.CMD_EDIT_FORM_NAME, null, null, address);
			}			    
			break;

		case R.id.enter_item_pass:

			stopBluetoothDiscovery();

			if (selected >= 0) {
				final String address = dataSource.getItem(selected);

				// get URL by device name
				String url = getURL(address);
				if (url == null) {
					log("onOptionsItemSelected: enter_item_pass can not get URL for "+address);
					return true;
				}

				connectPass = getPass(address);
				if (connectPass == null) {
					connectPass = "";
				}

				connectTo   = url;
				connectName = address;

				setupEditField(Dispatcher.CMD_EDIT_FORM_PASS, null, null, null);
			}			    
			break;

		case R.id.exit_item:

			stopBluetoothDiscovery();

			final Intent intente = new Intent();
			intente.putExtra(anyRemote.ACTION, "exit");
			setResult(RESULT_OK, intente);
			finish();
			break;

		case R.id.clean_item:

			if (selected >= 0) {
				final String address = dataSource.getItem(selected);
				cleanAddress(address);
				dataSource.remove(address);
			}
			break;	

		case R.id.log_item:

			stopBluetoothDiscovery();

			final Intent intentl = new Intent();
			intentl.putExtra(anyRemote.ACTION, "log");
			setResult(RESULT_OK, intentl);
			finish();
			break;			
		}
		return true;
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

		// get URL by device name
		String url = getURL(address);
		if (url == null) {
			log("doConnect: can not get URL for "+address);
			return;
		}
		log("doConnect: address is "+url);

		connectPass = getPass(address);
		if (connectPass == null) {
			connectPass = "";
		}

		connectTo   = url;
		connectName = address;

		connectOpts = option;
		if (connectTo.startsWith("btspp:") && 
				mBtAdapter != null &&  !mBtAdapter.isEnabled()) {
			btUseFlag = BT_USE_SEARCH;
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

	public void cleanAddress(String name) {
		//log("cleanAddress "+name);
		for (int i=0;i<addressesA.size();i++) {
			if (name.compareTo(addressesA.get(i).name) == 0) {
				addressesA.remove(i);
				break;
			}
		}
		anyRemote.protocol.cleanAddress(name);
	}

	public String getURL(String name) {
		for (int i=0;i<addressesA.size();i++) {
			if (name.compareTo(addressesA.get(i).name) == 0) {
				return addressesA.get(i).URL;
			}
		}
		return null;
	}

	public String getPass(String name) {
		for (int i=0;i<addressesA.size();i++) {
			if (name.compareTo(addressesA.get(i).name) == 0) {
				return addressesA.get(i).pass;
			}
		}
		return null;
	}

	// save new address in preferences
	public void addAddress(String name, String URL, String pass) {		        
		//log("addAddress "+name+"/"+URL+"/"+pass);

		Address a = new Address();
		a.name  = name;
		a.URL   = URL;
		a.pass  = pass;
		addressesA.add(a);

		anyRemote.protocol.addAddress(name,  URL, pass);
	}
}
