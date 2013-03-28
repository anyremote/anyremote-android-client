//
// anyRemote java client
// a bluetooth remote for your PC.
//
// Copyright (C) 2006-2013 Mikhail Fedotov <anyremote@mail.ru>
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
 
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Vector;
import java.util.Timer;
import java.util.TimerTask;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;

public class ARProtocol {

    static final int SLEEP_TIME  = 100;    // 1/10 of second
    static final int BUFFER_SIZE = 512;    

    static final int CMD_NO         = 0;    // commmands
    static final int CMD_BG         = 1;
    static final int CMD_CAPTION    = 2;
    static final int CMD_EFIELD     = 3;
    static final int CMD_FG         = 4;
    static final int CMD_FMAN       = 5;
    static final int CMD_FONT       = 6;
    static final int CMD_FSCREEN    = 7;
    static final int CMD_ICONLIST   = 8;
    static final int CMD_ICONS      = 9;
    static final int CMD_LIST       = 10;
    static final int CMD_MENU       = 11;
    static final int CMD_PARAM      = 12;
    static final int CMD_REPAINT    = 13;
    static final int CMD_LAYOUT     = 14;
    static final int CMD_STATUS     = 15;
    static final int CMD_TEXT       = 16;
    static final int CMD_TITLE      = 17;
    static final int CMD_IMAGE      = 18;
    static final int CMD_VIBRATE    = 19;
    static final int CMD_VOLUME     = 20;
    static final int CMD_COVER      = 21;
    static final int CMD_POPUP      = 22;
    
    static final int CMD_GETSCRSIZE = 51;
    static final int CMD_GETPLTF    = 52;
    static final int CMD_GETIMG     = 53;
    static final int CMD_GETCVRSIZE = 54;
    static final int CMD_GETVER     = 55;
    static final int CMD_GETCURSOR  = 56;
    static final int CMD_GETPASS    = 57;
    static final int CMD_GETPING    = 58;
    static final int CMD_GETICSIZE  = 59;
    static final int CMD_GETICPAD   = 60;
    
    static final int CMD_CLOSECONN  = 101;
    //static final int CMD_EXIT     = 102;

    //static final int ENDS_AT_UKNWN  = 0;    // reading of a word was finished at: ");" "," " " or all was read
    static final int ENDS_AT_CEND   = 1;    
    //static final int ENDS_AT_BEND = 2;    
    static final int ENDS_AT_COMMA  = 3;    
    //static final int ENDS_AT_SPACE  = 4;    
    static final int ENDS_AT_NOMORE = 5;    

    static final int READ_NO       = 0;
    static final int READ_CMDID    = 1;
    //static final int READ_SCMDID = 2;
    static final int READ_PART     = 3;
    
    boolean     charMode;
    boolean     useUtf8;
    byte[]      bArray;
    int         curCmdId;
    int         readingEndsAt;
    int         readStage;
    int         wasRead;
    int         btoRead;
    Vector      cmdTokens;

    String           connectionURL;
    String           connectionNUP;
    StreamConnection connection;
    DataInputStream  iStream;
    DataOutputStream oStream;
    int              flushErrors;    // fix Motorola-KRZR-K1 issues
    Thread           readWriteThread;
    boolean          reconnect;
    Integer          runSignal;
    Vector           cmdQueue;
    Controller       controller;
    
    Timer            keepaliveTimer;
    int              keepaliveTimeout = -1;
    int              keepaliveCounter = 0;
    
    public ARProtocol(Controller ctl) {
                
        controller = ctl;
        runSignal  = new Integer(0);
        cmdQueue   = new Vector();
                
        Runnable runnable = new Runnable() {
            public void run() {
                ARProtocol.this.run();    
            }
        };
        
        charMode      = true;
        useUtf8     = true;
        bArray        = new byte[BUFFER_SIZE];
        //wasRead     = 0;
        //btoRead     = 0;
        readStage     = READ_NO;
                
        cmdTokens = new Vector();
        
        reconnect = true;
        readWriteThread = new Thread(runnable);
        readWriteThread.start();
    }
        
