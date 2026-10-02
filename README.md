# Lava Rising

A Fabric minigame mod for Minecraft 1.21.1. **The world border shrinks to a small arena and lava rises one layer at a time. Build up, fight, survive. Last player alive wins.**

## How a round works

1. An op stands where the arena should be and types `/lava start`.
2. The world border shrinks to a **64×64** arena around them. Anyone outside gets teleported in.
3. You get **60 seconds** to gather blocks and food.
4. Then lava fills the arena **one layer every 15 seconds**. It fills air and water but never breaks blocks, so your towers stay standing.
5. Die and you're out. You come back as a spectator to watch.
6. The last player alive wins, with a title, a sound and a chat message.

A boss bar at the top always shows the lava height, the time until the next rise, and how many players are left. Hiding in a hole under the lava doesn't work: you'll start burning.

## Commands

All need op (or cheats on in singleplayer).

| Command | What it does |
|---|---|
| `/lava start` | 64×64 arena, rises every 15s, 60s to prepare |
| `/lava start <size>` | Pick the arena size (16 to 512) |
| `/lava start <size> <riseSeconds>` | Also pick how often it rises |
| `/lava start <size> <riseSeconds> <graceSeconds>` | Also pick the prep time |
| `/lava status` | Lava height and players left |
| `/lava stop` | End the round early |

When a round ends, the world border goes back to how it was and spectators are switched back to survival. The lava stays, so **play on a world you don't mind flooding** (a fresh world or a copy).

**Good settings:** quick 2 to 4 player game: `/lava start 48 10 45`. Bigger group: `/lava start 96 20 120`.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.1.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) in your `mods` folder.
3. Download `lavarising-x.x.x.jar` from [**Releases**](../../releases) and put it in `mods`.

**Servers:** only the server needs it. Players can join with a normal client.
**Singleplayer / LAN:** put it in your own `mods` folder and open to LAN with cheats on.

## Building

```
./gradlew build
```

The jar ends up in `build/libs/`.

To ship an update, bump `mod_version` in `gradle.properties` and push. GitHub Actions builds it and publishes a release automatically.
