package com.deadzone.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.deadzone.core.Game;
import com.deadzone.input.HudButton;
import com.deadzone.input.HudView;
import com.deadzone.input.JoystickView;

/** Mission HUD: health, ammo, weapon, objectives, boss bar, prompts,
 *  and the touch controls (joystick + action buttons). */
public class Hud {
    public final HudView view;
    private final Game g;

    private final TextView objText, ammoText, weaponText, promptText, waveText, toastText;
    private final BarView health, bossBar;
    private final TextView bossLabel;
    private final HudButton fire, aim, reload, swap, sprint, crouch, interact, pause;
    public final JoystickView stick;
    private final Crosshair cross;
    private String lastObj = "", lastAmmo = "", lastWeapon = "", lastPrompt = "";
    private float toastT;

    public Hud(Game g, Context ctx) {
        this.g = g;
        view = new HudView(ctx, g);

        int dp = (int) (ctx.getResources().getDisplayMetrics().density);

        int W = ctx.getResources().getDisplayMetrics().widthPixels;
        int H = ctx.getResources().getDisplayMetrics().heightPixels;

        // objectives (top center)
        objText = tv(ctx, 12, 0xCCC8FFE0);
        objText.setGravity(Gravity.CENTER);
        objText.setMaxLines(3);
        objText.setWidth((int) (W * 0.46f));
        view.addView(objText, lp(0, 0, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 6 * dp, 0, 0));

        // health (top left)
        health = new BarView(ctx, 0xFF661111, 0xFFE33B3B);
        view.addView(health, lp(150 * dp, 16 * dp, Gravity.TOP | Gravity.START, 12 * dp, 12 * dp, 0, 0));
        bossBar = new BarView(ctx, 0xFF331111, 0xFFD92626);
        view.addView(bossBar, lp((int) (W * 0.42f), 18 * dp, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 44 * dp, 0, 0));
        bossLabel = tv(ctx, 11, 0xFFFFCC88);
        bossLabel.setText("THE COLOSSUS");
        bossLabel.setTypeface(Typeface.DEFAULT_BOLD);
        view.addView(bossLabel, lp(0, 0, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 26 * dp, 0, 0));

        // ammo + weapon (bottom right, above fire)
        ammoText = tv(ctx, 30, 0xFFFFFFFF);
        ammoText.setTypeface(Typeface.DEFAULT_BOLD);
        view.addView(ammoText, lp(0, 0, Gravity.BOTTOM | Gravity.END, 0, 0, 24 * dp, 150 * dp));
        weaponText = tv(ctx, 12, 0xAA7CFC66);
        view.addView(weaponText, lp(0, 0, Gravity.BOTTOM | Gravity.END, 0, 0, 170 * dp, 196 * dp));

        // interact prompt (center-low)
        promptText = tv(ctx, 15, 0xFFFFE08A);
        promptText.setTypeface(Typeface.DEFAULT_BOLD);
        promptText.setVisibility(View.GONE);
        view.addView(promptText, lp(0, 0, Gravity.CENTER_HORIZONTAL, 0, 0, 0, (int) (H * 0.42f)));

        // wave banner
        waveText = tv(ctx, 40, 0xFF7CFC66);
        waveText.setTypeface(Typeface.DEFAULT_BOLD);
        waveText.setVisibility(View.GONE);
        view.addView(waveText, lp(0, 0, Gravity.CENTER, 0, -40 * dp, 0, 0));

        // toast (top left, under health)
        toastText = tv(ctx, 13, 0xCCFFE9A8);
        toastText.setVisibility(View.GONE);
        view.addView(toastText, lp(170 * dp, 34 * dp, Gravity.TOP | Gravity.START, 12 * dp, 52 * dp, 0, 0));

        // joystick
        stick = new JoystickView(ctx);
        view.addView(stick, lp(176 * dp, 176 * dp, Gravity.BOTTOM | Gravity.START, 18 * dp, 0, 0, 20 * dp));

        // buttons
        fire = btn(ctx, "FIRE", 112 * dp, true);
        view.addView(fire, lp(0, 0, Gravity.BOTTOM | Gravity.END, 0, 0, 16 * dp, 18 * dp));
        aim = btn(ctx, "AIM", 88 * dp, true);
        view.addView(aim, lp(0, 0, Gravity.BOTTOM | Gravity.END, 0, 0, 140 * dp, 30 * dp));
        reload = btn(ctx, "RLD", 76 * dp, false);
        view.addView(reload, lp(0, 0, Gravity.BOTTOM | Gravity.END, 0, 0, 40 * dp, 150 * dp));
        swap = btn(ctx, "SWP", 76 * dp, false);
        view.addView(swap, lp(0, 0, Gravity.BOTTOM | Gravity.END, 0, 0, 132 * dp, 140 * dp));
        sprint = btn(ctx, "RUN", 82 * dp, true);
        view.addView(sprint, lp(0, 0, Gravity.BOTTOM | Gravity.START, 200 * dp, 0, 0, 30 * dp));
        crouch = btn(ctx, "CRCH", 76 * dp, true);
        view.addView(crouch, lp(0, 0, Gravity.BOTTOM | Gravity.START, 206 * dp, 0, 0, 124 * dp));
        interact = btn(ctx, "USE", 88 * dp, false);
        interact.setVisibility(View.GONE);
        view.addView(interact, lp(0, 0, Gravity.BOTTOM | Gravity.END, 0, 0, 238 * dp, 80 * dp));
        pause = btn(ctx, "II", 58 * dp, false);
        view.addView(pause, lp(0, 0, Gravity.TOP | Gravity.END, 0, 10 * dp, 10 * dp, 0));

        // crosshair
        cross = new Crosshair(ctx);
        view.addView(cross, lp(100 * dp, 100 * dp, Gravity.CENTER, 0, 0, 0, 0));
    }

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