    private void run() {
        //int waitTime = 0;
                
        while (reconnect == true) {
            try {
                synchronized (runSignal) {
                    runSignal.wait();                    
                }
                if (reconnect == true) {
                    openConnection();
                }
                do {
                    doNextCommand();
                    receiveReplay();

                    synchronized (runSignal) {              // We wants to receive alarms from server!
                         runSignal.wait(SLEEP_TIME); 
                    }
                    
                } while (reconnect == true);

            } catch (IOException e) {
                //System.out.println  ("run() IOException");
                //controller.showAlertAsTitle("run() IOException "+e.getMessage());
                stopKeepaliveTimer();
                continue;            
            } catch (InterruptedException e) {
                //System.out.println  ("run() InterruptedException");
                //controller.showAlertAsTitle("run() InterruptedException");
                stopKeepaliveTimer();
                throw new RuntimeException("InterruptedException "+e.getMessage());    
            } catch (Exception e) {
                //System.out.println  ("run() Exception "+e.getClass().getName()+" "+e.getMessage());
                //controller.showAlertAsTitle("run() Exception "+e.getClass().getName());
            }
            stopKeepaliveTimer();
            closeConnection();
        }
    }
    
    private int cmdId(String header) {
        //System.out.println  ("cmdId "+header);
        //controller.showAlert("cmdId: "+header);
        
        if (header.equals("Set(bg")) {
            return CMD_BG;
        } else if (header.equals("Set(caption")) {
            return CMD_CAPTION;
        } else if (header.equals("Set(editfield")) {
            return CMD_EFIELD;
        } else if (header.equals("Set(fg")) {
            return CMD_FG;
        } else if (header.equals("Set(filemanager")) {
            return CMD_FMAN;
        } else if (header.equals("Set(font")) {
            return CMD_FONT;
        } else if (header.equals("Set(fullscreen")) {
            return CMD_FSCREEN;
        } else if (header.equals("Set(iconlist")) {
            return CMD_ICONLIST;
        } else if (header.equals("Set(icons")) {
            return CMD_ICONS;
        } else if (header.equals("Set(list")) {
            return CMD_LIST;
        } else if (header.equals("Set(menu")) {
            return CMD_MENU;
        } else if (header.equals("Set(parameter")) {
            return CMD_PARAM;
        } else if (header.equals("Set(repaint")) {
            return CMD_REPAINT;
        } else if (header.equals("Set(layout") ||
                   header.equals("Set(skin")) {        // obsoleted
            return CMD_LAYOUT;
        } else if (header.equals("Set(status")) {
            return CMD_STATUS;
        } else if (header.equals("Set(text")) {
            return CMD_TEXT;
        } else if (header.equals("Set(title")) {
            return CMD_TITLE;
        } else if (header.equals("Set(image")) {
            return CMD_IMAGE;
        } else if (header.equals("Set(cover")) {
            return CMD_COVER;
        } else if (header.equals("Set(vibrate")) {
            return CMD_VIBRATE;
        } else if (header.equals("Set(volume")) {
            return CMD_VOLUME;
        } else if (header.equals("Get(screen_size")) {
            return CMD_GETSCRSIZE;
        } else if (header.equals("Get(model")) {
            return CMD_GETPLTF;
        } else if (header.equals("Get(is_exists")) {
            return CMD_GETIMG;
        } else if (header.equals("Get(cover_size")) {
            return CMD_GETCVRSIZE;
        } else if (header.equals("Get(version")) {
            return CMD_GETVER;
        } else if (header.equals("Get(cursor")) {
            return CMD_GETCURSOR;
        } else if (header.equals("Get(ping")) {
            return CMD_GETPING;
        } else if (header.equals("Get(icon_size")) {
            return CMD_GETICSIZE;
        } else if (header.equals("Get(icon_padding")) {
            return CMD_GETICPAD;
        } else if (header.equals("Get(password")) {
            return CMD_GETPASS;
        } else if (header.equals("Set(disconnect")) {
            return CMD_CLOSECONN;
        } else if (header.equals("Set(popup")) {
            return CMD_POPUP;
        //} else if (header.equals("Set(exit")) {
        //    return CMD_EXIT;
        } 
        return CMD_NO;
    }
    
