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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.TimerTask;
import java.util.Vector;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import anyremote.client.android.MainLoop;
import anyremote.client.android.util.ISocket;
import anyremote.client.android.util.ProtocolMessage;
import anyremote.client.android.util.UserException;

/**
 * A connection sets up a connection to the server and handles receiving and
 * sending of messages from/to the server.
 */
public final class Connection implements Runnable {

	static final int SLEEP_TIME = 100; // 1/10 of second
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

	/**
	 * Interface for classes interested in the state of a {@link Connection}.
	 */
	public interface IConnectionListener {

		/**
		 * Notifies a successful connection.
		 * 
		 * @param player
		 *            the connected player
		 */
		public void notifyConnected(Connection conn);

		/**
		 * Notifies a disconnection.
		 * 
		 * @param sock
		 *            the socket used by the broken connection - if it is worth
		 *            trying to reconnect, otherwise <code>null</code>
		 * @param reason
		 *            the user exception describing the reason for disconnecting
		 */
		public void notifyDisconnected(ISocket sock, UserException reason);

		public void notifyMessage(int commandId, Vector commandTokens, int stage);

	}

	private boolean closed = false;

	private final IConnectionListener connectionListener;

	private boolean connectionListenerNotifiedAboutError = false;

	private final DataInputStream dis;
	private final DataOutputStream dos;

	/** Flag indicating if a reconnect has chance to succeed. */
	private boolean reconnect = true;

	private final ISocket sock;

