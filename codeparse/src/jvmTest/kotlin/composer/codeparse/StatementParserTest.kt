package composer.codeparse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StatementParserTest {

    /** Parse [body] as a function body block; returns its statements. */
    private fun stmts(body: String): List<KStatement> {
        val src = "fun t() {\n$body\n}"
        val fn = scanSource(src).declarations.filterIsInstance<KFunctionDecl>().single()
        return assertNotNull(fn.bodyBlock).statements
    }

    private fun stmtTexts(body: String): List<String> {
        val src = "fun t() {\n$body\n}"
        val fn = scanSource(src).declarations.filterIsInstance<KFunctionDecl>().single()
        return fn.bodyBlock!!.statements.map { src.substring(it.range.first, it.range.last + 1) }
    }

    // ---- splitting ----

    @Test
    fun simple_statements_split_on_newlines() {
        assertEquals(listOf("""Text("a")""", """Text("b")"""), stmtTexts("Text(\"a\")\nText(\"b\")"))
    }

    @Test
    fun multiline_call_is_one_statement() {
        val body = "Column(\n    modifier = Modifier.padding(8.dp),\n) {\n    Text(\"x\")\n}"
        assertEquals(1, stmts(body).size)
    }

    @Test
    fun chained_calls_across_lines_glue() {
        val body = "value\n    .map { it }\n    .filter { true }"
        assertEquals(1, stmts(body).size)
    }

    @Test
    fun elvis_at_line_start_glues() {
        assertEquals(1, stmts("val x = a\n    ?: b").size)
    }

    @Test
    fun operator_at_line_end_glues() {
        assertEquals(1, stmts("val x = a +\n    b").size)
    }

    @Test
    fun plus_at_line_start_does_not_glue() {
        // Kotlin semantics: `a` then `+b` are two statements.
        assertEquals(2, stmts("a\n+ b").size)
    }

    @Test
    fun semicolons_split() {
        assertEquals(listOf("foo()", "bar()"), stmtTexts("foo(); bar()"))
    }

    @Test
    fun braceless_if_keeps_its_body() {
        assertEquals(1, stmts("if (x)\n    foo()").size)
    }

    @Test
    fun single_line_if_does_not_swallow_next_statement() {
        assertEquals(2, stmts("if (x) foo()\nbar()").size)
    }

    @Test
    fun if_else_blocks_are_one_statement() {
        val body = "if (x) {\n    a()\n} else {\n    b()\n}"
        assertEquals(1, stmts(body).size)
    }

    @Test
    fun else_on_next_line_glues() {
        val body = "if (x) {\n    a()\n}\nelse {\n    b()\n}"
        assertEquals(1, stmts(body).size)
    }

    @Test
    fun when_expression_is_one_statement() {
        val body = "when (x) {\n    1 -> a()\n    else -> b()\n}\nafter()"
        assertEquals(2, stmts(body).size)
    }

    @Test
    fun annotated_statement_glues() {
        val body = "@Suppress(\"x\")\nfoo()"
        assertEquals(1, stmts(body).size)
    }

    @Test
    fun trailing_lambda_on_next_line_glues() {
        val body = "items(list)\n{ item -> Text(item) }"
        assertEquals(1, stmts(body).size)
    }

    @Test
    fun try_catch_is_one_statement() {
        val body = "try {\n    a()\n} catch (e: Exception) {\n    b()\n} finally {\n    c()\n}\nafter()"
        assertEquals(2, stmts(body).size)
    }

    @Test
    fun statement_ranges_are_trimmed() {
        val src = "fun t() {\n    Text(\"a\")   \n}"
        val fn = scanSource(src).declarations.filterIsInstance<KFunctionDecl>().single()
        val st = fn.bodyBlock!!.statements.single()
        assertEquals("Text(\"a\")", src.substring(st.range.first, st.range.last + 1))
    }

    // ---- expression parsing ----

    @Test
    fun call_with_named_args_and_trailing_lambda() {
        val st = stmts("Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.Center) { Text(\"x\") }").single()
        val call = assertIs<KExprStatement>(st).expr as KCall
        assertEquals("Column", (call.callee as KName).name)
        assertEquals(2, call.args.size)
        assertEquals(listOf("modifier", "verticalArrangement"), call.args.map { it.name })
        assertEquals(1, call.trailingLambdas.size)
        assertEquals(1, call.trailingLambdas[0].body.statements.size)
    }

    @Test
    fun modifier_chain_shape() {
        val st = stmts("Text(\"x\", modifier = Modifier.padding(16.dp).fillMaxWidth())").single()
        val call = assertIs<KExprStatement>(st).expr as KCall
        val mod = call.args[1].expr
        // Modifier.padding(16.dp).fillMaxWidth() → KDot(KDot(KName, KCall), KCall)
        val outer = assertIs<KDot>(mod)
        assertIs<KCall>(outer.selector)
        val inner = assertIs<KDot>(outer.receiver)
        assertEquals("Modifier", (inner.receiver as KName).name)
    }

    @Test
    fun state_declaration_shape() {
        val st = stmts("var state1 by remember { mutableStateOf(true) }").single()
        val prop = assertIs<KPropertyStatement>(st)
        assertTrue(prop.isVar)
        assertEquals("state1", prop.name)
        val remember = assertIs<KCall>(prop.delegate)
        assertEquals("remember", (remember.callee as KName).name)
        assertEquals(0, remember.args.size)
        val lambda = remember.trailingLambdas.single()
        assertTrue(lambda.params.isEmpty())
        val only = assertIs<KExprStatement>(lambda.body.statements.single()).expr
        assertEquals("mutableStateOf", ((only as KCall).callee as KName).name)
        assertEquals("true", (only.args.single().expr as KConst).text)
    }

    @Test
    fun assignment_statement() {
        val st = stmts("state1 = it").single()
        val bin = assertIs<KBinary>(assertIs<KExprStatement>(st).expr)
        assertEquals("=", bin.op)
        assertEquals("state1", (bin.left as KName).name)
        assertEquals("it", (bin.right as KName).name)
    }

    @Test
    fun toggle_lambda_shape() {
        val st = stmts("Switch(checked = state1, onCheckedChange = { state1 = it })").single()
        val call = assertIs<KCall>(assertIs<KExprStatement>(st).expr)
        val lambda = assertIs<KLambda>(call.args[1].expr)
        val body = assertIs<KBinary>(assertIs<KExprStatement>(lambda.body.statements.single()).expr)
        assertEquals("=", body.op)
    }

    @Test
    fun negative_literals_and_units() {
        val st = stmts("Box(modifier = Modifier.offset((-8).dp, 4.dp))").single()
        val call = assertIs<KCall>(assertIs<KExprStatement>(st).expr)
        val offset = assertIs<KDot>(call.args[0].expr)
        val offsetCall = assertIs<KCall>(offset.selector)
        val firstArg = offsetCall.args[0].expr
        val dot = assertIs<KDot>(firstArg)
        val paren = assertIs<KParen>(dot.receiver)
        val neg = assertIs<KPrefix>(paren.inner)
        assertEquals("-", neg.op)
        assertEquals("8", (neg.base as KConst).text)
    }

    @Test
    fun division_binary_for_aspect_ratio() {
        val st = stmts("Image(modifier = Modifier.aspectRatio(16f / 9f))").single()
        val call = assertIs<KCall>(assertIs<KExprStatement>(st).expr)
        val chain = assertIs<KDot>(call.args[0].expr)
        val ar = assertIs<KCall>(chain.selector)
        val div = assertIs<KBinary>(ar.args[0].expr)
        assertEquals("/", div.op)
        assertEquals("16f", (div.left as KConst).text)
    }

    @Test
    fun lambda_with_parameter_names() {
        val st = stmts("Scaffold { innerPadding ->\n    Text(\"x\")\n}").single()
        val call = assertIs<KCall>(assertIs<KExprStatement>(st).expr)
        assertEquals(listOf("innerPadding"), call.trailingLambdas.single().params)
    }

    @Test
    fun unrecognized_statements_become_unknown_with_full_extent() {
        val texts = stmtTexts("for (i in 0..2) {\n    Text(\"a\")\n}\nText(\"b\")")
        assertEquals(2, texts.size)
        assertTrue(texts[0].startsWith("for"))
        assertTrue(texts[0].endsWith("}"))
        val sts = stmts("for (i in 0..2) {\n    Text(\"a\")\n}\nText(\"b\")")
        assertIs<KUnknownStatement>(sts[0])
        assertIs<KExprStatement>(sts[1])
    }

    @Test
    fun safe_call_is_marked() {
        val st = stmts("thing?.doIt()").single()
        val dot = assertIs<KDot>(assertIs<KExprStatement>(st).expr)
        assertTrue(dot.safe)
    }

    @Test
    fun type_arguments_flagged_on_call() {
        val st = stmts("listOf<String>(\"a\")").single()
        val call = assertIs<KCall>(assertIs<KExprStatement>(st).expr)
        assertTrue(call.hasTypeArgs)
    }

    @Test
    fun comparison_less_than_is_binary_not_type_args() {
        val st = stmts("a < b").single()
        val bin = assertIs<KBinary>(assertIs<KExprStatement>(st).expr)
        assertEquals("<", bin.op)
    }

    @Test
    fun destructuring_declaration_is_a_property_without_name() {
        val st = stmts("val (a, b) = pair()").single()
        val prop = assertIs<KPropertyStatement>(st)
        assertEquals(null, prop.name)
    }

    @Test
    fun statement_tokens_cover_nested_lambdas() {
        val st = stmts("Column {\n    Text(counter)\n}").single()
        assertTrue(st.tokens.any { it.kind == TokKind.IDENT && it.text == "counter" })
    }
}
