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

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
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
import android.content.BroadcastReceiver;
import anyremote.client.android.util.About;
import anyremote.client.android.util.Address;
import anyremote.client.android.util.AddressAdapter;
import anyremote.client.android.util.BTScanner;
import anyremote.client.android.util.IPScanner;
import anyremote.client.android.util.ZCScanner;
import anyremote.client.android.util.IScanner;
import anyremote.client.android.util.ScanMessage;

public class SearchForm extends arActivity 
                        implements OnItemClickListener,
                                   //DialogInterface.OnDismissListener,
                                   //DialogInterface.OnCancelListener,
                                   AdapterView.OnItemSelectedListener {

	ListView searchList;
	AddressAdapter dataSource;
	int selected = 0;
    
    Handler handler = null; 

    IScanner scanner = null;

	// BT stuff
	private BluetoothAdapter mBtAdapter;

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

		//requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		requestWindowFeature(Window.FEATURE_ACTION_BAR);
		
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
        
        handler = new Handler() {
            
            @Override
            public void handleMessage(Message inputMessage) {

                switch (inputMessage.what) {

                    case IScanner.SCAN_STARTED:   // Indicate scanning in the title
                        setProgressBarIndeterminateVisibility(true);
                        setTitle(R.string.searching);
                        break;
                    
                    case IScanner.SCAN_FAILED:
        	            setProgressBarIndeterminateVisibility(false);
		                setTitle(R.string.searchFormUnconnected);
                        break;
                    
                    case IScanner.SCAN_FINISHED:
        	            setProgressBarIndeterminateVisibility(false);
		                setTitle(R.string.searchFormUnconnected);
                        break;
                    
                    case IScanner.SCAN_PROGRESS:
                        ScanMessage pmsg = (ScanMessage) inputMessage.obj;
                        setProgressBarIndeterminateVisibility(true);
                        setTitle(pmsg.name);
                        
                        break;
                    
                    case IScanner.SCAN_FOUND:
                        ScanMessage fmsg = (ScanMessage) inputMessage.obj;
                        dataSource.addIfNew(fmsg.name, fmsg.address, "",false);
                        break;
                    
                    default:
                        super.handleMessage(inputMessage);
                }
            }
        };
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
		stopSearch();

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

		stopSearch();

		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();

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
		doExit();
	}

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
					doRealConnect();
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
		//log("stopSearch");

        if (scanner != null) {
            scanner.stopScan();
            scanner = null;
        }
		
		setProgressBarIndeterminateVisibility(false);
		setTitle(R.string.searchFormUnconnected);
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) { 
		menu.clear();
		MenuInflater mi = getMenuInflater();		
		mi.inflate(R.menu.search_menu, menu);
		
        log("onPrepareOptionsMenu: "+(scanner == null ? "NULL" : "OK"));
        
		if (scanner != null) {    // have active search
			
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
				
				stopSearch();
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

		finish();
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) { 
		
		log("onKeyUp: "+keyCode);
		
        boolean lp = longPress;
        longPress = false;
        
		switch (keyCode) {
		
		    case  KeyEvent.KEYCODE_BACK:
	            if (event.isTracking() && !event.isCanceled() && lp) {
	                log("onKeyUp KEYCODE_BACK long press - show menu");
	                     
	                new Handler().postDelayed(new Runnable() { 
	                    public void run() { 
	                        openOptionsMenu(); 
	                      } 
	                   }, 1000); 
	            } else {
	                doExit();
	            }
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
            case KeyEvent.KEYCODE_BACK:
                event.startTracking();
                return true;
		}
		return false;
	}
	
    protected void showWeb() {
	    log("showWeb");
	    final Intent showWeb = new Intent(getBaseContext(), WebScreen.class);
	    startActivity(showWeb); 
	}

	public void switchBluetoothOn() {
		log("switchBluetoothOn");
		deregStateRcv = true;
		registerReceiver(mBTStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
		startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 0);
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
			switchBluetoothOn();
		} else if (connectTo.startsWith("web://")) {
            anyRemote.protocol.webUrl = "http://"+connectTo.substring(6);  // 6 == size("web://")
			addAddress(connectName,connectTo,connectPass,connectAuto);
            showWeb();
		} else {   
			doRealConnect();
		}
	}

	public void doRealConnect() {
		//log("doRealConnect");
		
        stopSearch();

		log("doRealConnect: address is "+connectTo);

		// be sure peer is stored
		addAddress(connectName,connectTo,connectPass,connectAuto);

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
		log("addAddress "+name+" >"+URL+"<");
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
		
			dataSource.addIfNew(n,a,p,ac);
			addAddress(n,a,p,ac);
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
				
                if (scanner != null) {
                    log("scanner: already searching");
                    return;
                }
                
                if (isBT) {
                    scanner = new BTScanner(handler, this);
				} else {
                    boolean zeroconf = ((BT_IP_Choose_Dialog) dialog).isZeroconf();
                    int apiVersion = Integer.valueOf(android.os.Build.VERSION.SDK);
                    
                    if (zeroconf && apiVersion >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                        scanner = new ZCScanner(handler, anyRemote.protocol.context);
                    } else {
					    scanner = new IPScanner(handler);
                    }
				}
                
                if (scanner != null) {
                    scanner.startScan();
                    setProgressBarIndeterminateVisibility(true);
		            setTitle(R.string.searching);
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
            
		    case Dispatcher.CMD_SENSOR_DIALOG:
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

		//int i = 8;
		//while (i < baddr.length() - 3) {
		//	if (baddr.charAt(i) == ':') {
		//		baddr.deleteCharAt(i);
		//	} else {
		//		i++;
		//	}
		//}
		log("formatBTAddr: "+baddr);
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
