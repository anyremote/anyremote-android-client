//
// anyRemote android client
// a bluetooth/wi-fi remote control for Linux.
//
// Copyright (C) 2011-2012 Mikhail Fedotov <anyremote@mail.ru>
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

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.TreeMap;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.widget.Toast;
import anyremote.client.android.util.ProtocolMessage;
import anyremote.client.android.R;

public class anyRemote extends Activity 
                       implements Handler.Callback {

	public static final int DISCONNECTED = 0;
	public static final int CONNECTED    = 1;
	public static final int COMMAND      = 2;
	
	public static final int SWIPE_MIN_DISTANCE = 120;
    public static final int SWIPE_THRESHOLD_VELOCITY = 200; 

	static final int  NO_FORM      = 0;
	static final int  SEARCH_FORM  = 1;
	static final int  CONTROL_FORM = 2;
	static final int  FMGR_FORM    = 3;
	static final int  TEXT_FORM    = 4;
	static final int  LIST_FORM    = 5;
	static final int  EDIT_FORM    = 6;
	static final int  WMAN_FORM    = 7;
	static final int  LOG_FORM     = 8;
	static final int  DUMMY_FORM   = 9;

	static final int  LOG_CAPACITY = 16384;

	static final String  CONN_ADDR      = "ADR";
	static final String  CONN_NAME      = "CNM";
	static final String  CONN_PASS      = "CNP";
	static final String  ACTION         = "ACT";
	static final String  SWITCHTO       = "SWT";

	int         prevForm = NO_FORM;
	private static int  currForm = NO_FORM;
	static int         status;
	static Dispatcher  protocol;

	public static boolean finishFlag   = false;
	public static boolean firstConnect = true;
	
	static TreeMap<String,Bitmap> iconMap = new TreeMap<String,Bitmap>();

	private static Handler globalHandler = null;
	
	private static DateFormat now_format = new SimpleDateFormat("HH:mm:ss");
	private static Date teraz = new Date();
	
	// Logging stuff
	public static StringBuilder logData;
	
	// Wait indicator stuff
	public static ProgressDialog waiting;

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);		
        
		logData = new StringBuilder(LOG_CAPACITY);
		
		_log("onCreate "+android.os.Build.MODEL+ " " +android.os.Build.VERSION.CODENAME+" "+android.os.Build.VERSION.RELEASE);
		
		protocol = new Dispatcher(this);

		//requestWindowFeature(Window.FEATURE_NO_TITLE);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		setContentView(R.layout.main);

		currForm        = DUMMY_FORM;
		status          = DISCONNECTED;
        
		if (globalHandler == null) {
		    globalHandler = new Handler(this);
		}

		MainLoop.enable();
		
		currForm = DUMMY_FORM;
		status = DISCONNECTED;
	}
	

	@Override
	protected void onStart() {
		_log("onStart "+currForm);		
		super.onStart();
		
		finishFlag = false;
		if (currForm != LOG_FORM && status == DISCONNECTED) {
		    setCurrentView(SEARCH_FORM,"");
		}
	}
	
	@Override
	protected void onPause() {
		_log("onPause "+currForm);		
		//protocol.pauseConnection();
		super.onPause();
		//runningCount--;
	}

	@Override
	protected void onResume() {
		//logData = ""; // remove old log
		_log("onResume "+currForm);	
		super.onResume();
		//protocol.resumeConnection();
		//runningCount++;
	}
	
	@Override
	protected void onStop() {
		_log("onStop");		
	    super.onStop();	
	}

	@Override
	protected void onDestroy() {	
		_log("onDestroy");

		super.onDestroy();
		
		finishFlag = true;
		setCurrentView(NO_FORM,"");
		status = DISCONNECTED;
		protocol.disconnect(true);
		MainLoop.disable();
	}

	//@Override
	//protected void onResume() {
	//	_log("onResume");
	//	super.onStart();				
	//	//protocol.resumeConnection(); // get result by notification
	//}

	//@Override
	//protected void onPause() {
	//	_log("onPause");		
	//	super.onStop();	
	//	//protocol.pauseConnection();
	//}

	public void setCurrentView(int which, String subCommand) {
		_log("setCurrentView " + getScreenStr(which) + " (was " + getScreenStr(currForm) + ") finish="+finishFlag);

		if (finishFlag == true) return; // on destroy

		if (currForm == which) {
			_log("setCurrentView TRY TO SWITCH TO THE SAME FORM ???");
			//if (currForm == SEARCH_FORM) {
				return;
			//}
		}
		
		prevForm = currForm;
		currForm = which;

		if (currForm != prevForm) {
						
			// finish current form
			switch (prevForm) { 
			case SEARCH_FORM:
				_log("[AR] setCurrentView mess SEARCH_FORM with some other");
				break;
	
			case CONTROL_FORM:
			case LIST_FORM:
			case TEXT_FORM:
			case WMAN_FORM:
				_log("setCurrentView stop "+prevForm);
				protocol.sendToActivity(prevForm, Dispatcher.CMD_CLOSE,ProtocolMessage.FULL);
				
	            break;
	
			case LOG_FORM:
			case DUMMY_FORM:
				break;
	
			}
		}
		
		if (prevForm != LOG_FORM) {
	     	protocol.menuReplaceDefault(currForm);
		}

		switch (currForm) { 
		case SEARCH_FORM:
			_log("setCurrentView start SearchForm");
			final Intent doSearch = new Intent(getBaseContext(), SearchForm.class);
			startActivityForResult(doSearch, which); 
			break;

		case CONTROL_FORM:
			_log("setCurrentView start ControlScreen");
			final Intent control = new Intent(getBaseContext(), ControlScreen.class);
			startActivityForResult(control, which); 
			//startActivity(control); 
			break;

		case LIST_FORM:
			_log("setCurrentView start ListScreen");
			final Intent showList = new Intent(getBaseContext(), ListScreen.class);
			startActivity(showList); 
			break;

		case TEXT_FORM:
			_log("setCurrentView start TextScreen");
			final Intent showText = new Intent(getBaseContext(), TextScreen.class);
			showText.putExtra("SUBID", subCommand);
			startActivityForResult(showText, which); 
			break;
			
		case WMAN_FORM:
			_log("setCurrentView start WinManager");
			final Intent showWman = new Intent(getBaseContext(), WinManager.class);
			startActivity(showWman); 
			break;

		case LOG_FORM:
			_log("setCurrentView start TextScreen (LOG)");
			final Intent showLog = new Intent(getBaseContext(), TextScreen.class);
			showLog.putExtra("SUBID", "__LOG__");
			startActivityForResult(showLog, which); 
			break;
		}
	}
	
	public void setPrevView(int which) {
		_log("setPrevView " + which);
		prevForm = which;
	}

	// Collect data from Search Form
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		_log("onActivityResult " + requestCode);

		if (requestCode == SEARCH_FORM) {
			if (resultCode == RESULT_OK) {

				String connName   = intent.getStringExtra(CONN_NAME);
				String connPass   = intent.getStringExtra(CONN_PASS);
				String connAddr   = intent.getStringExtra(CONN_ADDR);
				String act        = intent.getStringExtra(ACTION);

				if (act != null && act.length() > 0) {
					if (act.contentEquals("exit")) {
						doExit();
					} else if (act.contentEquals("log")) {
						// show log
						setCurrentView(LOG_FORM, "");
					}
				} else if (connAddr != null && connAddr.length() > 0) {
					setProgressBarIndeterminateVisibility(true);	
					protocol.doConnect(connName, connAddr, connPass);
				} else {
					setCurrentView(DUMMY_FORM, "");
				}

			} else if (resultCode == RESULT_CANCELED) {
				_log("onActivityResult RESULT_CANCELED");
				// Handle cancel
				//super.onBackPressed();
			}

		} else if (requestCode == CONTROL_FORM) {

			// we should come here on if user chooses "Exit"
			_log("onActivityResult CONTROL_FORM EXIT (cur form is "+currForm+")");

			String act = intent.getStringExtra(ACTION);

			if (act != null && act.length() > 0) {
				if (act.contentEquals("exit")) {
					doExit(); 
				} else if (act.contentEquals("disconnect")) {
					protocol.disconnect(true);
				} else if (act.contentEquals("log")) {               	
					setCurrentView(LOG_FORM,"");
				} else if (act.contentEquals("close")) {               	
					//setCurrentView(DUMMY_FORM,"");
				}
			}

		} else if (requestCode == LIST_FORM ||
				   requestCode == TEXT_FORM ||
				   requestCode == WMAN_FORM) {

			_log("onActivityResult LIST/TEXT/WMAN");
			
		} else if (requestCode == LOG_FORM) {
			_log("onActivityResult LOG ->"+prevForm);
			if (prevForm  == LOG_FORM) {
				prevForm = CONTROL_FORM; // still have issues with activity stop/start synchronizations
			}
			setCurrentView(prevForm, "show");
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.clear();
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) { 

		menu.clear();
		MenuInflater mi = getMenuInflater();

		if (status == DISCONNECTED) { 
			mi.inflate(R.menu.menu, menu);
		} else {
			mi.inflate(R.menu.menu2, menu);
		}

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch(item.getItemId()) {

		case R.id.connect_main:
			_log("onOptionsItemSelected connect_main");
			setCurrentView(SEARCH_FORM,"");
			return true;

		case R.id.disconnect_main:
			protocol.disconnect(true);
			return true;

		case R.id.exit_main:
			finishFlag = true;
			_log("onOptionsItemSelected exit_main");
			setCurrentView(NO_FORM,"");
			protocol.disconnect(true);
			finish();
			return true;

		case R.id.log_main:
			_log("onOptionsItemSelected log_main");
			setCurrentView(LOG_FORM,"");
			return true;					
		}

		// else - user defined items
		//cScreen.commandAction(item.getTitle().toString());
		return true;
	}
	
	public static void sendGlobal(int id, Object obj) {
		if (globalHandler != null) {
			Message msg = globalHandler.obtainMessage(id, obj);
		    msg.sendToTarget();
		}
	}
	
	// get messages sent from sendGlobal()
	public boolean handleMessage(Message msg) {

		switch(msg.what){
		
		case anyRemote.CONNECTED:
			
			anyRemote._log("handleMessage: CONNECTED");
			//Toast.makeText(client, R.string.connection_successful, Toast.LENGTH_SHORT).show();
			
			protocol.connected((Connection) msg.obj);
			handleEvent(anyRemote.CONNECTED);
			break;
			
		case anyRemote.DISCONNECTED:
			
			anyRemote._log("handleMessage: DISCONNECTED");
			
			if (msg.obj != null ) {
				Resources res = getResources();
				
				String alert = res.getString(R.string.connection_failed);
				if (((String) msg.obj).length() > 0) {
					alert += "\n"+(String) msg.obj;
				}
			    Toast.makeText(this, alert, Toast.LENGTH_LONG).show();
			}
			
			protocol.disconnected();
			handleEvent(anyRemote.DISCONNECTED);
			break;	
			
		case anyRemote.COMMAND:
			
			//anyRemote._log("handleMessage: COMMAND");
			protocol.handleCommand((ProtocolMessage) msg.obj);
			break;			
		}
		return true;
	}
	
	public void handleEvent(int what) {
		
		_log("handleEvent");
		switch (what) {
		case CONNECTED:
			_log("handleEvent: Connection established");

			status = CONNECTED;
			setProgressBarIndeterminateVisibility(false);

			if (currForm != LOG_FORM) {
			    setCurrentView(CONTROL_FORM,"");
			}
			break;

		case DISCONNECTED:
			_log("handleEvent: Connection lost");
			status = DISCONNECTED;
			//protocol.closeCurrentScreen(currForm);
			
			if (!finishFlag) {   // this happens on exit
				
				// send quit to all registered activity
				protocol.sendToActivity(-1, Dispatcher.CMD_CLOSE,ProtocolMessage.FULL);

				if (currForm != LOG_FORM) {
					currForm = DUMMY_FORM;  // trick
				}
			}

			if (currForm != LOG_FORM) {
			    setCurrentView(SEARCH_FORM,"");
			}
			break;
		default:
			_log("handleEvent: unknown event");
		}
	}

	public static int icon2int(String btn) {

		if (btn == null) return R.drawable.icon;

		if (btn.equals("default"))  return R.drawable.def;
		if (btn.equals("down")) 	return R.drawable.down;
		if (btn.equals("file")) 	return R.drawable.file;
		if (btn.equals("fit")) 		return R.drawable.fit;
		if (btn.equals("folder")) 	return R.drawable.folder;
		if (btn.equals("forward")) 	return R.drawable.forward;
		if (btn.equals("fullscreen")) return R.drawable.fullscreen;
		if (btn.equals("info")) 	return R.drawable.info;
		if (btn.equals("left")) 	return R.drawable.left;
		if (btn.equals("minus")) 	return R.drawable.minus;
		if (btn.equals("mute")) 	return R.drawable.mute;
		if (btn.equals("next")) 	return R.drawable.next;
		if (btn.equals("no")) 		return R.drawable.no;
		if (btn.equals("pause")) 	return R.drawable.pause;
		if (btn.equals("play")) 	return R.drawable.play;
		if (btn.equals("plus")) 	return R.drawable.plus;
		if (btn.equals("prev")) 	return R.drawable.prev;
		if (btn.equals("question")) return R.drawable.question;
		if (btn.equals("refresh")) 	return R.drawable.refresh;
		if (btn.equals("rewind")) 	return R.drawable.rewind;
		if (btn.equals("right")) 	return R.drawable.right;
		if (btn.equals("stop")) 	return R.drawable.stop;
		if (btn.equals("up")) 		return R.drawable.up;
		if (btn.equals("vol_down")) return R.drawable.vol_down;
		if (btn.equals("vol_up")) 	return R.drawable.vol_up;
		
		if (btn.equals("click_icon")) return R.drawable.click_icon;
		if (btn.equals("transparent")) return R.drawable.transparent;

		return R.drawable.icon;
	}

	public static Bitmap getIconBitmap(Resources resources, String icon) {
		
		if (icon.equals("none")) {	
			return null;
		}
		
		synchronized (iconMap) {	
		
			if (iconMap.containsKey(icon)) {
				return (Bitmap) iconMap.get(icon);
			}
			
			int iconId = icon2int(icon);
	
			//_log("getIconBitmap "+icon+" "+iconId);
			if (iconId == R.drawable.icon) {
	
				File dir = Environment.getExternalStorageDirectory();
				File iFile = new File(dir, "Android/data/anyremote.client.android/files/"+icon+".png");
	
				if(iFile.canRead()) {
					_log("getIconBitmap", icon+" found on SDCard"); 
					Bitmap ic = BitmapFactory.decodeFile(iFile.getAbsolutePath());
					iconMap.put(icon,ic);
					return ic;
				} else {
					_log("getIconBitmap", iFile.getAbsolutePath()+" absent on SDCard");
					
					// try to auto upload it
					protocol.autoUpload(icon);
					return null;
				}
			}
			
			Bitmap ic = BitmapFactory.decodeResource(resources, icon2int(icon));
			iconMap.put(icon,ic);
			return ic;
		}
	}

	public static int parseColor(String r, String g, String b) {
		int[] RGB = new int[3];
		try {
			RGB[0] = Integer.parseInt(r);
			RGB[1] = Integer.parseInt(g);
			RGB[2] = Integer.parseInt(b);

			int i;
			for (i=0;i<2;i++) {
				if (RGB[i]<0  ) RGB[i] = 0;
				if (RGB[i]>255) RGB[i] = 255;
			}
		} catch (Exception e) { 
			RGB[0] = 0;
			RGB[1] = 0;
			RGB[2] = 0;
		}
		return Color.rgb(RGB[0], RGB[1], RGB[2]);
	}	

	void doExit() {
		// how to do exit ?
		_log("doExit");
		finishFlag = true;
		setCurrentView(NO_FORM, "");
		protocol.disconnect(true);
		super.onBackPressed();
	}

	public static int getCurScreen() {
		return currForm;
	}
	
	public static String getScreenStr(int form) {
		switch (form) {
            case NO_FORM:      return "NO";
            case SEARCH_FORM:  return "SEARCH";
            case CONTROL_FORM: return "CONTROL";
            case FMGR_FORM:    return "FMGR";
            case TEXT_FORM:    return "TEXT";
            case LIST_FORM:    return "LIST";
            case EDIT_FORM:    return "EDIT";
            case WMAN_FORM:    return "WMAN";
            case LOG_FORM:     return "LOG";
            case DUMMY_FORM:   return "DUMMY";
		}
		return "UNKNOWN";
	}
	
	public static void popup(Activity cxt, boolean show, String msg) {
		_log("popup " + show);
		
		//cxt.setProgressBarIndeterminateVisibility(show);
		if (waiting != null) {
			waiting.dismiss();
			waiting = null; 
		}
		if (show) {
			if (waiting == null) {
				waiting = new ProgressDialog(cxt, ProgressDialog.STYLE_HORIZONTAL);
			}
			waiting.setMessage(msg);
			waiting.show();
		} 
	}
	
	public static String getLocalIpAddress() {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
				
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					
					if (!(inetAddress.isLoopbackAddress() || 
					     inetAddress.getHostAddress().contains(":"))) {  // avoid IPv6 addresses
						return inetAddress.getHostAddress();
					}
				}
			}
		} catch (SocketException ex) {
			return null;
		}
		return null;
	}

	public static boolean logVisible() {
		return (currForm == LOG_FORM);
	}

	private static void _log(String log) {
		_log("anyRemote", log);	
	}

	public static void _log(String prefix, String msg) {
		if (logData.length() > LOG_CAPACITY) {
			logData.delete(0,LOG_CAPACITY);		
		}
		
		teraz.setTime(java.lang.System.currentTimeMillis());
		logData.append("\n" + "[" + now_format.format(teraz) + "] ["+prefix+"] "+msg);
		Log.i(prefix,msg);
	}
}