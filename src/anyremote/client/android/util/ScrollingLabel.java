package anyremote.client.android.util;

import android.widget.TextView;
import android.content.Context;
import android.util.AttributeSet;
import android.view.animation.LinearInterpolator;
import android.widget.Scroller;

public class ScrollingLabel extends TextView implements Runnable {

    private static final float DEFAULT_SPEED = 15.0f;

    private Scroller scroller;
    private float speed = DEFAULT_SPEED;
    private boolean continuousScrolling = true;

    public ScrollingLabel(Context context) {
        super(context);
        setup(context);
    }

    public ScrollingLabel(Context context, AttributeSet attributes) {
        super(context, attributes);
        setup(context);
    }

    private void setup(Context context) {
        scroller = new Scroller(context, new LinearInterpolator());
        setScroller(scroller);
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
        int visiblePx = viewWidth - getPaddingLeft() - getPaddingRight();
        int lineSzPx  = (int) getTextSize();

        int offset = -1 * visiblePx;
        int distance = visiblePx + getLineCount() * lineSzPx;
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