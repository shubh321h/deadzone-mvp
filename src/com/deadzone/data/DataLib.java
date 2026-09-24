package com.deadzone.data;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads all game configuration (weapons, enemies, missions, upgrade costs).
 *  Content is 100% data-driven: add weapons/enemies/missions via JSON, no code changes. */
public class DataLib {
    public static final String[] AMMO_TYPES = {"5.56", "9mm", "12g", ".338"};

    public WeaponData[] weapons;
    public Map<String, WeaponData> wmap = new LinkedHashMap<String, WeaponData>();
    public Map<String, EnemyData> enemies = new LinkedHashMap<String, EnemyData>();
    public MissionData[] missions;
    // upgrade cost[axis][level] = {scrap, parts}; axis: 0 dmg, 1 mag, 2 acc
    public int[][][] upgCost;
    public float[] upgPer = {0.12f, 0.15f, 0.10f};

    public static int ammoIndex(String type) {
        if (type == null) return 1;
        for (int i = 0; i < AMMO_TYPES.length; i++)
            if (AMMO_TYPES[i].equals(type)) return i;
        // tolerate shorthand from mission data (ammo_556, ammo_338)
        if (type.equals("556")) return 0;
        if (type.equals("338")) return 3;
        return 1;
    }

    public WeaponData w(String id) { return wmap.get(id); }
    public EnemyData e(String id) { return enemies.get(id); }
    public MissionData m(int id) {
        for (MissionData m : missions) if (m.id == id) return m;
        return null;
    }

    private static Map<String, Object> parse(InputStream in) {
        try {
            Object o = Json.parse(new InputStreamReader(in, "UTF-8"));
            return Json.asObj(o);
        } catch (Exception e) {
            throw new RuntimeException("json parse failed", e);
        }
    }

    private void loadAll(File dir) {
        try {
            loadAllBody(dir);
        } catch (java.io.IOException e) {
            throw new RuntimeException("config load failed", e);
        }
    }

    private void loadAllBody(File dir) throws java.io.IOException {
        weapons = new WeaponData[0];
        Map<String, Object> wj = parse(new FileInputStream(new File(dir, "weapons.json")));
        List<Object> wl = Json.asArr(Json.get(wj, "weapons"));
        weapons = new WeaponData[wl.size()];
        for (int i = 0; i < wl.size(); i++) {
            weapons[i] = WeaponData.fromJson(Json.asObj(wl.get(i)));
            wmap.put(weapons[i].id, weapons[i]);
        }
        Map<String, Object> ej = parse(new FileInputStream(new File(dir, "enemies.json")));
        Object em = Json.get(ej, "enemies");
        for (Map.Entry<String, Object> en : ((Map<String, Object>) em).entrySet()) {
            EnemyData e = EnemyData.fromJson(Json.asObj(en.getValue()));
            if (e.id == null || e.id.equals("?")) e.id = en.getKey(); // id is the type discriminator
            enemies.put(en.getKey(), e);
        }
        Map<String, Object> mj = parse(new FileInputStream(new File(dir, "missions.json")));
        List<Object> ml = Json.asArr(Json.get(mj, "missions"));
        missions = new MissionData[ml.size()];
        for (int i = 0; i < ml.size(); i++) missions[i] = MissionData.fromJson(Json.asObj(ml.get(i)));
        Map<String, Object> uj = parse(new FileInputStream(new File(dir, "upgrades.json")));
        upgCost = new int[3][][];
        for (int ax = 0; ax < 3; ax++) {
            String key = new String[]{"dmg", "mag", "acc"}[ax];
            Map<String, Object> am = Json.asObj(Json.get(uj, key));
            upgPer[ax] = (float) Json.num(am, "per", 0.1f);
            List<Object> cs = Json.asArr(Json.get(am, "cost"));
            upgCost[ax] = new int[cs.size()][2];
            for (int i = 0; i < cs.size(); i++) {
                List<Object> c = Json.asArr(cs.get(i));
                upgCost[ax][i][0] = ((Number) c.get(0)).intValue();
                upgCost[ax][i][1] = c.size() > 1 ? ((Number) c.get(1)).intValue() : 0;
            }
        }
    }

    /** Where extracted asset copies live (atlas/menu art read from here). */
    public static java.io.File assetDir() {
        return filesDir;
    }

    private static java.io.File filesDir;

    /** JVM side (smoke tests). */
    public static DataLib loadFromDir(File dir) {
        DataLib d = new DataLib();
        d.loadAll(dir);
        return d;
    }

    /** Android side: copies small JSON configs out of assets into internal storage. */
    public static DataLib load(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "data");
        if (!dir.isDirectory()) dir.mkdirs();
        // refresh asset copies whenever the app version changes — otherwise an
        // upgrade keeps running on stale mission/enemy data
        String ver = "0";
        try {
            ver = String.valueOf(ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0).versionCode);
        } catch (Exception ignored) { }
        File vf = new File(dir, ".ver");
        String have = "";
        try {
            java.io.FileInputStream fr = new java.io.FileInputStream(vf);
            byte[] b = new byte[32];
            int n = fr.read(b);
            fr.close();
            if (n > 0) have = new String(b, 0, n).trim();
        } catch (Exception ignored) { }
        boolean needCopy = !ver.equals(have);
        if (!needCopy) {
            for (String f : new String[]{"weapons.json", "enemies.json", "missions.json", "upgrades.json"}) {
                if (new File(dir, f).length() == 0) { needCopy = true; break; }
            }
        }
        if (needCopy) {
            for (String f : new String[]{"weapons.json", "enemies.json", "missions.json", "upgrades.json"}) {
                try {
                    InputStream in = ctx.getAssets().open("data/" + f);
                    java.io.FileOutputStream out = new java.io.FileOutputStream(new File(dir, f));
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    out.close();
                    in.close();
                } catch (Exception e) {
                    throw new RuntimeException("asset copy failed: " + f, e);
                }
            }
            for (String f : new String[]{"atlas.png", "menu_bg.jpg"}) {
                try {
                    InputStream in = ctx.getAssets().open(f);
                    java.io.FileOutputStream out = new java.io.FileOutputStream(new File(dir, f));
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    out.close();
                    in.close();
                } catch (Exception ignored) { }
            }
            try {
                java.io.FileWriter vw = new java.io.FileWriter(vf);
                vw.write(ver);
                vw.close();
            } catch (Exception ignored) { }
        }
        filesDir = dir;
        DataLib d = new DataLib();
        d.loadAll(dir);
        return d;
    }
}
