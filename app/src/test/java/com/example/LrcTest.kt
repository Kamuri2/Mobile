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
}
