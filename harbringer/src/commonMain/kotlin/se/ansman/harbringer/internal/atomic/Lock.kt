package se.ansman.harbringer.internal.atomic

internal expect class Lock

internal expect fun newLock(): Lock
internal expect fun Lock.lock()
internal expect fun Lock.unlock()
internal expect fun Lock.tryLock(): Boolean
internal inline fun <R> Lock.withLock(block: () -> R): R {
    lock()
    return try {
        block()
    } finally {
        unlock()
    }
}
internal inline fun Lock.tryWithLock(block: () -> Unit) {
    if (tryLock()) {
        try {
            block()
        } finally {
            unlock()
        }
    }
}