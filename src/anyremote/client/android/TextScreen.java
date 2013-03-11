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


import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
//import android.text.method.ScrollingMovementMethod;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.GestureDetector.OnGestureListener;
import android.view.GestureDetector;
import android.widget.TextView;
import anyremote.client.android.util.InfoMessage;
import anyremote.client.android.util.ProtocolMessage;
import anyremote.client.android.R;

public class TextScreen extends arActivity implements OnGestureListener {

	TextView  text;
	Dispatcher.ArHandler hdlLocalCopy;
	boolean isLog = false;
	private GestureDetector gestureScanner;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);  

		setContentView(R.layout.text_form);
	
		Intent  intent = getIntent();
		String subid   = intent.getStringExtra("SUBID");

		text = (TextView) findViewById(R.id.text_form);			

		if (subid.equals("__LOG__")) {	// Special case for log view	

			prefix = "Log"; // log stuff
			isLog = true;
			log("onCreate");
			
		} else {
			
			gestureScanner = new GestureDetector(this);
			
			hdlLocalCopy = new Dispatcher.ArHandler(anyRemote.TEXT_FORM, new Handler(this));
			anyRemote.protocol.addMessageHandler(hdlLocalCopy);

			prefix = "TextScreen"; // log stuff
			log("onCreate");
		}		
		
		registerForContextMenu(text);
		
		//text.setMovementMethod(new ScrollingMovementMethod());
	}
	
	// update all visuals
	void redraw()  {
		log("redraw");
		
		if (isLog) {
			text.setText(anyRemote.logData); 
			setTitle(anyRemote.protocol.context.getString(R.string.log_item));
		} else {
			
			anyRemote.protocol.setFullscreen(this);

			text.setText(anyRemote.protocol.textContent);
			setFont();
			setTextColor();
			setBackground();
			setTitle(anyRemote.protocol.textTitle);
		}
	}
	
	@Override
	protected void onPause() {
		log("onPause");
		hidePopup();
	    super.onPause();	
	}
	
	@Override
	protected void onResume() {
		log("onResume");

		super.onResume();
		
		exiting = false;
		
        if (anyRemote.status == anyRemote.DISCONNECTED && 
        	!isLog) {
        	
        	log("onResume no connection");
        	doFinish("");
        }

        redraw();
		
		exiting = false;
	}
	
	@Override
	protected void onDestroy() {	
		log("onDestroy");
		if (!isLog) {
	   	    anyRemote.protocol.removeMessageHandler(hdlLocalCopy);
		}
	   	isLog = false;
	   	super.onDestroy();
	}

	// Handle long-click
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		if (!isLog && v.getId() == R.id.text_form) {
			menu.setHeaderTitle(anyRemote.protocol.textTitle);
			addContextMenu(menu);
		}
	}

	@Override 
    public boolean dispatchTouchEvent(MotionEvent ev) { 
		//log("dispatchTouchEvent");
        super.dispatchTouchEvent(ev); 
        return (isLog ? false : gestureScanner.onTouchEvent(ev)); 
	}

    @Override
    public boolean onTouchEvent(MotionEvent me) {
        return (isLog ? false : gestureScanner.onTouchEvent(me));
    }

	// Handle context menu, opened by long-click
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		commandAction(item.getTitle().toString());  	
		return super.onContextItemSelected(item);
	}	
	
	@Override
	protected void onUserLeaveHint() {
		log("onUserLeaveHint - make disconnect");
		if (!exiting && anyRemote.protocol.messageQueueSize() == 0) {
		    anyRemote.protocol.disconnect(false);
		}
	}
	
	@Override
	protected void doFinish(String action) {
		
		log("doFinish");
		
		if (isLog) {
		    final Intent intent = new Intent();  
		    intent.putExtra(anyRemote.ACTION, action);	    
		    setResult(RESULT_OK, intent);
		}
		
		if (!isLog) {
			log("doFinish finish");
		}
		exiting = true;
		finish();  	
	}

	@Override
	public void onBackPressed() { 
		commandAction(anyRemote.protocol.context.getString(R.string.back_item));
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		commandAction(item.getTitle().toString());
		return true;
	}

	public void commandAction(String command) {
		if (isLog) {
			if (command.equals(anyRemote.protocol.context.getString(R.string.back_item))) {
				doFinish("log");  // just close Log form
			} else if (command.equals(anyRemote.protocol.context.getString(R.string.clear_log_item))) {
				anyRemote.logData.delete(0,anyRemote.logData.length());
				text.setText("");
			} else if (command.equals(anyRemote.protocol.context.getString(R.string.report_bug_item))) {

				Intent mailIntent = new Intent(Intent.ACTION_SEND);
				mailIntent.setType("text/plain");
				mailIntent.putExtra(Intent.EXTRA_EMAIL, new String[] { "anyremote@mail.ru" });
				mailIntent.putExtra(Intent.EXTRA_SUBJECT, "anyRemote android client bugreport");
				mailIntent.putExtra(Intent.EXTRA_TEXT, anyRemote.logData.toString());

				startActivity(Intent.createChooser(mailIntent, "Bug report"));
			}
		} else {
			if (command.equals(anyRemote.protocol.context.getString(R.string.back_item))) {
				command = "Back";  // avoid national alphabets
			}
			anyRemote.protocol.queueCommand(command);
		}
	}

	// Set(text,add,title,_text_)		3+text
	// Set(text,replace,title,_text_)	3+text
	// Set(text,fg|bg,r,g,b)		6
	// Set(text,font,small|medium|large)	3
	// Set(text,close[,clear])		2 or 3
	// Set(text,wrap,on|off)		3
	// Set(text,show)			2
	public void handleEvent(InfoMessage data) {
		
		log("handleEvent " + Dispatcher.cmdStr(data.id) + " " + data.stage);
		
		if (isLog) return; 

    	checkPopup();

    	if (data.stage == ProtocolMessage.FULL || data.stage == ProtocolMessage.FIRST) {

			if (handleCommonCommand(data.id)) {
				return;
			}
			
			if (data.id == Dispatcher.CMD_TEXT) {
				
				// update all visuals
				redraw();
			}
		} else  if (data.stage == ProtocolMessage.INTERMED || data.stage == ProtocolMessage.LAST) {
			redraw();
		}
	}		

	private void setTextColor() {
		text.setTextColor(anyRemote.protocol.textFrgr);
	}

	private void setBackground() {
		text.setBackgroundColor(anyRemote.protocol.textBkgr);
	}

	private void setFont() {

		TextView ttx  = (TextView) findViewById(R.id.text_form);
		ttx.setTypeface (anyRemote.protocol.textTFace);
		ttx.setTextSize (anyRemote.protocol.textFSize);
	}
	
    public boolean onDown(MotionEvent e) {
        //log("onDown");
        return true;
    }
   
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
    	if (isLog) {
    		return false;
    	}
    		
    	try {
             // right to left swipe
            if (e1.getX() - e2.getX() > anyRemote.SWIPE_MIN_DISTANCE && 
            		   Math.abs(velocityX) > anyRemote.SWIPE_THRESHOLD_VELOCITY) {
                commandAction("TextSlideLeft");
            } else if (e2.getX() - e1.getX() > anyRemote.SWIPE_MIN_DISTANCE && 
            		   Math.abs(velocityX) > anyRemote.SWIPE_THRESHOLD_VELOCITY) {
            	commandAction("TextSlideRight");
            } else {
             	return false;
            }
        } catch (Exception e) {
            // nothing
        }
        return true;
    }
   
    public void onLongPress(MotionEvent e) {
        //log("onLongPress");
    }
   
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        //log("onScroll");
        return true;
    }
   
    public void onShowPress(MotionEvent e) {
        //log("onShowPress");
    }    
   
    public boolean onSingleTapUp(MotionEvent e) {
        //log("onSingleTapUp");
        return true;
    }
 }
