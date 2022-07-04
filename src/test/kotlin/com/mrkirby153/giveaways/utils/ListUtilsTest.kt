package com.mrkirby153.giveaways.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ListUtilsTest {

    @Test
    fun testSlice() {
        val items = (0..10).toList()
        val slice = items[0..3]
        assertEquals(3, slice.size)
        assertEquals(listOf(0, 1, 2), slice)
    }
}