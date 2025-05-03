package se.ansman.harbringer.internal

internal class SortedMap<K, V : Comparable<V>> {
    private val delegate = HashMap<K, V>()
    private val _values = ArrayList<V>()

    val size: Int
        get() = delegate.size

    val entries: Set<Map.Entry<K, V>>
        get() = delegate.entries

    val keys: Set<K>
        get() = delegate.keys

    val values: List<V>
        get() = _values

    fun isEmpty() = size == 0

    fun clear() {
        delegate.clear()
        _values.clear()
    }

    operator fun get(key: K): V? = delegate[key]

    operator fun set(key: K, value: V) {
        val existing = delegate.put(key, value)
        if (existing != null) {
            _values.removeAt(_values.binarySearch(existing))
        }

        val index = _values.binarySearch(value)
        val insertionIndex = if (index >= 0) index else -(index + 1)
        _values.add(insertionIndex, value)
    }

    fun remove(key: K): V? {
        val existing = delegate.remove(key)
        if (existing != null) {
            _values.removeAt(_values.binarySearch(existing))
        }
        return existing
    }
}