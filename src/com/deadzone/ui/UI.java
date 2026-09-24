package com.deadzone.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.deadzone.core.Game;
import com.deadzone.data.MissionData;
import com.deadzone.data.SaveData;
import com.deadzone.data.WeaponData;

import java.util.ArrayList;

/**
 * Fully self-drawn UI. ONE custom View, zero child widgets.
 *
 * Every screen (menu, missions, weapons, survivor, inventory, settings,
 * intro, results, story, dead, pause, note, safehouse) is rendered with
 * Canvas calls and hit-tested manually. Motivated by a device class
 * (Moto G85 / Adreno 619, Android 14) that intermittently refuses to lay
 * out or draw nested widget subtrees — backgrounds render, children
 * vanish. Canvas text/rects are the one path that never failed there.
 *
 * Public API matches the old widget UI 1:1 (Game.java untouched).
 */
public class UI {
    public static final int BG = 0xF20B1118;
    public static final int ACCENT = 0xFF7CFC66;
    public static final int AMBER = 0xFFFFC857;
    public static final int RED = 0xFFFF5A52;
    public static final int DIM = 0xB0C8D2DC;
    private static final int SCRIM = 0xD80B1118;

    // screens
    private static final int S_NONE = -1, S_MENU = 0, S_MISSIONS = 1, S_WEAPONS = 2,
            S_SURVIVOR = 3, S_INVENTORY = 4, S_SETTINGS = 5, S_INTRO = 6, S_RESULTS = 7,
            S_STORY = 8, S_DEAD = 9, S_PAUSE = 10, S_NOTE = 11, S_SAFEHOUSE = 12;

    public final Game g;
    public View panels;
    public int menuContentW = -1, menuContentH = -1; // boot-log forensics

    private int screen = S_NONE;
    private final ArrayList<W> ws = new ArrayList<W>();
    private float scrollY, contentBottom;
    private int selMission = 0;
    private boolean delConfirm;
    private android.graphics.Bitmap menuArt;

    // dynamic per-screen state
    private String noteTitle = "", noteText = "";
    private boolean resWin;

    // widget model
    private static class W {
        static final int TEXT = 0, BUTTON = 1;
        int kind; String text; float x, y; int color; float sp; boolean bold; int align; // 0 left, 1 center
        RectF rect; Runnable action;
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();

    public UI(Game g) { this.g = g; }

    public void build(Context ctx) {
        panels = new CanvasPanel(ctx);
        panels.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        try {
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(
                    new java.io.File(com.deadzone.data.DataLib.assetDir(), "menu_bg.jpg").getAbsolutePath());
            if (bmp != null) {
                menuArt = bmp.copy(bmp.getConfig(), true);
                new Canvas(menuArt).drawColor(0x8F0B1118);
            }
        } catch (Exception ignored) { }
    }

    // ---------------- public API (identical to the old widget UI) ----------------

    public void hideAll() { showScreen(S_NONE); }
    public boolean anyVisible() { return screen != S_NONE; }

    public void showMenu() { delConfirm = false; showScreen(S_MENU); }
    public void showDead() { showScreen(S_DEAD); }
    public void showPause() { showScreen(S_PAUSE); }
    public void showMissions() { showScreen(S_MISSIONS); }
    public void showWeapons() { showScreen(S_WEAPONS); }
    public void showSurvivor() { showScreen(S_SURVIVOR); }
    public void showInventory() { showScreen(S_INVENTORY); }
    public void showSettings() { showScreen(S_SETTINGS); }
    public void showSafehouseUI() { showScreen(S_SAFEHOUSE); }

    public void openIntro(int mi) {
        MissionData m = g.data.missions[mi];
        g.introMission = m.id;
        selMission = mi;
        showScreen(S_INTRO);
    }

    public void showResults(boolean win) { resWin = win; showScreen(S_RESULTS); }

    public void showStory(int mi) { showScreen(S_STORY); }

    public void showNote(String title, String text) {
        noteTitle = title == null ? "" : title.toUpperCase();
        noteText = text == null ? "" : text;
        showScreen(S_NOTE);
    }

    private void showScreen(int s) {
        screen = s;
        scrollY = 0;
        delConfirm = false;
        relayout();
        com.deadzone.core.Boot.log("screen -> " + s + " widgets=" + ws.size());
    }

