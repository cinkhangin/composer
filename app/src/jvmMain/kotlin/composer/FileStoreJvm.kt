package composer

/**
 * In-memory [FileStore] for the IDE designer: the editor always runs embedded
 * there, so real persistence rides the bridge (the IDE owns the files). This
 * exists to keep [Workspace] consistent, not to store anything durably.
 */
actual object FileStore {
    private val designs = LinkedHashMap<String, String>()
    private val metas = LinkedHashMap<String, FileMeta>()
    private var counter = 0

    actual fun list(): List<FileMeta> = metas.values.sortedByDescending { it.updatedAt }

    actual fun loadDesign(id: String): String? = designs[id]

    actual fun save(id: String, name: String, designJson: String): SaveResult {
        designs[id] = designJson
        val meta = FileMeta(id, name, System.currentTimeMillis().toDouble())
        metas[id] = meta
        return SaveResult.Ok(meta)
    }

    actual fun delete(id: String) {
        designs.remove(id)
        metas.remove(id)
    }

    actual fun newId(): String = "f${System.currentTimeMillis()}-${++counter}"
}
