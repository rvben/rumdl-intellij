package com.rumdl.intellij

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*

/**
 * Settings UI for rumdl plugin configuration.
 * Available under Settings/Preferences → Tools → rumdl
 */
class RumdlConfigurable(private val project: Project) : BoundConfigurable("rumdl") {

    private val configService = RumdlConfigService.getInstance(project)

    override fun createPanel(): DialogPanel {
        return panel {
            group("Executable") {
                row("rumdl path:") {
                    textFieldWithBrowseButton(
                        FileChooserDescriptor(true, false, false, false, false, false)
                            .withTitle("Select rumdl Executable"),
                        project
                    )
                        .bindText(
                            getter = { configService.state.rumdlPath ?: "" },
                            setter = { configService.state.rumdlPath = it.ifEmpty { null } }
                        )
                        .comment("Leave empty for auto-detection (virtualenv → PATH → common locations)")
                        .align(AlignX.FILL)
                }
                row {
                    checkBox("Use virtualenv")
                        .bindSelected(configService.state::useVirtualenv)
                        .comment("Look for rumdl in project's Python virtualenv first")
                }
                row {
                    val detected = Rumdl.detectExecutable(project)
                    val status = if (detected != null) {
                        "✓ Found: ${detected.absolutePath}"
                    } else {
                        "✗ Not found"
                    }
                    label(status)
                        .comment("Detected rumdl executable")
                }
            }

            group("Editor Integration") {
                row {
                    checkBox("Enable LSP")
                        .bindSelected(configService.state::enableLsp)
                        .comment("Enable Language Server Protocol for diagnostics and formatting")
                }
                row {
                    checkBox("Format on save")
                        .bindSelected(configService.state::formatOnSave)
                        .comment("Automatically format Markdown files when saving")
                }
            }
        }
    }

    override fun apply() {
        super.apply()
        // Restart LSP server if settings changed
        RumdlLspManager.getInstance(project).restart()
    }
}
