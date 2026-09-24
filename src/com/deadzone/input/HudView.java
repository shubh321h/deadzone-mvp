package com.deadzone.input;

import android.content.Context;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.View;
import android.graphics.Canvas;
import android.widget.FrameLayout;

import com.deadzone.core.Game;

import java.util.HashMap;

/** Full-screen transparent layer hosting HUD widgets. Unconsumed touches
 *  become camera drags; everything is consumed so the GL view never sees input. */
public class HudView extends FrameLayout {
    private final Game game;
    public com.deadzone.ui.Hud hud; // informational layer, drawn in onDraw
    private final HashMap<Integer, PointF> cam = new HashMap<Integer, PointF>();

    public HudView(Context c, Game g) {
        super(c);
        game = g;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (hud != null) hud.drawLayer(canvas, getWidth(), getHeight());
    }

    public Game gameRef() { return game; }

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        boolean handled = super.dispatchTouchEvent(e);
        if (!handled) routeCamera(e);
        return true;
    }

    private void routeCamera(MotionEvent e) {
        int act = e.getActionMasked();
        switch (act) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = e.getActionIndex();
                cam.put(e.getPointerId(idx), new PointF(e.getX(idx), e.getY(idx)));
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int pid = e.getPointerId(i);
                    PointF last = cam.get(pid);
                    if (last != null) {
                        float x = e.getX(i), y = e.getY(i);
                        game.input.cameraDrag(x - last.x, y - last.y);
                        last.set(x, y);
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int idx = e.getActionIndex();
                cam.remove(e.getPointerId(idx));
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                cam.clear();
                break;
        }
    }
}
