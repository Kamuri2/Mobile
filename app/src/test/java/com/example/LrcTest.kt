package com.example

import com.example.data.LrcParser
import org.junit.Test
import org.junit.Assert.*

class LrcTest {
    @Test
    fun testLrcParser() {
        val lyrics = """
[00:12.34] Hello World
<01:23.45> Something else
        """.trimIndent()
        
        val parsed = LrcParser.parse(lyrics)
        println("PARSED SIZE: " + parsed.size)
        for (line in parsed) {
            println(line)
        }
        assertTrue(parsed.isNotEmpty())
    }

    @Test
    fun testCleanExtractedLyricsAndMusicoletFormat() {
        val rawMusicoletWithHeader = "eng||[01:07.80]A groan of tedium escapes me\n[01:10.50]Another line here"
        val cleaned = com.example.data.AudioLyricsExtractor.cleanExtractedLyrics(rawMusicoletWithHeader)
        assertEquals("[01:07.80]A groan of tedium escapes me\n[01:10.50]Another line here", cleaned)

        val parsed = com.example.model.LyricsParser.parseLrc(cleaned)
        assertEquals(2, parsed.size)
        assertEquals("A groan of tedium escapes me", parsed[0].text)
        assertEquals(67800L, parsed[0].timestampMs)
    }

    @Test
    fun testResidualMetadataFilteringInLyricsParser() {
        val lrcWithMetadataResidue = """
            [ar:Coldplay]
            [ti:Yellow]
            [al:Parachutes]
            [tool:MiniLyrics]
            [offset:0]
            [00:15.00]Look at the stars
            [00:20.50]Look how they shine for you
        """.trimIndent()

        val parsed = com.example.model.LyricsParser.parseLrc(lrcWithMetadataResidue)
        assertEquals(2, parsed.size)
        assertEquals("Look at the stars", parsed[0].text)
        assertEquals(15000L, parsed[0].timestampMs)
        assertEquals("Look how they shine for you", parsed[1].text)
        assertEquals(20500L, parsed[1].timestampMs)
    }

    @Test
    fun testCorruptedLyricsDetection() {
        // SoundCheck normalization hex residues
        assertTrue(com.example.data.AudioLyricsExtractor.isCorruptedLyrics("00000512 00000490 000021A4 00001FB0 00010992"))
        // Gapless playback residues
        assertTrue(com.example.data.AudioLyricsExtractor.isCorruptedLyrics("00000000 00000210 00000A00 0000000000B24760"))
        // Ripper comments
        assertTrue(com.example.data.AudioLyricsExtractor.isCorruptedLyrics("Exact Audio Copy v1.0b3"))
        // Pure metadata headers
        assertTrue(com.example.data.AudioLyricsExtractor.isCorruptedLyrics("[ar:Artist]\n[ti:Title]\n[offset:0]"))
        // Genuine lyrics
        assertFalse(com.example.data.AudioLyricsExtractor.isCorruptedLyrics("[00:12.00]Real song lyrics here\n[00:15.00]Second line"))
    }

    @Test
    fun testJAudioTaggerFrameDumpExtraction() {
        val frameDump = "Frame: USLT: Description=\"\"; Language=\"eng\"; Lyrics=\"[00:10.00]Clean lyric inside\";"
        val cleaned = com.example.data.AudioLyricsExtractor.cleanExtractedLyrics(frameDump)
        assertEquals("[00:10.00]Clean lyric inside", cleaned)
    }
}
