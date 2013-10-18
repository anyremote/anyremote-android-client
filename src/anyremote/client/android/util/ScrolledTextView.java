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


package anyremote.client.android.util;

import android.content.Context;

import android.util.AttributeSet;
import android.widget.TextView;
import anyremote.client.android.anyRemote;
import android.content.Context;
import android.view.animation.LinearInterpolator;
import android.widget.Scroller;

public class ScrolledTextView extends TextView implements Runnable {

        private static final float DEFAULT_SPEED = 1.0f;

        private Scroller scroller;
        private float speed = DEFAULT_SPEED;
        private boolean continuousScrolling = true;

        public ScrolledTextView(Context context) {
            super(context);
            setup(context);
        }

        public ScrolledTextView(Context context, AttributeSet attributes) {
            super(context, attributes);
            setup(context);
        }

        private void setup(Context context) {
            scroller = new Scroller(context, new LinearInterpolator());
            setScroller(scroller);
        }
        
        public void setScrollableText(String text) {
            anyRemote._log("ScrolledTextView", "setScrollableText " + text);
            setText(text);
            scroll();
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            if (scroller.isFinished()) {
                scroll();
            }
        }

        private void scroll() {
            int viewWidth = getWidth();
            int visibleWidth = viewWidth - getPaddingLeft() - getPaddingRight();
 
            int offset = -1 * visibleWidth;
            int distance = 1;
            int duration = (int) (distance * speed);

            scroller.startScroll(offset, 0, distance, 0, duration);

            if (continuousScrolling) {
                post(this);
            }
        }

        @Override
        public void run() {
            if (scroller.isFinished()) {
                scroll();
            } else {
                post(this);
            }
        }

        public void setSpeed(float speed) {
            this.speed = speed;
        }

        public float getSpeed() {
            return speed;
        }

        public void setContinuousScrolling(boolean continuousScrolling) {
            this.continuousScrolling = continuousScrolling;
        }

        public boolean isContinuousScrolling() {
            return continuousScrolling;
        }
 }