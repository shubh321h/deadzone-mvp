# DEAD ZONE

An original, completely **free** offline single-player third-person zombie
survival shooter for Android. Set in a post-apocalyptic India, the MVP is a
fully playable **Mumbai vertical slice** — 5 missions, 4 weapons, 4 zombie
types, a multi-phase boss, loot, progression, a safehouse, and local saves.

No IAP, no ads, no premium currency, no paywalls, no online services.
Everything is earned in gameplay. 100% original code, content and audio
(procedurally synthesized — no third-party assets).

## Install

```
adb install DEADZONE_MVP_0.1.0.apk
```

- Min Android 7.0 (API 24), target 34, requires OpenGL ES 3.0
- Landscape, touch controls: left virtual joystick (move), right-side drag
  (camera/aim), plus FIRE / AIM / RLD / SWP / RUN / CRCH / USE buttons
- Save is local (internal storage), automatic after each mission

## What's in the MVP (Mumbai)

| System | Detail |
|---|---|
| Player | 3rd-person over-shoulder camera (ray-clamped), move/sprint/crouch, ADS, health, death/retry |
| Weapons | 4 data-driven types: AR-15 "Ranger" (5.56), SMG (9mm), shotgun (12g), sniper (.338) — damage, rate, mag, reload, spread, range, headshot multiplier, upgrade levels (damage/reload/accuracy/mag) |
| Zombies | Walker, Runner, Brute, Screamer (shriek aggro) — detect/chase/attack, A* street pathfinding, separation, headshots, pooling-free but cap-limited |
| Boss | **The Colossus** — melee / telegraphed charge / ground slam / roar, phase change at 50% (enrage + summons), 25% summon, head weak point, big reward |
| Missions | 1 First Night (tutorial: kill 10 + reach safehouse) · 2 The Search (medical supplies) · 3 Flooded Streets (supply crates + 3 horde waves) · 4 The Docks (evidence) · 5 The Colossus (boss + final evidence) — each with intro, objectives, completion, failure (death), rewards, results + story beats |
| World | Procedural grid city: buildings, weathered slabs, rooftop tanks, street props, market, camp, railway yard, docks, safehouse tower, optional rain; flood variant for mission 3 |
| Loot/Progression | Ammo/meds/parts pickups, scrap bounties, XP levels, free stat upgrades (health/speed/reload/damage), weapon upgrades, persistent inventory & equipping |
| Safehouse | Hub UI: missions, weapons, survivor stats, inventory, settings; mission extract points; safehouse level visuals |
| Settings | Camera sensitivity, Low/Med/High graphics (draw distance, rain, zombie cap), volume, delete save |
| Audio | 23 original procedurally-generated SFX/ambient loops (see `tools/make_audio.py`) |

## Project layout

```
AndroidManifest.xml        app metadata (minSdk 24, GL ES 3.0)
res/                       icons (tools/make_icons.py), theme, strings
assets/data/*.json         all game data: weapons, enemies, missions, upgrades
assets/audio/*.wav         23 generated SFX (tools/make_audio.py)
src/com/deadzone/
  MainActivity.java        shell: immersive fullscreen, back/pause wiring
  core/                    Game (shell/state machine), Host interface, InputState
  gl/                      Mat4, Vec3, Renderer (batched static/dyn boxes, fog, rain)
  data/                    Json, DataLib, WeaponData/EnemyData/MissionData, SaveData
  world/                   World (city gen, colliders, LOS, anchors), Aabb, Fx, PathGraph
  entities/                Player, Zombie (+boss patterns)
  systems/                 MissionMgr (objectives, waves, loot, extraction)
  audio/                   AudioMgr (SoundPool, asset extract to filesDir, loops)
  input/                   Input, JoystickView, HudButton, HudView
  ui/                      Hud (mission HUD), UI (menus/panels/safehouse)
tools/
  make_audio.py            regenerates all audio (pure stdlib)
  make_icons.py            regenerates launcher icons (pure stdlib)
  setup_sdk.sh             downloads platform-34 + build-tools 34 (linux)
  sim/Smoke.java           headless JVM gameplay test (no Android needed)
build.sh                   aapt2 → javac → d8 → zipalign → apksigner
```

## Build (linux, JDK 11+)

```
export ANDROID_HOME=/path/to/android-sdk   # platform-34 + build-tools/34.0.0
bash tools/setup_sdk.sh                    # one-time SDK fetch (if needed)
bash build.sh                              # -> DEADZONE_MVP_0.1.0.apk (signed)
```

The pipeline is deliberately Gradle-free: `aapt2 compile/link`, `javac
--release 8`, `d8`, `zipalign`, `apksigner`. Regenerate assets with
`python3 tools/make_audio.py && python3 tools/make_icons.py`, then rebuild.

## Headless gameplay verification (no device needed)

`tools/sim/Smoke.java` runs the real game code on the JVM with a fake
host/renderer: data loading, save round-trip, world generation + anchor
reachability, LOS, a full heuristic bot **playthrough of mission 1**
(kill 10 + reach the safehouse door + extraction), an **isolated Colossus
duel** (patterns, 50% phase change, summons, kill, post-boss evidence), and
per-type zombie behavior (chase/attack/tanking/screamer/death).

```
javac --release 8 -encoding UTF-8 -cp $ANDROID_HOME/platforms/android-34/android.jar \
      -d /tmp/obj $(find src tools/sim -name '*.java')
java -cp /tmp/obj:$ANDROID_HOME/platforms/android-34/android.jar sim.Smoke assets/data
# -> SMOKE RESULT: 207 passed, 0 failed

The suite is randomized (ambient spawns, loot rolls), so individual runs can
drop a late-campaign boss fight — placement/logic checks never flap. Expect a
clean sweep about 10 runs out of 12.
```

What the sim caught (all fixed): a game-breaking safehouse anchor placed
inside its own collider (mission 1 unwinnable), zero starting 5.56 ammo,
zombies/boss stuck forever on buildings (added A* street pathfinding), and
per-frame charge damage on the boss (now one hit per charge).

## Architecture notes for future chapters

- **Adding Delhi / Bengaluru / Darjeeling / Ladakh**: each region is a new
  `assets/data/missions.json` block + a `World` style variant (the generator
  already branches on `MissionData.flood`; a `region` field can drive
  palettes, building heights, props, weather — rain for Bengaluru, snow
  drifts for Ladakh, etc.). The safehouse UI unlocks the next chapter when
  its prerequisite missions complete (`SaveData.unlocked`).
- **Story**: original mystery (the "K-9" signal) revealed through mission
  story beats and evidence notes; the full origin is reserved for the Ladakh
  finale chapter.
- Data-driven: weapons/enemies/missions/upgrades all load from JSON; balance
  changes need no code edits.
- Performance: single batched static vertex buffer per mission, capped
  dynamic pools (zombies 24–48 by quality tier, FX arrays), no per-frame
  allocations in hot paths, ES 3.0 only.

## Status

0.4.0-HD — the full three-chapter campaign (Mumbai / Delhi / Ladakh, 12
missions, 3 bosses) now on the HD render path: textured material atlas
(asphalt / concrete / metal / rust), sun + sky lighting with per-region
atmospheres, procedural emissive windows on level geometry, procedural sky
dome with sun glow, 4x MSAA, HD launcher icons and key-art main menu.
207 smoke checks, ~10/12 full-sweep green.
