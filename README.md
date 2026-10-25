# FoundryEngine IntelliJ Plugin

An IntelliJ IDEA plugin designed to assist with developing and testing **FoundryEngine** script bundles.

## Features

- **Automatic Project Availability**: Automatically detects projects containing a `*.bundles.toml` file in `src/main/resources` and activates the tool window.
- **Minecraft Instance Detection**: Automatically scans local environments for installed Minecraft instances across dev run folders, the vanilla launcher, Prism Launcher, Modrinth, and CurseForge.
- **Classpath & Mod Management**: Pick a target instance's `mods` directory and select individual engine `.jar` files to sync with your Gradle workspace settings.
- **One-Click Gradle Task Execution**: Trigger bundle build (`buildBundle`) and deployment (`deployBundle`) tasks directly from the plugin tool window.
- **Prism Launcher Integration**: Auto-detects or configures custom paths to Prism Launcher executables and launches Prism instances directly from within the IDE.

---

## Tool Window Usage

When opened inside a project that contains a valid `*.bundles.toml` file:

1. **Instance Selector**: Pick an auto-detected Minecraft launcher profile/instance or specify a custom `mods` directory.
2. **Jars Selection**: Select which JAR files from your `mods` directory should be on the project classpath.
3. **Bundles Folder**: Configure custom deploy destinations or auto-fill them from the selected Prism instance.
4. **Action Buttons**:
   - **Apply & Sync**: Saves configuration into `<project>/.foundryengine/instance.properties` and triggers a Gradle project refresh.
   - **Reset**: Removes per-project configuration overrides.
   - **Build**: Runs the `buildBundle` Gradle task.
   - **Deploy**: Runs the `deployBundle` Gradle task.
   - **Run / Launch Prism**: Launches `runClient` via Gradle or launches the selected Prism instance via command line.
   - **Open Folder**: Opens the output bundles directory in your system file manager.

---

## Configuration File

The plugin stores project-specific options locally in:
```path
<project>/.foundryengine/instance.properties

```

*Note: This file is managed by the plugin and should not be committed to version control.*

---

## Development & Building

### Prerequisites

* JDK 25+
* IntelliJ IDEA 2026.2+ (or compatible IntelliJ Platform build)

### Gradle Tasks

* **Run IDE with plugin enabled**:
```bash
./gradlew runIde

```


* **Build plugin package**:
```bash
./gradlew buildPlugin

```


* **Run verification checks**:
```bash
./gradlew verifyPlugin

```



---

## License

This project is maintained by [LuckyMcDev](https://github.com/LuckyMcDev).
