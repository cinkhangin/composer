package composer.model

import kotlinx.serialization.json.Json

/**
 * JSON (de)serialization for the design tree — the persistence format (M6).
 * Pure: depends only on the model and kotlinx.serialization.
 */
object DesignJson {
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
    }

    fun encode(root: Node): String = json.encodeToString(Node.serializer(), root)

    /** Decode a tree. Throws [kotlinx.serialization.SerializationException] on malformed input. */
    fun decode(text: String): Node = json.decodeFromString(Node.serializer(), text)
}