        public boolean streamedCmd(int id) {
            return (id == CMD_ICONLIST || 
                        id == CMD_LIST     || 
                        id == CMD_TEXT     || 
                        id == CMD_IMAGE    ||
                        id == CMD_COVER
                        );
       }
        
    private String bytes2String(int nBytes) {
        //System.out.println  ("bytes2String()");
        String word;
        if (nBytes <= 0) {
            return "";
        }
        
        try {
            word = new String(bArray,0, nBytes, "UTF-8");
        } catch (Exception e) { 
                // UnsupportedEncodingException");
            // It could gives java.lang.RuntimeException: IOException reading reader invalid byte 101001
            // for some national letters
            //System.out.println  ("bytes2String() Exception "+e.getClass().getName() + " " + e.getMessage());
            word = new String(bArray,0, nBytes);
        }
        
        return word;
    }
    
    //
    // get next token from input stream. Tokens are separated by "," and ");" 
    //
    public String getWord(boolean doNotSkip) throws IOException {

        //System.out.println  ("getWord() " + doNotSkip + " " +wasRead);
        boolean cBrace   = false;
        boolean semicol  = false;
        boolean comma    = false;
        
        // comma is allowed in text, status and title data
        boolean checkComma = ! (curCmdId == CMD_STATUS || curCmdId == CMD_TITLE || 
                                (curCmdId == CMD_TEXT && (cmdTokens.size() >=3 || readingEndsAt == ENDS_AT_NOMORE)));

        if (!doNotSkip) { 
            checkComma = false;
        } else if (curCmdId == CMD_TEXT && 
                   cmdTokens.size() >=2 && 
                   (((String) cmdTokens.elementAt(1)).equals("fg") || 
		    ((String) cmdTokens.elementAt(1)).equals("bg"))) {
            checkComma = true;
        }
	
        String aWord = "";
        
        // thanks to Nokia for bug in read()
        // we could not rely on available() so much
        
        if (btoRead <= 0) {
            btoRead = iStream.available();
            //controller.showAlert("AVAILABLE="+btoRead);
            //System.out.println  ("AVAILABLE="+btoRead);
        }
        //System.out.println  ("getWord() to read "+btoRead);
        while (btoRead > 0 && wasRead < BUFFER_SIZE) {
	
	    //System.out.println  ("getWord() WHILE");
            
	    int n = iStream.read(bArray,wasRead,1);
	    
	    //System.out.println  ("getWord() WAS READ "+n);
            if (n>0) {
                wasRead += n;
                btoRead -= n;
                // can crash if encoding is wrong
                //String s = new String(bArray,wasRead-1,1);
                //System.out.println("getWord() WAS READ " + " " +wasRead + " " +btoRead);
                //controller.showAlert("getWord() WAS READ "+s+" " +wasRead + " " +btoRead);
            }

            // read short command fully ot long command partially
            try {
                if (checkComma && bArray[wasRead-1] == ',') {
                    comma  = true;
                } 
                if (bArray[wasRead-1] == ')') {
                    cBrace  = true;
                    semicol = false;
                } else if (bArray[wasRead-1] == ';' && cBrace == true) {
                    semicol = true;
                } else {
                    cBrace  = false;
                }
            } catch (Exception e) { // ignore it
                //System.out.println  ("Exception at getWord() " + e.getClass().getName() + e.getMessage());
                //controller.showAlert("Exception at getWord() " + e.getClass().getName() + e.getMessage());
            }
        
            if (comma) {
                //System.out.println  ("getWord() COMMA");
                aWord = bytes2String(wasRead-1);
                readingEndsAt = ENDS_AT_COMMA;
                wasRead = 0;

                return aWord.trim();
            }
        
            if (cBrace && semicol) {
                //System.out.println  ("getWord() );");
                if (doNotSkip) {
                    aWord = bytes2String(wasRead-2);
                }
                readingEndsAt = ENDS_AT_CEND;
                wasRead = 0;
            
                return aWord.trim();
            }
                
            if (wasRead == BUFFER_SIZE) {
                //System.out.println  ("getWord() BUFFER_SIZE");
                readingEndsAt = ENDS_AT_NOMORE;
                
                int i = wasRead-1;    // Search a space backward
                         
                while (i>=0) { 
                    if (bArray[i] == ' ') {
                        break;
                    }
                    i--;
               }
                        
               if (i>0) {
                    i++;
                    if (i > wasRead-1) {
                        i = wasRead-1;
                    }
                    aWord = bytes2String(i);
                    	    
                    wasRead -= i;
                    try {
                        int k = 0;
                        while (k<wasRead) {
                            bArray[k] = bArray[k+i];
                            k++;
                        }
                    } catch (Exception e) {
                        //System.out.println  ("Exception at shift " + e.getClass().getName() + e.getMessage());
                        //controller.showAlert("Exception at shift " + e.getClass().getName() + e.getMessage());
                    }
                                
                    return aWord;    // no trim !!!
                } else {    // last chance - convert all buffer
                    aWord = bytes2String(BUFFER_SIZE);
                    wasRead = 0;
                    return aWord;
                }
            }
        }
        
        readingEndsAt = ENDS_AT_NOMORE;
        if (doNotSkip) {
            if (streamedCmd(curCmdId)) {
                aWord = bytes2String(wasRead);
                wasRead = 0;
                return aWord.trim();
            }
        } else {
            wasRead = 0;
        }

        return "";
    }
    
