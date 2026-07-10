package composer.codeparse

import composer.codegen.CodeGen
import org.jetbrains.kotlin.psi.KtNamedFunction
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Byte-equality oracle: the hand-rolled scanner must agree with the Kotlin
 * compiler's PSI on every offset write-back depends on — function ranges,
 * verbatim parameter lists, statement ranges, imports, and the import insert
 * offset. This is the safety net for replacing PSI with the common-code
 * front-end; kotlin-compiler stays a TEST-ONLY dependency for it.
 */
class PsiConformanceTest {

    private fun assertConforms(text: String) {
        val file = PsiTestEnv.ktFile(text)
        val scanned = scanSource(text)

        val psiFns = file.declarations.filterIsInstance<KtNamedFunction>()
        val myFns = scanned.declarations.filterIsInstance<KFunctionDecl>()
        assertEquals(psiFns.map { it.name }, myFns.map { it.name }, "function names\n$text")
        psiFns.zip(myFns).forEach { (p, m) ->
            assertEquals(
                p.textRange.startOffset until p.textRange.endOffset, m.range,
                "fnRange of ${p.name}\n$text",
            )
            assertEquals(p.valueParameterList?.text ?: "()", m.paramListText, "params of ${p.name}\n$text")
            val pb = p.bodyBlockExpression
            val mb = m.bodyBlock
            assertEquals(pb == null, mb == null, "body kind of ${p.name}\n$text")
            if (pb != null && mb != null) {
                assertEquals(
                    pb.statements.map { it.textRange.startOffset until it.textRange.endOffset },
                    mb.statements.map { it.range },
                    "statement ranges in ${p.name}\n$text",
                )
            }
        }

        assertEquals(
            file.importDirectives.mapNotNull { it.importPath?.pathStr },
            scanned.imports.map { it.pathStr },
            "imports\n$text",
        )
        val psiInsert = file.importDirectives.lastOrNull()?.textRange?.endOffset
            ?: file.packageDirective?.textRange?.endOffset ?: 0
        val myInsert = scanned.imports.lastOrNull()?.endOffset ?: scanned.packageEndOffset ?: 0
        assertEquals(psiInsert, myInsert, "import insert offset\n$text")
    }

    @Test
    fun agrees_with_psi_on_all_generated_seeds() {
        for (seed in 1..150) {
            assertConforms(CodeGen.generate(TreeGen(seed).design()))
        }
    }

    @Test
    fun agrees_with_psi_on_adversarial_files() {
        val corpus = listOf(
            // Expression bodies, receivers, generics, return types.
            """
            package a.b

            import x.y.Z

            fun price(): String = "$" + amount / 100.0
            fun String.ext(n: Int = 1): Int = n
            fun <T : Comparable<T>> max2(a: T, b: T): T = if (a > b) a else b
            fun lambdaRet(): (Int) -> Unit = { }
            """.trimIndent(),
            // KDoc ownership, plain comments, annotations with args.
            """
            /** Doc comment. */
            @OptIn(ExperimentalMaterial3Api::class)
            @Composable
            fun WithDoc() {
                Text("x")
            }

            // plain comment — not part of the declaration
            @Composable
            fun Plain() { }
            """.trimIndent(),
            // Statement splitting: chains, elvis, semicolons, control flow.
            """
            @Composable
            fun Statements() {
                val x = listOf(1, 2)
                    .map { it * 2 }
                    .filter { it > 1 }
                val y = a
                    ?: b
                foo(); bar()
                if (x.isEmpty()) {
                    foo()
                } else {
                    bar()
                }
                when (y) {
                    1 -> one()
                    else -> other()
                }
                try {
                    risky()
                } catch (e: Exception) {
                    handle(e)
                }
                for (i in 0 until 10) step(i)
                var state1 by remember { mutableStateOf(true) }
                Switch(checked = state1, onCheckedChange = { state1 = it })
            }
            """.trimIndent(),
            // Strings that could derail a lexer.
            """
            @Composable
            fun Strings() {
                Text("dollar ${'$'}{price} and \n escape")
                Text("plain")
                raw(${'"'}${'"'}${'"'}multi "quoted" line${'"'}${'"'}${'"'})
            }
            """.trimIndent(),
            // Classes/objects with member functions and nested braces.
            """
            enum class Kind { A, B }

            class Widget(val id: Int) : Base(), Iface {
                @Composable
                fun Member() { Text("m") }
                companion object {
                    fun of() = Widget(1)
                }
            }

            @Composable
            fun After() { }
            """.trimIndent(),
            // Properties with accessors, typealiases, file annotations.
            """
            @file:Suppress("unused")
            package p.q

            import r.s.T

            val computed: Int
                get() = 42

            var counter = 0
                private set

            typealias Handler = (Int) -> Unit

            @Composable
            fun Last() { }
            """.trimIndent(),
            // No package, no imports.
            """
            @Composable
            fun Bare() { Text("b") }
            """.trimIndent(),
            // Trailing lambda on its own line binds to the call (Kotlin gotcha).
            """
            @Composable
            fun Dangling() {
                items(list)
                { item -> Text(item) }
            }
            """.trimIndent(),
        )
        corpus.forEach(::assertConforms)
    }
}
