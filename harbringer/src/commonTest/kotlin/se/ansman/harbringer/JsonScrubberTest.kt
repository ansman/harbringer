package se.ansman.harbringer

import assertk.assertThat
import assertk.assertions.isEqualTo
import okio.Buffer
import okio.Sink
import okio.buffer
import kotlin.test.Test

class JsonScrubberTest {
    private val scrubber = Scrubber.json(
        patterns = listOf("$.password", "$.secret", "$.username"),
    )

    @Test
    fun `writes original json if nothing was replaced`() {
        assertThat(scrubber.scrub("{\"weird\":\n\"formatting\"\n}"))
            .isEqualTo("{\"weird\":\n\"formatting\"\n}")
    }

    @Test
    fun `can replace strings`() {
        assertThat(scrubber.scrub("""{"username":"example","password":"hunter2","rememberMe":true}"""))
            .isEqualTo("""{"username":"******","password":"******","rememberMe":true}""")
    }

    @Test
    fun `can replace arrays`() {
        assertThat(scrubber.scrub("""{"secret":[1,2,3]}"""))
            .isEqualTo("""{"secret":[]}""")
    }

    @Test
    fun `can replace objects`() {
        assertThat(scrubber.scrub("""{"secret":{"username":"example","password":"hunter2","rememberMe":true}}"""))
            .isEqualTo("""{"secret":{}}""")
    }

    @Test
    fun `can replace booleans`() {
        assertThat(scrubber.scrub("""{"secret":false}"""))
            .isEqualTo("""{"secret":"******"}""")
    }

    @Test
    fun `can replace number`() {
        assertThat(scrubber.scrub("""{"secret":4711}"""))
            .isEqualTo("""{"secret":"******"}""")
    }

    @Test
    fun `doesn't replace non affected things`() {
        assertThat(scrubber.scrub("""{"public":"stuff"}"""))
            .isEqualTo("""{"public":"stuff"}""")
    }

    @Test
    fun `supports wildcards`() {
        val scrubber = Scrubber.Companion.json(
            patterns = listOf("$.secret.*"),
        )
        assertThat(scrubber.scrub("""{"secret":{"username":"example","password":"hunter2","rememberMe":true}}"""))
            .isEqualTo("""{"secret":{"username":"******","password":"******","rememberMe":"******"}}""")
    }

    @Test
    fun `can scrub arrays`() {
        val scrubber = Scrubber.Companion.json(
            patterns = listOf("$.secret[]", "$.secret2[].foo"),
        )
        assertThat(scrubber.scrub("""{"secret":[1,2,3],"secret2":[{"foo":"bar"},{"bar":"baz"}]}"""))
            .isEqualTo("""{"secret":["******","******","******"],"secret2":[{"foo":"******"},{"bar":"baz"}]}""")
    }

    private fun ((Sink) -> Sink).scrub(value: String): String? {
        val output = Buffer()
        this(output).buffer().writeUtf8(value).close()
        return output.readUtf8()
    }
}