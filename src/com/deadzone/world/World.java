package com.deadzone.world;

import com.deadzone.data.MissionData;
import com.deadzone.gl.Renderer;
import com.deadzone.gl.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Level geometry for the Mumbai vertical slice. The city is generated
 * deterministically (fixed seed) so every mission shares the same streets —
 * continuity matters. Colliders are AABBs; navigation uses open street points
 * plus line-of-sight raycasts.
 */
public class World {
    public static final int BOUND = 124;

    public final List<Aabb> solids = new ArrayList<Aabb>();
    public Vec3[] open = new Vec3[0];
    public final Map<String, Vec3> anchors = new LinkedHashMap<String, Vec3>();
    public boolean rain = true, flood;
    public String region = "mumbai";
    public int mode; // 0 = city, 1 = safehouse
    public PathGraph graph; // street network for enemy pathfinding
    public Random rnd;

    // ---------------- builders ----------------

    public void buildCity(MissionData md, Renderer r) {
        solids.clear();
        anchors.clear();
        mode = 0;
        rain = md.rain;
        flood = md.flood;
        region = md.region;
        // same city for every mission of a region, distinct per region
        rnd = new Random("delhi".equals(region) ? 90210L : 731217L);

        // open street grid (streets between 40m blocks)
        ArrayList<Vec3> pts = new ArrayList<Vec3>();
        int[] ln = {-90, -50, -10, 30, 70, 110};
        for (int l : ln) {
            for (int p = -120; p <= 120; p += 10) {
                pts.add(new Vec3(l, 0, p));
                pts.add(new Vec3(p, 0, l));
            }
        }
        open = pts.toArray(new Vec3[0]);
        graph = new PathGraph(open);

        anchors.put("spawn", new Vec3(0, 0, 8));
        anchors.put("safehouse", new Vec3(10, 0, -66));
        anchors.put("safehouse_door", new Vec3(10, 0, -57.5f));
        if ("delhi".equals(region)) {
            // Delhi layout: Connaught Place ring, the Ridge, cantonment arena
            anchors.put("market", new Vec3(-60, 0, -70));   // Connaught Place bazaar
            anchors.put("camp", new Vec3(60, 0, -60));      // the Ridge camp
            anchors.put("docks", new Vec3(30, 0, 105));     // unused in Delhi missions
            anchors.put("station", new Vec3(100, 0, 10));   // India Gate arena
            anchors.put("tower", new Vec3(121, 0, 40));
            anchors.put("flood", new Vec3(0, 0, -100));     // unused in Delhi missions
        } else {
            anchors.put("market", new Vec3(-70, 0, 20));
            anchors.put("camp", new Vec3(60, 0, 70));
            anchors.put("docks", new Vec3(30, 0, 105));
            anchors.put("station", new Vec3(106, 0, 0));
            anchors.put("tower", new Vec3(121, 0, 40));
            anchors.put("flood", new Vec3(0, 0, -100));
        }

        // make sure every objective anchor is a standable spot
        for (java.util.Map.Entry<String, Vec3> e : anchors.entrySet()) {
            if (e.getKey().equals("safehouse")) continue; // building center, used for placement
            Vec3 q = e.getValue().clone();
            resolve(q, 0.5f);
            if (e.getValue().dist(q) > 0.3f) anchors.put(e.getKey(), q);
        }

        r.beginStatic();
        // ground: wet asphalt in Mumbai, dry dust in Delhi
        float gc = flood ? 0.13f : ("delhi".equals(region) ? 0.15f : 0.10f);
        if ("delhi".equals(region))
            r.addBox(0, -0.25f, 0, 0, 0, 320, 0.5f, 320, gc + 0.045f, gc + 0.02f, gc);
        else
            r.addBox(0, -0.25f, 0, 0, 0, 320, 0.5f, 320, gc, gc + 0.01f, gc + 0.03f);
        if (flood) {
            r.addBox(0, 0.06f, -102, 0, 0, 280, 0.22f, 46, 0.05f, 0.08f, 0.12f); // flooded north strip
        }

        drawBuildings(r);
        drawSafehouseTower(r);
        drawMarket(r);
        drawCamp(r);
        if (!"delhi".equals(region)) drawDocks(r); // Mumbai dockyard visual only
        drawStation(r);
        drawStreetProps(r);
        r.endStatic();
        if (graph != null) graph.markBlocked(this);
    }