    private TextView tv(Context ctx, int sp, int color) {
        TextView t = new TextView(ctx);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setShadowLayer(3f, 0, 1f, 0xAA000000);
        return t;
    }

    private FrameLayout.LayoutParams lp(int w, int h, int grav, int l, int t, int r, int b) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h, grav);
        p.leftMargin = l;
        p.topMargin = t;
        p.rightMargin = r;
        p.bottomMargin = b;
        return p;
    }

    public void show() {
        view.setVisibility(View.VISIBLE);
    }

    public void hide() {
        view.setVisibility(View.GONE);
    }

    public void setToast(String s) {
        toastText.setText(s);
        toastText.setVisibility(View.VISIBLE);
        toastT = 3.2f;
    }

    public void update(Game g) {
        cross.invalidate();
        float hp = g.player.hp;
        health.set(0, 1, hp / g.player.maxHp);
        // ammo
        int mag = g.player.mags == null ? 0 : g.player.mags[g.player.cur];
        int res = g.player.resAmmo(g);
        String a = mag + "  /  " + res;
        if (!a.equals(lastAmmo)) {
            lastAmmo = a;
            ammoText.setText(a);
            if (mag == 0 && res == 0) ammoText.setTextColor(0xFFFF6655);
            else if (mag == 0) ammoText.setTextColor(0xFFE8B84A);
            else ammoText.setTextColor(0xFFFFFFFF);
        }
        String wn = g.data.weapons[g.player.cur].name;
        if (!wn.equals(lastWeapon)) {
            lastWeapon = wn;
            weaponText.setText(wn + (g.player.reloadT > 0 ? "  RELOADING…" : ""));
        } else if (g.player.reloadT > 0) {
            weaponText.setText(wn + "  RELOADING…");
        } else if (weaponText.getText().toString().contains("RELOADING")) {
            weaponText.setText(wn);
        }
        String obj = g.mission.objLine();
        if (!obj.equals(lastObj)) {
            lastObj = obj;
            objText.setText(obj);
        }
        // interact prompt
        boolean showInteract = false;
        String prompt = "";
        if (g.state == Game.ST_MISSION) {
            com.deadzone.gl.Vec3 pp = g.player.pos;
            for (com.deadzone.systems.MissionMgr.InteractPoint p : g.mission.pts) {
                if (p.used) continue;
                if (p.gate != null && p.gate.equals("boss") && !g.mission.bossDead) continue;
                if (pp.dist(p.pos) < 3.2f) { showInteract = true; prompt = "USE — " + p.label; break; }
            }
            if (!showInteract && pp.dist(g.mission.extract) < 4.5f) {
                showInteract = true;
                prompt = g.mission.allDone() ? "USE — EXTRACT" : "EXTRACTION LOCKED";
            }
        }
        interact.setVisibility(showInteract ? View.VISIBLE : View.GONE);
        if (!prompt.equals(lastPrompt)) {
            lastPrompt = prompt;
            promptText.setText(prompt);
            promptText.setVisibility(showInteract ? View.VISIBLE : View.GONE);
        }
        // wave banner
        if (g.mission.bannerT > 0 && g.mission.banner != null) {
            waveText.setVisibility(View.VISIBLE);
            waveText.setText(g.mission.banner);
            waveText.setAlpha(g.mission.bannerT / 3f);
        } else waveText.setVisibility(View.GONE);
        // toast
        if (toastT > 0) {
            toastT -= 0.016f;
            if (toastT <= 0) toastText.setVisibility(View.GONE);
        }
        // boss bar
        boolean boss = false;
        float bfrac = 0;
        for (com.deadzone.entities.Zombie z : g.zombiesList) {
            if (z.alive && z.boss) { boss = true; bfrac = z.hp / z.maxHp; break; }
        }
        bossBar.setVisibility(boss ? View.VISIBLE : View.GONE);
        bossLabel.setVisibility(boss ? View.VISIBLE : View.GONE);
        if (boss) bossBar.set(0, 1, bfrac);
    }

    // ---------------- custom widgets ----------------

    static class BarView extends View {
        private final Paint bg = new Paint(), fg = new Paint();
        private float frac;
        private final int bgc, fgc;
        BarView(Context c, int bgc, int fgc) {
            super(c);
            this.bgc = bgc; this.fgc = fgc;
            setWillNotDraw(false);
        }
        void set(float min, float max, float frac) {
            frac = Math.max(0f, Math.min(1f, frac));
            invalidate();
        }
        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            bg.setColor(bgc);
            c.drawRoundRect(0, 0, w, h, h / 2f, h / 2f, bg);
            fg.setColor(fgc);
            c.drawRoundRect(1, 1, Math.max(h, w * frac) - 1, h - 1, h / 2f, h / 2f, fg);
        }
    }

    static class Crosshair extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Crosshair(Context c) {
            super(c);
            setWillNotDraw(false);
        }
        @Override protected void onDraw(Canvas c) {
            Game g = ((HudView) getParent()).gameRef();
            if (g == null || g.state != Game.ST_MISSION) return;
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float k = 1f - g.player.adsT * 0.45f;
            p.setColor(0xCCFFFFFF);
            p.setStrokeWidth(2f);
            float r = 9f * k;
            c.drawCircle(cx, cy, r, p);
            p.setColor(0xFF7CFC66);
            p.setStrokeWidth(2.5f);
            float t = 4f * k;
            c.drawLine(cx - r - t, cy, cx - r + 2, cy, p);
            c.drawLine(cx + r + t, cy, cx + r - 2, cy, p);
            c.drawLine(cx, cy - r - t, cx, cy - r + 2, p);
            c.drawLine(cx, cy + r + t, cx, cy + r - 2, p);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy, 1.8f, p);
        }
    }
}
