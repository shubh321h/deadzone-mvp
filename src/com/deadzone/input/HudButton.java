package com.deadzone.input;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** Round translucent HUD button. hold=true: onDown/onUp. hold=false: onClick on tap. */
public class HudButton extends View {
    public interface Listener {
        void onDown(View v);
        void onUp(View v);
        void onClick(View v);
    }

    public String label = "";
    public boolean hold;
    public Listener listener;
    public boolean enabledFlag = true;
    private int active = -1;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public HudButton(Context c) {
        super(c);
        setWillNotDraw(false);
    }

    public void setLabel(String l) { label = l; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) / 2f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(active >= 0 ? 0x4D7CFC66 : 0x26FFFFFF);
        c.drawCircle(cx, cy, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f);
        p.setColor(active >= 0 ? 0xCC7CFC66 : 0x55FFFFFF);
        c.drawCircle(cx, cy, r - 1.5f, p);
        p.setTextSize(r * 0.42f);
        p.setColor(enabledFlag ? 0xEEFFFFFF : 0x55FFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        float tw = p.measureText(label);
        if (tw > r * 1.7f) {
            p.setTextSize(r * 1.7f / tw * (r * 0.42f));
        }
        c.drawText(label, cx, cy - (p.descent() + p.ascent()) / 2f, p);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!enabledFlag) return false;
        int act = e.getActionMasked();
        if (act == MotionEvent.ACTION_DOWN) {
            active = e.getPointerId(0);
            invalidate();
            if (listener != null && hold) listener.onDown(this);
            return true;
        }
        if (active < 0) return false;
        if (e.findPointerIndex(active) < 0) return false;
        if (act == MotionEvent.ACTION_UP) {
            active = -1;
            invalidate();
            if (listener != null) {
                if (hold) listener.onUp(this);
                else listener.onClick(this);
            }
            return true;
        }
        if (act == MotionEvent.ACTION_CANCEL) {
            active = -1;
            invalidate();
            if (listener != null && hold) listener.onUp(this);
            return true;
        }
        return true;
    }
}
