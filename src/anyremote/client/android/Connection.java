//
// anyRemote android client
// a bluetooth/wi-fi remote control for Linux.
//
// Copyright (C) 2011-2013 Mikhail Fedotov <anyremote@mail.ru>
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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Vector;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Message;
import anyremote.client.android.util.ISocket;
import anyremote.client.android.util.ProtocolMessage;
import anyremote.client.android.util.UserException;

/**
 * A connection sets up a connection to the server and handles receiving and
 * sending of messages from/to the server.
 */
public final class Connection implements Runnable {

	static final int BUFFER_SIZE = 4096;

	static final int ENDS_AT_CEND = 1;
	static final int ENDS_AT_COMMA = 3;
	static final int ENDS_AT_NOMORE = 5;

	static final int READ_NO = 0;
	static final int READ_CMDID = 1;
	static final int READ_PART = 3;
	
	static final int  QUEUE_CAPACITY = 2048;
	
	boolean charMode;
	byte[] bArray;
	int curCmdId;
	int readingEndsAt;
	int readStage;
	int wasRead;
	int btoRead;
	Vector cmdTokens;

	Integer runSignal;
	Vector cmdQueue;
	StringBuilder dataQueue;

	private boolean closed = false;

	private final Dispatcher connectionListener;

	private boolean connectionListenerNotifiedAboutError = false;

	private final DataInputStream dis;
	private final DataOutputStream dos;

	private final ISocket sock;

	/**
	 * Create a new connection.
	 * <p>
	 * A new thread for receiving data is created automatically. Once this
	 * thread has set up a connection to the server, it notifies the listener
	 * using {@link Dispatcher}. If setting up
	 * the connection fails the listener is notified using
	 * Handler.
	 * <p>
	 * If the connection has been set up received messages are passed to that {@link Dispatcher}.
	 * <p>
	 * 
	 * @param sock
	 *            socket providing streams for the connection
	 * @param listener
	 *            {@link Dispatcher} connection event listener
	 */
	public Connection(ISocket sock, Dispatcher listener) {

		this.sock = sock;
		this.connectionListener = listener;

		bArray = new byte[BUFFER_SIZE];
		cmdTokens = new Vector();
		//dataQueue = new StringBuilder(QUEUE_CAPACITY);
		
		dis = new DataInputStream(sock.getInputStream());
		dos = new DataOutputStream(sock.getOutputStream());

		new Thread(this, "Connection Thread").start();

	}

	/**
	 * Close the connection. If the connection is already closed, this method
	 * has no effect. There will be no connection events for a
	 * {@link Dispatcher} after a call to this method.
	 * 
	 */
	public void close() {
		anyRemote._log("Connection", "do close");
		connectionListenerNotifiedAboutError = true;
		downPrivate();
	}

	public boolean isClosed() {
		return closed;
	}

	public void run() {

		// delay startup a little bit to give UI a chance to update
		try {
			Thread.sleep(200);
		} catch (InterruptedException e) {
		}

		final Connection c = this;
		
	    anyRemote.sendGlobal(anyRemote.CONNECTED, c);

		while (!closed) { // loop receiving messages

			try {
				
				receiveReplay();
				Thread.sleep(100); // do not eat full CPU
	
			} catch (EOFException e) {
				anyRemote._log("Connection", "run -> EOFException");
				downPrivate();
				notifyDisconnected("Connection broken", "Connection closed by other side.", e);
				return;
			} catch (IOException e) {
				anyRemote._log("Connection", "run -> IOException");
				downPrivate();
				notifyDisconnected("Connection broken", "IO error while receiving data.", e);
				return;
			} catch (Exception e) {
				anyRemote._log("Connection", "run -> Exception " + e);
				downPrivate();
				notifyDisconnected("Connection broken", "Exception", e);
				return;
			}
		}
	}

	/**
	 * Sends a message.
	 * 
	 * @param m
	 *            The message to send. Content does not get changed.
	 */
	public void send(String m) {
		synchronized (dos) {
			if (closed)
				return;

			try {
				sendPrivate(m);
			} catch (IOException e) {
				downPrivate();
				notifyDisconnected("Connection broken", "IO Error while sending data", e);
			}
		}
	}

