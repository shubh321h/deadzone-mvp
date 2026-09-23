package com.deadzone.entities;

import com.deadzone.core.Host;
import com.deadzone.core.InputState;
import com.deadzone.data.DataLib;
import com.deadzone.data.SaveData;
import com.deadzone.data.WeaponData;
import com.deadzone.gl.Mat4;
import com.deadzone.gl.Renderer;
import com.deadzone.gl.Vec3;
import com.deadzone.world.World;

import java.util.ArrayList;
import java.util.List;

/** Third-person survivor: movement, sprint/crouch, over-the-shoulder camera,
 *  data-driven weapons, hitscan shooting with headshots, reload, damage. */
public class Player {
    public Vec3 pos = new Vec3();
    public float camYaw, camPitch;
    public float fov = 60f;
    public float hp, maxHp = 100f;
    public boolean crouch;
    public float crouchT;          // 0..1 animated
    public float adsT;             // 0..1 aim down sights
    public float fireCd, reloadT, hurtT, walkPh, emptySndT;
    public float spreadBloom;
    public int cur;                // index into data.weapons
    public int[] mags;             // parallel to data.weapons
    public Vec3 camPos = new Vec3();
    public Vec3 camFwd = new Vec3(0, 0, -1);
    public boolean dead;

    public void reset(Vec3 p, float yaw, Host host) {
        pos.copy(p);
        camYaw = yaw;
        camPitch = 0.05f;
        hp = maxHp = host.save().maxHp();
        crouch = false; crouchT = 0; adsT = 0;
        fireCd = 0; reloadT = 0; hurtT = 0; walkPh = 0; spreadBloom = 0;
        dead = false;
        mags = new int[host.data().weapons.length];
        for (int i = 0; i < mags.length; i++) {
            if (!host.save().owned(host.data().weapons[i].id)) { mags[i] = 0; continue; }
            int ai = DataLib.ammoIndex(host.data().weapons[i].ammoType);
            int eff = effMag(i, host);
            int take = Math.min(eff, host.sessionAmmo()[ai]);
            mags[i] = take;
            host.sessionAmmo()[ai] -= take;
        }
        cur = 0;
        String eq = host.save().equip;
        for (int i = 0; i < host.data().weapons.length; i++)
            if (host.data().weapons[i].id.equals(eq) && host.save().owned(eq)) cur = i;
        // deployment guarantee: the safehouse tops you up before you head out —
        // never deploy unable to fire a shot (there is no melee; a bone-dry
        // save would softlock the run). Bounded: only fires when the equipped
        // gun is completely dry, and only once per deployment.
        int aiCur = DataLib.ammoIndex(host.data().weapons[cur].ammoType);
        int effCur = effMag(cur, host);
        if (mags[cur] + host.sessionAmmo()[aiCur] < effCur) {
            mags[cur] = effCur;
            host.sessionAmmo()[aiCur] += effCur; // a full reserve mag on top
        }
    }

    public WeaponData wcur(Host host) { return host.data().weapons[cur]; }

    public int effMag(int wi, Host host) {
        WeaponData w = host.data().weapons[wi];
        return (int) (w.mag * (1 + host.data().upgPer[1] * host.save().wLevel(w.id, 1)));
    }

    public float effDmg(int wi, Host host) {
        WeaponData w = host.data().weapons[wi];
        return w.dmg * (1 + host.data().upgPer[0] * host.save().wLevel(w.id, 0)) * host.save().dmgMul();
    }

    public float effSpread(int wi, Host host) {
        WeaponData w = host.data().weapons[wi];
        return w.spread * (1 - host.data().upgPer[2] * host.save().wLevel(w.id, 2));
    }

    public int resAmmo(Host host) {
        return host.sessionAmmo()[DataLib.ammoIndex(wcur(host).ammoType)];
    }

    public void swap(Host host) {
        int n = host.data().weapons.length;
        for (int i = 0; i < n; i++) {
            int nx = (cur + 1 + i) % n;
            if (host.save().owned(host.data().weapons[nx].id)) {
                cur = nx;
                reloadT = 0;
                host.sfx("swap");
                return;
            }
        }
    }