    private void backToBase() {
        if (g.state == Game.ST_SAFEHOUSE) g.safehouseUI();
        else g.toMenu();
    }

    // ---------------- the view ----------------

    private class CanvasPanel extends View {
        private float downX, downY, downScroll;
        private boolean panning;
        private boolean built;

        CanvasPanel(Context c) { super(c); }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            menuContentW = w; menuContentH = h;
            built = true;
            relayout();
            com.deadzone.core.Boot.log("CanvasPanel sized " + w + "x" + h);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            if (screen == S_NONE) return;
            // backdrops
            if (screen == S_MENU) {
                if (menuArt != null) {
                    float as = Math.max(w / menuArt.getWidth(), h / menuArt.getHeight());
                    c.drawBitmap(menuArt, (w - menuArt.getWidth() * as) / 2f,
                            (h - menuArt.getHeight() * as) / 2f, null);
                } else { c.drawColor(BG); }
            } else if (screen == S_SAFEHOUSE) {
                // transparent — GL safehouse scene shows through
            } else if (screen == S_PAUSE || screen == S_DEAD || screen == S_RESULTS || screen == S_NOTE) {
                c.drawColor(SCRIM);
            } else {
                c.drawColor(BG);
            }
            c.save();
            c.clipRect(0, 0, w, h);
            c.translate(0, -scrollY);
            for (W wd : ws) {
                if (wd.kind == W.TEXT) {
                    p.setTextSize(wd.sp * res());
                    p.setFakeBoldText(wd.bold);
                    p.setColor(wd.color);
                    float x = wd.align == 1 ? wd.x - p.measureText(wd.text) / 2f : wd.x;
                    c.drawText(wd.text, x, wd.y, p);
                } else {
                    rf.set(wd.rect);
                    pBg.setColor(wd.color & 0xFFFFFF | 0x33000000);
                    if ((wd.color & 0xFFFFFFFFL) == ACCENT) pBg.setColor(0x407CFC66);
                    c.drawRoundRect(rf, 10 * res(), 10 * res(), pBg);
                    p.setTextSize(wd.sp * res());
                    p.setFakeBoldText(true);
                    p.setColor(wd.color);
                    c.drawText(wd.text, rf.centerX() - p.measureText(wd.text) / 2f,
                            rf.centerY() + wd.sp * res() * 0.35f, p);
                }
            }
            c.restore();
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (screen == S_NONE) return false; // let touches reach the game HUD
                    downX = e.getX(); downY = e.getY();
                    downScroll = scrollY; panning = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dy = e.getY() - downY;
                    if (panning || Math.abs(dy) > res() * 14) {
                        panning = true;
                        scrollY = clamp(downScroll - dy, 0, Math.max(0, contentBottom - getHeight() + res() * 10));
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    if (!panning) {
                        float x = e.getX(), y = e.getY() + scrollY;
                        for (int i = ws.size() - 1; i >= 0; i--) {
                            W wd = ws.get(i);
                            if (wd.kind == W.BUTTON && wd.rect != null && wd.rect.contains(x, y)) {
                                g.audio.play("ui");
                                performClick();
                                Runnable r = wd.action;
                                if (r != null) r.run();
                                return true;
                            }
                        }
                    }
                    return true;
                }
                default:
                    return true;
            }
        }