    private boolean clearBlock(float bx, float bz) {
        Vec3 sh = anchors.get("safehouse");
        Vec3 mk = anchors.get("market");
        Vec3 cp = anchors.get("camp");
        Vec3 sp = anchors.get("spawn");
        float t = new Vec3(bx, 0, bz).dist(sh);
        if (t < 26) return true;
        t = new Vec3(bx, 0, bz).dist(mk);
        if (t < 24) return true;
        t = new Vec3(bx, 0, bz).dist(cp);
        if (t < 20) return true;
        t = new Vec3(bx, 0, bz).dist(sp);
        if (t < 24) return true;
        if ("delhi".equals(region)) {
            if (bx > 80 && bz > -30 && bz < 50) return true; // cantonment arena
            if (bz < -40 && bx > 30) return true;            // the Ridge
            return false;
        }
        if (bx > 80 && Math.abs(bz) < 62) return true;  // railway yard
        if (bz > 80) return true;                       // dockyard
        return false;
    }

    private void drawBuildings(Renderer r) {
        int[] cx = {-110, -70, -30, 10, 50, 90};
        boolean delhi = "delhi".equals(region);
        float[][] pal = new float[][]{
            {0.28f, 0.28f, 0.30f}, {0.32f, 0.26f, 0.22f}, {0.30f, 0.30f, 0.26f},
            {0.24f, 0.27f, 0.30f}, {0.33f, 0.31f, 0.28f}, {0.26f, 0.24f, 0.28f},
        };
        if (delhi) // sandstone, concrete, sun-bleached plaster
            pal = new float[][]{ {0.45f, 0.38f, 0.28f}, {0.40f, 0.34f, 0.26f}, {0.36f, 0.32f, 0.30f},
                {0.30f, 0.28f, 0.26f}, {0.44f, 0.40f, 0.32f}, {0.34f, 0.30f, 0.24f} };
        Vec3 core = delhi ? anchors.get("market") : new Vec3(0, 0, -30); // dense tall core
        for (int bx : cx) {
            for (int bz : cx) {
                if (clearBlock(bx, bz)) continue;
                int n = 1 + rnd.nextInt(3);
                for (int i = 0; i < n; i++) {
                    float w = 10 + rnd.nextFloat() * 13;
                    float d = 10 + rnd.nextFloat() * 13;
                    // Delhi: lower, wider sprawl; Mumbai: tower blocks
                    float h = delhi ? 6 + rnd.nextFloat() * 9 : 8 + rnd.nextFloat() * 13;
                    float dx = new Vec3(bx, 0, bz).dist(core);
                    if (dx < 70) h += rnd.nextFloat() * 8;
                    float x = bx + (rnd.nextFloat() - 0.5f) * 12;
                    float z = bz + (rnd.nextFloat() - 0.5f) * 12;
                    float[] c = pal[rnd.nextInt(pal.length)];
                    r.addBox(x, h / 2f, z, 0, 0, w, h, d, c[0], c[1], c[2]);
                    // weathering slabs on faces
                    int sl = 2 + rnd.nextInt(3);
                    for (int s = 0; s < sl; s++) {
                        float side = rnd.nextFloat() * 4;
                        float yy = h * (0.25f + rnd.nextFloat() * 0.55f);
                        float hh = h * (0.25f + rnd.nextFloat() * 0.4f);
                        float ww = w * (0.15f + rnd.nextFloat() * 0.3f);
                        float dd = d * (0.15f + rnd.nextFloat() * 0.3f);
                        float fx = side < 1 ? x - w / 2 - 0.07f : (side < 2 ? x + w / 2 + 0.07f : x);
                        float fz = side < 2 ? z + (rnd.nextFloat() - 0.5f) * d * 0.6f
                                : (side < 3 ? z - d / 2 - 0.07f : z + d / 2 + 0.07f);
                        float sw = side < 2 ? 0.14f : ww;
                        float sd = side < 2 ? ww : 0.14f;
                        float dc = 0.75f + rnd.nextFloat() * 0.3f;
                        r.addBox(fx, yy, fz, 0, 0, sw, hh, sd, c[0] * dc, c[1] * dc, c[2] * dc);
                    }
                    // rooftop tank
                    if (rnd.nextFloat() < 0.5f) {
                        r.addBox(x + (rnd.nextFloat() - 0.5f) * w * 0.4f, h + 0.6f,
                                z + (rnd.nextFloat() - 0.5f) * d * 0.4f, 0, 0,
                                1.6f, 1.2f, 1.6f, c[0] * 0.8f, c[1] * 0.8f, c[2] * 0.8f);
                    }
                    solids.add(Aabb.box(x, h / 2f, z, w, h, d));
                }
            }
        }
    }

