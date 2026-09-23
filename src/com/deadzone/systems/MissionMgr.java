package com.deadzone.systems;

import com.deadzone.core.Host;
import com.deadzone.data.DataLib;
import com.deadzone.data.MissionData;
import com.deadzone.entities.Zombie;
import com.deadzone.gl.Renderer;
import com.deadzone.gl.Vec3;
import com.deadzone.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Mission runtime: objectives, timed waves, loot pickups, interact points
 * (evidence / supplies / notes), extraction, win/lose. Definitions come from
 * missions.json — adding a mission (or a whole new region) is data only.
 */
public class MissionMgr {
    public static final int RUN = 0, WON = 1, LOST = 2;

    public static class Pickup {
        public Vec3 pos = new Vec3();
        public String t;
        public int n;
        public float seed;
        public boolean taken;
    }

    public static class InteractPoint {
        public Vec3 pos = new Vec3();
        public String t;
        public String label;
        public String text;
        public boolean used;
        public String gate; // e.g. "boss" — only usable once condition met
    }

    public MissionData m;
    public Host h;
    public float t;
    public int kills;
    public int state = RUN;
    public List<MissionData.Obj> objs = new ArrayList<MissionData.Obj>();
    public int[] objDone = new int[0];
    public List<MissionData.Wave> pending = new ArrayList<MissionData.Wave>();
    public int waveTotal;
    public int waveHit;
    public List<Pickup> loot = new ArrayList<Pickup>();
    public List<InteractPoint> pts = new ArrayList<InteractPoint>();
    public Vec3 extract = new Vec3();
    public boolean bossSpawned, bossDead;
    private boolean exWarned;
    public String banner;
    public float bannerT;
    public int scrapGained, partsGained;
    public float lastTrickle;
    private final Random rnd = new Random();

    public void start(MissionData md, Host host) {
        m = md;
        h = host;
        t = 0; kills = 0; state = RUN;
        scrapGained = 0; partsGained = 0;
        exWarned = false;
        bossSpawned = false; bossDead = false;
        banner = null; bannerT = 0; lastTrickle = 0;
        objs.clear();
        for (MissionData.Obj o : md.objectives) objs.add(o);
        objDone = new int[objs.size()];
        pending = new ArrayList<MissionData.Wave>(md.waves);
        Collections.sort(pending, new java.util.Comparator<MissionData.Wave>() {
            public int compare(MissionData.Wave a, MissionData.Wave b) {
                return Float.compare(a.at, b.at);
            }
        });
        waveTotal = 0; waveHit = 0;
        for (MissionData.Wave w : pending) if (w.wave) waveTotal++;

        World w = host.world();
        // ambient spawns — ring starts at 30 m so nothing aggros at t=0
        for (String type : m.ambient.keySet()) {
            int n = m.ambient.get(type);
            for (int i = 0; i < n; i++) {
                Vec3 p = w.spawnPoint(w.anchor("spawn"), 30, 65, rnd);
                if (p != null) host.spawnZombie(type, p);
            }
        }
        // loot
        loot.clear();
        for (MissionData.Loot l : m.loot) {
            Pickup p = new Pickup();
            Vec3 a = w.anchor(l.at);
            if (a == null) a = w.anchor("spawn");
            p.pos.set(a.x + l.off[0] + (rnd.nextFloat() - 0.5f) * 10f, 0, a.z + l.off[1] + (rnd.nextFloat() - 0.5f) * 10f);
            int tries = 0;
            while (w.insideSolid(p.pos) && tries < 8) {
                p.pos.set(a.x + l.off[0] + (rnd.nextFloat() - 0.5f) * 10f, 0, a.z + l.off[1] + (rnd.nextFloat() - 0.5f) * 10f);
                tries++;
            }
            p.t = l.t;
            p.n = l.n;
            p.seed = rnd.nextFloat() * 100;
            Vec3 rp = w.approachPoint(p.pos, 12f); // keep loot walkable-to
            p.pos.set(rp.x, 0f, rp.z);
            loot.add(p);
        }
        // interact points
        pts.clear();
        for (MissionData.Point p : m.points) {
            InteractPoint ip = new InteractPoint();
            Vec3 a = w.anchor(p.anchor);
            if (a == null) a = w.anchor("spawn");
            ip.pos.set(a.x + p.off[0], 0, a.z + p.off[1]);
            if (w.insideSolid(ip.pos)) ip.pos.set(a.x, 0, a.z); // never bury an objective in a building
            Vec3 rp = w.approachPoint(ip.pos, 12f); // and never pocket it away from the streets
            ip.pos.set(rp.x, 0f, rp.z);
            ip.t = p.t;
            ip.label = p.label;
            ip.text = p.text;
            if (p.t.equals("evidence") && m.boss) ip.gate = "boss";
            pts.add(ip);
        }
        Vec3 ex = w.anchor(m.extractAnchor);
        if (ex == null) ex = w.anchor("camp");
        extract.copy(ex);
    }

