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

class ReplaceBodyScrubberTest {
    private val scrubber = Scrubber.replaceBody("******")
    private val sink = TestSink()

    @Test
    fun `writing is discarded`() {
        val input = scrubber(sink)
        input.write("Secret")
        assertThat(sink).prop(TestSink::buffer).prop(Buffer::size).isZero()
        assertThat(sink).prop(TestSink::closeCount).isZero()
        assertThat(sink).prop(TestSink::flushCount).isZero()
    }

    @Test
    fun `flush flushes the delegate`() {
        val input = scrubber(sink)
        input.flush()
        assertThat(sink).prop(TestSink::buffer).prop(Buffer::size).isZero()
        assertThat(sink).prop(TestSink::closeCount).isZero()
        assertThat(sink).prop(TestSink::flushCount).isEqualTo(1)
    }

    @Test
    fun `close writes the data and closes the delegate`() {
        val input = scrubber(sink)
        input.close()
        assertThat(sink.buffer.readUtf8()).isEqualTo("******")
        assertThat(sink).prop(TestSink::closeCount).isEqualTo(1)
        assertThat(sink).prop(TestSink::flushCount).isZero()
    }

    @Test
    fun `close twice only writes the data once`() {
        val input = scrubber(sink)
        input.close()
        input.close()
        assertThat(sink.buffer.readUtf8()).isEqualTo("******")
        assertThat(sink).prop(TestSink::closeCount).isEqualTo(2)
        assertThat(sink).prop(TestSink::flushCount).isZero()
    }

    @Test
    fun `the delegates timeout is returned`() {
        val input = scrubber(sink)
        assertThat(input).prop(Sink::timeout).isSameInstanceAs(sink.timeout())
    }
    
    private fun Sink.write(string: String) {
        write(Buffer().writeUtf8(string))
    }

    private fun Sink.write(buffer: Buffer) {
        write(buffer, buffer.size)
        assertThat(buffer.size).isZero()
    }
    
    private class TestSink : Sink {
        val timeout = Timeout()
        var flushCount = 0
        var closeCount = 0
        val buffer = Buffer()

        override fun write(source: Buffer, byteCount: Long) {
            source.copyTo(buffer, 0, byteCount)
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