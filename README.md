# Example Mod — Fabric for Minecraft 26.1.2

A basic [Fabric](https://fabricmc.net/) mod skeleton targeting **Minecraft 26.1.2**, based on the
official [fabric-example-mod](https://github.com/FabricMC/fabric-example-mod) template.

## Versions

| Component     | Version          |
|---------------|------------------|
| Minecraft     | 26.1.2           |
| Fabric Loader | 0.19.3           |
| Fabric API    | 0.150.0+26.1.2   |
| Fabric Loom   | 1.16-SNAPSHOT (`net.fabricmc.fabric-loom`) |
| Java          | 25               |
| Gradle        | 9.4.1 (wrapper)  |

Note: as of Minecraft 26.1, Fabric uses Mojang's official mappings (Yarn is no longer published),
and the Gradle plugin id is `net.fabricmc.fabric-loom`. Check current versions at
<https://fabricmc.net/develop/>.

## Requirements

- JDK 25 (Minecraft 26.1+ requires Java 25)

## Building

```sh
./gradlew build
```

The mod jar is produced at `build/libs/modid-1.0.0.jar` (plus a `-sources` jar).
A GitHub Actions workflow (`.github/workflows/build.yml`) builds the mod and uploads the
jars as artifacts on every push.

## Project layout

```
├── build.gradle                  # Loom build configuration
├── gradle.properties             # Minecraft/Loader/Fabric API versions
├── settings.gradle               # Plugin repositories + project name
└── src
    ├── main                      # Common (client + server) code
    │   ├── java/com/example
    │   │   ├── ExampleMod.java           # "main" entrypoint
    │   │   └── mixin/ExampleMixin.java
    │   └── resources
    │       ├── fabric.mod.json           # Mod metadata
    │       ├── modid.mixins.json
    │       └── assets/modid/icon.png
    └── client                    # Client-only code (split source sets)
        ├── java/com/example/client
        │   ├── ExampleModClient.java     # "client" entrypoint
        │   └── mixin/ExampleClientMixin.java
        └── resources
            └── modid.client.mixins.json
```

## Renaming the mod

To make this your own, update the mod id `modid` and the `com.example` package consistently in:

1. `settings.gradle` — `rootProject.name`
2. `gradle.properties` — `maven_group`, `mod_version`
3. `src/main/resources/fabric.mod.json` — `id`, `name`, `entrypoints`, `mixins`, `icon`
4. The two mixin config file names and their `package` fields
5. `build.gradle` — the `loom.mods` block name
6. Java package directories under `src/main/java` and `src/client/java`
7. `src/main/resources/assets/modid/` directory name

## License

CC0-1.0 — same as the upstream template.