    private void receiveReplay() throws Exception {
        //System.out.println  ("receiveReplay() => "+ curCmdId+" "+readStage);
        
        while (true) {
            if (charMode) {
                //System.out.println  ("receiveReplay before getWord() ");
		
                String aWord = getWord(true); // a part of input stream separated by "," ");"

                //if (aWord.length() > 0) {
                //    controller.showAlert("getWord() " + aWord);
                //    System.out.println  ("receiveReplay->getWord() " + aWord);
                //}
                                        
                if (aWord.length() == 0 && readingEndsAt == ENDS_AT_NOMORE) {    
                    // this could happens if command was not readed fully
                    //System.out.println  ("receiveReplay(): getWord() => NO DATA");
                    //controller.showAlert("getWord() => NO DATA");
                    return;
                }                                

                if (readStage == READ_NO) {    // got header

                    //System.out.println  ("got header " + aWord);
                    //controller.showAlert("got header " + aWord);
                    
                    if (cmdTokens.size() > 0) {
                        //controller.showAlert("Header in incorrect order " + aWord);
                        cmdTokens.removeAllElements();
                    }
                                        
                    charMode = true;
                    int id = cmdId(aWord);
                    
                    if (id == CMD_NO) {
                        //controller.showAlert("Incorrect command " + aWord);
                        //System.out.println  ("Incorrect command " + aWord);
                                                
                        getWord(false);    // skip until  ");" or until end of available bytes
                         
                        readStage = READ_NO;
                        curCmdId  = CMD_NO;
                        continue;
                    }
                    
                    readStage = READ_CMDID;
                    
                    if (id == CMD_IMAGE || id == CMD_COVER) {
                        //System.out.println  ("GOT BINARY DATA");
                        charMode = false;
                        //btoRead  = 0; // in binary mode we will read full image
                    }
                    
                    curCmdId = id;
                    cmdTokens.addElement(new Integer(id));
                    
                } else {
                    //System.out.println  ("got the rest/next part");
                    cmdTokens.addElement(aWord);
                }
        
                if (readingEndsAt == ENDS_AT_CEND) {        // command was read fully
                                    
                    //System.out.println  ("FULL read");
                    int stage  = (readStage == READ_PART ? CanvasConsumer.LAST : CanvasConsumer.FULL);
                    
                    execCommand(cmdTokens,curCmdId,stage);
                    
                    readStage = READ_NO;
                    curCmdId  = CMD_NO;
                    
                } else if (readingEndsAt == ENDS_AT_NOMORE  ) {    // command was read partially
                
                   //System.out.println  ("PARTIAL read");
                   if (streamedCmd(curCmdId)) {
 
                        int stage  = (readStage == READ_CMDID ? CanvasConsumer.FIRST : CanvasConsumer.INTERMED);
                        execCommand(cmdTokens,curCmdId,stage);

                        readStage  = READ_PART;
                        
                    } // other commands will be read further on next cycle
                }
                
            } else {    // handle binary data here

                if (readStage == READ_NO) {    
                    // got header, this means we did not reset charMode flag, this is error
                    //controller.showAlert("Got header in binary mode ?");
                    //System.out.println  ("Got header in binary mode ?");
                    
                    charMode = true;
		    btoRead   = 0;
                    //continue;
                    
                } else if (readStage == READ_CMDID){
                    
                    //System.out.println  ("HANDLE binary command");
                    execCommand(cmdTokens,curCmdId,CanvasConsumer.FIRST);
                    
                    //System.out.println  ("End of binary mode");
                    
                    // all were done inside binary handler      
                   charMode  = true;
                   readStage = READ_NO;
                   curCmdId  = CMD_NO;
		   btoRead   = 0;
                   //continue;
                }
            }
        }
    }
            
