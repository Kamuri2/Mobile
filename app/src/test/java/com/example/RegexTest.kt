package com.example

import org.junit.Test
import org.junit.Assert.*

class RegexTest {
    @Test
    fun testRegex() {
        val r = Regex("(\\[|<)\\d{1,3}:\\d{1,2}")
        assertTrue(r.containsMatchIn("[00:12.34]"))
        assertTrue(r.containsMatchIn("<00:12.34>"))
    }
}
