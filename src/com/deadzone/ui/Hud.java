package com.deadzone.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;
import android.widget.FrameLayout;

import com.deadzone.core.Game;
import com.deadzone.input.HudButton;
import com.deadzone.input.HudView;
import com.deadzone.input.JoystickView;

/**
 * Mission HUD: health, ammo, weapon, objectives, boss bar, prompts, and the
 * touch controls (joystick + action buttons).
 *
 * THREADING: update(Game) runs on the GL thread and ONLY writes volatile
 * fields. All rendering happens in HudView's onDraw (UI thread) via Canvas.
 * No TextView/visibility mutation exists any more — a device was observed
 * throwing CalledFromWrongThreadException from GL-thread setText, and this
 * path is also immune to that device's widget-drawing quirk.
 */
public class Hud {
    public final HudView view;
    private final Game g;

    private final HudButton fire, aim, reload, swap, sprint, crouch, interact, pause;
    public final JoystickView stick;
    private float toastT;

    // ---- volatile state: written on GL thread, read on UI thread ----
    private volatile String objStr = "", ammoStr = "", weaponStr = "", promptStr = "";
    private volatile int ammoColor = 0xFFFFFFFF;
    private volatile float hpFrac = 1f;
    private volatile boolean bossOn;
    private volatile float bossFrac;
    private volatile String bossName = "THE COLOSSUS";
    private volatile String waveStr = "";
    private volatile float waveA;
    private volatile String toastStr = "";

    public Hud(Game g, Context ctx) {
        this.g = g;
        view = new HudView(ctx, g);
        view.hud = this;
        view.setWillNotDraw(false);

        int dp = (int) (ctx.getResources().getDisplayMetrics().density);

        // joystick
        stick = new JoystickView(ctx);
        view.addView(stick, lp(176 * dp, 176 * dp, Gravity_BOTTOM_START, 18 * dp, 0, 0, 20 * dp));

        // buttons
        fire = btn(ctx, "FIRE", 112 * dp, true);
        view.addView(fire, lp(0, 0, Gravity_BOTTOM_END, 0, 0, 16 * dp, 18 * dp));
        aim = btn(ctx, "AIM", 88 * dp, true);
        view.addView(aim, lp(0, 0, Gravity_BOTTOM_END, 0, 0, 140 * dp, 30 * dp));
        reload = btn(ctx, "RLD", 76 * dp, false);
        view.addView(reload, lp(0, 0, Gravity_BOTTOM_END, 0, 0, 40 * dp, 150 * dp));
        swap = btn(ctx, "SWP", 76 * dp, false);
        view.addView(swap, lp(0, 0, Gravity_BOTTOM_END, 0, 0, 132 * dp, 140 * dp));
        sprint = btn(ctx, "RUN", 82 * dp, true);
        view.addView(sprint, lp(0, 0, Gravity_BOTTOM_START, 200 * dp, 0, 0, 30 * dp));
        crouch = btn(ctx, "CRCH", 76 * dp, true);
        view.addView(crouch, lp(0, 0, Gravity_BOTTOM_START, 206 * dp, 0, 0, 124 * dp));
        interact = btn(ctx, "USE", 88 * dp, false);
        interact.enabledFlag = false; // enabled per-frame from update()
        view.addView(interact, lp(0, 0, Gravity_BOTTOM_END, 0, 0, 238 * dp, 80 * dp));
        pause = btn(ctx, "II", 58 * dp, false);
        view.addView(pause, lp(0, 0, Gravity_TOP_END, 0, 10 * dp, 10 * dp, 0));
    }

    private static final int Gravity_BOTTOM_END = android.view.Gravity.BOTTOM | android.view.Gravity.END;
    private static final int Gravity_BOTTOM_START = android.view.Gravity.BOTTOM | android.view.Gravity.START;
    private static final int Gravity_TOP_END = android.view.Gravity.TOP | android.view.Gravity.END;

