//
// anyRemote android client
// a bluetooth/wi-fi remote control for Linux.
//
// Copyright (C) 2011-2015 Mikhail Fedotov <anyremote@mail.ru>
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

import java.lang.Math;
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
    private Sensor mAccelerometer = null;
    private Sensor mGyroscope     = null;
   
    float mLastX, mLastY, mLastZ;
    boolean mInitialized;

    static final int NUM_BUTTONS = 5;
 
    static final float NOISE   = 0.16f;
    static final float NOISE_GYROSCOPE = 0.04f;
    static final float G_VALUE = 9.81f;
    
    static final float NS2S = 1.0f / 1000000000.0f;
    static final float EPSILON = 16f;
    private final float[] deltaRotationVector = new float[4];
    private float[] rotationCurrent = new float[3];
    private float timestamp;
     
    static final int[] mBtns = {R.id.mouseButton1, R.id.mouseButton2, R.id.mouseButton3, R.id.mouseButton4, R.id.mouseButton5};
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefix = "MouseScreen"; // log stuff
        log("onCreate");

        gestureScanner = new GestureDetector(anyRemote.protocol.context, this);

        hdlLocalCopy = new Dispatcher.ArHandler(anyRemote.MOUSE_FORM, new Handler(this));
        anyRemote.protocol.addMessageHandler(hdlLocalCopy);
        
        privateMenu = anyRemote.MOUSE_FORM;
        
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE); 
        if (mGyroscope == null) {
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        mInitialized = false;
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
            if (mGyroscope != null) { 
                mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_GAME);
            } else if (mAccelerometer != null) {
                mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
            }
        } catch (Exception e) {
            //TextView tx = (TextView) findViewById(R.id.xval);
            //if (tx != null) {
            //    tx.setText("Exception:"+e.getMessage());
            //}
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
        mSensorManager.unregisterListener(this);
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

        //synchronized (anyRemote.protocol.mouseMenu) {
            setContentView(R.layout.mouse_form_default);
        //}
        
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
       
        case R.id.mouseButton4:
            key = "4";
            break;
        
        case R.id.mouseButton5:
            key = "5";
            break;

        default:
            log("onClick: Unknown button");
            return;
            // return false;
        }

        //clickOn(key);
        anyRemote.protocol.queueCommand("_MB_("+key+",)");
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
        
        boolean lp = longPress;
        longPress = false;
        
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.isTracking() && !event.isCanceled() && lp) {
                log("onKeyUp KEYCODE_BACK long press - show menu");
                     
                new Handler().postDelayed(new Runnable() { 
                    public void run() { 
                        openOptionsMenu(); 
                      } 
                   }, 1000); 
                return true;
            } else {
                log("onKeyUp KEYCODE_BACK");
                commandAction(anyRemote.protocol.context.getString(R.string.back_item));
                return true;
            }
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
        switch (keyCode) {
          case KeyEvent.KEYCODE_BACK:
            event.startTracking();
            return true;
        }
        return false;
    }

    public void clickOn(String key) {
        log("clickOn "+key);
        anyRemote.protocol.queueCommand(key, true);
        anyRemote.protocol.queueCommand(key, false);
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
       
        if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE)  {  
            return;  
        }  
       
        // Many sensors return 3 values, one for each axis.
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
         
        if (mGyroscope != null) {
             
            if (timestamp != 0) {
                
                 final float dT = (event.timestamp - timestamp) * NS2S;
                 
                 // Axis of the rotation sample, not normalized yet.
                 float axisX = event.values[0];
                 float axisY = event.values[2];
                 float axisZ = event.values[1];
    
                 // Calculate the angular speed of the sample
                 float omegaMagnitude = (float) Math.sqrt(axisX*axisX + axisY*axisY + axisZ*axisZ);
    
                 // Normalize the rotation vector if it's big enough to get the axis
                 // (that is, EPSILON should represent your maximum allowable margin of error)
                 if (omegaMagnitude > EPSILON) {
                     axisX /= omegaMagnitude;
                     axisY /= omegaMagnitude;
                     axisZ /= omegaMagnitude;
                 }
    
                 // Integrate around this axis with the angular speed by the timestep
                 // in order to get a delta rotation from this sample over the timestep
                 // We will convert this axis-angle representation of the delta rotation
                 // into a quaternion before turning it into the rotation matrix.
                 float thetaOverTwo = omegaMagnitude * dT / 2.0f;
                 float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
                 float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);
                 deltaRotationVector[0] = sinThetaOverTwo * axisX;
                 deltaRotationVector[1] = sinThetaOverTwo * axisY;
                 deltaRotationVector[2] = sinThetaOverTwo * axisZ;
                 deltaRotationVector[3] = cosThetaOverTwo;
                 
            
            } else {  
                
                 // Axis of the rotation sample, not normalized yet.
                 float axisX = event.values[0];
                 float axisY = event.values[2];
                 float axisZ = event.values[1];
                
                 mLastX = axisX;
                 mLastY = axisY;
                 mLastZ = axisZ;
           }

           timestamp = event.timestamp;
           float[] deltaRotationMatrix = new float[9];
           SensorManager.getRotationMatrixFromVector(deltaRotationMatrix, deltaRotationVector);
             
           // User code should concatenate the delta rotation we computed with the current rotation
           // in order to get the updated rotation.
           //rotationCurrent = rotationCurrent * deltaRotationMatrix;
           
           //SensorManager.remapCoordinateSystem(deltaRotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, deltaRotationMatrix);
           SensorManager.getOrientation(deltaRotationMatrix, rotationCurrent);
           
           mLastZ = (float) Math.toDegrees(rotationCurrent[0]);
           mLastY = (float) Math.toDegrees(rotationCurrent[1]);
           mLastX = (float) Math.toDegrees(rotationCurrent[2]);
           
           x = mLastX;
           y = -mLastY;
           z = mLastZ;
           
         
        } else { // Accelerometer
                          
             //float deltaX = 0;
             //float deltaY = 0;
             //float deltaZ = 0;
             
             //if (!mInitialized) {
                 
                 mLastX = x;
                 mLastY = y;
                 mLastZ = z;
                 
                // mInitialized = true;
                 
              //} else {
                  
                 //deltaX = mLastX - x;
                 //deltaY = mLastY - y;
                 //deltaZ = mLastZ - z;
                 
                 //if (Math.abs(deltaX) < NOISE) deltaX = 0.0f;
                 //if (Math.abs(deltaY) < NOISE) deltaY = 0.0f;
                 //if (Math.abs(deltaZ) < NOISE) deltaZ = 0.0f;
                 
             //    mLastX = x;
             //    mLastY = y;
             //    mLastZ = z;
             //}
         }
         
         /*TextView tx = (TextView) findViewById(R.id.xval);
         if (tx != null) {
             tx.setText("X axis" +"\t\t"+mLastX);
         }
         TextView ty = (TextView) findViewById(R.id.yval);
         if (ty != null) {
             ty.setText("Y axis" + "\t\t" +mLastY);
         }
         TextView tz = (TextView) findViewById(R.id.zval);
         if (tz != null) {
             tz.setText("Z axis" +"\t\t" +mLastZ);
         }*/
         
         int mx = 0;
         int my = 0;
         float noise = (mGyroscope == null ? NOISE : NOISE_GYROSCOPE);
         
         if (Math.abs(x) > noise) {
             mx = (int) (x*x);
             if (x > 0) {
                 mx = -mx;
             }
         }
         
         if (Math.abs(y) > noise) {
             my = (int) (y*y);
             if (y > 0) {
                 my = -my;
             }
         }
  
         if (mx != 0 || my != 0) {
             anyRemote.protocol.queueCommand("_MM_("+mx+","+ my +")");
         }
    }
}