    public void startReload(Host host) {
        WeaponData w = wcur(host);
        if (reloadT > 0) return;
        if (mags[cur] >= effMag(cur, host)) return;
        if (resAmmo(host) <= 0) return;
        reloadT = w.reload * host.save().reloadMul();
        host.sfx("reload");
    }

    public void update(float dt, InputState in, Host host) {
        World w = host.world();
        if (dead) return;

        // ---- camera ----
        camYaw -= in.camDX * 0.0032f * host.sens();
        camPitch += (-in.camDY) * 0.0028f * host.sens();
        camPitch = World.clamp(camPitch, -0.18f, 0.85f);

        // ---- movement (camera relative) ----
        float cy = (float) Math.cos(camYaw), sy = (float) Math.sin(camYaw);
        // forward (-sin, -cos), right (cos, -sin)
        float mx = (-sy * in.joyY + cy * in.joyX);
        float mz = (-cy * in.joyY - sy * in.joyX);
        float ml = (float) Math.sqrt(mx * mx + mz * mz);
        boolean moving = ml > 0.01f;
        if (ml > 1f) { mx /= ml; mz /= ml; }
        float speed = 4.2f * host.save().speedMul();
        boolean sprinting = in.sprint && moving && adsT < 0.5f;
        if (sprinting) speed *= 1.55f;
        crouch = in.crouch;
        crouchT += ((crouch ? 1f : 0f) - crouchT) * Math.min(1f, dt * 10f);
        if (crouch) speed *= 0.55f;
        if (adsT > 0.5f) speed *= 0.6f;
        pos.addScaled(new Vec3(mx, 0, mz), speed * dt);
        w.resolve(pos, 0.45f);
        if (moving) walkPh += speed * dt * 2.0f;

        // ads
        adsT += ((in.aim ? 1f : 0f) - adsT) * Math.min(1f, dt * 9f);

        // ---- camera position (over the shoulder, collision clamped) ----
        float hh = 1.55f - 0.45f * crouchT;
        Vec3 head = new Vec3(pos.x, pos.y + hh, pos.z);
        float rxo = 0.62f - 0.30f * adsT;
        float upo = 0.32f - 0.22f * adsT;
        float back = 1.35f - 0.78f * adsT;
        // local (x=right, z=back) -> world
        float ox = rxo, oz = back;
        float wx = pos.x + ox * cy + oz * sy;
        float wz = pos.z - ox * sy + oz * cy;
        camPos.set(wx, head.y + upo, wz);
        Vec3 dir = new Vec3(camPos.x - head.x, camPos.y - head.y, camPos.z - head.z);
        float L = dir.len();
        dir.norm();
        float tHit = w.rayDist(head, dir, L);
        if (tHit < L - 0.2f) camPos.copy(head).addScaled(dir, Math.max(0.3f, tHit - 0.2f));
        Mat4.forwardY(fwdScratch, camYaw, camPitch);
        camFwd.set(fwdScratch[0], fwdScratch[1], fwdScratch[2]);

        // ---- reload ----
        if (reloadT > 0) {
            reloadT -= dt;
            if (reloadT <= 0) {
                int ai = DataLib.ammoIndex(wcur(host).ammoType);
                int cap = effMag(cur, host);
                int need = cap - mags[cur];
                int take = Math.min(need, host.sessionAmmo()[ai]);
                mags[cur] += take;
                host.sessionAmmo()[ai] -= take;
            }
        }

        // ---- actions ----
        if (in.swapTap) swap(host);
        if (in.reloadTap) startReload(host);
        if (in.interactTap) host.playerInteract();

        // ---- firing ----
        fireCd -= dt;
        emptySndT -= dt;
        spreadBloom = Math.max(0f, spreadBloom - dt * 3f);
        WeaponData wd = wcur(host);
        if (in.fire && fireCd <= 0 && reloadT <= 0 && !dead) {
            if (mags[cur] > 0) {
                shoot(host);
                fireCd = 1f / wd.rate;
            } else {
                if (emptySndT <= 0) {
                    host.sfx("empty");
                    emptySndT = 0.4f;
                }
                startReload(host);
            }
        }
        hurtT = Math.max(0f, hurtT - dt);
    }