    void openConnection() throws IOException {
        //System.out.println  ("openConnection "+connectionURL);
        if (connection != null) {
            closeConnection();
        }
        
        synchronized (runSignal) {
            runSignal.notifyAll();
        }    
        
        //if (controller.isBtOn()) {
            if (connectionURL != null) {
                try {
                    if (connectionURL.startsWith("comm:")) {    // COMM connection
                        connection = (StreamConnection)Connector.open(connectionURL + ";baudrate=9600");
                    } else {
                        connection = (StreamConnection)Connector.open(connectionURL);
                    }
                    iStream = connection.openDataInputStream();
                    oStream = connection.openDataOutputStream();

                } catch (Exception e) {
                    controller.showAlertAsTitle("openConnection Exception "+e.getClass().getName() + " " + e.getMessage());
                    throw new IOException("");
                } 
        
                controller.arStatusChanged(Controller.CONNECTED);
            } else {
                throw new IOException("");
            }
        //} else {
        //    controller.showAlertAsTitle("Bluetooth is off");
        //}
    }
    
    public void setConnectionURL(String name_url_pass) {
        //System.out.println  ("setConnectionURL "+name_url_pass);
        closeConnection();
        synchronized (runSignal) {
            runSignal.notifyAll();
        }
        
        int p1 = name_url_pass.indexOf('\n');        
        int p2 = name_url_pass.lastIndexOf('\n');    
        
        if (p1 == p2) {
            connectionURL = name_url_pass;
        } else {    
            connectionURL = name_url_pass.substring(p1+1,p2);
        }
        connectionNUP = name_url_pass;
    }
    
    public void closeConnection() {
        //System.out.println  ("closeConnection");
        try {
            if (iStream != null) iStream.close();
        } catch (Exception e) { } // Might be closed already, doesn't matter.
        
        try {
            if (oStream != null) oStream.close();
        } catch (Exception e) { } // Might be closed already, doesn't matter.
        
        try {
            if (connection != null) connection.close();
        } catch (IOException e) { } // Might be closed already, doesn't matter.

        connection = null;
        iStream = null;
        oStream = null;
        
        controller.arStatusChanged(Controller.DISCONNECTED);
    }
    