    private void drawSafehouseTower(Renderer r) {
        float x = 10, z = -66;
        r.addBox(x, 17, z, 0, 0, 14, 34, 14, 0.30f, 0.30f, 0.33f);
        r.addBox(x, 1.6f, z + 7.1f, 0, 0, 3, 3.2f, 0.3f, 0.15f, 0.14f, 0.12f); // door
        r.addBox(x, 35, z, 0, 0, 1.6f, 1.6f, 1.6f, 0.2f, 1.0f, 0.3f);           // beacon
        r.addBox(x, 41, z, 0, 0, 0.5f, 12, 0.5f, 0.12f, 0.7f, 0.22f);           // light beam
        solids.add(Aabb.box(x, 17, z, 14, 34, 14));
    }

    private void drawMarket(Renderer r) {
        float mx = -70, mz = 20;
        float[][] awn = {{0.15f, 0.4f, 0.4f}, {0.6f, 0.35f, 0.1f}, {0.7f, 0.6f, 0.15f}, {0.55f, 0.15f, 0.15f}};
        for (int i = 0; i < 8; i++) {
            float ang = rnd.nextFloat() * 6.283f;
            float rr = 3 + rnd.nextFloat() * 11;
            float x = mx + (float) Math.cos(ang) * rr;
            float z = mz + (float) Math.sin(ang) * rr;
            float yaw = rnd.nextFloat() * 3.14f;
            float[] a = awn[i % awn.length];
            r.addBox(x, 1.2f, z, yaw, 0, 3.2f, 2.4f, 3, 0.3f, 0.28f, 0.25f);
            r.addBox(x, 2.5f, z, yaw, 0, 3.8f, 0.12f, 2.2f, a[0], a[1], a[2]);
            solids.add(Aabb.box(x, 1.2f, z, 3.4f, 2.4f, 3.2f));
        }
        for (int i = 0; i < 6; i++) {
            float x = mx + (rnd.nextFloat() - 0.5f) * 20;
            float z = mz + (rnd.nextFloat() - 0.5f) * 20;
            r.addBox(x, 0.4f, z, rnd.nextFloat() * 3, 0, 0.8f, 0.8f, 0.8f, 0.4f, 0.32f, 0.2f);
        }
    }

    private void drawCamp(Renderer r) {
        float cxp = 60, czp = 70;
        for (int i = 0; i < 4; i++) {
            float ang = i * 1.7f + 0.4f;
            float x = cxp + (float) Math.cos(ang) * 6;
            float z = czp + (float) Math.sin(ang) * 6;
            r.addBox(x, 0.75f, z, ang, 0, 3.2f, 1.5f, 3.8f, 0.18f, 0.28f, 0.18f);
            solids.add(Aabb.box(x, 0.75f, z, 3.4f, 1.5f, 4f));
        }
        for (int i = 0; i < 3; i++)
            r.addBox(cxp - 8 + i * 2, 0.4f, czp - 6, i, 0, 0.8f, 0.8f, 0.8f, 0.4f, 0.32f, 0.2f);
        // fire
        for (int i = 0; i < 5; i++) {
            float a = i * 1.257f;
            r.addBox(cxp + (float) Math.cos(a) * 0.6f, 0.12f, czp + (float) Math.sin(a) * 0.6f, 0, 0, 0.4f, 0.25f, 0.4f, 0.25f, 0.24f, 0.22f);
        }
        r.addBox(cxp, 0.45f, czp, 0, 0, 0.7f, 0.9f, 0.7f, 1.0f, 0.55f, 0.15f);
        r.addBox(cxp, 0.04f, czp, 0, 0, 4.5f, 0.08f, 4.5f, 0.30f, 0.18f, 0.08f);
        r.addBox(cxp + 9, 0.7f, czp - 3, 0, 0, 1f, 1.4f, 1f, 0.2f, 0.3f, 0.45f);
    }

