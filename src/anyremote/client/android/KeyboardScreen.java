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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import anyremote.client.android.util.InfoMessage;
import anyremote.client.android.util.ProtocolMessage;

public class KeyboardScreen extends arActivity 
                            implements KeyEvent.Callback {

    Dispatcher.ArHandler hdlLocalCopy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefix = "KeyboardScreen"; // log stuff
        log("onCreate");
 
        hdlLocalCopy = new Dispatcher.ArHandler(anyRemote.KEYBOARD_FORM, new Handler(this));
        anyRemote.protocol.addMessageHandler(hdlLocalCopy);
        
        privateMenu = anyRemote.KEYBOARD_FORM;
         
   }

    /*
     * @Override protected void onStart() { log("onStart"); super.onStart(); }
     */

    @Override
    protected void onPause() {
        log("onPause");
        hidePopup();
        
        View kView = findViewById(R.id.keyboard_view);
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(kView.getWindowToken(), 0);
        
        //getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        
        super.onPause();
    }

    @Override
    protected void onResume() {
        log("onResume");

        super.onResume();
        
        if (anyRemote.status == anyRemote.DISCONNECTED) {
            log("onResume no connection");
            doFinish("");
            return;
        }

        exiting = false;
        
        anyRemote.protocol.setFullscreen(this);

        synchronized (anyRemote.protocol.keyboardMenu) {
            setContentView(R.layout.keyboard_form_default);
        }
        
        final EditText text = (EditText) findViewById(R.id.keyboard_view);

        text.addTextChangedListener(new TextWatcher() {
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                log("onTextChanged >"+s+"<"+start+" "+before+" "+count);
                if (s.length() == 0 || count == 0) {
                    log("skip input");
                    return;
                }
                for (int idx = start; idx < start + count; idx++) { 
                    char inputCh[] = new char[1];
                    inputCh[0]= s.charAt(idx);
                   
                    String k = new String(inputCh,0,1);
                    log("got input >"+k+"<");
                    if (inputCh[0] == ',') {
                        k = "comma";
                    } else if (inputCh[0] == ';') {
                        k = "semicolon";
                    } else if (inputCh[0] == ' ') {
                        k = "space";
                    } else if (inputCh[0] == '(') {
                        k = "parenleft";
                    } else if (inputCh[0] == ')') {
                        k = "parenright";
                    }
                    anyRemote.protocol.queueCommand("_KB_(,"+k+")");
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void afterTextChanged(Editable s) { 
                if (s.length() > 0) {
                    text.setText("");
                }
            }
        });
        
        
        
        /*findViewById(R.id.keyboard_view).post(
            new Runnable() {
                public void run() {
                   
                    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
  
                    View kView = findViewById(R.id.keyboard_view);
                    
                    InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                    imm.showSoftInput(kView, 0);
                    kView.requestFocus();
                    
                    //imm.toggleSoftInputFromWindow(kView.getApplicationWindowToken(), InputMethodManager.SHOW_FORCED, 0);
                }
            }
        );*/
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
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        commandAction(item.getTitle().toString());
        return true;
    }
    
    public void onToggleClicked(View view) {
        boolean on = ((ToggleButton) view).isChecked();
        int id = view.getId();
        //log("onToggleClicked " + (id == R.id.ctrlButton ? "CTRL " : "ALT ") + on );
        String btn = (id == R.id.ctrlButton ? "Ctrl" : "Alt");
        char   v = (on ? '1' : '0');
        
        anyRemote.protocol.queueCommand("_KM_(" + v +"," + btn + ")");
    }
    
    public void onClicked(View view) {
        int id = view.getId();
        
        //log("onToggleClicked " + (id == R.id.ctrlButton ? "CTRL " : "ALT ") + on );
        String btn = (id == R.id.tabButton ? "Tab" : "");
        if (btn.length() == 0) {
            return;
        }
        
        anyRemote.protocol.queueCommand("_KB_(," + btn + ")");
    }

    public void commandAction(String command) {
        log("commandAction " + command);
        if (command.equals(anyRemote.protocol.context.getString(R.string.back_item))) {
            doFinish("");
        } else if (command.equals(anyRemote.protocol.context.getString(R.string.escape))) {
            anyRemote.protocol.queueCommand("_KB_(,Escape)");
        } else if (command.equals(anyRemote.protocol.context.getString(R.string.enter))) {
            anyRemote.protocol.queueCommand("_KB_(,Return)");
        } else if (command.equals(anyRemote.protocol.context.getString(R.string.backspace))) {
            anyRemote.protocol.queueCommand("_KB_(,BackSpace)");
        } else if (command.equals(anyRemote.protocol.context.getString(R.string.alt_f4))) {
            anyRemote.protocol.queueCommand("_KP_(,Alt_L)");
            anyRemote.protocol.queueCommand("_KB_(,F4)");
            anyRemote.protocol.queueCommand("_KR_(,Alt_L)");
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

}
