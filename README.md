# ChestSee

Someone on your server keeps stealing from chests? ChestSee writes a log for every chest, barrel and shulker box, so you can see who took what and when.

Only the server needs the mod. Players can join with a vanilla client.

## What gets logged

- who opened the container and when
- which items were added and which were removed
- who broke a container
- hoppers placed underneath a container

Double chests are logged for both halves.

## Where the logs are

Each container gets its own text file inside the world folder:

```
world/ChestLog/
├── Overworld/
│   └── Chest 120 64 -35.txt
├── The Nether/
└── The End/
```

Example entry:

```
[01.05.25  12:38:33] Steve
Items Added: [192 gold_ingot, 6 oak_log]
Items Removed: [26 diamond, 2 netherite_ingot]
```

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api) on your server.
2. Download ChestSee for your Minecraft version from [Modrinth](https://modrinth.com/mod/chestsee-chestlogger).
3. Put the jar into the `mods` folder and restart the server.

## Versions

Every Minecraft version has its own branch:

| Minecraft | Branch |
|-----------|--------|
| 26.2 | `main` |
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