    private float[] fwdScratch = new float[3];

    private void shoot(Host host) {
        World w = host.world();
        WeaponData wd = wcur(host);
        mags[cur]--;
        float hh = 1.55f - 0.45f * crouchT;
        float mx = 0.28f, mz = 0.55f + 0.25f * adsT;
        float cy = (float) Math.cos(camYaw), sy = (float) Math.sin(camYaw);
        Vec3 muzzle = new Vec3(
                pos.x + mx * cy + mz * sy,
                pos.y + hh - 0.15f - 0.1f * adsT,
                pos.z - mx * sy + mz * cy);
        float spreadDeg = effSpread(cur, host) * (1 + spreadBloom)
                * (0.45f + 0.55f * (1 - adsT)) * (1 - 0.15f * crouchT);
        if (crouchT > 0.5f) spreadDeg *= 0.85f;
        float dmg = effDmg(cur, host);
        List<Zombie> zs = host.zombies();
        for (int p = 0; p < wd.pellets; p++) {
            Vec3 dir = camFwd.clone();
            // random cone
            float a = (float) Math.random() * 6.283f;
            float r = (float) Math.random() * spreadDeg;
            float s = (float) Math.tan(Math.toRadians(Math.max(0.05f, r)));
            Vec3 right = new Vec3(cy, 0, -sy);
            dir.addScaled(right, (float) Math.cos(a) * s).addScaled(new Vec3(0, 1, 0), (float) Math.sin(a) * s);
            dir.norm();
            float range = wd.range;
            float tWorld = w.rayDist(muzzle, dir, range);
            float bestT = tWorld;
            Zombie best = null;
            boolean bestHead = false;
            for (Zombie z : zs) {
                if (!z.alive) continue;
                float hit = rayZombie(muzzle, dir, z);
                if (hit >= 0 && hit < bestT) {
                    bestT = hit; best = z;
                    float hy = dir.y * hit;
                    bestHead = hy > 1.25f * z.d.scale && hy < 1.85f * z.d.scale;
                }
            }
            Vec3 end = new Vec3(muzzle.x, muzzle.y, muzzle.z).addScaled(dir, bestT);
            if (best != null) {
                float mul = bestHead ? best.d.headMult : 1f;
                best.takeHit(dmg * mul, bestHead, muzzle, host);
                host.fx().blood(end, dir);
            }
            host.fx().tracer(muzzle, end, 1f, 0.9f, 0.45f);
        }
        host.fx().muzzle(muzzle, camFwd);
        host.sfx("shot_" + wd.id);
        spreadBloom = Math.min(1.6f, spreadBloom + 0.28f);
        camPitch += 0.0035f;
    }

    /** Ray vs zombie vertical capsule (XZ circle radius, y from 0 to 1.8*scale). Returns t or -1. */
    static float rayZombie(Vec3 o, Vec3 d, Zombie z) {
        float rx = o.x - z.pos.x, rz = o.z - z.pos.z;
        float rad = 0.42f * z.d.scale + 0.1f;
        float A = d.x * d.x + d.z * d.z;
        float B = 2 * (rx * d.x + rz * d.z);
        float C = rx * rx + rz * rz - rad * rad;
        if (A < 1e-6f) return -1;
        float disc = B * B - 4 * A * C;
        if (disc < 0) return -1;
        float sq = (float) Math.sqrt(disc);
        float t1 = (-B - sq) / (2 * A);
        float t2 = (-B + sq) / (2 * A);
        float t = t1 > 0.1f ? t1 : (t2 > 0.1f ? t2 : -1);
        if (t < 0) return -1;
        float hy = o.y + d.y * t;
        if (hy < 0 || hy > 1.85f * z.d.scale) return -1;
        return t;
    }

