package com.example
 
import com.example.data.AudioLyricsExtractor
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OpusLyricsTest {

    private fun writeInt32LE(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }

    private fun createOggPage(
        headerType: Int,
        granulePos: Long,
        serialNo: Int,
        pageSeqNo: Int,
        packetSegments: List<ByteArray>
    ): ByteArray {
        val out = ByteArrayOutputStream()
        // OggS magic
        out.write("OggS".toByteArray(Charsets.US_ASCII))
        out.write(0) // version
        out.write(headerType)
        val gBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(granulePos).array()
        out.write(gBytes)
        out.write(writeInt32LE(serialNo))
        out.write(writeInt32LE(pageSeqNo))
        out.write(writeInt32LE(0)) // CRC checksum (0 for test)

        // Segment table
        val segLens = mutableListOf<Int>()
        for (seg in packetSegments) {
            segLens.add(seg.size)
        }
        out.write(segLens.size)
        for (len in segLens) {
            out.write(len)
        }
        for (seg in packetSegments) {
            out.write(seg)
        }
        return out.toByteArray()
    }

    @Test
    fun testOpusSinglePageLyricsExtraction() {
        // Page 0: OpusHead
        val opusHead = "OpusHead".toByteArray(Charsets.US_ASCII) + ByteArray(11) { 0 }
        val page0 = createOggPage(2, 0L, 1001, 0, listOf(opusHead))

        // Page 1: OpusTags with UNSYNCED LYRICS
        val vendor = "Lavf60.16.100".toByteArray(Charsets.UTF_8)
        val comment1 = "TITLE=Test Song".toByteArray(Charsets.UTF_8)
        val comment2 = "ARTIST=Test Artist".toByteArray(Charsets.UTF_8)
        val lyricsText = "[00:12.50]First line of opus song\n[00:18.00]Second line of opus song"
        val comment3 = ("UNSYNCED LYRICS=" + lyricsText).toByteArray(Charsets.UTF_8)

        val packet2Stream = ByteArrayOutputStream()
        packet2Stream.write("OpusTags".toByteArray(Charsets.US_ASCII))
        packet2Stream.write(writeInt32LE(vendor.size))
        packet2Stream.write(vendor)
        packet2Stream.write(writeInt32LE(3)) // 3 comments
        packet2Stream.write(writeInt32LE(comment1.size))
        packet2Stream.write(comment1)
        packet2Stream.write(writeInt32LE(comment2.size))
        packet2Stream.write(comment2)
        packet2Stream.write(writeInt32LE(comment3.size))
        packet2Stream.write(comment3)

        val packet2Bytes = packet2Stream.toByteArray()
        val segs = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < packet2Bytes.size) {
            val chunk = Math.min(255, packet2Bytes.size - offset)
            segs.add(packet2Bytes.copyOfRange(offset, offset + chunk))
            offset += chunk
        }

        val page1 = createOggPage(0, 0L, 1001, 1, segs)
        val fullStream = page0 + page1

        val result = AudioLyricsExtractor.parseOggOpusBytes(fullStream)
        assertNotNull(result)
        assertEquals(lyricsText, result?.lyrics)
        assertTrue(result?.isSynced == true)
    }

    @Test
    fun testOpusMultiPagePacketReassembly() {
        // Test packet spanning across 2 Ogg pages
        val opusHead = "OpusHead".toByteArray(Charsets.US_ASCII) + ByteArray(11) { 0 }
        val page0 = createOggPage(2, 0L, 2002, 0, listOf(opusHead))

        val vendor = "libopus".toByteArray(Charsets.UTF_8)
        val pic = ByteArray(3000) { 'A'.code.toByte() } // Large picture field
        val lyricsText = "[01:05.00]Multi page lyric line 1\n[01:10.00]Multi page lyric line 2"
        val commentLyrics = ("LYRICS=" + lyricsText).toByteArray(Charsets.UTF_8)
        val commentPic = "METADATA_BLOCK_PICTURE=".toByteArray(Charsets.US_ASCII) + pic

        val packetStream = ByteArrayOutputStream()
        packetStream.write("OpusTags".toByteArray(Charsets.US_ASCII))
        packetStream.write(writeInt32LE(vendor.size))
        packetStream.write(vendor)
        packetStream.write(writeInt32LE(2))
        packetStream.write(writeInt32LE(commentPic.size))
        packetStream.write(commentPic)
        packetStream.write(writeInt32LE(commentLyrics.size))
        packetStream.write(commentLyrics)

        val totalPacket = packetStream.toByteArray()
        assertTrue(totalPacket.size > 2000)

        // Split packet across Page 1 (8 segments of 255 = 2040 bytes) and Page 2 (remainder)
        val p1Len = 8 * 255
        val p1Data = totalPacket.copyOfRange(0, p1Len)
        val p2Data = totalPacket.copyOfRange(p1Len, totalPacket.size)

        val p1Segs = mutableListOf<ByteArray>()
        for (i in 0 until 8) {
            p1Segs.add(p1Data.copyOfRange(i * 255, (i + 1) * 255))
        }
        val page1 = createOggPage(0, 0L, 2002, 1, p1Segs)

        val p2Segs = mutableListOf<ByteArray>()
        var p2Off = 0
        while (p2Off < p2Data.size) {
            val chunk = Math.min(255, p2Data.size - p2Off)
            p2Segs.add(p2Data.copyOfRange(p2Off, p2Off + chunk))
            p2Off += chunk
        }
        val page2 = createOggPage(1, 0L, 2002, 2, p2Segs) // headerType=1 (continued)

        val fullMultiPage = page0 + page1 + page2

        val result = AudioLyricsExtractor.parseOggOpusBytes(fullMultiPage)
        assertNotNull(result)
        assertEquals(lyricsText, result?.lyrics)
        assertTrue(result?.isSynced == true)
    }

    @Test
    fun testOpusCleanExtractedLyricsPrefixes() {
        val rawUnsynced = "UNSYNCED LYRICS=[00:15.00]Hello World\n[00:20.00]Goodbye World"
        val cleaned = AudioLyricsExtractor.cleanExtractedLyrics(rawUnsynced)
        assertEquals("[00:15.00]Hello World\n[00:20.00]Goodbye World", cleaned)

        val rawSynced = "SYNCEDLYRICS: [01:00.00]Line 1\n[01:05.00]Line 2"
        val cleaned2 = AudioLyricsExtractor.cleanExtractedLyrics(rawSynced)
        assertEquals("[01:00.00]Line 1\n[01:05.00]Line 2", cleaned2)
    }

    @Test
    fun testBufferSearchFallbackForOpus() {
        // Even if OggS framing is corrupted, buffer search recovers OpusTags
        val vendor = "libopus".toByteArray(Charsets.UTF_8)
        val lyricsText = "[00:30.00]Fallback lyric line\n[00:35.00]Next line"
        val comment = ("LRC=" + lyricsText).toByteArray(Charsets.UTF_8)

        val packetStream = ByteArrayOutputStream()
        packetStream.write("OpusTags".toByteArray(Charsets.US_ASCII))
        packetStream.write(writeInt32LE(vendor.size))
        packetStream.write(vendor)
        packetStream.write(writeInt32LE(1))
        packetStream.write(writeInt32LE(comment.size))
        packetStream.write(comment)

        val corruptedBuffer = ByteArray(50) { 0xFF.toByte() } + packetStream.toByteArray() + ByteArray(50) { 0 }
        val result = AudioLyricsExtractor.parseOggOpusBytes(corruptedBuffer)
        assertNotNull(result)
        assertEquals(lyricsText, result?.lyrics)
    }
}
