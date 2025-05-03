package se.ansman.harbringer.storage

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import okio.*
import se.ansman.harbringer.Harbringer
import se.ansman.harbringer.internal.SortedMap
import se.ansman.harbringer.internal.atomic.newLock
import se.ansman.harbringer.internal.atomic.withLock
import se.ansman.harbringer.internal.json.FileSystemDatabase
import se.ansman.harbringer.internal.json.toDatabaseEntry
import se.ansman.harbringer.internal.json.toRequestLoggerEntry
import se.ansman.harbringer.storage.HarbringerStorage.StoredEntry

/**
 * An [Harbringer] that stores entries in a [FileSystem].
 */
@OptIn(ExperimentalSerializationApi::class)
class FileSystemHarbringerStorage internal constructor(
    root: Path,
    private val fileSystem: FileSystem,
    json: Json,
    private val rethrowErrors: Boolean = false,
) : HarbringerStorage {
    private val json = Json(json) {
        ignoreUnknownKeys = true
    }

    private val storedEntries = SortedMap<String, StoredEntry>()
    private val pendingEntries = HashMap<String, PendingEntry>()

    override var bytesStored: Long = 0L
        get() {
            initialize()
            return field
        }
       private set

    override var entriesStored: Int = 0
        get() {
            initialize()
            return field
        }
        private set

    @Volatile
    private var isInitialized = false
    private val lock = newLock()

    /**
     * Creates a new [FileSystemHarbringerStorage] with the given [root] directory and [fileSystem].
     *
     * The root directory needs to be owned by this storage. Any existing files in the directory can be deleted.
     *
     * @param root The root directory for the storage. This directory will be created if it does not exist.
     * @param fileSystem The file system to use. Defaults to [FileSystem.SYSTEM].
     */
    constructor(
        root: Path,
        fileSystem: FileSystem = FileSystem.SYSTEM,
    ) : this(
        root = root,
        fileSystem = fileSystem,
        json = Json
    )

    override fun getIds(): Set<String> {
        initialize()
        return lock.withLock {
            storedEntries.values.mapTo(LinkedHashSet(storedEntries.size)) { it.id }
        }
    }

    override fun getEntryMetadata(id: String): StoredEntry? {
        initialize()
        return lock.withLock { storedEntries[id] }
    }

    override fun getEntry(id: String): Harbringer.Entry? =
        try {
            fileSystem.source(pathForEntryJson(id)).gzip().buffer().use {
                json
                    .decodeFromBufferedSource(FileSystemDatabase.Entry.serializer(), it)
                    .toRequestLoggerEntry()
            }
        } catch (e: SerializationException) {
            e.rethrow()
            null
        } catch (e: IOException) {
            e.rethrow()
            null
        }

    override fun getOldestEntryMetadata(): StoredEntry? {
        initialize()
        return lock.withLock { storedEntries.values.firstOrNull() }
    }

    override fun store(id: String): HarbringerStorage.PendingEntry {
        initialize()
        return lock.withLock {
            check(id !in storedEntries.keys) {
                "There is already an Entry with ID $id"
            }
            check(id !in pendingEntries.keys) {
                "There is already an Entry with ID $id"
            }
            PendingEntry(id).also { pendingEntries[id] = it }
        }
    }

    override fun readRequestBody(id: String): Source? {
        return readBody(id, pathForEntryRequestBody(id))
    }

    override fun readResponseBody(id: String): Source? = readBody(id, pathForEntryResponseBody(id))

    private fun readBody(id: String, path: Path): Source? {
        initialize()
        lock.withLock {
            // This is needed to avoid reading a partially written file
            if (id !in storedEntries.keys) {
                return null
            }
        }
        return try {
            fileSystem.source(path).gzip()
        } catch (_: FileNotFoundException) {
            null
        } catch (e: IOException) {
            e.rethrow()
            null
        }
    }

    override fun deleteEntry(id: String): StoredEntry? {
        initialize()
        val entry = lock.withLock {
            doDeleteEntry(id)
        }
        deleteEntryData(id)
        return entry
    }

    override fun deleteOldestEntry(): StoredEntry? {
        initialize()
        val entry = lock.withLock {
            val entry = storedEntries.values.firstOrNull()
                ?: return null
            doDeleteEntry(entry.id)
                ?: return null
        }
        deleteEntryData(entry.id)
        return entry
    }

    private fun doDeleteEntry(id: String): StoredEntry? {
        pendingEntries.remove(id)?.discard()
        val entry = storedEntries.remove(id)
        if (entry != null) {
            bytesStored -= entry.size
            --entriesStored
        }
        return entry
    }

    private fun deleteEntryData(id: String) {
        fileSystem.deleteRecursivelyQuietly(directoryForEntry(id))
    }

    private fun FileSystem.deleteRecursivelyQuietly(path: Path) {
        try {
            deleteRecursively(path)
        } catch (e: IOException) {
            e.rethrow()
        }
    }

    private fun initialize() {
        if (isInitialized) {
            return
        }
        lock.withLock {
            if (isInitialized) {
                return
            }
            try {
                fileSystem.list(entriesDir)
                    .asSequence()
                    .mapNotNull { path ->
                        val storedEntry = try {
                            fileSystem.source(path / ENTRY_JSON).gzip().buffer().use {
                                json
                                    .decodeFromBufferedSource(FileSystemDatabase.SimpleEntry.serializer(), it)
                                    .run {
                                        StoredEntry(
                                            id = id,
                                            size = fileSystem.totalSize(directoryForEntry(id)),
                                            startedAt = startedAt,
                                        )
                                    }
                            }
                        } catch (_: FileNotFoundException) {
                            null
                        } catch (e: SerializationException) {
                            e.rethrow()
                            null
                        } catch (e: IOException) {
                            e.rethrow()
                            null
                        }
                        if (storedEntry == null) {
                            fileSystem.deleteRecursivelyQuietly(path)
                        }
                        storedEntry
                    }
                    .forEach { storedEntries[it.id] = it }
            } catch (_: FileNotFoundException) {
                // Ignored
            } catch (e: IOException) {
                e.rethrow()
            }
            isInitialized = true
        }
    }

    private fun createEntryDirectory(id: String) {
        fileSystem.createDirectories(directoryForEntry(id))
    }

    private val entriesDir = root / "entries"
    private fun directoryForEntry(id: String): Path = entriesDir / id
    private fun pathForEntryJson(id: String): Path = entriesDir / id / ENTRY_JSON
    private fun pathForEntryRequestBody(id: String): Path = entriesDir / id / "request.gz"
    private fun pathForEntryResponseBody(id: String): Path = entriesDir / id / "response.gz"

    private fun Throwable.rethrow() {
        if (rethrowErrors) {
            throw this
        }
    }

    companion object {
        private const val ENTRY_JSON = "entry.json.gz"
    }

    private inner class PendingEntry(override val id: String) : HarbringerStorage.PendingEntry {
        @Volatile
        private var isClosed = false
        override val responseBody: Sink
        override val requestBody: Sink

        init {
            var responseBody: Sink = blackholeSink()
            var requestBody: Sink = blackholeSink()
            try {
                createEntryDirectory(id)
                requestBody = fileSystem.sink(pathForEntryRequestBody(id)).gzip()
                responseBody = fileSystem.sink(pathForEntryResponseBody(id)).gzip()
            } catch (e: IOException) {
                e.rethrow()
            }
            this.requestBody = requestBody
            this.responseBody = responseBody
        }

        fun close() {
            isClosed = true
            try {
                responseBody.close()
                requestBody.close()
            } catch (e: IOException) {
                e.rethrow()
            }
        }

        override fun write(entry: Harbringer.Entry) {
            check(id == entry.id) {
                "The ID of the entry does not match the ID of the pending entry"
            }
            check(!isClosed) {
                "This entry is already closed, you may not call write twice or after calling discard"
            }
            val storedEntry = try {
                close()
                fileSystem.sink(pathForEntryJson(id)).gzip().buffer().use {
                    json.encodeToBufferedSink(
                        serializer = FileSystemDatabase.Entry.serializer(),
                        value = entry.toDatabaseEntry(),
                        sink = it,
                    )
                }
                StoredEntry(
                    id = id,
                    size = fileSystem.totalSize(directoryForEntry(id)),
                    startedAt = entry.startedAt
                )
            } catch (e: IOException) {
                e.rethrow()
                deleteEntry(id)
                return
            }
            lock.withLock {
                entriesStored += 1
                bytesStored += storedEntry.size
                storedEntries[id] = storedEntry
            }
        }

        override fun discard() {
            close()
            deleteEntry(id)
        }
    }
}

private fun FileSystem.totalSize(path: Path): Long =
    listRecursively(path)
        .map { metadata(it) }
        .sumOf { it.size ?: 0L }