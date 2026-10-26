package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Fuzion Player", appName)
  }

  @Test
  fun `verify device default language and translations resolution`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val deviceLang = com.example.ui.LanguageManager.getDeviceDefaultLanguage(context)
    org.junit.Assert.assertTrue(deviceLang.isNotBlank())

    val resolvedJapanese = com.example.ui.Translations.get("Japanese", "favorite_songs")
    assertEquals("お気に入りの曲", resolvedJapanese)

    val resolvedSystem = com.example.ui.Translations.get("System", "favorite_songs")
    org.junit.Assert.assertTrue(resolvedSystem.isNotBlank())
  }

  @Test
  fun `verify main activity launches successfully without crashing`() {
    val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
    org.junit.Assert.assertNotNull(controller.get())
  }

  @Test
  fun `verify japanese mojibake repair restores kanji and hiragana`() {
    val scrambled = "æ»ä½“ã ®ã‚ˆã †ã §è„³ã ¯Happy Happy Happy Dance"
    val repaired = com.example.data.AudioLyricsExtractor.fixMojibakeIfPresent(scrambled)
    assertEquals("死体のようで脳はHappy Happy Happy Dance", repaired)
  }

  @Test
  fun `verify artists with tilde symbol are split into separate artist profiles`() {
    val raw = "Joji~GIVĒON"
    val artists = com.example.util.ArtistParser.splitArtists(raw)
    assertEquals(listOf("Joji", "GIVĒON"), artists)

    val track = com.example.model.Track(
      id = 999L,
      title = "Piece of You",
      artist = raw,
      album = "Smithereens",
      path = "/music/piece_of_you.mp3",
      durationMs = 135000L
    )
    val profiles = com.example.data.ArtistInfoFetcher.getCachedArtistsForTrack(track)
    assertEquals(2, profiles.size)
    assertEquals("Joji", profiles[0].name)
    assertEquals("GIVĒON", profiles[1].name)
  }
}