    private void drawDocks(Renderer r) {
        r.addBox(30, 0.15f, 107, 0, 0, 120, 0.3f, 24, 0.18f, 0.18f, 0.19f); // pier
        r.addBox(0, 0.05f, 150, 0, 0, 320, 0.2f, 80, 0.03f, 0.07f, 0.12f);  // harbor water
        float[][] cc = {{0.55f, 0.2f, 0.15f}, {0.2f, 0.35f, 0.55f}, {0.2f, 0.5f, 0.25f}, {0.6f, 0.45f, 0.1f}, {0.45f, 0.15f, 0.4f}};
        for (int i = 0; i < 10; i++) {
            float x = -20 + (i % 5) * 12;
            float z = i / 5 == 0 ? 100 : 112;
            float[] c = cc[i % cc.length];
            r.addBox(x, 1.3f, z, 0, 0, 6, 2.6f, 2.4f, c[0], c[1], c[2]);
            solids.add(Aabb.box(x, 1.3f, z, 6.2f, 2.6f, 2.6f));
            if (rnd.nextFloat() < 0.35f) {
                float[] c2 = cc[(i + 2) % cc.length];
                r.addBox(x, 3.9f, z, 0, 0, 6, 2.6f, 2.4f, c2[0], c2[1], c2[2]);
            }
        }
        for (int i = 0; i < 3; i++) {
            float x = 0 + i * 30;
            r.addBox(x, 5, 117, 0, 0, 0.8f, 10, 0.8f, 0.35f, 0.2f, 0.15f);
            r.addBox(x, 10.2f, 117, 0, 0, 14, 0.8f, 0.8f, 0.35f, 0.2f, 0.15f);
            r.addBox(x, 1.4f, 117, 0, 0, 2.2f, 2.8f, 1.4f, 0.3f, 0.18f, 0.13f);
            solids.add(Aabb.box(x, 5, 117, 1.2f, 10, 1.2f));
        }
    }

    private void drawStation(Renderer r) {
        r.addBox(108, 0.02f, 0, 0, 0, 44, 0.06f, 110, 0.09f, 0.09f, 0.10f);
        for (int tr = 0; tr < 3; tr++) {
            float tz = -30 + tr * 30;
            r.addBox(108, 0.12f, tz - 0.7f, 0, 0, 42, 0.12f, 0.12f, 0.3f, 0.3f, 0.32f);
            r.addBox(108, 0.12f, tz + 0.7f, 0, 0, 42, 0.12f, 0.12f, 0.3f, 0.3f, 0.32f);
            for (float x = 88; x <= 128; x += 3)
                r.addBox(x, 0.06f, tz, 0, 0, 0.3f, 0.1f, 1.9f, 0.12f, 0.10f, 0.09f);
        }
        // deranged carriage
        r.addBox(98, 1.6f, -38, 0.3f, 0, 12, 3.2f, 3, 0.35f, 0.2f, 0.15f);
        r.addBox(98, 3.6f, -38, 0.3f, 0, 8, 1, 2.8f, 0.3f, 0.18f, 0.13f);
        solids.add(Aabb.box(98, 1.6f, -38, 12.5f, 4.5f, 3.4f));
        // signal tower (M5 evidence)
        r.addBox(121, 6, 40, 0, 0, 1.2f, 12, 1.2f, 0.25f, 0.25f, 0.28f);
        r.addBox(121, 13.5f, 40, 0, 0, 3.2f, 2.5f, 3.2f, 0.3f, 0.3f, 0.34f);
        r.addBox(121, 15.2f, 40, 0, 0, 0.5f, 0.5f, 0.5f, 1.0f, 0.2f, 0.15f);
        solids.add(Aabb.box(121, 6, 40, 1.4f, 12, 1.4f));
    }

