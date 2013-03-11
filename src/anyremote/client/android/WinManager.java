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

import android.os.Bundle;
import android.os.Handler;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Display;
import android.view.MenuItem;
import android.view.View;
import android.view.MotionEvent;
import android.widget.ImageButton;
import android.view.GestureDetector.OnGestureListener;
import android.view.GestureDetector;
import anyremote.client.android.util.InfoMessage;
import anyremote.client.android.util.ProtocolMessage;
import anyremote.client.android.R;

public class WinManager extends arActivity 
                        implements OnGestureListener {
	
    ImageButton image;
 	Dispatcher.ArHandler hdlLocalCopy;
	private GestureDetector gestureScanner;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);  

		setContentView(R.layout.win_manager);
		
		image = (ImageButton) findViewById(R.id.window);
		
		gestureScanner = new GestureDetector(this,this);	
				
		hdlLocalCopy = new Dispatcher.ArHandler(anyRemote.WMAN_FORM, new Handler(this));
		anyRemote.protocol.addMessageHandler(hdlLocalCopy);

		prefix = "WinManager"; // log stuff
		log("onCreate");

		registerForContextMenu(image);
	}
	
	// update all visuals
	void redraw()  {
		log("redraw");
		anyRemote.protocol.setFullscreen(this);
		image.setImageBitmap(anyRemote.protocol.imScreen);		
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
		
        if (anyRemote.status == anyRemote.DISCONNECTED) {
          	log("onResume no connection");
        	doFinish("");
        }

        redraw();

		exiting = false;
	}
	
	@Override
	protected void onDestroy() {	
		log("onDestroy");	
	   	anyRemote.protocol.removeMessageHandler(hdlLocalCopy);		
	   	super.onDestroy();
	}
	
	@Override 
    public boolean dispatchTouchEvent(MotionEvent ev) { 
		log("dispatchTouchEvent");
        super.dispatchTouchEvent(ev); 
        return gestureScanner.onTouchEvent(ev); 
	}
	
    @Override
    public boolean onTouchEvent(MotionEvent me) {
    	//log("onTouchEvent");
        return gestureScanner.onTouchEvent(me);
    }

	// Handle long-click
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		if (v.getId() == R.id.window) {
			addContextMenu(menu);
		}
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
		if (command.equals(anyRemote.protocol.context.getString(R.string.back_item))) {
			command = "Back";  // avoid national alphabets
		}
		anyRemote.protocol.queueCommand(command);
	}

	public void handleEvent(InfoMessage data) {
		
		log("handleEvent " + Dispatcher.cmdStr(data.id) + " " + data.stage);
		
		checkPopup();

		if (data.stage == ProtocolMessage.FULL || data.stage == ProtocolMessage.FIRST) {
			
			if (handleCommonCommand(data.id)) {
				return;
			}
			
			if (data.id == Dispatcher.CMD_IMAGE) {
				
				// update all visuals
				redraw();
			}
		} else  if (data.stage == ProtocolMessage.INTERMED || data.stage == ProtocolMessage.LAST) {
			// should not come here
			redraw();
		}
	}	
	
    public boolean onDown(MotionEvent e) {
        log("onDown");
        return true;
    }
   
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        //log("onFling " + e1.getX() + " " + e1.getY() + " " 
        //		       + e2.getX() + " " + e2.getY() + " " 
        //		       + velocityX + " " + velocityY);
        try {
             // right to left swipe
            if (e1.getX() - e2.getX() > anyRemote.SWIPE_MIN_DISTANCE && 
            		   Math.abs(velocityX) > anyRemote.SWIPE_THRESHOLD_VELOCITY) {
                commandAction("ImageSlideLeft");
            } else if (e2.getX() - e1.getX() > anyRemote.SWIPE_MIN_DISTANCE && 
            		   Math.abs(velocityX) > anyRemote.SWIPE_THRESHOLD_VELOCITY) {
            	commandAction("ImageSlideRight");
            } else if (e1.getY() - e2.getY() > anyRemote.SWIPE_MIN_DISTANCE && 
            		   Math.abs(velocityY) > anyRemote.SWIPE_THRESHOLD_VELOCITY) {
            	commandAction("ImageSlideUp");
            } else if (e2.getY() - e1.getY() > anyRemote.SWIPE_MIN_DISTANCE && 
            		   Math.abs(velocityY) > anyRemote.SWIPE_THRESHOLD_VELOCITY) {
            	commandAction("ImageSlideDown");
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
    	//final int pointerCount = e.getPointerCount();
        //log("onSingleTapUp "+e.getX(0) + " " + e.getY(0));
         
		Display display = this.getWindowManager().getDefaultDisplay(); 
        
		int sw = display.getWidth();
		int sh = display.getHeight();
	    
		int dx = (sw - anyRemote.protocol.imScreen.getWidth())/2;
		int dy = (sh - anyRemote.protocol.imScreen.getHeight())/2;
		
		int px = (int) e.getX(0);
		int py = (int) e.getY(0);
		
		if (py < dy) {
			py = dy;
		} else if (py > sh - dy) {
			py = sh - dy;
		}
		
		if (px < dx) {
			px = dx;
		} else if (px > sw - dx) {
			px = sw - dx;
		}
     
        commandAction("PressedX("+(px - dx)+",)");
        commandAction("PressedY("+(py - dy)+",)");
        
        return true;
    }
}
