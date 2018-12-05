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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import anyremote.client.android.R;
import anyremote.client.android.util.InfoMessage;
import anyremote.client.android.util.ProtocolMessage;

public class WebScreen extends arActivity 
                            implements KeyEvent.Callback {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefix = "WebScreen"; // log stuff
        log("onCreate");
        
        privateMenu = anyRemote.WEB_FORM;
    }
   
    @Override
    protected void onResume() {
        log("onResume");

        super.onResume();

        exiting = false;
        
        anyRemote.protocol.setFullscreen(this);

        //synchronized (anyRemote.protocol.webMenu) {
            setContentView(R.layout.web_form);
        //}
        
        WebView wwwView = (WebView) findViewById(R.id.web_view);
        wwwView.setWebViewClient(new WebViewClient());
        
        WebSettings webSettings = wwwView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        
        log("onResume loadUrl "+anyRemote.protocol.webUrl);
        wwwView.loadUrl(anyRemote.protocol.webUrl);
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
        /*case KeyEvent.KEYCODE_DPAD_UP:
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
            return (!anyRemote.protocol.cfUseJoystick ? "FIRE" : "");*/
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
                //commandAction(anyRemote.protocol.context.getString(R.string.back_item));
                doFinish("");
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        String title = item.getTitle().toString();
        log("onOptionsItemSelected " + title);
        
        if (title == anyRemote.protocol.context.getString(R.string.disconnect_item)) {
            doFinish("");
            return true; 
        } else if (title == anyRemote.protocol.context.getString(R.string.back_item)) {
            WebView wwwView = (WebView) findViewById(R.id.web_view);
            wwwView.loadUrl(anyRemote.protocol.webUrl+"/Back.menu");
            return true; 
        }
        
        commandAction(title);
        return true;
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
