package se.ansman.harbringer

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isZero
import assertk.assertions.prop
import okio.Buffer
import okio.Sink
import okio.Timeout
import kotlin.test.Test
import kotlin.test.fail

class DiscardBodyScrubberTest {
    private val scrubber = Scrubber.discardBody()
    private val sink = TestSink()

    @Test
    fun `discardBody discards the body`() {
        val input = scrubber(sink)
        input.write(Buffer().apply { writeUtf8("Hello") })
        assertThat(sink).prop(TestSink::closeCount).isZero()
        assertThat(sink).prop(TestSink::flushCount).isZero()
    }

    @Test
    fun `flush flushes the delegate`() {
        val input = scrubber(sink)
        input.flush()
        assertThat(sink).prop(TestSink::closeCount).isZero()
        assertThat(sink).prop(TestSink::flushCount).isEqualTo(1)
    }

    @Test
    fun `close closes the delegate`() {
        val input = scrubber(sink)
        input.close()
        assertThat(sink).prop(TestSink::closeCount).isEqualTo(1)
        assertThat(sink).prop(TestSink::flushCount).isZero()
    }

    @Test
    fun `the delegates timeout is returned`() {
        val input = scrubber(sink)
        assertThat(input).prop(Sink::timeout).isSameInstanceAs(sink.timeout())
    }
    
    private fun Sink.write(buffer: Buffer) {
        write(buffer, buffer.size)
        assertThat(buffer.size).isZero()
    }
    
    private class TestSink : Sink {
        val timeout = Timeout()
        var flushCount = 0
        var closeCount = 0
        override fun write(source: Buffer, byteCount: Long) {
            fail("Should not be called")
        }

        override fun flush() {
            ++flushCount
        }

        override fun timeout(): Timeout = timeout

        override fun close() {
            ++closeCount
        }
    }
}