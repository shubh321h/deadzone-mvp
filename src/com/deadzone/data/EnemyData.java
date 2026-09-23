package com.deadzone.data;

/** Data-driven enemy definition. */
public class EnemyData {
    public String id, name;
    public float hp, speed, dmg, range, cd;
    public float detect;    // detection radius (m) with line of sight
    public float aggro;     // radius at which noise/screams alert (m)
    public float[] color = {0.5f, 0.6f, 0.5f};
    public float scale = 1f;
    public int score;
    public int scrapMin, scrapMax;
    public float scream = 0f; // screamer: alert radius when it screams
    public boolean boss;
    public float headMult = 2f;

    public static EnemyData fromJson(java.util.Map<String, Object> m) {
        EnemyData e = new EnemyData();
        e.id = Json.str(m, "id", "?");
        e.name = Json.str(m, "name", e.id);
        e.hp = (float) Json.num(m, "hp", 30);
        e.speed = (float) Json.num(m, "speed", 1.5f);
        e.dmg = (float) Json.num(m, "dmg", 8);
        e.range = (float) Json.num(m, "range", 1.6f);
        e.cd = (float) Json.num(m, "cd", 1.2f);
        e.detect = (float) Json.num(m, "detect", 25);
        e.aggro = (float) Json.num(m, "aggro", 45);
        e.color[0] = (float) Json.num(m, "colorR", 0.5);
        e.color[1] = (float) Json.num(m, "colorG", 0.6);
        e.color[2] = (float) Json.num(m, "colorB", 0.5);
        e.scale = (float) Json.num(m, "scale", 1);
        e.score = Json.inum(m, "score", 10);
        e.scrapMin = Json.inum(m, "scrapMin", 1);
        e.scrapMax = Json.inum(m, "scrapMax", 3);
        e.scream = (float) Json.num(m, "scream", 0);
        e.boss = Json.bool(m, "boss", false);
        e.headMult = (float) Json.num(m, "headMult", 2f);
        return e;
    }
}
