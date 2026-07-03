package composer

import composer.model.ButtonVariant
import composer.model.GradientDirection
import composer.model.HAlignment
import composer.model.IconKind
import composer.model.ModifierSpec.Background
import composer.model.ModifierSpec.Clip
import composer.model.ModifierSpec.DropShadow
import composer.model.ModifierSpec.FillMaxSize
import composer.model.ModifierSpec.FillMaxWidth
import composer.model.ModifierSpec.Height
import composer.model.ModifierSpec.Padding
import composer.model.ModifierSpec.Size
import composer.model.ModifierSpec.Weight
import composer.model.CornerUnit
import composer.model.Node
import composer.model.TextAlignment
import composer.model.TextWeight
import composer.model.ThemeColorRef
import composer.model.VArrangement

/**
 * Built-in starter designs shown on the home page — separate from the user's
 * files. Opening one starts a NEW design seeded with the template tree (saved
 * as the user's own file on first edit, so templates are never mutated).
 */
class Template(val name: String, val description: String, val glyph: String, val build: () -> Node)

val templates: List<Template> = listOf(
    Template("Login", "Email + password sign-in form", "TextField") { loginTemplate() },
    Template("Onboarding", "Gradient hero with call-to-action", "Image") { onboardingTemplate() },
    Template("Profile", "Avatar, stats and actions", "Fab") { profileTemplate() },
    Template("Dashboard", "Stat cards and settings list", "Card") { dashboardTemplate() },
)

private val primary = ThemeColorRef.token("primary")!!
private val onPrimary = ThemeColorRef.token("onPrimary")!!
private val background = ThemeColorRef.token("background")!!

private fun loginTemplate(): Node = Node.Artboard(
    id = "lg-art",
    layerNames = mapOf("lg-screen" to "Login"),
    composables = listOf(
        Node.Composable(
            id = "lg-screen", width = 390, height = 844,
            children = listOf(
                Node.Column(
                    id = "lg-col",
                    modifier = listOf(FillMaxSize, Background(background), Padding(24)),
                    verticalArrangement = VArrangement.Center,
                    horizontalAlignment = HAlignment.Center,
                    spacing = 12,
                    children = listOf(
                        Node.Text("lg-title", "Welcome back", fontSize = 28, fontWeight = TextWeight.Bold),
                        Node.Text("lg-sub", "Sign in to continue", color = 0xFF8A90A0),
                        Node.Spacer("lg-gap1", modifier = listOf(Height(12))),
                        Node.TextField("lg-email", placeholder = "Email", modifier = listOf(FillMaxWidth)),
                        Node.TextField("lg-pass", placeholder = "Password", modifier = listOf(FillMaxWidth)),
                        Node.Spacer("lg-gap2", modifier = listOf(Height(8))),
                        Node.Button(
                            "lg-signin", modifier = listOf(FillMaxWidth),
                            children = listOf(Node.Text("lg-signin-t", "Sign in")),
                        ),
                        Node.Button(
                            "lg-forgot", variant = ButtonVariant.Text,
                            children = listOf(Node.Text("lg-forgot-t", "Forgot password?")),
                        ),
                    ),
                ),
            ),
        ),
    ),
)

