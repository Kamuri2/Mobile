package com.example.data

import android.net.Uri
import com.example.model.Track

object SampleMusicProvider {

    fun getSampleTracks(): List<Track> {
        return listOf(
            Track(
                id = 1001L,
                title = "Midnight Horizon",
                artist = "Luna Waves",
                album = "Echoes in the Dark",
                durationMs = 195000L,
                contentUri = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"),
                albumArtUri = Uri.parse("https://picsum.photos/seed/liquid1/600/600"),
                lyrics = """
                    [00:00.00] Midnight Horizon - Luna Waves
                    [00:04.50] (Instrumental Intro)
                    [00:12.00] Driving through the neon glow
                    [00:16.80] City lights begin to slow
                    [00:22.10] Whispers in the cooling breeze
                    [00:27.40] Falling leaves and memories
                    [00:32.60] Can you feel the rhythm now?
                    [00:37.80] Finding hope and wondering how
                    [00:43.00] Underneath the endless sky
                    [00:48.20] Watch the stars ignite and fly
                    [00:54.00] We will meet where night meets day
                    [00:59.50] Washing all the doubts away
                    [01:05.00] Midnight horizon calling out my name
                    [01:10.50] Nothing here will ever be the same
                    [01:16.20] (Electric Solo)
                    [01:28.00] Colors dancing in the rain
                    [01:33.40] Freedom breaks the heavy chain
                    [01:39.00] Hold my hand and breathe the sound
                    [01:44.20] Finally our peace is found
                    [01:50.00] Midnight horizon calling out my name
                    [01:56.50] Outro fade...
                """.trimIndent(),
                isSample = true
            ),
            Track(
                id = 1002L,
                title = "Neon Dreams",
                artist = "Solar Drift",
                album = "Cyberpunk Boulevard",
                durationMs = 210000L,
                contentUri = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"),
                albumArtUri = Uri.parse("https://picsum.photos/seed/liquid2/600/600"),
                lyrics = """
                    [00:00.00] Neon Dreams - Solar Drift
                    [00:06.00] Electric pulse inside my soul
                    [00:12.50] Holograms that take control
                    [00:18.00] Digital rain on glass and steel
                    [00:24.00] Tell me what is really real
                    [00:30.00] Synthesizer takes the lead
                    [00:36.50] Running at a lightning speed
                    [00:43.00] In the glow of ultraviolet light
                    [00:49.00] We conquer the electric night
                    [00:56.00] Fade into the matrix sound
                    [01:03.00] Never looking back to ground
                """.trimIndent(),
                isSample = true
            ),
            Track(
                id = 1003L,
                title = "Ocean Whispers",
                artist = "Aura Borealis",
                album = "Deep Current",
                durationMs = 180000L,
                contentUri = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"),
                albumArtUri = Uri.parse("https://picsum.photos/seed/liquid3/600/600"),
                lyrics = """
                    [00:00.00] Ocean Whispers - Aura Borealis
                    [00:05.00] Waves crash softly on the shore
                    [00:11.20] Tidelines reaching evermore
                    [00:17.80] Deep blue mystery below
                    [00:23.50] Where the coral corals glow
                    [00:30.00] Gentle currents guide the way
                    [00:36.40] Till the dawn brings in the day
                    [00:44.00] Breathe in ocean peace so deep
                    [00:50.00] Promises the waters keep
                """.trimIndent(),
                isSample = true
            )
        )
    }

    fun getLyricsForSample(title: String): String? {
        val found = getSampleTracks().find { it.title.equals(title, ignoreCase = true) }
        return found?.lyrics
    }
}