    public void doNextCommand() throws IOException {

        for (String cmd = getNextCommand(); cmd != null; cmd = getNextCommand()) {
 
            try {
                byte[] bts;
                 
                if (useUtf8) {
                    try {
                        bts = cmd.getBytes("UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        controller.showAlert("UnsupportedEncodingException");
                        bts = cmd.getBytes();
                    }
                } else {
                    bts = cmd.getBytes();
                }
                
                //System.out.println("Send Command "+cmd);

                oStream.write(bts, 0, bts.length);
                oStream.writeChar(';');
                oStream.writeChar('\r');
            
                if (flushErrors == 0 || (! controller.motoFixMenu)) {
                    try {    // Motorola KRZR K1 always throws this exception; and call to flush() takes a lo-o-ot of time 
                        oStream.flush();
			//System.out.println("Send Command flushed");
                    } catch (IOException e) { 
                        flushErrors++;    
                        controller.showAlert("oStream.flush: "+e.getMessage()+"->"+e.getClass().getName());
                    }
                }
                bts = null;

            } catch (IOException e) {
                //System.out.println("doNextCommand() IOException "+e.getClass().getName());
                //controller.showAlert("doNextCommand() IOException");
                //controller.showAlertAsTitle("DNC IOEx "+e.getMessage()+"->"+e.getClass().getName());
                
                throw new IOException("Exception on send 1: "+e.getMessage());
            } catch (Exception e) {
                //System.out.println("doNextCommand() Exception "+e.getClass().getName());
                //controller.showAlert("doNextCommand() Exception "+e.getClass().getName());
                //controller.showAlertAsTitle("DNC Ex "+e.getClass().getName());
                //throw new IOException("Exception on send 2: "+e.getMessage());
            } 
        }
    }
    
    private String getNextCommand() {
        synchronized (cmdQueue) {
            if (cmdQueue.isEmpty()) {
                return null;
            } else {
                String ret = (String) cmdQueue.elementAt(0);
                cmdQueue.removeElementAt(0);
                return ret;
            }
        }
    }
    
    public void destroy() {
        
        reconnect = false;        // this kill RW-thread
        synchronized (runSignal) {    // release the block to make thread run to the end
            runSignal.notifyAll();
        }
        try {
            readWriteThread.join();
        } catch (InterruptedException e) {
            //e.printStackTrace();
            //throw new RuntimeException("InterruptedException caught: "+e.getMessage());    
        }
    }

    public void queueCommand(int keycode, boolean pressed) {
        //System.out.println("queueCommand ("+keycode +" " + pressed + ") joystick="+ Canvas.FIRE+" "+Canvas.DOWN+" "+Canvas.UP+" "+Canvas.LEFT+" "+Canvas.RIGHT);
        //controller.showAlert("queueCommand "+keycode+" p/r="+pressed);
        
        String key = String.valueOf(keycode);
        
        if (keycode >= 0 && keycode < 10) {
            key = "K"+String.valueOf(keycode);
        } else {
        
            switch (keycode) {
            case Canvas.KEY_NUM1:  key = "1";break;
            case Canvas.KEY_NUM2:  key = "2";break;
            case Canvas.KEY_NUM3:  key = "3";break;
            case Canvas.KEY_NUM4:  key = "4";break;
            case Canvas.KEY_NUM5:  key = "5";break;
            case Canvas.KEY_NUM6:  key = "6";break;
            case Canvas.KEY_NUM7:  key = "7";break;
            case Canvas.KEY_NUM8:  key = "8";break;
            case Canvas.KEY_NUM9:  key = "9";break;
            case Canvas.KEY_STAR:  key = "*";break;
            case Canvas.KEY_NUM0:  key = "0";break;
            case Canvas.KEY_POUND: key = "#";break;
            default: 
                if (controller.cScreen.cf.isRealJoystick(keycode)) {
                    //controller.showAlert("isRealJoystick");
                    
                    try {
                        switch (controller.cScreen.getGameAction(keycode)) {
                            case Canvas.FIRE:      key = "FIRE"; break;
                            case Canvas.UP:        key = "UP";   break;
                            case Canvas.DOWN:      key = "DOWN"; break;
                            case Canvas.LEFT:      key = "LEFT"; break;
                            case Canvas.RIGHT:     key = "RIGHT";break;
                        }
                        break;
                    } catch (Exception e) {}
                }
            }
        }
        appendCommand("+CKEV: " + key + "," + (pressed ? "1" : "0"));
    }

    public void queueCommand(String message) {
         //System.out.println("queueCommand " + message);
         appendCommand("Msg:" + message);
    }

    void appendCommand(String cmd) {
        //System.out.println("appendCommand " + cmd);
        synchronized (cmdQueue) {
            cmdQueue.addElement(cmd);
        }
        synchronized (runSignal) {
            runSignal.notifyAll();
        }        
    }

    public Vector splitReplay(String Replay, int max, int startWith) {

        int chunks = (max > 0 ? max : 9999);  // 9999 - too big to be real
        int idx = 0;
        int i   = 0;
        
        Vector Out = new Vector();
        
        try {
            while (i<chunks) {
                int idx2 = Replay.indexOf(",",idx);
                                
                if (i >= startWith) {
                    Out.addElement(Replay.substring(idx,idx2).trim());
                }
                                
                idx = idx2+1;
                i++;
            }
        } catch (Exception e) {  // last element
            Out.addElement(Replay.substring(idx).trim());
        }
        
        return Out;
    }
        
    private void execCommand(Vector cmdTokens, int id, int stage) {
        if (cmdTokens.size() == 0) {
            return;
        }
        
        //System.out.println  ("execCommand " + id + " " + stage);
        //controller.showAlert("execCommand " + id + " " + stage);

        switch (id) {
        
            case CMD_CLOSECONN:
                closeConnection();
                break;
                
            /*case CMD_EXIT:
                controller.showAlert("controller.exit()");
                controller.exit();
                break;*/
                
            case CMD_BG:
                controller.cScreen.cf.setColor(CanvasScreen.BG, cmdTokens);
                break; 
            
            case CMD_CAPTION:
                controller.cScreen.cf.setCaption((String) cmdTokens.elementAt(1));
                break; 
                    
            case CMD_EFIELD:
                if (cmdTokens.size() > 3) {
                    controller.setupEField((String) cmdTokens.elementAt(1),(String) cmdTokens.elementAt(2),(String) cmdTokens.elementAt(3),false);
                                 controller.showScr(Controller.EDIT_FORM);
                }
                break; 
                   
            case CMD_FG:
                controller.cScreen.cf.setColor(CanvasScreen.FG, cmdTokens);
                break;  
                     
            case CMD_FMAN:
                controller.cScreen.setData(CanvasScreen.FILEMGR_SCREEN,cmdTokens,stage);
                break;  
                    
            case CMD_FONT:
                controller.cScreen.cf.setFontCF(cmdTokens);
                break; 
                     
            case CMD_FSCREEN:
                controller.cScreen.setFullScreen((String) cmdTokens.elementAt(1));
                break;   
                 
            case CMD_ICONS:
                controller.cScreen.cf.setIconLayout(cmdTokens,true);
                break; 
                     
            case CMD_ICONLIST:
            case CMD_LIST:
                controller.cScreen.setData(CanvasScreen.LIST_SCREEN,cmdTokens,stage);
                break;  
                     
            case CMD_MENU:
                controller.setMenu(cmdTokens);
                break; 
                      
            case CMD_PARAM:
                controller.setParam(cmdTokens);
                break;
                
            case CMD_REPAINT:
                controller.repaintCanvas();
                break;
                
            case CMD_LAYOUT:
                controller.cScreen.cf.setSkin(cmdTokens);
                break;
                      
            case CMD_STATUS:
                controller.cScreen.cf.setStatus((String) cmdTokens.elementAt(1));
                break;

            case CMD_TEXT:
                controller.cScreen.setData(CanvasScreen.TEXT_SCREEN,cmdTokens,stage);
                break;       

            case CMD_TITLE:
                controller.cScreen.cf.setInfo((String) cmdTokens.elementAt(1));
                break;
                
            case CMD_VIBRATE:
            
                int vi = 2;
                if (cmdTokens.size() > 1) {
                    try {
                         vi = (int) Integer.parseInt((String) cmdTokens.elementAt(1));
                    } catch (NumberFormatException e) {
                         //controller.showAlert("Incorrect data in Set(vibrate,...) command");
                    }
                }
                if (vi < 0) {
                    break; // skip 
                }
                if (vi > 300) {
                    vi = 300;
                }
                Display.getDisplay(controller).vibrate(100*vi);
                break;
                
            case CMD_VOLUME:
                controller.cScreen.cf.setVolume((String) cmdTokens.elementAt(1));
                break;  
                   
            case CMD_IMAGE:
                controller.cScreen.setData(CanvasScreen.WMAN_SCREEN,cmdTokens,stage);
                break;    

            case CMD_COVER:
                controller.cScreen.cf.setData(cmdTokens,stage);
                break; 
                   
            case CMD_POPUP:
                controller.cScreen.popup(cmdTokens);
                break;    
            
            case CMD_GETSCRSIZE:
                queueCommand("SizeX("+controller.cScreen.CW+",)");
                queueCommand("SizeY("+controller.cScreen.CH+",)");
                break;

            case CMD_GETCVRSIZE:
                queueCommand("CoverSize("+controller.cScreen.cf.getCoverSize()+",)");
                break;

            case CMD_GETICSIZE:
                queueCommand("IconSize("+controller.cScreen.cf.icSize+",)");
                break;
                
            case CMD_GETICPAD:
                queueCommand("IconPadding("+controller.cScreen.cf.split+",)");
                break;
            
            case CMD_GETVER:
                queueCommand("Version(,"+controller.getAppProperty("MIDlet-Version")+")");
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
                String connectionPass = "";
                //System.out.println("CMD_GETPASS " + connectionNUP);

                int li = connectionNUP.lastIndexOf('\n');
                if (li > 0 && li < connectionNUP.length() - 1) {
                    connectionPass = connectionNUP.substring(li+1);
                }
                if (connectionPass.equals("")) {
                    controller.getPass();
                } else {
                    queueCommand("_PASSWORD_(,"+connectionPass+")");
                }
                break;

            case CMD_GETCURSOR:
                if (controller.cScreen.wm != null) {
                    controller.cScreen.wm.sendCursorPos();
                }
                break;

            case CMD_GETPLTF:
                queueCommand("Model(,"+System.getProperty("microedition.platform")+")");
                break;

            case CMD_GETIMG:
                
                String item1 = (String) cmdTokens.elementAt(1);
        	String size = "";
        	String name = "";
		boolean isIcon = true;

        	if (item1.equals("icon")) {              // Get(is_exists,icon,size,name)
                    size = (String) cmdTokens.elementAt(2);
                    name = (String) cmdTokens.elementAt(3);
        	} else if (item1.equals("cover")) {        // Get(is_exists,cover,name)
                    name = (String) cmdTokens.elementAt(2);
		    size = size + controller.cScreen.cf.getCoverSize();
		    isIcon = false;
        	} else {                      // old syntax Get(is_exists,size,name)
                    size = item1;
                    name = (String) cmdTokens.elementAt(1);
        	}

        	boolean isExists = controller.rmsSearch(size, name,isIcon);
        	//System.out.println  ("IS EXISTS(): "+name+" >"+size+"< "+isExists);

        	String resp;
        	if (size.length() == 0) {
                    if (isExists) {
                	resp = "CoverExists(,"+name+")";
                    } else {
                	resp = "CoverNotExists(,"+name+")";
                    }
        	} else {
                    resp = size+","+name+")";
                    if (isExists) {
                	resp = "IconExists("+resp;
                    } else {
                	resp = "IconNotExists("+resp;
                    }
        	}
                queueCommand(resp);
                break;
                
            default:
                //System.out.println  ("execCommand(): Command or handler unknown");
                //controller.showAlert("execCommand(): Command or handler unknown");
        }
        //System.out.println  ("execCommand: Clean up tokens");
        cmdTokens.removeAllElements();
    }
    
    private void stopKeepaliveTimer() {
        if (keepaliveTimer != null) {
            keepaliveTimer.cancel();
            keepaliveTimer = null;
        }
    }
        
    private synchronized void scheduleKeepaliveTask() {
        
        TimerTask keepaliveCheck = new TimerTask() {
            public void run() { keepaliveTask(); }
        };
        
        keepaliveTimer = new Timer();
        keepaliveTimer.scheduleAtFixedRate(keepaliveCheck, 0, (long) (keepaliveTimeout * 3000)); // x2 is not enough sometimes        
    }
    
    private synchronized void keepaliveTask() {

        if (keepaliveTimeout > 0) {
            //System.out.println("keepaliveTask test keepalive");
            if (keepaliveCounter == 0) {
                //System.out.println("keepaliveTask seems connection is lost, do disconnect");
                stopKeepaliveTimer();
                closeConnection();
            } else {
                keepaliveCounter = 0;
            }
        } else {
            stopKeepaliveTimer();
        }
    }
}
