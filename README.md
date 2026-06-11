# SkyBlock Value Alerts

A client-side [Fabric](https://fabricmc.net/) mod for **Minecraft 1.21.10** and **Hypixel SkyBlock**.
Whenever you pick up an item worth more than **1,000,000 coins** — by bazaar instant-sell value or
auction house lowest BIN — a notification appears in the top-right corner of the screen for
**10 seconds** and a level-up sound plays.

## How it works

Items you gain are detected three ways:

1. **Ground pickups** — a client mixin hooks the vanilla *take item entity* packet, the moment
   your player visibly collects an item from the ground (instant).
2. **Inventory gains** — the player inventory is diffed every tick, catching the many SkyBlock
   acquisitions that never spawn a ground item: drops teleported straight to your inventory,
   shop/bazaar purchases, auction claims, crafting, and so on. Gains wait in a ~1 second settle
   window and are offset against items that visibly left the open container, so moving your own
   items out of a chest or storage does not alert.
3. **Sack pickups** — the compact `[Sacks] +N items` chat notification is parsed (the per-item
   breakdown lives in its hover text), and names are mapped to item ids via the official
   Hypixel item catalogue.

Valuation:

- The item's market id is derived from its `minecraft:custom_data` component — the plain
  SkyBlock `id` for most items, with special handling for enchanted books
  (`ENCHANTMENT_<NAME>_<LEVEL>`), pets (`LVL_1_<TIER>_<TYPE>`), potions, runes, and shiny
  variants, matching what the price APIs list.
- Prices are cached in memory and refreshed every 5 minutes on a background thread (failed
  fetches retry after 30 seconds), from:
  - **Bazaar instant-sell**: the official, key-less Hypixel endpoint
    `https://api.hypixel.net/v2/skyblock/bazaar`
  - **Auction lowest BIN**: `https://hysky.de/api/auctions/lowestbins`
    (the aggregation backend used by the Skyblocker mod)
- The last known prices are also persisted to `config/skyblock_value_alerts_prices.json`, so
  alerts work immediately on the next launch, before the first refresh completes.
- The higher of the two unit prices is used. If `count × unit price` is at least
  1,000,000 coins, an alert is shown with the item name, total value, and price source.
  Repeat alerts for the same item id are suppressed for 3 seconds.

## Tuning

Constants in code, all in `src/client/java/com/rareuncommon/skyblockvaluealerts/client/`:

| Constant | Default | Meaning |
|---|---|---|
| `ItemValueChecker.VALUE_THRESHOLD` | 1,000,000 | Coin value that triggers an alert |
| `NotificationOverlay.DURATION_MS` | 10,000 | How long a notification stays on screen |
| `PriceService.REFRESH_INTERVAL_MINUTES` | 5 | Price cache refresh interval |

## Remaining caveats

- Sack detection requires sack notifications to be enabled in your Hypixel settings (they are
  by default), since it works by reading that chat message.
- The container-transfer heuristic is best-effort: if a container is very slow (more than ~2s)
  to reflect a withdrawal, pulling an expensive item out of your own storage can rarely
  produce a spurious alert. Withdrawing from a *sack* into your inventory counts as a gain.
- Pet BIN prices are keyed by tier and type (`LVL_1_…`), so a pet's level / held-item premium
  is not reflected in the alert value.
- The very first launch ever still needs one successful price fetch (a few seconds online);
  after that, cached prices from the previous session are used until fresh data arrives.
- Alerts are suppressed for the first ~5 seconds after joining a world while the server is
  still populating your inventory.

## Versions

| Component     | Version          |
|---------------|------------------|
| Minecraft     | 1.21.10          |
| Fabric Loader | 0.19.2           |
| Fabric API    | 0.138.4+1.21.10  |
| Fabric Loom   | 1.16-SNAPSHOT (`net.fabricmc.fabric-loom-remap`) |
| Mappings      | Mojang official  |
| Java          | 21               |
| Gradle        | 9.4.1 (wrapper)  |

The project uses Mojang's official names with the current Fabric toolchain (Yarn is no longer
published); for pre-26.1 Minecraft versions like 1.21.10 the plugin is the `-remap` variant.
Check current versions at <https://fabricmc.net/develop/>.

## Building

Always build with the bundled Gradle wrapper, from the project folder:

```sh
./gradlew build      # Windows: gradlew build
```

The wrapper downloads Gradle 9.4.1 on first run — do not use a system-installed `gradle`
older than 9.4, since fabric-loom 1.16 cannot be resolved by it. The build defines a Java
toolchain, so if JDK 21 isn't installed, Gradle downloads one automatically.

The mod jar is produced at `build/libs/skyblock-value-alerts-1.0.0.jar`. Drop it into your
`.minecraft/mods` folder together with [Fabric API](https://modrinth.com/mod/fabric-api).
A GitHub Actions workflow builds the jar and uploads it as an artifact on every push.

## License

CC0-1.0
