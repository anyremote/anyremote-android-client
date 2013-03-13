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

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.Activity;
import android.app.Dialog;
import android.os.AsyncTask;
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
                                   //DialogInterface.OnDismissListener,
                                   //DialogInterface.OnCancelListener,
                                   AdapterView.OnItemSelectedListener {

	static final int  BT_USE_NO      = 0;
	static final int  BT_USE_SEARCH  = 1;
	static final int  BT_USE_CONNECT = 2;
	
	static final String  DEFAULT_IP_PORT = "5197";

	ListView searchList;
	AddressAdapter dataSource;
	int selected = 0;
	
	// IP search
	Integer asyncNum = new Integer(-1);
	ArrayList<String> hosts = new ArrayList<String>();
	PingTask ipSearchTask = null;;

	private BluetoothAdapter mBtAdapter;

	// BT stuff
	int btUseFlag = BT_USE_NO;
	String connectTo   = "";
	String connectName = "";
	String connectPass = "";
	boolean connectAuto = false;
	
	boolean skipDismissDialog = false;
	boolean deregStateRcv = false;
	
	String id;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		prefix = "SearchForm"; // log stuff
		
		Intent  intent = getIntent();
		id = intent.getStringExtra("SUBID");
		
		log("onCreate " + id);

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
			if (anyRemote.firstConnect) {
				anyRemote.firstConnect = false;
		        final Address auto = dataSource.getAutoconnectItem();
				if (auto != null) {
					log("onCreate: autoconnect to "+auto.name);
					doConnect(auto);
				}
			} else {
			    
				if (anyRemote.protocol.currentConnName.length() > 0) {
					
					final Address conn = dataSource.getItem(anyRemote.protocol.currentConnName);
					if (conn != null) {
						log("onCreate: resume connect to "+conn.name);
						doConnect(conn);
					}
				}
			}
			
		}
	}

	@Override
	protected void onPause() {
		log("onPause "+id);	
	    super.onPause();	
	}

	@Override
	protected void onResume() {
		log("onResume "+id);		
		super.onResume();
	}
	
	@Override
	protected void onStop() {
		log("onStop "+id);		
	    super.onStop();	
	}

	@Override
	protected void onDestroy() {
		log("onDestroy "+id);

		// Make sure we're not doing discovery anymore
		cancelSearch(false);

		// Unregister broadcast listeners
		unregisterReceiver(mReceiver);
		if (deregStateRcv) {
			unregisterReceiver(mBTStateReceiver);
		}
		super.onDestroy();
	}

	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		Address a = dataSource.getItem(arg2);
		if (a != null) {
		    doConnect(a);
	    }
	}

	// Handle long-click
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		if (v.getId() == R.id.search_list) {
			final AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
			final TextView tv= (TextView) info.targetView.findViewById(R.id.peer_list_item);

			menu.setHeaderTitle(tv.getText());
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.search_menu2, menu);
		}
	}

	// Handle context menu, opened by long-click
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		
		stopBluetoothDiscovery();
		
		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		
		Address a = dataSource.getItem(info.position);

		switch (item.getItemId()) {

		case R.id.connect_to:

			doConnect(a);
			break;
						
		case R.id.enter_item_addr:

			changeAddress(a);
			break;

		case R.id.clean_item:
			
			final String address = a.name;
			cleanAddress(address);
			dataSource.remove(address);
			break;

		}
		return super.onContextItemSelected(item);
	}

	@Override
	public void onBackPressed() {
		
		/*cancelSearch(false);

		final Intent intent = new Intent();  
		intent.putExtra(anyRemote.CONN_ADDR, "");

		setResult(RESULT_OK, intent);*/
		
		doExit();

		//super.onBackPressed();
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
					if (dataSource.addIfNew(device.getName(), "btspp://"+device.getAddress(), "",false)) {
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
	
	private void stopSearch() { 
		cancelSearch(false);
	}

	private void cancelSearch(boolean onlyCancel) { 
		//log("cancelSearch");
		
		stopBluetoothDiscovery();
		stopTcpDiscovery();
		
		if (!onlyCancel) {
			setProgressBarIndeterminateVisibility(false);
			setTitle(R.string.searchFormUnconnected);
		}
	}
	
	private void tcpSearch() {
		
		if (ipSearchTask != null) {
			log("tcpSearch: already searching");
			return;
		}
		
		String ip = anyRemote.getLocalIpAddress();
		if (ip == null) {
			return;
		}
		log("tcpSearch "+ip);
	
		scanSubNet(ip.substring(0,ip.lastIndexOf('.')+1));
		//scanSubNet("172.16.32.");
	}

	private void stopTcpDiscovery() {
		synchronized (asyncNum) {
			asyncNum = -1;
		}
		if (ipSearchTask != null) {
			log("stopTcpDiscovery");
			ipSearchTask.cancel(true);
		}
	}
	
	private void scanSubNet(String subnet){
		
		log("scanSubNet "+subnet);
				
	    hosts.clear();
	    asyncNum = 0; 
	    
		setProgressBarIndeterminateVisibility(true);
		setTitle(R.string.searching);
		
	    ipSearchTask = new PingTask();
	    ipSearchTask.execute(subnet);    
	}
	
	class PingTask extends AsyncTask<String, Integer, Void> {

		@Override
		protected Void doInBackground(String... params) {
			
		    for (int i=1; i<254; i++){
		        log("Trying: " + params[0] + String.valueOf(i));
		        
		        synchronized (asyncNum) {
		        	if (asyncNum < 0) {
		        		// cancel search
		        		log("Search cancelled");
		        		i = 255;
		        	} else {
		        	    asyncNum++;
		        	}
		        }
		        PingHostTask mTask = new PingHostTask();
		        mTask.execute(params[0] + String.valueOf(i));
		        
		        while (asyncNum > 16) {
		        	log("Waiting to run : " + asyncNum);
		    		try {
		    			Thread.sleep(200);
		    		} catch (InterruptedException e) {
		    		}
		        }
			    
			    publishProgress(i);
		    }
		    
		    while (asyncNum > 0) {
	    		try {
	    			Thread.sleep(300);
	     		} catch (InterruptedException e) {
	    		}
		    }

			return null;
		}
		
        @Override
        protected void onProgressUpdate(Integer... progress) {
        	
        	log("onProgressUpdate "+progress[0]);
        	
        	// dynamically add discovered hosts
		    synchronized (hosts) {
			    for (int h = 0;h<hosts.size();h++) {
			        dataSource.addIfNew("socket://"+hosts.get(h), "socket://"+hosts.get(h) + ":" + DEFAULT_IP_PORT, "",false);
			    }
			    hosts.clear();
		    }
		    
		    synchronized (asyncNum) {
		    	if (asyncNum > 0) {
				    log("onProgressUpdate " + asyncNum);
			    	setTitle(progress[0]+"/255");
		    	}
		    }
        }
        
        @Override
        protected void onPostExecute(Void unused) {
        	setProgressBarIndeterminateVisibility(false);
		    setTitle(R.string.searchFormUnconnected);
		    asyncNum = -1;
		    ipSearchTask = null;
        }
        
        @Override
        protected void onCancelled() {
        	setProgressBarIndeterminateVisibility(false);
		    setTitle(R.string.searchFormUnconnected);
		    asyncNum = -1;
		    ipSearchTask = null;
        }
	}
	
	class PingHostTask extends AsyncTask<String, Void, Void> {
		
        /*PipedOutputStream mPOut;
        PipedInputStream mPIn;
        LineNumberReader mReader;
        Process mProcess;*/
 
		/*
		@Override
        protected void onPreExecute() {
            /*mPOut = new PipedOutputStream();
            try {
                mPIn = new PipedInputStream(mPOut);
                mReader = new LineNumberReader(new InputStreamReader(mPIn));
            } catch (IOException e) {
                cancel(true);
            }
        }*/

        /*public void stop() {
			Process p = mProcess;
			if (p != null) {
				p.destroy();
			}
			cancel(true);
		}*/

		@Override
		protected Void doInBackground(String... params) {

			try {
				InetAddress inetAddress = InetAddress.getByName(params[0]);
				if (inetAddress.isReachable(1000)) {
					
					synchronized (asyncNum) {
						if (asyncNum < 0) {
							return null;
						}
					}

					log("Up # " + params[0]);
					String host = inetAddress.getHostName();

					synchronized (hosts) {
						hosts.add(host);
					}

					synchronized (asyncNum) {
						asyncNum--;
					}

					return null;
				}
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			/*try {
				log("start # " + params[0]);
				String cmd = "/system/bin/ping -q -n -w 1 -c 1 " + params[0];
				Process mProcess = new ProcessBuilder()
						.command(cmd)
						.redirectErrorStream(true).start();
				log("started # " + params[0]);

				try {
					InputStream in = mProcess.getInputStream();
					OutputStream out = mProcess.getOutputStream();
					byte[] buffer = new byte[1024];
					int count;
					log("AA " + params[0]);
					
					// in -> buffer -> mPOut -> mReader -> 1 line of ping
					// information to parse
					while ((count = in.read(buffer)) != -1) {
						mPOut.write(buffer, 0, count);
						// publishProgress();
					}
					log("done for # " + params[0] + " " + buffer);
					out.close();
					in.close();
					mPOut.close();
					mPIn.close();
				} finally {
					mProcess.destroy();
					mProcess = null;
				}
			} catch (IOException e) {
				log("IOException for # " + params[0] + " " + e.getMessage());
			}*/

			synchronized (asyncNum) {
				asyncNum--;
			}

			log("Down # " + params[0]);
			return null;
		}
		
		/*
        @Override
        protected void onProgressUpdate(Void... values) {
            try {
                // Is a line ready to read from the "ping" command?
                while (mReader.ready()) {
                    // This just displays the output, you should typically parse it I guess.
                	log("Got "+mReader.readLine());
                }
            } catch (IOException t) {
            }
        }*/
 	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) { 
		menu.clear();
		MenuInflater mi = getMenuInflater();		
		mi.inflate(R.menu.search_menu, menu);
		
		if (asyncNum >= 0 || ipSearchTask != null ||    // have active search
			btUseFlag == BT_USE_SEARCH) {
			
			/*MenuItem bsrch = menu.findItem(R.id.bt_search_item);
			bsrch.setVisible(false);
			
			MenuItem isrch = menu.findItem(R.id.tcp_search_item);
			isrch.setVisible(false);*/
			
			MenuItem isrch = menu.findItem(R.id.search_item);
			isrch.setVisible(false);
		} else {
			MenuItem srch = menu.findItem(R.id.cancel_search_item);
			srch.setVisible(false);
		}
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
				
			case R.id.search_item:
				
				showDialog(Dispatcher.CMD_SEARCH_DIALOG);
				break;
	
			case R.id.cancel_search_item:
	
				stopSearch();
				break;
	
			case R.id.enter_item:
	
				stopSearch();
				showDialog(Dispatcher.CMD_NEW_ADDR_DIALOG);
				break;
					
			case R.id.exit_item:
	
				doExit();
				break;
	
	
			case R.id.log_item:
	
				stopSearch();
				
				//anyRemote.sendGlobal(anyRemote.SHOW_LOG, null);
				
				//final Intent intentl = new Intent();
				//intentl.putExtra(anyRemote.ACTION, "log");
				//setResult(RESULT_OK, intentl);
				//finish();
				
				showLog();
				
				break;	
				
		    case R.id.about_item:
		    	
		    	About about = new About(this);
		    	about.setTitle(R.string.about);
		    	about.show();	    	
		}
		
		return true;
	}
	
	public void doExit() { 

		log("doExit");
		stopSearch();
		
		anyRemote.sendGlobal(anyRemote.DO_EXIT, null);	  
		
		//final Intent intente = new Intent();
		//intente.putExtra(anyRemote.ACTION, "exit");
		//setResult(RESULT_OK, intente);
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
		
		btUseFlag = BT_USE_NO;

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

	public void doConnect(String address) {
		
		log("doConnect:  "+address);
		Address a = dataSource.getItem(address);
		if (a == null) {
			log("doConnect: can not get information for "+address);
			return;
		}
		doConnect(a);
	}
	
	private void doConnect(Address a) {

		log("doConnect: host is "+a.URL);
		
		stopSearch();
		
		connectTo   = a.URL;
		connectName = a.name;
		connectPass = a.pass;
		connectAuto = a.autoconnect;

		if (connectTo.startsWith("btspp:") && mBtAdapter != null &&  !mBtAdapter.isEnabled()) {
			btUseFlag = BT_USE_CONNECT;
			switchBluetoothOn();
		} else {   
			doRealConnect();
		}
	}

	public void doRealConnect() {
		//log("doRealConnect");
		cancelSearch(false);

		log("doRealConnect: address is "+connectTo);

		// be sure peer is stored
		addAddress(connectName,connectTo,connectPass,connectAuto);
		
		//final Intent intent = new Intent();  

		//intent.putExtra(anyRemote.CONN_ADDR, connectTo);
		//intent.putExtra(anyRemote.CONN_NAME, connectName);
		//intent.putExtra(anyRemote.CONN_PASS, connectPass);
		//setResult(RESULT_OK, intent);

		Address conn = new Address();
		conn.name = connectName;
		conn.URL  = connectTo;
		conn.pass = connectPass;
		
		connectTo   = "";
		connectName = "";
		connectPass = "";
		connectAuto = false;
		
		anyRemote.sendGlobal(anyRemote.DO_CONNECT, conn);	  
		
		log("SearchForm::doRealConnect: finish");
		finish();
	}
	
	public void changeAddress(Address a) {
		log("changeAddress");
		
		if (a == null) {
			return;
		}
		
		if (a.URL == null) {
			log("changeAddress: can not get peer address for "+a.name);
			return;
		}

		connectPass = (a.pass == null ? "" : a.pass);
		connectTo   = a.URL;
		connectName = a.name;
		connectAuto = a.autoconnect;

		showDialog(Dispatcher.CMD_EDIT_FORM_ADDR);	
	}			    
	
	public void cleanAddress(String name) {
		//log("cleanAddress "+name);
		anyRemote.protocol.cleanAddress(name);
	}

	// save new address in preferences
	public void addAddress(String name, String URL, String pass,boolean autoconnect) {		        
		anyRemote.protocol.addAddress(name, URL, pass, autoconnect);
	}

	// Got result from AddressDialog dialog ("Ok"/"Cancel" was pressed)
	public void onDismissAddressDialog (DialogInterface dialog) {
	
		if (skipDismissDialog) {
			skipDismissDialog = false;
		} else {
			String n = stripNewLines(((AddressDialog) dialog).getPeerName());
			String a = stripNewLines(((AddressDialog) dialog).getPeerAddress());
			String p = stripNewLines(((AddressDialog) dialog).getPeerPassword());
			boolean ac = ((AddressDialog) dialog).getPeerAutoConnect();
			
			log("onDismiss AddressDialog >"+n+"< >"+a+"< >"+p+"<");
			
			if (a.length() == 0) {
				return;
			}
			if (n.length() == 0) {
				n = a;
			}
			if (a.startsWith("btspp://")) {
				a = formatBTAddr(a);
			}

			if (connectName.length() > 0 && a != connectName) {
				cleanAddress(connectName);
				dataSource.remove(connectName);
			}
			
			if (dataSource.addIfNew(n,a,p,ac)) {
			    addAddress(n,a,p,ac);
			}
		}
	}
	
	// Got result from SearchDialog dialog ("Ok"/"Cancel" was pressed)
	public void onDismissProtoChooseDialog (DialogInterface dialog) {
	
		if (skipDismissDialog) {
			skipDismissDialog = false;
		} else {
			
			boolean isBT = ((BT_IP_Choose_Dialog) dialog).isBluetooth();

			if (((BT_IP_Choose_Dialog) dialog).id() == Dispatcher.CMD_NEW_ADDR_DIALOG) {
				
				int id = (isBT ? Dispatcher.CMD_EDIT_FORM_BT : Dispatcher.CMD_EDIT_FORM_IP);
				showDialog(id);
				
			} else if (((BT_IP_Choose_Dialog) dialog).id() == Dispatcher.CMD_SEARCH_DIALOG) {
				if (isBT) {
					doDiscovery();
				} else {
					tcpSearch();
				}
			}
		}
	}
	
	// Handle "Cancel" press in EditFieldDialog/SearchDialog
	public void onCancel(DialogInterface dialog) { 
		skipDismissDialog = true;
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		
		switch(id){
		    case Dispatcher.CMD_EDIT_FORM_ADDR:
		    case Dispatcher.CMD_EDIT_FORM_IP:
		    case Dispatcher.CMD_EDIT_FORM_BT:
			    return new AddressDialog(this);
			    
		    case Dispatcher.CMD_NEW_ADDR_DIALOG:
			    return new BT_IP_Choose_Dialog(this, Dispatcher.CMD_NEW_ADDR_DIALOG);
			    
		    case Dispatcher.CMD_SEARCH_DIALOG:
			    return new BT_IP_Choose_Dialog(this, Dispatcher.CMD_SEARCH_DIALOG);
		}
		return null;
	}
	
	@Override
	protected void onPrepareDialog(int id, Dialog d) {
	
		if (d == null) return;
		
		skipDismissDialog = false;
		
		switch(id){
	
		    case Dispatcher.CMD_EDIT_FORM_ADDR:
	
				((AddressDialog) d).setupDialog(connectName,connectTo,connectPass,connectAuto);
				break;
			
			case Dispatcher.CMD_EDIT_FORM_IP:
		
				connectPass = "";
				connectTo   = "";
				connectName = "";
				
				((AddressDialog) d).setupDialog("",getResources().getString(R.string.default_ip),"",false);
		
				break;
		
			case Dispatcher.CMD_EDIT_FORM_BT:
		
				connectPass = "";
				connectTo   = "";
				connectName = "";
				
				((AddressDialog) d).setupDialog("",getResources().getString(R.string.default_bt),"",false);
				break;
				
			case Dispatcher.CMD_NEW_ADDR_DIALOG:
				
				// nothing
				break;

			case Dispatcher.CMD_SEARCH_DIALOG:
				
				// nothing
				break;
			
			default:
				return;
		}
		
		switch(id){
		    case Dispatcher.CMD_EDIT_FORM_ADDR:
		    case Dispatcher.CMD_EDIT_FORM_IP:
		    case Dispatcher.CMD_EDIT_FORM_BT:
				d.setOnDismissListener(new OnDismissListener() {
		            public void onDismiss(DialogInterface dialog) {
		                onDismissAddressDialog(dialog);
		            }
		        });	
		        break;
		        
		    case Dispatcher.CMD_NEW_ADDR_DIALOG:
		    case Dispatcher.CMD_SEARCH_DIALOG:
				d.setOnDismissListener(new OnDismissListener() {
		            public void onDismiss(DialogInterface dialog) {
		                onDismissProtoChooseDialog(dialog);
		            }
		        });	
			    
	    }
				
		d.setOnCancelListener (this);
	}
	
	private String formatBTAddr(String value) {
		
		// Samsung's does allows BT address only in capital
		StringBuffer baddr = new StringBuffer("btspp://");

		baddr.append(value.substring(8)
				.replace('a', 'A').replace('b', 'B')
				.replace('c', 'C').replace('d', 'D')
				.replace('e', 'E').replace('f', 'F'));

		int i = 8;
		while (i < baddr.length() - 3) {
			if (baddr.charAt(i) == ':') {
				baddr.deleteCharAt(i);
			} else {
				i++;
			}
		}
		return new String(baddr);
	}

	private String stripNewLines(String value) {
		if (value.length() > 0 && value.charAt(value.length() - 1) == '\n') {
			value = value.substring(0, value.length() - 1);
		}
		if (value.length() > 0 && value.charAt(0) == '\n') {
			value = value.substring(1, value.length());
		}
		return value;
	}
}