    public void update(float dt) {
        if (state != RUN) return;
        t += dt;
        World w = h.world();
        Vec3 pp = h.player().pos;
        // waves
        for (int i = pending.size() - 1; i >= 0; i--) {
            MissionData.Wave wv = pending.get(i);
            if (wv.at <= t) {
                pending.remove(i);
                for (String type : wv.counts.keySet()) {
                    int n = wv.counts.get(type);
                    for (int k = 0; k < n; k++) {
                        Vec3 p = w.spawnPoint(pp, 30, 52, rnd);
                        if (p != null) h.spawnZombie(type, p);
                    }
                }
                if (wv.wave) {
                    waveHit++;
                    banner = "WAVE " + waveHit + " / " + waveTotal;
                    bannerT = 3f;
                    h.sfx("wave");
                }
            }
        }
        bannerT = Math.max(0f, bannerT - dt);
        // boss
        if (m.boss && !bossSpawned && t > 4f) {
            bossSpawned = true;
            Vec3 bp = w.anchor("station");
            h.spawnZombie(m.bossEnemy, new Vec3(bp.x, 0, bp.z));
            String bt = "colossus_alpha".equals(m.bossEnemy) ? "THE ALPHA COLOSSUS AWAKENS"
                    : "subject_zero".equals(m.bossEnemy) ? "SUBJECT ZERO BREAKS CONTAINMENT"
                    : "THE COLOSSUS AWAKENS";
            h.toast(bt);
            h.sfx("boss_roar");
        }
        // ambient trickle
        lastTrickle += dt;
        int ambTotal = 0;
        for (int n : m.ambient.values()) ambTotal += n;
        List<Zombie> zs = h.zombies();
        int alive = 0;
        for (Zombie z : zs) if (z.alive) alive++;
        if (alive < ambTotal && lastTrickle > 18f && t > 10f) {
            lastTrickle = 0;
            if (rnd.nextFloat() < 0.8f) {
                String type = m.ambient.keySet().iterator().next();
                for (String ty : m.ambient.keySet()) {
                    if (rnd.nextInt(10) < 6) type = ty;
                }
                Vec3 p = w.spawnPoint(pp, 45, 65, rnd);
                if (p != null) h.spawnZombie(type, p);
            }
        }
        // auto pickups
        for (Pickup p : loot) {
            if (p.taken) continue;
            if (p.pos.dist(pp) < 1.4f) {
                p.taken = true;
                applyPickup(p);
            }
        }
        // reach objectives
        for (int i = 0; i < objs.size(); i++) {
            MissionData.Obj o = objs.get(i);
            if (o.t.equals("reach")) {
                Vec3 a = w.anchor(o.anchor);
                if (a != null && pp.dist(a) < 6f) objDone[i] = 1;
            }
        }
        // survive objective
        for (int i = 0; i < objs.size(); i++) {
            MissionData.Obj o = objs.get(i);
            if (o.t.equals("survive") && waveHit >= o.n) objDone[i] = 1;
        }
        if (allDone() && m.autoEnd) win();
        // extraction: auto on entering the zone
        if (pp.dist(extract) < 4.5f) {
            if (allDone()) win();
            else if (!exWarned) {
                h.toast("OBJECTIVES INCOMPLETE");
                h.sfx("deny");
                exWarned = true;
            }
        } else {
            exWarned = false;
        }
    }

    private void applyPickup(Pickup p) {
        if (p.t.startsWith("ammo_")) {
            String type = p.t.substring(5);
            int idx = DataLib.ammoIndex(type);
            h.sessionAmmo()[idx] += p.n;
            h.toast("+" + p.n + " " + type + " ammo");
        } else if (p.t.equals("med")) {
            h.player().useMed(h);
        } else if (p.t.equals("scrap")) {
            scrapGained += p.n;
            h.toast("+" + p.n + " scrap");
        } else if (p.t.equals("parts")) {
            partsGained += p.n;
            h.toast("+" + p.n + " weapon parts");
        }
        h.sfx("pickup");
    }

    public void zombieKilled(Zombie z) {
        if (state != RUN) return;
        kills++;
        if (z.boss) {
            bossDead = true;
            h.fx().boom(z.pos);
            h.fx().ring(z.pos, 2f, 10f);
            h.toast("THE COLOSSUS HAS FALLEN");
        }
        int s = z.d.scrapMin + (int) (Math.random() * Math.max(1, z.d.scrapMax - z.d.scrapMin + 1));
        scrapGained += s;
        if (z.d.id.equals("brute") && Math.random() < 0.6f) {
            partsGained++;
            h.toast("+1 weapon part");
        }
        for (int i = 0; i < objs.size(); i++) {
            if (objs.get(i).t.equals("kill") && objDone[i] < objs.get(i).n) {
                objDone[i] = Math.min(objs.get(i).n, kills);
            }
        }
    }

