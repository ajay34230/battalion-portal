package com.vision.assistant

import com.vision.assistant.memory.EmbeddingCodec
import org.junit.Assert.*
import org.junit.Test

class EmbeddingCodecTest {
    @Test fun bytesRoundTrip() { val v = floatArrayOf(0.5f, -1.25f, 3f); assertArrayEquals(v, EmbeddingCodec.fromBytes(EmbeddingCodec.toBytes(v)), 0f) }
    @Test fun rejectsBadFiles() { assertNull(EmbeddingCodec.fromBytes(ByteArray(0))); assertNull(EmbeddingCodec.fromBytes(ByteArray(7))); assertNull(EmbeddingCodec.fromBytes(ByteArray(100_000))) }
    @Test fun keyIsStableAndIgnoresOuterSpaces() {
        assertEquals(EmbeddingCodec.key("hello"), EmbeddingCodec.key("  hello ")); assertNotEquals(EmbeddingCodec.key("hello"), EmbeddingCodec.key("Hello"))
        assertEquals(32, EmbeddingCodec.key("x").length)
    }
}