    public void damage(float d, Vec3 from, Host host) {
        if (dead) return;
        hp -= d;
        hurtT = 0.35f;
        host.sfx("hurt");
        if (hp <= 0) {
            hp = 0;
            dead = true;
            host.sfx("die");
            host.playerDied();
        }
    }

    public void useMed(Host host) {
        hp = Math.min(maxHp, hp + 45f);
        host.sfx("med");
        host.toast("+45 HP");
    }

    // ---------------- rendering ----------------

    private void box(Renderer r, float lx, float ly, float lz,
                     float sx, float sy, float sz, float cr, float cg, float cb, float yaw) {
        float cyw = (float) Math.cos(yaw), syw = (float) Math.sin(yaw);
        float x3 = lx * cyw + lz * syw;
        float z3 = -lx * syw + lz * cyw;
        r.addBox(pos.x + x3, ly, pos.z + z3, yaw, 0, sx, sy, sz, cr, cg, cb);
    }

    public void draw(Renderer r, Host host) {
        if (dead) return;
        float yaw = camYaw;
        float bob = (float) Math.sin(walkPh * 2f) * 0.035f * (adsT < 0.5f ? 1f : 0.2f);
        float cr = crouchT;
        float legH = 0.55f * (1 - 0.3f * cr);
        float legY = legH / 2f;
        float torsoY = legH + 0.45f * (1 - 0.25f * cr) + bob;
        float headY = legH + 0.95f * (1 - 0.2f * cr) + bob;
        float swing = (float) Math.sin(walkPh) * 0.24f * (1 - cr * 0.7f);
        float[] skin = {0.72f, 0.58f, 0.44f};
        float[] jacket = {0.33f, 0.37f, 0.28f};
        float[] pants = {0.20f, 0.21f, 0.22f};
        // legs
        box(r, -0.14f, legY, swing, 0.2f, legH, 0.22f, pants[0], pants[1], pants[2], yaw);
        box(r, 0.14f, legY, -swing, 0.2f, legH, 0.22f, pants[0], pants[1], pants[2], yaw);
        // torso
        box(r, 0, torsoY, 0, 0.55f, 0.9f, 0.34f, jacket[0], jacket[1], jacket[2], yaw);
        // backpack
        box(r, 0, torsoY + 0.05f, -0.24f, 0.4f, 0.55f, 0.24f, 0.16f, 0.16f, 0.15f, yaw);
        // head + cap
        box(r, 0, headY, 0, 0.3f, 0.3f, 0.3f, skin[0], skin[1], skin[2], yaw);
        box(r, 0, headY + 0.16f, -0.02f, 0.32f, 0.1f, 0.32f, 0.18f, 0.2f, 0.16f, yaw);
        // arms
        float armY = torsoY + 0.1f;
        box(r, -0.37f, armY, 0, 0.14f, 0.65f, 0.16f, jacket[0], jacket[1], jacket[2], yaw);
        // weapon hand + gun
        float gunZ = 0.5f + 0.3f * adsT;
        box(r, 0.37f, armY - 0.05f, 0.1f * adsT, 0.14f, 0.5f, 0.16f, jacket[0], jacket[1], jacket[2], yaw);
        WeaponData wd = wcur(host);
        float gl = 0.5f + wd.len * 0.6f;
        box(r, 0.3f, torsoY - 0.12f - 0.18f * adsT, gunZ, 0.1f, 0.16f, gl, 0.12f, 0.12f, 0.13f, yaw);
        // hurt flash
        if (hurtT > 0.25f) {
            r.addBox(pos.x, torsoY, pos.z, yaw, 0, 0.6f, 1f, 0.4f, 1f, 0.2f, 0.2f);
        }
    }

    // ---------------- camera ----------------

    private float sprintFov;

    public float[] viewProj(float aspect, float targetAdsFov, boolean sprinting) {
        sprintFov = sprinting ? 4f : 0f;
        float targetFov = 60f - (60f - targetAdsFov) * adsT + sprintFov;
        fov += (targetFov - fov) * 0.25f;
        float[] out = new float[16];
        Mat4.viewProj(out, camPos.x, camPos.y, camPos.z, camYaw, camPitch, fov, aspect);
        return out;
    }
}