    /** Player pressed the interact button. */
    public void tryInteract() {
        if (state != RUN) return;
        Vec3 pp = h.player().pos;
        // interact points
        for (InteractPoint p : pts) {
            if (p.used) continue;
            if (p.gate != null && p.gate.equals("boss") && !bossDead) {
                if (pp.dist(p.pos) < 3.2f) h.toast("The cache is sealed — something guards it");
                continue;
            }
            if (pp.dist(p.pos) < 3.2f) {
                p.used = true;
                h.sfx("interact");
                for (int i = 0; i < objs.size(); i++) {
                    MissionData.Obj o = objs.get(i);
                    if (o.t.equals("collect") && o.tag != null && o.tag.equals(p.t) && objDone[i] < o.n)
                        objDone[i]++;
                }
                if (p.text != null && p.text.length() > 0)
                    h.showNote(p.label, p.text);
                else
                    h.toast(p.label + " — secured");
                return;
            }
        }
    }

    public boolean allDone() {
        for (int i = 0; i < objs.size(); i++) {
            MissionData.Obj o = objs.get(i);
            int done = objDone[i];
            if (o.t.equals("kill") && done < o.n) return false;
            if (o.t.equals("collect") && done < o.n) return false;
            if (o.t.equals("reach") && done < 1) return false;
            if (o.t.equals("survive") && done < 1) return false;
            if (o.t.equals("boss") && !bossDead) return false;
        }
        return true;
    }

    public String objLine() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < objs.size(); i++) {
            MissionData.Obj o = objs.get(i);
            if (i > 0) sb.append("\n");
            String prog;
            if (o.t.equals("kill") || o.t.equals("collect") || o.t.equals("survive"))
                prog = objDone[i] + "/" + o.n;
            else
                prog = objDone[i] > 0 ? "OK" : "--";
            if (o.t.equals("survive")) prog = waveHit + "/" + o.n;
            sb.append(prog).append("  ").append(o.label);
        }
        if (allDone()) sb.append("\n>> REACH EXTRACTION (green circle)");
        return sb.toString();
    }

    public void win() {
        if (state != RUN) return;
        state = WON;
        h.missionWon(this);
    }

    public void lose() {
        if (state != RUN) return;
        state = LOST;
        h.missionLost(this);
    }

    // ---------------- rendering ----------------

    public void draw(Renderer r, float time) {
        for (Pickup p : loot) {
            if (p.taken) continue;
            float bob = (float) Math.sin(time * 2.5f + p.seed) * 0.15f + 0.55f;
            float[] c = pickupColor(p.t);
            r.addBox(p.pos.x, bob, p.pos.z, time * 2f + p.seed, 0, 0.34f, 0.34f, 0.34f, c[0], c[1], c[2]);
            r.addBox(p.pos.x, 1.6f, p.pos.z, 0, 0, 0.1f, 2.4f, 0.1f, c[0] * 0.6f, c[1] * 0.6f, c[2] * 0.6f);
        }
        for (InteractPoint p : pts) {
            if (p.used) continue;
            float bob = (float) Math.sin(time * 2f + p.pos.x) * 0.2f + 0.8f;
            float[] c = pointColor(p.t);
            r.addBox(p.pos.x, bob, p.pos.z, time * 1.5f, 0, 0.45f, 0.45f, 0.45f, c[0], c[1], c[2]);
            r.addBox(p.pos.x, 2.2f, p.pos.z, 0, 0, 0.14f, 4f, 0.14f, c[0] * 0.7f, c[1] * 0.7f, c[2] * 0.7f);
        }
        // extraction circle
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 3f);
        float ok = allDone() ? 1f : 0.45f;
        r.addBox(extract.x, 0.05f, extract.z, 0, 0, 9, 0.06f, 9, 0.1f, 0.9f * ok, 0.3f);
        r.addBox(extract.x, 0.06f, extract.z, 0, 0, 6.4f, 0.07f, 6.4f, 0.15f, 1f * ok, 0.4f);
        r.addBox(extract.x, 3f, extract.z, 0, 0, 0.2f, 6f, 0.2f, 0.2f + 0.3f * pulse * ok, 1f * ok, 0.5f);
    }

    private static float[] pickupColor(String t) {
        if (t.startsWith("ammo_")) return new float[]{1f, 0.8f, 0.2f};
        if (t.equals("med")) return new float[]{0.9f, 0.25f, 0.2f};
        if (t.equals("parts")) return new float[]{0.9f, 0.5f, 0.15f};
        return new float[]{0.6f, 0.7f, 0.5f};
    }

    private static float[] pointColor(String t) {
        if (t.equals("evidence")) return new float[]{0.3f, 0.9f, 0.9f};
        if (t.equals("med_supply")) return new float[]{0.9f, 0.9f, 0.3f};
        if (t.equals("supply")) return new float[]{0.9f, 0.7f, 0.2f};
        return new float[]{0.9f, 0.8f, 0.5f};
    }
}
