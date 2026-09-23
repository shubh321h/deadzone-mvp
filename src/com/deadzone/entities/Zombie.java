package com.deadzone.entities;

import com.deadzone.core.Host;
import com.deadzone.data.EnemyData;
import com.deadzone.gl.Renderer;
import com.deadzone.gl.Vec3;
import com.deadzone.world.World;

import java.util.List;

/**
 * Infected enemy AI. Types (walker/runner/brute/screamer) are pure data +
 * shared behavior; the Colossus boss extends it with a telegraphed pattern
 * machine (charge / ground slam / roar + summons, two phases, head weakpoint).
 */
public class Zombie {
    public static final int IDLE = 0, CHASE = 1, ATTACK = 2, DYING = 3;

    public EnemyData d;
    public Vec3 pos = new Vec3();
    public float yaw, hp, maxHp;
    public int state = IDLE;
    public float stateT, attackCd, screamCd, walkPh, hitFlash, dieT, searchT;
    public boolean alive = true;
    public Vec3 lastSeen = new Vec3();
    private boolean dealt;

    // street pathfinding state
    private java.util.ArrayList<Integer> path = null;
    private int pathIdx;
    private float repathT;

    // boss
    public boolean boss;
    public boolean phase2;
    public int bPat;        // 0 melee, 1 charge telegraph, 2 charging, 3 slam telegraph, 4 roar
    public float bPatT;
    public float bChX, bChZ;
    public float bChD;     // charge distance travelled (capped)
    public boolean bHit;   // charge dealt its hit this pass
    public boolean summ1, summ2;
    public float bRoarCd;

    public void spawn(Vec3 p, EnemyData def) {
        d = def;
        boss = def.boss;
        pos.copy(p);
        hp = maxHp = def.hp;
        state = IDLE; // everyone idle at spawn; the boss guards its arena
        stateT = 0; attackCd = 0; screamCd = 0; walkPh = 0; hitFlash = 0;
        dieT = 0; searchT = 0; alive = true; dealt = false;
        phase2 = false; bPat = 0; bPatT = 0; summ1 = false; summ2 = false;
        bRoarCd = 3f;
        yaw = (float) Math.random() * 6.28f;
    }

    public float radius() { return 0.42f * d.scale + 0.1f; }

    public void alert() {
        if (state == IDLE && alive && !boss) { state = CHASE; stateT = 0; }
    }

    public void update(float dt, Host host) {
        if (!alive) return;
        World w = host.world();
        Player pl = host.player();
        hitFlash = Math.max(0f, hitFlash - dt);
        screamCd -= dt;
        attackCd -= dt;
        stateT += dt;

        if (state == DYING) {
            dieT += dt;
            if (dieT > 0.7f) alive = false;
            return;
        }
        if (pl.dead) return;

        float dist = pos.dist(pl.pos);
        float speed = d.speed * (boss && phase2 ? 1.3f : 1f);

        if (boss) { updateBoss(dt, host, w, pl, dist, speed); }
        else {
            switch (state) {
                case IDLE: {
                    if (stateT > 2f + Math.random() * 3f) {
                        yaw += (float) (Math.random() - 0.5f) * 2f;
                        stateT = 0;
                    }
                    Vec3 f = new Vec3(-(float) Math.sin(yaw), 0, -(float) Math.cos(yaw));
                    pos.addScaled(f, 0.4f * dt);
                    w.resolve(pos, radius());
                    walkPh += 0.4f * dt * 2f;
                    if (dist < d.detect && w.los(pos, pl.pos)) {
                        state = CHASE; stateT = 0; lastSeen.copy(pl.pos);
                    }
                    break;
                }
                case CHASE: {
                    final boolean see = w.los(pos, pl.pos);
                    Vec3 dir = chaseDir(w, pl, dist, dt, see);
                    float wantYaw = (float) Math.atan2(-dir.x, -dir.z);
                    yaw = turnToward(yaw, wantYaw, dt * 8f);
                    // obstacle avoidance: probe ahead, slide along
                    Vec3 probe = pos.clone().addScaled(dir, 1.1f);
                    boolean blocked = w.rayDist(pos.clone(), dir, 1.1f) < 1.0f;
                    if (blocked) {
                        Vec3 a = rotXZ(dir, 1.0f), b = rotXZ(dir, -1.0f);
                        boolean ca = w.rayDist(pos.clone(), a, 1.4f) > 1.2f;
                        boolean cb = w.rayDist(pos.clone(), b, 1.4f) > 1.2f;
                        if (ca && !cb) dir.copy(a);
                        else if (cb && !ca) dir.copy(b);
                        else if (ca || cb) { /* keep sliding, small random */ dir.copy(ca ? a : b); }
                        else dir.copy(rotXZ(dir, 1.4f));
                        speed *= 0.7f;
                    }
                    pos.addScaled(dir, speed * dt);
                    w.resolve(pos, radius());
                    walkPh += speed * dt * 2f;
                    // separation
                    List<Zombie> zs = host.zombies();
                    for (Zombie o : zs) {
                        if (o == this || !o.alive || o.state == DYING) continue;
                        float dd = pos.dist(o.pos);
                        float min = radius() + o.radius();
                        if (dd < min && dd > 0.01f) {
                            Vec3 push = pos.clone().sub(o.pos).mul((min - dd) * 0.5f / dd);
                            pos.add(push);
                        }
                    }
                    if (see) { lastSeen.copy(pl.pos); searchT = 0; }
                    else {
                        searchT += dt;
                        if (searchT > 7f) { state = IDLE; stateT = 0; }
                    }
                    // screamer shriek
                    if (d.scream > 0 && dist < 2.4f && screamCd <= 0) {
                        screamCd = 8f;
                        host.sfx("scream");
                        for (Zombie o : zs)
                            if (o.alive && !o.boss && pos.dist(o.pos) < d.scream) o.alert();
                        host.toast("Screamer shrieked!");
                    }
                    if (dist < d.range && attackCd <= 0) { state = ATTACK; stateT = 0; dealt = false; }
                    break;
                }
                case ATTACK: {
                    Vec3 dir = new Vec3(pl.pos.x - pos.x, 0, pl.pos.z - pos.z).norm();
                    float wind = stateT < 0.3f ? 0f : (stateT < 0.55f ? 2.2f : 0.3f);
                    if (wind > 0) { pos.addScaled(dir, wind * dt); w.resolve(pos, radius()); }
                    if (dist < d.range * 1.15f && !dealt && stateT > 0.25f) {
                        dealt = true;
                        pl.damage(d.dmg, pos, host);
                    }
                    if (stateT > 0.7f) { state = CHASE; attackCd = d.cd; }
                    break;
                }
            }
        }
    }