	private void downPrivate() {
		sock.close();
		closed = true;
		anyRemote._log("Connection", "socket closed");
	}

	/** See {@link #notifyDisconnected(UserException)}. */
	private void notifyDisconnected(String error, String details, Exception e) {
		notifyDisconnected(new UserException(error, details, e));
	}

	/**
	 * Notifies the connection listener that the connection is
	 * down/broken/disconnected (but only if the listener has not yet been
	 * notified).
	 * 
	 * @param ue
	 *            the user exception describing the disconnect reason
	 */
	private void notifyDisconnected(final UserException ue) {
		
		anyRemote._log("Connection", "notifyDisconnected " + ue.getDetails());
		
		if (!connectionListenerNotifiedAboutError) {

			connectionListenerNotifiedAboutError = true;
		    anyRemote.sendGlobal(anyRemote.DISCONNECTED, ue.getDetails());
		}
	}

	public boolean streamedCmd(int id) {
		return (id == Dispatcher.CMD_ICONLIST || 
				id == Dispatcher.CMD_LIST     || 
				id == Dispatcher.CMD_TEXT     || 
				id == Dispatcher.CMD_IMAGE    || 
				id == Dispatcher.CMD_COVER);
	}

	private String bytes2String(int nBytes) {
		String word;
		if (nBytes <= 0) {
			return "";
		}

		try {
			word = new String(bArray, 0, nBytes, "UTF-8");
		} catch (Exception e) {
			// UnsupportedEncodingException");
			// It could gives java.lang.RuntimeException: IOException reading
			// reader invalid byte 101001
			// for some national letters
			// System.out.println
			// ("bytes2String() Exception "+e.getClass().getName() + " " +
			// e.getMessage());
			word = new String(bArray, 0, nBytes);
		}
		return word;
	}

