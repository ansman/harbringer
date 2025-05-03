package se.ansman.harbringer.internal.atomic

internal actual typealias Lock = Any

internal actual inline fun <R> Lock.withLock(block: () -> R): R = synchronized(this) { block() }

internal actual fun newLock(): Lock = Any()