package composer.codeparse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LexerTest {

    private fun tokens(src: String): List<Token> = lex(src).tokens

    @Test
    fun identifiers_numbers_and_punctuation() {
        val toks = tokens("val x = foo(1_000, 0xFF_00, 0.5f, 16F, 3L)")
        assertEquals(
            listOf("val", "x", "=", "foo", "(", "1_000", ",", "0xFF_00", ",", "0.5f", ",", "16F", ",", "3L", ")"),
            toks.map { it.text },
        )
        assertEquals(TokKind.NUMBER, toks[5].kind)
        assertEquals(TokKind.NUMBER, toks[7].kind)
        assertEquals(TokKind.NUMBER, toks[9].kind)
    }

    @Test
    fun dot_after_int_is_member_access_not_fraction() {
        val toks = tokens("8.dp 1..2 0.5")
        assertEquals(listOf("8", ".", "dp", "1", "..", "2", "0.5"), toks.map { it.text })
    }

    @Test
    fun backticked_identifier_keeps_name_and_flag() {
        val t = tokens("`fun else`").single()
        assertEquals(TokKind.IDENT, t.kind)
        assertEquals("fun else", t.text)
        assertTrue(t.backticked)
    }

    @Test
    fun escaped_string_entries() {
        val t = tokens("\"a\\n\\\"b\\\$c\"").single()
        assertEquals(TokKind.STRING, t.kind)
        val entries = t.stringEntries!!
        assertEquals(
            listOf("a", "\n", "\"", "b", "$", "c"),
            entries.map { e ->
                when (e) {
                    is KStringEntry.Literal -> e.text
                    is KStringEntry.Escape -> e.unescaped
                    KStringEntry.Interpolation -> "\$INTERP"
                }
            },
        )
    }

    @Test
    fun invalid_escape_yields_null_unescaped() {
        val t = tokens(""""a\q"""").single()
        val esc = t.stringEntries!!.filterIsInstance<KStringEntry.Escape>().single()
        assertNull(esc.unescaped)
    }

    @Test
    fun unicode_escape_decodes() {
        val t = tokens("\"\\u0041\"").single()
        val esc = t.stringEntries!!.filterIsInstance<KStringEntry.Escape>().single()
        assertEquals("A", esc.unescaped)
    }

    @Test
    fun interpolation_is_atomic_even_with_nested_strings_and_braces() {
        val src = "\"x\${ if (a) \"b{\" else c }y\" + z"
        val toks = tokens(src)
        assertEquals(TokKind.STRING, toks[0].kind)
        assertEquals(listOf("+", "z"), toks.drop(1).map { it.text })
        val kinds = toks[0].stringEntries!!.map { it::class.simpleName }
        assertEquals(listOf("Literal", "Interpolation", "Literal"), kinds)
    }

    @Test
    fun simple_name_interpolation() {
        val t = tokens("\"a\$name b\"").single()
        assertTrue(t.stringEntries!!.any { it is KStringEntry.Interpolation })
    }

    @Test
    fun dollar_not_starting_template_is_literal() {
        val t = tokens("\"\$ \$1\"").single()
        assertTrue(t.stringEntries!!.none { it is KStringEntry.Interpolation })
    }

    @Test
    fun raw_string_with_quotes_and_template() {
        val src = "\"\"\"a\"b\${x}c\"\"\"\" + y" // raw containing a quote + template, extra quote at end
        val toks = tokens(src)
        assertEquals(TokKind.STRING, toks[0].kind)
        assertTrue(toks[0].stringRaw)
        assertEquals(listOf("+", "y"), toks.drop(1).map { it.text })
    }

    @Test
    fun nested_block_comments() {
        val r = lex("a /* x /* y */ z */ b")
        assertEquals(listOf("a", "b"), r.tokens.map { it.text })
        assertEquals("/* x /* y */ z */", r.comments.single().text)
    }

    @Test
    fun line_comment_excludes_newline() {
        val r = lex("a // hi\nb")
        assertEquals("// hi", r.comments.single().text)
        assertTrue(r.tokens[1].newlineBefore)
    }

    @Test
    fun multiline_block_comment_sets_newline_before() {
        val r = lex("a /* x\ny */ b")
        assertTrue(r.tokens[1].newlineBefore)
    }

    @Test
    fun same_line_block_comment_does_not_set_newline() {
        val r = lex("a /* x */ b")
        assertTrue(!r.tokens[1].newlineBefore)
    }

    @Test
    fun char_literals() {
        val toks = tokens("""'a' '\'' '\\' 'A'""")
        assertEquals(4, toks.size)
        assertTrue(toks.all { it.kind == TokKind.CHAR })
    }

    @Test
    fun shebang_is_skipped() {
        val toks = tokens("#!/usr/bin/env kotlin\nfoo")
        assertEquals(listOf("foo"), toks.map { it.text })
    }

    @Test
    fun longest_match_punctuation() {
        val toks = tokens("a === b !== c ..< d ?: e ?. f -> g")
        assertEquals(listOf("a", "===", "b", "!==", "c", "..<", "d", "?:", "e", "?.", "f", "->", "g"), toks.map { it.text })
    }

    @Test
    fun offsets_are_exact() {
        val src = "ab + cd"
        val toks = tokens(src)
        assertEquals("ab", src.substring(toks[0].start, toks[0].end))
        assertEquals("+", src.substring(toks[1].start, toks[1].end))
        assertEquals("cd", src.substring(toks[2].start, toks[2].end))
    }
}
