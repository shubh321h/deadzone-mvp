package com.deadzone.data;

/** Data-driven weapon definition. All combat code works off these fields only. */
public class WeaponData {
    public String id, name, ammoType;
    public float dmg;        // damage per hit (per pellet for shotguns)
    public float rate;       // shots per second
    public int mag;          // magazine size
    public float reload;     // seconds
    public float spread;     // base spread degrees (cone half-angle)
    public float range;      // effective range meters (bullet travel cap)
    public float headMult;   // headshot multiplier
    public boolean auto;
    public int pellets;      // 1 for single-shot weapons
    public float adsFov;     // fov while aiming
    public float len;        // visual length (world units)

    public static WeaponData fromJson(java.util.Map<String, Object> m) {
        WeaponData w = new WeaponData();
        w.id = Json.str(m, "id", "?");
        w.name = Json.str(m, "name", w.id);
        w.ammoType = Json.str(m, "type", "9mm");
        w.dmg = (float) Json.num(m, "dmg", 10);
        w.rate = (float) Json.num(m, "rate", 6);
        w.mag = Json.inum(m, "mag", 10);
        w.reload = (float) Json.num(m, "reload", 2f);
        w.spread = (float) Json.num(m, "spread", 1.5f);
        w.range = (float) Json.num(m, "range", 60);
        w.headMult = (float) Json.num(m, "headMult", 2f);
        w.auto = Json.bool(m, "auto", true);
        w.pellets = Math.max(1, Json.inum(m, "pellets", 1));
        w.adsFov = (float) Json.num(m, "adsFov", 45);
        w.len = (float) Json.num(m, "len", 1.0);
        return w;
    }
}