    private HudButton btn(Context ctx, String label, int size, boolean hold) {
        HudButton b = new HudButton(ctx);
        b.setLabel(label);
        b.hold = hold;
        b.listener = new HudButton.Listener() {
            @Override public void onDown(View v) {
                if (v == fire) g.input.state.fire = true;
                if (v == aim) g.input.state.aim = true;
                if (v == sprint) g.input.state.sprint = true;
                if (v == crouch) g.input.state.crouch = true;
            }
            @Override public void onUp(View v) {
                if (v == fire) g.input.state.fire = false;
                if (v == aim) g.input.state.aim = false;
                if (v == sprint) g.input.state.sprint = false;
                if (v == crouch) g.input.state.crouch = false;
            }
            @Override public void onClick(View v) {
                if (v == reload) g.input.state.reloadTap = true;
                if (v == swap) g.input.state.swapTap = true;
                if (v == interact) g.input.state.interactTap = true;
                if (v == pause) g.togglePause();
            }
        };
        FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(size, size);
        b.setLayoutParams(l);
        return b;
    }

    private FrameLayout.LayoutParams lp(int w, int h, int grav, int l, int t, int r, int b) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h, grav);
        p.leftMargin = l;
        p.topMargin = t;
        p.rightMargin = r;
        p.bottomMargin = b;
        return p;
    }

    public void show() { view.setVisibility(View.VISIBLE); }
    public void hide() { view.setVisibility(View.GONE); }

    public void setToast(String s) {
        toastStr = s == null ? "" : s;
        toastT = 3.2f;
    }

    /** GL thread: compute all HUD state into volatile fields. NO view calls. */
    public void update(Game g) {
        hpFrac = Math.max(0f, g.player.hp / g.player.maxHp);
        int mag = g.player.mags == null ? 0 : g.player.mags[g.player.cur];
        int res = g.player.resAmmo(g);
        ammoStr = mag + "  /  " + res;
        ammoColor = (mag == 0 && res == 0) ? 0xFFFF6655 : (mag == 0) ? 0xFFE8B84A : 0xFFFFFFFF;
        weaponStr = g.data.weapons[g.player.cur].name + (g.player.reloadT > 0 ? "  RELOADING\u2026" : "");
        objStr = g.mission.objLine();
        // interact prompt
        boolean showInteract = false;
        String prompt = "";
        if (g.state == Game.ST_MISSION) {
            com.deadzone.gl.Vec3 pp = g.player.pos;
            for (com.deadzone.systems.MissionMgr.InteractPoint p : g.mission.pts) {
                if (p.used) continue;
                if (p.gate != null && p.gate.equals("boss") && !g.mission.bossDead) continue;
                if (pp.dist(p.pos) < 3.2f) { showInteract = true; prompt = "USE \u2014 " + p.label; break; }
            }
            if (!showInteract && pp.dist(g.mission.extract) < 4.5f) {
                showInteract = true;
                prompt = g.mission.allDone() ? "USE \u2014 EXTRACT" : "EXTRACTION LOCKED";
            }
        }
        interact.enabledFlag = showInteract; // plain field; HudButton reads it on UI thread
        promptStr = prompt;
        // wave banner
        if (g.mission.bannerT > 0 && g.mission.banner != null) {
            waveStr = g.mission.banner;
            waveA = Math.min(1f, g.mission.bannerT / 3f);
        } else waveStr = "";
        // toast timer
        if (toastT > 0) {
            toastT -= 0.016f;
            if (toastT <= 0) toastStr = "";
        }
        // boss bar
        bossOn = false;
        for (com.deadzone.entities.Zombie z : g.zombiesList) {
            if (z.alive && z.boss) { bossOn = true; bossFrac = z.hp / z.maxHp; break; }
        }
    }

    /** UI thread (HudView.onDraw): render the informational layer. */
    public void drawLayer(Canvas c, int W, int H) {
        float d = g.act.getResources().getDisplayMetrics().density;
        p.setShadowLayer(4f, 0, 1.5f, 0xB0000000);
        // crosshair
        if (g.state == Game.ST_MISSION) {
            float cx = W / 2f, cy = H / 2f;
            float k = 1f - g.player.adsT * 0.45f;
            p.setColor(0xCCFFFFFF); p.setStrokeWidth(2f);
            float r = 9f * k;
            c.drawCircle(cx, cy, r, p);
            p.setColor(0xFF7CFC66); p.setStrokeWidth(2.5f);
            float t = 4f * k;
            c.drawLine(cx - r - t, cy, cx - r + 2, cy, p);
            c.drawLine(cx + r + t, cy, cx + r - 2, cy, p);
            c.drawLine(cx, cy - r - t, cx, cy - r + 2, p);
            c.drawLine(cx, cy + r + t, cx, cy + r - 2, p);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy, 1.8f, p);
            p.setStyle(Paint.Style.STROKE);
        }
        // health (top-left)
        float hw = 150 * d, hh = 16 * d, hx = 12 * d, hy = 12 * d;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF661111);
        c.drawRoundRect(hx, hy, hx + hw, hy + hh, hh / 2f, hh / 2f, p);
        p.setColor(0xFFE33B3B);
        float fw = Math.max(hh, (hw - 2) * hpFrac);
        if (hpFrac > 0.02f) c.drawRoundRect(hx + 1, hy + 1, hx + 1 + fw, hy + hh - 1, hh / 2f, hh / 2f, p);
        // boss bar (top center)
        if (bossOn) {
            float bw = W * 0.42f, bh = 18 * d, bx = (W - bw) / 2f, by = 44 * d;
            p.setColor(0xFFFFCC88); p.setFakeBoldText(true);
            p.setTextSize(11 * d);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(bossName, W / 2f, 26 * d + 10 * d, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF331111);
            c.drawRoundRect(bx, by, bx + bw, by + bh, bh / 2f, bh / 2f, p);
            p.setColor(0xFFD92626);
            float fw2 = Math.max(bh, (bw - 2) * Math.max(0f, bossFrac));
            if (bossFrac > 0.01f) c.drawRoundRect(bx + 1, by + 1, bx + 1 + fw2, by + bh - 1, bh / 2f, bh / 2f, p);
        }
        // objectives (top center, under boss bar area)
        if (objStr.length() > 0) {
            p.setColor(0xCCC8FFE0); p.setFakeBoldText(false);
            p.setTextSize(12 * d);
            p.setTextAlign(Paint.Align.CENTER);
            float y = bossOn ? 78 * d : 52 * d;
            for (String ln : objStr.split("\n")) {
                c.drawText(ln, W / 2f, y, p);
                y += 16 * d;
            }
        }
        // ammo (bottom right, above FIRE)
        p.setTextAlign(Paint.Align.RIGHT);
        p.setFakeBoldText(true);
        p.setTextSize(30 * d);
        p.setColor(ammoColor);
        c.drawText(ammoStr, W - 24 * d, H - 150 * d, p);
        p.setFakeBoldText(false);
        p.setTextSize(12 * d);
        p.setColor(0xAA7CFC66);
        c.drawText(weaponStr, W - 170 * d, H - 196 * d, p);
        // interact prompt (center-low)
        if (promptStr.length() > 0) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setFakeBoldText(true);
            p.setTextSize(15 * d);
            p.setColor(0xFFFFE08A);
            c.drawText(promptStr, W / 2f, H - H * 0.42f, p);
        }
        // wave banner (center)
        if (waveStr.length() > 0 && waveA > 0) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setFakeBoldText(true);
            p.setTextSize(40 * d);
            p.setColor(0xFF7CFC66);
            int a = (int) (255 * Math.min(1f, waveA));
            p.setColor((a << 24) | 0x7CFC66);
            c.drawText(waveStr, W / 2f, H / 2f - 40 * d, p);
        }
        // toast (top-left, under health)
        if (toastStr.length() > 0) {
            p.setTextAlign(Paint.Align.LEFT);
            p.setFakeBoldText(false);
            p.setTextSize(13 * d);
            p.setColor(0xCCFFE9A8);
            c.drawText(toastStr, 12 * d, 52 * d + 14 * d, p);
        }
        p.setTextAlign(Paint.Align.LEFT);
        p.clearShadowLayer();
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
}
