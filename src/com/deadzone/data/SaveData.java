package com.deadzone.data;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent player state: progression, weapons + upgrades, inventory,
 * mission completion, safehouse level, settings. Plain JSON file in internal
 * storage — protected from app closure by auto-save at every mission boundary,
 * on pause, and when the app goes to background.
 */
public class SaveData {
    public static final int MAX_MISSIONS = 12;

    public static class WSave {
        public boolean own;
        public int lD, lM, lA; // damage / magazine / accuracy upgrade levels
    }

    public int xp;
    public int level = 1;
    public int scrap, meds, parts;
    public int[] ammo = {0, 120, 24, 0};
    public final Map<String, WSave> weapons = new LinkedHashMap<String, WSave>();
    public String equip = "rifle";
    public int stH, stSp, stR, stD;   // survivor stats: health, speed, reload, damage
    public boolean[] done = new boolean[MAX_MISSIONS];
    public float sens = 1f;
    public int quality = 1;
    public float vol = 1f;
    public float playTime;

    public void reset() {
        xp = 0; level = 1; scrap = 0; meds = 1; parts = 0;
        // starting loadout: full AR mag + 60 reserve, SMG + reserve, shotgun shells
        ammo = new int[]{90, 120, 24, 0};
        weapons.clear();
        WSave r = new WSave(); r.own = true; weapons.put("rifle", r);
        WSave s = new WSave(); s.own = true; weapons.put("smg", s);
        equip = "rifle";
        stH = stSp = stR = stD = 0;
        done = new boolean[MAX_MISSIONS];
        sens = 1f; quality = 1; vol = 1f; playTime = 0;
    }

    public WSave wsave(String id) {
        WSave w = weapons.get(id);
        if (w == null) { w = new WSave(); weapons.put(id, w); }
        return w;
    }

    public boolean owned(String id) {
        WSave w = weapons.get(id);
        return w != null && w.own;
    }

    public int wLevel(String id, int axis) {
        WSave w = weapons.get(id);
        if (w == null) return 0;
        if (axis == 0) return w.lD;
        if (axis == 1) return w.lM;
        return w.lA;
    }

    public void wUpgrade(String id, int axis) {
        WSave w = wsave(id);
        if (axis == 0) w.lD++;
        else if (axis == 1) w.lM++;
        else w.lA++;
    }

    // ---- derived stats ----

    public int maxHp() { return 100 + 20 * stH; }
    public float speedMul() { return 1 + 0.04f * stSp; }
    public float reloadMul() { return 1f / (1 + 0.06f * stR); }
    public float dmgMul() { return 1 + 0.04f * stD; }

    public int xpNext() { return 150 + (level - 1) * 100; }

    public boolean addXp(int n) {
        xp += n;
        boolean up = false;
        while (level < 30 && xp >= xpNext()) {
            xp -= xpNext();
            level++;
            up = true;
        }
        return up;
    }

    public boolean unlocked(int mi) {
        return mi == 0 || (mi >= 1 && mi < MAX_MISSIONS && done[mi - 1]);
    }

    public int completedCount() {
        int n = 0;
        for (boolean b : done) if (b) n++;
        return n;
    }

    /** Safehouse visual level: grows with campaign progress + upgrades. */
    public int baseLevel() {
        int lv = 1 + completedCount();
        int up = stH + stSp + stR + stD;
        if (up >= 6) lv++;
        return Math.min(6, lv);
    }

    public static int statMax(int which) {
        return which == 0 ? 5 : 4;
    }

    public int statCost(int cur) { return 60 + cur * 70; }

