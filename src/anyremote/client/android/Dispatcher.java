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

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.view.Display;
import android.view.WindowManager;
import anyremote.client.android.Connection.IConnectionListener;
import anyremote.client.android.util.Address;
import anyremote.client.android.util.ControlScreenHandler;
import anyremote.client.android.util.ISocket;
import anyremote.client.android.util.ListHandler;
import anyremote.client.android.util.ListItem;
import anyremote.client.android.util.ProtocolMessage;
import anyremote.client.android.util.TextHandler;
import anyremote.client.android.util.UserException;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public class Dispatcher implements IConnectionListener {

	static final int CMD_NO       	= 0;	// commands
	static final int CMD_BG     	= 1;
	static final int CMD_EFIELD     = 2;
	static final int CMD_FG     	= 3;
	static final int CMD_FMAN     	= 4;
	static final int CMD_FONT     	= 5;
	static final int CMD_FSCREEN    = 6;
	static final int CMD_ICONLIST 	= 7;
	static final int CMD_ICONS      = 8;
	static final int CMD_LIST     	= 9;
	static final int CMD_MENU     	= 10;
	static final int CMD_PARAM      = 11;
	static final int CMD_REPAINT    = 12;
	static final int CMD_SKIN     	= 13;
	static final int CMD_STATUS     = 14;
	static final int CMD_TEXT     	= 15;
	static final int CMD_TITLE      = 16;
	static final int CMD_IMAGE  	= 17;
	static final int CMD_VIBRATE    = 18;
	static final int CMD_VOLUME     = 19;
	static final int CMD_COVER      = 20;
	static final int CMD_POPUP      = 21;
	
	static final int CMD_GETSCRSIZE = 51;
	static final int CMD_GETPLTF    = 52;
	static final int CMD_GETICON    = 53;
	static final int CMD_GETCVRSIZE = 54;
	static final int CMD_GETVER     = 55;
	static final int CMD_GETCURSOR  = 56;
	static final int CMD_GETPASS    = 57;
	static final int CMD_GETPING    = 58;

	static final int CMD_CLOSECONN  = 101;
	//static final int CMD_EXIT       = 102;

	static final int  CMD_EDIT_FORM_IP    = 103; // Internal pseudo-commands
	static final int  CMD_EDIT_FORM_BT    = 104;
	static final int  CMD_EDIT_FORM_NAME  = 105;
	static final int  CMD_EDIT_FORM_PASS  = 106;

	static final int  CMD_CLOSE  = 110;

	static final int SIZE_SMALL    = 10;
	static final int SIZE_MEDIUM   = 15;
	static final int SIZE_LARGE    = 25;

	ArrayList<Handler> handlers;

	Connection   connection = null;
	anyRemote    context    = null;
	boolean      autoPass   = false;

	String      currentConnection = "";
	String      currentConnName   = "";
	String      currentConnPass   = "";
	boolean     fullscreen = false;

	// Control Screen stuff
	Vector<String> cfMenu = new Vector<String>();
	ControlScreenHandler cfHandler = null;
	int    cfSkin;
	String cfTitle;
	String cfStatus;
	String cfCaption;
	String [] cfIcons;
	String cfUpEvent;
	String cfDownEvent;
	int    cfInitFocus;
	int    cfFrgr;
	int    cfBkgr;
	Bitmap cfCover;
	float  cfFSize;
	Typeface cfTFace;

	// List Screen stuff
	String listTitle;
	Vector<String> listMenu = new Vector<String>();
	ListHandler listHandler = null;
	ArrayList<ListItem> listContent = null;

	// Text Screen stuff
	String textTitle;
	Vector<String> textMenu = new Vector<String>();
	TextHandler textHandler = null;
	String      textContent;
	int         textFrgr;
	int         textBkgr;
	float       textFSize;
	Typeface    textTFace;

	// telephony handler
	PhoneManager phoneManager;
	
	// Popup stuff
	boolean popupState     = false;
    StringBuilder popupMsg = new StringBuilder(16);
	
	boolean connectBT = false;

	public Dispatcher(anyRemote ctx) {

		log("Dispatcher::Dispatcher"); 

		handlers = new ArrayList<Handler>();
		context = ctx;

		listContent = new ArrayList<ListItem>();
		cfIcons     = new String[ControlScreen.NUM_ICONS];
		
		setDefValues();
		
		TelephonyManager telephonyManager = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);		
		phoneManager = new PhoneManager(ctx, this);
		telephonyManager.listen(phoneManager, PhoneStateListener.LISTEN_CALL_STATE);
	}
	
	public void setDefValues() {
		
		for (int i=0;i<ControlScreen.NUM_ICONS;i++) {
			cfIcons[i] = "default";
		}
		cfSkin = ControlScreen.SK_DEFAULT;
		cfUpEvent   = "UP";
		cfDownEvent = "DOWN";
		cfInitFocus = 5;
		cfFrgr = anyRemote.parseColor("255","255","255");
		cfBkgr = anyRemote.parseColor("0",  "0",  "0");		
		cfTitle   = "";
		cfStatus  = "";
		cfCaption = "";		
		cfCover = null;
		cfFSize = SIZE_MEDIUM;
		cfTFace = Typeface.defaultFromStyle(Typeface.NORMAL);

		textFrgr = anyRemote.parseColor("255","255","255");
		textBkgr = anyRemote.parseColor("0",  "0",  "0");
		textContent = "";
		textFSize = SIZE_MEDIUM;
		textTFace = Typeface.defaultFromStyle(Typeface.NORMAL);
		
		autoPass  = false;
	}
	
	public void doConnect(String name, String host, String pass) {

		log("doConnect " + (host!=null?host:"NULL")+" P=>"+pass+"<");
		if (connection != null && !connection.isClosed()) {
			log("doConnect already connected to " + currentConnection);
			notifyHandlers(anyRemote.CONNECTED);
			return;
		}

		if (host != null && host.length() > 0) {

			currentConnName = name;
			currentConnPass = pass;

			if (host.startsWith("socket://")) {	
				currentConnection = host;	    	
				connectWifi(host);
				return;
			} else if (host.startsWith("btspp://")) {
				currentConnection = host;	    	
				connectBluetooth(host);
				return;
			} 
		}

		//log("doConnect: cannot establish");
		notifyHandlers(anyRemote.DISCONNECTED);
	}

	/**
	 * connects to a wifi remote anyRemote server
	 * @param hostname the host to connect to
	 * @param port the port to connect to
	 * @param clientInfo client info describing this client
	 */
	public void connectWifi(String host){
		log("connectWifi");
		connectBT = false;
		MainLoop.schedule(new ConnectTask(ConnectTask.WIFI, host, this));
	}

	/**
	 * connects to a bluetooth remote anyRemote server
	 * @param hostname the host to connect to
	 * @param clientInfo client info describing this client
	 */
	public void connectBluetooth(String host){
		log("connectBluetooth");
		connectBT = true;
		MainLoop.schedule(new ConnectTask(ConnectTask.BLUETOOTH, host, this));
	}

	/**
	 * disconnects from the server
	 * does nothing if not connected
	 */
	public void disconnect(boolean full) {

		log("disconnect");

		if (connection != null){
			connection.close();
			autoPass = false;
		}

		if (full) {         // real close
			currentConnection = "";
			setDefValues();
		} // else           // pause connection
		notifyHandlers(anyRemote.DISCONNECTED);
	}

	public void resumeConnection(){

		log("resumeConnection");

		if (currentConnection.length() > 0) {      
			log("resumeConnection to "+currentConnection);
			doConnect(currentConnName, currentConnection, currentConnPass);
			return;
		}	
		notifyHandlers(anyRemote.DISCONNECTED);	
	}

	public void pauseConnection(){
		log("pauseConnection");
		disconnect(false);
	}

	@Override
	public void notifyConnected(Connection conn) { 
		log("notifyConnected");

		// check BT connection
		if (connectBT) {
			try {
				ComponentName comp = new ComponentName(context, this.getClass());
				PackageInfo pinfo = context.getPackageManager().getPackageInfo(comp.getPackageName(), 0);
				sendMessage("Version(,"+pinfo.versionName);
			} catch (android.content.pm.PackageManager.NameNotFoundException e) {
				sendMessage("Version(,unknown)");
			}
		}

		connection = conn;		
		notifyHandlers(anyRemote.CONNECTED, "");
	}

	@Override
	public void notifyDisconnected(ISocket sock, UserException reason) {
		log("notifyDisconnected " + reason.getDetails());
		
		notifyHandlers(anyRemote.DISCONNECTED);
		
		setDefValues();		
	}

	@Override
	public void notifyMessage(int id, Vector cmdTokens, int stage) {

		log("notifyMessage " + id + " " + cmdTokens+"(cur screen is "+anyRemote.getCurScreen()+")");

		switch (id) {

		case CMD_CLOSECONN:
			disconnect(true); 
			break;

			/*case CMD_EXIT:
			controller.exit();
			break;*/

		case CMD_BG:
		case CMD_FG:
		case CMD_FONT:
		case CMD_ICONS:
		case CMD_SKIN:
		case CMD_STATUS:
		case CMD_TITLE:
		case CMD_VOLUME:
		case CMD_COVER:

			// Create activity with (possibly) empty list
			if (anyRemote.getCurScreen() == anyRemote.CONTROL_FORM) {
				sendToControlScreen(id,cmdTokens,stage);
			} else {
				// Start list screen activity and transfer data
				context.setCurrentView(anyRemote.CONTROL_FORM, "");
				sendToControlScreen(id,cmdTokens,stage);
			}			
			break;

		case CMD_EFIELD:			

			if (anyRemote.getCurScreen() == anyRemote.CONTROL_FORM) {
				sendToControlScreen(id,cmdTokens,stage);
			} else if (anyRemote.getCurScreen() == anyRemote.LIST_FORM) {
				sendToListScreen(id,cmdTokens,stage);
			} else if (anyRemote.getCurScreen() == anyRemote.TEXT_FORM) {
				sendToTextScreen(id,cmdTokens,stage);
			}

			// result will be handled in handleEditFieldResult()
			break; 

		case CMD_FMAN:
			//controller.cScreen.setData(anyRemote.FMGR_FORM,cmdTokens,stage);
			break;  


		case CMD_FSCREEN:
			//controller.cScreen.setFullScreen((String) cmdTokens.elementAt(1));
			if (anyRemote.getCurScreen() == anyRemote.CONTROL_FORM) {
				sendToControlScreen(id,cmdTokens,stage);
			} else if (anyRemote.getCurScreen() == anyRemote.LIST_FORM) {
				sendToListScreen(id,cmdTokens,stage);
			} else if (anyRemote.getCurScreen() == anyRemote.TEXT_FORM) {
				sendToTextScreen(id,cmdTokens,stage);
			}
			break;   

		case CMD_ICONLIST:
		case CMD_LIST:

			// Create activity with (possibly) empty list
			if (anyRemote.getCurScreen() == anyRemote.LIST_FORM) {
				
				if (((String) cmdTokens.elementAt(1)).equals("close")) { 
					if (cmdTokens.size() > 2 && ((String) cmdTokens.elementAt(2)).equals("clear")) {
						listContent.clear();
					}
					context.setCurrentView(anyRemote.CONTROL_FORM, "");
					return;
				}

				sendToListScreen(id,cmdTokens,stage);
			} else {
				if (((String) cmdTokens.elementAt(1)).equals("close")) {  // skip
					if (cmdTokens.size() > 2 && ((String) cmdTokens.elementAt(2)).equals("clear")) {
						listContent.clear();
					}
					return;
				}
				// Start list screen activity and transfer data
				context.setCurrentView(anyRemote.LIST_FORM, (String) cmdTokens.get(1));
				sendToListScreen(id,cmdTokens,stage);
			}			
			break;  

		case CMD_MENU:

			if (anyRemote.getCurScreen() == anyRemote.CONTROL_FORM) {
				sendToControlScreen(id,cmdTokens,stage);
			} else if (anyRemote.getCurScreen() == anyRemote.LIST_FORM) {
				sendToListScreen(id,cmdTokens,stage);
			} else if (anyRemote.getCurScreen() == anyRemote.TEXT_FORM) {
				sendToTextScreen(id,cmdTokens,stage);
			//} else {
				//screen.setMenu(cmdTokens);
			}
			break; 

		case CMD_POPUP:
			
			if (anyRemote.getCurScreen() == anyRemote.CONTROL_FORM) {
				sendToControlScreen(id,cmdTokens,stage);
			} else if (anyRemote.getCurScreen() == anyRemote.LIST_FORM) {
				sendToListScreen(id,cmdTokens,stage);
			} else if (anyRemote.getCurScreen() == anyRemote.TEXT_FORM) {
				sendToTextScreen(id,cmdTokens,stage);
			//} else {
				//screen.setMenu(cmdTokens);
			}

			break; 

		case CMD_PARAM:
			//controller.setParam(cmdTokens);
			break;

		case CMD_REPAINT:
			//screen.drawSync();
			break;

		case CMD_TEXT:

			// Create activity with (possibly) empty list
			if (anyRemote.getCurScreen() == anyRemote.TEXT_FORM) {
				if (((String) cmdTokens.elementAt(1)).equals("close")) { 
					if (cmdTokens.size() > 2 && ((String) cmdTokens.elementAt(2)).equals("clear")) {
						listContent.clear();
					}
					
					context.setCurrentView(anyRemote.CONTROL_FORM, "");
					return;
				}

				sendToTextScreen(id,cmdTokens,stage);
			} else {
				if (((String) cmdTokens.elementAt(1)).equals("close")) {  // skip
					if (cmdTokens.size() > 2 && ((String) cmdTokens.elementAt(2)).equals("clear")) {
						textContent = "";
					}
					return;
				}
				// Start list screen activity and transfer data
				context.setCurrentView(anyRemote.TEXT_FORM, (String) cmdTokens.get(1));
				sendToTextScreen(id,cmdTokens,stage);
			}			
			break;       

		case CMD_VIBRATE:
			Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
			v.vibrate(500);
			break;

		case CMD_IMAGE:
			//controller.cScreen.setData(anyRemote.WMAN_FORM,cmdTokens,stage);
			break;    

		case CMD_GETSCRSIZE:

			Display display = context.getWindowManager().getDefaultDisplay(); 

			sendMessage("SizeX("+display.getWidth() +",)");
			sendMessage("SizeY("+display.getHeight()+",)");
			break;

		case CMD_GETCVRSIZE:

			Display d = context.getWindowManager().getDefaultDisplay(); 
			queueCommand("CoverSize("+(d.getWidth()*2)/3+",)");
			break;

		case CMD_GETVER:
			try {
				ComponentName comp = new ComponentName(context, this.getClass());
				PackageInfo pinfo = context.getPackageManager().getPackageInfo(comp.getPackageName(), 0);
				sendMessage("Version(,"+pinfo.versionName);
			} catch (android.content.pm.PackageManager.NameNotFoundException e) {
				sendMessage("Version(,unknown)");
			}          
			break;

		case CMD_GETPING:
			sendMessage("Ping");
			break;

		case CMD_GETPASS:

			log("CMD_GETPASS");

			// strip possible \n
			if (currentConnPass.length() > 0 && currentConnPass.charAt(currentConnPass.length() - 1) == '\n') {
				currentConnPass = currentConnPass.substring(0, currentConnPass.length() - 1);
			}
			if (currentConnPass.length() > 0 && currentConnPass.charAt(0) == '\n') {
				currentConnPass = currentConnPass.substring(1);
			}

			if (autoPass || currentConnPass.equals("")) {
				log("ASK FOR PASS");
				if (anyRemote.getCurScreen() == anyRemote.LIST_FORM) {
					sendToListScreen(id,cmdTokens,stage);
				} else if (anyRemote.getCurScreen() == anyRemote.TEXT_FORM) {
					sendToTextScreen(id,cmdTokens,stage);
				} else /* if (anyRemote.currForm == anyRemote.CONTROL_FORM)*/ {
					sendToControlScreen(id,cmdTokens,stage);
				}
				//result will be handled in handleEditFieldResult()
			} else {
				log("USE PASS >"+currentConnPass+"<");
				autoPass = true;
				queueCommand("_PASSWORD_(,"+currentConnPass+")");
			}
			break;

		case CMD_GETCURSOR:
			/*if (controller.cScreen.wm != null) {
             			controller.cScreen.wm.sendCursorPos();
            }*/
			break;

		case CMD_GETPLTF:
			sendMessage("Model("+android.os.Build.MODEL+"/Android-"+android.os.Build.VERSION.RELEASE);
			break;

		case CMD_GETICON:
			String size = (String) cmdTokens.elementAt(1);
			String icon = (String) cmdTokens.elementAt(2);		
			
			int iconId = anyRemote.icon2int(icon);
			
			boolean isExists = false;
			if (iconId == R.drawable.icon) {	// no such icon
				File dir = Environment.getExternalStorageDirectory();
				File iFile = new File(dir, "Android/data/anyremote.client.android/files/"+icon+".png");
				isExists = iFile.canRead();
			} else {
				isExists = true;
		    }
			
			String resp;
			if (isExists) {
				resp = "IconExists("+size+","+icon+")";
			} else {
				resp = "IconNotExists("+size+","+icon+")";
			}
			queueCommand(resp);
			break;

		default:
			log("notifyMessage: Command or handler unknown");
		}
	}

	void closeCurrentScreen(int screen) {
		if (screen == anyRemote.LIST_FORM) {
			Vector tokens = new Vector();
			tokens.add(screen);
			tokens.add("close");
			sendToListScreen(CMD_LIST,tokens,ProtocolMessage.FULL);
		}
	}

	public void sendToControlScreen(int id, Vector cmdTokens, int stage) {
		//log("sendToControlScreen "+id);
		int num = 0;
		while (cfHandler == null) {
			log("sendToControlScreen handler not set "+cmdTokens);

			// do not close closed control form
			if (cmdTokens.size() > 0 && ((Integer) cmdTokens.elementAt(0)) == CMD_CLOSE) {  // skip
				return;
			}

			try {
				Thread.sleep(1000);
			} catch(Exception e) {
				Thread.yield();
			}
			if (num>8) {
				log("sendToControlScreen SKIP EVENT");
				return;
			}
			num++;
		}
		ProtocolMessage pm = new ProtocolMessage();
		pm.tokens = cmdTokens;
		pm.stage  = stage;
		Message msg = cfHandler.obtainMessage(id, pm);
		//log("sendToControlScreen SEND");
		msg.sendToTarget();
	}

	public void sendToListScreen(int id, Vector cmdTokens, int stage) {
		//log("sendToListScreen "+id);
		int num = 0;
		while (listHandler == null) {
			log("sendToListScreen handler not set "+cmdTokens);

			// do not close closed list
			if (cmdTokens.size() > 1 && ((String) cmdTokens.elementAt(1)).equals("close")) {  // skip
				if (cmdTokens.size() > 2 && ((String) cmdTokens.elementAt(2)).equals("clear")) {
					listContent.clear();
				}
				return;
			}

			try {
				Thread.sleep(1000);
			} catch(Exception e) {
				Thread.yield();
			}
			if (num>8) {
				log("sendToListScreen SKIP EVENT");
				return;
			}
			num++;
		}
		ProtocolMessage pm = new ProtocolMessage();
		pm.tokens = cmdTokens;
		pm.stage  = stage;
		Message msg = listHandler.obtainMessage(id, pm);
		//log("sendToListScreen SEND");
		msg.sendToTarget();
	}

	public void sendToTextScreen(int id, Vector cmdTokens, int stage) {
		//log("sendToTextScreen "+id);
		int num = 0;
		while (textHandler == null) {
			log("sendToTextScreen handler not set "+cmdTokens);
			// do not close closed test
			if (cmdTokens.size() > 1 && ((String) cmdTokens.elementAt(1)).equals("close")) {  // skip
				if (cmdTokens.size() > 2 && ((String) cmdTokens.elementAt(2)).equals("clear")) {
					textContent = "";
				}
				return;
			}

			try {
				Thread.sleep(1000);
			} catch(Exception e) {
				Thread.yield();
			}
			if (num>8) {
				log("sendToTextScreen SKIP EVENT");
				return;
			}
			num++;
		}
		ProtocolMessage pm = new ProtocolMessage();
		pm.tokens = cmdTokens;
		pm.stage  = stage;
		Message msg = textHandler.obtainMessage(id, pm);
		//log("sendToTextScreen SEND");
		msg.sendToTarget();
	}

	public void handleEditFieldResult(int id, String button, String value) {
		if (id == Dispatcher.CMD_GETPASS) {
			queueCommand("_PASSWORD_(,"+value+")");
			setPassForConnection(value);
		} else {
			queueCommand(button + "(0," + value + ")");
		}
	}

	public void setPassForConnection(String pass) {

		SharedPreferences preference = context.getPreferences(Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = preference.edit();

		String UP = currentConnection + "\n" + pass;

		editor.putString(currentConnName, UP);
		editor.commit();
	}

	public Vector<Address> loadPrefs() {

		SharedPreferences preference = context.getPreferences(Context.MODE_PRIVATE);
		Vector<Address>  addressesA = new Vector<Address>();

		try {
			Map<String, String> datam = (Map<String, String>) preference.getAll();    

			Iterator it = datam.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry pairs = (Map.Entry)it.next();

				String url_pass = (String)  pairs.getValue();
				int p = url_pass.lastIndexOf('\n');
				if (p > 0) {
					Address a = new Address();

					a.name  = (String) pairs.getKey();
					a.URL =  url_pass.substring(0, p);
					a.pass = url_pass.substring(p);

					addressesA.add(a);
				}
			}		    
		} catch(Exception z) { }

		return addressesA;
	}

	public void cleanAddress(String name) {
		//log("cleanAddress "+name);

		SharedPreferences preference = context.getPreferences(Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = preference.edit();
		editor.remove(name);
		editor.commit();
	}

	// save new address in preferences
	public void addAddress(String name, String URL, String pass) {		        
		//log("addAddress "+name+"/"+URL+"/"+pass);

		SharedPreferences preference = context.getPreferences(Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = preference.edit();

		String UP = URL + "\n" + pass;

		editor.putString(name, UP);
		editor.commit();
	}

	public void sendMessage(String command) {
		if (connection != null && !connection.isClosed()) {
			//log("sendMessage " + command);
		    connection.send(command);
		}
	}

	public void queueCommand(int keycode, boolean pressed) {

		//log("queueCommand "+keycode +" " + pressed);

		String key = "";

		switch (keycode) {
		case ControlScreen.KEY_NUM1:  key = "1";break;
		case ControlScreen.KEY_NUM2:  key = "2";break;
		case ControlScreen.KEY_NUM3:  key = "3";break;
		case ControlScreen.KEY_NUM4:  key = "4";break;
		case ControlScreen.KEY_NUM5:  key = "5";break;
		case ControlScreen.KEY_NUM6:  key = "6";break;
		case ControlScreen.KEY_NUM7:  key = "7";break;
		case ControlScreen.KEY_NUM8:  key = "8";break;
		case ControlScreen.KEY_NUM9:  key = "9";break;
		case ControlScreen.KEY_STAR:  key = "*";break;
		case ControlScreen.KEY_NUM0:  key = "0";break;
		case ControlScreen.KEY_POUND: key = "#";break;
		default: 
		}

		if (key.length() > 0) {
			sendMessage("+CKEV: " + key + "," + (pressed ? "1" : "0"));
		}
	}

	public void queueCommand(String key, boolean pressed) {

		//log("queueCommand "+key +" " + pressed);

		if (key.length() > 0) {
			sendMessage("+CKEV: " + key + "," + (pressed ? "1" : "0"));
		}
	}

	public void queueCommand(String message) {
		sendMessage("Msg:" + message);
	}

	private void notifyHandlers(int what, Object obj){
		for(Handler h : handlers){
			Message msg = h.obtainMessage(what, obj);
			msg.sendToTarget();
		}
	}

	private void notifyHandlers(int what){
		for(Handler h : handlers){
			Message msg = h.obtainMessage(what);
			msg.sendToTarget();
		}
	}

	public void addHandler(Handler h){
		handlers.add(h);
	}

	public void removeHandler(Handler h){
		handlers.remove(h);
	}

	public void clearHandlers(){
		handlers.clear();
	}

	public void setFullscreen(String option, arActivity act) {

		if (option.startsWith("on")) {
			if (fullscreen) return;
			fullscreen = true;			    	
		} else if (option.startsWith("off")) {
			if (!fullscreen) return;
			fullscreen = false;
		} else if (option.startsWith("toggle")) {
			fullscreen = !fullscreen;
		}
		setFullscreen(act);
	}

	public void setFullscreen(arActivity act) {
		if (fullscreen) {
			act.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
					WindowManager.LayoutParams.FLAG_FULLSCREEN);
			act.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		} else {
			act.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, 
					WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
			act.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
	}
	public void log(String msg) {
		anyRemote._log("Dispatcher",msg);
	}

}
