package sim;

import com.deadzone.core.Host;
import com.deadzone.core.InputState;
import com.deadzone.data.DataLib;
import com.deadzone.data.MissionData;
import com.deadzone.data.SaveData;
import com.deadzone.entities.Player;
import com.deadzone.entities.Zombie;
import com.deadzone.gl.Renderer;
import com.deadzone.gl.Vec3;
import com.deadzone.systems.MissionMgr;
import com.deadzone.world.Aabb;
import com.deadzone.world.Fx;
import com.deadzone.world.World;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Headless gameplay simulation: exercises world generation, data loading,
 *  zombie AI, boss patterns, combat, objectives, extraction and save IO
 *  without Android or GL. Run: java -cp out:android.jar sim.Smoke <assetsDir> */
public class Smoke implements Host {
    // ---- host state ----
    World world = new World();
    Fx fx = new Fx();
    DataLib data;
    SaveData save;
    int[] sessionAmmo = new int[4];
    Player player = new Player();
    public List<Zombie> zs = new ArrayList<Zombie>();
    MissionMgr mission;
    int quality = 1;
    float sens = 1f;
    public int playerDeaths;
    public final List<String> events = new ArrayList<String>();

    static int passed = 0, failed = 0;

    static void check(boolean ok, String what) {
        if (ok) { passed++; System.out.println("  PASS  " + what); }
        else { failed++; System.out.println("  FAIL  " + what); }
    }

