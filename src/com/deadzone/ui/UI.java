package com.deadzone.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.deadzone.core.Game;
import com.deadzone.data.MissionData;
import com.deadzone.data.SaveData;
import com.deadzone.data.WeaponData;

import java.util.ArrayList;
import java.util.List;

/** All non-HUD screens, built programmatically (mission/weapon lists are
 *  data-driven and rebuilt when opened so state is always current). */
public class UI {
    public static final int BG = 0xF20B1118;
    public static final int ACCENT = 0xFF7CFC66;
    public static final int AMBER = 0xFFFFC857;
    public static final int RED = 0xFFFF5A52;
    public static final int DIM = 0xB0C8D2DC;

    public final Game g;
    public FrameLayout panels;
    private View menu, missions, weapons, survivor, inventory, settings,
            intro, results, story, dead, pause, note;
    private int selMission = 0;
    private TextView missionDetail;
    private LinearLayout missionList, safeBtns;
    private TextView baseInfo;
    private final List<View> all = new ArrayList<View>();

    public UI(Game g) {
        this.g = g;
    }

    public void build(Context ctx) {
        panels = new FrameLayout(ctx);
        panels.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        menu = menuPanel(ctx);
        missions = missionsPanel(ctx);
        weapons = weaponsPanel(ctx);
        survivor = survivorPanel(ctx);
        inventory = inventoryPanel(ctx);
        settings = settingsPanel(ctx);
        intro = introPanel(ctx);
        results = resultsPanel(ctx);
        story = storyPanel(ctx);
        dead = deadPanel(ctx);
        pause = pausePanel(ctx);
        note = notePanel(ctx);
        safehousePanel = buildSafehousePanel();
        for (View v : new View[]{menu, missions, weapons, survivor, inventory, settings, intro, results, story, dead, pause, note, safehousePanel}) {
            v.setVisibility(View.GONE);
            panels.addView(v);
            all.add(v);
        }
    }

    // ---------------- helpers ----------------

    private FrameLayout panelBg() {
        FrameLayout f = new FrameLayout(g.act);
        f.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        f.setBackgroundColor(BG);
        return f;
    }