    private void updateBoss(float dt, Host host, World w, Player pl, float dist, float speed) {
        // arena guardian: stands at the station until the player comes into
        // detection (or a bullet lands) — the fight belongs to the arena,
        // not a cross-city chase to the spawn point.
        if (state == IDLE) {
            if (dist < d.detect && w.los(pos, pl.pos)) {
                state = CHASE; stateT = 0; lastSeen.copy(pl.pos);
            }
            return;
        }
        bPatT += dt;
        bRoarCd -= dt;
        // phase change
        if (!phase2 && hp < maxHp * 0.5f) {
            phase2 = true;
            bPat = 4; bPatT = 0;
            host.sfx("boss_roar");
            host.toast("The Colossus is enraging!");
            summonAdd(host, 3, "walker");
        }
        if (!summ2 && hp < maxHp * 0.25f) {
            summ2 = true;
            summonAdd(host, 2, "runner");
        }
        switch (bPat) {
            case 0: { // melee chase
                Vec3 dir = chaseDir(w, pl, dist, dt, w.los(pos, pl.pos));
                float wantYaw = (float) Math.atan2(-dir.x, -dir.z);
                yaw = turnToward(yaw, wantYaw, dt * 4f);
                if (dist > d.range) {
                    pos.addScaled(dir, speed * dt);
                    walkPh += speed * dt * 1.4f;
                }
                w.resolve(pos, radius());
                if (dist < d.range && attackCd <= 0) {
                    pos.addScaled(dir, 1.5f * dt);
                    if (dist < d.range * 1.1f) { dealt = false; }
                    if (dist < d.range * 1.05f && !dealt) {
                        dealt = true;
                        pl.damage(d.dmg, pos, host);
                    }
                    attackCd = d.cd;
                }
                // pick next pattern — with a recovery floor so the boss cannot
                // chain-charge (back-to-back 13 m dashes with no punish window
                // outrun even a full-sprint player)
                if (dist > 11f && bPatT > 1.2f) { bPat = 1; bPatT = 0; host.sfx("boss_charge"); }
                else if (bPatT > 3.5f + Math.random() * 2f) { bPat = 3; bPatT = 0; host.sfx("boss_wind"); }
                break;
            }
            case 1: { // charge telegraph
                Vec3 dir = w.los(pos, pl.pos)
                        ? new Vec3(pl.pos.x - pos.x, 0, pl.pos.z - pos.z).norm()
                        : chaseDir(w, pl, dist, dt, false);
                yaw = turnToward(yaw, (float) Math.atan2(-dir.x, -dir.z), dt * 10f);
                if (bPatT > 0.9f) {
                    bChX = dir.x; bChZ = dir.z;
                    bChD = 0f; bHit = false;
                    bPat = 2; bPatT = 0;
                }
                break;
            }
            case 2: { // charging
                pos.addScaled(new Vec3(bChX, 0, bChZ), 13f * dt);
                bChD += 13f * dt;
                walkPh += 13f * dt * 1.2f;
                w.resolve(pos, radius());
                if (!bHit && pos.dist(pl.pos) < 2.4f) {
                    bHit = true;
                    pl.damage(d.dmg * 1.4f, pos, host);
                }
                Vec3 probe = new Vec3(bChX, 0, bChZ);
                if (w.rayDist(pos.clone(), probe, 1.5f) < 1.3f || bPatT > 1.6f || bChD > 13f) {
                    if (bPatT <= 1.6f && bChD <= 13f) {
                        // crashed into something mid-charge: back off the face so
                        // the melee chase that follows isn't wedged in a pocket
                        pos.addScaled(new Vec3(bChX, 0, bChZ), -1.2f);
                        w.resolve(pos, radius());
                    }
                    bPat = 0; bPatT = 0;
                }
                break;
            }
            case 3: { // slam telegraph
                if (bPatT > 1.0f) {
                    // impact
                    host.sfx("boss_slam");
                    host.fx().boom(pos);
                    host.fx().ring(pos, 1f, 6.5f);
                    if (dist < 6.5f) pl.damage(55f, pos, host);
                    bPat = 0; bPatT = 0;
                    attackCd = 0.5f;
                }
                break;
            }
            case 4: { // roar (aggro + summons)
                if (bPatT > 0.1f && bRoarCd > 2.5f) {
                    bRoarCd = 0;
                    host.sfx("boss_roar");
                    for (Zombie o : host.zombies())
                        if (o.alive && !o.boss && pos.dist(o.pos) < 45f) o.alert();
                }
                if (bPatT > 1.2f) { bPat = 0; bPatT = 0; }
                break;
            }
        }
    }

