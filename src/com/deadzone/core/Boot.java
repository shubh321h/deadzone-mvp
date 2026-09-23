package com.deadzone.core;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** On-screen boot diagnostic log. Everything goes to logcat AND
 *  files/deadzone_boot.txt AND a small overlay pinned over the game. */
public class Boot {
    private static TextView out;
    private static final StringBuilder buf = new StringBuilder();

    private static ScrollView scroller;

    /** Small, size-capped overlay pinned top-RIGHT (the game's own menu content
     *  is anchored top-left, so this corner never sits on top of real UI).
     *  Fixed dp size so it can never grow to cover the screen, however long
     *  the boot log gets — it scrolls internally instead. */
    public static View overlay(Context c) {
        float d = c.getResources().getDisplayMetrics().density;
        scroller = new ScrollView(c);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(0xB8000000);
        box.setPadding(12, 12, 12, 12);
        out = new TextView(c);
        out.setTextColor(0xFF9FE870);
        out.setTextSize(8);
        out.setTypeface(android.graphics.Typeface.MONOSPACE);
        out.setText("DEAD ZONE boot\n--------------\n");
        box.addView(out);
        scroller.addView(box);
        FrameLp lp = new FrameLp((int) (280 * d), (int) (160 * d));
        scroller.setLayoutParams(lp);
        return scroller;
    }

    private static class FrameLp extends android.widget.FrameLayout.LayoutParams {
        FrameLp(int w, int h) { super(w, h, Gravity.TOP | Gravity.END); }
    }

    public static void log(String s) {
        android.util.Log.i("DEADZONE", s);
        synchronized (buf) { buf.append(s).append('\n'); }
        if (out != null) out.post(new Runnable() { @Override public void run() {
            out.append("• " + s + "\n");
            if (scroller != null) scroller.fullScroll(View.FOCUS_DOWN);
        }});
    }

    /** Hide the overlay once boot is confirmed healthy, so it never sits over
     *  gameplay. The full text is still in deadzone_boot.txt via flush(). */
    public static void hide() {
        if (out != null) out.post(new Runnable() { @Override public void run() {
            if (scroller != null) scroller.setVisibility(View.GONE);
        }});
    }

    /** Write the log to files/deadzone_boot.txt. */
    public static void flush(Context c) {
        try {
            java.io.FileWriter w = new java.io.FileWriter(new java.io.File(c.getFilesDir(), "deadzone_boot.txt"));
            synchronized (buf) { w.write(buf.toString()); }
            w.close();
        } catch (Exception ignored) { }
    }
}
