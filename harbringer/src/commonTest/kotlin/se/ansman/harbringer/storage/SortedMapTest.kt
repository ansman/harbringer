package se.ansman.harbringer.storage

import assertk.assertThat
import assertk.assertions.*
import se.ansman.harbringer.internal.SortedMap
import kotlin.test.Test

class SortedMapTest {
    private val map = SortedMap<String, Int>()

    @Test
    fun `size returns the number of entries`() {
        assertThat(map).prop(SortedMap<*, *>::size).isEqualTo(0)
        map["one"] = 1
        assertThat(map).prop(SortedMap<*, *>::size).isEqualTo(1)
        map["two"] = 2
        assertThat(map).prop(SortedMap<*, *>::size).isEqualTo(2)
        map["three"] = 3
        assertThat(map).prop(SortedMap<*, *>::size).isEqualTo(3)
        map.remove("one")
        assertThat(map).prop(SortedMap<*, *>::size).isEqualTo(2)
        map.remove("two")
        assertThat(map).prop(SortedMap<*, *>::size).isEqualTo(1)
        map.remove("three")
        assertThat(map).prop(SortedMap<*, *>::size).isEqualTo(0)
    }

    @Test
    fun `clearing removes all entries`() {
        assertThat(map).prop(SortedMap<*, *>::size).isEqualTo(0)

        map.clear()
        assertThat(map).prop(SortedMap<*, *>::size).isEqualTo(0)

        map["one"] = 1
        map["two"] = 2
        map["three"] = 3
        assertThat(map).prop(SortedMap<*, *>::size).isEqualTo(3)

        map.clear()
        assertThat(map).prop(SortedMap<*, *>::size).isEqualTo(0)
    }

    @Test
    fun `adding an entry lets you read the entry`() {
        assertThat(map["one"]).isNull()

        map["one"] = 1
        assertThat(map["one"]).isEqualTo(1)

        map["one"] = 2
        assertThat(map["one"]).isEqualTo(2)

        map["two"] = 3
        assertThat(map["two"]).isEqualTo(3)
    }

    @Test
    fun `removing returns the removed entry or null`() {
        assertThat(map.remove("one")).isNull()

        map["one"] = 1
        assertThat(map.remove("one")).isEqualTo(1)
    }

    @Test
    fun `the values are ordered`() {
        assertThat(map).prop(SortedMap<*, *>::values).isEmpty()

        map["two"] = 2
        assertThat(map).prop(SortedMap<*, *>::values).containsExactly(2)

        map["three"] = 3
        assertThat(map).prop(SortedMap<*, *>::values).containsExactly(2, 3)

        map["one"] = 1
        assertThat(map).prop(SortedMap<*, *>::values).containsExactly(1, 2, 3)

        map["four"] = 4
        assertThat(map).prop(SortedMap<*, *>::values).containsExactly(1, 2, 3, 4)

        map["one"] = 5
        assertThat(map).prop(SortedMap<*, *>::values).containsExactly(2, 3, 4, 5)

        map["five"] = -1
        assertThat(map).prop(SortedMap<*, *>::values).containsExactly(-1, 2, 3, 4, 5)
    }

    @Test
    fun `duplicate values are supported`() {
        assertThat(map).prop(SortedMap<*, *>::keys).isEmpty()
        assertThat(map).prop(SortedMap<*, *>::values).isEmpty()
        assertThat(map).prop(SortedMap<*, *>::entries).isEmpty()
        assertThat(map).prop(SortedMap<*, *>::size).isEqualTo(0)

        map["one"] = 1
        map["two"] = 1
        map["three"] = 1
        assertThat(map).prop(SortedMap<*, *>::keys).containsExactlyInAnyOrder("one", "two", "three")
        assertThat(map).prop(SortedMap<*, *>::values).containsExactly(1, 1, 1)
        assertThat(map).prop(SortedMap<*, *>::entries).isEqualTo(mapOf("one" to 1, "two" to 1, "three" to 1).entries)
        assertThat(map).prop(SortedMap<*, *>::size).isEqualTo(3)

        map.remove("two")
        assertThat(map).prop(SortedMap<*, *>::keys).containsExactlyInAnyOrder("one", "three")
        assertThat(map).prop(SortedMap<*, *>::values).containsExactly(1, 1)
        assertThat(map).prop(SortedMap<*, *>::entries).isEqualTo(mapOf("one" to 1, "three" to 1).entries)
        assertThat(map).prop(SortedMap<*, *>::size).isEqualTo(2)

        map.remove("one")
        assertThat(map).prop(SortedMap<*, *>::keys).containsExactlyInAnyOrder("three")
        assertThat(map).prop(SortedMap<*, *>::values).containsExactly(1)
        assertThat(map).prop(SortedMap<*, *>::entries).isEqualTo(mapOf("three" to 1).entries)
        assertThat(map).prop(SortedMap<*, *>::size).isEqualTo(1)

        map.remove("three")
        assertThat(map).prop(SortedMap<*, *>::keys).isEmpty()
        assertThat(map).prop(SortedMap<*, *>::values).isEmpty()
        assertThat(map).prop(SortedMap<*, *>::entries).isEmpty()
        assertThat(map).prop(SortedMap<*, *>::size).isEqualTo(0)
    }
}