    // ---------- fake renderer (no GL) ----------
    public static class FakeRenderer extends Renderer {
        public FakeRenderer() { super(null); }
        @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl, javax.microedition.khronos.egl.EGLConfig c) { }
        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl, int w, int h) { }
        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) { }
        @Override public void begin(float r, float g, float b) { }
        @Override public void end() { }
        @Override public void endStatic() { }
        @Override public void clearStatic() { }
    }

    // ---------- Host ----------
    public World world() { return world; }
    public Fx fx() { return fx; }
    public DataLib data() { return data; }
    public SaveData save() { return save; }
    public int quality() { return quality; }
    public float sens() { return sens; }
    public int[] sessionAmmo() { return sessionAmmo; }
    public List<Zombie> zombies() { return zs; }
    public void sfx(String n) { }
    public void toast(String s) { events.add(s); }
    public void onZombieKilled(Zombie z) { if (mission != null) mission.zombieKilled(z); }
    public void playerDied() { playerDeaths++; }
    public void playerInteract() { if (mission != null && mission.state == MissionMgr.RUN) mission.tryInteract(); }
    public Player player() { return player; }
    public boolean bossOnlySpawns = false;
    private final List<Zombie> spawnQueue = new ArrayList<Zombie>();
    /** Mirrors Game: spawns are queued, flushed after update loops. */
    public void spawnZombie(String type, Vec3 p) {
        if (bossOnlySpawns && !type.equals("colossus")) return;
        Zombie z = new Zombie();
        z.spawn(p, data.e(type));
        spawnQueue.add(z);
    }
    public void flushSpawns() {
        for (Zombie z : spawnQueue) zs.add(z);
        spawnQueue.clear();
    }
    public void missionWon(MissionMgr m) { events.add("WON"); }
    public void missionLost(MissionMgr m) { events.add("LOST"); }
    public void showNote(String t, String x) { events.add("NOTE:" + t); }

    // ---------- main ----------
    public void debugPoints() {
        for (int mi = 0; mi < data.missions.length; mi++) {
            setupMission(data.missions[mi]);
            System.out.println("== mission " + data.missions[mi].id + " ==");
            for (MissionMgr.InteractPoint p : mission.pts) {
                boolean inside = world.insideSolid(p.pos);
                int n = world.graph != null ? world.graph.nearest(p.pos) : -1;
                float nd = n >= 0 ? world.graph.nodes[n].dist(p.pos) : -1;
                boolean see = n >= 0 && world.los(p.pos, world.graph.nodes[n]);
                System.out.println("  point " + p.t + " at (" + (int) p.pos.x + "," + (int) p.pos.z + ")"
                        + (inside ? "  ** INSIDE SOLID **" : "")
                        + " nearestNode=(" + (n >= 0 ? (int) world.graph.nodes[n].x : 999) + ","
                        + (n >= 0 ? (int) world.graph.nodes[n].z : 999) + ") nd=" + (int) nd
                        + " los=" + see + " approachable=" + world.approachable(p.pos, 20f));
            }
            for (MissionMgr.Pickup lk : mission.loot) {
                boolean inside = world.insideSolid(lk.pos);
                int n = world.graph != null ? world.graph.nearest(lk.pos) : -1;
                float nd = n >= 0 ? world.graph.nodes[n].dist(lk.pos) : -1;
                boolean see = n >= 0 && world.los(lk.pos, world.graph.nodes[n]);
                if (inside || !world.approachable(lk.pos, 20f))
                    System.out.println("  LOOT " + lk.t + " at (" + (int) lk.pos.x + "," + (int) lk.pos.z + ")"
                            + (inside ? " ** INSIDE SOLID **" : "")
                            + " nd=" + (int) nd + " los=" + see
                            + " approachable=" + world.approachable(lk.pos, 20f));
            }
        }
    }

    public void debugFlood() {
        setupMission(data.missions[2]);
        Vec3 p2 = new Vec3(16, 0, -110);
        System.out.println("solids near (16,-110):");
        for (Aabb b : world.solids) {
            float cx = (b.x0 + b.x1) / 2, cz = (b.z0 + b.z1) / 2;
            float d = (float) Math.sqrt((cx - 16) * (cx - 16) + (cz + 110) * (cz + 110));
            if (d < 45) {
                boolean contains = p2.x > b.x0 && p2.x < b.x1 && p2.z > b.z0 && p2.z < b.z1;
                System.out.printf("  box x[%.0f,%.0f] z[%.0f,%.0f] y[%.1f,%.1f]%s%n",
                        b.x0, b.x1, b.z0, b.z1, b.y0, b.y1, contains ? "  <-- CONTAINS POINT" : "");
            }
        }
        int from = world.graph.nearest(new Vec3(30, 0, -90));
        int to = world.graph.nearest(p2);
        List<Integer> path = world.graph.path(from, to);
        System.out.println("path 30,-90 -> 16,-110: " + (path == null ? "NULL" : path.size() + " nodes"));
        System.out.println("LOS 30,-90 -> 16,-110: " + world.los(new Vec3(30, 0, -90), p2));
        // what blocks the straight line?
        Vec3 o = new Vec3(30, 1.2f, -90);
        Vec3 dir = p2.clone().sub(o).norm();
        float tHit = 1e30f; Aabb hitB = null;
        for (Aabb b : world.solids) {
            float t = b.ray(o, dir, 60f);
            if (t >= 0 && t < tHit) { tHit = t; hitB = b; }
        }
        if (hitB != null)
            System.out.printf("first blocker at t=%.1f: x[%.0f,%.0f] z[%.0f,%.0f] y[%.1f,%.1f]%n",
                    tHit, hitB.x0, hitB.x1, hitB.z0, hitB.z1, hitB.y0, hitB.y1);
    }

    public void debugExtract() {
        setupMission(data.missions[1]); // M2
        for (int i = 0; i < mission.objs.size(); i++) {
            MissionData.Obj o = mission.objs.get(i);
            mission.objDone[i] = o.t.equals("kill") || o.t.equals("collect") ? o.n : 1;
        }
        System.out.println("allDone=" + mission.allDone() + " state=" + mission.state);
        System.out.println("extract=(" + mission.extract.x + "," + mission.extract.y + "," + mission.extract.z + ")");
        player.pos.copy(mission.extract);
        System.out.println("dist at extract=" + player.pos.dist(mission.extract));
        mission.update(1f / 60f);
        System.out.println("after update: state=" + mission.state + " (WON=1)");
        // step 1s to be sure
        for (int i = 0; i < 60; i++) mission.update(1f / 60f);
        System.out.println("after 1s: state=" + mission.state);
    }

    public static void main(String[] args) throws Exception {
        Smoke s = new Smoke();
        File dataDir = new File(args.length > 0 ? args[0] : "assets/data");
        if (args.length > 1 && args[1].equals("dbgpoints")) {
            s.data = DataLib.loadFromDir(dataDir);
            s.save = new SaveData();
            s.save.reset();
            s.save.equip = "rifle";
            s.debugPoints();
            return;
        }
        if (args.length > 1 && args[1].equals("dbgflood")) {
            s.data = DataLib.loadFromDir(dataDir);
            s.save = new SaveData();
            s.save.reset();
            s.save.equip = "rifle";
            s.debugFlood();
            return;
        }
        if (args.length > 1 && args[1].equals("dbgextract")) {
            s.data = DataLib.loadFromDir(dataDir);
            s.save = new SaveData();
            s.save.reset();
            s.save.equip = "rifle";
            s.debugExtract();
            return;
        }
        System.out.println("== 1. data load ==");
        s.data = DataLib.loadFromDir(dataDir);
        check(s.data.weapons.length == 4, "4 weapons loaded");
        check(s.data.enemies.size() >= 7 && s.data.enemies.containsKey("subject_zero"),
                s.data.enemies.size() + " enemy types loaded (incl. alpha colossus)");
        check(s.data.missions.length == 12, "12 missions loaded (3 chapters)");
        check(s.data.upgCost[0].length == 5 && s.data.upgCost[2].length == 5, "upgrade cost tables");
        for (MissionData m : s.data.missions) {
            check(m.objectives.size() >= 1, "mission " + m.id + " has objectives");
            check(m.intro.length() > 40, "mission " + m.id + " has intro text");
            check(m.story.length() > 40, "mission " + m.id + " has story text");
        }
        check(s.data.w("rifle").ammoType.equals("5.56"), "rifle ammo type 5.56");
        check(s.data.e("colossus").boss, "colossus flagged boss");

        System.out.println("== 2. save roundtrip ==");
        s.save = new SaveData();
        s.save.reset();
        s.save.scrap = 123; s.save.parts = 7; s.save.xp = 90; s.save.addXp(200);
        s.save.done[0] = true; s.save.equip = "smg"; s.save.wsave("smg").lD = 2;
        s.save.sens = 1.37f; s.save.quality = 2;
        File tmp = File.createTempFile("dzsave", ".json");
        s.save.saveJVM(tmp);
        SaveData back = SaveData.loadJVM(tmp);
        check(back.scrap == 123 && back.parts == 7, "save/load resources");
        check(back.level > 1, "xp/level persisted (level " + back.level + ")");
        check(back.done[0] && !back.done[1], "save/load mission progress");
        check(back.equip.equals("smg") && back.wsave("smg").lD == 2, "save/load weapon state");
        check(Math.abs(back.sens - 1.37f) < 0.001f && back.quality == 2, "save/load settings");
        check(back.unlocked(1) && !back.unlocked(2), "unlock chain correct");

        System.out.println("== 3. world generation ==");
        FakeRenderer rend = new FakeRenderer();
        for (int i = 0; i < s.data.missions.length; i++) {
            MissionData md = s.data.missions[i];
            s.world = new World();
            s.world.buildCity(md, rend);
            check(s.world.solids.size() > 60, "mission " + md.id + ": " + s.world.solids.size() + " colliders");
            check(s.world.open.length > 200, "mission " + md.id + ": " + s.world.open.length + " open points");
            for (String a : new String[]{"spawn", "safehouse", "market", "camp", "docks", "station", "tower"})
                check(s.world.anchor(a) != null, "anchor " + a + " present");
            // mission-specific anchors referenced by content must resolve
            for (MissionData.Loot l : md.loot)
                if (s.world.anchor(l.at) == null) check(false, "loot anchor " + l.at);
            for (MissionData.Point p : md.points)
                if (s.world.anchor(p.anchor) == null) check(false, "point anchor " + p.anchor);
            if (s.world.anchor(md.extractAnchor) == null) check(false, "extract anchor " + md.extractAnchor);
        }
        s.world = new World();
        s.world.buildSafehouse(5, rend);
        check(s.world.solids.size() >= 8, "safehouse built (" + s.world.solids.size() + " colliders)");
        // LOS sanity: two points far apart in the city
        s.world.buildCity(s.data.missions[0], rend);
        float losClear = 0, losBlocked = 0;
        java.util.Random rr = new java.util.Random(1);
        for (int i = 0; i < 200; i++) {
            Vec3 a = s.world.open[rr.nextInt(s.world.open.length)];
            Vec3 b = s.world.open[rr.nextInt(s.world.open.length)];
            if (a.dist(b) < 20) continue;
            if (s.world.los(a, b)) losClear++; else losBlocked++;
        }
        check(losClear > 5 && losBlocked > 5, "LOS: " + losClear + " clear / " + losBlocked + " blocked (buildings block sight)");

        System.out.println("== 4. mission 1 playthrough (heuristic player) ==");
        s.runMission1();

        System.out.println("== 5. boss fight (mission 5) ==");
        s.runBoss();

        System.out.println("== 6. screamer / brute / runner behavior ==");
        s.runEnemyBehaviors();

        System.out.println("== 7. full campaign (missions 1-5, real progression) ==");
        s.runCampaign();

        System.out.println("== 8. objective placement (no buried points/loot) ==");
        for (int mi = 0; mi < s.data.missions.length; mi++) {
            s.setupMission(s.data.missions[mi]);
            boolean buried = false, stranded = false;
            for (MissionMgr.InteractPoint p : s.mission.pts) {
                if (s.world.insideSolid(p.pos)) buried = true;
                if (!s.world.approachable(p.pos, 20f)) stranded = true;
            }
            for (MissionMgr.Pickup lk : s.mission.loot) {
                if (s.world.insideSolid(lk.pos)) buried = true;
                if (!s.world.approachable(lk.pos, 20f)) stranded = true;
            }
            check(!buried && !stranded, "mission " + s.data.missions[mi].id + " points/loot reachable");
        }

        System.out.println();
        System.out.println("SMOKE RESULT: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private void setupMission(MissionData md) {
        world = new World();
        FakeRenderer rend = new FakeRenderer();
        world.buildCity(md, rend);
        zs.clear();
        spawnQueue.clear();
        fx = new Fx();
        mission = new MissionMgr();
        player = new Player();
        sessionAmmo = save.ammo.clone();
        mission.start(md, this);
        flushSpawns(); // initial ambient (mirrors first game frame)
        Vec3 sp = world.anchor("spawn");
        player.reset(sp, 0f, this);
    }

    /** Simple heuristic: walk the street grid toward the goal (axis detours
     *  when blocked), shoot zombies that are close or on the way. */
    private int detourAxis = 0; // 0 none, 1 +x, 2 -x, 3 +z, 4 -z
    private float detourT = 0f;
    private Vec3 doorRef;
    private int missionKillGoal = 0;
    private boolean missionKillsLeft() { return mission.kills < missionKillGoal; }
    private Zombie huntTarget;
    private float huntSetT;

    // street pathing for the sim player (humans follow the streets too)
    private java.util.ArrayList<Integer> pPath;
    private int pPathIdx;
    private float pPathT;

    /** Direction to travel toward a point: straight if visible & near,
     *  otherwise along the A* street path. Deflects along obstacles
     *  (wall-slide, mirroring the zombie AI) so a waypoint behind a
     *  container corner cannot pin the walker against its face. */
    private Vec3 pathDir(World w, Vec3 target) {
        float dx = target.x - player.pos.x, dz = target.z - player.pos.z;
        float L = (float) Math.sqrt(dx * dx + dz * dz);
        if (L < 1f) return null;
        Vec3 mv;
        if ((w.los(player.pos, target) && L < 40f) || w.graph == null) {
            mv = new Vec3(dx / L, 0, dz / L);
        } else {
            pPathT -= 1f / 60f;
            if (pPath == null || pPathIdx >= pPath.size() || pPathT <= 0f) {
                pPath = w.graph.path(w.graph.nearestOpen(w, player.pos), w.graph.nearestOpen(w, target));
                pPathIdx = 0;
                pPathT = 2f;
            }
            mv = null;
            if (pPath != null && !pPath.isEmpty() && pPathIdx < pPath.size()) {
                Vec3 wp = w.graph.node(pPath.get(pPathIdx));
                Vec3 d = new Vec3(wp.x - player.pos.x, 0, wp.z - player.pos.z);
                float l = d.len();
                if (l < 1.5f) pPathIdx++;
                if (l > 0.01f) mv = d.mul(1f / l);
            }
            if (mv == null) mv = new Vec3(dx / L, 0, dz / L);
        }
        // probe ahead: something blocks the next step — slide along it
        if (w.rayDist(player.pos.clone(), mv, 1.1f) < 1.0f) {
            Vec3 a = rotXZ(mv, 1.0f), b = rotXZ(mv, -1.0f);
            boolean ca = w.rayDist(player.pos.clone(), a, 1.4f) > 1.2f;
            boolean cb = w.rayDist(player.pos.clone(), b, 1.4f) > 1.2f;
            if (ca && !cb) mv = a;
            else if (cb && !ca) mv = b;
            else if (ca || cb) mv = ca ? a : b;
            else mv = rotXZ(mv, 1.4f);
        }
        return mv;
    }

    private static Vec3 rotXZ(Vec3 v, float ang) {
        float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
        return new Vec3(v.x * c - v.z * s, 0, v.x * s + v.z * c);
    }

    private Zombie nearestZombie(float maxD) {
        Zombie best = null;
        float bd = maxD;
        for (Zombie z : zs) {
            if (!z.alive || z.state == Zombie.DYING || z.boss) continue;
            float d = player.pos.dist(z.pos);
            if (d < bd) { bd = d; best = z; }
        }
        return best;
    }

    private void stepPlayer(float dt, InputState in, Vec3 goal) {
        // --- defense: anything close gets shot first ---
        float defR = player.pos.dist(doorRef) < 8f && missionKillsLeft() ? 12f : 20f;
        Zombie near = nearestZombie(defR);
        boolean shooting = false;
        if (near != null && world.los(player.pos, near.pos)) {
            float zdx = near.pos.x - player.pos.x, zdz = near.pos.z - player.pos.z;
            float zL = (float) Math.sqrt(zdx * zdx + zdz * zdz);
            player.camYaw = (float) Math.atan2(-zdx / zL, -zdz / zL);
            float aimY = near.pos.y + 1.55f * near.d.scale;
            player.camPitch = (float) Math.max(-0.15f, Math.min(0.8f,
                    (float) Math.atan2(aimY - 1.40f, zL)));
            shooting = true;
            in.fire = true;
            // stand ground when swarmed, else keep walking
            if (zL < 5f) { in.joyY = 0; in.joyX = 0; }
            else { in.joyY = 0.5f; in.joyX = 0; }
            in.sprint = true;
            player.update(dt, in, this);
            return;
        }
        in.fire = false;
        // --- travel: straight when visible, else follow the street path ---
        Vec3 mv = pathDir(world, goal);
        if (mv == null) { in.joyY = 0; in.joyX = 0; }
        else {
            player.camYaw = (float) Math.atan2(-mv.x, -mv.z);
            player.camPitch = 0f;
            in.joyY = 1f;
            in.joyX = 0f;
        }
        in.sprint = true;
        player.update(dt, in, this);
    }

    private void runMission1() {
        setupMission(data.missions[0]);
        check(zs.size() >= 6, "ambient zombies spawned (" + zs.size() + ")");
        check(player.mags[0] > 0, "player starts with rifle ammo (" + player.mags[0] + " in mag)");
        Vec3 door = world.anchor("safehouse_door") != null ? world.anchor("safehouse_door") : world.anchor("safehouse");
        doorRef = door;
        missionKillGoal = 10;
        InputState in = new InputState();
        float t = 0;
        float lastProg = player.pos.dist(door);
        float stuckT = 0f;
        int nextAxis = 1;
        while (t < 900f && mission.state == MissionMgr.RUN) {
            // goal: door first (objective); once at the door, hunt zombies (sticky target
            // that survives leaving the reach circle)
            Vec3 goal = door;
            if (player.pos.dist(door) < 6f && huntTarget == null) {
                huntTarget = nearestZombie(1e9f);
                huntSetT = t;
            }
            if (huntTarget != null) {
                if (huntTarget.state == Zombie.DYING || !huntTarget.alive || t - huntSetT > 120f) {
                    huntTarget = null;
                    if (player.pos.dist(door) > 6f) goal = door;
                } else {
                    goal = huntTarget.pos;
                }
            }
            float prog = player.pos.dist(goal);
            if (prog > lastProg - 0.05f) stuckT += 1f / 60f; else stuckT = 0f;
            // axis detours only while far from the door; straight line at the end
            boolean longLeg = player.pos.dist(door) > 12f;
            if (longLeg && stuckT > 1.4f && detourT <= 0f) {
                detourAxis = nextAxis;
                nextAxis = nextAxis % 4 + 1;
                detourT = 2.5f;
                stuckT = 0f;
            }
            lastProg = prog;
            if (player.hp < 45f && save.meds > 0) player.useMed(this);
            stepPlayer(1f / 60f, in, goal);
            for (Zombie z : zs) if (z.alive) z.update(1f / 60f, this);
            flushSpawns();
            mission.update(1f / 60f);
            flushSpawns();
            fx.update(1f / 60f);
            t += 1f / 60f;
        }
        boolean won = mission.state == MissionMgr.WON;
        System.out.println("  [info] t=" + (int) t + "s kills=" + mission.kills
                + " hp=" + (int) player.hp + " finalDist=" + String.format("%.1f", player.pos.dist(door))
                + " dead=" + player.dead + " obj=" + mission.objLine().replace('\n', '|'));
        check(mission.kills >= 5, "player killed zombies (" + mission.kills + ")");
        check(won, "mission 1 completed (reach safehouse + kills): state=" + mission.state);
        check(mission.allDone() == (mission.state == MissionMgr.WON), "win state consistent with objectives");
    }

    private void runBoss() {
        save.equip = "rifle"; // player loads the rifle for the big fight
        bossOnlySpawns = true; // isolated duel: no wave/ambient/summon adds
        setupMission(data.missions[4]);
        // sim-only: stand in for ammo accumulated across missions 1-4
        sessionAmmo[0] += 550;
        // spawn boss immediately at the station
        Vec3 bp = world.anchor("station");
        spawnZombie("colossus", new Vec3(bp.x, 0, bp.z));
        flushSpawns();
        Zombie boss = null;
        for (Zombie z : zs) if (z.boss) boss = z;
        check(boss != null && boss.hp > 0, "colossus spawned (hp " + (boss == null ? 0 : (int) boss.hp) + ")");
        check(mission.allDone() == false, "boss objective pending before kill");
        InputState in = new InputState();
        float t = 0;
        boolean sawPhase2 = false, sawPattern = false;
        int patternsSeen = 0;
        int lastPat = -1;
        int summons = zs.size();
        int resupplies = 0;
        float bStuckT = 0f;
        Vec3 bPrev = player.pos.clone();
        float bOsT = 0f; Vec3 bOsP = player.pos.clone(); // net-progress watchdog
        while (t < 300f && boss.alive) {
            // --- pick target: close adds first, else the boss ---
            Zombie target = boss;
            float aimY = 2.3f;
            Zombie add = nearestZombie(12f);
            if (add != null && world.los(player.pos, add.pos)) {
                target = add;
                aimY = add.pos.y + add.d.scale;
            } else if (!world.los(player.pos, boss.pos)) {
                target = null; // reposition: no line of fire
            }
            float dx = target != null ? target.pos.x - player.pos.x : boss.pos.x - player.pos.x;
            float dz = target != null ? target.pos.z - player.pos.z : boss.pos.z - player.pos.z;
            float L = (float) Math.sqrt(dx * dx + dz * dz);
            player.camYaw = (float) Math.atan2(-dx / L, -dz / L);
            player.camPitch = (float) Math.atan2(aimY - 1.40f, L);
            int pat = boss.bPat;
            boolean losB = target != null && world.los(player.pos, target.pos);
            if (L > 32f || !losB) {
                // approach along the street network until we have a line of fire
                Vec3 mv = pathDir(world, target != null ? target.pos : boss.pos);
                if (mv != null) {
                    player.camYaw = (float) Math.atan2(-mv.x, -mv.z);
                    player.camPitch = 0f;
                }
                in.joyY = 1f;
                in.joyX = 0f;
                in.fire = false;
            } else if ((pat == 1 || pat == 2) && L < 35f) {
                // charge telegraph / charging: strafe across the charge line
                float side = (int) (t / 2f) % 2 == 0 ? 1f : -1f; // hold the side — fast flips cancel out
                in.joyX = 0.95f * side;
                in.joyY = 0f;
                in.fire = true;
                in.aim = false;
            } else if (L < 6f) {
                in.joyX = 0; in.joyY = -1f; // break away from melee range
                in.fire = true;
                in.aim = false;
            } else if (L < 14f) {
                // hard no-fly zone: charges telegraph at 11 m — full radial out
                float side = (int) (t / 2f) % 2 == 0 ? 1f : -1f;
                in.joyX = 0.4f * side; in.joyY = -1f;
                in.fire = true;
                in.aim = false;
            } else if (L < 20f) {
                // medium retreat, then re-snipe
                float side = (int) (t / 2f) % 2 == 0 ? 1f : -1f; // hold the side — fast flips cancel out
                in.joyX = 0.5f * side; in.joyY = -0.6f;
                in.fire = true;
                in.aim = L > 16f;
            } else {
                // 20-35 m firing band: strafe with outward drift (see playMission)
                float side = (int) (t / 2f) % 2 == 0 ? 1f : -1f; // hold the side — fast flips cancel out
                in.joyX = 0.7f * side; in.joyY = -0.4f;
                in.fire = true;
                in.aim = L > 24f;
            }
            in.sprint = true;
            // stuck fallback: force a fresh path
            if (player.pos.dist(bPrev) < 0.05f) bStuckT += 1f / 60f; else bStuckT = 0f;
            bOsT += 1f / 60f;
            if (player.pos.dist(bOsP) > 1.5f) { bOsP.copy(player.pos); bOsT = 0f; }
            if (bOsT > 3f) { bStuckT = 2f; bOsT = 0f; bOsP.copy(player.pos); }
            if (bStuckT > 1.5f) {
                bStuckT = 0f;
                pPath = null;
                pPathT = 0f;
            }
            if (player.dead && resupplies < 12) { // sim-only: keep the test character alive
                player.dead = false;
                player.hp = player.maxHp * 0.6f;
                resupplies++;
                Vec3 away = new Vec3(player.pos.x - boss.pos.x, 0, player.pos.z - boss.pos.z);
                float al = away.len();
                if (al < 0.5f) away.set(1, 0, 0); else away.mul(1f / al);
                player.pos.addScaled(away, 6f);
                world.resolve(player.pos, 0.5f); // never resurrect inside geometry
            }
            if (player.hp < 45f && save.meds > 0) player.useMed(this);
            player.update(1f / 60f, in, this);
            for (Zombie z : zs) if (z.alive) z.update(1f / 60f, this);
            flushSpawns();
            mission.update(1f / 60f);
            flushSpawns();
            fx.update(1f / 60f);
            t += 1f / 60f;
            if (boss.phase2) sawPhase2 = true;
            if (boss.bPat != lastPat) { patternsSeen++; lastPat = boss.bPat; if (boss.bPat != 0) sawPattern = true; }
            summons = Math.max(summons, zs.size());
        }
        check(!boss.alive, "colossus defeated at t=" + (int) t + "s (resupplies " + resupplies + ")");
        check(sawPattern, "boss used special patterns (" + patternsSeen + " transitions)");
        check(sawPhase2, "boss phase change at 50%");
        check(summons > 1, "boss summoned adds (" + summons + " zombies present)");
        check(mission.bossDead, "boss objective marked done");
        // evidence now usable
        boolean foundEvidence = false;
        for (MissionMgr.InteractPoint p : mission.pts)
            if (p.t.equals("evidence")) {
                player.pos.copy(p.pos);
                playerInteract();
                if (p.used) foundEvidence = true;
            }
        check(foundEvidence, "evidence cache usable after boss death");
    }

    // ---------------- campaign ----------------

    private Zombie findBoss() {
        for (Zombie z : zs) if (z.boss) return z;
        return null;
    }

    /** Mirrors Game.missionWon: session ammo rolls into save, rewards + first-clear weapon. */
    private void applyRewards(MissionMgr m) {
        SaveData sv = save;
        sv.ammo = sessionAmmo.clone();
        int mi = m.m.id;
        boolean firstClear = !sv.done[mi - 1];
        sv.scrap += m.m.rewardScrap + m.scrapGained;
        sv.parts += m.m.rewardParts + m.partsGained;
        sv.meds += m.m.rewardMeds;
        sv.addXp(m.m.rewardXp + m.kills * 3);
        if (firstClear) sv.done[mi - 1] = true;
        if (firstClear && m.m.rewardWeapon != null) {
            SaveData.WSave ws = sv.wsave(m.m.rewardWeapon);
            if (!ws.own) {
                ws.own = true;
                int ai = DataLib.ammoIndex(data.w(m.m.rewardWeapon).ammoType);
                sv.ammo[ai] += 30;
            }
        }
    }

    /** Pick the kill-hunt target: a chasing zombie nearby beats a far idle one. */
    private MissionMgr.Pickup nearestCrate(float maxD) {
        MissionMgr.Pickup best = null; float bd = maxD;
        for (MissionMgr.Pickup pk : mission.loot) {
            if (pk.taken || !pk.t.startsWith("ammo_")) continue;
            float d = player.pos.dist(pk.pos);
            if (d < bd) { bd = d; best = pk; }
        }
        return best;
    }

    private Zombie pickHunt() {
        Zombie ch = null; float cd = 70f;
        for (Zombie z : zs) {
            if (!z.alive || z.state == Zombie.DYING) continue;
            if (z.state == Zombie.CHASE || z.state == Zombie.ATTACK) {
                float d = player.pos.dist(z.pos);
                if (d < cd) { cd = d; ch = z; }
            }
        }
        return ch != null ? ch : pickTarget(1e9f);
    }

    /** Combat target: a brute within 45 m (or maxD) beats the nearest other zombie. */
    /** Sidestep joy for corner escape (world axis 1:+x 2:-x 3:+z 4:-z, camera-relative). */
    private void applyDetourJoy(InputState in, int axis) {
        float ax = 0f, az = 0f;
        if (axis == 1) ax = 1f; else if (axis == 2) ax = -1f;
        else if (axis == 3) az = 1f; else az = -1f;
        float cy = (float) Math.cos(player.camYaw), sy = (float) Math.sin(player.camYaw);
        in.joyY = 0.8f * (ax * (-sy) + az * (-cy));
        in.joyX = 0.8f * (ax * cy + az * (-sy));
    }

    private int countNear(float d) {
        int n = 0;
        for (Zombie z : zs)
            if (z.alive && z.state != Zombie.DYING && !z.boss && player.pos.dist(z.pos) < d) n++;
        return n;
    }

    private Zombie pickTarget(float maxD) {
        Zombie brute = null; float bd = 1e30f;
        Zombie best = null; float bb = maxD;
        for (Zombie z : zs) {
            if (!z.alive || z.state == Zombie.DYING || z.boss) continue;
            float d = player.pos.dist(z.pos);
            if (d >= maxD) continue;
            if (z.d.id.equals("brute") && d < 45f && d < bd) { bd = d; brute = z; }
            if (d < bb) { bb = d; best = z; }
        }
        return brute != null ? brute : best;
    }

    /** Safehouse shopping: survivor stats (scrap) first, then weapon upgrades
     *  (scrap + parts). Mirrors Game.upgradeStat / Game.upgradeWeapon costs. */
    private void shop() {
        int statBought = 0, wpnBought = 0;
        int[] prio = {0, 2, 3, 1}; // health, reload, damage, speed
        for (int pass = 0; pass < 10; pass++) {
            boolean got = false;
            for (int which : prio) {
                int cur = which == 0 ? save.stH : which == 1 ? save.stSp : which == 2 ? save.stR : save.stD;
                if (cur >= SaveData.statMax(which)) continue;
                int cost = save.statCost(cur);
                if (save.scrap >= cost + 120) {
                    save.scrap -= cost;
                    if (which == 0) save.stH++;
                    else if (which == 1) save.stSp++;
                    else if (which == 2) save.stR++;
                    else save.stD++;
                    statBought++; got = true;
                }
                if (got) break;
            }
            if (!got) break;
        }
        String[] ws = { "rifle", "smg", "shotgun", "sniper" };
        for (int pass = 0; pass < 15; pass++) {
            boolean got = false;
            for (String w : ws) {
                if (!save.owned(w)) continue;
                for (int a = 0; a < 3 && !got; a++) {
                    int cur = save.wLevel(w, a);
                    if (cur >= data.upgCost[a].length) continue;
                    int[] c = data.upgCost[a][cur];
                    if (save.scrap >= c[0] && save.parts >= c[1]) {
                        save.scrap -= c[0]; save.parts -= c[1];
                        save.wUpgrade(w, a);
                        wpnBought++; got = true;
                    }
                }
                if (got) break;
            }
            if (!got) break;
        }
        if (statBought > 0 || wpnBought > 0)
            System.out.println("  [shop] +" + statBought + " stat (maxHp " + save.maxHp() + "), +"
                    + wpnBought + " weapon lvl | left scrap " + save.scrap + ", parts " + save.parts
                    + ", meds " + save.meds);
    }

    private void runCampaign() {
        bossOnlySpawns = false;
        save = new SaveData();
        save.reset();
        save.equip = "rifle";
        int cleared = 0;
        for (int i = 0; i < data.missions.length; i++) {
            MissionData md = data.missions[i];
            boolean won = false;
            for (int attempt = 0; attempt < (md.boss ? 5 : 4) && !won; attempt++) { // the game allows safehouse retries
                if (attempt > 0) { System.out.println("  [retry] mission " + md.id); shop(); }
                won = playMission(md);
            }
            if (won) {
                cleared++;
                applyRewards(mission);
                shop();
                System.out.println("  [m" + md.id + "] cleared in " + (int) mission.t + "s, "
                        + mission.kills + " kills, hp=" + (int) player.hp
                        + " -> lvl " + save.level + ", scrap " + save.scrap + ", meds " + save.meds);
            } else {
                System.out.println("  [m" + md.id + "] FAILED state=" + mission.state
                        + " t=" + (int) mission.t + " kills=" + mission.kills
                        + " hp=" + (int) player.hp + " dead=" + player.dead);
            }
            check(won, "mission " + md.id + " " + md.name + " completed");
        }
        check(cleared == data.missions.length, "campaign: all " + data.missions.length + " missions cleared (progression to lvl " + save.level + ")");
        check(save.owned("sniper") && save.owned("shotgun"), "campaign: reward weapons earned");
        check(save.unlocked(data.missions.length - 1), "all chapters unlocked");
    }

    /** Plays one mission to completion with a generic objective bot.
     *  No resupply cheats: if the character dies, the mission fails. */
    private boolean playMission(MissionData md) {
        setupMission(md);
        pPath = null; pPathIdx = 0; pPathT = 0f;
        InputState in = new InputState();
        float t = 0f;
        Zombie hunt = null; float huntT = 0f;
        MissionMgr.InteractPoint ptGoal = null; float ptT = 0f;
        float stuckT = 0f;
        float detT = 0f;
        float osProgT = 0f; Vec3 osPos = player.pos.clone();
        int detAxis = 0, nextAxis = 1;
        Vec3 prev = player.pos.clone();
        Vec3 lastGoal = null;
        float lastGD = 0f, noProgT = 0f;
        float timeout = md.boss ? 1000f : (md.id == 1 || md.id == 3 ? 900f : 800f);
        while (t < timeout && mission.state == MissionMgr.RUN && !player.dead) {
            float dt = 1f / 60f;
            in.clearEdges();
            in.crouch = false;
            in.aim = false;
            Zombie boss = findBoss();
            boolean bossAlive = boss != null && boss.alive && boss.state != Zombie.DYING;

            // weapon management: AR primary; SMG when 5.56 dry; sniper on the boss at range
            if (player.cur == 0 && player.mags[0] + sessionAmmo[0] < 30
                    && player.mags[1] + sessionAmmo[1] > 40) {
                player.cur = 1; player.reloadT = 0;
            } else if (player.cur == 1 && player.mags[1] + sessionAmmo[1] < 20
                    && player.mags[0] + sessionAmmo[0] > 30) {
                player.cur = 0; player.reloadT = 0;
            } else if (bossAlive && save.owned("sniper")
                    && player.mags[3] + sessionAmmo[3] > 15
                    && boss.state != Zombie.IDLE // never gear up before it is actually coming
                    && player.pos.dist(boss.pos) > 14f
                    && player.pos.dist(boss.pos) < 60f) {
                player.cur = 3; player.reloadT = 0;
            } else if (player.cur == 3 && (player.mags[3] + sessionAmmo[3] < 5
                    || player.pos.dist(boss.pos) <= 9f)) {
                player.cur = player.mags[0] + sessionAmmo[0] > 10 ? 0 : 1;
                player.reloadT = 0;
            }
            // last resort: if the held weapon is dry, switch to whatever has the most ammo
            int aiHeld = DataLib.ammoIndex(data.weapons[player.cur].ammoType);
            if (player.mags[player.cur] + sessionAmmo[aiHeld] <= 1) {
                int bestW = -1, bestA = 0;
                for (int i = 0; i < 4; i++) {
                    if (!save.owned(data.weapons[i].id)) continue;
                    int tot = player.mags[i] + sessionAmmo[DataLib.ammoIndex(data.weapons[i].ammoType)];
                    if (tot > bestA) { bestA = tot; bestW = i; }
                }
                if (bestW >= 0 && bestW != player.cur) { player.cur = bestW; player.reloadT = 0; }
            }
            // crowd control: shotgun when swarmed at close range
            int near6 = countNear(6f);
            int near8 = countNear(8f);
            if ((near8 >= 2 || near6 >= 3) && player.cur != 2 && save.owned("shotgun")
                    && player.mags[2] + sessionAmmo[2] > 4) {
                player.cur = 2; player.reloadT = 0;
            } else if (player.cur == 2 && (near6 < 2 || player.mags[2] + sessionAmmo[2] < 2)) {
                // back to whichever of rifle/SMG actually has rounds
                player.cur = (player.mags[0] + sessionAmmo[0]) >= (player.mags[1] + sessionAmmo[1]) ? 0 : 1;
                player.reloadT = 0;
            }

            // ---------- objective goal (decided first: it also drives advance-and-suppress) ----------
            Vec3 goal = null;
            boolean allDone = mission.allDone();
            int dry = 0;
            for (int i = 0; i < 4; i++) dry += player.mags[i] + sessionAmmo[i];
            int aiCur = DataLib.ammoIndex(data.weapons[player.cur].ammoType);
            int curTotal = player.mags[player.cur] + sessionAmmo[aiCur];
            boolean outOfAmmo = dry <= 6 || (curTotal <= 2 && dry - curTotal <= 10);
            if (outOfAmmo) {
                // panic: everything dry — sprint to the nearest ammo crate, don't fight
                float bd = 1e30f;
                for (MissionMgr.Pickup pk : mission.loot) {
                    if (pk.taken || !pk.t.startsWith("ammo_")) continue;
                    float d = player.pos.dist(pk.pos);
                    if (d < bd) { bd = d; goal = pk.pos; }
                }
            } else if (!allDone) {
                // boss down: top up before the evidence walk — the post-fight
                // walk is where dry, wounded runs go to die
                if (mission.bossDead) {
                    float bd2 = 60f; Vec3 pick = null;
                    boolean wantMed = player.hp < player.maxHp * 0.75f;
                    int totAmmo = 0;
                    for (int i = 0; i < 4; i++) totAmmo += player.mags[i] + sessionAmmo[i];
                    boolean wantAmmo = totAmmo < 25;
                    if (wantMed || wantAmmo) {
                        for (MissionMgr.Pickup pk : mission.loot) {
                            if (pk.taken) continue;
                            boolean useful = (wantMed && pk.t.equals("med"))
                                    || (wantAmmo && pk.t.startsWith("ammo_"));
                            if (!useful) continue;
                            float d = player.pos.dist(pk.pos);
                            if (d < bd2) { bd2 = d; pick = pk.pos; }
                        }
                    }
                    if (pick != null) goal = pick;
                }
                boolean reachPending = false;
                for (int i = 0; i < mission.objs.size(); i++) {
                    MissionData.Obj o = mission.objs.get(i);
                    if (o.t.equals("reach") && mission.objDone[i] < 1) {
                        reachPending = true;
                        Vec3 a = world.anchor(o.anchor);
                        if (a != null) goal = a;
                    }
                }
                if (goal == null) {
                    if (ptGoal == null || ptGoal.used || t - ptT > 60f) {
                        ptGoal = null;
                        float bd = 1e30f;
                        for (MissionMgr.InteractPoint p : mission.pts) {
                            if (p.used) continue;
                            if (p.gate != null && p.gate.equals("boss") && !mission.bossDead) continue;
                            float d = player.pos.dist(p.pos);
                            if (d < bd) { bd = d; ptGoal = p; }
                        }
                        ptT = t;
                    }
                    if (ptGoal != null) goal = ptGoal.pos;
                }
                // opportunistic resupply: current weapon below ~1.5 mags and a
                // matching crate is nearby — top up BEFORE running dry
                if (goal == null) {
                    int aiNow = DataLib.ammoIndex(data.weapons[player.cur].ammoType);
                    if (player.mags[player.cur] + sessionAmmo[aiNow] < 45) {
                        float bd = 30f;
                        for (MissionMgr.Pickup pk : mission.loot) {
                            if (pk.taken || !pk.t.startsWith("ammo_")) continue;
                            if (DataLib.ammoIndex(pk.t.substring(5)) != aiNow) continue;
                            float d = player.pos.dist(pk.pos);
                            if (d < bd) { bd = d; goal = pk.pos; }
                        }
                    }
                }
                // resupply: if the rifle or SMG is running dry, grab matching ammo/med loot
                if (goal == null) {
                    boolean rifleDry = player.mags[0] + sessionAmmo[0] < 35;
                    boolean smgDry = player.mags[1] + sessionAmmo[1] < 35;
                    boolean curDry = player.mags[player.cur] + sessionAmmo[
                            DataLib.ammoIndex(data.weapons[player.cur].ammoType)] < 35;
                    if (rifleDry || smgDry || curDry) {
                        float bd = 1e30f;
                        for (MissionMgr.Pickup pk : mission.loot) {
                            if (pk.taken) continue;
                            boolean useful = false;
                            if (pk.t.startsWith("ammo_")) {
                                int ai = DataLib.ammoIndex(pk.t.substring(5));
                                if (ai == 0 && !rifleDry) continue;
                                if (ai == 1 && !smgDry) continue;
                                useful = true;
                            } else if (pk.t.equals("med") && (player.hp < 70f || save.meds == 0)) {
                                useful = true;
                            }
                            if (!useful) continue;
                            float d = player.pos.dist(pk.pos);
                            if (d < bd) { bd = d; goal = pk.pos; }
                        }
                    }
                }
                if (goal == null) {
                    boolean killPending = false;
                    for (int i = 0; i < mission.objs.size(); i++) {
                        MissionData.Obj o = mission.objs.get(i);
                        if (o.t.equals("kill") && mission.objDone[i] < o.n) killPending = true;
                    }
                        if (killPending) {
                            if (hunt == null || hunt.state == Zombie.DYING || !hunt.alive || t - huntT > 30f) {
                                hunt = pickHunt();
                                huntT = t;
                            }
                            if (hunt != null) goal = hunt.pos;
                        }
                }
                if (goal == null && bossAlive) goal = boss.pos; // go look for the boss
            } else {
                goal = mission.extract;
                hunt = null; ptGoal = null;
            }

            // ---------- fire target: boss (LOS) or the goal zombie, else any close add ----------
            Zombie target = null;
            float aimY = 0f;
            if (bossAlive) {
                float bL = player.pos.dist(boss.pos);
                // hold fire beyond the boss's own detection radius: waking the
                // colossus from afar starts a charge we cannot outpace — meet
                // it at the arena, where the ammo crates are
                if (bL < 40f && world.los(player.pos, boss.pos)) { target = boss; aimY = boss.pos.y + 1.55f * boss.d.scale; }
            }
            if (target == null && hunt != null && goal == hunt.pos) {
                // advance-and-suppress: shoot the hunted target while closing
                if (hunt.alive && hunt.state != Zombie.DYING && player.pos.dist(hunt.pos) < 38f
                        && world.los(player.pos, hunt.pos)) {
                    target = hunt;
                    aimY = hunt.pos.y + 1.55f * hunt.d.scale;
                }
            }
            // no-progress escape: sliding around a wall without closing on the goal
            if (goal != lastGoal) { lastGoal = goal; lastGD = 1e30f; noProgT = 0f; }
            if (goal != null) {
                float gdNow = player.pos.dist(goal);
                if (gdNow > lastGD - 0.05f) noProgT += dt; else noProgT = 0f;
                lastGD = gdNow;
                if (noProgT > 2.5f && detT <= 0f) {
                    noProgT = 0f; pPath = null; pPathT = 0f;
                    detAxis = nextAxis; nextAxis = nextAxis % 4 + 1; detT = 2.5f;
                }
            }
            if (outOfAmmo) target = null;
            boolean pointRun = ptGoal != null && player.pos.dist(ptGoal.pos) < 30f && !outOfAmmo;
            // sprinting to an unengaged boss: no kill objective, limited ammo —
            // fight only what is point-blank and keep running for the arena
            boolean bossApproach = bossAlive && goal == boss.pos && player.pos.dist(boss.pos) > 38f;
            // dry-gun panic run: commit to the crate, fight only what is
            // already touching you
            boolean panicRun = outOfAmmo && goal != null;
            boolean pushObj = goal != null && player.pos.dist(goal) > 25f;
            if (target == null && !pointRun) {
                Zombie near = pickTarget(panicRun ? 5f : bossApproach ? 8f : (pushObj ? 12f : 16f));
                if (near != null && world.los(player.pos, near.pos)) {
                    target = near;
                    aimY = near.pos.y + 1.55f * near.d.scale;
                }
            }

            if (pointRun) {
                // supply-point push: run for the point under fire; shoot point-blank threats only
                if (detT > 0f) {
                    applyDetourJoy(in, detAxis);
                } else {
                    Vec3 mv = pathDir(world, ptGoal.pos);
                    player.camYaw = (float) Math.atan2(-mv.x, -mv.z);
                    player.camPitch = 0f;
                    in.joyY = 1f; in.joyX = 0f;
                }
                in.aim = false;
                in.fire = false;
                if (player.pos.dist(ptGoal.pos) < 3.0f) in.interactTap = true;
                Zombie nb = nearestZombie(6f);
                if (nb != null && world.los(player.pos, nb.pos)) {
                    float nL = Math.max(0.1f, player.pos.dist(nb.pos));
                    player.camYaw = (float) Math.atan2(-(nb.pos.x - player.pos.x) / nL,
                            -(nb.pos.z - player.pos.z) / nL);
                    player.camPitch = 0f;
                    in.fire = true;
                }
                in.sprint = true;
            } else if (target != null) {
                float tdx = target.pos.x - player.pos.x, tdz = target.pos.z - player.pos.z;
                float tL = (float) Math.sqrt(tdx * tdx + tdz * tdz);
                player.camYaw = (float) Math.atan2(-tdx / tL, -tdz / tL);
                player.camPitch = (float) Math.max(-0.15f,
                        Math.min(0.8f, (float) Math.atan2(aimY - 1.40f, tL)));
                // burst fire: short rest between bursts lets spread bloom decay
                in.fire = ((t * 1.8f) % 1f) < 0.65f;
                if (target == boss && player.cur != 3 && save.owned("sniper")
                        && player.mags[3] + sessionAmmo[3] > 0 && tL > 12f && tL < 60f) {
                    player.cur = 3; player.reloadT = 0; // the .338 is the boss-killer
                }
                if (target == boss) {
                    Vec3 st = world.anchor("station");
                    if (st != null && player.pos.dist(st) > 35f && tL < 45f && tL > 15f) {
                        // beeline toward the station (ammo + meds), let the boss chase
                        float sx = st.x - player.pos.x, sz = st.z - player.pos.z;
                        float sl = (float) Math.sqrt(sx * sx + sz * sz);
                        if (sl > 0.1f) {
                            float cy2 = (float) Math.cos(player.camYaw), sy2 = (float) Math.sin(player.camYaw);
                            in.joyY = 0.8f * (sx / sl * (-sy2) + sz / sl * (-cy2));
                            in.joyX = 0.8f * (sx / sl * cy2 + sz / sl * (-sy2));
                        }
                        in.aim = false;
                    } else if ((boss.bPat == 1 || boss.bPat == 2) && tL < 35f) {
                        // charge telegraph / charging: strafe across the charge line
                        float side = (int) (t / 2f) % 2 == 0 ? 1f : -1f; // hold the side — fast flips cancel out
                        in.joyX = 0.95f * side; in.joyY = 0f;
                        in.aim = false;
                    } else if (tL < 6f) {
                        in.joyX = 0f; in.joyY = -1f; // break away: inside melee range
                        in.aim = false;
                    } else if (tL < 14f) {
                        // hard no-fly zone: charges telegraph at 11 m — lingering
                        // here is how runs end. Full radial sprint out.
                        float side = (int) (t / 2f) % 2 == 0 ? 1f : -1f;
                        in.joyX = 0.4f * side; in.joyY = -1f;
                        in.aim = false;
                    } else if (tL < 20f) {
                        // medium retreat, then re-snipe
                        float side = (int) (t / 2f) % 2 == 0 ? 1f : -1f; // hold the side — fast flips cancel out
                        in.joyX = 0.5f * side; in.joyY = -0.6f;
                        in.aim = tL > 16f;
                    } else if (tL > 32f) {
                        // hold and plink — closing into charge range is how runs end
                        float side = (int) (t / 2f) % 2 == 0 ? 1f : -1f;
                        in.joyX = 0.6f * side; in.joyY = 0f;
                        in.aim = true;
                    } else {
                        // 20-32 m firing band: strafe with outward drift. Boss p1
                        // closes 2.7 m/s, p2 3.5 — band bleeds to 20, kites back.
                        // Orbit toward the nearest ammo crate so the fight itself
                        // carries us onto a resupply (auto-pickup at 1.4 m).
                        float side = (int) (t / 2f) % 2 == 0 ? 1f : -1f; // hold the side — fast flips cancel out
                        MissionMgr.Pickup crate = nearestCrate(75f);
                        if (crate != null) {
                            float ax = player.pos.x - boss.pos.x, az = player.pos.z - boss.pos.z;
                            float al2 = (float) Math.max(0.01f, Math.sqrt(ax * ax + az * az));
                            float px = -az / al2, pz = ax / al2; // left of away-from-boss
                            float dot = px * (crate.pos.x - player.pos.x) + pz * (crate.pos.z - player.pos.z);
                            side = dot >= 0 ? 1f : -1f;
                        }
                        in.joyX = 0.7f * side; in.joyY = -0.4f;
                        in.aim = true; // ADS costs 40% speed — never while the boss is close
                    }
                } else if (bossApproach || panicRun) {
                    // keep running the objective; shoot over the shoulder —
                    // stopping to fight stragglers is what drains the loadout
                    // ...but grabbing a crate that sits ON the route is free ammo
                    int tot = player.mags[player.cur] + sessionAmmo[player.cur];
                    if (tot < 45) {
                        Vec3 am = null; float ab = 30f;
                        for (MissionMgr.Pickup pk : mission.loot) {
                            if (pk.taken || !pk.t.startsWith("ammo_")) continue;
                            float d = player.pos.dist(pk.pos);
                            if (d < ab) { ab = d; am = pk.pos; }
                        }
                        if (am != null) goal = am;
                    }
                    Vec3 mv = goal != null ? pathDir(world, goal) : null;
                    if (detT > 0f && mv != null) {
                        applyDetourJoy(in, detAxis); // shared un-wallow kick
                    } else if (mv != null) {
                        player.camYaw = (float) Math.atan2(-mv.x, -mv.z);
                        player.camPitch = 0f;
                        in.joyY = 1f; in.joyX = 0f;
                    }
                    in.aim = tL < 12f;
                } else {
                    // ---- swarm pressure: zombies within 8 m ----
                    int swarm = 0;
                    Vec3 cen = new Vec3();
                    for (Zombie z : zs) {
                        if (!z.alive || z.state == Zombie.DYING || z.boss) continue;
                        if (player.pos.dist(z.pos) < 8f) {
                            swarm++;
                            cen.x += z.pos.x; cen.y += z.pos.y; cen.z += z.pos.z;
                        }
                    }
                    if (swarm > 0) cen.mul(1f / swarm);
                    if (swarm >= 2) {
                        // kite: run from the swarm centroid, keep firing
                        Vec3 away = player.pos.clone().sub(cen);
                        away.y = 0;
                        if (away.len() < 0.5f) away.set(1, 0, 0);
                        away.norm();
                        float cy = (float) Math.cos(player.camYaw), sy = (float) Math.sin(player.camYaw);
                        in.joyY = away.x * (-sy) + away.z * (-cy);
                        in.joyX = away.x * cy + away.z * (-sy);
                        in.aim = false; in.crouch = false;
                    } else if (tL < 10f) {
                        // point blank: crouch + burst strafe (never stand still)
                        in.crouch = true; in.aim = false;
                        float side = (int) (t * 3f) % 2 == 0 ? 1f : -1f;
                        in.joyX = 0.35f * side; in.joyY = 0f;
                    } else if (tL < 26f && !pushObj) {
                        // effective range: ADS + continuous strafe
                        in.crouch = false; in.aim = true;
                        float side = (int) (t * 2f) % 2 == 0 ? 1f : -1f;
                        in.joyX = 0.55f * side; in.joyY = 0f;
                    } else if (goal != null && player.pos.dist(goal) > 4f) {
                        // long range: run-and-gun on the objective at half speed
                        in.crouch = false; in.aim = tL < 22f;
                        in.fire = tL < 22f; // do not spray across the map
                        Vec3 mv = pathDir(world, goal);
                        float cy = (float) Math.cos(player.camYaw), sy = (float) Math.sin(player.camYaw);
                        float jy = mv.x * (-sy) + mv.z * (-cy);
                        float jx = mv.x * cy + mv.z * (-sy);
                        float ml = (float) Math.sqrt(jx * jx + jy * jy);
                        if (ml > 0.01f) { in.joyX = 0.5f * jx / ml; in.joyY = 0.5f * jy / ml; }
                        else { in.joyX = 0; in.joyY = 0; }
                    } else {
                        // no objective in view: hold and strafe
                        in.crouch = false; in.aim = tL > 9f;
                        float side = (int) (t * 2f) % 2 == 0 ? 1f : -1f;
                        in.joyX = 0.4f * side; in.joyY = 0f;
                    }
                    if (detT > 0f) applyDetourJoy(in, detAxis); // corner escape, even mid-fight
                }
                in.sprint = true;
            } else {
                in.fire = false;
                // ---------- move toward the objective goal ----------
                if (goal != null) {
                    float gd = player.pos.dist(goal);
                    if (detT > 0f) {
                        applyDetourJoy(in, detAxis);
                    } else {
                        Vec3 mv = gd > 1.2f ? pathDir(world, goal) : null;
                        if (mv != null) {
                            player.camYaw = (float) Math.atan2(-mv.x, -mv.z);
                            player.camPitch = 0f;
                            in.joyY = 1f; in.joyX = 0f;
                        } else { in.joyX = 0; in.joyY = 0; }
                    }
                    if (ptGoal != null && gd < 3.0f) in.interactTap = true;
                }
                in.sprint = true;
            }

            float medThresh = countNear(7f) >= 2 ? 0.62f : 0.45f;
            if (player.hp < player.maxHp * medThresh && save.meds > 0) {
                save.meds--;
                player.useMed(this);
            }
            boolean wantsMove = (in.joyX != 0f || in.joyY != 0f);
            // two-layer stuck detection: frozen in place, or wallowing with no
            // net progress (oscillation defeats the per-tick check)
            if (wantsMove && player.pos.dist(prev) < 0.05f) stuckT += dt; else stuckT = 0f;
            osProgT += dt;
            if (player.pos.dist(osPos) > 1.5f) { osPos.copy(player.pos); osProgT = 0f; }
            if ((stuckT > 1.5f || osProgT > 3f) && detT <= 0f) {
                stuckT = 0f; osProgT = 0f; osPos.copy(player.pos);
                pPath = null; pPathT = 0f;
                detAxis = nextAxis; nextAxis = nextAxis % 4 + 1; detT = 2.5f;
            }
            if (detT > 0f) detT -= dt;
            prev.copy(player.pos);

            player.update(dt, in, this);
            for (Zombie z : zs) if (z.alive) z.update(dt, this);
            flushSpawns();
            mission.update(dt);
            flushSpawns();
            fx.update(dt);
            if ((int) (t / 10f) != (int) ((t - dt) / 10f))
                System.out.println("    t=" + (int) t + "s hp=" + (int) player.hp + "/" + player.maxHp
                        + " 5.56=" + sessionAmmo[0] + " 9mm=" + sessionAmmo[1]
                        + " kills=" + mission.kills + "  " + objSummary()
                        + "  pos=(" + (int) player.pos.x + "," + (int) player.pos.z + ")"
                        + (goal != null ? " goal=(" + (int) goal.x + "," + (int) goal.z + ")"
                                + " d=" + (int) player.pos.dist(goal)
                                + " path=" + (pathDir(world, goal) == null ? "NULL" : "ok") : " goal=-")
                        + (ptGoal != null ? " pt=(" + (int) ptGoal.pos.x + "," + (int) ptGoal.pos.z + ")"
                                + " pd=" + (int) player.pos.dist(ptGoal.pos) : "")
                        + " fire=" + (in.fire ? 1 : 0) + " joy=("
                        + (int) (in.joyX * 100) + "," + (int) (in.joyY * 100) + ")"
                        + (target != null ? " tgt=" + target.d.id + "/" + (int) player.pos.dist(target.pos) : " tgt=-")
                        + (boss != null && boss.alive ? " BH=" + (int) boss.hp + "/S" + boss.state + "/P" + boss.bPat : "")
                        + " n10=" + countNear(10f) + " bloom=" + (int) (player.spreadBloom * 100)
                        + " cur=" + player.cur + " mag=" + player.mags[player.cur]);
            t += dt;
        }
        if (player.dead)
            System.out.println("    [death] t=" + (int) t + "s pos=(" + (int) player.pos.x + ","
                    + (int) player.pos.z + ") kills=" + mission.kills + " " + objSummary());
        return mission.state == MissionMgr.WON;
    }

    private String objSummary() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mission.objs.size(); i++) {
            MissionData.Obj o = mission.objs.get(i);
            sb.append(o.t).append(':').append((int) mission.objDone[i]).append('/').append(o.n).append(' ');
        }
        return sb.toString().trim();
    }

    private void runEnemyBehaviors() {
        setupMission(data.missions[0]);
        // place a screamer near the player, kill it in 2 shots; expect aggro on others
        Vec3 p = player.pos;
        Zombie scream = new Zombie();
        scream.spawn(new Vec3(p.x + 8, 0, p.z), data.e("screamer"));
        zs.add(scream);
        Zombie walker = new Zombie();
        walker.spawn(new Vec3(p.x - 6, 0, p.z + 6), data.e("walker"));
        zs.add(walker);
        Zombie brute = new Zombie();
        brute.spawn(new Vec3(p.x + 6, 0, p.z - 6), data.e("brute"));
        zs.add(brute);
        Zombie runner = new Zombie();
        runner.spawn(new Vec3(p.x - 8, 0, p.z - 8), data.e("runner"));
        zs.add(runner);

        InputState in = new InputState();
        // let them all detect & chase for 3s
        for (int i = 0; i < 180; i++) {
            in.clearEdges();
            in.fire = false; in.sprint = false;
            player.update(1f / 60f, in, this);
            for (Zombie z : zs) if (z.alive) z.update(1f / 60f, this);
            fx.update(1f / 60f);
        }
        check(walker.state == Zombie.CHASE, "walker chases player");
        check(runner.state == Zombie.CHASE, "runner chases player");
        check(brute.state == Zombie.CHASE, "brute chases player");
        check(scream.state == Zombie.CHASE || scream.state == Zombie.ATTACK, "screamer engages");
        // kill the screamer -> everyone still alert; check brute takes multiple hits
        float bruteBefore = brute.hp;
        brute.takeHit(10f, false, player.pos, this);
        check(brute.hp < bruteBefore && brute.alive, "brute survives single hit (tanky)");
        for (int i = 0; i < 60; i++) brute.takeHit(10f, false, player.pos, this);
        check(!brute.alive || brute.state == Zombie.DYING, "brute dies to sustained damage (state " + brute.state + ")");
        check(mission.kills == 1, "kill credited to mission (" + mission.kills + ")");
        // player damage from attacks
        float hp0 = player.hp;
        player.damage(12f, p, this);
        check(player.hp == hp0 - 12f, "player takes damage");
        // death
        player.damage(9999f, p, this);
        check(player.dead, "player death state");
    }
}
