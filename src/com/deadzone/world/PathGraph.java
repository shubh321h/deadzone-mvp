package com.deadzone.world;

import com.deadzone.gl.Vec3;

import java.util.ArrayList;

/**
 * A* over the city's street open-points. The generator places open points in
 * a grid along the streets, so adjacency = points within 10.5 m. Cheap enough
 * (<=~300 nodes) to re-path per zombie every couple of seconds on mid-range
 * phones; paths are cached per zombie.
 */
public class PathGraph {
    public final Vec3[] nodes;
    public boolean[] blocked; // nodes inside geometry — A* routes around them
    private final int[][] adj;
    private final int[] gscore;
    private final int[] came;
    private final boolean[] closed;
    private final ArrayList<Integer> pathOut = new ArrayList<Integer>();

    public PathGraph(Vec3[] pts) {
        nodes = pts;
        int n = pts.length;
        adj = new int[n][];
        for (int i = 0; i < n; i++) {
            ArrayList<Integer> a = new ArrayList<Integer>();
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                if (pts[i].dist(pts[j]) < 10.6f) a.add(j);
            }
            adj[i] = new int[a.size()];
            for (int k = 0; k < a.size(); k++) adj[i][k] = a.get(k);
        }
        gscore = new int[n];
        came = new int[n];
        closed = new boolean[n];
    }

    public int nearest(Vec3 p) {
        int best = -1;
        float bd = 1e30f;
        for (int i = 0; i < nodes.length; i++) {
            float d = nodes[i].dist(p);
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    /** Nearest node to p that is actually walkable from p in one straight
     *  hop (clear line of sight). The plain nearest node can sit across a
     *  wall — pathing from it strands the walker against the wall. Scans
     *  nodes within ~28 m (building-block scale); falls back to the plain
     *  nearest. */
    public int nearestOpen(World w, Vec3 p) {
        int best = -1, seen = -1;
        float bd = 1e30f, sd = 1e30f;
        for (int i = 0; i < nodes.length; i++) {
            float d = nodes[i].dist(p);
            if (d < sd) { sd = d; seen = i; }
            if (d < bd && d < 28f && w.los(p, nodes[i])) { bd = d; best = i; }
        }
        return best >= 0 ? best : seen;
    }

    /** Node indices from a to b (inclusive). Empty list if unreachable. */
    public ArrayList<Integer> path(int a, int b) {
        pathOut.clear();
        int n = nodes.length;
        for (int i = 0; i < n; i++) { gscore[i] = Integer.MAX_VALUE; closed[i] = false; }
        if (a < 0 || b < 0 || a == b) return pathOut;
        gscore[a] = 0;
        // tiny priority queue (nodes <= ~300): linear scan is fine
        ArrayList<Integer> open = new ArrayList<Integer>();
        open.add(a);
        boolean found = false;
        while (!open.isEmpty()) {
            int bi = 0;
            int best = gscore[open.get(0)] + h(open.get(0), b);
            for (int i = 1; i < open.size(); i++) {
                int v = open.get(i);
                int f = gscore[v] + h(v, b);
                if (f < best) { best = f; bi = i; }
            }
            int cur = open.remove(bi);
            if (cur == b) { found = true; break; }
            closed[cur] = true;
            int[] nb = adj[cur];
            for (int i = 0; i < nb.length; i++) {
                int v = nb[i];
                if (closed[v] || (blocked != null && blocked[v])) continue;
                int ng = gscore[cur] + 10;
                if (ng < gscore[v]) {
                    gscore[v] = ng;
                    came[v] = cur;
                    boolean inList = false;
                    for (int k = 0; k < open.size(); k++)
                        if (open.get(k) == v) { inList = true; break; }
                    if (!inList) open.add(v);
                }
            }
        }
        if (!found) return pathOut;
        for (int v = b; v != a; v = came[v]) pathOut.add(v);
        pathOut.add(a);
        java.util.Collections.reverse(pathOut);
        return pathOut;
    }

    private int h(int i, int j) {
        float dx = nodes[i].x - nodes[j].x;
        float dz = nodes[i].z - nodes[j].z;
        return (int) (Math.sqrt(dx * dx + dz * dz) * 10f);
    }

    public Vec3 node(int i) { return nodes[i]; }

    /** Flag nodes buried inside world geometry after the city is built, so
     *  pathing never routes through a container or prop. */
    public void markBlocked(World w) {
        blocked = new boolean[nodes.length];
        for (int i = 0; i < nodes.length; i++) blocked[i] = w.insideSolid(nodes[i]);
    }
}
