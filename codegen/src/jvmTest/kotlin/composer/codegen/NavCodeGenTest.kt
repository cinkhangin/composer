package composer.codegen

import composer.model.ChipVariant
import composer.model.NavAction
import composer.model.Node
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavCodeGenTest {

    private fun twoScreens(vararg firstKids: Node): Node.Artboard = Node.Artboard(
        id = "a",
        composables = listOf(
            Node.Composable("s1", children = firstKids.toList()),
            Node.Composable("s2", children = listOf(Node.Text("t", "hi"))),
        ),
        layerNames = mapOf("s1" to "Login", "s2" to "Home"),
    )

    @Test
    fun nav_actions_synthesize_params_and_wire_click_sites() {
        val code = CodeGen.generate(
            twoScreens(
                Node.Button("b", navAction = NavAction.Navigate("s2"), children = listOf(Node.Text("bt", "Go"))),
                Node.Fab("f", navAction = NavAction.Back),
            ),
        )
        assertTrue("fun Login(onNavigateToHome: () -> Unit = {}, onBack: () -> Unit = {}) {" in code, code)
        assertTrue("Button(onClick = onNavigateToHome) {" in code, code)
        assertTrue("FloatingActionButton(onClick = onBack) {" in code, code)
        assertTrue("fun Home() {" in code, code) // target has no actions of its own
    }

    @Test
    fun duplicate_targets_share_one_param() {
        val code = CodeGen.generate(
            twoScreens(
                Node.Button("b1", navAction = NavAction.Navigate("s2")),
                Node.Button("b2", navAction = NavAction.Navigate("s2")),
            ),
        )
        assertTrue("fun Login(onNavigateToHome: () -> Unit = {}) {" in code, code)
    }

    @Test
    fun clickable_card_uses_the_onclick_overload() {
        val code = CodeGen.generate(
            twoScreens(Node.Card("c", children = listOf(Node.Text("t2", "x")), navAction = NavAction.Navigate("s2"))),
        )
        assertTrue("Card(onClick = onNavigateToHome) {" in code, code)
    }

    @Test
    fun dangling_navigate_emits_exactly_like_none() {
        val code = CodeGen.generate(
            twoScreens(
                Node.Button("b", navAction = NavAction.Navigate("gone")),
                Node.Card("c", children = listOf(Node.Text("t2", "x")), navAction = NavAction.Navigate("gone")),
            ),
        )
        assertTrue("fun Login() {" in code, code)
        assertTrue("Button(onClick = {}) {" in code, code)
        assertFalse("Card(onClick" in code, code)
    }

    @Test
    fun chip_with_nav_suppresses_state_hoisting() {
        val code = CodeGen.generate(
            twoScreens(Node.Chip("c", label = "Go", variant = ChipVariant.Filter, selected = true, navAction = NavAction.Navigate("s2"))),
        )
        assertTrue("selected = true" in code, code)
        assertTrue("onClick = onNavigateToHome" in code, code)
        assertFalse("remember" in code, code)
    }
}