    private void summonAdd(Host host, int n, String type) {
        World w = host.world();
        for (int i = 0; i < n; i++) {
            Vec3 p = w.spawnPoint(pos, 6f, 12f, hostRng);
            if (p != null) host.spawnZombie(type, p);
        }
    }

    private static java.util.Random hostRng = new java.util.Random(99);

    /** Where to walk: straight at the player when visible, otherwise along
     *  the street network (A* over World.graph). */
    private Vec3 chaseDir(World w, Player pl, float dist, float dt, boolean los) {
        if (los && dist < 34f) {
            path = null; repathT = 0;
            return new Vec3(pl.pos.x - pos.x, 0, pl.pos.z - pos.z).norm();
        }
        if (w.graph == null)
            return new Vec3(pl.pos.x - pos.x, 0, pl.pos.z - pos.z).norm();
        repathT -= dt;
        if (path == null || pathIdx >= path.size() || repathT <= 0f) {
            path = w.graph.path(w.graph.nearestOpen(w, pos), w.graph.nearestOpen(w, pl.pos));
            pathIdx = 0;
            repathT = 1.5f + (float) (Math.random() * 0.8f);
        }
        if (path != null && pathIdx < path.size()) {
            Vec3 wp = w.graph.node(path.get(pathIdx));
            Vec3 dir = new Vec3(wp.x - pos.x, 0, wp.z - pos.z);
            float L = dir.len();
            if (L < 1.4f) pathIdx++;
            if (L > 0.01f) return dir.mul(1f / L);
        }
        return new Vec3(pl.pos.x - pos.x, 0, pl.pos.z - pos.z).norm();
    }

    static float turnToward(float cur, float target, float maxStep) {
        float diff = target - cur;
        while (diff > Math.PI) diff -= 6.2832f;
        while (diff < -Math.PI) diff += 6.2832f;
        if (Math.abs(diff) <= maxStep) return target;
        return cur + (diff > 0 ? maxStep : -maxStep);
    }

    static Vec3 rotXZ(Vec3 v, float ang) {
        float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
        float x = v.x * c - v.z * s;
        float z = v.x * s + v.z * c;
        return new Vec3(x, 0, z);
    }