    private TextView tv(int sp, int color) {
        TextView t = new TextView(g.act);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private TextView btn(String text, int sp, int color, Runnable onClick) {
        TextView b = new TextView(g.act);
        b.setText(text);
        b.setTextSize(sp);
        b.setTextColor(color);
        b.setGravity(Gravity.CENTER);
        b.setAllCaps(true);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(24), dp(12), dp(24), dp(12));
        b.setBackgroundColor(0x33111A24);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                g.audio.play("ui");
                onClick.run();
            }
        });
        return b;
    }

    private TextView dim(String s, int sp) {
        TextView t = tv(sp, DIM);
        t.setText(s);
        return t;
    }

    private LinearLayout col(int padding) {
        LinearLayout l = new LinearLayout(g.act);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(padding, padding, padding, padding);
        l.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return l;
    }

    private int dp(int v) {
        return (int) (v * g.act.getResources().getDisplayMetrics().density);
    }

    public void hideAll() {
        for (View v : all) v.setVisibility(View.GONE);
    }

    public boolean anyVisible() {
        for (View v : all) if (v.getVisibility() == View.VISIBLE) return true;
        return false;
    }

    // ---------------- main menu ----------------

    private View menuPanel(Context ctx) {
        FrameLayout f = panelBg();
        LinearLayout c = col(dp(20));
        TextView title = tv(54, 0xFFE8FFE8);
        title.setText("DEAD ZONE");
        title.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(title);
        TextView sub = dim("MUMBAI  •  CHAPTER 1", 15);
        c.addView(sub);
        c.addView(spacer(dp(18)));
        String cont = g.hasSave ? "CONTINUE" : "NEW GAME";
        c.addView(btn(cont, 20, ACCENT, new Runnable() {
            @Override public void run() { g.toSafehouse(); }
        }));
        c.addView(btn("MISSIONS", 20, 0xFFFFFFFF, new Runnable() {
            @Override public void run() { g.toSafehouse(); showMissions(); }
        }));
        c.addView(btn("WEAPONS", 20, 0xFFFFFFFF, new Runnable() {
            @Override public void run() { showWeapons(); }
        }));
        c.addView(btn("SURVIVOR", 20, 0xFFFFFFFF, new Runnable() {
            @Override public void run() { showSurvivor(); }
        }));
        c.addView(btn("INVENTORY", 20, 0xFFFFFFFF, new Runnable() {
            @Override public void run() { showInventory(); }
        }));
        c.addView(btn("SETTINGS", 20, 0xFFFFFFFF, new Runnable() {
            @Override public void run() { showSettings(); }
        }));
        c.addView(spacer(dp(14)));
        TextView foot = dim("v0.1 MVP  •  100% OFFLINE  •  NO IN-APP PURCHASES", 11);
        c.addView(foot);
        f.addView(c);
        return f;
    }

    private View spacer(int h) {
        View v = new View(g.act);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(4), h));
        return v;
    }

    // ---------------- missions ----------------

    private View missionsPanel(Context ctx) {
        FrameLayout f = panelBg();
        LinearLayout row = new LinearLayout(g.act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        row.setPadding(dp(20), dp(16), dp(20), dp(16));

        // left: list
        LinearLayout left = new LinearLayout(g.act);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        TextView head = dim("— MUMBAI SECTOR —", 18);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        left.addView(head);
        left.addView(spacer(dp(8)));
        missionList = new LinearLayout(g.act);
        missionList.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(g.act);
        sv.addView(missionList);
        left.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        row.addView(left);

        // right: detail + deploy
        LinearLayout right = new LinearLayout(g.act);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        right.setPadding(dp(18), 0, 0, 0);
        missionDetail = tv(14, 0xFFD8E8F0);
        missionDetail.setLineSpacing(dp(4), 1f);
        right.addView(missionDetail, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView deploy = btn("DEPLOY", 22, ACCENT, new Runnable() {
            @Override public void run() { g.openIntro(selMission); }
        });
        right.addView(deploy);
        TextView back = btn("BACK", 16, DIM, new Runnable() {
            @Override public void run() { backToBase(); }
        });
        right.addView(back);
        row.addView(right);
        f.addView(row);
        return f;
    }

    public void showMissions() {
        missionList.removeAllViews();
        for (int i = 0; i < g.data.missions.length; i++) {
            final int mi = i;
            MissionData m = g.data.missions[i];
            SaveData sv = g.save;
            String status;
            int col;
            if (sv.done[m.id - 1]) { status = "CLEARED"; col = ACCENT; }
            else if (sv.unlocked(m.id - 1)) { status = "AVAILABLE"; col = AMBER; }
            else { status = "LOCKED"; col = 0x888899AA; }
            TextView t = tv(15, 0xFFFFFFFF);
            String star = (mi == selMission) ? "> " : "  ";
            t.setText(star + m.name + "   [" + status + "]");
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setPadding(dp(10), dp(10), dp(10), dp(10));
            t.setBackgroundColor(mi == selMission ? 0x447CFC66 : 0x22111A24);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    selMission = mi;
                    showMissions();
                }
            });
            missionList.addView(t);
        }
        refreshMissionDetail();
        show(missions);
    }

    private void refreshMissionDetail() {
        MissionData m = g.data.missions[selMission];
        SaveData sv = g.save;
        StringBuilder sb = new StringBuilder();
        sb.append("MISSION ").append(m.id).append(" — ").append(m.name).append("\n");
        sb.append(m.loc).append("\n\n");
        if (!sv.unlocked(m.id - 1)) {
            sb.append("LOCKED — clear the previous mission first.\n\n");
        }
        sb.append(m.intro).append("\n\nOBJECTIVES:\n");
        for (MissionData.Obj o : m.objectives)
            sb.append("  • ").append(o.label).append("\n");
        sb.append("\nREWARDS:  ").append(m.rewardXp).append(" XP,  ")
                .append(m.rewardScrap).append(" scrap");
        if (m.rewardMeds > 0) sb.append(",  ").append(m.rewardMeds).append(" medkit");
        if (m.rewardParts > 0) sb.append(",  ").append(m.rewardParts).append(" parts");
        if (m.rewardWeapon != null) sb.append(",  NEW WEAPON: ").append(g.data.w(m.rewardWeapon).name);
        missionDetail.setText(sb.toString());
    }

    // ---------------- weapons ----------------

    private View weaponsPanel(Context ctx) {
        FrameLayout f = panelBg();
        LinearLayout c = col(dp(20));
        TextView head = dim("WEAPONS — upgrades cost SCRAP + PARTS earned in missions", 15);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(head);
        c.addView(spacer(dp(10)));
        LinearLayout list = new LinearLayout(g.act);
        list.setOrientation(LinearLayout.VERTICAL);
        for (WeaponData w : g.data.weapons) {
            list.addView(weaponRow(w));
        }
        ScrollView sv = new ScrollView(g.act);
        sv.addView(list);
        c.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        c.addView(btn("BACK", 16, DIM, new Runnable() {
            @Override public void run() { backToBase(); }
        }));
        f.addView(c);
        return f;
    }

    private LinearLayout weaponRow(WeaponData w) {
        SaveData sv = g.save;
        boolean own = sv.owned(w.id);
        LinearLayout row = new LinearLayout(g.act);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackgroundColor(0x22111A24);
        LinearLayout top = new LinearLayout(g.act);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView name = tv(17, 0xFFFFFFFF);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setText(w.name + (w.id.equals(sv.equip) ? "  [EQUIPPED]" : ""));
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!own) {
            TextView lk = tv(13, 0x888899AA);
            lk.setText("LOCKED — clear " + weaponUnlockMission(w.id));
            top.addView(lk);
        } else {
            TextView eq = btn("EQUIP", 12, own ? DIM : ACCENT, new Runnable() {
                @Override public void run() { g.equipWeapon(w.id); showWeapons(); }
            });
            top.addView(eq);
        }
        row.addView(top);
        if (own) {
            int up = (int) (g.data.upgCost[0].length);
            TextView stats = tv(12, DIM);
            SaveData.WSave ws = sv.wsave(w.id);
            stats.setText(
                    "DMG " + Math.round(w.dmg * (1 + g.data.upgPer[0] * ws.lD)) +
                    "   MAG " + (int) (w.mag * (1 + g.data.upgPer[1] * ws.lM)) +
                    "   ACC " + String.format("%.1f", w.spread * (1 - g.data.upgPer[2] * ws.lA)) + "°" +
                    "   RELOAD " + String.format("%.1f", w.reload) + "s" +
                    "   HEAD " + w.headMult + "x");
            row.addView(stats);
            LinearLayout ups = new LinearLayout(g.act);
            ups.setOrientation(LinearLayout.HORIZONTAL);
            ups.addView(weaponUpBtn(w, 0, "DMG", ws.lD, up));
            ups.addView(weaponUpBtn(w, 1, "MAG", ws.lM, up));
            ups.addView(weaponUpBtn(w, 2, "ACC", ws.lA, up));
            row.addView(ups);
        }
        return row;
    }

    private TextView weaponUpBtn(final WeaponData w, final int axis, final String name, int cur, int max) {
        TextView b;
        if (cur >= max) {
            b = dim(name + " MAX", 12);
            b.setPadding(dp(10), dp(8), dp(10), dp(8));
            b.setBackgroundColor(0x22334455);
        } else {
            int[] cost = g.data.upgCost[axis][cur];
            boolean can = g.save.scrap >= cost[0] && g.save.parts >= cost[1];
            b = tv(12, can ? ACCENT : 0x888899AA);
            b.setText(name + " +" + (cost[0]) + "s" + (cost[1] > 0 ? " +" + cost[1] + "p" : ""));
            b.setPadding(dp(10), dp(8), dp(10), dp(8));
            b.setBackgroundColor(0x22111A24);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    g.upgradeWeapon(w.id, axis);
                    showWeapons();
                }
            });
        }
        return b;
    }

    private String weaponUnlockMission(String id) {
        for (MissionData m : g.data.missions)
            if (id.equals(m.rewardWeapon)) return "Mission " + m.id;
        return "a mission";
    }

    // ---------------- survivor ----------------

    private View survivorPanel(Context ctx) {
        FrameLayout f = panelBg();
        LinearLayout c = col(dp(20));
        final SaveData sv = g.save;
        TextView head = tv(17, 0xFFFFFFFF);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setText("SURVIVOR   —   Level " + sv.level + "   (" + sv.xp + "/" + sv.xpNext() + " XP)");
        c.addView(head);
        TextView scrap = dim("SCRAP: " + sv.scrap + "   •   used to buy stat ranks", 13);
        c.addView(scrap);
        c.addView(spacer(dp(10)));
        c.addView(statRow(0, "MAX HEALTH", "+20 HP per rank", sv.stH));
        c.addView(statRow(1, "MOVE SPEED", "+4% per rank", sv.stSp));
        c.addView(statRow(2, "RELOAD SPEED", "+6% per rank", sv.stR));
        c.addView(statRow(3, "DAMAGE", "+4% per rank", sv.stD));
        c.addView(spacer(dp(10)));
        c.addView(btn("BACK", 16, DIM, new Runnable() {
            @Override public void run() { backToBase(); }
        }));
        f.addView(c);
        return f;
    }

    private LinearLayout statRow(final int which, String name, String eff, final int cur) {
        final SaveData sv = g.save;
        int max = SaveData.statMax(which);
        LinearLayout row = new LinearLayout(g.act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.setBackgroundColor(0x22111A24);
        TextView n = tv(15, 0xFFFFFFFF);
        StringBuilder pips = new StringBuilder(name);
        for (int i = 0; i < max; i++) pips.append(i < cur ? " ■" : " □");
        n.setText(pips.toString());
        row.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView e = tv(12, DIM);
        e.setText(eff);
        row.addView(e);
        TextView b;
        if (cur >= max) {
            b = dim("MAX", 13);
            b.setPadding(dp(12), dp(8), dp(12), dp(8));
        } else {
            final int cost = sv.statCost(cur);
            boolean can = sv.scrap >= cost;
            b = tv(13, can ? ACCENT : 0x888899AA);
            b.setText("+ (" + cost + "s)");
            b.setPadding(dp(12), dp(8), dp(12), dp(8));
            b.setBackgroundColor(0x22111A24);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { g.upgradeStat(which); showSurvivor(); }
            });
        }
        row.addView(b);
        return row;
    }

    // ---------------- inventory ----------------

    private LinearLayout inventoryList;

    private View inventoryPanel(Context ctx) {
        FrameLayout f = panelBg();
        LinearLayout c = col(dp(20));
        TextView head = dim("INVENTORY", 18);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(head);
        c.addView(spacer(dp(8)));
        inventoryList = new LinearLayout(g.act);
        inventoryList.setOrientation(LinearLayout.VERTICAL);
        c.addView(inventoryList);
        c.addView(btn("BACK", 16, DIM, new Runnable() {
            @Override public void run() { backToBase(); }
        }));
        f.addView(c);
        return f;
    }

    public void refreshInventory() {
        if (inventoryList == null) return;
        LinearLayout list = inventoryList;
        list.removeAllViews();
        SaveData sv = g.save;
        list.addView(inventoryLine("5.56 ammo", sv.ammo[0] + " rounds"));
        list.addView(inventoryLine("9mm ammo", sv.ammo[1] + " rounds"));
        list.addView(inventoryLine("12g shells", sv.ammo[2] + " shells"));
        list.addView(inventoryLine(".338 ammo", sv.ammo[3] + " rounds"));
        list.addView(inventoryLine("Scrap", "" + sv.scrap));
        list.addView(inventoryLine("Medkits", "" + sv.meds));
        list.addView(inventoryLine("Weapon parts", "" + sv.parts));
        list.addView(spacer(dp(6)));
        for (WeaponData w : g.data.weapons) {
            list.addView(inventoryLine(w.name, sv.owned(w.id) ? "owned" : "not owned"));
        }
    }

    private LinearLayout inventoryLine(String k, String v) {
        LinearLayout row = new LinearLayout(g.act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(7), dp(12), dp(7));
        row.setBackgroundColor(0x22111A24);
        TextView a = tv(14, 0xFFD8E8F0);
        a.setText(k);
        row.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView b = tv(14, ACCENT);
        b.setText(v);
        row.addView(b);
        return row;
    }

    // ---------------- settings ----------------

    private View settingsPanel(Context ctx) {
        FrameLayout f = panelBg();
        rebuildSettings(f);
        return f;
    }

    private void rebuildSettings(FrameLayout f) {
        f.removeAllViews();
        LinearLayout c = col(dp(24));
        TextView head = dim("SETTINGS", 18);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(head);
        c.addView(spacer(dp(10)));

        TextView sensL = dim("CAMERA SENSITIVITY  x" + String.format("%.1f", g.save.sens), 14);
        c.addView(sensL);
        SeekBar sens = new SeekBar(g.act);
        sens.setMax(170);
        sens.setProgress((int) (g.save.sens * 100) - 30);
        sens.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean user) {
                g.save.sens = (p + 30) / 100f;
                sensL.setText("CAMERA SENSITIVITY  x" + String.format("%.1f", g.save.sens));
                g.save.save(g.act);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        c.addView(sens);
        c.addView(spacer(dp(10)));

        TextView gq = dim("GRAPHICS", 14);
        c.addView(gq);
        LinearLayout qrow = new LinearLayout(g.act);
        qrow.setOrientation(LinearLayout.HORIZONTAL);
        final String[] qnames = {"LOW", "MEDIUM", "HIGH"};
        for (int i = 0; i < 3; i++) {
            final int q = i;
            TextView qb = btn(qnames[i], 13, g.save.quality == i ? ACCENT : DIM, new Runnable() {
                @Override public void run() {
                    g.setQuality(q);
                    showSettings();
                }
            });
            qrow.addView(qb);
        }
        c.addView(qrow);
        c.addView(spacer(dp(10)));

        TextView volL = dim("VOLUME  " + Math.round(g.save.vol * 100) + "%", 14);
        c.addView(volL);
        SeekBar vol = new SeekBar(g.act);
        vol.setMax(100);
        vol.setProgress((int) (g.save.vol * 100));
        vol.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean user) {
                g.save.vol = p / 100f;
                g.audio.setVol(g.save.vol);
                volL.setText("VOLUME  " + p + "%");
                g.save.save(g.act);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        c.addView(vol);
        c.addView(spacer(dp(14)));

        TextView del = btn("DELETE SAVE (START OVER)", 13, RED, new Runnable() {
            @Override public void run() {
                g.newGame();
                g.toMenu();
            }
        });
        c.addView(del);
        c.addView(btn("BACK", 16, DIM, new Runnable() {
            @Override public void run() { backToBase(); }
        }));
        f.addView(c);
    }

    // ---------------- intro ----------------

    private View introPanel(Context ctx) {
        FrameLayout f = panelBg();
        LinearLayout c = col(dp(24));
        final TextView title = tv(30, ACCENT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(title);
        final TextView loc = tv(14, AMBER);
        c.addView(loc);
        c.addView(spacer(dp(12)));
        final TextView body = tv(15, 0xFFD8E8F0);
        body.setLineSpacing(dp(5), 1f);
        c.addView(body);
        c.addView(spacer(dp(12)));
        final TextView objs = tv(13, 0xB0C8FFE0);
        c.addView(objs);
        c.addView(spacer(dp(14)));
        LinearLayout row = new LinearLayout(g.act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView go = btn("DEPLOY", 20, ACCENT, new Runnable() {
            @Override public void run() { g.startMission(g.introMission); }
        });
        row.addView(go);
        row.addView(spacer(dp(10)));
        TextView back = btn("BACK", 16, DIM, new Runnable() {
            @Override public void run() { backToBase(); showMissions(); }
        });
        row.addView(back);
        c.addView(row);
        f.addView(c);
        introTitle = title; introLoc = loc; introBody = body; introObjs = objs;
        return f;
    }

    private TextView introTitle, introLoc, introBody, introObjs;

    public void openIntro(int mi) {
        MissionData m = g.data.missions[mi];
        g.introMission = m.id;
        introTitle.setText("MISSION " + m.id + " — " + m.name);
        introLoc.setText(m.loc);
        introBody.setText(m.intro);
        StringBuilder sb = new StringBuilder();
        for (MissionData.Obj o : m.objectives) sb.append("•  ").append(o.label).append("\n");
        introObjs.setText(sb.toString());
        show(intro);
    }

    // ---------------- results ----------------

    private View resultsPanel(Context ctx) {
        FrameLayout f = panelBg();
        LinearLayout c = col(dp(24));
        final TextView title = tv(38, 0xFFFFFFFF);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(title);
        final TextView body = tv(15, 0xFFD8E8F0);
        body.setLineSpacing(dp(5), 1f);
        c.addView(body);
        c.addView(spacer(dp(14)));
        TextView go = btn("CONTINUE", 20, ACCENT, new Runnable() {
            @Override public void run() { g.afterResults(); }
        });
        c.addView(go);
        f.addView(c);
        resTitle = title; resBody = body;
        return f;
    }

    private TextView resTitle, resBody;

    public void showResults(boolean win) {
        resTitle.setText(win ? "MISSION COMPLETE" : "MISSION FAILED");
        resTitle.setTextColor(win ? ACCENT : RED);
        resBody.setText(g.lastResultsText());
        show(results);
    }

    // ---------------- story ----------------

    private View storyPanel(Context ctx) {
        FrameLayout f = panelBg();
        LinearLayout c = col(dp(24));
        final TextView title = tv(24, AMBER);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setText("FIELD REPORT");
        c.addView(title);
        c.addView(spacer(dp(12)));
        final TextView body = tv(15, 0xFFD8E8F0);
        body.setLineSpacing(dp(6), 1f);
        c.addView(body);
        c.addView(spacer(dp(14)));
        TextView go = btn("CONTINUE", 20, ACCENT, new Runnable() {
            @Override public void run() { g.afterStory(); }
        });
        c.addView(go);
        f.addView(c);
        storyBody = body;
        return f;
    }

    private TextView storyBody;

    public void showStory(int mi) {
        MissionData m = g.data.missions[mi];
        storyBody.setText(m.story);
        show(story);
    }

    // ---------------- dead ----------------

    private View deadPanel(Context ctx) {
        FrameLayout f = panelBg();
        LinearLayout c = col(dp(24));
        TextView title = tv(42, RED);
        title.setText("YOU DIED");
        title.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(title);
        TextView sub = dim("The city keeps moving. Regroup and try again.", 14);
        c.addView(sub);
        c.addView(spacer(dp(16)));
        LinearLayout row = new LinearLayout(g.act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(btn("RETRY MISSION", 18, ACCENT, new Runnable() {
            @Override public void run() { g.retryMission(); }
        }));
        row.addView(spacer(dp(10)));
        row.addView(btn("ABANDON", 16, DIM, new Runnable() {
            @Override public void run() { g.toSafehouse(); }
        }));
        c.addView(row);
        f.addView(c);
        return f;
    }

    // ---------------- pause ----------------

    private View pausePanel(Context ctx) {
        FrameLayout f = panelBg();
        LinearLayout c = col(dp(24));
        TextView title = tv(28, 0xFFFFFFFF);
        title.setText("PAUSED");
        title.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(title);
        c.addView(spacer(dp(14)));
        c.addView(btn("RESUME", 20, ACCENT, new Runnable() {
            @Override public void run() { g.togglePause(); }
        }));
        c.addView(btn("RESTART MISSION", 16, DIM, new Runnable() {
            @Override public void run() { g.retryMission(); }
        }));
        c.addView(btn("ABANDON MISSION", 16, DIM, new Runnable() {
            @Override public void run() { g.toSafehouse(); }
        }));
        f.addView(c);
        return f;
    }

    // ---------------- note ----------------

    private View notePanel(Context ctx) {
        FrameLayout f = panelBg();
        LinearLayout c = col(dp(24));
        final TextView title = tv(20, AMBER);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(title);
        c.addView(spacer(dp(10)));
        final TextView body = tv(15, 0xFFD8E8F0);
        body.setLineSpacing(dp(5), 1f);
        c.addView(body);
        c.addView(spacer(dp(14)));
        TextView go = btn("POCKET IT", 18, ACCENT, new Runnable() {
            @Override public void run() { g.closeNote(); }
        });
        c.addView(go);
        f.addView(c);
        noteTitle = title; noteBody = body;
        return f;
    }

    private TextView noteTitle, noteBody;

    public void showNote(String title, String text) {
        noteTitle.setText(title.toUpperCase());
        noteBody.setText(text);
        show(note);
    }

    // ---------------- safehouse overlay ----------------

    public void showSafehouseUI() {
        int lv = g.save.baseLevel();
        baseInfo.setText("BASE LEVEL " + lv + " / 6\n" + baseLine(lv));
        show(safehousePanel);
    }

    private FrameLayout buildSafehousePanel() {
        FrameLayout f = panelBg();
        f.setBackgroundColor(0x00000000);
        LinearLayout right = new LinearLayout(g.act);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(10), dp(10), dp(10), dp(10));
        right.setBackgroundColor(0x660B1118);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        rl.rightMargin = dp(10);
        TextView head = tv(14, 0xFFB8E8C8);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setText("SAFEHOUSE");
        right.addView(head);
        right.addView(spacer(dp(6)));
        right.addView(btn("MISSIONS", 16, ACCENT, new Runnable() {
            @Override public void run() { showMissions(); }
        }));
        right.addView(btn("WEAPONS", 16, 0xFFFFFFFF, new Runnable() {
            @Override public void run() { showWeapons(); }
        }));
        right.addView(btn("SURVIVOR", 16, 0xFFFFFFFF, new Runnable() {
            @Override public void run() { showSurvivor(); }
        }));
        right.addView(btn("INVENTORY", 16, 0xFFFFFFFF, new Runnable() {
            @Override public void run() { showInventory(); }
        }));
        right.addView(btn("MAIN MENU", 14, DIM, new Runnable() {
            @Override public void run() { g.toMenu(); }
        }));
        f.addView(right, rl);
        baseInfo = tv(13, 0xCCC8FFE0);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        bl.leftMargin = dp(12);
        bl.topMargin = dp(12);
        baseInfo.setBackgroundColor(0x660B1118);
        baseInfo.setPadding(dp(10), dp(8), dp(10), dp(8));
        f.addView(baseInfo, bl);
        return f;
    }

    private FrameLayout safehousePanel;

    private String baseLine(int lv) {
        String[] lines = {
                "A fortified room. The generator is silent.",
                "Generator online. Lights hum in the dark.",
                "Plants on the windowsill. Someone is still here.",
                "Weapon rack installed. Kabir approves.",
                "Operation board up — the road north is mapped.",
                "The safehouse feels like home now.",
        };
        return lines[Math.min(lines.length - 1, lv - 1)];
    }

    // ---------------- show/back plumbing ----------------

    private void show(View v) {
        hideAll();
        if (v != null) v.setVisibility(View.VISIBLE);
    }

    private void backToBase() {
        if (g.state == Game.ST_SAFEHOUSE) g.safehouseUI();
        else g.toMenu();
    }

    public void showWeapons() { show(weapons); }
    public void showSurvivor() { show(survivor); }
    public void showInventory() { refreshInventory(); show(inventory); }
    public void showSettings() {
        if (settings instanceof FrameLayout) rebuildSettings((FrameLayout) settings);
        show(settings);
    }
    public void showMenu() { show(menu); }
    public void showDead() { show(dead); }
    public void showPause() { show(pause); }
}
