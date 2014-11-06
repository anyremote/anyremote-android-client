//
// anyRemote android client
// a bluetooth/wi-fi remote control for Linux.
//
// Copyright (C) 2014 Mikhail Fedotov <anyremote@mail.ru>
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

// API 16
import android.net.nsd.NsdServiceInfo;
import android.net.nsd.NsdManager;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.Build;

import anyremote.client.android.anyRemote;
import anyremote.client.android.Dispatcher;
import anyremote.client.android.util.IScanner;
import anyremote.client.android.util.ScanMessage;

//
// Zeroconf scanner
//

@TargetApi(android.os.Build.VERSION_CODES.JELLY_BEAN)  // API 16
public class ZCScanner implements IScanner {	

    static final String  ZEROCONF_SERVICE_TYPE = "_remote._tcp.";
    static final String  ZEROCONF_SERVICE_NAME = "anyRemote";

    // API 16
    private NsdManager                   mNsdManager        = null;
    private NsdManager.DiscoveryListener mDiscoveryListener = null;
    private NsdManager.ResolveListener   mResolveListener   = null;
    // API 16

    Handler searchFormHandler;
    Context context;

	public ZCScanner(Handler hdl, Context ctx) {
        searchFormHandler = hdl;
        context           = ctx;
    }
    
    @TargetApi(android.os.Build.VERSION_CODES.JELLY_BEAN)
    public void startScan() {
            
        // API 16
        if (mNsdManager == null) {
            mNsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        }
        // API 16
        
        initializeResolveListener();
        initializeDiscoveryListener(); 
        
        anyRemote._log("ZCScanner", "startScan discoverServices");
        
        // API 16
        mNsdManager.discoverServices(ZEROCONF_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }
    
    @TargetApi(android.os.Build.VERSION_CODES.JELLY_BEAN)
    public void stopScan () {
        // API 16
        mNsdManager.stopServiceDiscovery(mDiscoveryListener);
        
        //informDiscoveryResult(SCAN_FINISHED);
    }
    
    private void informDiscoveryResult(int res) {
        Message msg = searchFormHandler.obtainMessage(res);
		msg.sendToTarget();
    }
    
    @TargetApi(android.os.Build.VERSION_CODES.JELLY_BEAN)
    private void initializeResolveListener() {

        // API 16
        if (mResolveListener == null) {
            
            anyRemote._log("ZCScanner", "initializeResolveListener");
            
            mResolveListener = new NsdManager.ResolveListener() {

                @Override
                public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    anyRemote._log("ZCScanner", "ResolveListener: Resolve failed" + errorCode);
                }

                @Override
                public void onServiceResolved(NsdServiceInfo serviceInfo) {
                    
                    anyRemote._log("ZCScanner", "ResolveListener: Resolve Succeeded. " + serviceInfo);

                    //if (serviceInfo.getServiceName().equals(ZEROCONF_SERVICE_NAME)) {
                    //    log("ResolveListener: Same IP.");
                    //    return;
                    //}

                    String service = serviceInfo.getServiceName();
                    String host    = serviceInfo.getHost().getHostAddress();
                    String port    = String.valueOf(serviceInfo.getPort());
                    
                    ScanMessage sm = new ScanMessage();
                    sm.name    = service + "://" + host;
                    sm.address = "socket://" + host + ":" + port;
                    
                    Message msg = searchFormHandler.obtainMessage(SCAN_FOUND, sm);
		            msg.sendToTarget();
                }
            };
        }
    }
    
    @TargetApi(android.os.Build.VERSION_CODES.JELLY_BEAN)
    private void initializeDiscoveryListener() {

        // Instantiate a new DiscoveryListener
        // API 16
        if (mDiscoveryListener == null) {
        
            anyRemote._log("ZCScanner", "initializeDiscoveryListener");

            mDiscoveryListener = new NsdManager.DiscoveryListener() {

                //  Called as soon as service discovery begins.
                @Override
                public void onDiscoveryStarted(String regType) {
                    anyRemote._log("ZCScanner", "DiscoveryListener: Service discovery started");
                    //informDiscoveryResult(SCAN_STARTED);
               }

                @Override
                public void onServiceFound(NsdServiceInfo service) {
                    
                    // A service was found!  Do something with it.
                    anyRemote._log("ZCScanner","DiscoveryListener: Service discovery success" + service);
                    
                    if (!service.getServiceType().equals(ZEROCONF_SERVICE_TYPE)) {
                        
                        // Service type is the string containing the protocol and transport layer for this service
                        anyRemote._log("ZCScanner","DiscoveryListener: Unknown Service Type: " + service.getServiceType());

                    //} else if (service.getServiceName().equals(ZEROCONF_SERVICE_NAME)) {
                    //
                    //    // The name of the service tells the user what they'd be connecting to
                    //    log("DiscoveryListener: Same machine: " + ZEROCONF_SERVICE_NAME);
                    //
                    } else if (service.getServiceName().contains(ZEROCONF_SERVICE_NAME)){
                        mNsdManager.resolveService(service, mResolveListener);
                    }
                }

                @Override
                public void onServiceLost(NsdServiceInfo service) {
                    // When the network service is no longer available.
                    // Internal bookkeeping code goes here.
                    anyRemote._log("ZCScanner", "DiscoveryListener: service lost" + service);
                    informDiscoveryResult(SCAN_FAILED);
                }

                @Override
                public void onDiscoveryStopped(String serviceType) {
                    anyRemote._log("ZCScanner","DiscoveryListener: Discovery stopped: " + serviceType);
                    informDiscoveryResult(SCAN_FINISHED);
                }

                @Override
                public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                    anyRemote._log("ZCScanner", "DiscoveryListener: Discovery failed: Error code:" + errorCode);
                    mNsdManager.stopServiceDiscovery(this);
                    informDiscoveryResult(SCAN_FAILED);
                }

                @Override
                public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                    anyRemote._log("ZCScanner","DiscoveryListener: Discovery failed: Error code:" + errorCode);
                    mNsdManager.stopServiceDiscovery(this);
                    informDiscoveryResult(SCAN_FAILED);
                }
            };
        }
    }
}
