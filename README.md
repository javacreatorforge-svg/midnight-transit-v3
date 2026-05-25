# Midnight Transit

> He's Always Sees You

A Minecraft Forge **1.19.2** mod by **RedstoneDev** that adds Conductor No. 9 - a stalker
who only hunts at night. Watches you from windows, peeks from behind walls, sends footsteps
through the dark to drive you into hiding, and only then comes for you.

Requires **GeckoLib 3.1.40**.

## Build (source project, no PC needed)

Same flow as the previous mods - upload the source zip to a new GitHub repo, the workflow
in `.github/workflows/build.yml` builds the jar automatically.

1. Create empty GitHub repo `midnight-transit`
2. Upload `midnight_transit_source.zip` via "Add file → Upload files", commit
3. Open in a Codespace and run:
   ```bash
   cd /workspaces/midnight-transit
   rm -rf _x
   mkdir _x
   unzip -oq midnight_transit_source.zip -d _x
   (cd _x/midnight_transit && tar cf - .) | tar xf -
   rm -rf _x midnight_transit_source.zip
   git add -A && git commit -m "source" && git push
   ```
4. Actions tab → wait for green ✓ → Artifacts → download `midnight_transit-jar`

## What Conductor No. 9 does

**Spawning** — night only, surface only, one per dimension at a time. ~1-in-250 per 5s tick
per player. Each spawn picks a random mode:
- 70% **Stalking** (50/50 idle or stalking-pose animation) - stares, vanishes when looked at
- 15% **Window** - spawns 5-10 blocks away, plays the window animation. Look at him →
  window_seen animation → despawn ~1.5s later
- 10% **Looking** - spawns 8-14 blocks away (peek-from-cover pose). Look at him → 50%
  looking_seen + despawn, 50% stare for 3 full seconds then despawn
- 5% **Normal** - already walking around. Look at him or get within 2 blocks → aggression

**Aggression triggers** (from any non-stalking spawn): player looks directly at him, player
gets within 2 blocks, or player hits him.

**Aggressive behavior:** runs (no walk animation, ever - he only RUNS or stands still),
chase theme loops gaplessly via TickableSoundInstance, breaks blocks in his path (skips
bedrock/barrier/command/etc.), climbs walls via WallClimberNavigation.

**Stats:** 700,000 HP, 1,000,000 attack damage (per spec - one-shots the player).

**Despawns** after 1 minute (any mode), or instantly when looked at while stalking.

**Footsteps "HIDE" scenario** — ~1-in-600 per 5s at night per player:
- footsteps.ogg plays at the player's position
- "HIDE" title overlay appears with red bold text, "10s" countdown subtitle
- Countdown ticks down once per second
- At 0s: subtitle becomes "HE'S HERE" with violent shake, Conductor spawns ~16-24 blocks
  away in NORMAL wandering mode
- 2-minute hunt window: if the wandering Conductor finds you (looks at you / you collide) →
  aggression + chase. If he doesn't find you in 2 minutes → despawn, overlay clears.

**Message [Audio] item** — right-click to play the audio file and show the transcript as a
red bold title + subtitle for ~11 seconds. Just sits in your inventory until you use it.

**Spawn egg** — tinted with human-skin colors (`0xE8B294` base / `0x6B4423` darker spots)
per the spec.

## Tweaking

Main knobs:

| What | Where | Default |
|---|---|---|
| Lifetime | `aliveTicks >= 1200` in `aiStep` | 1 minute |
| Health | `createAttributes()` | 700,000 |
| Damage | `createAttributes()` | 1,000,000 |
| Ambient spawn rate at night | `tryAmbientSpawn` in `ForgeEvents` | 1-in-250 / 5s |
| Footsteps trigger rate | `tryTriggerFootsteps` in `ForgeEvents` | 1-in-600 / 5s |
| Hide countdown | `new HideState(0, 200)` | 200 ticks = 10s |
| Hunt window | `st.ticksLeft = 2400` | 2400 ticks = 2 min |

## Caveat

Source isn't test-compiled in the sandbox where it was assembled. Same APIs as the Face
Thief mod which has now built successfully via GitHub Actions, so this should compile too.
If `./gradlew build` errors, paste the log and I'll patch.
