# Litematica Server Paster — LiteLoader 1.12.2 Port

This sub-project contains the **LiteLoader** port of
[Litematica Server Paster](https://modrinth.com/mod/litematica-server-paster) for
Minecraft **1.12.2**.

The main project supports Fabric (MC 1.14.4 – 1.21.x).  Because Fabric does not exist
for MC 1.12.2, and because Litematica for 1.12.2 is a LiteLoader mod, a separate port
is needed for that version.

---

## Features

Same core functionality as the Fabric version:

* Intercepts Litematica's `/setblock` and `/summon` commands during schematic pasting.
* Appends the **full tile-entity / entity NBT** to the command, bypassing the vanilla
  chat-length restriction.
* Falls back gracefully to vanilla chat if the server does not have the mod installed.
* Supports "very long chat" segmented packet mode for commands exceeding ~32 KB.

---

## How it works

```
Client (LiteLoader 1.12.2)                    Server (any 1.12.2 server with the mod)
─────────────────────────────────────────────────────────────────────────────────────
  1. Player joins world
  2. → C2S HI packet (channel "lmspaster")
  3. ← S2C HI + ACCEPT_PACKETS response
  4. Litematica begins pasting a schematic
  5. For each block/entity with NBT:
     Mixin intercepts sendChatMessage()
     → C2S CHAT or VERY_LONG_CHAT packets
       carrying full NBT
```

The custom plugin channel name is **`lmspaster`** (fits within the 20-character limit
enforced by the vanilla MC 1.12.2 protocol).

---

## Project structure

```
liteloader-1.12.2/
├── build.gradle                       ForgeGradle 2.3 build script
├── gradle.properties                  Version pins
├── settings.gradle
├── gradle/wrapper/
│   └── gradle-wrapper.properties      Uses Gradle 4.10.3
└── src/main/
    ├── java/me/fallenbreath/lmspaster/
    │   ├── LiteModLmsPaster.java       Main LiteMod entry point; receives S2C packets
    │   ├── network/
    │   │   ├── LmsNetwork.java         Channel constant, packet builder helpers
    │   │   ├── LmsPasterPacket.java    Wire-format read/write
    │   │   └── ClientNetworkHandler.java  State machine + send helpers
    │   ├── mixins/
    │   │   ├── NetHandlerPlayClientMixin.java   Detects world join / respawn
    │   │   └── TaskPasteSchematicSetblockMixin.java  Core Litematica hook
    │   └── utils/
    │       └── NbtUtils.java
    └── resources/
        ├── litemod.json               LiteLoader metadata
        └── mixins.lmspaster.json      Mixin configuration
```

---

## Build requirements

| Requirement | Version |
|-------------|---------|
| JDK         | 8       |
| Gradle      | **4.x** (ForgeGradle 2.3 is not compatible with Gradle 5+) |
| ForgeGradle | 2.3-SNAPSHOT |

### Generate the Gradle wrapper scripts

```bash
# From this directory (liteloader-1.12.2/)
gradle wrapper   # generates gradlew / gradlew.bat using the version in gradle-wrapper.properties
```

### Build

```bash
./gradlew build
```

The output jar will be in `build/libs/`.

### IntelliJ / Eclipse run configs

```bash
./gradlew genIntellijRuns   # or genEclipseRuns
```

---

## Dependencies to provide in `gradle.properties`

Before building you need to supply valid version strings for the Masa mods.  Check the
respective Maven repositories:

| Property | Where to find the latest value |
|----------|-------------------------------|
| `malilib_version` | https://masa.dy.fi/maven/fi/dy/masa/malilib/ |
| `litematica_version` | https://masa.dy.fi/maven/fi/dy/masa/litematica/ |
| `liteloader_version` | http://dl.liteloader.com/versions/ |

---

## Server-side component

This jar only covers the **client side**.  For full NBT pasting to work, the server must
also run a compatible component that:

1. Listens on the `lmspaster` plugin channel.
2. Understands the same packet IDs (see `LmsNetwork.C2S`).
3. Executes the received commands on behalf of the player.

For MC 1.12.2 servers you have several options:

* **Spigot / Paper plugin** — register an incoming channel via
  `getServer().getMessenger().registerIncomingPluginChannel(...)` and handle
  `PluginMessageReceivedEvent`.
* **Forge server mod** — use `FMLCommonHandler.instance().bus().register(...)` and listen
  for `FMLNetworkEvent.ServerCustomPacketEvent`.
* **Cauldron / Thermos** — a hybrid that accepts both Forge and Bukkit plugins.

The server-side protocol is identical to the one used by the main Fabric server mod
(same packet IDs and NBT field names) so it is straightforward to adapt.

---

## Mixin target notes

The mixin `TaskPasteSchematicSetblockMixin` targets internal methods of
`fi.dy.masa.litematica.scheduler.tasks.TaskPasteSchematicSetblock`.  Because masa did not
always maintain a stable internal API across snapshot builds, the exact method names and
the chunk-provider call inside `processBox` may differ slightly between Litematica
snapshot versions.  If the build produces a Mixin warning or the mixin fails at runtime,
check the following strings in the mixin source and adjust them to match the decompiled
class:

* `"processBox"` — the outer loop method
* `"sendSetBlockCommand"` — the per-block command dispatch method
* `"summonEntities"` — the per-entity command dispatch method
* `target = "Lfi/dy/masa/litematica/world/ChunkProviderSchematic;provideChunk(II)..."` —
  the chunk-provider method call inside `processBox` (may be `getChunk` in some versions)

---

## License

GNU Lesser General Public License v3.0 — same as the main project.
