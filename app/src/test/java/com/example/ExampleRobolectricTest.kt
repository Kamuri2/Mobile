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
}
