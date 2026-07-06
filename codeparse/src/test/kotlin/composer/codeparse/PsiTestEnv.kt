package composer.codeparse

import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Standalone Kotlin PSI for tests — a bare [KotlinCoreEnvironment], no IDE.
 * The parser itself codes only against the stable org.jetbrains.kotlin.psi /
 * com.intellij.psi surface, which the real IDE provides at runtime.
 */
object PsiTestEnv {
    @OptIn(CompilerConfiguration.Internals::class, org.jetbrains.kotlin.K1Deprecation::class)
    private val env: KotlinCoreEnvironment by lazy {
        val conf = CompilerConfiguration()
        conf.put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        conf.put(CommonConfigurationKeys.MODULE_NAME, "codeparse-test")
        KotlinCoreEnvironment.createForProduction(
            Disposer.newDisposable("codeparse-test"),
            conf,
            EnvironmentConfigFiles.JVM_CONFIG_FILES,
        )
    }

    fun ktFile(text: String): KtFile = KtPsiFactory(env.project).createFile("Test.kt", text)
}
