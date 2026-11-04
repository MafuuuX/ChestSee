# ChestSee

Someone on your server keeps stealing from chests? ChestSee writes a log for every container, so you can see who took what and when. Staff can look the logs up in-game.

Only the server needs the mod. Players can join with a vanilla client.

## What gets logged

- who opened the container and when
- which items were added and which were removed
- who broke a container
- hoppers placed underneath a container

Containers: chests, trapped chests, barrels, shulker boxes, hoppers, dispensers, droppers, crafters, furnaces, blast furnaces, smokers, brewing stands, chest and hopper minecarts and chest boats.

Double chests are logged for both halves. For furnaces and brewing stands only what the player puts in or takes out is logged, not the smelting itself.

## Commands

Commands need OP level 2, or the LuckPerms nodes below.

| Command | What it does |
|---------|--------------|
| `/chestsee inspect` | Toggle inspect mode. Right-click or punch a container (or a minecart) to see its log instead of opening or breaking it |
| `/chestsee lookup <x y z> [page] [dimension]` | Show the log of the container at that position |
| `/chestsee player <name> [page]` | Show everything a player did, across all containers. Click an entry to open that container's log |
| `/chestsee reload` | Reload the config |

Everything also works from the server console and RCON.

## Permissions

LuckPerms is optional. ChestSee uses the [fabric-permissions-api](https://github.com/lucko/fabric-permissions-api) and falls back to OP level 2 if no permission mod is installed.

| Node | Access |
|------|--------|
| `chestsee.inspect` | `/chestsee inspect` |
| `chestsee.lookup` | `/chestsee lookup` |
| `chestsee.player` | `/chestsee player` |
| `chestsee.reload` | `/chestsee reload` |

```
/lp group moderator permission set chestsee.lookup true
```

## Where the logs are

Each container gets its own text file inside the world folder:

```
world/ChestLog/
├── Overworld/
│   ├── Chest 120 64 -35.txt
│   ├── Furnace 118 64 -30.txt
│   └── Minecart with Chest 0d5f0c1e-....txt
├── The Nether/
└── The End/
```

Example entry:

```
[01.05.25  12:38:33] Steve
Items Added: [192 gold_ingot, 6 oak_log]
Items Removed: [26 diamond, 2 netherite_ingot]
```

Minecarts and boats move around, so their files are named after the entity UUID instead of a position.

## Config

`config/chestsee.json` is created on the first start. Change it and run `/chestsee reload`, or restart.

```json
{
  "pageSize": 8,
  "retentionDays": 0,
  "maxFileSizeKb": 0,
  "excludedDimensions": [],
  "containers": {
    "chest": true,
    "barrel": true,
    "shulker_box": true,
    "hopper": true,
    "dispenser": true,
    "dropper": true,
    "crafter": true,
    "furnace": true,
    "brewing_stand": true,
    "minecart": true,
    "chest_boat": true,
    "other": true
  }
}
```

| Option | Meaning |
|--------|---------|
| `pageSize` | Entries per page in the commands |
| `retentionDays` | Entries older than this are removed on server start. `0` keeps everything |
| `maxFileSizeKb` | When a log file gets bigger than this, the oldest entries are dropped. `0` means no limit |
| `excludedDimensions` | Dimensions that aren't logged, e.g. `["minecraft:the_nether"]` |
| `containers` | Turn logging off for single container types. `furnace` covers blast furnaces and smokers too, `minecart` covers chest and hopper minecarts, `other` is for containers added by mods |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api) on your server.
2. Download ChestSee for your Minecraft version from [Modrinth](https://modrinth.com/mod/chestsee-chestlogger).
3. Put the jar into the `mods` folder and restart the server.

## Versions

Every Minecraft version has its own branch:

| Minecraft | Branch |
|-----------|--------|
| 26.3 | `main` |
| 26.2 | `mc/26.2` |
| 26.1.2 | `mc/26.1.2` |
| 26.1.1 | `mc/26.1.1` |
| 26.1 | `mc/26.1` |
| 1.21.11 | `mc/1.21.11` |
| 1.21.10 | `mc/1.21.10` |
| 1.21.9 | `mc/1.21.9` |
| 1.21.8 | `mc/1.21.8` |
| 1.21.7 | `mc/1.21.7` |
| 1.21.4 | `mc/1.21.4` |
| 1.21.1 | `mc/1.21.1` |

## Building

```
./gradlew build
```

The jar ends up in `build/libs`. 26.x needs Java 25, 1.21.x needs Java 21.

## License

MIT, see [LICENSE](LICENSE).
