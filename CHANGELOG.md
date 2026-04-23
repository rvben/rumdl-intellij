# Changelog

## [Unreleased]

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