	/**
	 * Create a new connection.
	 * <p>
	 * A new thread for receiving data is created automatically. Once this
	 * thread has set up a connection to the server, it notifies the listener
	 * using {@link IConnectionListener#notifyConnected(Player)}. If setting up
	 * the connection fails the listener is notified using
	 * {@link IConnectionListener#notifyDisconnected(ISocket, UserException)}.
	 * <p>
	 * If the connection has been set up, a {@link Player} object is created and
	 * received messages are passed to that player.
	 * <p>
	 * Notifications and messages are passed via the {@link MainLoop} thread to
	 * decouple their handling from the receiver thread used by this connection.
	 * 
	 * @param sock
	 *            socket providing streams for the connection
	 * @param listener
	 *            connection event listener
	 * @param ci
	 *            initial client info to send to the server (client info updates
	 *            can be sent using {@link #send(ClientInfo)})
	 */
	public Connection(ISocket sock, IConnectionListener listener,
			String ci) {

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
	 * {@link IConnectionListener} after a call to this method.
	 * 
	 */
	public void close() {
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

		/*
		 * try { Looper.prepare(); } catch (RuntimeException e) {}
		 */

		final Connection c = this;

		MainLoop.schedule(new TimerTask() {
			public void run() {
				connectionListener.notifyConnected(c);
			}
		});

		while (!closed) { // loop receiving messages

			final String m;

			try {
				
				receiveReplay();
				Thread.sleep(100); // do not eat full CPU
	
			} catch (EOFException e) {
				anyRemote._log("Connection", "run -> EOFException");
				downPrivate();
				notifyDisconnected(new UserException("Connection broken",
						"Connection closed by other side.", e));
				return;
			} catch (IOException e) {
				anyRemote._log("Connection", "run -> IOException");
				downPrivate();
				notifyDisconnected(new UserException("Connection broken",
						"IO error while receiving data.", e));
				return;
			} catch (Exception e) {
				anyRemote._log("Connection", "run -> Exception " + e);
				notifyDisconnected(new UserException("Connection broken",
						"Exception", e));
				return;
			}
		}
	}

	/**
	 * Sends a message.
	 * <p>
	 * If sending fails, the listener set in
	 * {@link #Connection(ISocket, IConnectionListener, int)} is notified using
	 * {@link IConnectionListener#notifyDisconnected(ISocket, UserException)}.
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
				notifyDisconnected("Connection broken",
						"IO Error while sending data.", e);
			}
		}
	}

	private void downPrivate() {
		closed = true;
		sock.close();
	}

	/** See {@link #notifyDisconnected(UserException)}. */
	private void notifyDisconnected(String error, String details, Exception e) {
		anyRemote._log("Connection", "notifyDisconnected/3 " + error + " : "
				+ details);
		notifyDisconnected(new UserException(error, details, e));
	}

	/**
	 * Notifies the connection listener that the connection is
	 * down/broken/disconnected (but only if the listener has not yet been
	 * notified).
	 * <p>
	 * Notification is done via the global timer thread.
	 * 
	 * @param ue
	 *            the user exception describing the disconnect reason
	 */
	private void notifyDisconnected(final UserException ue) {
		anyRemote._log("Connection", "notifyDisconnected/1 " + ue);
		if (!connectionListenerNotifiedAboutError) {

			connectionListenerNotifiedAboutError = true;

			MainLoop.schedule(new TimerTask() {
				public void run() {
					connectionListener.notifyDisconnected(reconnect ? sock
							: null, ue);
				}
			});
		}
	}

	private void notifyMessage(final int id, final Vector tokens, final int stage) {
		
		anyRemote._log("Connection", "notifyMessage " + tokens);
		MainLoop.schedule(new TimerTask() {
			public void run() {
				connectionListener.notifyMessage(id, tokens, stage);
			}
		});
	}

	public boolean streamedCmd(int id) {
		return (id == Dispatcher.CMD_ICONLIST || id == Dispatcher.CMD_LIST
				|| id == Dispatcher.CMD_TEXT || id == Dispatcher.CMD_IMAGE || id == Dispatcher.CMD_COVER);
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
				
				//anyRemote._log("Connection", "NEXT WORD "
				//		+ aWord);

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
	public Bitmap getBinaryCover() {
		try {
			Bitmap cover = receiveImage();
			return cover;
		} catch (Exception e1) {

		} catch (Error me) {

		}
		return null;
	}

	public void getBinaryImage() {
		anyRemote._log("Connection", "getBinaryImage");

		try {
			String action = getWord(true);

			if (action.equals("window") ||
			    action.equals("icon")) {

				boolean isIcon = false;
				String iName = "";
				if (action.equals("icon")) {
					isIcon = true;
					iName = getWord(true);
				}
				anyRemote._log("Connection", "getBinaryImage icon " + iName);
				Bitmap screen = receiveImage();

				// int imW = screen.getWidth();
				// int imH = screen.getHeight();
				// dX = (controller.cScreen.CW - imW)/2;
				// dY = (controller.cScreen.CH - imH)/2;

				if (isIcon) { // Just store it, not show

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
					OutputStream outStream = null;

					File dir = Environment.getExternalStorageDirectory();

					File arDir = new File(dir,
							"Android/data/anyremote.client.android/files");

					if (!arDir.isDirectory()) {
						arDir.mkdirs();
					}

					File file = new File(arDir, iName + ".png");

					anyRemote
							._log("Connection",
									"getBinaryImage going to save it to /sdcard/Android/data/anyremote.client.android/files/"
											+ iName + ".png");

					try {

						outStream = new FileOutputStream(file);
						screen.compress(Bitmap.CompressFormat.PNG, 100,
								outStream);
						outStream.flush();
						outStream.close();

						anyRemote._log("Connection",
								"Saved /sdcard/Android/data/anyremote.client.android/files/"
										+ iName + ".png");

					} catch (FileNotFoundException e) {
						anyRemote._log("Connection",
								"Can not save /sdcard/Android/data/anyremote.client.android/files/"
										+ iName + ".png " + e.toString());
					} catch (IOException e) {
						anyRemote._log("Connection",
								"Can not save /sdcard/Android/data/anyremote.client.android/files//"
										+ iName + ".png " + e.toString());
					}
					return;
				} else {
					anyRemote.protocol.imScreen = screen;
					
					Vector tokens = new Vector();
					tokens.add(Dispatcher.CMD_IMAGE);
					
					execCommand(tokens, Dispatcher.CMD_IMAGE, ProtocolMessage.FULL);
					
				} 
				
			} else if (action.equals("close")) {
					
				Vector tokens = new Vector();
				tokens.add(Dispatcher.CMD_IMAGE);
				tokens.add("close");
				
				execCommand(tokens, Dispatcher.CMD_IMAGE, ProtocolMessage.FULL);
				
				/*
				 * } else if (action.equals("set_cursor")) { int x =
				 * Integer.parseInt(controller.protocol.getWord(true)); int y =
				 * Integer.parseInt(controller.protocol.getWord(true)); curX =
				 * x+dX; curY = y+dY; }  else if
				 * (action.equals("cursor")) { // have to repaint (draw or hide
				 * cursor cross) useCursor = true; useDynCursor = false; if
				 * (controller.cScreen.scr != anyRemote.WMAN_FORM) { return; } }
				 * else if (action.equals("dynamic_cursor")) { useCursor = true;
				 * useDynCursor = true; if (controller.cScreen.scr !=
				 * anyRemote.WMAN_FORM) { return; } } else if
				 * (action.equals("nocursor")) { useCursor = false; useDynCursor
				 * = false; if (controller.cScreen.scr != anyRemote.WMAN_FORM) {
				 * return; } } else if (action.equals("remove_all")) {
				 * controller.rmsClean(); return; } else if
				 * (action.equals("clear_cache")) {
				 * controller.cScreen.iconNameCache.removeAllElements();
				 * controller.cScreen.iconCache.removeAllElements(); return; }
				 * else if (!action.equals("show")) {
				 * //System.out.println("WinManager NOT SHOW ???");
				 * controller.showAlert("WM:unknown cmd"); return;
				 */
			}

		} catch (Exception e1) {

		}
	}

	public Bitmap receiveImage() throws IOException {

		int sz = dis.readInt();
		anyRemote._log("Connection", "receiveImage size="+sz);
		if (sz <= 0) {
			return null;
		}
		
		btoRead -= 4;

		byte[] rgbArray = new byte[sz];
		byte[] bufArray = new byte[1];

		dis.readFully(rgbArray);
		btoRead -= sz;

		// get trailing ");"
		dis.read(bufArray, 0, 1);
		dis.read(bufArray, 0, 1);
		btoRead -= 2;

		Bitmap im = im = BitmapFactory.decodeByteArray(rgbArray, 0, sz);

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

	private void skip(int num) throws IOException {

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
	}

	private int cmdId(String header) {
		if (header.equals("Set(bg")) {
			return Dispatcher.CMD_BG;
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
		} else if (header.equals("Set(skin")) {
			return Dispatcher.CMD_SKIN;
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
			return Dispatcher.CMD_GETICON;
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
		anyRemote._log("Connection", "execCommand " + id + " " + cmdTokens);

		final Vector tokens = new Vector(cmdTokens);

		MainLoop.schedule(new TimerTask() {
			public void run() {
				try {
					notifyMessage(id, tokens, stage);
				} catch (Exception e) {
					anyRemote._log("Connection", "execCommand Exception");
					notifyDisconnected("Connection Error",
							"Received malformed data.", e);
				} catch (OutOfMemoryError e) {
					anyRemote
							._log("Connection", "execCommand OutOfMemoryError");
					notifyDisconnected("Memory Error",
							"Received data too big.", null);
				}
			}
		});
		cmdTokens.removeAllElements();
	}
}