    public void takeHit(float dmg, boolean head, Vec3 from, Host host) {
        if (!alive || state == DYING) return;
        hp -= dmg;
        hitFlash = 0.12f;
        if (state == IDLE) { state = CHASE; stateT = 0; }
        lastSeen.copy(from);
        if (hp <= 0) {
            hp = 0;
            state = DYING;
            dieT = 0;
            host.sfx("zdie");
            host.onZombieKilled(this);
        }
    }

    // ---------------- rendering ----------------

    public void draw(Renderer r, float t) {
        if (!alive) return;
        float s = d.scale;
        float cr = 1f, cg = 1f, cb = 1f;
        if (hitFlash > 0) {
            float k = hitFlash / 0.12f;
            cr = d.color[0] + (1 - d.color[0]) * k;
            cg = d.color[1] + (1 - d.color[1]) * k;
            cb = d.color[2] + (1 - d.color[2]) * k;
        }
        float rx = 0f, sink = 0f;
        if (state == DYING) {
            float k = Math.min(1f, dieT / 0.55f);
            rx = k * 1.57f;
            sink = k * 0.35f;
        }
        float walk = (float) Math.sin(walkPh);
        float bob = Math.abs(walk) * 0.04f * s;
        float armZ = (state == CHASE || state == ATTACK) ? 0.5f : 0.22f;
        float armY = 1.05f - 0.1f * s;
        if (state == ATTACK && stateT < 0.3f) armY += 0.2f;
        if (boss) {
            if (bPat == 1) { armY += 0.1f; armZ = 0.1f; }
            if (bPat == 3) { armY += 0.45f; armZ = 0.3f; }
        }
        float y0 = -sink;
        r.addBox(pos.x, y0 + 0.28f * s + bob * 0.5f, pos.z, yaw, rx, 0.16f * s, 0.55f * s, 0.18f * s,
                d.color[0] * 0.7f * cr, d.color[1] * 0.7f * cg, d.color[2] * 0.7f * cb);
        // legs alternate
        float sw = walk * 0.2f * s;
        r.addBox(pos.x, y0 + 0.28f * s, pos.z + sw, yaw, rx, 0.16f * s, 0.55f * s, 0.18f * s,
                d.color[0] * 0.75f * cr, d.color[1] * 0.75f * cg, d.color[2] * 0.75f * cb);
        r.addBox(pos.x, y0 + 0.28f * s, pos.z - sw, yaw, rx, 0.16f * s, 0.55f * s, 0.18f * s,
                d.color[0] * 0.75f * cr, d.color[1] * 0.75f * cg, d.color[2] * 0.75f * cb);
        // torso
        r.addBox(pos.x, y0 + 0.95f * s + bob, pos.z, yaw, rx, 0.55f * s, 0.8f * s, 0.34f * s,
                d.color[0] * cr, d.color[1] * cg, d.color[2] * cb);
        // head (weakpoint glows on the boss)
        float hr = d.color[0] * 0.85f * cr, hg = d.color[1] * 0.85f * cg, hb = d.color[2] * 0.85f * cb;
        if (boss) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * 6f);
            hr = 1f; hg = 0.25f + 0.35f * pulse; hb = 0.15f;
        }
        r.addBox(pos.x, y0 + 1.52f * s + bob, pos.z, yaw, rx, 0.32f * s, 0.32f * s, 0.32f * s, hr, hg, hb);
        // arms (local side offsets, both extended forward in chase/attack)
        float ax = 0.36f * s;
        float cyw = (float) Math.cos(yaw), syw = (float) Math.sin(yaw);
        float axw = ax * cyw, azw = -ax * syw;
        float as = 0.13f * s;
        r.addBox(pos.x + axw, y0 + armY * s + bob, pos.z + azw, yaw, rx, as, 0.65f * s, 0.14f * s,
                d.color[0] * 0.7f * cr, d.color[1] * 0.7f * cg, d.color[2] * 0.7f * cb);
        r.addBox(pos.x - axw, y0 + armY * s + bob, pos.z - azw, yaw, rx, as, 0.65f * s, 0.14f * s,
                d.color[0] * 0.7f * cr, d.color[1] * 0.7f * cg, d.color[2] * 0.7f * cb);
        // boss warning glow during telegraphs
        if (boss && (bPat == 1 || bPat == 3)) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * 14f);
            r.addBox(pos.x, y0 + 0.2f, pos.z, 0, 0, 1.6f * s, 0.1f, 1.6f * s, 1f, 0.15f, 0.1f);
            if (pulse > 0.5f)
                r.addBox(pos.x, y0 + 1.9f * s, pos.z, yaw, 0, 0.4f * s, 0.4f * s, 0.4f * s, 1f, 0.4f, 0.2f);
        }
    }
}
