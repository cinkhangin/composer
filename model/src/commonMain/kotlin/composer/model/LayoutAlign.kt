package composer.model

import kotlinx.serialization.Serializable

/** Layout arrangement/alignment options that map to Compose's Arrangement/Alignment. */

@Serializable
enum class VArrangement { Top, Bottom, Center, SpaceBetween, SpaceAround, SpaceEvenly }

@Serializable
enum class HArrangement { Start, End, Center, SpaceBetween, SpaceAround, SpaceEvenly }

@Serializable
enum class HAlignment { Start, Center, End }

@Serializable
enum class VAlignment { Top, Center, Bottom }

@Serializable
enum class BoxAlignment { TopStart, TopCenter, TopEnd, CenterStart, Center, CenterEnd, BottomStart, BottomCenter, BottomEnd }