    // ---- serialization ----

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("v", (double) 1);
        m.put("xp", (double) xp);
        m.put("level", (double) level);
        m.put("scrap", (double) scrap);
        m.put("meds", (double) meds);
        m.put("parts", (double) parts);
        List<Object> am = new java.util.ArrayList<Object>();
        for (int a : ammo) am.add((double) a);
        m.put("ammo", am);
        Map<String, Object> wm = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, WSave> en : weapons.entrySet()) {
            WSave w = en.getValue();
            Map<String, Object> wmm = new LinkedHashMap<String, Object>();
            wmm.put("own", w.own);
            wmm.put("lD", (double) w.lD);
            wmm.put("lM", (double) w.lM);
            wmm.put("lA", (double) w.lA);
            wm.put(en.getKey(), wmm);
        }
        m.put("weapons", wm);
        m.put("equip", equip);
        m.put("stH", (double) stH);
        m.put("stSp", (double) stSp);
        m.put("stR", (double) stR);
        m.put("stD", (double) stD);
        List<Object> dm = new java.util.ArrayList<Object>();
        for (int i = 0; i < MAX_MISSIONS; i++) dm.add(done[i] ? 1.0 : 0.0);
        m.put("done", dm);
        m.put("sens", (double) sens);
        m.put("quality", (double) quality);
        m.put("vol", (double) vol);
        m.put("playTime", playTime);
        return m;
    }

    public static SaveData fromMap(Map<String, Object> m) {
        SaveData s = new SaveData();
        s.xp = Json.inum(m, "xp", 0);
        s.level = Json.inum(m, "level", 1);
        s.scrap = Json.inum(m, "scrap", 0);
        s.meds = Json.inum(m, "meds", 0);
        s.parts = Json.inum(m, "parts", 0);
        Object am = Json.get(m, "ammo");
        if (am instanceof List) {
            List<?> al = (List<?>) am;
            for (int i = 0; i < 4 && i < al.size(); i++) s.ammo[i] = ((Number) al.get(i)).intValue();
        }
        s.weapons.clear();
        Object wm = Json.get(m, "weapons");
        if (wm instanceof Map) {
            for (Map.Entry<String, Object> en : ((Map<String, Object>) wm).entrySet()) {
                Map<String, Object> wmm = Json.asObj(en.getValue());
                WSave w = new WSave();
                w.own = Json.bool(wmm, "own", false);
                w.lD = Json.inum(wmm, "lD", 0);
                w.lM = Json.inum(wmm, "lM", 0);
                w.lA = Json.inum(wmm, "lA", 0);
                s.weapons.put(en.getKey(), w);
            }
        }
        s.equip = Json.str(m, "equip", "rifle");
        s.stH = Json.inum(m, "stH", 0);
        s.stSp = Json.inum(m, "stSp", 0);
        s.stR = Json.inum(m, "stR", 0);
        s.stD = Json.inum(m, "stD", 0);
        Object dm = Json.get(m, "done");
        if (dm instanceof List) {
            List<?> dl = (List<?>) dm;
            for (int i = 0; i < MAX_MISSIONS && i < dl.size(); i++)
                s.done[i] = ((Number) dl.get(i)).intValue() == 1;
        }
        s.sens = (float) Json.num(m, "sens", 1f);
        s.quality = Json.inum(m, "quality", 1);
        s.vol = (float) Json.num(m, "vol", 1f);
        s.playTime = (float) Json.num(m, "playTime", 0f);
        return s;
    }

    // ---- IO (Android) ----

    public static File file(Context ctx) {
        return new File(ctx.getFilesDir(), "save.json");
    }

    public void save(Context ctx) {
        try {
            String json = Json.write(toMap());
            FileOutputStream out = new FileOutputStream(file(ctx));
            out.write(json.getBytes("UTF-8"));
            out.close();
        } catch (Exception e) {
            throw new RuntimeException("save failed", e);
        }
    }

    public static SaveData load(Context ctx) {
        File f = file(ctx);
        if (f.exists() && f.length() > 0) {
            try {
                Object o = Json.parse(new InputStreamReader(new FileInputStream(f), "UTF-8"));
                return fromMap(Json.asObj(o));
            } catch (Exception e) {
                // corrupted save: start fresh rather than crash
            }
        }
        SaveData s = new SaveData();
        s.reset();
        return s;
    }

    // ---- IO (JVM smoke tests) ----

    public void saveJVM(File f) {
        try {
            FileOutputStream out = new FileOutputStream(f);
            out.write(Json.write(toMap()).getBytes("UTF-8"));
            out.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static SaveData loadJVM(File f) {
        if (f.exists() && f.length() > 0) {
            try {
                Object o = Json.parse(new java.io.InputStreamReader(new FileInputStream(f), "UTF-8"));
                return fromMap(Json.asObj(o));
            } catch (Exception e) {
                // fall through
            }
        }
        SaveData s = new SaveData();
        s.reset();
        return s;
    }
}
