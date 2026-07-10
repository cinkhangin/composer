package composer.codeparse

import composer.codegen.CodeGen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The correctness anchor: for the entire supported subset, the parser is the
 * exact inverse of codegen — `parse(generate(tree)) == tree` (modulo ids and the
 * documented lossy normalizations, see [canon]) and regenerating a parsed file
 * reproduces it byte-for-byte.
 */
class RoundTripTest {

    @Test
    fun parse_inverts_codegen_across_random_trees() {
        for (seed in 1..150) {
            val tree = TreeGen(seed).design()
            val code = CodeGen.generate(tree)
            val parsed = DesignParser.parse(code)
            assertNotNull(parsed, "seed $seed produced unparseable output:\n$code")
            assertEquals(
                canon(tree),
                canon(parsed.artboard),
                "seed $seed round-trip mismatch. Generated code:\n$code",
            )
        }
    }

    @Test
    fun regenerating_a_parsed_file_is_byte_identical() {
        for (seed in 1..150) {
            val tree = TreeGen(seed).design()
            val code = CodeGen.generate(tree)
            val parsed = DesignParser.parse(code)
            assertNotNull(parsed, "seed $seed unparseable:\n$code")
            val regenerated = CodeGen.generate(parsed.artboard)
            assertEquals(code, regenerated, "seed $seed regeneration drifted")
        }
    }

    @Test
    fun reparse_of_unchanged_text_yields_stable_ids_and_hashes() {
        val tree = TreeGen(7).design()
        val code = CodeGen.generate(tree)
        val a = DesignParser.parse(code)!!
        val b = DesignParser.parse(code)!!
        assertEquals(a.artboard, b.artboard)
        assertEquals(a.functions, b.functions)
    }
}
