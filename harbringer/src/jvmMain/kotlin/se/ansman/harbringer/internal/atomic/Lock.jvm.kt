package se.ansman.harbringer.internal.atomic

import java.util.concurrent.locks.ReentrantLock

internal actual typealias Lock = ReentrantLock

internal actual fun newLock(): Lock = ReentrantLock()

internal actual fun Lock.lock() = lock()
internal actual fun Lock.unlock() = unlock()
internal actual fun Lock.tryLock(): Boolean = tryLock()