# rumdl IntelliJ Plugin

A JetBrains IDE plugin for [rumdl](https://github.com/rvben/rumdl), a fast Markdown linter and formatter written in Rust.

## Features

- **Real-time diagnostics** - See linting errors and warnings as you type
- **Reformat Code** - `⌥⇧⌘L` / `Ctrl+Alt+L` runs rumdl on the current Markdown file
- **Format on save** - Enable via Settings → Tools → Actions on Save → Reformat code
- **Quick fixes** - Apply suggested fixes directly from the editor
- **Virtualenv support** - Automatically detects rumdl from your Python virtualenv

## Installation

### From JetBrains Marketplace

1. Open **Settings/Preferences** → **Plugins** → **Marketplace**
2. Search for "rumdl"
3. Click **Install**

### Install rumdl

The plugin requires rumdl to be installed. Choose one of:

```bash
# Via pip (recommended for Python projects)
pip install rumdl

# Via cargo
cargo install rumdl

# Via Homebrew (macOS)
brew install rvben/tap/rumdl
```

## Configuration

Go to **Settings/Preferences** → **Tools** → **rumdl**

- **rumdl path** - Custom path to rumdl executable (leave empty for auto-detection)
- **Use virtualenv** - Look for rumdl in project's Python virtualenv first
- **Enable LSP** - Enable Language Server Protocol features (diagnostics + Reformat Code)

To format on save, enable **Settings → Tools → Actions on Save → Reformat code** —
the IDE will route the action through rumdl for Markdown files.

## Executable Detection

The plugin searches for rumdl in the following order:

1. Custom path (if configured in settings)
2. Project's Python virtualenv (PyCharm, IntelliJ with Python plugin)
3. System PATH
4. Common locations (`~/.cargo/bin`, `/opt/homebrew/bin`, etc.)

## Supported IDEs

- IntelliJ IDEA (2024.3+)
- PyCharm (2024.3+)
- WebStorm (2024.3+)
- Other JetBrains IDEs with Markdown support

## Development

### Building

```bash
./gradlew build
```

### Dependency locks

Compile/runtime and test classpaths use strict Gradle dependency locking.
`gradle.lockfile` records the default IDE target from `gradle.properties`;
`gradle/locks/<version>.lockfile` records each alternate IDE target tested by CI.
`buildscript-gradle.lockfile` records the build plugins and their transitive
Maven dependencies. Keep these generated files in Git. The generated `settings-gradle.lockfile`
also records the settings version-catalog configuration. Synthetic IDE and
bundled-plugin coordinates are excluded; IDE targets remain pinned separately.

Normal builds validate the configurations they resolve. `make verify-locks`
checks all four project classpaths, and CI runs it without rewriting locks.
Missing project lock state or an incompatible graph fails verification. A new
IDE target needs its own generated lockfile before it can build.

After deliberately updating dependency, plugin, or IDE versions:

```bash
make lock-dependencies
make ci
upd audit --lang gradle
```

Review and commit the lockfile changes with the version changes. Keep the
alternate targets in `make lock-dependencies` aligned with the CI matrix.
`upd` currently audits the adjacent default project and buildscript lockfiles;
it does not discover the alternate target lockfiles under `gradle/locks`.
Locking does not audit the IDE/JDK contents or the settings-plugin dependency
graph, and does not verify artifact checksums. It also does not lock every
IntelliJ tooling configuration, such as the downloaded Plugin Verifier IDEs.

The build excludes Undertow, which the IntelliJ Gradle plugin uses only for its
custom-plugin-repository bridge. This project uses standard repositories and
bundled plugins. If adding `customPluginRepository`,
review this exclusion and the Undertow advisory GHSA-3x3v-w654-m28m first.

### Running in Development IDE

```bash
./gradlew runIde
```

### Publishing

```bash
./gradlew publishPlugin
```

## License

MIT
