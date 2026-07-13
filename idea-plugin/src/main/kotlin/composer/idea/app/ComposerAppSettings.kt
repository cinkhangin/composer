package composer.idea.app

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Per-project whole-app designer configuration, stored in `.idea/composer.xml`
 * (shareable). [mainActivityUrl] anchors discovery: the app's files are the
 * `MainActivity.kt` plus the screen-triplet files in its directory — the
 * PARSER decides actual ownership, the config only prunes candidates.
 */
@Service(Service.Level.PROJECT)
@State(name = "ComposerApp", storages = [Storage("composer.xml")])
class ComposerAppSettings : PersistentStateComponent<ComposerAppSettings.State> {

    class State {
        var enabled: Boolean = false
        var mainActivityUrl: String = ""
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }
}
