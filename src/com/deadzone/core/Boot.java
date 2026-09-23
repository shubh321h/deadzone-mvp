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

    /** Small overlay pinned top-left, above everything. */
    public static View overlay(Context c) {
        ScrollView sv = new ScrollView(c);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(0xB8000000);
        box.setPadding(12, 20, 12, 12);
        out = new TextView(c);
        out.setTextColor(0xFF9FE870);
        out.setTextSize(9);
        out.setTypeface(android.graphics.Typeface.MONOSPACE);
        out.setText("DEAD ZONE boot\n--------------\n");
        box.addView(out);
        sv.addView(box);
        sv.setLayoutParams(new FrameLp());
        return sv;
    }

    private static class FrameLp extends android.widget.FrameLayout.LayoutParams {
        FrameLp() { super(android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START); }
    }

    public static void log(String s) {
        android.util.Log.i("DEADZONE", s);
        synchronized (buf) { buf.append(s).append('\n'); }
        if (out != null) out.post(new Runnable() { @Override public void run() {
            out.append("• " + s + "\n");
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