private fun onboardingTemplate(): Node = Node.Artboard(
    id = "ob-art",
    layerNames = mapOf("ob-screen" to "Onboarding"),
    composables = listOf(
        Node.Composable(
            id = "ob-screen", width = 390, height = 844,
            children = listOf(
                Node.Column(
                    id = "ob-col",
                    modifier = listOf(
                        FillMaxSize,
                        Background(0xFF5563E8, colors = listOf(0xFF5563E8, 0xFF9C27B0), direction = GradientDirection.Vertical),
                        Padding(28),
                    ),
                    verticalArrangement = VArrangement.Center,
                    horizontalAlignment = HAlignment.Center,
                    spacing = 16,
                    children = listOf(
                        Node.Box(
                            "ob-hero",
                            modifier = listOf(Size(140, 140), Background(0x33FFFFFF, corner = 50, cornerUnit = CornerUnit.Percent)),
                            children = listOf(Node.Icon("ob-hero-ic", IconKind.Star, modifier = listOf(Size(64, 64)))),
                            contentAlignment = composer.model.BoxAlignment.Center,
                        ),
                        Node.Spacer("ob-gap", modifier = listOf(Height(12))),
                        Node.Text("ob-title", "Design with real code", fontSize = 26, fontWeight = TextWeight.Bold, color = 0xFFFFFFFF, textAlign = TextAlignment.Center),
                        Node.Text(
                            "ob-body",
                            "Drag, drop and arrange Compose components.\nExport clean Kotlin when you're done.",
                            color = 0xCCFFFFFF, textAlign = TextAlignment.Center,
                        ),
                        Node.Row(
                            "ob-dots", spacing = 6,
                            children = listOf(
                                Node.Box("ob-d1", modifier = listOf(Size(8, 8), Background(0xFFFFFFFF, corner = 50, cornerUnit = CornerUnit.Percent))),
                                Node.Box("ob-d2", modifier = listOf(Size(8, 8), Background(0x66FFFFFF, corner = 50, cornerUnit = CornerUnit.Percent))),
                                Node.Box("ob-d3", modifier = listOf(Size(8, 8), Background(0x66FFFFFF, corner = 50, cornerUnit = CornerUnit.Percent))),
                            ),
                        ),
                        Node.Spacer("ob-gap2", modifier = listOf(Height(8))),
                        Node.Button(
                            "ob-cta", variant = ButtonVariant.Elevated, modifier = listOf(FillMaxWidth),
                            children = listOf(Node.Text("ob-cta-t", "Get started")),
                        ),
                    ),
                ),
            ),
        ),
    ),
)

