package composer.idea

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

/**
 * Balloon notifications in the "Composer" group (registered in plugin.xml),
 * rate-limited to once per [key] per IDE session — the sync loop can hit the
 * same failure on every keystroke, and one balloon is enough.
 */
object ComposerNotifications {
    private val shown = ConcurrentHashMap.newKeySet<String>()

    fun warnOnce(project: Project?, key: String, content: String) =
        once(project, key, content, NotificationType.WARNING)

    fun infoOnce(project: Project?, key: String, content: String) =
        once(project, key, content, NotificationType.INFORMATION)

    private fun once(project: Project?, key: String, content: String, type: NotificationType) {
        if (!shown.add(key)) return
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Composer")
            .createNotification(content, type)
            .notify(project)
    }
}
