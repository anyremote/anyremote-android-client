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

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.TimerTask;
import java.util.Vector;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.os.Vibrator;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import anyremote.client.android.util.Address;
import anyremote.client.android.util.InfoMessage;
import anyremote.client.android.util.ListItem;
import anyremote.client.android.util.ProtocolMessage;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public class Dispatcher {

	static final int CMD_NO       	= 0;	// commands
	static final int CMD_BG     	= 1;
	static final int CMD_CAPTION    = 2;
	static final int CMD_EFIELD     = 3;
	static final int CMD_FG     	= 4;
	static final int CMD_FMAN       = 5;
	static final int CMD_FONT     	= 6;
	static final int CMD_FSCREEN    = 7;
	static final int CMD_ICONLIST   = 8;
	static final int CMD_ICONS      = 9;
	static final int CMD_HINTS      = 10;
	static final int CMD_LIST       = 11;
	static final int CMD_MENU       = 12;
	static final int CMD_PARAM      = 13;
	static final int CMD_REPAINT    = 14;
	static final int CMD_LAYOUT  	= 15;
	static final int CMD_STATUS     = 16;
	static final int CMD_TEXT       = 17;
	static final int CMD_TITLE      = 18;
	static final int CMD_IMAGE  	= 19;
	static final int CMD_VIBRATE    = 20;
	static final int CMD_VOLUME     = 21;
	static final int CMD_COVER      = 22;
	static final int CMD_POPUP      = 23;

	static final int CMD_GETSCRSIZE = 51;
	static final int CMD_GETPLTF    = 52;
	static final int CMD_GETIMG     = 53;
	static final int CMD_GETCVRSIZE = 54;
	static final int CMD_GETVER     = 55;
	static final int CMD_GETCURSOR  = 56;
	static final int CMD_GETPASS    = 57;
	static final int CMD_GETPING    = 58;
	static final int CMD_GETICSIZE  = 59;
	static final int CMD_GETPADDING = 60;

	static final int CMD_CLOSECONN  = 101;
	//static final int CMD_EXIT       = 102;

	static final int  CMD_EDIT_FORM_IP    = 103; // Internal pseudo-commands
	static final int  CMD_EDIT_FORM_BT    = 104;
	static final int  CMD_EDIT_FORM_ADDR  = 105;
	static final int  CMD_SEARCH_DIALOG   = 106;
	static final int  CMD_NEW_ADDR_DIALOG = 107;
	static final int  CMD_SENSOR_DIALOG   = 108;

	static final int  CMD_LIST_UPDATE     = 110;

	static final int  CMD_CLOSE  = 120;

	static final int SIZE_SMALL    = 12;
	static final int SIZE_MEDIUM   = 22;
	static final int SIZE_LARGE    = 36;

	static final int MAX_ATTEMPTS  = 100;
	
	static final int NOTUPDATE_NOTSWITCH = 1;
	static final int NOTUPDATE_SWITCH    = 2;
	static final int UPDATE_SWITCH       = 3;
	static final int UPDATE_NOTSWITCH    = 4;
	
	static final String M_SENSOR = "_mouse_sensor_";
	
    public static class ArHandler {
    	
    	public ArHandler(int a,Handler h) {
    		actId  = a;
    		hdl    = h;
    	}
    	
        public int actId   = anyRemote.NO_FORM;
        public Handler hdl = null;
    }
    
    public static class QueueMessage {
    	
    	public int activity;
       	public int id;
    	public int stage;
    	public int attemptsToSend;
    }

	private ArrayList<Handler> handlers;
	
	ArrayList<ArHandler> actHandlers = new ArrayList<ArHandler>();
	
	ArrayList<QueueMessage> msgQueue = new ArrayList<QueueMessage>();
	
	ArrayList<String> autoUploadedI = new ArrayList<String>();
	ArrayList<String> autoUploadedC = new ArrayList<String>();
	
	Connection   connection = null;
	anyRemote    context    = null;
	boolean      autoPass   = false;

	String      currentConnection = "";
	String      currentConnName   = "";
	String      currentConnPass   = "";
	boolean     fullscreen = false;

	// Control Screen stuff
	Vector<String> cfMenu = new Vector<String>();	
	int    cfSkin;
	boolean cfUseJoystick;
	String cfTitle;
	String cfStatus;
	String cfCaption;
	String [] cfIcons;
	String [] cfHints;
	String cfUpEvent;
	String cfDownEvent;
	int    cfInitFocus;
	int    cfFrgr;
	int    cfBkgr;
	Bitmap cfCover;
	String cfNamedCover;
	float  cfFSize;
	Typeface cfTFace;
	String cfVolume;
	int cfPadding;
	int cfIconSize;
	int cfIconSizeOverride;

	// List Screen stuff
	String listTitle;
	int    listSelectPos = -1;
	Vector<String> listMenu = new Vector<String>();
	ArrayList<ListItem> listContent = null;
	boolean listCustomBackColor = false;
	boolean listCustomTextColor = false;
	int     listText;
	int     listBkgr;
	float   listFSize;
	String  listIcon;
	StringBuilder  listBufferedItem;

	// Text Screen stuff
	String textTitle;
	Vector<String> textMenu = new Vector<String>();
	StringBuilder textContent;
	int         textFrgr;
	int         textBkgr;
	float       textFSize;
	Typeface    textTFace;
	
	// Image Screen stuff
	Bitmap      imScreen;
	Vector<String> winMenu = new Vector<String>();
 
	// Mouse Screen stuff
    //Vector<String> mouseMenu = new Vector<String>();
	private boolean useGyroscope = true;

    // Keyboard Screen stuff
    //Vector<String> keyboardMenu = new Vector<String>();
    
    // Web Screen stuff
    String webUrl;
    //Vector<String> webMenu = new Vector<String>();

	// telephony handler
	PhoneManager phoneManager;
	
	// Popup stuff
	boolean popupState     = false;
	StringBuilder popupMsg = new StringBuilder(16);

	// Edit Field stuff
	String efCaption = "";
	String efLabel   = "";
	String efValue   = "";
	int efId; 
	
	boolean connectBT = false;
	int     keepaliveTimeout = -1;
	int     keepaliveCounter = 0;

	public Dispatcher(anyRemote ctx) {

		log("Dispatcher::Dispatcher");

		// TODO: avoid
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
		StrictMode.setThreadPolicy(policy);

		handlers = new ArrayList<Handler>();
		
		context = ctx;

		listContent = new ArrayList<ListItem>();
		cfIcons     = new String[ControlScreen.NUM_ICONS];
		cfHints     = new String[ControlScreen.NUM_ICONS];
		listBufferedItem = new StringBuilder();
		
		textContent = new StringBuilder();
		
		setDefValues();
		
		TelephonyManager telephonyManager = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);		
		phoneManager = new PhoneManager(ctx, this);
		telephonyManager.listen(phoneManager, PhoneStateListener.LISTEN_CALL_STATE);
	}
	
	public void setDefValues() {
		
		for (int i=0;i<ControlScreen.NUM_ICONS;i++) {
			cfIcons[i] = "none";
		}
        for (int i=0;i<ControlScreen.NUM_ICONS;i++) {
            cfHints[i] = "";
        }
		cfSkin = ControlScreen.SK_DEFAULT;
		cfUpEvent   = "UP";
		cfDownEvent = "DOWN";
		cfInitFocus = 4; // index of '5' button
		cfFrgr = Color.parseColor("#FFFFFF");
		cfBkgr = Color.parseColor("#000000");		
		cfTitle   = "";
		cfStatus  = "";
		cfCaption = "";		
		cfCover = null;
		cfNamedCover = "";
		cfIconSizeOverride = -1;
		cfPadding  = 0;
		cfIconSize = -1;
		cfFSize = SIZE_MEDIUM;
		cfTFace = Typeface.defaultFromStyle(Typeface.NORMAL);
		cfMenu.clear();
		menuAddDefault(anyRemote.CONTROL_FORM);
		
		listTitle     = "";
		listIcon      = "";
		listSelectPos = -1;
		listCustomBackColor = false;
		listCustomTextColor = false;
		listContent.clear(); 
		listFSize = -1;
		listMenu.clear();
		menuAddDefault(anyRemote.LIST_FORM);
		listBufferedItem.delete(0, listBufferedItem.length());

		textFrgr = Color.parseColor("#FFFFFF");
		textBkgr = Color.parseColor("#000000");
		textContent.delete(0, textContent.length());
		textFSize = SIZE_MEDIUM;
		textTFace = Typeface.defaultFromStyle(Typeface.NORMAL);
		textMenu.clear();
		menuAddDefault(anyRemote.TEXT_FORM);
 
		imScreen = null;
		winMenu.clear();
		menuAddDefault(anyRemote.WMAN_FORM);
		
        //mouseMenu.clear();
        //menuAddDefault(anyRemote.MOUSE_FORM);
        //keyboardMenu.clear();
        //menuAddDefault(anyRemote.KEYBOARD_FORM);
        //webMenu.clear();
        //menuAddDefault(anyRemote.WEB_FORM);
        
		autoPass  = false;
	}
	public void doConnect(String name, String host, String pass) {

		log("doConnect " + (host!=null?host:"NULL")+" P=>"+pass+"<");
		if (connection != null && !connection.isClosed()) {
			log("doConnect already connected to " + currentConnection);
		    anyRemote.sendGlobal(anyRemote.CONNECTED, "");
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

		log("doConnect: cannot establish");
	    anyRemote.sendGlobal(anyRemote.DISCONNECTED, "");
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

		log("disconnect "+full);
		boolean isConn = (connection != null && !connection.isClosed());
	
		disconnected(full);
		
		if (isConn) {  
			if (full) {
			    anyRemote.sendGlobal(anyRemote.DISCONNECTED, "");
			} else {
				anyRemote.sendGlobal(anyRemote.LOSTFOCUS, "");	
			}
		} else{
			log("disconnect: alredy disconnected");
		}
	}

	public void disconnected(boolean full) {

		if (connection != null) {
			connection.close();
			connection = null;
			autoPass = false;
		}
		
		synchronized (msgQueue) {
			msgQueue.clear();
		}
		
		if (full) {
			currentConnection = "";	
			currentConnName   = "";	
		}
		
		setDefValues();
	}

	public void pauseConnection(){
		log("pauseConnection");
		disconnect(false);
	}

	public void connected(Connection conn) { 
		log("connected");

		// check BT connection
		if (connectBT) {
			try {
				ComponentName comp = new ComponentName(context, this.getClass());
				PackageInfo pinfo = context.getPackageManager().getPackageInfo(comp.getPackageName(), 0);
				queueCommand("Version(,"+pinfo.versionName);
			} catch (android.content.pm.PackageManager.NameNotFoundException e) {
				queueCommand("Version(,unknown)");
			}
		}

		connection = conn;
	}
	
	public static String cmdStr(int cmd) {
		switch (cmd) {
	        case CMD_NO:       return "CMD_NO";
	        case CMD_BG :      return "Set(bg)";
	        case CMD_CAPTION : return "Set(caption)";
	        case CMD_EFIELD:   return "Set(editfield)";
	        case CMD_FG:       return "Set(fg)";
	        case CMD_FMAN:     return "Set(filemanager)";
	        case CMD_FONT:     return "Set(font)";
	        case CMD_FSCREEN:  return "Set(fullscreen)";
	        case CMD_ICONLIST: return "Set(iconlist)";
	        case CMD_ICONS:    return "Set(icons)"; 
	        case CMD_HINTS:    return "Set(hints)";
	        case CMD_LIST:     return "Set(list)";
	        case CMD_MENU:     return "Set(menu)";
	        case CMD_PARAM:    return "Set(param)";
	        case CMD_REPAINT:  return "Set(repaint)";
	        case CMD_LAYOUT:   return "Set(layout)";
	        case CMD_STATUS:   return "Set(status)";
	        case CMD_TEXT:     return "Set(text)";
	        case CMD_TITLE:    return "Set(title)";
	        case CMD_IMAGE:    return "Set(image)";
	        case CMD_VIBRATE:  return "Set(vibrate)";
	        case CMD_VOLUME:   return "Set(volume)";
	        case CMD_COVER:      return "Set(cover)";
	        case CMD_POPUP:      return "Set(popup)";
	        case CMD_GETSCRSIZE: return "Get(screen_size)";
	        case CMD_GETPLTF:    return "Get(model)";
	        case CMD_GETIMG:    return "Get(is_exists)";
	        case CMD_GETCVRSIZE: return "Get(cover_size)";
	        case CMD_GETVER:     return "Get(version)";
	        case CMD_GETCURSOR:  return "Get(cursor)";
	        case CMD_GETPASS:    return "Get(password)";
	        case CMD_GETPING:    return "Get(ping)";
	        case CMD_GETICSIZE:  return "Get(icon_size)";
	        case CMD_GETPADDING: return "Get(icon_padding)";
	        case CMD_CLOSECONN:  return "CMD_CLOSECONN";
		    //case CMD_EXIT:     return "CMD_EXIT";
	        case CMD_EDIT_FORM_IP:   return "CMD_EDIT_FORM_IP";  // used for logging only ?
	        case CMD_EDIT_FORM_BT:   return "CMD_EDIT_FORM_BT";
	        case CMD_LIST_UPDATE:    return "CMD_LIST_UPDATE";
	        case CMD_SEARCH_DIALOG:  return "CMD_SEARCH_DIALOG";
            case CMD_SENSOR_DIALOG:  return "CMD_SENSOR_DIALOG";
	        case CMD_CLOSE:          return "CMD_CLOSE";
		}
		return "UNKNOWN";
	}
	
    public void handleGetCoverSizeCmd() {
	    Display d = context.getWindowManager().getDefaultDisplay(); 
        queueCommand("CoverSize("+(d.getWidth()*2)/3+",)");
    }
    
    public void handleGetScreeenSizeCmd() {
        Display display = context.getWindowManager().getDefaultDisplay(); 
        boolean rotated = (display.getOrientation() == Surface.ROTATION_90 ||
                           display.getOrientation() == Surface.ROTATION_270);
        String ori = (rotated ? "R" : "");
        
        queueCommand("SizeX("+display.getWidth() +","+ori+")");
        queueCommand("SizeY("+display.getHeight()+","+ori+")");
    }
    
	public void handleCommand(ProtocolMessage msg) {

		int id           = msg.id; 
		Vector cmdTokens = msg.tokens; 
		int stage        = msg.stage;
		
		log("handleCommand got:" + cmdStr(id) + " " + cmdTokens+"(cur screen is "+anyRemote.getScreenStr(anyRemote.getCurScreen())+")");

		switch (id) {

		case CMD_CLOSECONN:
		    log("handleCommand got: CMD_CLOSECONN");
			disconnect(true); 
			break;

		/*case CMD_EXIT:
			controller.exit();
			break;*/

		case CMD_BG:
		case CMD_CAPTION:
		case CMD_FG:
		case CMD_FONT:
		case CMD_ICONS:
		case CMD_HINTS:
		case CMD_LAYOUT:
		case CMD_STATUS:
		case CMD_TITLE:
		case CMD_VOLUME:
		case CMD_COVER:
			
			synchronized (cfTitle) {
				controlDataProcess(cmdTokens);
	
				// Create activity with (possibly) empty list
				boolean isActive = (anyRemote.getCurScreen() == anyRemote.CONTROL_FORM);
				if (!(id == CMD_BG || id == CMD_FG ||  id == CMD_FONT) &&
					!isActive) {
					log("handleCommand switch to CONTROL_FORM");
					context.setCurrentView(anyRemote.CONTROL_FORM, "");
				} 
				
				if (isActive || !(id == CMD_BG || id == CMD_FG ||  id == CMD_FONT)) {
			        sendToActivity(anyRemote.CONTROL_FORM,id,stage);
			    }
			}
		    break;

		case CMD_EFIELD:
			
			if (cmdTokens.size() < 4) return;
			
			efCaption = (String) cmdTokens.elementAt(1);
			efLabel   = (String) cmdTokens.elementAt(2);
			efValue   = (String) cmdTokens.elementAt(3);
			efId      = CMD_EFIELD;
			
			sendToActivity(anyRemote.getCurScreen(),id,ProtocolMessage.FULL);
			// CMD_EFIELD: result will be handled in handleEditFieldResult()
			break; 
			
		case CMD_FSCREEN:
			
			setFullscreen((String) cmdTokens.elementAt(1));
			sendToActivity(anyRemote.getCurScreen(),id,ProtocolMessage.FULL);
			break; 

		case CMD_POPUP:
			
			popupState = false;
			popupMsg.delete(0, popupMsg.length());
			
			String op = (String) cmdTokens.elementAt(1);
			
			if (op.equals("show")) { 

				popupState = true;
				
				for (int i=2;i<cmdTokens.size();i++) {
					if (i != 2) {
						popupMsg.append(", ");
					}
					popupMsg.append((String) cmdTokens.elementAt(i));
				}
			}
			
			sendToActivity(anyRemote.getCurScreen(),id,ProtocolMessage.FULL);
			break; 
			
		case CMD_MENU:

			menuProcess(cmdTokens, anyRemote.getCurScreen());
			break; 

		case CMD_FMAN:
			//controller.cScreen.setData(anyRemote.FMGR_FORM,cmdTokens,stage);
			break;  

		case CMD_ICONLIST:
		case CMD_LIST:
            
			// setup List Screen activity persistent data			
			//if (anyRemote.getCurScreen() != anyRemote.LIST_FORM) {
				// by default do not change system colors
			//	listCustomBackColor = false;
			//	listCustomTextColor = false;
			//}
			
			int switchTo = listDataProcess(id, cmdTokens, stage); 
			//log("handleCommand listDataProcess:" + switchTo);
			
			boolean doClose = (cmdTokens.size() > 1 && ((String) cmdTokens.elementAt(1)).equals("close"));
			
			if (anyRemote.logVisible()) {
				if (doClose) {
					context.setPrevView(anyRemote.CONTROL_FORM);
				}
				return;
			}

			if (doClose) { 
				// Close List Activity (even it was not started ;-)) and open ControlForm
				log("handleCommand switch to CONTROL_FORM (L)");
				context.setCurrentView(anyRemote.CONTROL_FORM, "");
				return;
			}
			
			boolean isActive = (anyRemote.getCurScreen() == anyRemote.LIST_FORM);
			
			// Create activity with (possibly) empty list
			if ((switchTo == NOTUPDATE_SWITCH || switchTo == UPDATE_SWITCH) &&
			    !isActive) {
				log("handleCommand switch to LIST_FORM");
				context.setCurrentView(anyRemote.LIST_FORM, "");
			}
			
			int lid = ((switchTo == UPDATE_SWITCH || switchTo == UPDATE_NOTSWITCH) ? CMD_LIST_UPDATE : id);
			
			if (isActive || (switchTo == NOTUPDATE_SWITCH || switchTo == UPDATE_SWITCH)) {
			    sendToActivity(anyRemote.LIST_FORM,lid,stage);
			}
			break;  

		case CMD_PARAM:
			
			for (int i = 1; i < cmdTokens.size(); ) {
				
				String tag = (String) cmdTokens.elementAt(i);
				if (tag.equals("icon_padding")) {
					
					i++;
					if (i >= cmdTokens.size()) return;
					
					log("icon_padding "+((String) cmdTokens.elementAt(i)));
					
					cfPadding= Integer.parseInt(((String) cmdTokens.elementAt(i)));
					
					if (cfPadding < 0) cfPadding = 0;
					
					Display display = context.getWindowManager().getDefaultDisplay(); 
					if (cfPadding >= display.getWidth()/6) cfPadding = display.getWidth()/6;
					
					if (anyRemote.getCurScreen() == anyRemote.CONTROL_FORM) {
						sendToActivity(anyRemote.CONTROL_FORM,id,stage);
					} 
					
				} else if (tag.equals("icon_size")) {
					
					i++;
					if (i >= cmdTokens.size()) return;
					
					cfIconSizeOverride = Integer.parseInt(((String) cmdTokens.elementAt(i)));
					
					if (cfIconSizeOverride < 0) cfIconSizeOverride = -1;   // == do not use
					
					Display display = context.getWindowManager().getDefaultDisplay(); 
					if (cfIconSizeOverride >= display.getWidth()/3) cfIconSizeOverride = display.getWidth()/3;
					
					if (anyRemote.getCurScreen() == anyRemote.CONTROL_FORM) {
						sendToActivity(anyRemote.CONTROL_FORM,id,stage);
					} 
				}
				i++;
			}
			
			break;

		case CMD_REPAINT:
			if (anyRemote.getCurScreen() == anyRemote.CONTROL_FORM) {
				sendToActivity(anyRemote.CONTROL_FORM,id,stage);
			} 
			break;

		case CMD_TEXT:	
			
			boolean needSwitch = textDataProcess(cmdTokens, stage); 
			
			boolean doCloset = (cmdTokens.size() > 1 && ((String) cmdTokens.elementAt(1)).equals("close"));
			
			if (anyRemote.logVisible()) {
				if (doCloset) {
					context.setPrevView(anyRemote.CONTROL_FORM);
				}
				return;
			}
			
			if (doCloset) { 
				// Close Text Activity (even it was not started ;-)) and open ControlForm
				log("handleCommand switch to CONTROL_FORM (T)");
				context.setCurrentView(anyRemote.CONTROL_FORM, "");
				return;
			}
		
			// Create activity with (possibly) empty list
			int scr = anyRemote.getCurScreen();
			if (needSwitch && scr != anyRemote.TEXT_FORM) {
				log("handleCommand switch to TEXT_FORM");
				context.setCurrentView(anyRemote.TEXT_FORM, "");
			}
			
			if (needSwitch || scr == anyRemote.TEXT_FORM) {
			    sendToActivity(anyRemote.TEXT_FORM,id,stage);	
			}
			break;       

		case CMD_VIBRATE:
			
			int vi = 2;
			if (cmdTokens.size() > 1) {				
			    try {
			    	vi = Integer.parseInt((String) cmdTokens.elementAt(1));
				} catch (NumberFormatException e) {
				}          
				if (vi <= 0) {					
					break;
				}
				if (vi > 300) {
					vi = 300;
				}
			}
			Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
			v.vibrate(vi*100);
			break;

		case CMD_IMAGE:
			
			boolean doCloseW = false;
			
			if (cmdTokens.size() < 2) {
				log("Improper image command");
				return;
			}
			
			if (((String) cmdTokens.elementAt(1)).equals("close")) {
				doCloseW = true;
			}
			
			if (anyRemote.logVisible()) {
				if (doCloseW) {
					context.setPrevView(anyRemote.CONTROL_FORM);
				}
				return;
			}

			if (doCloseW) { 
				// Close WinManager Activity (even it was not started ;-)) and open ControlForm
				log("handleCommand switch to CONTROL_FORM (W)");
				context.setCurrentView(anyRemote.CONTROL_FORM, "");
				return;
			}
		
			if (((String) cmdTokens.elementAt(1)).equals("window")) {
				// Create activity
				if (anyRemote.getCurScreen() != anyRemote.WMAN_FORM) {
					log("handleCommand switch to WMAN_FORM");
					context.setCurrentView(anyRemote.WMAN_FORM, "");
				}
				
				sendToActivity(anyRemote.WMAN_FORM,id,stage);
				
			} else if (((String) cmdTokens.elementAt(1)).equals("icon")) {
				
				// redraw ControlForm if it is active
				if (anyRemote.getCurScreen() == anyRemote.CONTROL_FORM) {
					sendToActivity(anyRemote.CONTROL_FORM,id,stage);	
				}
				// redraw ListForm if it is active (list can show icons!)
				if (anyRemote.getCurScreen() == anyRemote.LIST_FORM) {
					sendToActivity(anyRemote.LIST_FORM,id,stage);	
				}
			} else if (((String) cmdTokens.elementAt(1)).equals("cover")) {
				// redraw ControlForm if it is active
				if (anyRemote.getCurScreen() == anyRemote.CONTROL_FORM) {
					sendToActivity(anyRemote.CONTROL_FORM,id,stage);	
				}
			} else if (((String) cmdTokens.elementAt(1)).equals("remove")) {
				if (cmdTokens.size() < 3) {
					log("Improper image remove command");
					return;
				}
				
				String rm = (String) cmdTokens.elementAt(2);
				
				File store = Environment.getExternalStorageDirectory();

                		if (rm.equals("icons") || rm.equals("all")) {
                			log("Remove all downloaded icons");
     					File d = new File(store, "Android/data/anyremote.client.android/files/icons");
    					emptyDir(d);
                		}

                		if (rm.equals("covers") || rm.equals("all")) {
                			log("Remove all downloaded covers Android/data/anyremote.client.android/files/covers");
                			File d = new File(store, "Android/data/anyremote.client.android/files/covers");
                			emptyDir(d);
				}
			} else if (((String) cmdTokens.elementAt(1)).equals("clear_cache")) {
				//log("Clear image cache");
				anyRemote.clearCache();
				clearRequested();
			}
			break;    

		case CMD_GETSCRSIZE:

			handleGetScreeenSizeCmd();
			break;

		case CMD_GETCVRSIZE:

		    handleGetCoverSizeCmd();
			break;

		case CMD_GETICSIZE:
			
			synchronized (cfTitle) {
			    queueCommand("IconSize("+(cfIconSizeOverride >= 0 ? cfIconSizeOverride : cfIconSize)+",)");
			}
			break; 
			
		case CMD_GETPADDING:
			
			synchronized (cfTitle) {
			    queueCommand("IconPadding("+cfPadding+",)");
			}			
			break;

		case CMD_GETVER:
			
			try {
				ComponentName comp = new ComponentName(context, this.getClass());
				PackageInfo pinfo = context.getPackageManager().getPackageInfo(comp.getPackageName(), 0);
				queueCommand("Version(,"+pinfo.versionName+")");
			} catch (android.content.pm.PackageManager.NameNotFoundException e) {
				queueCommand("Version(,unknown)");
			}          
			break;

		case CMD_GETPING:
			
			queueCommand("Ping");
			
			if (cmdTokens.size() > 1) {   // get timeout
				keepaliveTimeout = Integer.parseInt(((String) cmdTokens.elementAt(1)));
			
				if (keepaliveTimeout > 0) {
					scheduleKeepaliveTask();
				}
			}
			
			if (keepaliveTimeout > 0) {
				keepaliveCounter++;
			}
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
				sendToActivity(anyRemote.getCurScreen(),id,stage);
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
			
			queueCommand("Model(,"+android.os.Build.MODEL+"/Android-"+android.os.Build.VERSION.RELEASE+")");
			break;

		case CMD_GETIMG:
			
			// old format Get(is_exists,sz,name)
			// new format Get(is_exists,[icon,sz,name]|cover,name])
			String item2 = (String) cmdTokens.elementAt(1);
			
			boolean isIcon = true;
			String size;
			String name;
			
			if (item2.equals("icon")) {
				size = (String) cmdTokens.elementAt(2);
			    name = (String) cmdTokens.elementAt(3);	
		    } else if (item2.equals("cover")) {
		    	isIcon = false;
		    	size = "";
		    	name = (String) cmdTokens.elementAt(2);	
		    } else {
				size = (String) cmdTokens.elementAt(1);
				name = (String) cmdTokens.elementAt(2);		
			}
			
			if (isIcon) {
				int iconId = anyRemote.icon2int(name);
				
				boolean isExists = false;
				if (iconId == R.drawable.icon) {	// no such icon
					File dir = Environment.getExternalStorageDirectory();
					File iFile = new File(dir, "Android/data/anyremote.client.android/files/icons/"+name+".png");
					isExists = iFile.canRead();
				} else {
					isExists = true;
			    }
				
				String resp;
				if (isExists) {
					resp = "IconExists("+size+","+name+")";
				} else {
					resp = "IconNotExists("+size+","+name+")";
				}
				queueCommand(resp);
			} else {
				boolean isExists = false;
				File dir = Environment.getExternalStorageDirectory();
				File iFile = new File(dir, "Android/data/anyremote.client.android/files/covers/"+name+".png");
				isExists = iFile.canRead();
				
				String resp;
				if (isExists) {
					resp = "CoverExists(,"+name+")";
				} else {
					resp = "CoverNotExists(,"+name+")";
				}
				queueCommand(resp);
			}
			break;

		default:
			
			log("handleCommand: Command or handler unknown");
		}
	}
	
	public synchronized void sendToActivity(int activity, int id, int stage) {
		
		log("sendToActivity to "+anyRemote.getScreenStr(activity)+" "+cmdStr(id));
		
		QueueMessage pm = new QueueMessage();
		pm.activity       = activity;
		pm.id             = id;
		pm.stage          = stage;
		pm.attemptsToSend = 0;
		
		synchronized (msgQueue) {
		    msgQueue.add(0,pm);
		}
		processMessageQueue();
	}
	
	public int messageQueueSize() {
		synchronized (msgQueue) {
		    return msgQueue.size();
		}
	}
		
	private synchronized void processMessageQueue() {
		
		final Iterator<QueueMessage> msgItr = msgQueue.iterator();
		
		while (msgItr.hasNext()) {
			
			final QueueMessage pm = msgItr.next();
			
			boolean sent = false;
			
			synchronized (actHandlers) {
				
			    final Iterator<ArHandler> itr = actHandlers.iterator();
				log("processMessageQueue MSG " + cmdStr(pm.id) + " handlers #" + actHandlers.size());
	
				while (itr.hasNext()) {
					try {
						final ArHandler handler = itr.next();
						if (pm.activity < 0 || // send to all
						    handler.actId == pm.activity) {
	
							log("processMessageQueue MSG " + cmdStr(pm.id) + 
							    " to " + anyRemote.getScreenStr(pm.activity) + 
							    " SENT (attempt " + pm.attemptsToSend + ") to "+ 
							    anyRemote.getScreenStr(handler.actId));
													
							InfoMessage im = new InfoMessage();
							im.id    = pm.id;
							im.stage = pm.stage;
							
							Message msg = handler.hdl.obtainMessage(im.id, im);
							msg.sendToTarget();
	
							sent = true;
						}
					} catch (Exception e) {
						log("processMessageQueue exception " + e.getMessage());
					}
				}
			}

			if (sent) {
				// just drop it from queue
				synchronized (msgQueue) {
					msgItr.remove();
				}
				break;
				
			} else {
				
				log("processMessageQueue MSG " + cmdStr(pm.id) + " to " + anyRemote.getScreenStr(pm.activity) + " WAIT");
				pm.attemptsToSend++;
				
				if ((pm.id == CMD_CLOSE && (pm.activity == anyRemote.NO_FORM || pm.activity == -1)) ||
				    pm.attemptsToSend > MAX_ATTEMPTS) {
					// just drop it from queue
					log("processMessageQueue MSG " + cmdStr(pm.id) + " to " + anyRemote.getScreenStr(pm.activity) + " DROP");					
					synchronized (msgQueue) {
						msgItr.remove();
					}
					break;
				}
			}
		}
		
		if (msgQueue.size() > 0) {
			// Schedule new attempts to send
			
			log("processMessageQueue schedule next iteration (have " + msgQueue.size() + " events)");
			
			MainLoop.schedule(new TimerTask() {
                public void run() {
                     processMessageQueue();
                }
            });			
		}
	}

	private synchronized void scheduleKeepaliveTask() {
		MainLoop.schedule(
				new TimerTask() {
                    public void run() {
                	     keepaliveTask();
                   }
                }, 
                (long) (keepaliveTimeout * 3000));	// x2 is not enough sometimes		
	}
	
	private synchronized void keepaliveTask() {

		if (keepaliveTimeout > 0) {
			log("keepaliveTask test keepalive");
			if (keepaliveCounter == 0) {
				log("keepaliveTask seems connection is lost, do disconnect");
				disconnect(true);
			} else {
				keepaliveCounter = 0;
			    scheduleKeepaliveTask();
			}
		}
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
	    log("loadPrefs");
	    
		SharedPreferences preference = context.getPreferences(Context.MODE_PRIVATE);
		Vector<Address>  addresses = new Vector<Address>();

		try {
			Map<String, String> datam = (Map<String, String>) preference.getAll();    
			log("loadPrefs got #"+datam.size());
			
			Iterator<Map.Entry<String, String>> it = datam.entrySet().iterator();
			
			while (it.hasNext()) {
				Map.Entry<String, String> pairs = (Map.Entry<String, String>) it.next();
				
				String key         = (String) pairs.getKey();
				String cf_url_pass = (String)  pairs.getValue();
				
				
				//log("loadPrefs k/v >"+key+"< >"+cf_url_pass+"<");
				
				if (key.equals(M_SENSOR)) {  // special cases
				    
				    useGyroscope = (cf_url_pass.compareTo("g") == 0);
				        
				} else {
				
    				// format a|c+:+address+\n+pass
    				// old format is address+\n+pass
    				
    				boolean autoconnect = cf_url_pass.startsWith("a");
    				
    				String url_pass = (cf_url_pass.charAt(1) == ':' ?  
    						           cf_url_pass.substring(2) : 
    						           cf_url_pass);
    				
    				int p = url_pass.lastIndexOf('\n');
    				if (p > 0) {
    					Address a = new Address();
    
    					a.name = key;
    					a.URL  =  url_pass.substring(0, p).trim();
    					a.pass = url_pass.substring(p+1);
    					a.autoconnect = autoconnect;
    					
    					//log("loadPrefs >"+cf_url_pass.charAt(0)+"< >"+a.name+"< >"+a.URL+"< >"+a.pass+"<");
    					
    					addresses.add(a);
    				}
				}
			}		    
		} catch(Exception z) { }
		
		return addresses;
	}
	
	boolean sensorGyroscope() {
	    return useGyroscope;
	}
	
	public void setSensorType(boolean useG) {
	    
	    if (useGyroscope != useG) {
	        useGyroscope = useG;
	        
	        SharedPreferences preference = context.getPreferences(Context.MODE_PRIVATE);
	        SharedPreferences.Editor editor = preference.edit();
	        
	        editor.putString(M_SENSOR, (useG ? "g" : "a"));
	        editor.commit();
	    }
	}
	
	public void cleanAddress(String name) {
		log("cleanAddress "+name);

		SharedPreferences preference = context.getPreferences(Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = preference.edit();
		editor.remove(name);
		editor.commit();
	}

	// save new address in preferences
	public void addAddress(String name, String URL, String pass, boolean autoConnect) {		        
		//log("addAddress "+name+"/"+URL+"/"+pass);

		SharedPreferences preference = context.getPreferences(Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = preference.edit();
		
		if (autoConnect) {   // only one peer can have auto connect flag
			try {
				Map<String, String> datam = (Map<String, String>) preference.getAll();    

				Iterator<Map.Entry<String, String>> it = datam.entrySet().iterator();
				
				while (it.hasNext()) {
					Map.Entry<String, String> pairs = (Map.Entry<String, String>) it.next();

					// format a|c+:+address+\n+pass
					// old format is address+\n+pass
					String cf_url_pass = (String)  pairs.getValue();
					
					boolean autoconnect = cf_url_pass.startsWith("a");
					
                    if (autoconnect) {
                    	cf_url_pass = "c" + cf_url_pass.substring(1);
                    	editor.putString((String) pairs.getKey(), cf_url_pass);
					}
				}		    
			} catch(Exception z) { }
		}

		String UP = (autoConnect ? "a:" : "c:") + URL.trim() + "\n" + pass;
		log("addAddress commit "+name+"/"+UP);
		editor.putString(name, UP);
		editor.commit();
	}

	private void sendMessage(String command) {
		if (connection != null && !connection.isClosed()) {
			log("sendMessage " + command);
		    connection.send(command+";\r");
		}
	}

	public void queueCommand(int keycode, boolean pressed) {

		log("queueCommand "+keycode +" " + pressed);

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

	public void setFullscreen(String option) {

		if (option.startsWith("on")) {
			if (fullscreen) return;
			fullscreen = true;
		} else if (option.startsWith("off")) {
			if (!fullscreen) return;
			fullscreen = false;
		} else if (option.startsWith("toggle")) {
			fullscreen = !fullscreen;
		}
	}
	
	//public void setFullscreen(String option, arActivity act) {
	//	
	//	setFullscreen(option);
	//	setFullscreen(act);
	//}

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
	
	public synchronized void addMessageHandler(ArHandler h) {
		log("addMessageHandler");
		synchronized (actHandlers) {
			if (!actHandlers.contains(h)) {
			    log("addMessageHandler DONE");
			    actHandlers.add(h);
		    }
	    }
	}
	
  	public synchronized void removeMessageHandler(ArHandler h) {
  		synchronized (actHandlers) {
  			actHandlers.remove(h);
  		}
	}
  	
	//
	// Control Screen activity persistent data handling
	//
  	private void controlDataProcess(Vector vR) {
    	
    	//log("processData >"+vR+"<");
    	
    	int id = (Integer) vR.elementAt(0);
   
		switch (id) {
		
		    case CMD_LAYOUT:
		    	
		    	if (vR.size() < 2) {
		    		return;
		    	}
		    	controlSetSkin(vR); 
			    break;
				      
		    case CMD_STATUS:
			   
		    	if (vR.size() < 2) {
		    		return;
		    	}
			    cfStatus = (String) vR.elementAt(1);
				break;

		    case CMD_TITLE:
		    	
		    	if (vR.size() < 2) {
		    		return;
		    	}
		    	cfTitle = (String) vR.elementAt(1);
				break;

		    case CMD_CAPTION:
		    	
		    	if (vR.size() < 2) {
		    		return;
		    	}
		    	cfCaption = (String) vR.elementAt(1);
				break;
			
		    case CMD_ICONS:
		    	
		    	if (vR.size() < 2) {
		    		return;
		    	}
				controlSetIconLayout(vR);
				break; 
           
		    case CMD_HINTS:
                
                if (vR.size() < 2) {
                    return;
                }
                controlSetHints(vR);
                break; 
				
  		    case CMD_BG:
  		    	
  		    	cfBkgr = anyRemote.parseColor(vR,1);
  		    	break; 
  		
  		    case CMD_FG:

  		    	cfFrgr = anyRemote.parseColor(vR,1);
   		    	break;  
  		  
  		    case CMD_FONT:
  		    	
  		    	if (vR.size() < 2) {
  		    		return;
  		    	}
			    controlSetFontParams(vR);
			    break; 
			     
		    case CMD_FSCREEN:
		    	
		    	if (vR.size() < 2) {
		    		return;
		    	}
		    	setFullscreen((String) vR.elementAt(1));
			    break;   
			 				
		    case CMD_VOLUME:
		    	
		    	if (vR.size() < 2) {
		    		return;
		    	}
		    	cfVolume = (String) vR.elementAt(1);
				break;  

			case CMD_COVER:
				
		    	if (vR.size() < 3) {
	    		    if (vR.size() == 2 && !((String) vR.elementAt(1)).equals("clear")) {
	    		    	log("processData wrong Set(cover...) command");
	    		    } 
			        cfCover = null;
			        cfNamedCover = "";
		    	} else {
		    		if (((String) vR.elementAt(1)).equals("noname")) {
				        cfCover = (Bitmap) vR.elementAt(2);
				        cfNamedCover = "";
		    		} else {
		    			cfCover = null;
		    			cfNamedCover = (String) vR.elementAt(2);
		    		}
		    	}
				break;
		}
    }
    
    private void controlSetSkin(Vector vR) {
		
        String name   = (String) vR.elementAt(1);

        //useCover    = false;
        //cover       = null;
         
        //boolean newVolume = false;
    	//int     newSize   = icSize;
        
        cfUseJoystick = true;
        cfUpEvent   = "UP";
        cfDownEvent = "DOWN";
        cfInitFocus = 5;

        boolean oneMore = false;
        
        for (int i=2;i<vR.size();) {
        
	        String oneParam = (String) vR.elementAt(i);
                
    		if (oneMore) {
        		try {
        		    cfInitFocus = btn2int(oneParam);
                } catch (NumberFormatException e) {
                	cfInitFocus = -1;
 	            }
                	
                oneMore = false;
    		} else if (oneParam.equals("joystick_only")) {
        		cfUseJoystick = true;
		        //useKeypad   = false;
    		} else if (oneParam.equals("keypad_only")) {
        		cfUseJoystick = false;
		        //useKeypad   = true;
    		} else if (oneParam.equals("ticker")) {
		        //newTicker   = true;
    		} else if (oneParam.equals("noticker")) {
		        //newTicker   = false;
    		} else if (oneParam.equals("volume")) {
		        //newVolume   = true;
    		} else if (oneParam.equals("size16")) {
		        //newSize = 16;
    		} else if (oneParam.equals("size32")) {
	            //newSize = 32;
    		} else if (oneParam.equals("size48")) {
	            //newSize = 48;
    		} else if (oneParam.equals("size64")) {
	            //newSize = 64;
    		} else if (oneParam.equals("size128")) {
	            //newSize = 128;
    		} else if (oneParam.equals("choose")) {
		        oneMore = true;
    		} else if (oneParam.equals("up")) {
                i++;
                if (i<vR.size()) {
                    cfUpEvent = (String) vR.elementAt(i);
                }
    		} else if (oneParam.equals("down")) {
                i++;
                if (i<vR.size()) {
                	cfDownEvent = (String) vR.elementAt(i);
                }
    		} 
            i++;
        }
        
	    if (name.equals("default") ||
	        name.equals("3x4")) {
	    	cfSkin = ControlScreen.SK_DEFAULT;
        } else if (name.equals("7x1") ||
        		   name.equals("bottomline")) {
        	cfSkin = ControlScreen.SK_BOTTOMLINE;
        }
    }
 
	private void controlSetIconLayout(Vector data) {
     	
    	if (data.size() == 0) {
     	    return;
    	}
    	
    	// want to support new&old format for now:
    	// Set(icons,caption,1,icon,...)
       	// Set(icons,1,icon,...)
    	int start = 1;
    	if (btn2int((String) data.elementAt(start)) < 0) {
    		start++;
    	}
    	
		if (start == 2 && !((String) data.elementAt(1)).equals("SAME")) {
			cfCaption = (String) data.elementAt(1);
        }

		if (data.size() < start+1) {
			return;
		}
		int maxIcon = (cfSkin == ControlScreen.SK_BOTTOMLINE ?  ControlScreen.NUM_ICONS_BTM : ControlScreen.NUM_ICONS);
		
        for (int idx=start;idx<data.size()-1;idx+=2) {
         	try {
        		int i = btn2int((String) data.elementAt(idx));

        		if (i >= 0 || i < maxIcon) {    
        			cfIcons[i] = (String) data.elementAt(idx+1);
        			log("controlSetIconLayout "+i+" -> "+cfIcons[i]); 
       		    }  
	         } catch (Exception e) { }
        }
    }
	
	private void controlSetHints(Vector data) {
        
        if (data.size() < 2) {
            return;
        }
        
        for (int idx=1;idx<data.size()-1;idx+=2) {
            try {
                int i = btn2int((String) data.elementAt(idx));

                if (i >= 0 || i < ControlScreen.NUM_ICONS) {    
                    cfHints[i] = (String) data.elementAt(idx+1);
                    log("controlSetHints "+i+" -> "+cfHints[i]); 
                }  
             } catch (Exception e) { }
        }
    }
  
	private void controlSetFontParams(Vector defs) {
		
		boolean bold   = false;
		boolean italic = false;
		float   size   = SIZE_MEDIUM;
		boolean setSize = false;
		
		int start = 1;
       	while (start<defs.size()) {
            String spec = (String) defs.elementAt(start);
            if (spec.equals("plain")) {
            	//style = Font.STYLE_PLAIN;
            } else if (spec.equals("bold")) {
            	bold = true;
            } else if (spec.equals("italic")) {
            	italic = true;
            } else if (spec.equals("underlined")) {
            	//style = (style == Font.STYLE_PLAIN ? Font.STYLE_UNDERLINED : style|Font.STYLE_UNDERLINED);
            } else if (spec.equals("small")) {
            	size = Dispatcher.SIZE_SMALL;
            	setSize = true;
            } else if (spec.equals("medium")) {
            	size = Dispatcher.SIZE_MEDIUM;
               	setSize = true;
            } else if (spec.equals("large")) {
            	size = Dispatcher.SIZE_LARGE;
               	setSize = true;
            } else if (spec.equals("monospace")) {
            	//face  = Font.FACE_MONOSPACE;
            } else if (spec.equals("system")) {
            	//face  = Font.FACE_SYSTEM;
            } else if (spec.equals("proportional")) {
            	//face  = Font.FACE_PROPORTIONAL;
            //} else {
            //	controller.showAlert("Incorrect font "+spec);
            }
        	start++;
        }
       	
	    if (bold && italic) {
	    	cfTFace = Typeface.defaultFromStyle(Typeface.BOLD_ITALIC);
	    } else if (bold) {
	    	cfTFace = Typeface.defaultFromStyle(Typeface.BOLD);
	    } else if (italic) {
	    	cfTFace = Typeface.defaultFromStyle(Typeface.ITALIC);
	    } else {
	    	cfTFace = Typeface.defaultFromStyle(Typeface.NORMAL);
	    }
	    
	    if (setSize) {
	        cfFSize = size;
	    }
	}

	private int btn2int(String btn) {
       	int i = -1;
        
		if (btn.equals("*")) {
		    i=9;
        } else if (btn.equals("#")) {
        	i=11;
        } else {
        	try {
        		i = Integer.parseInt(btn) - 1;
        		if (i == -1) {	// 0 was parsed
        			i = 10;
        		}
            } catch (NumberFormatException e) { }
        }
        return i;
    }	
	
	//
	// List Screen activity persistent data handling
	//
  	
	//
	// list|iconlist, add|replace|!close!|clear|!show![,Title,item1,...itemN]
	// 
	// Set(list,close) does NOT processed here !
	//
	// returns 0 - do not update data source, do not switch to list screen
	//         1 - need update data source + switch to list screen
	//         2 - need update data, do not switch to list screen
	// 
	private int listDataProcess(int id, Vector vR, int stage) {	
		log("listDataProcess "+id+" "+vR); 

		if (stage == ProtocolMessage.INTERMED ||
	        stage == ProtocolMessage.LAST) {
			// get next portion of Set(list,add/replace ...)
			listAdd(id, vR, 0, false);
			return UPDATE_SWITCH;
		}
		
		String oper  = (String) vR.elementAt(1); 

		if (oper.equals("clear")) {

			listClean();
			return UPDATE_NOTSWITCH;

		} else if (oper.equals("close")) {
            
			// here processed only "clear" part of the command
			if (vR.size() > 2 && ((String) vR.elementAt(2)).equals("clear")) {
				listClean();
			}
			return NOTUPDATE_NOTSWITCH;
			
		} else if (oper.equals("fg")) {

			int color = anyRemote.parseColor(vR,2);
			listText = color;
			listCustomTextColor = true;
			return UPDATE_NOTSWITCH;
			
		} else if (oper.equals("bg")) {

			int color = anyRemote.parseColor(vR,2);
			listBkgr = color;
			listCustomBackColor = true;
			return UPDATE_NOTSWITCH;
	
		} else if (oper.equals("font")) {

			listSetFont(vR);
			return UPDATE_NOTSWITCH;
		
		} else if (oper.equals("caption")) {

			if (vR.size() > 2) {
				listTitle = (String) vR.elementAt(2);;
			}
			return UPDATE_SWITCH;

		} else if (oper.equals("select")) {

			try { 
				int i = Integer.parseInt((String) vR.elementAt(2))-1;
				if (i>0) {
					listSelectPos = i;
				}
			} catch(Exception z) { 
				listSelectPos = -1;	
			}
			return UPDATE_SWITCH;
			
		} else if (oper.equals("icon")) {

			try { 
				listIcon = (String) vR.elementAt(2);
			} catch(Exception z) { 
				listIcon = "";
			}
			return UPDATE_NOTSWITCH;

		} else if (oper.equals("add") || oper.equals("replace")) {

			String title = (String) vR.elementAt(2);

			if (oper.equals("replace")) {
				listClean();
			}
			if (!title.equals("SAME")) {
				listTitle = title;
			}
			listAdd(id, vR, 3, (stage == ProtocolMessage.FULL));
			return UPDATE_SWITCH;
						
		} else if (oper.equals("show")) {
			// nothing to do
			return NOTUPDATE_SWITCH;
		} else {
	    	log("processList: ERROR improper command >"+oper+"<");
		}
		return NOTUPDATE_NOTSWITCH;
	}

	private void listAdd(int id, Vector vR, int start, boolean fullCmd) {

		//log("addToList "+vR); 

		int end = vR.size();
		if (!fullCmd) {
			end -= 1;
		}

		for (int idx=start;idx<end;idx++) {

			String item = (String) vR.elementAt(idx);
			
			if (start == 0 && idx == 0) {
				item = listBufferedItem.toString() + item;
				listBufferedItem.delete(0, listBufferedItem.length());
			}
			
			String[] items = item.split("\n");
			for (int i=0;i<items.length;i++) {
				String subitem = items[i];
				if (!subitem.equals("") && ! (subitem.length() == 1 && subitem.charAt(0) == '\n')) {
					listAddWithIcon(id, subitem); 
				}
			}
		}

		if (!fullCmd) {
			listBufferedItem.append((String) vR.elementAt(end));
		}
	}

	private void listAddWithIcon(int id, String content) {

		ListItem item = new ListItem();

		int idx = (id == CMD_ICONLIST ? content.indexOf(":") : 0);
		if (idx > 0) {
			item.icon = content.substring(0,idx).trim();
			item.text = content.substring(idx+1).trim().replace('\r', ',');
		} else {
			item.icon = null;
			item.text = content;
		}
		synchronized (listContent) {
		    listContent.add(item);
		}
	}	

	private void listClean() {
		log("listClean");
		
		listSelectPos = -1;
		synchronized (listContent) {
		    listContent.clear();
		}
		listBufferedItem.delete(0, listBufferedItem.length());
	}
	
	private void listSetFont(Vector defs) {

		int start = 2;
		while(start<defs.size()) {
			String spec = (String) defs.elementAt(start);
			if (spec.equals("small")) {
				listFSize = Dispatcher.SIZE_SMALL;
			} else if (spec.equals("medium")) {
				listFSize = Dispatcher.SIZE_MEDIUM;
			} else if (spec.equals("large")) {
				listFSize = Dispatcher.SIZE_LARGE;
			} else {
				listFSize = -1;
			}
			start++;
		}
	}
	
	//
	// Text Screen activity persistent data handling
	//
	
	// Set(text,add,title,_text_)		3+text
	// Set(text,replace,title,_text_)	3+text
	// Set(text,fg|bg,r,g,b)		6
	// Set(text,font,small|medium|large)	3
	// Set(text,close[,clear])		2 or 3
	// Set(text,wrap,on|off)		3
	// Set(text,show)
	//
	// Set(list,close) does NOT processed here !
	//
	private boolean textDataProcess(Vector vR, int stage) {   // message = add|replace|show|clear,title,long_text
		//log("textDataProcess VSIZE "+vR.size() + " stage " + stage); 
		if (vR.size() == 0) {
			return false;	
		}
		
		if (stage == ProtocolMessage.INTERMED) {
			    
			    textContent.append((String) vR.elementAt(0));
			    return true;
			    
		} else if (stage == ProtocolMessage.LAST) {
			    if (vR.size() > 2) {
			        textContent.append((String) vR.elementAt(3));
			    }
				return true;
		}

		String oper = (String) vR.elementAt(1);

		if (oper.equals("clear")) {

			textContent.delete(0, textContent.length());
			return false;
			
		} else if (oper.equals("add") || 
                  oper.equals("replace")) {

			if (!((String) vR.elementAt(2)).equals("SAME")) {
				textTitle = (String) vR.elementAt(2);
			}

			if (oper.equals("replace")) {
				textContent.delete(0, textContent.length());
			}
			textContent.append((String) vR.elementAt(3));
			return true;
			
		} else if (oper.equals("fg")) {

			textFrgr = anyRemote.parseColor(vR,2);
			return false;
			
		} else if (oper.equals("bg")) {

			textBkgr = anyRemote.parseColor(vR,2);
			return false;

		} else if (oper.equals("font")) {

			textFontParams(vR);
			return false;

		} else if (oper.equals("caption")) {

			if (vR.size() > 2) {
				textTitle = (String) vR.elementAt(2);;
			}
			return true;

		//} else if (oper.equals("wrap")) {

			// not supported
			//return false;

		} else if (oper.equals("close")) {

			if (vR.size() > 2 && ((String) vR.elementAt(2)).equals("clear")) {
				textContent.delete(0, textContent.length());
			}

		} else if (!oper.equals("show")) {
			return false; // seems command improperly formed
		}
		return true;
	}	
	
	private void textFontParams(Vector defs) {

		boolean bold   = false;
		boolean italic = false;
		float   size   = Dispatcher.SIZE_MEDIUM; 

		int start = 2;
		while(start<defs.size()) {
			String spec = (String) defs.elementAt(start);
			if (spec.equals("plain")) {
				//style = Font.STYLE_PLAIN;
			} else if (spec.equals("bold")) {
				bold = true;
			} else if (spec.equals("italic")) {
				italic = true;
			} else if (spec.equals("underlined")) {
				//style = (style == Font.STYLE_PLAIN ? Font.STYLE_UNDERLINED : style|Font.STYLE_UNDERLINED);
			} else if (spec.equals("small")) {
				size = Dispatcher.SIZE_SMALL;
			} else if (spec.equals("medium")) {
				size = Dispatcher.SIZE_MEDIUM;
			} else if (spec.equals("large")) {
				size = Dispatcher.SIZE_LARGE;
			} else if (spec.equals("monospace")) {
				//face  = Font.FACE_MONOSPACE;
			} else if (spec.equals("system")) {
				//face  = Font.FACE_SYSTEM;
			} else if (spec.equals("proportional")) {
				//face  = Font.FACE_PROPORTIONAL;
				//} else {
				//	controller.showAlert("Incorrect font "+spec);
			}
			start++;
		}

		if (bold && italic) {
			textTFace = Typeface.defaultFromStyle(Typeface.BOLD_ITALIC);
		} else if (bold) {
			textTFace = Typeface.defaultFromStyle(Typeface.BOLD);
		} else if (italic) {
			textTFace = Typeface.defaultFromStyle(Typeface.ITALIC);
		} else {
			textTFace = Typeface.defaultFromStyle(Typeface.NORMAL);
		}
		textFSize = size;
	}
	
	//
	// Menu data handling
	//
	public void menuProcess(Vector vR, int screen) {
		anyRemote._log("Dispatcher", "menuProcess "+screen+" "+vR);
		
		String oper  = (String) vR.elementAt(1); 

		if (oper.equals("clear")) {

			menuClean(screen);    

		} else if (oper.equals("add") || oper.equals("replace")) {

			if (oper.equals("replace")) {
				menuClean(screen);
				menuAddDefault(screen); 
			}

			menuAdd(vR, screen);
		}
	}
	
	void menuClean(int screen) {
		
		switch(screen) {
			case anyRemote.CONTROL_FORM:
				cfMenu.clear();
				break;
			case anyRemote.TEXT_FORM:
			case anyRemote.LOG_FORM:
				textMenu.clear();
				break;
			case anyRemote.LIST_FORM:
				listMenu.clear();
				break;
			case anyRemote.WMAN_FORM:
				winMenu.clear();
				break;
		}
	}
	
	void menuAdd(Vector from, int screen) { 
		
		anyRemote._log("Dispatcher", "menuAdd "+screen+" "+from); 
		
		for (int idx=2;idx<from.size();idx++) {
			String item = (String) from.elementAt(idx);

			if (item.length() > 0) {
				switch(screen) {
					case anyRemote.CONTROL_FORM:
						//anyRemote._log("Dispatcher", "menuAdd cfMenu "+item);
						cfMenu.add(item);
						break;
					case anyRemote.TEXT_FORM:
						textMenu.add(item);
						break;
					case anyRemote.LIST_FORM:
						listMenu.add(item);
						break;
					case anyRemote.WMAN_FORM:
						winMenu.add(item);
						break;
				}
			}
		}
	}
	
	void menuReplaceDefault(int screen) {
		menuClean(screen);
		menuAddDefault(screen); 	
	}
	
	private void menuAddDefault(int screen) {   	
		switch(screen) {
			case anyRemote.CONTROL_FORM:
				cfMenu.add(context.getString(R.string.disconnect_item));
				cfMenu.add(context.getString(R.string.exit_item));
				cfMenu.add(context.getString(R.string.mouse_item));	
	            cfMenu.add(context.getString(R.string.keyboard_item)); 
                cfMenu.add(context.getString(R.string.log_item));   
				break;
			case anyRemote.TEXT_FORM:
				textMenu.add(context.getString(R.string.back_item));
				break;
			case anyRemote.LIST_FORM:
				listMenu.add(context.getString(R.string.back_item));
				break;
			case anyRemote.WMAN_FORM:
				winMenu.add(context.getString(R.string.back_item));
				break;
            /*case anyRemote.MOUSE_FORM:
                mouseMenu.add(context.getString(R.string.back_item));
                break;
            case anyRemote.KEYBOARD_FORM:
                keyboardMenu.add(context.getString(R.string.back_item));
                break;
            case anyRemote.WEB_FORM:
                webMenu.add(context.getString(R.string.disconnect_item));
                break;
			case anyRemote.LOG_FORM:
				textMenu.add(context.getString(R.string.clear_log_item));
				textMenu.add(context.getString(R.string.report_bug_item));
				textMenu.add(context.getString(R.string.back_item));
				break;*/
		}
	}
	
	Vector<String> getMenu() {
		
		int screen = anyRemote.getCurScreen();
		
		switch(screen) {
			case anyRemote.CONTROL_FORM:
				return cfMenu;
			case anyRemote.TEXT_FORM:
			//case anyRemote.LOG_FORM:
				return textMenu;
			case anyRemote.LIST_FORM:
				return listMenu;
			/*case anyRemote.MOUSE_FORM:
				return mouseMenu;
            case anyRemote.KEYBOARD_FORM:
                return keyboardMenu;
            case anyRemote.WEB_FORM:
                return webMenu;*/
            case anyRemote.WMAN_FORM:
                return winMenu;
		}
		return null;
	}
	
	public void autoUploadIcon(String name) {
		synchronized (autoUploadedI) {
			if (!autoUploadedI.contains(name)) {
				anyRemote._log("Dispatcher", "autoUploadIcon request for "+name);
				autoUploadedI.add(name);
		        	queueCommand("_GET_ICON_(128,"+ name +")");
			}
		}
	}

	public void autoUploadCover(String name) {
		synchronized (autoUploadedC) {
			if (!autoUploadedC.contains(name)) {
				anyRemote._log("Dispatcher", "autoUploadCover request for "+name);
				autoUploadedC.add(name);
				
				Display d = context.getWindowManager().getDefaultDisplay(); 
				
		        	queueCommand("_GET_COVER_("+(d.getWidth()*2)/3+","+ name +")");
			}
		}
	}  
	
	public void clearRequested() {
		synchronized (autoUploadedI) {
			autoUploadedI.clear();
		}
		synchronized (autoUploadedC) {
			autoUploadedC.clear();
		}
	}
	
	private void emptyDir(File dir) {
		anyRemote._log("Dispatcher", "cleanup directory "+dir.getAbsolutePath());
		if (dir.isDirectory()) {
			File[] files = dir.listFiles();
			for (int i=0;i<files.length;i++) {
				File one = files[i];
				//anyRemote._log("Dispatcher", "remove "+one.getAbsolutePath());
				one.delete();
			}
		} else {
			anyRemote._log("Dispatcher", "no directory "+dir.getAbsolutePath());
		}
	}
}
