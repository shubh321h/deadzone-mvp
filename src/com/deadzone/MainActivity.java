package com.deadzone;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import com.deadzone.core.Game;

public class MainActivity extends Activity {
    private Game game;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            game = new Game(this);
            setContentView(game.buildUi());
        } catch (Throwable t) {
            showFatal(t);
        }
    }

    /** Full-screen crash report — a boot failure must never be a silent black screen. */
    public void showFatal(Throwable t) {
        android.util.Log.e("DEADZONE", "fatal", t);
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        try {
            java.io.FileWriter fw = new java.io.FileWriter(
                    new java.io.File(getFilesDir(), "deadzone_crash.txt"));
            fw.write(sw.toString());
            fw.close();
        } catch (Exception ignored) { }
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        android.widget.LinearLayout l = new android.widget.LinearLayout(this);
        l.setOrientation(android.widget.LinearLayout.VERTICAL);
        l.setPadding(24, 24, 24, 48);
        l.setBackgroundColor(0xFF230D0D);
        android.widget.TextView h = new android.widget.TextView(this);
        h.setText("DEAD ZONE hit an error at boot.\nScreenshot this screen and send it in.\n\n");
        h.setTextColor(0xFFFF5A52);
        h.setTextSize(16);
        android.widget.TextView tv = new android.widget.TextView(this);
        String st = sw.toString();
        tv.setText(st.substring(0, Math.min(st.length(), 3500)));
        tv.setTextColor(0xFFFFD9D9);
        tv.setTextSize(11);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        l.addView(h);
        l.addView(tv);
        sv.addView(l);
        setContentView(sv);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (game != null) game.onActivityPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (game != null) game.onActivityResume();
    }

    @Override public void onBackPressed() {
        if (game != null && game.onBack()) return;
        super.onBackPressed();
    }
}
