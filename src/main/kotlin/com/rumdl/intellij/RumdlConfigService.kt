package com.rumdl.intellij

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

/**
 * Persistent state service for rumdl plugin configuration.
 * Settings are stored per-project.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "RumdlConfigService",
    storages = [Storage("rumdl.xml")]
)
class RumdlConfigService : PersistentStateComponent<RumdlConfigService.State> {

    private var myState = State()

    /**
     * Configuration state that gets persisted.
     */
    data class State(
        /** Custom path to rumdl executable (overrides auto-detection) */
        var rumdlPath: String? = null,

        /** Enable LSP-based features */
        var enableLsp: Boolean = true,

        /** Enable format on save */
        var formatOnSave: Boolean = false,

        /** Use rumdl from project's Python virtualenv if available */
        var useVirtualenv: Boolean = true,

        /** Whether the "rumdl not found" notification has been dismissed */
        var notFoundNotificationDismissed: Boolean = false,
    )

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): RumdlConfigService {
            return project.service()
        }
    }
}
