# SkyBlock Value Alerts

A client-side [Fabric](https://fabricmc.net/) mod for **Minecraft 26.1.2** and **Hypixel SkyBlock**.
Whenever you pick up an item worth more than **1,000,000 coins** — by bazaar instant-sell value or
auction house lowest BIN — a notification appears in the top-right corner of the screen for
**10 seconds** and a level-up sound plays.

## How it works

- A client mixin hooks the vanilla *take item entity* packet — the moment your player visibly
  collects an item from the ground.
- The item's SkyBlock id is read from its `minecraft:custom_data` component (`id` at the root,
  with a legacy `ExtraAttributes.id` fallback).
- Prices are cached in memory and refreshed every 5 minutes on a background thread:
  - **Bazaar instant-sell**: the official, key-less Hypixel endpoint
    `https://api.hypixel.net/v2/skyblock/bazaar`
  - **Auction lowest BIN**: `https://hysky.de/api/auctions/lowestbins`
    (the aggregation backend used by the Skyblocker mod)
- On pickup, the higher of the two unit prices is used. If `stack count × unit price` is at
  least 1,000,000 coins, an alert is shown with the item name, total value, and price source.
  Repeat alerts for the same item id are suppressed for 3 seconds.

## Tuning

Constants in code, all in `src/client/java/com/rareuncommon/skyblockvaluealerts/client/`:

| Constant | Default | Meaning |
|---|---|---|
| `ItemValueChecker.VALUE_THRESHOLD` | 1,000,000 | Coin value that triggers an alert |
| `NotificationOverlay.DURATION_MS` | 10,000 | How long a notification stays on screen |
| `PriceService.REFRESH_INTERVAL_MINUTES` | 5 | Price cache refresh interval |

## Limitations

- Pickup detection covers items collected from the ground (the vanilla pickup animation).
  Items that go straight into sacks, or are bought from menus/NPCs, do not fire that packet.
- Price lookup matches the item's plain SkyBlock id. Items whose market listing uses a
  different id (enchanted books, pets, attribute shards, etc.) may not be priced.
- Alerts only start once the first price refresh has completed (a few seconds after launch,
  requires internet access to the two endpoints above).

## Versions

| Component     | Version          |
|---------------|------------------|
| Minecraft     | 26.1.2           |
| Fabric Loader | 0.19.3           |
| Fabric API    | 0.150.0+26.1.2   |
| Fabric Loom   | 1.16-SNAPSHOT (`net.fabricmc.fabric-loom`) |
| Java          | 25               |
| Gradle        | 9.4.1 (wrapper)  |

As of Minecraft 26.1, Fabric uses Mojang's official names (Yarn is no longer published).
Check current versions at <https://fabricmc.net/develop/>.

## Building

Requires JDK 25.

```sh
./gradlew build
```

The mod jar is produced at `build/libs/skyblock-value-alerts-1.0.0.jar`. Drop it into your
`.minecraft/mods` folder together with [Fabric API](https://modrinth.com/mod/fabric-api).
A GitHub Actions workflow builds the jar and uploads it as an artifact on every push.

## License

CC0-1.0
