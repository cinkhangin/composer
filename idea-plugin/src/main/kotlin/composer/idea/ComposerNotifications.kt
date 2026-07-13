package composer.idea

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import java.awt.datatransfer.StringSelection
import java.util.concurrent.ConcurrentHashMap

/**
 * Balloon notifications in the "Composer" group (registered in plugin.xml),
 * rate-limited to once per project + [key] per IDE session — the sync loop can
 * hit the same failure on every keystroke, while separate projects still need
 * their own diagnostics.
 */
object ComposerNotifications {
    private val shown = ConcurrentHashMap.newKeySet<String>()

    fun warnOnce(project: Project?, key: String, content: String) =
        once(project, key, content, NotificationType.WARNING)

    fun infoOnce(project: Project?, key: String, content: String) =
        once(project, key, content, NotificationType.INFORMATION)

    /** Info balloon with a "copy" action putting [copyText] on the clipboard. */
    fun infoOnceWithCopy(project: Project?, key: String, content: String, copyLabel: String, copyText: String) {
        if (!shown.add(scopedKey(project, key))) return
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Composer")
            .createNotification(content, NotificationType.INFORMATION)
            .addAction(object : NotificationAction(copyLabel) {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    CopyPasteManager.getInstance().setContents(StringSelection(copyText))
                    notification.expire()
                }
            })
            .notify(project)
    }

    private fun once(project: Project?, key: String, content: String, type: NotificationType) {
        if (!shown.add(scopedKey(project, key))) return
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Composer")
            .createNotification(content, type)
            .notify(project)
    }

    private fun scopedKey(project: Project?, key: String): String =
        "${project?.locationHash ?: "<application>"}:$key"
}
