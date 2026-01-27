package com.rumdl.intellij

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project

/**
 * Helper object for rumdl plugin notifications.
 */
object RumdlNotifications {
    private const val NOTIFICATION_GROUP_ID = "rumdl"
    private const val INSTALL_DOCS_URL = "https://rumdl.dev/installation/"

    /**
     * Shows a notification that rumdl was not found, with actions to install or configure.
     * Only shows once per project unless user opens settings.
     */
    fun notifyRumdlNotFound(project: Project) {
        val configService = RumdlConfigService.getInstance(project)

        // Don't show if already dismissed
        if (configService.state.notFoundNotificationDismissed) {
            return
        }

        val notificationGroup = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID) ?: return

        val notification = notificationGroup.createNotification(
            "rumdl not found",
            "Install rumdl to enable Markdown linting. Run <code>pip install rumdl</code> or <code>cargo install rumdl</code>, or configure the path in settings.",
            NotificationType.WARNING
        )

        notification.addAction(NotificationAction.createSimple("Installation Guide") {
            BrowserUtil.browse(INSTALL_DOCS_URL)
        })

        notification.addAction(NotificationAction.createSimple("Configure") {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, RumdlConfigurable::class.java)
        })

        notification.addAction(NotificationAction.createSimple("Don't Show Again") {
            configService.state.notFoundNotificationDismissed = true
            notification.expire()
        })

        notification.notify(project)

        // Mark as shown (will be reset if user opens settings and changes path)
        configService.state.notFoundNotificationDismissed = true
    }

    /**
     * Resets the notification dismissed state (called when settings change).
     */
    fun resetNotificationState(project: Project) {
        val configService = RumdlConfigService.getInstance(project)
        configService.state.notFoundNotificationDismissed = false
    }
}
