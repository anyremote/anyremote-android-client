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

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.GestureDetector.OnGestureListener;
import android.view.GestureDetector;
import android.view.Display;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.KeyEvent;
import anyremote.client.android.util.InfoMessage;
import anyremote.client.android.util.ProtocolMessage;

public class MouseScreen 
       extends arActivity 
       implements View.OnClickListener,
                  // View.OnTouchListener,
                  KeyEvent.Callback, OnGestureListener, SensorEventListener {

    // private static final int SWIPE_MAX_OFF_PATH = 250;

    boolean fullscreen = false;
    Dispatcher.ArHandler hdlLocalCopy;

    ImageButton[] buttons;
    LinearLayout[] buttonsLayout;
    private GestureDetector gestureScanner;
    
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;

    static final int NUM_BUTTONS = 3;
     
    static final int[] mBtns = {R.id.mouseButton1, R.id.mouseButton2, R.id.mouseButton3};
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefix = "MouseScreen"; // log stuff
        log("onCreate");

        gestureScanner = new GestureDetector(this);

        hdlLocalCopy = new Dispatcher.ArHandler(anyRemote.MOUSE_FORM, new Handler(this));
        anyRemote.protocol.addMessageHandler(hdlLocalCopy);
        
        privateMenu = anyRemote.MOUSE_FORM;
        
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER); 
    }

    /*
     * @Override protected void onStart() { log("onStart"); super.onStart(); }
     */

    @Override
    protected void onPause() {
        log("onPause");
        hidePopup();
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        log("onResume");

        super.onResume();
        
        try {
            mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        } catch (Exception e) {
            TextView tx = (TextView) findViewById(R.id.xval);
            if (tx != null) {
                tx.setText("Exception:"+e.getMessage());
            }
        }
        if (anyRemote.status == anyRemote.DISCONNECTED) {
            log("onResume no connection");
            doFinish("");
            return;
        }

        redraw();

        exiting = false;
    }

    @Override
    protected void onStop() {
        log("onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        log("onDestroy");
        anyRemote.protocol.removeMessageHandler(hdlLocalCopy);
        super.onDestroy();
    }

    @Override
    protected void onUserLeaveHint() {
        log("onUserLeaveHint");
        // no time to sending events
        // commandAction(anyRemote.protocol.context.getString(R.string.disconnect_item));
        if (!exiting && anyRemote.protocol.messageQueueSize() == 0) {
            log("onUserLeaveHint - make disconnect");
            anyRemote.protocol.disconnect(false);
        }
    }

    public void handleEvent(InfoMessage data) {

        log("handleEvent " + Dispatcher.cmdStr(data.id));

        if (data.stage != ProtocolMessage.FULL && // process only full commands
                data.stage == ProtocolMessage.FIRST) {
            return;
        }

        if (handleCommonCommand(data.id)) {
            return;
        }

        if (data.id == Dispatcher.CMD_VOLUME) {
            Toast.makeText(this, "Volume is " + anyRemote.protocol.cfVolume + "%", Toast.LENGTH_SHORT).show();
            return;
        }

        redraw();
    }
    
    private void redraw() {

        anyRemote.protocol.setFullscreen(this);

        synchronized (anyRemote.protocol.mouseMenu) {
            setContentView(R.layout.mouse_form_default);
        }
        
        Display display = getWindowManager().getDefaultDisplay();

        int h = display.getHeight();
        int w = display.getWidth();

        int iconSize = w / 3;
        
        log("redraw  icon size "+iconSize);

        ImageButton[] buttons;
        buttons = new ImageButton[NUM_BUTTONS];

        for (int i = 0; i < NUM_BUTTONS; i++) {
            buttons[i] = (ImageButton) findViewById(mBtns[i]);
            buttons[i].setOnClickListener(this);
            
            buttons[i].setMaxHeight(iconSize);
            buttons[i].setMaxWidth(iconSize);
            buttons[i].setMinimumHeight(iconSize);
            buttons[i].setMinimumWidth(iconSize);
        }
    }
     
    public void onClick(View v) {
        // public boolean onTouch (View v, MotionEvent e) {

        String key = "";
 
        switch (v.getId()) {

        case R.id.mouseButton1:
            key = "1";
            break;

        case R.id.mouseButton2:
            key = "2";
            break;

        case R.id.mouseButton3:
            key = "3";
            break;

        default:
            log("onClick: Unknown button");
            return;
            // return false;
        }

        clickOn(key);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        super.dispatchTouchEvent(ev);
        return gestureScanner.onTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent me) {
        return gestureScanner.onTouchEvent(me);
    }

    private String key2str(int keyCode) {

        log("key2str " + keyCode);

        switch (keyCode) {
        case KeyEvent.KEYCODE_1:
            return "1";
        case KeyEvent.KEYCODE_2:
            return "2";
        case KeyEvent.KEYCODE_3:
            return "3";
        case KeyEvent.KEYCODE_4:
            return "4";
        case KeyEvent.KEYCODE_5:
            return "5";
        case KeyEvent.KEYCODE_6:
            return "6";
        case KeyEvent.KEYCODE_7:
            return "7";
        case KeyEvent.KEYCODE_8:
            return "8";
        case KeyEvent.KEYCODE_9:
            return "9";
        case KeyEvent.KEYCODE_STAR:
            return "*";
        case KeyEvent.KEYCODE_0:
            return "0";
        case KeyEvent.KEYCODE_POUND:
            return "#";
        case KeyEvent.KEYCODE_MENU:
        case KeyEvent.KEYCODE_HOME:
        case KeyEvent.KEYCODE_BACK:
            return ""; // do not process them
        case KeyEvent.KEYCODE_VOLUME_UP:
            return "VOL+";
        case KeyEvent.KEYCODE_VOLUME_DOWN:
            return "VOL-";
        case KeyEvent.KEYCODE_DPAD_UP:
            return anyRemote.protocol.cfUpEvent; 
        case KeyEvent.KEYCODE_DPAD_DOWN:
            return anyRemote.protocol.cfDownEvent;
        case KeyEvent.KEYCODE_DPAD_LEFT:
            return (!anyRemote.protocol.cfUseJoystick ? "LEFT" : ""); // do not
                                                                      // process
                                                                      // them
                                                                      // if
                                                                      // joystick_only
                                                                      // param
                                                                      // was
                                                                      // set
        case KeyEvent.KEYCODE_DPAD_RIGHT:
            return (!anyRemote.protocol.cfUseJoystick ? "RIGHT" : "");
        case KeyEvent.KEYCODE_DPAD_CENTER:
            return (!anyRemote.protocol.cfUseJoystick ? "FIRE" : "");
        case KeyEvent.KEYCODE_SEARCH:
            return "SEARCH";
        default:
            if (keyCode >= 0 && keyCode < 10) {
                return "K" + String.valueOf(keyCode);
            }
            return String.valueOf(keyCode);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        log("onKeyUp " + keyCode);
        
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            log("onKeyUp KEYCODE_BACK");
            commandAction(anyRemote.protocol.context.getString(R.string.back_item));
            return true;
        }
        
        String key = key2str(keyCode);
        if (key.length() > 0) {
            log("onKeyUp MSG " + key);
            return true;
        }
        log("onKeyUp TRANSFER " + keyCode);
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        log("onKeyDown " + keyCode);
        return false;
     }

    public void clickOn(String key) {
        log("clickOn "+key);
        //anyRemote.protocol.queueCommand(key, true);
        //anyRemote.protocol.queueCommand(key, false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        commandAction(item.getTitle().toString());
        return true;
    }

    // @Override
    // public void onBackPressed() {
    // log("onBackPressed");
    // commandAction(anyRemote.protocol.context.getString(R.string.disconnect_item));
    // }
    
    public void commandAction(String command) {
        log("commandAction " + command);
        if (command.equals(anyRemote.protocol.context.getString(R.string.back_item))) {
            doFinish("");
        }
    }

    @Override
    protected void doFinish(String action) {

        log("doFinish " + action);
        // exiting = true;

        final Intent intent = new Intent();
        intent.putExtra(anyRemote.ACTION, action);
        setResult(RESULT_OK, intent);

        finish();
    }

    public boolean onDown(MotionEvent e) {
        // log("onDown");
        return true;
    }

    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        // log("onFling " + e1.getX() + " " + e1.getY() + " "
        // + e2.getX() + " " + e2.getY() + " "
        // + velocityX + " " + velocityY);
        try {
            // if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH) {
            // return false;
            // }

            // right to left swipe
            if (e1.getX() - e2.getX() > anyRemote.SWIPE_MIN_DISTANCE
                    && Math.abs(velocityX) > anyRemote.SWIPE_THRESHOLD_VELOCITY) {
                clickOn("SlideLeft");
            } else if (e2.getX() - e1.getX() > anyRemote.SWIPE_MIN_DISTANCE
                    && Math.abs(velocityX) > anyRemote.SWIPE_THRESHOLD_VELOCITY) {
                clickOn("SlideRight");
            } else if (e1.getY() - e2.getY() > anyRemote.SWIPE_MIN_DISTANCE
                    && Math.abs(velocityY) > anyRemote.SWIPE_THRESHOLD_VELOCITY) {
                clickOn("SlideUp");
            } else if (e2.getY() - e1.getY() > anyRemote.SWIPE_MIN_DISTANCE
                    && Math.abs(velocityY) > anyRemote.SWIPE_THRESHOLD_VELOCITY) {
                clickOn("SlideDown");
            }
        } catch (Exception e) {
            // nothing
        }

        return true;
    }

    public void onLongPress(MotionEvent e) {
        // log("onLongPress");
    }

    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        // log("onScroll");
        return true;
    }

    public void onShowPress(MotionEvent e) {
        // log("onShowPress");
    }

    public boolean onSingleTapUp(MotionEvent e) {
        // log("onSingleTapUp");
        return true;
    }
    
    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
       // Do something here if sensor accuracy changes.
    }
    
    @Override
    public final void onSensorChanged(SensorEvent event) {

         // Many sensors return 3 values, one for each axis.
         float x = event.values[0];
         float y = event.values[1];
         float z = event.values[2];
         
         TextView tx = (TextView) findViewById(R.id.xval);
         if (tx != null) {
             tx.setText("X axis" +"\t\t"+x);
         }
         TextView ty = (TextView) findViewById(R.id.yval);
         if (ty != null) {
             ty.setText("Y axis" + "\t\t" +y);
         }
         TextView tz = (TextView) findViewById(R.id.zval);
         if (tz != null) {
             tz.setText("Z axis" +"\t\t" +z);
         }
    }
}
