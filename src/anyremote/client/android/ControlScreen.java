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

import java.util.Vector;

import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;
//import android.widget.SeekBar;
//import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TableLayout;
import android.widget.TextView;
import android.text.method.ScrollingMovementMethod;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.KeyEvent;
import android.view.WindowManager;
import anyremote.client.android.util.ControlScreenHandler;
import anyremote.client.android.util.ProtocolMessage;

public class ControlScreen extends arActivity 
                           implements View.OnClickListener,
                                      //View.OnTouchListener,
                                      KeyEvent.Callback {
  
   	static final int KEY_NUM1  = 1;
	static final int KEY_NUM2  = 2;
	static final int KEY_NUM3  = 3;
	static final int KEY_NUM4  = 4;
	static final int KEY_NUM5  = 5;
	static final int KEY_NUM6  = 6;
	static final int KEY_NUM7  = 7;
	static final int KEY_NUM8  = 8;
	static final int KEY_NUM9  = 9;
	static final int KEY_STAR  = 10;
	static final int KEY_NUM0  = 0;
	static final int KEY_POUND = 12;
	static final int KEY_UNKNOWN = -1;
	
   	static final String STR_NUM1  = "1";
	static final String STR_NUM2  = "2";
	static final String STR_NUM3  = "3";
	static final String STR_NUM4  = "4";
	static final String STR_NUM5  = "5";
	static final String STR_NUM6  = "6";
	static final String STR_NUM7  = "7";
	static final String STR_NUM8  = "8";
	static final String STR_NUM9  = "9";
	static final String STR_STAR  = "*";
	static final String STR_NUM0  = "0";
	static final String STR_POUND = "#";
	static final String STR_UNKNOWN = "";

	ControlScreenHandler evHandler; 
	
	static final int SK_DEFAULT    = 0;
    static final int SK_BOTTOMLINE = 1;
   
    static final int NUM_ICONS     = 12;
    static final int NUM_ICONS_BTM = 7;
    
    boolean fullscreen  = false;
    boolean useJoystick = false;
    
    ImageButton [] buttons;
    
    Vector<String> defMenu = new Vector<String>();
     
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);  
		
		prefix = "ControlScreen"; // log stuff
		log("onCreate");
		
		defMenu.add("Disconnect");
		defMenu.add("Exit");
		defMenu.add("Log");	
		
		buttons = new ImageButton[NUM_ICONS];
		setSkin();
		
		setTitle(anyRemote.protocol.cfCaption);
		
		setFont();
		setTextColor();
	    setBackground();
		setTitleField();
		setStatusField();
		setCover();
	    callMenuUpdate();
	    
	    //TextView title = (TextView) findViewById(R.id.cf_title);
	    //title.setMovementMethod(new ScrollingMovementMethod());
	    
	    //TextView title2 = (TextView) findViewById(R.id.cf_btitle);
	    //title2.setMovementMethod(new ScrollingMovementMethod());

		evHandler = new ControlScreenHandler(this);
		anyRemote.protocol.setControlScreenHandler(evHandler);
		anyRemote.protocol.setFullscreen(this);
		
		popup();
	}
	
	@Override
	protected void onStart() {
		log("onStart");		
		super.onStart();
	}
	
	@Override
	protected void onPause() {
		log("onPause");	
		
		dismissPopup();
		popup();
		
		//MainLoop.disable();
	    super.onPause();	
	}

	@Override
	protected void onResume() {
		log("onResume");		
		//MainLoop.enable();
		super.onResume();
		
        if (anyRemote.status == anyRemote.DISCONNECTED) {
        	log("onResume no connection");	
        	doFinish("");
        }
	}
	
	@Override
	protected void onStop() {
		log("onStop");		
	    super.onStop();	
	}

	@Override
	protected void onDestroy() {	
		log("onDestroy");		
		super.onDestroy();
	}

    public void handleEvent(ProtocolMessage data) {
	   
    	log("handleEvent "+ data.tokens);
	    
		if (data.tokens.size() == 0) {
			return;
		}
		
		if (data.stage != ProtocolMessage.FULL &&	// process only full commands
		    data.stage == ProtocolMessage.FIRST) {
			return;
		}

		Integer id  = (Integer) data.tokens.elementAt(0);
		
	    if (handleCommonCommand(id, data.tokens)) {
			return;
		}
	    
        if (id == Dispatcher.CMD_CLOSE) {
  			doFinish("close");
  		} else {
			processData(data.tokens);
		}
    }

    public void processData(Vector vR) {
    	
    	//log("processData >"+vR+"<");
    	if (vR.size() == 0) {
    		return;
    	}
    	
    	int id = (Integer) vR.elementAt(0);
    	
	    if (id == Dispatcher.CMD_CLOSE) {
	    	doFinish("close");
		 	return;
	    }
	    
	   	if (vR.size() < 2) {
    		return;
    	}
 
		switch (id) {
		
		    case Dispatcher.CMD_SKIN:
		    	
		    	setSkin(vR); 
			    break;
				      
		    case Dispatcher.CMD_STATUS:
			   
			    anyRemote.protocol.cfStatus = (String) vR.elementAt(1);
			    setStatusField();
				break;

		    case Dispatcher.CMD_TITLE:
		    	
		    	anyRemote.protocol.cfTitle = (String) vR.elementAt(1);
		    	setTitleField();
				break;
			
		    case Dispatcher.CMD_ICONS:
		    	
				setIconLayout(vR);
				break; 
				
  		    case Dispatcher.CMD_BG:
  		    	anyRemote.protocol.cfBkgr = anyRemote.parseColor(
                        (String) vR.elementAt(1),
                        (String) vR.elementAt(2),
                        (String) vR.elementAt(3));
  		    	setBackground();
  		    	
 				break; 
  		
  		    case Dispatcher.CMD_FG:

  		    	anyRemote.protocol.cfFrgr = anyRemote.parseColor(
                        (String) vR.elementAt(1),
                        (String) vR.elementAt(2),
                        (String) vR.elementAt(3));
  		    	setTextColor();
 		    	break;  
  		  
  		    case Dispatcher.CMD_FONT:
  		    	
			    setFontParams(vR);
			    setFont();
			    break; 
			     
		    case Dispatcher.CMD_FSCREEN:
		    	
		    	anyRemote.protocol.setFullscreen((String) vR.elementAt(1), this);
			    break;   
			 				
		    case Dispatcher.CMD_VOLUME:
		    	
				//screen.controlForm.setVolume((String) cmdTokens.elementAt(1));
		    	Toast.makeText(this, "Volume is "+(String) vR.elementAt(1) +"%", Toast.LENGTH_SHORT).show();
				break;  
				   
			case Dispatcher.CMD_IMAGE:
				//controller.cScreen.setData(anyRemote.WMAN_FORM,cmdTokens,stage);
				break;    

			case Dispatcher.CMD_COVER:
				anyRemote.protocol.cfCover = (Bitmap) vR.elementAt(1); 
			    setCover();
				break;
		}
    }
    
    private void setTitleField() {
    	//log("setTitleField "+anyRemote.protocol.cfTitle);
    	
    	int id = R.id.cf_title;
        if (anyRemote.protocol.cfSkin == SK_BOTTOMLINE) {
	        id = R.id.cf_btitle;
        } 
	    TextView title = (TextView) findViewById(id);
	    
	    title.setText(anyRemote.protocol.cfTitle);
	    
	    //title.setMovementMethod(new ScrollingMovementMethod());
    }
    
    private void setStatusField() {
    	//log("setStatusField "+anyRemote.protocol.cfStatus);
    	
    	int id = R.id.cf_status;
        if (anyRemote.protocol.cfSkin == SK_BOTTOMLINE) {
        	id = R.id.cf_bstatus;
        } 
        TextView status = (TextView) findViewById(id);
        //status.setMovementMethod(new ScrollingMovementMethod());
        
    	status.setText(anyRemote.protocol.cfStatus);
    }
    
    private void setIconLayout(Vector data) {
     	
    	if (data.size() == 0) {
     	    return;
    	}
    	
		if (!((String) data.elementAt(1)).equals("SAME")) {
			anyRemote.protocol.cfCaption = (String) data.elementAt(1);
			setTitle(anyRemote.protocol.cfCaption);
        }
		
		int maxIcon = (anyRemote.protocol.cfSkin == SK_BOTTOMLINE ?  NUM_ICONS_BTM : NUM_ICONS);
		
        for (int idx=2;idx<data.size()-1;idx+=2) {
         	try {
        		int i = btn2int((String) data.elementAt(idx));

        		if (i >= 0 || i < maxIcon) {    
        			anyRemote.protocol.cfIcons[i] = (String) data.elementAt(idx+1);
       		    }  
	         } catch (Exception e) { }
        }
        
        setSkin();
    }
   
	public void setSkin(Vector vR) {
        String name   = (String) vR.elementAt(1);

        //useCover    = false;
        //cover       = null;
         
        //boolean newVolume = false;
    	//int     newSize   = icSize;
        
        useJoystick = true;

        boolean oneMore = false;
        
        for (int i=2;i<vR.size();) {
        
	        String oneParam = (String) vR.elementAt(i);
                
    		if (oneMore) {
        		try {
        			//newCur = btn2int(oneParam);
                } catch (NumberFormatException e) {
 	            }
                	
                oneMore = false;
    		} else if (oneParam.equals("joystick_only")) {
        		useJoystick = true;
		        //useKeypad   = false;
    		} else if (oneParam.equals("keypad_only")) {
        		useJoystick = false;
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
    		} else if (oneParam.equals("split")) {
	            //newSplit = true;
    		} else if (oneParam.equals("choose")) {
		        //oneMore = true;
    		} else if (oneParam.equals("up")) {
                i++;
                if (i<vR.size()) {
                    //upEvent = (String) vR.elementAt(i);
                }
    		} else if (oneParam.equals("down")) {
                i++;
                if (i<vR.size()) {
                	//downEvent = (String) vR.elementAt(i);
                }
    		} 
            i++;
        }
        
	    int newSkin = anyRemote.protocol.cfSkin;
	    if (name.equals("default")) {
        	newSkin = SK_DEFAULT;
        } else if (name.equals("bottomline")) {
        	newSkin = SK_BOTTOMLINE;
         }

	    if (anyRemote.protocol.cfSkin  != newSkin) {
            anyRemote.protocol.cfSkin = newSkin;
            setSkin();
 	    }
    }
	
	private void setSkinSimple() {
		
		Display display = getWindowManager().getDefaultDisplay(); 
		int sz = (display.getWidth() > display.getHeight() ? display.getHeight() : display.getWidth());

		if (anyRemote.protocol.cfSkin == SK_BOTTOMLINE) {
			
			//log("setSkin SK_BOTTOMLINE");
			
			setContentView(R.layout.control_form_bottomline);
			
			buttons[0]  = (ImageButton) findViewById(R.id.bb1);
			buttons[1]  = (ImageButton) findViewById(R.id.bb2);
			buttons[2]  = (ImageButton) findViewById(R.id.bb3);
			buttons[3]  = (ImageButton) findViewById(R.id.bb4);
			buttons[4]  = (ImageButton) findViewById(R.id.bb5);
			buttons[5]  = (ImageButton) findViewById(R.id.bb6);
			buttons[6]  = (ImageButton) findViewById(R.id.bb7);
			buttons[7]  = null;
			buttons[8]  = null;
			buttons[9]  = null;
			buttons[10] = null;
			buttons[11] = null;
			
	        int realCnt = 0;
			for (int i=0;i<NUM_ICONS_BTM;i++) {
				Bitmap ic = anyRemote.getIconBitmap(getResources(), anyRemote.protocol.cfIcons[i]);
				
				buttons[i].setImageBitmap(ic);
				
				if (ic == null) {
					buttons[i].setVisibility(View.GONE);
				} else {
					buttons[i].setVisibility(View.VISIBLE);
				    realCnt++;
				}
				//buttons[i].setOnTouchListener(this);
				buttons[i].setOnClickListener(this);
			}
			
			for (int i=0;i<NUM_ICONS_BTM;i++) {
				buttons[i].setMaxHeight(sz/realCnt);    	
				buttons[i].setMaxWidth (sz/realCnt);
			}
			
			ImageView cover = (ImageView) findViewById(R.id.cover);
			cover.setMaxHeight((2*sz)/3);    	
			cover.setMaxWidth ((2*sz)/3);
			cover.setBackgroundColor(anyRemote.protocol.cfBkgr);

		} else {
			
			//log("setSkin SK_DEFAULT");
			
		    setContentView(R.layout.control_form_default);

			buttons[0]  = (ImageButton) findViewById(R.id.b1);
			buttons[1]  = (ImageButton) findViewById(R.id.b2);
			buttons[2]  = (ImageButton) findViewById(R.id.b3);
			buttons[3]  = (ImageButton) findViewById(R.id.b4);
			buttons[4]  = (ImageButton) findViewById(R.id.b5);
			buttons[5]  = (ImageButton) findViewById(R.id.b6);
			buttons[6]  = (ImageButton) findViewById(R.id.b7);
			buttons[7]  = (ImageButton) findViewById(R.id.b8);
			buttons[8]  = (ImageButton) findViewById(R.id.b9);
			buttons[9]  = (ImageButton) findViewById(R.id.b10);
			buttons[10] = (ImageButton) findViewById(R.id.b0);
			buttons[11] = (ImageButton) findViewById(R.id.b11);
			
			for (int i=0;i<NUM_ICONS;i++) {
				
				Bitmap ic = anyRemote.getIconBitmap(getResources(), anyRemote.protocol.cfIcons[i]);
				if (ic == null) { // no to squeze
					ic = anyRemote.getIconBitmap(getResources(), "transparent"); 
				}
				
				buttons[i].setImageBitmap(ic);
								
				buttons[i].setVisibility(View.VISIBLE);
				buttons[i].setOnClickListener(this);
				
				buttons[i].setMaxHeight(sz/4);    	
				buttons[i].setMaxWidth (sz/4);
			}
		}
	}
	
	private void setSkin() {
	    setSkinSimple();
		setFont();
		setTextColor();
	    setBackground();
		setTitleField();
		setStatusField();
		setCover();
	}
	
	private void setBackground() {
		
		int id = R.id.skin_default;
		int maxIcon = NUM_ICONS;
		if (anyRemote.protocol.cfSkin == SK_BOTTOMLINE) {
	        id = R.id.skin_bottomline;
	        maxIcon = NUM_ICONS_BTM;
	        
	        ImageView cover = (ImageView) findViewById(R.id.cover);
	        cover.setBackgroundColor(anyRemote.protocol.cfBkgr);
	        
	        RelativeLayout ll = (RelativeLayout) findViewById(id);
		    ll.setBackgroundColor(anyRemote.protocol.cfBkgr);

		} else {
            LinearLayout ll = (LinearLayout) findViewById(id);
	        ll.setBackgroundColor(anyRemote.protocol.cfBkgr);
	        
	        TableLayout tl = (TableLayout) findViewById(R.id.icons);
	        tl.setBackgroundColor(anyRemote.protocol.cfBkgr);
		}
	    for (int i=0;i<maxIcon;i++) {
			//buttons[i].setBackgroundColor(anyRemote.protocol.cfBkgr);
		}
	}
	
	private void setTextColor() {
		
		int t = R.id.cf_title;
		int s = R.id.cf_status;
		if (anyRemote.protocol.cfSkin == SK_BOTTOMLINE) {
	       t = R.id.cf_btitle;
	       s = R.id.cf_bstatus;
		} 		
		TextView title  = (TextView) findViewById(t);
		TextView status = (TextView) findViewById(s);
		
	    title.setTextColor (anyRemote.protocol.cfFrgr);
	    status.setTextColor(anyRemote.protocol.cfFrgr);
	}
	
	private void setCover() {
		
		if (anyRemote.protocol.cfSkin == SK_BOTTOMLINE && anyRemote.protocol.cfCover != null) {
			ImageView cover  = (ImageView) findViewById(R.id.cover);
			cover.setImageBitmap(anyRemote.protocol.cfCover);
		}
	}
	
	private void setFontParams(Vector defs) {
		
		boolean bold   = false;
		boolean italic = false;
		float   size   = Dispatcher.SIZE_MEDIUM; 
		
		int start = 1;
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
	    	anyRemote.protocol.cfTFace = Typeface.defaultFromStyle(Typeface.BOLD_ITALIC);
	    } else if (bold) {
	    	anyRemote.protocol.cfTFace = Typeface.defaultFromStyle(Typeface.BOLD);
	    } else if (italic) {
	    	anyRemote.protocol.cfTFace = Typeface.defaultFromStyle(Typeface.ITALIC);
	    } else {
	    	anyRemote.protocol.cfTFace = Typeface.defaultFromStyle(Typeface.NORMAL);
	    }
	    anyRemote.protocol.cfFSize = size;
	}
	
    private void setFont() {
       	
		int t = R.id.cf_title;
		int s = R.id.cf_status;
		if (anyRemote.protocol.cfSkin == SK_BOTTOMLINE) {
	       t = R.id.cf_btitle;
	       s = R.id.cf_bstatus;
		} 		
		TextView title  = (TextView) findViewById(t);
		TextView status = (TextView) findViewById(s);

		title.setTypeface (anyRemote.protocol.cfTFace);
	    status.setTypeface(anyRemote.protocol.cfTFace);
	    
	    //log("SetFont " +anyRemote.protocol.cfFSize);
	    title.setTextSize (anyRemote.protocol.cfFSize);
	    status.setTextSize(anyRemote.protocol.cfFSize);
	}
    
    
    public void onClick (View v) {  
    	
    	String key = "";
    	
    	switch (v.getId()) {
		
		  case R.id.b1: 
		  case R.id.bb1: 
	    	  key = STR_NUM1;	
			  break;
			  
		  case R.id.b2: 
		  case R.id.bb2: 
	    	  key = STR_NUM2;	
			  break;
			  
		  case R.id.b3: 
		  case R.id.bb3: 
	    	  key = STR_NUM3;	
			  break;
			  
		  case R.id.b4: 
		  case R.id.bb4: 
			  key = STR_NUM4;
			  break;
			  
		  case R.id.b5: 
		  case R.id.bb5: 
			  key = STR_NUM5;
			  break;
			  
		  case R.id.b6: 
		  case R.id.bb6: 
			  key = STR_NUM6;
			  break;
				
		  case R.id.b7: 
		  case R.id.bb7: 
			  key = STR_NUM7;
		      break;
				
		  case R.id.b8: 
			  key = STR_NUM8;
			  break;
				
		  case R.id.b9: 
			  key = STR_NUM9;
			  break;
			  
		  case R.id.b10: 
			  key = STR_STAR;
			  break;
		 
		  case R.id.b0: 
			  key = STR_NUM0;
			  break;
			 
		  case R.id.b11: 
			  key = STR_POUND;
		      break;
      
		  default:
			  log("onClick: Unknown button");
			  return;
		}
    	
    	keyReleased(key);
    }
    
	/*public boolean onTouch(View v, MotionEvent me) {  
    	
    	log("onTouch "+" "+v.getId()+" "+ R.id.b1);
    	
    	int a = me.getAction();
    	//String key = "";
    	int btnIdx = -1;
    	
    	switch (v.getId()) {
		
		  case R.id.b1: 
		  case R.id.bb1: 
	    	  //key = STR_NUM1;
	    	  btnIdx = 0;
			  break;
			  
		  case R.id.b2: 
		  case R.id.bb2: 
	    	  //key = STR_NUM2;
			  btnIdx = 1;
			  break;
			  
		  case R.id.b3: 
		  case R.id.bb3: skin
	    	  //key = STR_NUM3;	
			  btnIdx = 2;
			  break;
			  
		  case R.id.b4: 
		  case R.id.bb4: 
			  //key = STR_NUM4;
			  btnIdx = 3;
			  break;
			  
		  case R.id.b5: 
		  case R.id.bb5: 
			  //key = STR_NUM5;
			  btnIdx = 4;
			  break;
			  
		  case R.id.b6: 
		  case R.id.bb6: 
			  //key = STR_NUM6;
			  btnIdx = 5;
			  break;
				
		  case R.id.b7: 
		  case R.id.bb7: 
			  //key = STR_NUM7;
			  btnIdx = 6;
		      break;
				
		  case R.id.b8: 
			  //key = STR_NUM8;
			  btnIdx = 7;
			  break;
				
		  case R.id.b9: 
			  //key = STR_NUM9;
			  btnIdx = 8;
			  break;
			  
		  case R.id.b10: 
			  //key = STR_STAR;
			  btnIdx = 9;
			  break;
		 
		  case R.id.b0: 
			  //key = STR_NUM0;
			  btnIdx = 10;
			  break;
			 
		  case R.id.b11: 
			  //key = STR_POUND;
			  btnIdx = 11;
		      break;
        
		  default:
			  log("onTouch: Unknown button");
			  return false;
		}
    	
    	if (btnIdx >= 0) {
    	    buttons[btnIdx].setBackgroundColor(
    	    		 a == MotionEvent.ACTION_DOWN ? 
    	    				 anyRemote.protocol.cfFrgr :
    	    				 anyRemote.protocol.cfBkgr);
    	}
    	return false;
    }*/
	
	private String key2str(int keyCode) { 

		switch (keyCode) {
		    case KeyEvent.KEYCODE_1:     return "1";
			case KeyEvent.KEYCODE_2:     return "2";
			case KeyEvent.KEYCODE_3:     return "3";
			case KeyEvent.KEYCODE_4:     return "4";
			case KeyEvent.KEYCODE_5:     return "5";
			case KeyEvent.KEYCODE_6:     return "6";
			case KeyEvent.KEYCODE_7:     return "7";
			case KeyEvent.KEYCODE_8:     return "8";
			case KeyEvent.KEYCODE_9:     return "9";
			case KeyEvent.KEYCODE_STAR:  return "*";
			case KeyEvent.KEYCODE_0:     return "0";
			case KeyEvent.KEYCODE_POUND: return "#";
			case KeyEvent.KEYCODE_MENU: 
			case KeyEvent.KEYCODE_HOME: 
			case KeyEvent.KEYCODE_BACK:   return "";		// do not process them
			case KeyEvent.KEYCODE_VOLUME_UP:   return "VOL+";
			case KeyEvent.KEYCODE_VOLUME_DOWN: return "VOL-";
			case KeyEvent.KEYCODE_DPAD_UP:     
				    return (useJoystick || 
				    		anyRemote.protocol.cfSkin == SK_BOTTOMLINE ? "UP"   : "");   // do not process them if joystick_only param was set
			case KeyEvent.KEYCODE_DPAD_DOWN:   
				    return (useJoystick || 
				    		anyRemote.protocol.cfSkin == SK_BOTTOMLINE ? "DOWN" : "");
			case KeyEvent.KEYCODE_DPAD_LEFT:   
				    return (useJoystick ? "LEFT" : "");   // do not process them if joystick_only param was set 
			case KeyEvent.KEYCODE_DPAD_RIGHT:  
				    return (useJoystick ? "RIGHT": "");
			case KeyEvent.KEYCODE_DPAD_CENTER: 
				    return (useJoystick ? "FIRE" : "");
			case KeyEvent.KEYCODE_SEARCH:      return "SEARCH";
			default: 
				if (keyCode >= 0 && keyCode < 10) {
					return "K"+String.valueOf(keyCode);
				}
				return String.valueOf(keyCode); 
        }
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) { 
		log("onKeyUp "+keyCode);
		
		String key = key2str(keyCode); 
		if (key.length() > 0) {
            anyRemote.protocol.queueCommand(key, false);
            return true;
		}
        return false;
    }
	
	@Override
	public boolean onKeyDown (int keyCode, KeyEvent event) { 
		
		log("onKeyDown "+keyCode);

		String key = key2str(keyCode);
		if (key.length() > 0) {
            anyRemote.protocol.queueCommand(key, true);
            return true;
		}
	    return false;
    }

	public void keyPressed(int keyCode) {

	}
	
	public void keyPressed(String key) {

	}
	
	public void keyReleased(int keyCode) {
		anyRemote.protocol.queueCommand(keyCode, true);
		anyRemote.protocol.queueCommand(keyCode, false);
 	}
	
	public void keyReleased(String key) {
		anyRemote.protocol.queueCommand(key, true);
		anyRemote.protocol.queueCommand(key, false);
 	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
     	menu.clear();
    	return true;
    }
	 
    @Override
	public boolean onPrepareOptionsMenu(Menu menu) { 
    	menu.clear();
			      
	    for(int i = 0;i<menuItems.size();i++) {
		    menu.add(menuItems.elementAt(i));
	    }
  		return true;
	}
    
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    commandAction(item.getTitle().toString());
		return true;
	}
	
	@Override
	public void onBackPressed() { 
		commandAction("Exit");
	}
	
	public void commandAction(String command) {
		log("commandAction "+command);
		
        if (command.equals("Exit")) {	
        	doFinish("exit");		  
        } else if (command.equals("Disconnect")) {
        	doFinish("disconnect");	
        } else if (command.equals("Log")) {
        	doFinish("log");	
	    } else {
	        anyRemote.protocol.queueCommand(command);
        }
    }
	
   private void doFinish(String action) {
    	
    	log("doFinish "+action);
 
    	anyRemote.protocol.setControlScreenHandler(null);
    	
	    final Intent intent = new Intent();  
	    intent.putExtra(anyRemote.ACTION, action);
        setResult(RESULT_OK, intent);
        finish();  	
    }
	
	void callMenuUpdate()  { // Add predefined menu items
		 menuItems.add("Disconnect");
		 menuItems.add("Exit");
		 menuItems.add("Log");	
		 restorePersistentMenu(anyRemote.protocol.cfMenu);
	}
	
	public void processMenu(Vector vR) {
		processMenu(vR, anyRemote.protocol.cfMenu, defMenu);
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
	
	/*private int key2num(int keycode) {
		
		switch (keycode) {
			case KEY_NUM1: return 0;
			case KEY_NUM2: return 1;
			case KEY_NUM3: return 2;
			case KEY_NUM4: return 3;
			case KEY_NUM5: return 4;
			case KEY_NUM6: return 5;
			case KEY_NUM7: return 6;
			case KEY_NUM8: return 7;
			case KEY_NUM9: return 8;
			case KEY_STAR: return 9;
			case KEY_NUM0: return 10;
			case KEY_POUND: return 11;
			default: return -1;
		}
	}

	private int num2key(int num) {
		
		switch (num) {
			case 0 : return KEY_NUM1;
			case 1 : return KEY_NUM2;
			case 2 : return KEY_NUM3;
			case 3 : return KEY_NUM4;
			case 4 : return KEY_NUM5;
			case 5 : return KEY_NUM6;
			case 6 : return KEY_NUM7;
			case 7 : return KEY_NUM8;
			case 8 : return KEY_NUM9;
			case 9 : return KEY_STAR;
			case 10: return KEY_NUM0;
			case 11: return KEY_POUND;
			default: return KEY_UNKNOWN;
		}
	}*/
}
