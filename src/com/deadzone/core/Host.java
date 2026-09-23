package com.deadzone.core;

import com.deadzone.data.DataLib;
import com.deadzone.data.SaveData;
import com.deadzone.entities.Zombie;
import com.deadzone.world.Fx;
import com.deadzone.world.World;

/**
 * Everything gameplay code needs from the game shell. Kept as an interface so
 * the core simulation (world, player, zombies, missions) can run headless in
 * JVM smoke tests with a fake host — no Android/GL dependency.
 */
public interface Host {
    World world();
    Fx fx();
    DataLib data();
    SaveData save();
    int quality();      // 0 low, 1 medium, 2 high
    float sens();       // camera sensitivity multiplier
    int[] sessionAmmo();           // working ammo for the current mission
    java.util.List<Zombie> zombies();

    void sfx(String name);
    void toast(String s);

    void onZombieKilled(Zombie z);
    void playerDied();
    void playerInteract();
    com.deadzone.entities.Player player();
    void spawnZombie(String type, com.deadzone.gl.Vec3 p);
    void missionWon(com.deadzone.systems.MissionMgr m);
    void missionLost(com.deadzone.systems.MissionMgr m);
    void showNote(String title, String text);
}
