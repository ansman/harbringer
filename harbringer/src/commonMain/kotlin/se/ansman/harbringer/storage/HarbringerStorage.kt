package se.ansman.harbringer.storage

import okio.Sink
import okio.Source
import se.ansman.harbringer.Harbringer

/**
 * A storage for storing request and response entries.
 *
 * This class must be thread safe and will be accessed from multiple threads.
 *
 * Please note that *all* functions and properties can perform I/O so access should not be done on a UI thread.
 *
 * @see FileSystemHarbringerStorage
 * @see InMemoryHarbringerStorage
 */
interface HarbringerStorage {
    /**
     * The number of bytes stored in this storage. This can return -1 if this storage does not track the disk usage.
     * This value can be an approximation.
     *
     * Calling this property should be fast and cached as it will be called frequently.
     *
     * Pending entries should not be included in this value.
     */
    val bytesStored: Long

    /**
     * The number of entries stored in this storage.
     *
     * This does not include pending entries.
     * 
     * Calling this property should be fast and cached as it will be called frequently.
     */
    val entriesStored: Int

    /** Returns a set of all the IDs of the entries in the storage. */
    fun getIds(): Set<String>

    /** Returns the metadata for the entry with the given [id], or `null` if there is no entry with that ID. */
    fun getEntryMetadata(id: String): StoredEntry?

    /** Returns entry with the given [id], or `null` if there is no entry with that ID. */
    fun getEntry(id: String): Harbringer.Entry?

    /**
     * Creates a [PendingEntry] for an entry with the given [id].
     *
     * @throws IllegalStateException If there is already a stored or pending entry with the given ID.
     */
    fun store(id: String): PendingEntry

    /**
     * Returns a [Source] that reads the response body for the entry with the given [id], or `null` if there is no
     * entry with that ID or if there is no body for the entry.
     *
     * The returned source must be closed after reading from it.
     */
    fun readResponseBody(id: String): Source?

    /**
     * Returns a [Source] that reads the request body for the entry with the given [id], or `null` if there is no
     * entry with that ID or if there is no body for the entry.
     *
     * The returned source must be closed after reading from it.
     */
    fun readRequestBody(id: String): Source?

    /**
     * Deletes the entry with the given [id]. This will also delete the request and response bodies for the entry.
     *
     * Returns the metadata for the deleted entry, or `null` if there is no entry with that ID.
     */
    fun deleteEntry(id: String): StoredEntry?

    /**
     * Deletes the oldest entry.
     *
     * Returns the metadata for the deleted entry, or `null` if the storage was empty
     */
    fun deleteOldestEntry(): StoredEntry?

    /** Removes all stored entries. */
    fun clear() {
        for (id in getIds()) {
            deleteEntry(id)
        }
    }

    /** Some metadata for a stored entry. */
    data class StoredEntry(
        /**
         * The entry's ID.
         */
        val id: String,

        /**
         * The approximate size of the entry, in bytes.
         */
        val size: Long,

        /**
         * The time the request was started, in milliseconds since the epoch.
         *
         * The precision of this value is not guaranteed.
         */
        val startedAt: Long,
    ) : Comparable<StoredEntry> {
        override fun compareTo(other: StoredEntry): Int =
            when {
                startedAt != other.startedAt -> startedAt.compareTo(other.startedAt)
                id != other.id -> id.compareTo(other.id)
                else -> 0
            }
    }

    /**
     * An entry that's pending storage.
     *
     * You can write the request and response bodies to the [requestBody] and [responseBody] respectively.
     *
     * You *must* call [write] or [discard] when the entry is done.
     */
    interface PendingEntry {
        val id: String
        val requestBody: Sink
        val responseBody: Sink

        fun write(entry: Harbringer.Entry)
        fun discard()
    }
}