    private Vec3 randStreetPoint(float minDistFromSpawn) {
        for (int i = 0; i < 40; i++) {
            Vec3 p = open[rnd.nextInt(open.length)];
            if (p.dist(anchors.get("spawn")) > minDistFromSpawn) return p;
        }
        return anchors.get("spawn");
    }

    private void drawStreetProps(Renderer r) {
        // abandoned vehicles
        float[][] vc = {{0.45f, 0.45f, 0.5f}, {0.5f, 0.3f, 0.2f}, {0.3f, 0.4f, 0.3f}, {0.55f, 0.55f, 0.5f}};
        for (int i = 0; i < 9; i++) {
            Vec3 p = randStreetPointOffNode(14);
            float yaw = (rnd.nextInt(4)) * 1.5708f;
            float[] c = vc[rnd.nextInt(vc.length)];
            r.addBox(p.x, 0.75f, p.z, yaw, 0, 4.4f, 1.5f, 1.9f, c[0], c[1], c[2]);
            float fx = -((float) Math.sin(yaw)), fz = -((float) Math.cos(yaw));
            r.addBox(p.x + fx * 0.35f, 1.75f, p.z + fz * 0.35f, yaw, 0, 2.4f, 0.9f, 1.8f, c[0] * 0.7f, c[1] * 0.7f, c[2] * 0.7f);
            solids.add(Aabb.box(p.x, 0.85f, p.z, 4.6f, 1.7f, 2f));
        }
        // street lamps with fake light pools
        for (int i = 0; i < 10; i++) {
            Vec3 p = randStreetPoint(10);
            float px = p.x + 6, pz = p.z + 6;
            r.addBox(px, 3.25f, pz, 0, 0, 0.22f, 6.5f, 0.22f, 0.15f, 0.15f, 0.16f);
            r.addBox(px, 6.3f, pz, 0, 0, 0.18f, 0.18f, 1.4f, 0.15f, 0.15f, 0.16f);
            r.addBox(px, 6.1f, pz + 0.7f, 0, 0, 0.6f, 0.2f, 0.6f, 1.0f, 0.9f, 0.55f);
            r.addBox(px, 0.04f, pz + 0.7f, 0, 0, 2.4f, 0.08f, 2.4f, 0.28f, 0.24f, 0.13f);
            solids.add(Aabb.box(px, 3.25f, pz, 0.5f, 6.5f, 0.5f));
        }
        // barricades
        for (int i = 0; i < 10; i++) {
            Vec3 p = randStreetPointOffNode(8);
            float yaw = rnd.nextFloat() * 3.14f;
            r.addBox(p.x, 0.55f, p.z, yaw + 0.35f, 0, 2.6f, 1.1f, 0.18f, 0.35f, 0.25f, 0.15f);
            r.addBox(p.x, 0.55f, p.z, yaw - 0.35f, 0, 2.6f, 1.1f, 0.18f, 0.30f, 0.22f, 0.13f);
            solids.add(Aabb.box(p.x, 0.55f, p.z, 2.6f, 1.2f, 0.9f));
        }
        // dumpsters
        for (int i = 0; i < 8; i++) {
            Vec3 p = randStreetPointOffNode(8);
            r.addBox(p.x, 0.65f, p.z, rnd.nextFloat() * 3, 0, 2.4f, 1.3f, 1.5f, 0.2f, 0.22f, 0.2f);
            solids.add(Aabb.box(p.x, 0.65f, p.z, 2.5f, 1.3f, 1.6f));
        }
        // rubble
        for (int i = 0; i < 18; i++) {
            Vec3 p = randStreetPoint(6);
            float s = 0.4f + rnd.nextFloat() * 0.9f;
            r.addBox(p.x, s / 2f, p.z, rnd.nextFloat() * 3, 0, s, s * 0.8f, s, 0.2f, 0.2f, 0.21f);
        }
    }

    /** Like randStreetPoint, but biased off the node centers: blocking props
     *  (vehicles, barricades, dumpsters) are shifted to a segment middle so
     *  every A* node stays physically walkable. */
    private Vec3 randStreetPointOffNode(float minDistFromSpawn) {
        Vec3 p = randStreetPoint(minDistFromSpawn);
        float ox = (rnd.nextFloat() < 0.5f ? 1 : -1) * (3.5f + rnd.nextFloat() * 1.5f);
        float oz = (rnd.nextFloat() < 0.5f ? 1 : -1) * (3.5f + rnd.nextFloat() * 1.5f);
        Vec3 q = new Vec3(p.x + ox, 0, p.z + oz);
        keepIn(q);
        if (!insideSolid(q)) return q; // else fall back to the node itself
        return p;
    }

