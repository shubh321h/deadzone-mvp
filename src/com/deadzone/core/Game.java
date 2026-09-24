package com.deadzone.core;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.deadzone.audio.AudioMgr;
import com.deadzone.MainActivity;
import com.deadzone.data.DataLib;
import com.deadzone.data.MissionData;
import com.deadzone.data.SaveData;
import com.deadzone.entities.Player;
import com.deadzone.entities.Zombie;
import com.deadzone.gl.Renderer;
import com.deadzone.gl.Vec3;
import com.deadzone.input.HudView;
import com.deadzone.input.Input;
import com.deadzone.systems.MissionMgr;
import com.deadzone.ui.Hud;
import com.deadzone.ui.UI;
import com.deadzone.world.Fx;
import com.deadzone.world.World;

import java.util.ArrayList;

/**
 * Game shell: state machine (menu / safehouse / mission / paused / dead /
 * results), the GL frame driver, Host implementation for the simulation,
 * save points, and quality tiers. All content decisions come from data.
 */
public class Game implements Host, Renderer.Driver {
    public static final int ST_MENU = 0, ST_SAFEHOUSE = 1, ST_MISSION = 2,
            ST_PAUSED = 3, ST_DEAD = 4, ST_RESULTS = 5;

    public final Activity act;
    public Renderer rend;
    public final Input input = new Input();
    public final AudioMgr audio = new AudioMgr();
    public final DataLib data;
    public SaveData save;
    public World world = new World();
    public Player player = new Player();
    public Fx fx = new Fx();
    public MissionMgr mission = new MissionMgr();
    public UI ui;
    public Hud hud;

    public int state = ST_MENU;
    public int curMission = 1;
    public int introMission = 1;
    public boolean hasSave;
    public ArrayList<Zombie> zombiesList = new ArrayList<Zombie>();
    private final ArrayList<Zombie> spawnQueue = new ArrayList<Zombie>();
    public int[] sessionAmmo = new int[4];
    private int quality;
    private float simT;
    private float safeCam;
    private Runnable pendingGL;
    private boolean lastWin;
    private String lastResults = "";
    private boolean noteOpen;

    public Game(Activity a) {
        act = a;
        hasSave = SaveData.file(a).exists();
        data = DataLib.load(a);
        save = SaveData.load(a);
        quality = save.quality;
        sessionAmmo = save.ammo.clone();
        ui = new UI(this);
        hud = new Hud(this, a);
        ui.build(a);
        audio.init(a);
        audio.setVol(save.vol);
        pendingGL = new Runnable() {
            @Override public void run() { buildSafehouseScene(); }
        };
        ui.showMenu();
    }

    // ---------------- UI wiring ----------------