        @Override public boolean performClick() { return super.performClick(); }
    }

    // ---------------- layout ----------------

    private void relayout() {
        ws.clear();
        scrollY = 0;
        contentBottom = 0;
        if (screen == S_NONE || !built() || panels.getWidth() <= 0) {
            if (panels != null) panels.invalidate();
            return;
        }
        float w = panels.getWidth(), h = panels.getHeight();
        switch (screen) {
            case S_MENU: buildMenu(w, h); break;
            case S_MISSIONS: buildMissions(w, h); break;
            case S_WEAPONS: buildWeapons(w, h); break;
            case S_SURVIVOR: buildSurvivor(w, h); break;
            case S_INVENTORY: buildInventory(w, h); break;
            case S_SETTINGS: buildSettings(w, h); break;
            case S_INTRO: buildIntro(w, h); break;
            case S_RESULTS: buildResults(w, h); break;
            case S_STORY: buildStory(w, h); break;
            case S_DEAD: buildDead(w, h); break;
            case S_PAUSE: buildPause(w, h); break;
            case S_NOTE: buildNote(w, h); break;
            case S_SAFEHOUSE: buildSafehouse(w, h); break;
        }
        panels.invalidate();
    }

    private boolean built() {
        return panels != null && panels.getWidth() > 0;
    }

    private float res() {
        return g.act.getResources().getDisplayMetrics().density;
    }

    private static float clamp(float v, float a, float b) { return v < a ? a : (v > b ? b : v); }

    private W txt(String s, float x, float y, int sp, int color, boolean bold, int align) {
        W wd = new W();
        wd.kind = W.TEXT; wd.text = s; wd.x = x; wd.y = y;
        wd.color = color; wd.sp = sp; wd.bold = bold; wd.align = align;
        ws.add(wd);
        float lh = sp * res() * 1.4f;
        if (y > contentBottom) contentBottom = y + lh;
        return wd;
    }

    private W btn(String label, float x, float y, float bw, float bh, int sp, int color, Runnable run) {
        W wd = new W();
        wd.kind = W.BUTTON; wd.text = label;
        wd.rect = new RectF(x, y, x + bw, y + bh);
        wd.color = color; wd.sp = sp; wd.action = run;
        ws.add(wd);
        if (y + bh > contentBottom) contentBottom = y + bh;
        return wd;
    }

    private String[] wrap(String s, int sp, float maxW) {
        p.setTextSize(sp * res());
        p.setFakeBoldText(false);
        ArrayList<String> out = new ArrayList<String>();
        for (String para : s.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String t = line.length() == 0 ? word : line + " " + word;
                if (p.measureText(t) > maxW && line.length() > 0) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else line = new StringBuilder(t);
            }
            out.add(line.toString());
        }
        return out.toArray(new String[0]);
    }

    private float wrapped(String s, float x, float y, int sp, int color, float maxW) {
        for (String ln : wrap(s, sp, maxW)) {
            txt(ln, x, y, sp, color, false, 0);
            y += sp * res() * 1.45f;
        }
        return y;
    }

    // ---------------- screens ----------------

    private void buildMenu(float w, float h) {
        txt("DEAD ZONE", w / 2f, h * 0.20f, 44, 0xFFE8FFE8, true, 1);
        String sub = "v" + g.versionName() + "  \u2022  " + g.data.missions.length
                + " missions  \u2022  " + g.data.enemies.size() + " infected types";
        txt(sub, w / 2f, h * 0.20f + 30 * res(), 13, DIM, false, 1);
        float bw = Math.min(w * 0.62f, 340 * res()), bh = 46 * res();
        float y0 = h * 0.46f, gap = 10 * res();
        String cont = g.hasSave ? "CONTINUE" : "NEW GAME";
        btn(cont, w / 2f - bw / 2f, y0, bw, bh, 20, ACCENT, new Runnable() {
            @Override public void run() { g.toSafehouse(); }});
        btn("MISSIONS", w / 2f - bw / 2f, y0 + (bh + gap), bw, bh, 20, 0xFFFFFFFF, new Runnable() {
            @Override public void run() { g.toSafehouse(); showMissions(); }});
        btn("WEAPONS", w / 2f - bw / 2f, y0 + 2 * (bh + gap), bw, bh, 20, 0xFFFFFFFF, new Runnable() {
            @Override public void run() { showWeapons(); }});
        btn("SURVIVOR", w / 2f - bw / 2f, y0 + 3 * (bh + gap), bw, bh, 20, 0xFFFFFFFF, new Runnable() {
            @Override public void run() { showSurvivor(); }});
        btn("INVENTORY", w / 2f - bw / 2f, y0 + 4 * (bh + gap), bw, bh, 20, 0xFFFFFFFF, new Runnable() {
            @Override public void run() { showInventory(); }});
        btn("SETTINGS", w / 2f - bw / 2f, y0 + 5 * (bh + gap), bw, bh, 20, 0xFFFFFFFF, new Runnable() {
            @Override public void run() { showSettings(); }});
        txt("100% OFFLINE  \u2022  NO ADS  \u2022  NO IAP", w / 2f, h - 14 * res(), 10, DIM, false, 1);
        contentBottom = y0 + 6 * (bh + gap);
    }

    private void buildMissions(float w, float h) {
        float pad = 20 * res();
        txt("SELECT MISSION", pad, 34 * res(), 18, 0xFFFFFFFF, true, 0);
        SaveData sv = g.save;
        // left: rows
        float lx = pad, lw = w * 0.46f, rowH = 44 * res(), y = 52 * res();
        float listTop = y;
        for (int i = 0; i < g.data.missions.length; i++) {
            MissionData m = g.data.missions[i];
            String status; int col;
            if (sv.done[m.id - 1]) { status = "CLEARED"; col = ACCENT; }
            else if (sv.unlocked(m.id - 1)) { status = "AVAILABLE"; col = AMBER; }
            else { status = "LOCKED"; col = 0xFF888899; }
            final int mi = i;
            W row = btn((mi == selMission ? "> " : "     ") + m.name + "  [" + status + "]",
                    lx, y, lw, rowH - 6 * res(), 14, col, new Runnable() {
                        @Override public void run() { selMission = mi; relayout(); }});
            row.color = mi == selMission ? ACCENT : 0x88FFFFFF;
            y += rowH;
        }
        // right: detail
        float rx = lx + lw + 24 * res(), rw = w - rx - pad;
        MissionData m = g.data.missions[selMission];
        float dy = 52 * res();
        dy = txtline("MISSION " + m.id + " \u2014 " + m.name, rx, dy, 17, 0xFFFFFFFF, true, rw);
        dy = txtline(m.loc, rx, dy + 4 * res(), 13, AMBER, false, rw);
        dy += 12 * res();
        if (!sv.unlocked(m.id - 1))
            dy = txtline("LOCKED \u2014 clear the previous mission first.", rx, dy, 13, 0xFF888899, false, rw);
        dy = wrapped(m.intro, rx, dy, 13, 0xFFD8E8F0, rw);
        dy += 12 * res();
        dy = txtline("OBJECTIVES:", rx, dy, 13, 0xB0C8FFE0, true, rw);
        for (MissionData.Obj o : m.objectives)
            dy = txtline("  \u2022 " + o.label, rx, dy, 13, 0xB0C8FFE0, false, rw);
        dy += 12 * res();
        String rw2 = "REWARDS:  " + m.rewardXp + " XP,  " + m.rewardScrap + " scrap";
        if (m.rewardMeds > 0) rw2 += ",  " + m.rewardMeds + " medkit";
        if (m.rewardParts > 0) rw2 += ",  " + m.rewardParts + " parts";
        if (m.rewardWeapon != null) rw2 += ",  NEW WEAPON: " + g.data.w(m.rewardWeapon).name;
        dy = txtline(rw2, rx, dy, 13, DIM, false, rw);
        // bottom buttons
        float bw2 = Math.min(rw, 240 * res());
        btn("DEPLOY", w - pad - bw2, h - 108 * res(), bw2, 52 * res(), 20, ACCENT, new Runnable() {
            @Override public void run() { g.openIntro(selMission); }});
        btn("BACK", w - pad - bw2, h - 48 * res(), bw2, 40 * res(), 15, DIM, new Runnable() {
            @Override public void run() { backToBase(); }});
        contentBottom = Math.max(contentBottom, listTop + g.data.missions.length * rowH);
    }

    private float txtline(String s, float x, float y, int sp, int color, boolean bold, float maxW) {
        txt(s, x, y, sp, color, bold, 0);
        return y + sp * res() * 1.4f;
    }

    private void buildWeapons(float w, float h) {
        float pad = 20 * res();
        txt("WEAPONS \u2014 upgrades cost SCRAP + PARTS", pad, 34 * res(), 16, 0xFFFFFFFF, true, 0);
        float y = 52 * res();
        SaveData sv = g.save;
        int maxUp = g.data.upgCost[0].length;
        for (WeaponData wd : g.data.weapons) {
            boolean own = sv.owned(wd.id);
            // card bg
            float cardH = own ? 128 * res() : 54 * res();
            W bgc = new W();
            bgc.kind = W.TEXT; bgc.text = ""; bgc.x = pad; bgc.y = y + cardH - 10 * res();
            bgc.color = 0x00000000; bgc.sp = 1; ws.add(bgc); // spacer keeps order sane
            // name row
            String nm = wd.name + (wd.id.equals(sv.equip) ? "  [EQUIPPED]" : "");
            y = txtline(nm, pad + 10 * res(), y + 22 * res(), 16, 0xFFFFFFFF, true, w - 2 * pad);
            if (!own) {
                y = txtline("LOCKED \u2014 clear " + weaponUnlockMission(wd.id), pad + 10 * res(), y, 12, 0xFF888899, false, w - 2 * pad);
                y += 12 * res();
                continue;
            }
            SaveData.WSave wsv = sv.wsave(wd.id);
            y = txtline("DMG " + Math.round(wd.dmg * (1 + g.data.upgPer[0] * wsv.lD))
                    + "   MAG " + (int) (wd.mag * (1 + g.data.upgPer[1] * wsv.lM))
                    + "   ACC " + String.format("%.1f", wd.spread * (1 - g.data.upgPer[2] * wsv.lA)) + "\u00b0"
                    + "   RELOAD " + String.format("%.1f", wd.reload) + "s"
                    + "   HEAD " + wd.headMult + "x", pad + 10 * res(), y, 12, DIM, false, w - 2 * pad);
            y += 8 * res();
            float bbh = 38 * res(), bw3 = (w - 2 * pad - 30 * res()) / 4f;
            float bx = pad + 10 * res();
            if (!wd.id.equals(sv.equip))
                btn("EQUIP", bx, y, bw3, bbh, 13, 0xFFFFFFFF, equip(wd.id));
            bx += bw3 + 10 * res();
            bx = upBtn(bx, y, bw3, bbh, wd, 0, "DMG", wsv.lD, maxUp);
            bx += bw3 + 10 * res();
            bx = upBtn(bx, y, bw3, bbh, wd, 1, "MAG", wsv.lM, maxUp);
            bx += bw3 + 10 * res();
            upBtn(bx, y, bw3, bbh, wd, 2, "ACC", wsv.lA, maxUp);
            y += bbh + 16 * res();
        }
        btn("BACK", pad, y + 6 * res(), 160 * res(), 42 * res(), 15, DIM, new Runnable() {
            @Override public void run() { backToBase(); }});
        contentBottom = y + 60 * res();
    }

    private Runnable equip(final String id) {
        return new Runnable() { @Override public void run() { g.equipWeapon(id); showWeapons(); }};
    }

    private float upBtn(float x, float y, float bw, float bh, final WeaponData wd,
                        final int axis, String name, int cur, int max) {
        if (cur >= max) {
            txt(name + " MAX", x + 10 * res(), y + bh / 2f + 4 * res(), 12, 0xFF888899, false, 0);
            return x + bw;
        }
        int[] cost = g.data.upgCost[axis][cur];
        boolean can = g.save.scrap >= cost[0] && g.save.parts >= cost[1];
        String lbl = name + " +" + cost[0] + "s" + (cost[1] > 0 ? " +" + cost[1] + "p" : "");
        btn(lbl, x, y, bw, bh, 12, can ? ACCENT : 0xFF888899, new Runnable() {
            @Override public void run() { g.upgradeWeapon(wd.id, axis); showWeapons(); }});
        return x + bw;
    }

    private String weaponUnlockMission(String id) {
        for (MissionData m : g.data.missions)
            if (id.equals(m.rewardWeapon)) return "Mission " + m.id;
        return "a mission";
    }

    private void buildSurvivor(float w, float h) {
        float pad = 20 * res();
        SaveData sv = g.save;
        txt("SURVIVOR   \u2014   Level " + sv.level + "   (" + sv.xp + "/" + sv.xpNext() + " XP)",
                pad, 34 * res(), 17, 0xFFFFFFFF, true, 0);
        txt("SCRAP: " + sv.scrap + "   \u2022   used to buy stat ranks", pad, 58 * res(), 13, DIM, false, 0);
        float y = 80 * res();
        y = statRow(y, w, 0, "MAX HEALTH", "+20 HP per rank", sv.stH);
        y = statRow(y, w, 1, "MOVE SPEED", "+4% per rank", sv.stSp);
        y = statRow(y, w, 2, "RELOAD SPEED", "+6% per rank", sv.stR);
        y = statRow(y, w, 3, "DAMAGE", "+4% per rank", sv.stD);
        btn("BACK", pad, y + 10 * res(), 160 * res(), 42 * res(), 15, DIM, new Runnable() {
            @Override public void run() { backToBase(); }});
        contentBottom = y + 60 * res();
    }

    private float statRow(float y, float w, final int which, String name, String eff, int cur) {
        float pad = 20 * res();
        int max = SaveData.statMax(which);
        StringBuilder pips = new StringBuilder(name + "  ");
        for (int i = 0; i < max; i++) pips.append(i < cur ? "\u25a0" : "\u25a1");
        txt(pips.toString(), pad + 10 * res(), y + 26 * res(), 14, 0xFFFFFFFF, false, 0);
        txt(eff, w - pad - 260 * res(), y + 26 * res(), 12, DIM, false, 0);
        if (cur >= max) {
            txt("MAX", w - pad - 120 * res(), y + 26 * res(), 13, 0xFF888899, false, 0);
        } else {
            final int cost = g.save.statCost(cur);
            boolean can = g.save.scrap >= cost;
            btn("+ (" + cost + "s)", w - pad - 120 * res(), y + 4 * res(), 120 * res(), 40 * res(),
                    13, can ? ACCENT : 0xFF888899, new Runnable() {
                        @Override public void run() { g.upgradeStat(which); showSurvivor(); }});
        }
        return y + 54 * res();
    }

    private void buildInventory(float w, float h) {
        float pad = 20 * res();
        txt("INVENTORY", pad, 34 * res(), 18, 0xFFFFFFFF, true, 0);
        float y = 56 * res();
        SaveData sv = g.save;
        y = kv(y, w, "5.56 ammo", sv.ammo[0] + " rounds");
        y = kv(y, w, "9mm ammo", sv.ammo[1] + " rounds");
        y = kv(y, w, "12g shells", sv.ammo[2] + " shells");
        y = kv(y, w, ".338 ammo", sv.ammo[3] + " rounds");
        y = kv(y, w, "Scrap", "" + sv.scrap);
        y = kv(y, w, "Medkits", "" + sv.meds);
        y = kv(y, w, "Weapon parts", "" + sv.parts);
        y += 10 * res();
        for (WeaponData wd : g.data.weapons)
            y = kv(y, w, wd.name, sv.owned(wd.id) ? "owned" : "not owned");
        btn("BACK", pad, y + 10 * res(), 160 * res(), 42 * res(), 15, DIM, new Runnable() {
            @Override public void run() { backToBase(); }});
        contentBottom = y + 60 * res();
    }

    private float kv(float y, float w, String k, String v) {
        txt(k, 32 * res(), y + 20 * res(), 14, 0xFFD8E8F0, false, 0);
        txt(v, w - 32 * res(), y + 20 * res(), 14, ACCENT, false, 1);
        return y + 36 * res();
    }

    private void buildSettings(float w, float h) {
        float pad = 24 * res();
        SaveData sv = g.save;
        txt("SETTINGS", pad, 36 * res(), 18, 0xFFFFFFFF, true, 0);
        float y = 64 * res();
        txt("CAMERA SENSITIVITY  x" + String.format("%.1f", sv.sens), pad, y, 14, DIM, false, 0);
        float bs = 44 * res();
        btn("\u2013", pad, y + 10 * res(), bs, bs, 18, 0xFFFFFFFF, new Runnable() {
            @Override public void run() {
                g.save.sens = Math.max(0.3f, g.save.sens - 0.1f); g.save.save(g.act); showSettings(); }});
        btn("+", pad + bs + 10 * res(), y + 10 * res(), bs, bs, 18, 0xFFFFFFFF, new Runnable() {
            @Override public void run() {
                g.save.sens = Math.min(2.0f, g.save.sens + 0.1f); g.save.save(g.act); showSettings(); }});
        y += bs + 26 * res();
        txt("GRAPHICS", pad, y, 14, DIM, false, 0);
        final String[] qnames = {"LOW", "MEDIUM", "HIGH"};
        float qw = 110 * res();
        for (int i = 0; i < 3; i++) {
            final int q = i;
            btn(qnames[i], pad + i * (qw + 10 * res()), y + 10 * res(), qw, bs, 13,
                    g.save.quality == i ? ACCENT : DIM, new Runnable() {
                        @Override public void run() { g.setQuality(q); showSettings(); }});
        }
        y += bs + 26 * res();
        txt("VOLUME  " + Math.round(g.save.vol * 100) + "%", pad, y, 14, DIM, false, 0);
        btn("\u2013", pad, y + 10 * res(), bs, bs, 18, 0xFFFFFFFF, new Runnable() {
            @Override public void run() {
                g.save.vol = Math.max(0f, Math.round(g.save.vol * 20 - 1) / 20f - 0.05f);
                g.audio.setVol(g.save.vol); g.save.save(g.act); showSettings(); }});
        btn("+", pad + bs + 10 * res(), y + 10 * res(), bs, bs, 18, 0xFFFFFFFF, new Runnable() {
            @Override public void run() {
                g.save.vol = Math.min(1f, Math.round(g.save.vol * 20 + 1) / 20f + 0.05f);
                g.audio.setVol(g.save.vol); g.save.save(g.act); showSettings(); }});
        y += bs + 30 * res();
        btn(delConfirm ? "TAP AGAIN TO CONFIRM DELETE" : "DELETE SAVE (START OVER)",
                pad, y, 320 * res(), 44 * res(), 13, RED, new Runnable() {
                    @Override public void run() {
                        if (delConfirm) { g.newGame(); g.toMenu(); }
                        else { delConfirm = true; relayout(); }
                    }});
        btn("BACK", pad, y + 56 * res(), 160 * res(), 42 * res(), 15, DIM, new Runnable() {
            @Override public void run() { backToBase(); }});
        contentBottom = y + 110 * res();
    }

    private void buildIntro(float w, float h) {
        float pad = 24 * res();
        MissionData m = g.data.missions[selMission];
        float maxW = w - 2 * pad;
        float y = 40 * res();
        y = txtline("MISSION " + m.id + " \u2014 " + m.name, pad, y, 26, ACCENT, true, maxW);
        y = txtline(m.loc, pad, y + 4 * res(), 14, AMBER, false, maxW);
        y += 12 * res();
        y = wrapped(m.intro, pad, y, 14, 0xFFD8E8F0, maxW);
        y += 14 * res();
        for (MissionData.Obj o : m.objectives)
            y = txtline("\u2022  " + o.label, pad, y, 13, 0xB0C8FFE0, false, maxW);
        float bw = 200 * res();
        btn("DEPLOY", w - pad - bw, h - 64 * res(), bw, 50 * res(), 20, ACCENT, new Runnable() {
            @Override public void run() { g.startMission(g.introMission); }});
        btn("BACK", pad, h - 60 * res(), 160 * res(), 44 * res(), 15, DIM, new Runnable() {
            @Override public void run() { backToBase(); showMissions(); }});
        contentBottom = y + 20 * res();
    }

    private void buildResults(float w, float h) {
        float pad = 24 * res();
        txt(resWin ? "MISSION COMPLETE" : "MISSION FAILED", w / 2f, 90 * res(), 34,
                resWin ? ACCENT : RED, true, 1);
        float y = 130 * res();
        y = wrapped(g.lastResultsText(), pad, y, 14, 0xFFD8E8F0, w - 2 * pad);
        float bw = 260 * res();
        btn("CONTINUE", w / 2f - bw / 2f, Math.min(y + 24 * res(), h - 80 * res()), bw, 52 * res(),
                20, ACCENT, new Runnable() {
                    @Override public void run() { g.afterResults(); }});
        contentBottom = y + 100 * res();
    }

    private void buildStory(float w, float h) {
        float pad = 24 * res();
        txt("FIELD REPORT", pad, 44 * res(), 22, AMBER, true, 0);
        float y = 76 * res();
        MissionData m = g.data.missions[g.introMission - 1];
        y = wrapped(m.story, pad, y, 15, 0xFFD8E8F0, w - 2 * pad);
        float bw = 260 * res();
        btn("CONTINUE", w / 2f - bw / 2f, Math.min(y + 24 * res(), h - 80 * res()), bw, 52 * res(),
                20, ACCENT, new Runnable() {
                    @Override public void run() { g.afterStory(); }});
        contentBottom = y + 100 * res();
    }

    private void buildDead(float w, float h) {
        txt("YOU DIED", w / 2f, h * 0.30f, 40, RED, true, 1);
        txt("The city keeps moving. Regroup and try again.", w / 2f, h * 0.30f + 34 * res(), 14, DIM, false, 1);
        float bw = 280 * res(), y = h * 0.52f;
        btn("RETRY MISSION", w / 2f - bw / 2f, y, bw, 50 * res(), 18, ACCENT, new Runnable() {
            @Override public void run() { g.retryMission(); }});
        btn("ABANDON", w / 2f - bw / 2f, y + 60 * res(), bw, 44 * res(), 16, DIM, new Runnable() {
            @Override public void run() { g.toSafehouse(); }});
        contentBottom = y + 120 * res();
    }

    private void buildPause(float w, float h) {
        txt("PAUSED", w / 2f, h * 0.24f, 28, 0xFFFFFFFF, true, 1);
        float bw = 320 * res(), y = h * 0.38f;
        btn("RESUME", w / 2f - bw / 2f, y, bw, 50 * res(), 20, ACCENT, new Runnable() {
            @Override public void run() { g.togglePause(); }});
        btn("RESTART MISSION", w / 2f - bw / 2f, y + 62 * res(), bw, 44 * res(), 16, 0xFFFFFFFF, new Runnable() {
            @Override public void run() { g.retryMission(); }});
        btn("ABANDON MISSION", w / 2f - bw / 2f, y + 116 * res(), bw, 44 * res(), 16, DIM, new Runnable() {
            @Override public void run() { g.toSafehouse(); }});
        contentBottom = y + 170 * res();
    }

    private void buildNote(float w, float h) {
        float pad = 24 * res();
        txt(noteTitle, pad, 48 * res(), 18, AMBER, true, 0);
        float y = 80 * res();
        y = wrapped(noteText, pad, y, 15, 0xFFD8E8F0, w - 2 * pad);
        float bw = 260 * res();
        btn("POCKET IT", w / 2f - bw / 2f, Math.min(y + 24 * res(), h - 80 * res()), bw, 50 * res(),
                18, ACCENT, new Runnable() {
                    @Override public void run() { g.closeNote(); }});
        contentBottom = y + 100 * res();
    }

    private void buildSafehouse(float w, float h) {
        int lv = g.save.baseLevel();
        // top-left base info chip
        float pad = 12 * res();
        txt("BASE LEVEL " + lv + " / 6", pad + 10 * res(), 34 * res(), 14, 0xFFB8E8C8, true, 0);
        txt(baseLine(lv), pad + 10 * res(), 56 * res(), 13, 0xCCC8FFE0, false, 0);
        // right column
        float bw = 190 * res(), bh = 42 * res(), x = w - bw - 14 * res();
        float y0 = h / 2f - (5 * (bh + 8 * res())) / 2f;
        txt("SAFEHOUSE", x, y0 - 10 * res(), 14, 0xFFB8E8C8, true, 0);
        btn("MISSIONS", x, y0, bw, bh, 16, ACCENT, new Runnable() {
            @Override public void run() { showMissions(); }});
        btn("WEAPONS", x, y0 + (bh + 8 * res()), bw, bh, 16, 0xFFFFFFFF, new Runnable() {
            @Override public void run() { showWeapons(); }});
        btn("SURVIVOR", x, y0 + 2 * (bh + 8 * res()), bw, bh, 16, 0xFFFFFFFF, new Runnable() {
            @Override public void run() { showSurvivor(); }});
        btn("INVENTORY", x, y0 + 3 * (bh + 8 * res()), bw, bh, 16, 0xFFFFFFFF, new Runnable() {
            @Override public void run() { showInventory(); }});
        btn("MAIN MENU", x, y0 + 4 * (bh + 8 * res()), bw, bh, 14, DIM, new Runnable() {
            @Override public void run() { g.toMenu(); }});
        contentBottom = y0 + 5 * (bh + 8 * res());
    }

    private String baseLine(int lv) {
        String[] lines = {
                "A fortified room. The generator is silent.",
                "Generator online. Lights hum in the dark.",
                "Plants on the windowsill. Someone is still here.",
                "Weapon rack installed. Kabir approves.",
                "Operation board up \u2014 the road north is mapped.",
                "The safehouse feels like home now.",
        };
        return lines[Math.min(lines.length - 1, lv - 1)];
    }
}