    // ---------------- safehouse ----------------

    public void buildSafehouse(int level, Renderer r) {
        solids.clear();
        anchors.clear();
        anchors.put("center", new Vec3(0, 0, 0));
        mode = 1;
        rain = false;
        flood = false;
        open = new Vec3[]{new Vec3(0, 0, 0)};
        rnd = new Random(42L);

        r.beginStatic();
        r.addBox(0, -0.15f, 0, 0, 0, 26, 0.3f, 26, 0.14f, 0.13f, 0.11f);
        // walls
        r.addBox(0, 2.5f, -12.9f, 0, 0, 26, 5, 0.4f, 0.20f, 0.18f, 0.15f);
        r.addBox(0, 2.5f, 12.9f, 0, 0, 26, 5, 0.4f, 0.20f, 0.18f, 0.15f);
        r.addBox(-12.9f, 2.5f, 0, 0, 0, 0.4f, 5, 26, 0.19f, 0.17f, 0.14f);
        r.addBox(12.9f, 2.5f, 0, 0, 0, 0.4f, 5, 26, 0.19f, 0.17f, 0.14f);
        solids.add(Aabb.box(0, 2.5f, -12.9f, 26, 5, 0.4f));
        solids.add(Aabb.box(0, 2.5f, 12.9f, 26, 5, 0.4f));
        solids.add(Aabb.box(-12.9f, 2.5f, 0, 0.4f, 5, 26));
        solids.add(Aabb.box(12.9f, 2.5f, 0, 0.4f, 5, 26));
        // window moonlight
        r.addBox(-4, 3, -12.85f, 0, 0, 3, 1.8f, 0.15f, 0.35f, 0.55f, 0.75f);
        // bed
        r.addBox(-8, 0.35f, 8, 0, 0, 2.4f, 0.7f, 1.3f, 0.35f, 0.2f, 0.15f);
        r.addBox(-8.8f, 0.8f, 8, 0, 0, 0.7f, 0.25f, 1f, 0.7f, 0.7f, 0.65f);
        solids.add(Aabb.box(-8, 0.35f, 8, 2.5f, 0.7f, 1.4f));
        // shelf + crate
        r.addBox(8, 1.2f, 10.5f, 0, 0, 2.2f, 2.4f, 0.5f, 0.3f, 0.24f, 0.16f);
        r.addBox(8, 0.4f, 10.5f, 0, 0, 0.8f, 0.8f, 0.8f, 0.4f, 0.32f, 0.2f);
        solids.add(Aabb.box(8, 1.2f, 10.5f, 2.3f, 2.4f, 0.6f));
        // table with map
        r.addBox(0, 0.4f, 5, 0, 0, 2.2f, 0.8f, 1.2f, 0.3f, 0.24f, 0.16f);
        r.addBox(0, 0.82f, 5, 0, 0, 2f, 0.04f, 1f, 0.6f, 0.55f, 0.4f);
        solids.add(Aabb.box(0, 0.4f, 5, 2.3f, 0.8f, 1.3f));
        // campfire
        for (int i = 0; i < 5; i++) {
            float a = i * 1.257f;
            r.addBox((float) Math.cos(a) * 0.55f, 0.12f, -3 + (float) Math.sin(a) * 0.55f, 0, 0, 0.4f, 0.25f, 0.4f, 0.25f, 0.24f, 0.22f);
        }
        r.addBox(0, 0.45f, -3, 0, 0, 0.7f, 0.9f, 0.7f, 1.0f, 0.55f, 0.15f);
        r.addBox(0, 0.04f, -3, 0, 0, 4.5f, 0.08f, 4.5f, 0.30f, 0.18f, 0.08f);
        // barrel + crates
        r.addBox(10, 0.7f, -8, 0, 0, 1f, 1.4f, 1f, 0.2f, 0.3f, 0.45f);
        solids.add(Aabb.box(10, 0.7f, -8, 1.1f, 1.4f, 1.1f));
        r.addBox(-10, 0.5f, -8, 0.3f, 0, 1.2f, 1, 1.2f, 0.4f, 0.32f, 0.2f);
        solids.add(Aabb.box(-10, 0.5f, -8, 1.3f, 1f, 1.3f));
        // progression furniture
        if (level >= 2) {
            r.addBox(10.5f, 0.6f, 4, 0, 0, 1.6f, 1.2f, 1f, 0.25f, 0.25f, 0.28f);
            r.addBox(10.5f, 1.4f, 4, 0, 0, 0.3f, 0.5f, 0.3f, 0.4f, 0.4f, 0.42f);
            solids.add(Aabb.box(10.5f, 0.6f, 4, 1.7f, 1.2f, 1.1f));
        }
        if (level >= 3) {
            r.addBox(-11, 0.35f, 2, 0, 0, 0.5f, 0.7f, 0.5f, 0.4f, 0.2f, 0.12f);
            r.addBox(-11, 1f, 2, 0, 0, 0.9f, 0.9f, 0.9f, 0.2f, 0.45f, 0.2f);
            r.addBox(-11, 0.35f, 4.5f, 0, 0, 0.5f, 0.7f, 0.5f, 0.4f, 0.2f, 0.12f);
            r.addBox(-11, 0.9f, 4.5f, 0, 0, 0.8f, 0.7f, 0.8f, 0.2f, 0.45f, 0.2f);
        }
        if (level >= 4) {
            r.addBox(-12.6f, 1f, 0, 0, 0, 0.3f, 1.8f, 2.6f, 0.3f, 0.26f, 0.2f);
            for (int i = 0; i < 3; i++)
                r.addBox(-12.5f, 0.7f + i * 0.4f, -0.8f + i * 0.8f, 0.5f * i, 0, 0.7f, 0.15f, 0.15f, 0.15f, 0.15f, 0.16f);
            solids.add(Aabb.box(-12.6f, 1f, 0, 0.4f, 1.8f, 2.7f));
        }
        if (level >= 5) {
            r.addBox(6, 2.2f, 12.7f, 0, 0, 2.6f, 1.8f, 0.12f, 0.5f, 0.45f, 0.35f);
            for (int i = 0; i < 3; i++)
                r.addBox(5.2f + i * 0.8f, 2f + (i % 2) * 0.5f, 12.6f, 0, 0, 0.08f, 0.08f, 0.08f, 1f, 0.3f, 0.2f);
        }
        // NPCs (original characters: Dr. Rhea Iyer, Kabir)
        r.addBox(3, 0.95f, 1.5f, -0.4f, 0, 0.55f, 0.9f, 0.35f, 0.85f, 0.85f, 0.8f); // Rhea (white coat)
        r.addBox(3, 1.6f, 1.5f, -0.4f, 0, 0.32f, 0.32f, 0.32f, 0.8f, 0.68f, 0.58f);
        solids.add(Aabb.box(3, 0.95f, 1.5f, 0.6f, 1f, 0.4f));
        r.addBox(-4, 0.95f, -0.5f, 0.5f, 0, 0.55f, 0.9f, 0.35f, 0.35f, 0.28f, 0.2f); // Kabir
        r.addBox(-4, 1.6f, -0.5f, 0.5f, 0, 0.32f, 0.32f, 0.32f, 0.55f, 0.4f, 0.3f);
        solids.add(Aabb.box(-4, 0.95f, -0.5f, 0.6f, 1f, 0.4f));
        r.endStatic();
    }

