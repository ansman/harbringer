package se.ansman.harbringer.internal.atomic

internal expect class Lock

internal expect inline fun <R> Lock.withLock(block: () -> R): R
internal expect fun newLock(): Lock