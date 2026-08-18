package composer.codegen

import composer.model.DesignTheme
import composer.model.NamedTheme
import composer.model.NavAction
import composer.model.Node
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppCodeGenTest {

    /** Fixed sample app: nav both ways, a registered component instance, a custom theme. */
    private fun sampleApp(): Node.Artboard = Node.Artboard(
        id = "a",
        composables = listOf(
            Node.Composable(
                "s1",
                children = listOf(
                    Node.Text("t1", "Welcome"),
                    Node.Button("b1", children = listOf(Node.Text("bt", "Sign in")), navAction = NavAction.Navigate("s2")),
                    Node.Fab("f1", navAction = NavAction.Back),
                ),
            ),
            Node.Composable(
                "s2",
                children = listOf(
                    Node.Text("t2", "You are home"),
                    Node.Instance("i1", refId = "s3"),
                ),
            ),
            Node.Composable("s3", children = listOf(Node.Text("t3", "widget"))),
        ),
        layerNames = mapOf("s1" to "Login Screen", "s2" to "Home", "s3" to "Stat Card"),
        componentIds = listOf("s3"),
        themes = listOf(NamedTheme("Brand", DesignTheme(primary = 0xFF6366F1))),
    )

    @Test
    fun name_plan_strips_screen_suffix_and_reserves_app_names() {
        val plan = AppCodeGen.appNamePlan(sampleApp())
        assertEquals(listOf("Login", "Home", "StatCard"), plan.bases)
        assertTrue(plan.themed)
        assertEquals(listOf("BrandColors"), plan.schemeVals)
        // Screens named like reserved words dedupe away from them.
        val clash = Node.Artboard(
            id = "a",
            composables = listOf(Node.Composable("s1"), Node.Composable("s2")),
            layerNames = mapOf("s1" to "Main Activity", "s2" to "App"),
        )
        assertEquals(listOf("MainActivity2", "App2"), AppCodeGen.appNamePlan(clash).bases)
    }

    @Test
    fun generates_three_files_per_screen_plus_main_activity() {
        val files = AppCodeGen.generate(sampleApp(), "com.example.app")
        assertEquals(
            listOf(
                "LoginScreenUI.kt", "LoginScreen.kt", "LoginViewModel.kt",
                "HomeScreenUI.kt", "HomeScreen.kt", "HomeViewModel.kt",
                "StatCardScreenUI.kt", "StatCardScreen.kt", "StatCardViewModel.kt",
                "AppTheme.kt",
                "MainActivity.kt",
            ),
            files.map { it.path },
        )
        for (f in files) {
            assertTrue(f.text.startsWith("package com.example.app\n"), f.path)
            assertTrue(
                Regex("(?m)^@Composable\\s*$").findAll(f.text).count() <= 1,
                "${f.path} contains multiple non-preview composable functions",
            )
            CodeGenTest().assertLexicallyValid(f.text)
        }
    }

    @Test
    fun empty_app_and_default_package_generate_valid_source() {
        val files = AppCodeGen.generate(Node.Artboard("a"), "")
        assertEquals(listOf("MainActivity.kt"), files.map { it.path })
        val main = files.single().text
        assertTrue(!main.startsWith("package "))
        assertTrue("rememberNavBackStack()" in main)
        assertTrue("TODO" !in main)
        CodeGenTest().assertLexicallyValid(main)
    }

    @Test
    fun matches_the_golden_file_set() {
        val files = AppCodeGen.generate(sampleApp(), "com.example.app")
        val missing = files.filter { javaClass.getResource("/golden/app_sample/${it.path}") == null }
        if (missing.isNotEmpty()) {
            // Bootstrap mode: write the goldens and fail so they get reviewed + committed.
            val dir = File("src/jvmTest/resources/golden/app_sample")
            dir.mkdirs()
            for (f in missing) File(dir, f.path).writeText(f.text)
            error("goldens missing — wrote ${missing.size} file(s) to src/jvmTest/resources/golden/app_sample; review and re-run")
        }
        for (f in files) {
            assertEquals(javaClass.getResource("/golden/app_sample/${f.path}")!!.readText(), f.text, "drift in ${f.path}")
        }
    }
}
