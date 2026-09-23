package com.deadzone.input;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** Fixed-base virtual joystick. Outputs normalized vector (x right, y up=forward). */
public class JoystickView extends View {
    public float x, y;
    private float radius, knob;
    private int active = -1;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public JoystickView(Context c) {
        super(c);
        setWillNotDraw(false);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        radius = Math.min(w, h) / 2f;
        knob = radius * 0.45f;
    }

    @Override protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x22FFFFFF);
        c.drawCircle(cx, cy, radius, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f);
        p.setColor(0x55FFFFFF);
        c.drawCircle(cx, cy, radius, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(active >= 0 ? 0xAA9EFF7A : 0x669EFF7A);
        c.drawCircle(cx + x * knob, cy - y * knob, knob, p);
    }

    private void trackAt(int idx, MotionEvent e) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float dx = e.getX(idx) - cx, dy = e.getY(idx) - cy;
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        float max = radius * 0.9f;
        if (l > max) { dx *= max / l; dy *= max / l; }
        x = dx / max;
        y = -dy / max;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int act = e.getActionMasked();
        if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_POINTER_DOWN) {
            if (active < 0) {
                active = e.getActionIndex();
                trackAt(active, e);
                invalidate();
                return true;
            }
        } else if (act == MotionEvent.ACTION_MOVE && active >= 0 && e.getPointerCount() > active) {
            trackAt(active, e);
            invalidate();
        } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_POINTER_UP) {
            if (e.getActionIndex() == active) {
                active = -1;
                x = 0; y = 0;
                invalidate();
            }
        } else if (act == MotionEvent.ACTION_CANCEL) {
            active = -1; x = 0; y = 0;
            invalidate();
        }
        return true;
    }
}
