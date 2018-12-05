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

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Message;

import anyremote.client.android.anyRemote;
import anyremote.client.android.util.IScanner;
import anyremote.client.android.util.ScanMessage;

//
// IP search
//

public class IPScanner implements IScanner {	

	static final String  DEFAULT_IP_PORT = "5197";
	static final int     MAX_PING_TASKS  = 16;
	static final int     MAX_SUBNET_ADDR = 254;

    Handler searchFormHandler;

	volatile Integer asyncNum = new Integer(-1);
	ArrayList<String> hosts = new ArrayList<String>();
	PingTask ipSearchTask   = null;

	public IPScanner(Handler hdl) {
        searchFormHandler = hdl;
    }

    public void startScan() {

        if (ipSearchTask != null) {
            anyRemote._log("IPScanner", "startScan: already scanning");
            return;
        }

        String ip = anyRemote.getLocalIpAddress();
        if (ip == null) {
            Message msg = searchFormHandler.obtainMessage(SCAN_FAILED);
            msg.sendToTarget();
            return;
        }
		anyRemote._log("IPScanner", "startScan "+ip);
	
		scanSubNet(ip.substring(0,ip.lastIndexOf('.')+1));
		//scanSubNet("172.16.32.");
    }

    public void stopScan () {
    
		synchronized (asyncNum) {
			asyncNum = -1;
		}
        
		if (ipSearchTask != null) { 
			anyRemote._log("IPScanner", "stopScan");
			ipSearchTask.cancel(true);
		}
    }
	
	private void scanSubNet(String subnet){
		
		anyRemote._log("IPScanner", "scanSubNet "+subnet);
				
	    hosts.clear();
	    asyncNum = 0; 
	    
        Message msg = searchFormHandler.obtainMessage(SCAN_STARTED);
		msg.sendToTarget();
		
	    ipSearchTask = new PingTask();
	    ipSearchTask.execute(subnet);    
	}
	
	class PingTask extends AsyncTask<String, Integer, Void> {

        @Override
		protected Void doInBackground(String... params) {
			
		    for (int i=1; i<MAX_SUBNET_ADDR; i++){
		        anyRemote._log("IPScanner", "PingTask.doInBackground Trying: " + params[0] + String.valueOf(i));
		        
		        synchronized (asyncNum) {
		        	if (asyncNum < 0) {
		        		// cancel search
		        		anyRemote._log("IPScanner", "PingTask.doInBackground Search cancelled");
		        		i = 255;
		        	} else {
		        	    asyncNum++;
		        	}
		        }
                
                if (i < MAX_SUBNET_ADDR) {
		            PingHostTask mTask = new PingHostTask();
                    
                    int apiVersion = Integer.valueOf(android.os.Build.VERSION.SDK);
		            
                    if (apiVersion >= android.os.Build.VERSION_CODES.HONEYCOMB) {  // API 11
 		                mTask.executeOnExecutor(THREAD_POOL_EXECUTOR, params[0] + String.valueOf(i));
                    } else {
                        mTask.execute(params[0] + String.valueOf(i));
                    }

		            while (asyncNum > MAX_PING_TASKS) {
		        	    anyRemote._log("IPScanner", "PingTask.doInBackground Waiting to run : " + asyncNum);
		    		    try {
		    			    Thread.sleep(300);
		    		    } catch (InterruptedException e) {
		    		    }
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
        	
        	anyRemote._log("IPScanner", "PingTask.onProgressUpdate "+progress[0]);
        	
        	// dynamically add discovered hosts
		    synchronized (hosts) {
			    for (int h = 0;h<hosts.size();h++) {
                    
                    ScanMessage sm = new ScanMessage();
                    sm.name    = "socket://"+hosts.get(h);
                    sm.address = "socket://"+hosts.get(h) + ":" + DEFAULT_IP_PORT;
                    
                    Message msg = searchFormHandler.obtainMessage(SCAN_FOUND, sm);
		            msg.sendToTarget();
			    }
			    hosts.clear();
		    }
		    
		    synchronized (asyncNum) {
		    	if (asyncNum > 0) {
				    anyRemote._log("IPScanner", "PingTask.onProgressUpdate " + asyncNum);
                    
                    ScanMessage sm = new ScanMessage();
                    sm.name    = progress[0]+"/255";

                    Message msg = searchFormHandler.obtainMessage(SCAN_PROGRESS, sm);
		            msg.sendToTarget();
		    	}
		    }
        }
        
        @Override
        protected void onPostExecute(Void unused) {
            Message msg = searchFormHandler.obtainMessage(SCAN_FINISHED);
		    msg.sendToTarget();
		    asyncNum = -1;
		    ipSearchTask = null;
        }
        
        @Override
        protected void onCancelled() {
            Message msg = searchFormHandler.obtainMessage(SCAN_FINISHED);
		    msg.sendToTarget();
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

			anyRemote._log("IPScanner", "PingHostTask.doInBackground START");
            
            try {
				InetAddress inetAddress = InetAddress.getByName(params[0]);
				
                anyRemote._log("IPScanner", "PingHostTask.doInBackground # " + inetAddress);
                
                if (inetAddress.isReachable(1000)) {
					
					synchronized (asyncNum) {
						if (asyncNum < 0) {
							return null;
						}
					}

					anyRemote._log("IPScanner", "PingHostTask.doInBackground reachable # " + params[0]);
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

			anyRemote._log("IPScanner", "PingHostTask.doInBackground Down # " + params[0]);
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
}