	//
	// get next token from input stream. Tokens are separated by "," and ");"
	//
	public String getWord(boolean doNotSkip) throws IOException {

		boolean cBrace = false;
		boolean semicol = false;
		boolean comma = false;

		// comma is allowed in text, status and title data
		boolean checkComma = !(curCmdId == Dispatcher.CMD_STATUS
				|| curCmdId == Dispatcher.CMD_TITLE || (curCmdId == Dispatcher.CMD_TEXT && (cmdTokens
				.size() >= 3 || readingEndsAt == ENDS_AT_NOMORE)));

		if (!doNotSkip) {
			checkComma = false;
		} else if (curCmdId == Dispatcher.CMD_TEXT
				&& cmdTokens.size() >= 2
				&& (((String) cmdTokens.elementAt(1)).equals("fg") || ((String) cmdTokens
						.elementAt(1)).equals("bg"))) {
			checkComma = true;
		}

		String aWord = "";

		if (btoRead <= 0) {
			btoRead = dis.available();
		}

		while (btoRead > 0 && wasRead < BUFFER_SIZE) {

			int n = dis.read(bArray, wasRead, 1);
			if (n > 0) {
				wasRead += n;
				btoRead -= n;
			}

			// read short command fully or long command partially
			try {
				if (checkComma && bArray[wasRead - 1] == ',') {
					comma = true;
				}
				if (bArray[wasRead - 1] == ')') {
					cBrace = true;
					semicol = false;
				} else if (bArray[wasRead - 1] == ';' && cBrace == true) {
					semicol = true;
				} else {
					cBrace = false;
				}
			} catch (Exception e) { // ignore it
				anyRemote._log("Connection", "Exception at getWord() "
						+ e.getClass().getName() + e.getMessage());
			}

			if (comma) {
				aWord = bytes2String(wasRead - 1);
				readingEndsAt = ENDS_AT_COMMA;
				wasRead = 0;

				return aWord.trim();
			}

			if (cBrace && semicol) {
				if (doNotSkip) {
					aWord = bytes2String(wasRead - 2);
				}
				readingEndsAt = ENDS_AT_CEND;
				wasRead = 0;

				return aWord.trim();
			}

			if (wasRead == BUFFER_SIZE) {
				readingEndsAt = ENDS_AT_NOMORE;

				int i = wasRead - 1; // Search a space backward

				while (i >= 0) {
					if (bArray[i] == ' ') {
						break;
					}
					i--;
				}

				if (i > 0) {
					i++;
					if (i > wasRead - 1) {
						i = wasRead - 1;
					}
					aWord = bytes2String(i);

					wasRead -= i;
					try {
						int k = 0;
						while (k < wasRead) {
							bArray[k] = bArray[k + i];
							k++;
						}
					} catch (Exception e) {
						anyRemote._log(
								"Connection",
								"getWord() Exception on shift "
										+ e.getClass().getName() + " "
										+ e.getMessage());
					}
					return aWord; // no trim !!!

				} else { // last chance - convert all buffer
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

		while (true) {
			if (charMode) {

				String aWord = getWord(true); // a part of input stream
												// separated by "," ");"

				if (aWord.length() == 0 && readingEndsAt == ENDS_AT_NOMORE) {
					// this could happens if command was not readed fully
					return;
				}

				//anyRemote._log("Connection", "NEXT WORD " + aWord);

				if (readStage == READ_NO) { // got header

					if (cmdTokens.size() > 0) {
						cmdTokens.removeAllElements();
					}
					charMode = true;
					int id = cmdId(aWord);

					if (id == Dispatcher.CMD_NO) {

						anyRemote._log("Connection", "Incorrect command "
								+ aWord);
						getWord(false); // skip until ");" or until end of
										// available bytes

						readStage = READ_NO;
						curCmdId = Dispatcher.CMD_NO;
						continue;
					}

					readStage = READ_CMDID;

					if (id == Dispatcher.CMD_IMAGE || id == Dispatcher.CMD_COVER) {
						charMode = false;
						// btoRead = 0; // in binary mode we will read full
						// image
					}

					curCmdId = id;
					cmdTokens.addElement(new Integer(id));

				} else {
					cmdTokens.addElement(aWord);
				}

				if (readingEndsAt == ENDS_AT_CEND) { // command was read fully

					int stage = (readStage == READ_PART ? ProtocolMessage.LAST
							: ProtocolMessage.FULL);

					execCommand(cmdTokens, curCmdId, stage);

					readStage = READ_NO;
					curCmdId = Dispatcher.CMD_NO;

				} else if (readingEndsAt == ENDS_AT_NOMORE) { // command was
																// read
																// partially

					if (streamedCmd(curCmdId)) {

						int stage = (readStage == READ_CMDID ? ProtocolMessage.FIRST
								: ProtocolMessage.INTERMED);
						execCommand(cmdTokens, curCmdId, stage);
						readStage = READ_PART;

					} // other commands will be read further on next cycle
				}

			} else { // handle binary data here

				if (readStage == READ_NO) {
					// got header, this means we did not reset charMode flag,
					// this is error
					charMode = true;
					continue;

				} else if (readStage == READ_CMDID) {

					if (curCmdId == Dispatcher.CMD_COVER) {
						//System.out.println ("CMD_COVER");
						boolean ok = getBinaryCover();  // modify cmdTokens
						if (ok) {
							execCommand(cmdTokens, Dispatcher.CMD_COVER, ProtocolMessage.FULL);
						}
					} else if (curCmdId == Dispatcher.CMD_IMAGE) {
						getBinaryImage();
					}
					// System.out.println ("End of binary mode ?");
					// all were done inside binary handler
					charMode = true;
					readStage = READ_NO;
					curCmdId = Dispatcher.CMD_NO;
				}
			}
		}
	}
/*
	public String getHeader(boolean doNotSkip) throws IOException {

		if (dataQueue.length() > 0) {
			// try to parse header from buffer
			int end = dataQueue.indexOf(",");
			if (end > 0) {    // Set(.../Get(...
				String aWord = dataQueue.substring(0,end).trim();
				dataQueue.delete(0, end+1);
				return aWord;
			}
		}
		
		if (dis.available() <= 0) {
			return "";
		}

		String aWord = "";
		int wasRead = 0;

		while (wasRead < BUFFER_SIZE) {

			int n = dis.read(bArray, wasRead, 1);
			if (n > 0) {
				wasRead++;

				try {
					if (bArray[wasRead - 1] == ',') {
						break;
					}
				} catch (Exception e) { // ignore it
					anyRemote._log("Connection", "Exception at getWord() "
							+ e.getClass().getName() + e.getMessage());
				}
			} else {
				break;
			}
		}
		aWord = dataQueue.toString() + bytes2String(wasRead - 1);
		dataQueue.delete(0,dataQueue.length());
		
		return aWord.trim();
	}

	private void receiveReplay3() throws Exception {
		
		//anyRemote._log("Connection", "receiveReplay");
		
		if (curCmdId == Dispatcher.CMD_NO) {

			//anyRemote._log("Connection", "receiveReplay waiting for header");
			
			String aWord = getHeader(true);
			//anyRemote._log("Connection", "Header of command >" + aWord + "<");
			if (aWord.length() == 0) {
				return;
			}

			int id = cmdId(aWord);

			if (id == Dispatcher.CMD_NO) {

				anyRemote._log("Connection", "Incorrect command " + aWord);
				//getWord(false); // skip until ");" or until end of available
				//				// bytes

				curCmdId = Dispatcher.CMD_NO;

				return;
			}
			curCmdId = id;
			anyRemote._log("Connection", "Command id "+id);
			
			if (cmdTokens.size() > 0) {
				cmdTokens.removeAllElements();
			}
			cmdTokens.addElement(new Integer(id));
		}

		if (curCmdId == Dispatcher.CMD_IMAGE || curCmdId == Dispatcher.CMD_COVER) {
			//anyRemote._log("Connection", "Binary command");
			
			// binary data - icons or images
			if (curCmdId == Dispatcher.CMD_COVER) {
				Bitmap cover = getBinaryCover();
				if (cover != null) {
					cmdTokens.addElement(cover);
					execCommand(cmdTokens, Dispatcher.CMD_COVER,
							ProtocolMessage.FULL);
				}
			} else if (curCmdId == Dispatcher.CMD_IMAGE) {
				getBinaryImage();
			}
			// System.out.println ("End of binary mode ?");
			// all were done inside binary handler
			curCmdId = Dispatcher.CMD_NO;

		} else {

			//anyRemote._log("Connection", "Character command");
			
			// text data

			if (dis.available() > 0) {
				
				int read = dis.read(bArray, 0, BUFFER_SIZE - 1); // This is blocking
				String strData = bytes2String(read);
				//anyRemote._log("Connection", "Got character data "+strData);

				dataQueue.append(strData);
			}
			int end = dataQueue.indexOf(");");
			
			if (end >= 0) {
				
				String cmd = dataQueue.substring(0, end);
				dataQueue.delete(0,end + 2);

				// parse it by ','
				int comma = cmd.indexOf(",");
				while (comma >= 0) {

					String token = cmd.substring(0, comma);
					
					cmdTokens.addElement(token);

					cmd = cmd.substring(comma + 1);
					comma = cmd.indexOf(",");
				}

				cmdTokens.addElement(cmd);
				
				execCommand(cmdTokens, curCmdId, ProtocolMessage.FULL);
				curCmdId = Dispatcher.CMD_NO;
				
				//anyRemote._log("Connection", "Set storage to "+dataQueue.toString());
			}
		}
	}
*/
	private boolean getBinaryCover() {
		
		Bitmap cover = null;
		try {
			int sz = dis.readInt();
			anyRemote._log("Connection", "getBinaryCover size="+sz);
			if (sz <= 0) {
				return false;
			}
			if (sz == 1652121454) {  // trick: Set(cover,by_name,<name>)
				anyRemote._log("Connection", "getBinaryCover by_name ");

				String dummy = getWord(true);
				cmdTokens.addElement("by_name");
				String name = getWord(true);
				cmdTokens.addElement(name);
				
				sz = 0;
			} else if (sz == 1852796513) {  // trick: Set(cover,noname,<image data>)
				anyRemote._log("Connection", "getBinaryCover noname ");

				String dummy = getWord(true);
				cmdTokens.addElement("noname");
				
				sz = dis.readInt();
				anyRemote._log("Connection", "getBinaryCover size="+sz);
			
			} else if (sz == 1668048225) {  // trick: Set(cover,clear)
				
				anyRemote._log("Connection", "getBinaryCover clear ");

				String dummy = getWord(true);
				cmdTokens.addElement("clear");
				
				sz = 0;
			} else {  // old syntax: Set(cover,,<image data>)
				cmdTokens.addElement("noname");
			}
			
			if (sz > 0) {
			    cover = receiveImage(sz);
			    cmdTokens.addElement(cover);
			} else {
				btoRead = 0;
			}
			
		} catch (Exception e1) {
			return false;
		} catch (Error me) {
			return false;
		}
		
		return true;
	}

	public void getBinaryImage() {
		anyRemote._log("Connection", "getBinaryImage");

		try {
			String action = getWord(true);

			if (action.equals("window") ||
			    action.equals("icon")   ||
			    action.equals("cover")) {

				boolean justStore = false;
				String iName = "";
				if (action.equals("icon") || action.equals("cover")) {
					justStore = true;
					iName = getWord(true);
				}
				anyRemote._log("Connection", "getBinaryImage " + action + " " + iName);
				Bitmap screen = receiveImage();

				// int imW = screen.getWidth();
				// int imH = screen.getHeight();
				// dX = (controller.cScreen.CW - imW)/2;
				// dY = (controller.cScreen.CH - imH)/2;

				if (justStore) { // Just store it, not show

					// if (imW == imH && (imW == 16 || imW == 32 || imW == 48 ||
					// imW == 64 || imW == 128)) {

					/*
					 * String name_sz = iName + String.valueOf(imW); //int found
					 * = controller.cScreen.iconNameCache.indexOf(name_sz); //if
					 * (found < 0) { //
					 * controller.cScreen.addToIconCache(name_sz,screen); //}
					 * 
					 * // Redraw if this image was queued for upload from
					 * ControlScreen
					 * controller.cScreen.cf.handleIfNeeded(iName,imW,screen);
					 * 
					 * int argb[] = new int[imW*imH];
					 * screen.getRGB(argb,0,imW,0,0,imW,imH);
					 * controller.rmsHandle(true,argb,imW,imH,iName); argb =
					 * null;
					 */
					// } else {
					// controller.showAlert("Icon does not fit ("+imW+","+imH+")");
					// }

					// store it in /sdcard/anyRemote/<name>.png
					synchronized (anyRemote.iconMap) {
						File dir = Environment.getExternalStorageDirectory();
						
						File arDir;
						String path;
						
						if (action.equals("icon")) {
						    path = "Android/data/anyremote.client.android/files/icons";
						} else { // cover
						    path = "Android/data/anyremote.client.android/files/covers";	
						}
						arDir = new File(dir, path);	
						
						if (!arDir.isDirectory()) {
							arDir.mkdirs();
						}
	
						File file = new File(arDir, iName + ".png");
	
						anyRemote._log("Connection", "getBinaryImage going to save it to " + path + "/" + iName + ".png");
						try {
	
							OutputStream outStream = new FileOutputStream(file);
							screen.compress(Bitmap.CompressFormat.PNG, 100, outStream);
							outStream.flush();
							outStream.close();
	
							anyRemote._log("Connection", "Saved " + path + "/" + iName + ".png");
	
						} catch (FileNotFoundException e) {
							anyRemote._log("Connection",
									"Can not save " + path + "/" + iName + ".png " + e.toString());
						} catch (IOException e) {
							anyRemote._log("Connection",
									"Can not save " + path + "/" + iName + ".png " + e.toString());
						}
					}
				} else {
					anyRemote.protocol.imScreen = screen;
				} 
				
				// Inform protocol about saved image
				Vector tokens = new Vector();
				tokens.add(Dispatcher.CMD_IMAGE);
				tokens.add(action);
				execCommand(tokens, Dispatcher.CMD_IMAGE, ProtocolMessage.FULL);
			
			} else if (action.equals("close")) {
					
				Vector tokens = new Vector();
				tokens.add(Dispatcher.CMD_IMAGE);
				tokens.add("close");
				
				execCommand(tokens, Dispatcher.CMD_IMAGE, ProtocolMessage.FULL);
				
			/*
			} else if (action.equals("set_cursor")) { 
			
			    int x =  Integer.parseInt(controller.protocol.getWord(true)); 
			    int y =  Integer.parseInt(controller.protocol.getWord(true)); 
			    curX = x+dX; 
			    curY = y+dY;
			
			}  else if (action.equals("cursor")) { // have to repaint (draw or hide cursor cross)
			
				useCursor = true; 
				useDynCursor = false; 
				if (controller.cScreen.scr != anyRemote.WMAN_FORM) { 
				    return; 
				} 
			} else if (action.equals("dynamic_cursor")) { 
			
			    useCursor = true;
				useDynCursor = true; 
				if (controller.cScreen.scr != anyRemote.WMAN_FORM) { 
				    return; 
				} 
				 
			} else if (action.equals("nocursor")) { 
			    
			    useCursor = false; 
			    useDynCursor = false; 
			    if (controller.cScreen.scr != anyRemote.WMAN_FORM) {
				    return; 
				} 
			*/
			} else if (action.equals("remove_all")) {
				
				Vector tokens = new Vector();
				tokens.add(Dispatcher.CMD_IMAGE);
				tokens.add("remove");
				tokens.add("all");
				execCommand(tokens, Dispatcher.CMD_IMAGE, ProtocolMessage.FULL);

			} else if (action.equals("remove")) {
				
				String what = getWord(true);
				
				Vector tokens = new Vector();
				tokens.add(Dispatcher.CMD_IMAGE);
				tokens.add("remove");
				tokens.add(what);
				execCommand(tokens, Dispatcher.CMD_IMAGE, ProtocolMessage.FULL);
			
		    } else if (action.equals("clear_cache")) {
				
				Vector tokens = new Vector();
				tokens.add(Dispatcher.CMD_IMAGE);
				tokens.add("clear_cache");
				execCommand(tokens, Dispatcher.CMD_IMAGE, ProtocolMessage.FULL);
			
			/*} else if (!action.equals("show")) {
				 //System.out.println("WinManager NOT SHOW ???");
				 controller.showAlert("WM:unknown cmd"); return;
		    */
			}

		} catch (Exception e1) {

		}
	}
	
	private Bitmap receiveImage() throws IOException {

		int sz = dis.readInt();
		anyRemote._log("Connection", "receiveImage size="+sz);
		if (sz <= 0) {
			return null;
		}
		if (sz > 10000000) {
			anyRemote._log("Connection", "receiveImage image too big, skip it");
			return null;
		}
		return receiveImage(sz);
	}
	
	private Bitmap receiveImage(int sz) throws IOException {
		
		btoRead -= 4;

		byte[] rgbArray = new byte[sz];
		int haveRead = 0;
		
		while (haveRead < sz) {

			int av = dis.available();
			
			if (haveRead + av > sz) {
				av = sz - haveRead;
			}
			
			try {
			    dis.readFully(rgbArray,haveRead,av);
			    haveRead += av;
			    btoRead  -= av; 
			} catch (EOFException e) {
				anyRemote._log("Connection", "EOFException");
				btoRead = 0;
				return null;
			}
		}

		// get trailing ");"
		byte[] bufArray = new byte[1];
		dis.read(bufArray, 0, 1);
		dis.read(bufArray, 0, 1);
		btoRead -= 2;

		Bitmap im = BitmapFactory.decodeByteArray(rgbArray, 0, sz);

		rgbArray = null;
		return im;
	}

	/** Send a message without exception handling. */
	private void sendPrivate(String m) throws IOException {

		anyRemote._log("Connection", "send " + m);
		byte[] bts;
		try {
			bts = m.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			bts = m.getBytes();
		}
		dos.write(bts);
		dos.flush();
	}

/*	private void skip(int num) throws IOException {

		long rest = num;
		while (rest > 0) {

			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
			}

			final long avail = dis.available();
			if (avail == 0) {
				break;
			}
			rest -= dis.skip(Math.min(avail, 10240));
		}
	}*/

	private int cmdId(String header) {
		if (header.equals("Set(bg")) {
			return Dispatcher.CMD_BG;
		} else if (header.equals("Set(caption")) {
			return Dispatcher.CMD_CAPTION;
		} else if (header.equals("Set(editfield")) {
			return Dispatcher.CMD_EFIELD;
		} else if (header.equals("Set(fg")) {
			return Dispatcher.CMD_FG;
		} else if (header.equals("Set(filemanager")) {
			return Dispatcher.CMD_FMAN;
		} else if (header.equals("Set(font")) {
			return Dispatcher.CMD_FONT;
		} else if (header.equals("Set(fullscreen")) {
			return Dispatcher.CMD_FSCREEN;
		} else if (header.equals("Set(iconlist")) {
			return Dispatcher.CMD_ICONLIST;
		} else if (header.equals("Set(icons")) {
			return Dispatcher.CMD_ICONS;
		} else if (header.equals("Set(list")) {
			return Dispatcher.CMD_LIST;
		} else if (header.equals("Set(menu")) {
			return Dispatcher.CMD_MENU;
		} else if (header.equals("Set(parameter")) {
			return Dispatcher.CMD_PARAM;
		} else if (header.equals("Set(repaint")) {
			return Dispatcher.CMD_REPAINT;
		} else if (header.equals("Set(skin") ||
		           header.equals("Set(layout")) {
			return Dispatcher.CMD_LAYOUT;
		} else if (header.equals("Set(status")) {
			return Dispatcher.CMD_STATUS;
		} else if (header.equals("Set(text")) {
			return Dispatcher.CMD_TEXT;
		} else if (header.equals("Set(title")) {
			return Dispatcher.CMD_TITLE;
		} else if (header.equals("Set(image")) {
			return Dispatcher.CMD_IMAGE;
		} else if (header.equals("Set(cover")) {
			return Dispatcher.CMD_COVER;
		} else if (header.equals("Set(vibrate")) {
			return Dispatcher.CMD_VIBRATE;
		} else if (header.equals("Set(volume")) {
			return Dispatcher.CMD_VOLUME;
		} else if (header.equals("Set(popup")) {
			return Dispatcher.CMD_POPUP;
		} else if (header.equals("Get(screen_size")) {
			return Dispatcher.CMD_GETSCRSIZE;
		} else if (header.equals("Get(model")) {
			return Dispatcher.CMD_GETPLTF;
		} else if (header.equals("Get(is_exists")) {
			return Dispatcher.CMD_GETIMG;
		} else if (header.equals("Get(cover_size")) {
			return Dispatcher.CMD_GETCVRSIZE;
		} else if (header.equals("Get(version")) {
			return Dispatcher.CMD_GETVER;
		} else if (header.equals("Get(cursor")) {
			return Dispatcher.CMD_GETCURSOR;
		} else if (header.equals("Get(ping")) {
			return Dispatcher.CMD_GETPING;
		} else if (header.equals("Get(password")) {
			return Dispatcher.CMD_GETPASS;
		} else if (header.equals("Get(icon_size")) {
			return Dispatcher.CMD_GETICSIZE;
		} else if (header.equals("Get(icon_padding")) {
			return Dispatcher.CMD_GETPADDING;
		} else if (header.equals("Set(disconnect")) {
			return Dispatcher.CMD_CLOSECONN;
			// } else if (header.equals("Set(exit")) {
			// return CMD_EXIT;
		}
		return Dispatcher.CMD_NO;
	}

	private void execCommand(Vector cmdTokens, final int id, final int stage) {

		if (cmdTokens.size() <= 0) {
			return;
		}
		anyRemote._log("Connection", "execCommand " + Dispatcher.cmdStr(id) + " " + cmdTokens);
	
		final ProtocolMessage pm = new ProtocolMessage();
		pm.id     = id;
		pm.stage  = stage;
		pm.tokens = new Vector(cmdTokens);
        
		try {
			
		    anyRemote.sendGlobal(anyRemote.COMMAND, pm);

		} catch (Exception e) {
			
			anyRemote._log("Connection", "execCommand Exception");
			downPrivate();
			notifyDisconnected("Connection Error", "Received malformed data.", e);
			
		} catch (OutOfMemoryError e) {
			
			anyRemote ._log("Connection", "execCommand OutOfMemoryError");
			downPrivate();
			notifyDisconnected("Memory Error", "Received data too big.", null);
		}

		cmdTokens.removeAllElements();
		//anyRemote._log("Connection", "Active count " + anyRemote.runningCount);
	}	
}
