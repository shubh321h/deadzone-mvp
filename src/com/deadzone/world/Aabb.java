package com.deadzone.world;

import com.deadzone.gl.Vec3;

/** Axis-aligned box used for collision and raycasts. */
public class Aabb {
    public float x0, y0, z0, x1, y1, z1;

    public static Aabb box(float cx, float cy, float cz, float sx, float sy, float sz) {
        Aabb a = new Aabb();
        a.x0 = cx - sx * 0.5f; a.x1 = cx + sx * 0.5f;
        a.y0 = cy - sy * 0.5f; a.y1 = cy + sy * 0.5f;
        a.z0 = cz - sz * 0.5f; a.z1 = cz + sz * 0.5f;
        return a;
    }

    /** Slab raycast. Returns entry t in [0, max] or -1. */
    public float ray(Vec3 o, Vec3 d, float max) {
        float tmin = 0f, tmax = max;
        // X
        if (Math.abs(d.x) < 1e-6f) {
            if (o.x < x0 || o.x > x1) return -1;
        } else {
            float inv = 1f / d.x;
            float t1 = (x0 - o.x) * inv, t2 = (x1 - o.x) * inv;
            if (t1 > t2) { float tt = t1; t1 = t2; t2 = tt; }
            if (t1 > tmin) tmin = t1;
            if (t2 < tmax) tmax = t2;
            if (tmin > tmax) return -1;
        }
        // Y
        if (Math.abs(d.y) < 1e-6f) {
            if (o.y < y0 || o.y > y1) return -1;
        } else {
            float inv = 1f / d.y;
            float t1 = (y0 - o.y) * inv, t2 = (y1 - o.y) * inv;
            if (t1 > t2) { float tt = t1; t1 = t2; t2 = tt; }
            if (t1 > tmin) tmin = t1;
            if (t2 < tmax) tmax = t2;
            if (tmin > tmax) return -1;
        }
        // Z
        if (Math.abs(d.z) < 1e-6f) {
            if (o.z < z0 || o.z > z1) return -1;
        } else {
            float inv = 1f / d.z;
            float t1 = (z0 - o.z) * inv, t2 = (z1 - o.z) * inv;
            if (t1 > t2) { float tt = t1; t1 = t2; t2 = tt; }
            if (t1 > tmin) tmin = t1;
            if (t2 < tmax) tmax = t2;
            if (tmin > tmax) return -1;
        }
        return tmin;
    }

    /** Push circle (x,z,r) out of this box (XZ plane). Mutates p. Returns true if collided. */
    public boolean pushOut(Vec3 p, float rad) {
        float ix0 = x0 - rad, ix1 = x1 + rad, iz0 = z0 - rad, iz1 = z1 + rad;
        if (p.x <= ix0 || p.x >= ix1 || p.z <= iz0 || p.z >= iz1) return false;
        float dx0 = p.x - ix0, dx1 = ix1 - p.x, dz0 = p.z - iz0, dz1 = iz1 - p.z;
        float m = Math.min(Math.min(dx0, dx1), Math.min(dz0, dz1));
        if (m == dx0) p.x = ix0;
        else if (m == dx1) p.x = ix1;
        else if (m == dz0) p.z = iz0;
        else p.z = iz1;
        return true;
    }
}