private fun profileTemplate(): Node = Node.Artboard(
    id = "pf-art",
    layerNames = mapOf("pf-screen" to "Profile"),
    composables = listOf(
        Node.Composable(
            id = "pf-screen", width = 390, height = 844,
            children = listOf(
                Node.Scaffold(
                    id = "pf-scaffold",
                    topBar = Node.Slot(
                        "pf-scaffold-topBar", "topBar",
                        children = listOf(Node.TopAppBar("pf-bar", title = Node.Text("pf-bar-t", "Profile"))),
                    ),
                    bottomBar = Node.Slot("pf-scaffold-bottomBar", "bottomBar"),
                    fab = Node.Slot("pf-scaffold-fab", "fab"),
                    children = listOf(
                        Node.Column(
                            id = "pf-col",
                            modifier = listOf(FillMaxSize, Padding(20)),
                            horizontalAlignment = HAlignment.Center,
                            spacing = 12,
                            children = listOf(
                                Node.Box(
                                    "pf-avatar",
                                    modifier = listOf(Size(96, 96), Background(primary, corner = 50, cornerUnit = CornerUnit.Percent)),
                                    contentAlignment = composer.model.BoxAlignment.Center,
                                    children = listOf(Node.Text("pf-avatar-t", "JD", fontSize = 30, fontWeight = TextWeight.Bold, color = onPrimary)),
                                ),
                                Node.Text("pf-name", "John Doe", fontSize = 22, fontWeight = TextWeight.Bold),
                                Node.Text("pf-handle", "@johndoe · Product designer", color = 0xFF8A90A0),
                                Node.Row(
                                    "pf-stats", spacing = 24,
                                    children = listOf(
                                        statColumn("pf-s1", "128", "Designs"),
                                        statColumn("pf-s2", "2.4k", "Followers"),
                                        statColumn("pf-s3", "180", "Following"),
                                    ),
                                ),
                                Node.Row(
                                    "pf-actions", spacing = 10, modifier = listOf(FillMaxWidth),
                                    children = listOf(
                                        Node.Button("pf-follow", modifier = listOf(Weight(1f)), children = listOf(Node.Text("pf-follow-t", "Follow"))),
                                        Node.Button("pf-msg", variant = ButtonVariant.Outlined, modifier = listOf(Weight(1f)), children = listOf(Node.Text("pf-msg-t", "Message"))),
                                    ),
                                ),
                                Node.Divider("pf-div"),
                                Node.Text("pf-about-h", "About", fontSize = 15, fontWeight = TextWeight.Bold, modifier = listOf(FillMaxWidth)),
                                Node.Text(
                                    "pf-about",
                                    "Designing delightful interfaces and turning them into real Compose code.",
                                    color = 0xFF8A90A0, modifier = listOf(FillMaxWidth),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    ),
)

private fun statColumn(id: String, number: String, label: String): Node = Node.Column(
    id = id, spacing = 2, horizontalAlignment = HAlignment.Center,
    children = listOf(
        Node.Text("$id-n", number, fontSize = 18, fontWeight = TextWeight.Bold),
        Node.Text("$id-l", label, fontSize = 12, color = 0xFF8A90A0),
    ),
)

private fun dashboardTemplate(): Node = Node.Artboard(
    id = "db-art",
    layerNames = mapOf("db-screen" to "Dashboard"),
    composables = listOf(
        Node.Composable(
            id = "db-screen", width = 390, height = 844,
            children = listOf(
                Node.Column(
                    id = "db-col",
                    modifier = listOf(FillMaxSize, Background(background), Padding(20)),
                    spacing = 14,
                    children = listOf(
                        Node.Text("db-hello", "Good morning", fontSize = 24, fontWeight = TextWeight.Bold),
                        Node.Text("db-sub", "Here's what's happening today", color = 0xFF8A90A0),
                        Node.Row(
                            "db-cards", spacing = 12, modifier = listOf(FillMaxWidth),
                            children = listOf(
                                statCard("db-c1", "Revenue", "$12.4k", 0xFF5563E8),
                                statCard("db-c2", "Sessions", "8,921", 0xFF9C27B0),
                            ),
                        ),
                        Node.Card(
                            "db-settings",
                            modifier = listOf(FillMaxWidth, DropShadow(radius = 12, color = 0x22000000, offsetY = 4, corner = 12)),
                            children = listOf(
                                Node.Column(
                                    "db-set-col", modifier = listOf(Padding(16), FillMaxWidth), spacing = 10,
                                    children = listOf(
                                        Node.Text("db-set-h", "Quick settings", fontSize = 15, fontWeight = TextWeight.Bold),
                                        settingRow("db-r1", "Notifications", true),
                                        settingRow("db-r2", "Weekly report", true),
                                        settingRow("db-r3", "Public profile", false),
                                    ),
                                ),
                            ),
                        ),
                        Node.LinearProgress("db-progress", modifier = listOf(FillMaxWidth)),
                        Node.Text("db-progress-t", "Monthly goal: 68%", fontSize = 12, color = 0xFF8A90A0),
                    ),
                ),
            ),
        ),
    ),
)

private fun statCard(id: String, label: String, value: String, tint: Long): Node = Node.Box(
    id = id,
    modifier = listOf(
        Weight(1f),
        Background(tint, corner = 14, colors = listOf(tint, 0xFF1C212A), direction = GradientDirection.Diagonal),
        Padding(14),
    ),
    children = listOf(
        Node.Column(
            "$id-col", spacing = 4,
            children = listOf(
                Node.Text("$id-l", label, fontSize = 12, color = 0xCCFFFFFF),
                Node.Text("$id-v", value, fontSize = 22, fontWeight = TextWeight.Bold, color = 0xFFFFFFFF),
            ),
        ),
    ),
)

private fun settingRow(id: String, label: String, on: Boolean): Node = Node.Row(
    id = id, modifier = listOf(FillMaxWidth), spacing = 8,
    verticalAlignment = composer.model.VAlignment.Center,
    children = listOf(
        Node.Text("$id-t", label, modifier = listOf(Weight(1f))),
        Node.Switch("$id-s", checked = on),
    ),
)