    // ---------------- queries ----------------

    public boolean los(Vec3 a, Vec3 b) {
        Vec3 o = new Vec3(a.x, 1.5f, a.z);
        Vec3 d = new Vec3(b.x - o.x, 0f, b.z - o.z);
        float L = d.len();
        if (L < 0.01f) return true;
        d.mul(1f / L);
        float tHit = rayDist(o, d, L);
        return tHit >= L - 0.3f;
    }

    /** True if the point lies inside any solid (e.g. buried inside a building). */
    public boolean insideSolid(Vec3 p) {
        for (Aabb b : solids)
            if (p.x > b.x0 && p.x < b.x1 && p.y < b.y1 && p.z > b.z0 && p.z < b.z1) return true;
        return false;
    }

    public float rayDist(Vec3 o, Vec3 d, float max) {
        float best = max;
        for (Aabb a : solids) {
            float t = a.ray(o, d, best);
            if (t >= 0 && t < best) best = t;
        }
        return best;
    }

    public void resolve(Vec3 p, float rad) {
        for (Aabb a : solids) a.pushOut(p, rad);
        keepIn(p);
    }

    public void keepIn(Vec3 p) {
        if (mode == 1) {
            p.x = clamp(p.x, -11.5f, 11.5f);
            p.z = clamp(p.z, -11.5f, 11.5f);
            return;
        }
        p.x = clamp(p.x, -124f, 124f);
        p.z = clamp(p.z, -124f, 116f);
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public Vec3 spawnPoint(Vec3 center, float minR, float maxR, Random r) {
        for (int i = 0; i < 40; i++) {
            Vec3 p = open[r.nextInt(open.length)];
            float d = p.dist(center);
            if (d >= minR && d <= maxR) {
                Vec3 q = p.clone();
                q.x += (r.nextFloat() - 0.5f) * 6;
                q.z += (r.nextFloat() - 0.5f) * 6;
                keepIn(q);
                // never spawn inside geometry or pocketed off the street grid
                if (insideSolid(q) || !los(q, p)) continue;
                return q;
            }
        }
        return null;
    }

    /** True if p can be walked to from the street network: p is not inside
     *  geometry, has room to stand next to, and some street node within
     *  maxNodeD has clear line to it. Guards against objectives buried in
     *  pockets or parked against a prop whose node it shadows. */
    public boolean approachable(Vec3 p, float maxNodeD) {
        if (!standable(p)) return false;
        if (graph == null) return true;
        for (int i = 0; i < graph.nodes.length; i++) {
            if (graph.nodes[i].dist(p) > maxNodeD) continue;
            if (los(p, graph.nodes[i])) return true;
        }
        return false;
    }

    /** True if the player could physically occupy a spot right next to p:
     *  p itself is out of geometry and at least half the probe directions
     *  around it are clear (not pinched inside a collider corner). */
    public boolean standable(Vec3 p) {
        if (insideSolid(p)) return false;
        int free = 0;
        for (int k = 0; k < 8; k++) {
            float a = k * 0.785398f;
            Vec3 q = new Vec3(p.x + (float) Math.cos(a) * 1.2f, 0, p.z + (float) Math.sin(a) * 1.2f);
            keepIn(q);
            if (!insideSolid(q)) free++;
        }
        return free >= 4;
    }

    /** Nudges p until approachable: first tries sliding toward each nearby
     *  street node, then probes ring positions around p, finally falls back
     *  to pushing p out of geometry. Returns a fresh vector, p untouched. */
    public Vec3 approachPoint(Vec3 p, float maxNodeD) {
        if (approachable(p, maxNodeD)) return p.clone();
        if (graph != null) {
            for (int n = 0; n < graph.nodes.length; n++) {
                Vec3 node = graph.nodes[n];
                if (node.dist(p) > maxNodeD + 4f) continue;
                for (int s = 1; s <= 8; s++) {
                    Vec3 q = new Vec3(p.x + (node.x - p.x) * s / 8f, 0,
                            p.z + (node.z - p.z) * s / 8f);
                    if (insideSolid(q)) break;
                    if (node.dist(q) <= maxNodeD && los(q, node) && standable(q)) return q;
                }
            }
        }
        for (int k = 0; k < 8; k++) { // ring probe: any standable, street-linked spot nearby works
            float a = k * 0.785398f;
            for (float rr = 0.9f; rr <= 2.7f; rr += 0.9f) {
                Vec3 q = new Vec3(p.x + (float) Math.cos(a) * rr, 0, p.z + (float) Math.sin(a) * rr);
                keepIn(q);
                if (approachable(q, 20f)) return q;
            }
        }
        Vec3 f = graph.nodes[graph.nearest(p)].clone(); // the street itself
        resolve(f, 0.8f);
        return f;
    }

    public Vec3 anchor(String k) { return anchors.get(k); }
}
