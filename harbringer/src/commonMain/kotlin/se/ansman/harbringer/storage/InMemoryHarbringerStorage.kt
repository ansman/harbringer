package se.ansman.harbringer.storage

import okio.*
import se.ansman.harbringer.Harbringer
import se.ansman.harbringer.internal.SortedMap
import se.ansman.harbringer.internal.atomic.newLock
import se.ansman.harbringer.internal.atomic.withLock

/**
 * An in-memory implementation of [Harbringer] that stores entries in a map.
 *
 * This is useful for testing, but should be used with care since it does not persist data between sessions.
 *
 * The entries will be compressed but still stored in memory, so it is not suitable for large entries.
 */
class InMemoryHarbringerStorage : HarbringerStorage {
    private val storedEntries = SortedMap<String, StoredEntry>()
    private val pendingEntries = mutableMapOf<String, PendingEntry>()
    private val lock = newLock()

    override var bytesStored: Long = 0
        private set

    override var entriesStored: Int = 0
        private set

    override fun getIds(): Set<String> = lock.withLock {
        storedEntries.values.mapTo(LinkedHashSet(storedEntries.size)) { it.id }
    }

    override fun getEntryMetadata(id: String): HarbringerStorage.StoredEntry? = lock.withLock {
        storedEntries[id]?.storedEntry
    }

    override fun getOldestEntryMetadata(): HarbringerStorage.StoredEntry? = lock.withLock {
        storedEntries.values.firstOrNull()?.storedEntry
    }

    override fun getEntry(id: String): Harbringer.Entry? = lock.withLock {
        storedEntries[id]?.entry
    }

    override fun store(id: String): HarbringerStorage.PendingEntry = lock.withLock {
        check(id !in pendingEntries.keys) {
            "There is already a pending entry with the ID $id"
        }
        check(id !in storedEntries.keys) {
            "There is already a stored entry with the ID $id"
        }

        return PendingEntry(id).also { pendingEntries[id] = it }
    }

    override fun readRequestBody(id: String): Source? =
        (lock.withLock { storedEntries[id]?.requestBody }?.let(Buffer()::write) as Source?)?.gzip()

    override fun readResponseBody(id: String): Source? =
        (lock.withLock { storedEntries[id]?.responseBody }?.let(Buffer()::write) as Source?)?.gzip()


    override fun deleteEntry(id: String): HarbringerStorage.StoredEntry? = lock.withLock {
        doDeleteEntry(id)
    }

    override fun deleteOldestEntry(): HarbringerStorage.StoredEntry? = lock.withLock {
        doDeleteEntry(storedEntries.values.firstOrNull()?.storedEntry?.id ?: return null)
    }

    private fun doDeleteEntry(id: String): HarbringerStorage.StoredEntry? {
        pendingEntries.remove(id)?.discard()
        return storedEntries.remove(id)
            ?.storedEntry
            ?.also {
                bytesStored -= it.size
                --entriesStored
            }
    }

    private data class StoredEntry(
        val entry: Harbringer.Entry,
        val requestBody: ByteString,
        val responseBody: ByteString
    ) : Comparable<StoredEntry> {
        val id get() = entry.id
        val storedEntry = HarbringerStorage.StoredEntry(
            id = entry.id,
            size = requestBody.size + responseBody.size.toLong(),
            startedAt = entry.startedAt,
        )

        override fun compareTo(other: StoredEntry): Int = storedEntry.compareTo(other.storedEntry)
    }

    private inner class PendingEntry(override val id: String) : HarbringerStorage.PendingEntry {
        private var isClosed = false
        private val requestBuffer: Buffer = Buffer()
        private val responseBuffer: Buffer = Buffer()

        override val requestBody: Sink = CloseableSink(requestBuffer).gzip()
        override val responseBody: Sink = CloseableSink(responseBuffer).gzip()

        override fun write(entry: Harbringer.Entry) {
            check(id == entry.id) {
                "The ID of the entry does not match the ID of the pending entry"
            }
            check(close()) {
                "This entry is already closed, you may not call write twice or after calling discard"
            }
            val requestBody = requestBuffer.readByteString()
            val responseBody = responseBuffer.readByteString()
            lock.withLock {
                storedEntries[id] = StoredEntry(
                    entry = entry,
                    requestBody = requestBody,
                    responseBody = responseBody
                )
                pendingEntries.remove(id)
            }
        }

        override fun discard() {
            deleteEntry(id)
        }

        private fun close(): Boolean {
            if (isClosed) {
                return false
            }
            isClosed = true
            requestBody.close()
            responseBody.close()
            return true
        }
    }
}

private class CloseableSink(
    private val sink: Sink,
) : Sink {
    private var isClosed = false
    override fun write(source: Buffer, byteCount: Long) {
        check(!isClosed) { "closed" }
        sink.write(source, byteCount)
    }

    override fun flush() {
        check(!isClosed) { "closed" }
        sink.flush()
    }

    override fun timeout(): Timeout = sink.timeout()

    override fun close() {
        isClosed = true
        sink.close()
    }

}