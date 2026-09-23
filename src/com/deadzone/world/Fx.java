package com.deadzone.world;

import com.deadzone.gl.Renderer;
import com.deadzone.gl.Vec3;

import java.util.ArrayList;

/** Lightweight line/particle effects: tracers, muzzle flash, blood, rings, rain.
 *  All drawn in the shared line pass (single draw call). */
public class Fx {
    static class Tracer {
        float x1, y1, z1, x2, y2, z2, life, max, r, g, b;
    }
    static class Burst {
        float x, y, z, life, max, seed, r, g, b;
    }
    static class Ring {
        float x, z, r0, r1, life, max, r, g, b;
        Ring(float x, float z, float r0, float r1, float life, float max, float r, float g, float b) {
            this.x = x; this.z = z; this.r0 = r0; this.r1 = r1;
            this.life = life; this.max = max; this.r = r; this.g = g; this.b = b;
        }
    }

    public final ArrayList<Tracer> tracers = new ArrayList<Tracer>();
    public final ArrayList<Burst> bursts = new ArrayList<Burst>();
    public final ArrayList<Ring> rings = new ArrayList<Ring>();
    private float t;

    public void reset() {
        tracers.clear();
        bursts.clear();
        rings.clear();
        t = 0;
    }

    public void muzzle(Vec3 p, Vec3 dir) {
        Burst b = new Burst();
        b.x = p.x + dir.x * 0.2f; b.y = p.y + dir.y * 0.2f; b.z = p.z + dir.z * 0.2f;
        b.life = b.max = 0.05f;
        b.seed = (float) (Math.random() * 100);
        b.r = 1f; b.g = 0.85f; b.b = 0.4f;
        bursts.add(b);
    }

    public void tracer(Vec3 a, Vec3 b, float r, float g, float bl) {
        Tracer t = new Tracer();
        t.x1 = a.x; t.y1 = a.y; t.z1 = a.z;
        t.x2 = b.x; t.y2 = b.y; t.z2 = b.z;
        t.life = t.max = 0.06f;
        t.r = r; t.g = g; t.b = bl;
        if (tracers.size() < 64) tracers.add(t);
    }

    public void blood(Vec3 p, Vec3 dir) {
        Burst b = new Burst();
        b.x = p.x; b.y = p.y; b.z = p.z;
        b.life = b.max = 0.18f;
        b.seed = (float) (Math.random() * 100);
        b.r = 0.55f; b.g = 0.05f; b.b = 0.05f;
        if (bursts.size() < 48) bursts.add(b);
    }

    public void boom(Vec3 p) {
        Burst b = new Burst();
        b.x = p.x; b.y = p.y + 0.4f; b.z = p.z;
        b.life = b.max = 0.35f;
        b.seed = (float) (Math.random() * 100);
        b.r = 1f; b.g = 0.5f; b.b = 0.15f;
        bursts.add(b);
        rings.add(new Ring(p.x, p.z, 1f, 6f, 0.4f, 0.4f, 1f, 0.6f, 0.2f));
    }

    public void ring(Vec3 p, float r0, float r1) {
        rings.add(new Ring(p.x, p.z, r0, r1, 0.5f, 0.5f, 0.3f, 0.5f, 0.9f));
    }

    public void update(float dt) {
        t += dt;
        for (int i = tracers.size() - 1; i >= 0; i--) {
            Tracer tr = tracers.get(i);
            tr.life -= dt;
            if (tr.life <= 0) tracers.remove(i);
        }
        for (int i = bursts.size() - 1; i >= 0; i--) {
            Burst b = bursts.get(i);
            b.life -= dt;
            if (b.life <= 0) bursts.remove(i);
        }
        for (int i = rings.size() - 1; i >= 0; i--) {
            Ring rg = rings.get(i);
            rg.life -= dt;
            if (rg.life <= 0) rings.remove(i);
        }
    }

    public void draw(Renderer r, Vec3 cam, int rainN, boolean rainOn) {
        for (Tracer tr : tracers) {
            float a = tr.life / tr.max;
            r.addLine(tr.x1, tr.y1, tr.z1, tr.x2, tr.y2, tr.z2, tr.r, tr.g, tr.b, 0.9f * a);
        }
        for (Burst b : bursts) {
            float a = b.life / b.max;
            for (int i = 0; i < 4; i++) {
                float s = b.seed + i * 17.3f;
                float ang = (float) (s * 0.6);
                float up = 0.5f + (float) Math.sin(s * 1.7f) * 0.5f;
                float len = (0.25f + 0.35f * a) * (0.6f + 0.8f * up);
                r.addLine(b.x, b.y, b.z,
                        b.x + (float) Math.cos(ang) * len,
                        b.y + up * len * 1.4f,
                        b.z + (float) Math.sin(ang) * len,
                        b.r, b.g, b.b, 0.8f * a);
            }
        }
        for (Ring rg : rings) {
            float k = 1f - rg.life / rg.max;
            float rad = rg.r0 + (rg.r1 - rg.r0) * k;
            float a = (rg.life / rg.max) * 0.8f;
            for (int i = 0; i < 12; i++) {
                float a0 = i * 0.5236f, a1 = (i + 1) * 0.5236f;
                r.addLine(rg.x + (float) Math.cos(a0) * rad, 0.15f, rg.z + (float) Math.sin(a0) * rad,
                        rg.x + (float) Math.cos(a1) * rad, 0.15f, rg.z + (float) Math.sin(a1) * rad,
                        rg.r, rg.g, rg.b, a);
            }
        }
        if (rainOn && rainN > 0) {
            float top = cam.y + 8f;
            for (int i = 0; i < rainN; i++) {
                float f = i * 12.9898f * 43758.5453f;
                float fx = f - (float) Math.floor(f);
                f = i * 78.233f * 43758.5453f;
                float fz = f - (float) Math.floor(f);
                float x = cam.x - 22 + fx * 44;
                float z = cam.z - 22 + fz * 44;
                float fall = (i * 31.7f + t * 16f) % 18f;
                float y = top - fall;
                r.addLine(x, y, z, x, y - 1.3f, z, 0.55f, 0.65f, 0.8f, 0.25f);
            }
        }
    }
}
