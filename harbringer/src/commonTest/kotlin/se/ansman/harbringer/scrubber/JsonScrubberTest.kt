package se.ansman.harbringer.scrubber

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okio.Buffer
import okio.buffer
import org.intellij.lang.annotations.Language
import se.ansman.harbringer.Harbringer
import kotlin.test.Test

@OptIn(ExperimentalSerializationApi::class)
class JsonScrubberTest {
    private val scrubber = Scrubber.json("$.password", "$.secret", "$.username")

    @Test
    fun `writes original json if nothing was replaced`() {
        Scrubber.json()
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
    fun `supports arbitrary replacing`() {
        val scrubber = Scrubber.json(
            json = Json {
                prettyPrint = true
                prettyPrintIndent = "  "
            }
        ) { path, element ->
            if (path.startsWith("$.secret.") || ".key2" in path || "$.nested.key1" == path) {
                JsonPrimitive("******")
            } else {
                element
            }

        }
        assertThat(
            scrubber.scrub(
                """
                {
                  "secret": {
                    "username": "example",
                    "password": "hunter2",
                    "rememberMe": true
                  },
                  "key1": "value1",
                  "key2": "value2",
                  "nested": {
                    "key1": "value3",
                    "key2": "value4"
                  },
                  "array": [
                    {
                      "key1": "value5",
                      "key2": "value6"
                    }
                  ]
                }
                """.trimIndent()
            )
        ).isEqualTo(
            """
            {
              "secret": {
                "username": "******",
                "password": "******",
                "rememberMe": "******"
              },
              "key1": "value1",
              "key2": "******",
              "nested": {
                "key1": "******",
                "key2": "******"
              },
              "array": [
                {
                  "key1": "value5",
                  "key2": "******"
                }
              ]
            }
            """.trimIndent()
        )
    }

    @Test
    fun `can scrub arrays`() {
        val scrubber = Scrubber.json("$.secret[]", "$.secret2[].foo")
        assertThat(scrubber.scrub("""{"secret":[1,2,3],"secret2":[{"foo":"bar"},{"bar":"baz"}]}"""))
            .isEqualTo("""{"secret":["******","******","******"],"secret2":[{"foo":"******"},{"bar":"baz"}]}""")
    }

    private fun BodyScrubber.scrub(@Language("json") value: String): String? {
        val output = Buffer()
        val request = Harbringer.Request(
            method = "GET",
            url = "https://example.com",
            headers = Harbringer.Headers(),
            protocol = "HTTP/1.1",
        )
        scrub(request, output).buffer().writeUtf8(value).close()
        return output.readUtf8()
    }
}