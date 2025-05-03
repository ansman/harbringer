package se.ansman.harbringer.internal

internal fun <T, R> List<T>.lazyMap(mapper: (T) -> R): List<R> = object : List<R> {
    override val size: Int get() = this@lazyMap.size
    override fun isEmpty(): Boolean = this@lazyMap.isEmpty()

    override fun contains(element: R): Boolean = throw UnsupportedOperationException("contains() is not supported")

    override fun iterator(): Iterator<R> = listIterator()

    override fun containsAll(elements: Collection<R>): Boolean =
        throw UnsupportedOperationException("containsAll() is not supported")

    override fun get(index: Int): R = mapper(this@lazyMap[index])

    override fun indexOf(element: R): Int {
        throw UnsupportedOperationException("indexOf() is not supported")
    }

    override fun lastIndexOf(element: R): Int {
        throw UnsupportedOperationException("lastIndexOf() is not supported")
    }

    override fun listIterator(): ListIterator<R> = listIterator(0)

    override fun listIterator(index: Int): ListIterator<R> = object : ListIterator<R> {
        private val delegate = this@lazyMap.listIterator(index)
        override fun next(): R = mapper(delegate.next())
        override fun hasNext(): Boolean = delegate.hasNext()
        override fun hasPrevious(): Boolean = delegate.hasPrevious()
        override fun previous(): R = mapper(delegate.previous())
        override fun nextIndex(): Int = delegate.nextIndex()
        override fun previousIndex(): Int = delegate.previousIndex()
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<R> =
        this@lazyMap.subList(fromIndex, toIndex).lazyMap(mapper)
}