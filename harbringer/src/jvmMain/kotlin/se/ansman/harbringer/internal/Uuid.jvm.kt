package se.ansman.harbringer.internal

import java.util.*

internal actual fun randomUuid(): String = UUID.randomUUID().toString()