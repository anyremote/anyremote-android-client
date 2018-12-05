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


import android.content.Context;
import android.os.Handler;
import android.os.Message;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;

import anyremote.client.android.R;
import anyremote.client.android.anyRemote;
import anyremote.client.android.SearchForm;
import anyremote.client.android.util.IScanner;
import anyremote.client.android.util.ScanMessage;

//
// Bluetooth scanner
//

public class BTScanner implements IScanner {	

    Handler    searchFormHandler;
    SearchForm calledFrom;
    
    boolean deregStateRcv = false;

	private BluetoothAdapter mBtAdapter;

	public BTScanner(Handler hdl, SearchForm sf) {
        searchFormHandler = hdl;
        calledFrom        = sf;
    }

    public void startScan() {

		// Register for broadcasts when a device is discovered
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		calledFrom.registerReceiver(mReceiver, filter);

		// Register for broadcasts when discovery has finished
		filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		calledFrom.registerReceiver(mReceiver, filter);

        // Get the local Bluetooth adapter
		mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        
        doDiscovery();
    }

    public void stopScan () {
    
		if (mBtAdapter != null && mBtAdapter.isDiscovering()) {
			anyRemote._log("BTScanner", "stopScan");
			mBtAdapter.cancelDiscovery();		
		}
        
        // Make sure we're not doing discovery anymore
        calledFrom.unregisterReceiver(mReceiver);
		if (deregStateRcv) {
		    calledFrom.unregisterReceiver(mBTStateReceiver);
		}
    }

    private void informDiscoveryResult(int res) {
        Message msg = searchFormHandler.obtainMessage(res);
		msg.sendToTarget();
    }

    private void informDiscoveryResult(String v) {
    
        ScanMessage sm = new ScanMessage();
        sm.name = v;

        Message msg = searchFormHandler.obtainMessage(SCAN_PROGRESS, sm);
		msg.sendToTarget();
    }
    
	private void doDiscovery() {
    
		if (mBtAdapter == null) {
            informDiscoveryResult(SCAN_FAILED);
            stopScan();
			return;
		}
		if (!mBtAdapter.isEnabled()) {
			switchBluetoothOn();
		} else {       
			doRealDiscovery();
		}
	}

	private void doRealDiscovery() {
    
		anyRemote._log("BTScanner", "doRealDiscovery");

		// Indicate scanning in the title
        informDiscoveryResult(SCAN_STARTED);

		// Request discover from BluetoothAdapter
		mBtAdapter.startDiscovery();
	}
    
	public void switchBluetoothOn() {
    
		anyRemote._log("BTScanner", "switchBluetoothOn");
		
        String actionStateChanged  = BluetoothAdapter.ACTION_STATE_CHANGED;
		String actionRequestEnable = BluetoothAdapter.ACTION_REQUEST_ENABLE;
		
        deregStateRcv = true;
		
        calledFrom.registerReceiver(mBTStateReceiver, new IntentFilter(actionStateChanged));
		
        calledFrom.startActivityForResult(new Intent(actionRequestEnable), 0);
	}
    
	// The BroadcastReceiver that listens for discovered devices and
	// changes the title when discovery is finished
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		
        @Override
		public void onReceive(Context context, Intent intent) {
			anyRemote._log("BTScanner", "BroadcastReceiver.onReceive discovery ");

			String action = intent.getAction();

			// When discovery finds a device
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {

				// Get the BluetoothDevice object from the Intent
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				anyRemote._log("BTScanner", "BroadcastReceiver::onReceive discovery GOT ONE "+device.getName()+" "+device.getAddress());

				// If it's already paired, skip it, because it's been listed already
				//if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    ScanMessage sm = new ScanMessage();
                    sm.name    = device.getName();
                    sm.address = "btspp://"+device.getAddress();
                    
                    Message msg = searchFormHandler.obtainMessage(SCAN_FOUND, sm);
		            msg.sendToTarget();
				//}

			// When discovery is finished, change the Activity title
			} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {

				anyRemote._log("BTScanner", "BroadcastReceiver::onReceive discovery FINISHED");
				informDiscoveryResult(SCAN_FINISHED);
			}
		}
	};

	// The BroadcastReceiver that handles BT state
	private final BroadcastReceiver mBTStateReceiver = new BroadcastReceiver() {
		
        @Override
		public void onReceive(Context context, Intent intent) {
			anyRemote._log("BTScanner", "BroadcastReceiver::onReceive state");

			String stateExtra = BluetoothAdapter.EXTRA_STATE;
			int state = intent.getIntExtra(stateExtra, -1);
			
            switch (state) {
				
                case (BluetoothAdapter.STATE_TURNING_ON) : {
					informDiscoveryResult(R.string.bt_on);
					break;
				}
				
                case (BluetoothAdapter.STATE_ON) : {
					anyRemote._log("BTScanner", "BroadcastReceiver::onReceive state ON");
                    
                    informDiscoveryResult(R.string.bt_on);
                    
					calledFrom.unregisterReceiver(this);
					deregStateRcv = false;
	
					doRealDiscovery();

					break;
				}
				
                case (BluetoothAdapter.STATE_TURNING_OFF) : {
					informDiscoveryResult(R.string.bt_on);
					break;
				}
				
                case (BluetoothAdapter.STATE_OFF) : {
					anyRemote._log("BTScanner", "BroadcastReceiver::onReceive state OFF");
					informDiscoveryResult(R.string.bt_on);
					
                    calledFrom.unregisterReceiver(this);
					deregStateRcv = false;
					
                    break;
				}
			}
		}
	};
}
