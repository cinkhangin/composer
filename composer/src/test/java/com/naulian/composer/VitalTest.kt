package com.naulian.composer

import org.junit.Test

class VitalTest {

    @Test
    fun testVital() {
        val source = """
            ~!@#$%^&*()_+=-0987654321`q 
            uyueriewotkwerl[]\|';"
            :?></.,jdfksjgiu  ,mlkjfkj
            dfkajdfjakf87q854r7q9
            dfajkdfjk(*&^%^$&^*Y
        """.trimIndent()


        val task = fuckAround {
            ComposerParser(source).parse()
        } and findOut()

        when (task) {
            is Exception -> {
                assert(false)
            }

            is List<*> -> {
                assert(true)
            }
        }
    }
}

class FuckAround(val block: () -> Any)

infix fun FuckAround.and(catch: FuckAround): Any {
    return try {
        this.block()
    } catch (e: Exception) {
        catch.block()
    }
}

fun fuckAround(block: () -> Unit = {}): FuckAround {
    return FuckAround(block = block)
}

fun findOut(block: () -> Any = {}): FuckAround {
    return FuckAround(block = block)
}