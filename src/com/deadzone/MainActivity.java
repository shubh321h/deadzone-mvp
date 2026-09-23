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
        game = new Game(this);
        setContentView(game.buildUi());
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
