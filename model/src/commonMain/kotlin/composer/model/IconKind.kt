package composer.model

import kotlinx.serialization.Serializable

/**
 * A curated set of Material icons. Names match `Icons.Default.<name>` exactly so
 * codegen can emit `Icons.Default.${name}` and import `…icons.filled.${name}`.
 */
@Serializable
enum class IconKind {
    Menu, Search, Home, Settings, Favorite, Star, Add, Close, Check,
    Delete, Edit, Share, Notifications, Person, Info, MoreVert, Email, Lock,
}
