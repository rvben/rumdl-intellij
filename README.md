# rumdl IntelliJ Plugin

A JetBrains IDE plugin for [rumdl](https://github.com/rvben/rumdl), a fast Markdown linter and formatter written in Rust.

## Features

- **Real-time diagnostics** - See linting errors and warnings as you type
- **Format on save** - Automatically format Markdown files when saving
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
- **Enable LSP** - Enable Language Server Protocol features
- **Format on save** - Automatically format on file save

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
