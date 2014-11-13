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
import java.util.ArrayList;
import java.util.TimerTask;

import android.net.nsd.NsdServiceInfo;
import android.net.nsd.NsdManager;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.Build;

import anyremote.client.android.MainLoop;
import anyremote.client.android.anyRemote;
import anyremote.client.android.Dispatcher;
import anyremote.client.android.util.IScanner;
import anyremote.client.android.util.ScanMessage;

//
// Zeroconf scanner
//

@TargetApi(android.os.Build.VERSION_CODES.JELLY_BEAN)  // API 16
public class ZCScanner implements IScanner {	

    static final String ZEROCONF_TCP_SERVICE_TYPE = "_remote._tcp";
    static final String ZEROCONF_WEB_SERVICE_TYPE = "_http._tcp";
    static final String ZEROCONF_ANR_SERVICE_TYPE = "_anyremote._tcp";  // future
    
    static final String ZEROCONF_SERVICE_NAME = "anyRemote";

    private NsdManager          mNsdManager           = null;
    private ArDiscoveryListener mDiscoveryListenerTCP = null;
    private ArDiscoveryListener mDiscoveryListenerWEB = null;
    private NsdManager.ResolveListener resolver       = null;
    
    ArrayList<NsdServiceInfo> toResolve = new ArrayList<NsdServiceInfo>();
    Handler searchFormHandler;
    Context context;
    

	public ZCScanner(Handler hdl, Context ctx) {
        searchFormHandler = hdl;
        context           = ctx;
    }

    @TargetApi(android.os.Build.VERSION_CODES.JELLY_BEAN)
    public void resolveNext() {
        
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
		}
        
        synchronized (toResolve) {
            if (toResolve.size() > 0) {
                NsdServiceInfo srvInfo = toResolve.remove(0);
                anyRemote._log("ZCScanner","resolveNext (remains #" + toResolve.size() + ") " + srvInfo);
                mNsdManager.resolveService(srvInfo, resolver);
            }
        }
    }

    @TargetApi(android.os.Build.VERSION_CODES.JELLY_BEAN)
    public void startScan() {
            
        if (mNsdManager == null) {
            mNsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        }
        
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
		}
        
        if (resolver == null) {
        
            resolver = new NsdManager.ResolveListener() {

                @Override
                public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    anyRemote._log("ZCScanner", "resolve failed " + errorCode);
                    resolveNext(); 
                }

                @Override
                public void onServiceResolved(NsdServiceInfo serviceInfo) {

                    String service = serviceInfo.getServiceName();
                    String type    = serviceInfo.getServiceType();
                    String host    = serviceInfo.getHost().getHostAddress();
                    String port    = String.valueOf(serviceInfo.getPort());

                    anyRemote._log("ZCScanner", "resolve succeeded " + service +"/" + type);

                    ScanMessage sm = new ScanMessage();

                    if (type.contains(ZEROCONF_TCP_SERVICE_TYPE) ||
                        type.contains(ZEROCONF_ANR_SERVICE_TYPE)) {

                        sm.name    = service + "://" + host;
                        sm.address = "socket://" + host + ":" + port;

                    } else if (type.contains(ZEROCONF_WEB_SERVICE_TYPE)) {

                        sm.name    = "web://" + host;
                        sm.address = "web://" + host + ":" + port;

                    } else {

                        anyRemote._log("ZCScanner", "resolver: improper service type " + type);
                        return;
                    }

                    Message msg = searchFormHandler.obtainMessage(SCAN_FOUND, sm);
		            msg.sendToTarget();

                    resolveNext(); 
                }
            };
        }
        
        if (mDiscoveryListenerTCP == null) {
            anyRemote._log("ZCScanner", "startScan TCP");
            mDiscoveryListenerTCP = new ArDiscoveryListener(ZEROCONF_TCP_SERVICE_TYPE);
        }
        
        if (mDiscoveryListenerWEB == null) {
            anyRemote._log("ZCScanner", "startScan WEB");
            mDiscoveryListenerWEB = new ArDiscoveryListener(ZEROCONF_WEB_SERVICE_TYPE);
        }
        
        anyRemote._log("ZCScanner", "startScan discoverServices");
        
            			
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
		}
        
		MainLoop.schedule(new TimerTask() {
            public void run() {
                 stopScan();
            }
            }, 15000);  // stop discovery after 15 seconds
        
        mNsdManager.discoverServices(ZEROCONF_TCP_SERVICE_TYPE, 
                                     NsdManager.PROTOCOL_DNS_SD, 
                                     (NsdManager.DiscoveryListener) mDiscoveryListenerTCP);

		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
		}
        
        mNsdManager.discoverServices(ZEROCONF_WEB_SERVICE_TYPE, 
                                     NsdManager.PROTOCOL_DNS_SD, 
                                     (NsdManager.DiscoveryListener) mDiscoveryListenerWEB);
    }
    
    @TargetApi(android.os.Build.VERSION_CODES.JELLY_BEAN)
    public void stopScan () {
        mNsdManager.stopServiceDiscovery((NsdManager.DiscoveryListener) mDiscoveryListenerTCP);
        mNsdManager.stopServiceDiscovery((NsdManager.DiscoveryListener) mDiscoveryListenerWEB);
    }
    
    private void informDiscoveryResult(int res) {
        Message msg = searchFormHandler.obtainMessage(res);
		msg.sendToTarget();
    }
    
    @TargetApi(android.os.Build.VERSION_CODES.JELLY_BEAN)
    public class ArDiscoveryListener implements NsdManager.DiscoveryListener {

        private String serviceType;
         
        public ArDiscoveryListener(String sType) {
            
            serviceType = sType; 

            anyRemote._log("ZCScanner", "ArDiscoveryListener "+serviceType);
        }
        
        //  Called as soon as service discovery begins.
        @Override
        public void onDiscoveryStarted(String regType) {
            anyRemote._log("ZCScanner", "service discovery started "+serviceType);
        }

        @Override
        public void onServiceFound(NsdServiceInfo service) {
            
            anyRemote._log("ZCScanner","service discovery success " + service.getServiceName() + " " + service.getServiceType());
            if (!service.getServiceName().contains(ZEROCONF_SERVICE_NAME)) {
                return;
            }
            
            if (service.getServiceType().contains(serviceType)) {
                 synchronized (toResolve) {
                     toResolve.add(service);
                     anyRemote._log("ZCScanner","resolve queue #" + toResolve.size());
                 }
                 resolveNext();
            } else {
                // Service type is the string containing the protocol and transport layer for this service
                anyRemote._log("ZCScanner","unknown service type " + service.getServiceType());
            }
        }
        
        @Override
        public void onServiceLost(NsdServiceInfo service) {
            // When the network service is no longer available.
            // Internal bookkeeping code goes here.
            anyRemote._log("ZCScanner", "service lost " + service.getServiceName());
            informDiscoveryResult(SCAN_FAILED);
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            anyRemote._log("ZCScanner","discovery stopped " + serviceType);
            informDiscoveryResult(SCAN_FINISHED);
        }

        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            anyRemote._log("ZCScanner", "discovery failed: error code " + errorCode);
            mNsdManager.stopServiceDiscovery((NsdManager.DiscoveryListener) this);
            informDiscoveryResult(SCAN_FAILED);
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            anyRemote._log("ZCScanner","discovery failed: error code " + errorCode);
            mNsdManager.stopServiceDiscovery((NsdManager.DiscoveryListener) this);
            informDiscoveryResult(SCAN_FAILED);
        }
    }
}
