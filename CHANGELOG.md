# Changelog

## [Unreleased]

## [0.1.5] - 2026-05-22

### Fixed

- "Reformat Code" on Markdown now reliably routes through rumdl. Before the LSP
  capability handshake settled, the IDE could fall back to its native Markdown
  formatter and apply no lint fixes; the plugin now always claims exclusive
  formatting for Markdown files. (#2)

### Changed

- Removed the plugin's `until-build` upper bound so it stays compatible with
  current and future IDE builds (2026.1 / 261 and later) without a per-release
  update. The plugin relies only on stable public APIs.

## [0.1.4] - 2026-05-06

### Added

- "Reformat Code" (⌥⇧⌘L / Ctrl+Alt+L) now runs rumdl on Markdown files via the LSP
  `textDocument/formatting` request. The same wiring activates the IDE's standard
  "Actions on Save → Reformat code" for Markdown, replacing the previous (non-functional)
  custom "Format on save" toggle. (#2)

### Removed

- "Format on save" checkbox under Settings → Tools → rumdl. The setting was never wired
  up to any save listener and did nothing. Format-on-save is now provided by the IDE's
  built-in mechanism, which routes through the new LSP formatting integration.
- Support for IntelliJ Platform 2025.1 (build 251). Minimum supported build is now 252,
  required by the modern `LspCustomization` API used for the Reformat Code integration.

## [0.1.3]

### Fixed

- Plugin build for IDEA Ultimate 2025.3 no longer fails with "Could not find bundled plugin with ID: 'PythonCore'". The Python virtualenv integration now reaches the Python SDK API via reflection and has no build-time dependency on a specific Python plugin, so the plugin builds against the bare IntelliJ Platform and degrades gracefully when the Python plugin isn't installed at runtime.

### Changed

- Pinned the project's Java toolchain via `mise.toml` (`temurin-21`) to match CI.

## [0.1.2]

### Changed

- Retargeted plugin build base from PyCharm Professional to IntelliJ IDEA Ultimate so the plugin is discoverable in IDEA's in-IDE Marketplace search (#1). Plugin continues to work in PyCharm; PythonCore is bundled in both.

## [0.1.1]

### Changed

- Bumped IntelliJ Platform to 2025.3 and raised supported build range to 251–261.* for compatibility with PyCharm 2026.1
- Upgraded Kotlin to 2.2.20 and changelog plugin to 2.4.0
- Replaced deprecated/internal `PySdkExtKt.pythonSdk` with public `PythonSdkUtil.findPythonSdk`

### Removed

- Support for IntelliJ Platform 2024.3 (build 243)

## [0.1.0]

### Added

- Initial release
- LSP-based diagnostics for Markdown files
- Automatic rumdl detection from virtualenv, PATH, and common locations
- Settings UI for custom executable path
- Format on save support
- Python virtualenv integration (PyCharm, IntelliJ with Python plugin)

[Unreleased]: https://github.com/rvben/rumdl/compare/v0.1.5...HEAD
[0.1.5]: https://github.com/rvben/rumdl/compare/v0.1.4...v0.1.5
[0.1.4]: https://github.com/rvben/rumdl/compare/v0.1.3...v0.1.4
[0.1.3]: https://github.com/rvben/rumdl/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/rvben/rumdl/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/rvben/rumdl/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/rvben/rumdl/commits/v0.1.0