    public String versionName() {
        try {
            return act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    public View buildUi() {
        GLSurfaceView gl = new GLSurfaceView(act);
        gl.setEGLContextClientVersion(3);
        gl.setEGLConfigChooser(new Renderer.MsaaChooser()); // 4x MSAA when available
        rend = new Renderer(this);
        gl.setRenderer(rend);
        gl.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        HudView hudView = hud.view;
        FrameLayout root = new FrameLayout(act);
        root.addView(gl, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(hudView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(ui.panels, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        hud.hide();
        return root;
    }

    // ---------------- frame ----------------

    @Override public void frame(float dt, Renderer r) {
        try {
            frameInner(dt, r);
        } catch (Throwable t) {
            android.util.Log.e("DEADZONE", "gl frame error", t);
            try { ((MainActivity) act).showFatal(t); } catch (Throwable ignored) { }
        }
    }

    private void frameInner(float dt, Renderer r) {
        simT += dt;
        if (pendingGL != null) {
            Runnable p = pendingGL;
            pendingGL = null;
            p.run();
        }
        if (state == ST_MISSION && mission.state == MissionMgr.RUN && !noteOpen) {
            input.state.joyX = hud.stick.x;
            input.state.joyY = hud.stick.y;
            player.update(dt, input.state, this);
            for (Zombie z : zombiesList)
                if (z.alive) z.update(dt, this);
            flushSpawns();
            mission.update(dt);
            flushSpawns();
            // tutorial prompts
            if (mission.m.tutorial.size() > 0) {
                tutorialT += dt;
                int idx = (int) (tutorialT / 6f);
                if (idx != tutorialStep && idx < mission.m.tutorial.size()) {
                    tutorialStep = idx;
                    toast(mission.m.tutorial.get(idx));
                }
            }
            if (player.dead && state == ST_MISSION) {
                state = ST_DEAD;
                ui.showDead();
            }
        }
        fx.update(dt);
        render(r);
        input.endFrame();
    }

    private void render(Renderer r) {
        float aspect = r.w / (float) Math.max(1, r.h);
        if (state == ST_MISSION || state == ST_DEAD || state == ST_PAUSED || state == ST_RESULTS) {
            r.begin(0, 0, 0);
            r.atmosphere("delhi".equals(world.region) ? 1
                    : ("ladakh".equals(world.region) ? 2 : 0));
            r.setCam(player.camPos.x, player.camPos.y, player.camPos.z, drawDist());
            float[] vp = player.viewProj(aspect, player.wcur(this).adsFov,
                    input.state.sprint && input.state.joyY > 0.2f);
            r.setVP(vp);
            r.drawSky();
            player.draw(r, this);
            for (Zombie z : zombiesList)
                if (z.alive) z.draw(r, simT);
            mission.draw(r, simT);
            fx.draw(r, player.camPos, rainCount(), world.rain);
            r.end();
            hud.update(this);
        } else {
            safeCam += 0.008f;
            Vec3 eye = new Vec3((float) Math.cos(safeCam) * 9f, 3.4f, (float) Math.sin(safeCam) * 9f);
            Vec3 center = new Vec3(0, 1.1f, 0);
            r.begin(0, 0, 0);
            r.atmosphere(3);
            r.setCam(eye.x, eye.y, eye.z, 70f);
            r.setLookVP(eye, center, new Vec3(0, 1, 0), 55f);
            r.drawSky();
            player.draw(r, this);
            fx.draw(r, eye, 0, false);
            r.end();
        }
    }

    // ---------------- level / flow ----------------

    private void buildSafehouseScene() {
        world.buildSafehouse(save.baseLevel(), rend);
        fx.reset();
        zombiesList.clear();
        spawnQueue.clear();
        player.reset(new Vec3(0, 0, 3), 0f, this);
    }

    public void startMission(int id) {
        introMission = id;
        curMission = id;
        final MissionData md = data.m(id);
        sessionAmmo = save.ammo.clone();
        pendingGL = new Runnable() {
            @Override public void run() {
                world.buildCity(md, rend);
                fx.reset();
                zombiesList.clear();
        spawnQueue.clear();
                Vec3 sp = world.anchor("spawn");
                Vec3 sh = world.anchor("safehouse");
                float dx = sh.x - sp.x, dz = sh.z - sp.z;
                float L = (float) Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) Math.atan2(-dx / L, -dz / L);
                player.reset(sp, yaw, Game.this);
                mission.start(md, Game.this);
            }
        };
        state = ST_MISSION;
        ui.hideAll();
        hud.show();
        audio.setDrone(true);
        audio.setRain(md.rain);
        if (md.tutorial != null && md.tutorial.size() > 0)
            toast(md.tutorial.get(0));
        tutorialStep = 1;
        tutorialT = 0;
    }

    private int tutorialStep;
    private float tutorialT;

    public void retryMission() {
        startMission(curMission);
    }

    public void toSafehouse() {
        pendingGL = new Runnable() {
            @Override public void run() { buildSafehouseScene(); }
        };
        state = ST_SAFEHOUSE;
        ui.hideAll();
        hud.hide();
        audio.setDrone(true);
        audio.setRain(false);
        save.save(act);
        ui.showSafehouseUI();
    }

    public void toMenu() {
        state = ST_MENU;
        ui.hideAll();
        hud.hide();
        audio.setRain(false);
        save.save(act);
        ui.showMenu();
    }

    public void safehouseUI() {
        if (state == ST_SAFEHOUSE) ui.showSafehouseUI();
    }

    public void openIntro(int mi) {
        ui.openIntro(mi);
    }

    public void afterResults() {
        if (lastWin) ui.showStory(curMission);
        else toSafehouse();
    }

    public void afterStory() {
        toSafehouse();
    }

    public void closeNote() {
        noteOpen = false;
        ui.hideAll();
        hud.show();
        state = ST_MISSION;
    }

    public void togglePause() {
        if (state == ST_MISSION) {
            state = ST_PAUSED;
            ui.showPause();
            audio.pauseAll();
            save.save(act);
        } else if (state == ST_PAUSED) {
            state = ST_MISSION;
            ui.hideAll();
            hud.show();
            audio.resumeAll();
        }
    }

    // ---------------- mission outcome ----------------

    @Override public void missionWon(MissionMgr m) {
        SaveData sv = save;
        sv.ammo = sessionAmmo.clone();
        int mi = m.m.id;
        boolean firstClear = !sv.done[mi - 1];
        int xpGain = m.m.rewardXp + m.kills * 3;
        int scrapGain = m.m.rewardScrap + m.scrapGained;
        int partsGain = m.m.rewardParts + m.partsGained;
        sv.scrap += scrapGain;
        sv.parts += partsGain;
        sv.meds += m.m.rewardMeds;
        boolean lvUp = sv.addXp(xpGain);
        if (firstClear) sv.done[mi - 1] = true;
        String weaponMsg = "";
        if (firstClear && m.m.rewardWeapon != null) {
            SaveData.WSave ws = sv.wsave(m.m.rewardWeapon);
            if (!ws.own) {
                ws.own = true;
                int ai = DataLib.ammoIndex(data.w(m.m.rewardWeapon).ammoType);
                sv.ammo[ai] += 30;
                weaponMsg = "\nNEW WEAPON: " + data.w(m.m.rewardWeapon).name;
            }
        }
        sv.playTime += m.t;
        sv.save(act);
        lastWin = true;
        String time = fmtTime(m.t);
        StringBuilder sb = new StringBuilder();
        sb.append("Kills: ").append(m.kills).append("    Time: ").append(time).append("\n\n");
        sb.append("XP +").append(xpGain).append("    SCRAP +").append(scrapGain);
        if (m.m.rewardMeds > 0) sb.append("    MED +").append(m.m.rewardMeds);
        if (m.m.rewardParts > 0) sb.append("    PARTS +").append(m.m.rewardParts);
        if (lvUp) sb.append("\nSURVIVOR LEVEL UP → ").append(sv.level);
        sb.append(weaponMsg);
        if (firstClear && mi < data.missions.length)
            sb.append("\n\nNEXT MISSION UNLOCKED: ").append(data.missions[mi].name);
        lastResults = sb.toString();
        state = ST_RESULTS;
        audio.play("ui");
        ui.showResults(true);
    }

    @Override public void missionLost(MissionMgr m) {
        save.playTime += m.t;
        save.save(act);
        lastWin = false;
        lastResults = "Kills: " + m.kills + "    Time: " + fmtTime(m.t)
                + "\n\nThe sector holds. Regroup at the safehouse and try again.";
        state = ST_RESULTS;
        ui.showResults(false);
    }

    private String fmtTime(float t) {
        int s = (int) t;
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    public String lastResultsText() { return lastResults; }

    // ---------------- progression actions ----------------

    public void upgradeWeapon(String id, int axis) {
        int cur = save.wLevel(id, axis);
        int max = data.upgCost[axis].length;
        if (cur >= max) return;
        int[] cost = data.upgCost[axis][cur];
        if (save.scrap >= cost[0] && save.parts >= cost[1]) {
            save.scrap -= cost[0];
            save.parts -= cost[1];
            save.wUpgrade(id, axis);
            save.save(act);
            audio.play("ui");
        }
    }

    public void upgradeStat(int which) {
        int cur = which == 0 ? save.stH : which == 1 ? save.stSp : which == 2 ? save.stR : save.stD;
        int max = SaveData.statMax(which);
        if (cur >= max) return;
        int cost = save.statCost(cur);
        if (save.scrap >= cost) {
            save.scrap -= cost;
            if (which == 0) save.stH++;
            else if (which == 1) save.stSp++;
            else if (which == 2) save.stR++;
            else save.stD++;
            save.save(act);
            audio.play("ui");
        }
    }

    public void equipWeapon(String id) {
        if (!save.owned(id)) return;
        save.equip = id;
        save.save(act);
        audio.play("ui");
    }

    public void setQuality(int q) {
        quality = q;
        save.quality = q;
        save.save(act);
    }

    public void newGame() {
        save = new SaveData();
        save.reset();
        save.save(act);
        hasSave = true;
        quality = save.quality;
        sessionAmmo = save.ammo.clone();
    }

    // ---------------- Host ----------------

    @Override public World world() { return world; }
    @Override public Fx fx() { return fx; }
    @Override public DataLib data() { return data; }
    @Override public SaveData save() { return save; }
    @Override public int quality() { return quality; }
    @Override public float sens() { return save.sens; }
    @Override public int[] sessionAmmo() { return sessionAmmo; }
    @Override public java.util.List<Zombie> zombies() { return zombiesList; }

    @Override public void sfx(String name) { audio.play(name); }

    @Override public void toast(String s) {
        if (state == ST_MISSION || state == ST_PAUSED) hud.setToast(s);
    }

    @Override public void onZombieKilled(Zombie z) {
        if (mission.state == MissionMgr.RUN) mission.zombieKilled(z);
    }

    @Override public void playerDied() {
        // state flip happens in frame()
    }

    @Override public void playerInteract() {
        if (state == ST_MISSION && mission.state == MissionMgr.RUN)
            mission.tryInteract();
    }

    @Override public Player player() { return player; }

    /** Spawns are queued and flushed after the update loops so zombie updates
     *  (e.g. the boss summoning adds) can never mutate the list mid-iteration. */
    @Override public void spawnZombie(String type, Vec3 p) {
        if (type == null || data.e(type) == null) return;
        boolean boss = data.e(type).boss;
        int alive = spawnQueue.size();
        for (Zombie z : zombiesList) if (z.alive) alive++;
        if (!boss && alive >= zCap()) return;
        Zombie z = new Zombie();
        z.spawn(p, data.e(type));
        spawnQueue.add(z);
    }

    private void flushSpawns() {
        if (spawnQueue.isEmpty()) return;
        for (Zombie z : spawnQueue) zombiesList.add(z);
        spawnQueue.clear();
    }

    @Override public void showNote(String title, String text) {
        if (state == ST_MISSION) {
            state = ST_PAUSED;
            noteOpen = true;
            ui.showNote(title, text);
            audio.pauseAll();
        }
    }

    // ---------------- activity lifecycle ----------------

    public void onActivityPause() {
        if (state == ST_MISSION) togglePause();
        save.save(act);
        audio.pauseAll();
    }

    public void onActivityResume() {
        audio.resumeAll();
    }

    /** @return true if the back key was consumed */
    public boolean onBack() {
        if (noteOpen) { closeNote(); return true; }
        if (state == ST_PAUSED) { togglePause(); return true; }
        if (ui.anyVisible()) {
            if (state == ST_SAFEHOUSE) safehouseUI();
            else if (state == ST_MISSION) toSafehouse();
            else toMenu();
            return true;
        }
        if (state == ST_MISSION) { togglePause(); return true; }
        if (state == ST_SAFEHOUSE) { toMenu(); return true; }
        return false;
    }

    // ---------------- quality tiers ----------------

    public int drawDist() { return quality == 0 ? 70 : quality == 1 ? 110 : 150; }
    public int rainCount() { return quality == 0 ? 0 : quality == 1 ? 140 : 220; }
    public int zCap() { return quality == 0 ? 24 : quality == 1 ? 36 : 48; }
                     }
