package com.deadzone.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Data-driven mission definition (region-agnostic: regions just provide other mission sets). */
public class MissionData {
    public static class Wave { public float at; public Map<String, Integer> counts; public boolean wave; }
    public static class Obj { public String t; public int n; public String label; public String anchor; public String tag; }
    public static class Loot { public String t; public int n; public String at; public float[] off = new float[2]; }
    public static class Point { public String t; public String anchor; public float[] off; public String label; public String text; }

    public int id;
    public String name, loc, intro, story;
    public String region = "mumbai";      // world style variant (palette/anchors/layout)
    public String bossEnemy = "colossus"; // which enemy the boss objective spawns
    public boolean rain = true, flood, boss, autoEnd;
    public Map<String, Integer> ambient = new LinkedHashMap<String, Integer>();
    public List<Wave> waves = new ArrayList<Wave>();
    public List<Obj> objectives = new ArrayList<Obj>();
    public List<Loot> loot = new ArrayList<Loot>();
    public List<Point> points = new ArrayList<Point>();
    public String extractAnchor = "camp";
    public int rewardXp, rewardScrap, rewardMeds, rewardParts;
    public String rewardWeapon;
    public List<String> tutorial = new ArrayList<String>();

    public static MissionData fromJson(Map<String, Object> m) {
        MissionData d = new MissionData();
        d.id = Json.inum(m, "id", 0);
        d.name = Json.str(m, "name", "Mission");
        d.loc = Json.str(m, "loc", "");
        d.region = Json.str(m, "region", "mumbai");
        d.bossEnemy = Json.str(m, "bossEnemy", "colossus");
        d.intro = Json.str(m, "intro", "");
        d.story = Json.str(m, "story", "");
        d.rain = Json.bool(m, "rain", true);
        d.flood = Json.bool(m, "flood", false);
        d.boss = Json.bool(m, "boss", false);
        d.autoEnd = Json.bool(m, "autoEnd", false);
        Object amb = Json.get(m, "ambient");
        if (amb instanceof Map) {
            for (Map.Entry<String, Object> en : ((Map<String, Object>) amb).entrySet())
                d.ambient.put(en.getKey(), ((Number) en.getValue()).intValue());
        }
        Object wv = Json.get(m, "waves");
        if (wv instanceof List) {
            for (Object o : (List<?>) wv) {
                Map<String, Object> wm = Json.asObj(o);
                Wave w = new Wave();
                w.at = (float) Json.num(wm, "at", 0);
                w.wave = Json.bool(wm, "wave", false);
                w.counts = new LinkedHashMap<String, Integer>();
                for (String k : wm.keySet()) {
                    if (k.equals("at") || k.equals("wave")) continue;
                    w.counts.put(k, ((Number) wm.get(k)).intValue());
                }
                d.waves.add(w);
            }
        }
        Object ob = Json.get(m, "objectives");
        if (ob instanceof List) {
            for (Object o : (List<?>) ob) {
                Map<String, Object> om = Json.asObj(o);
                Obj o1 = new Obj();
                o1.t = Json.str(om, "t", "kill");
                o1.n = Json.inum(om, "n", 1);
                o1.label = Json.str(om, "label", o1.t);
                o1.anchor = Json.str(om, "anchor", null);
                o1.tag = Json.str(om, "tag", null);
                d.objectives.add(o1);
            }
        }
        Object lt = Json.get(m, "loot");
        if (lt instanceof List) {
            for (Object o : (List<?>) lt) {
                Map<String, Object> lm = Json.asObj(o);
                Loot l = new Loot();
                l.t = Json.str(lm, "t", "scrap");
                l.n = Json.inum(lm, "n", 1);
                l.at = Json.str(lm, "at", "spawn");
                Object lof = Json.get(lm, "off");
                if (lof instanceof List && ((List<?>) lof).size() >= 2) {
                    l.off[0] = ((Number) ((List<?>) lof).get(0)).floatValue();
                    l.off[1] = ((Number) ((List<?>) lof).get(1)).floatValue();
                }
                d.loot.add(l);
            }
        }
        Object pt = Json.get(m, "points");
        if (pt instanceof List) {
            for (Object o : (List<?>) pt) {
                Map<String, Object> pm = Json.asObj(o);
                Point p = new Point();
                p.t = Json.str(pm, "t", "note");
                p.anchor = Json.str(pm, "anchor", "spawn");
                float[] off = new float[2];
                Object of = Json.get(pm, "off");
                if (of instanceof List && ((List<?>) of).size() >= 2) {
                    off[0] = ((Number) ((List<?>) of).get(0)).floatValue();
                    off[1] = ((Number) ((List<?>) of).get(1)).floatValue();
                }
                p.off = off;
                p.label = Json.str(pm, "label", p.t);
                p.text = Json.str(pm, "text", "");
                d.points.add(p);
            }
        }
        d.extractAnchor = Json.str(m, "extract", "camp");
        d.rewardXp = Json.inum(m, "rewardXp", 100);
        d.rewardScrap = Json.inum(m, "rewardScrap", 50);
        d.rewardMeds = Json.inum(m, "rewardMeds", 0);
        d.rewardParts = Json.inum(m, "rewardParts", 0);
        d.rewardWeapon = Json.str(m, "rewardWeapon", null);
        Object tu = Json.get(m, "tutorial");
        if (tu instanceof List)
            for (Object o : (List<?>) tu) d.tutorial.add(o.toString());
        return d;
    }
}
