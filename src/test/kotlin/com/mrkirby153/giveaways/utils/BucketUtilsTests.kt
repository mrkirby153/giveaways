package com.mrkirby153.giveaways.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BucketUtilsTests {

    @Test
    fun testEmptyBucket() {
        val list = emptyList<String>()
        val bucketed = makeBucket(list, 10)
        assertEquals(0, bucketed.size)
    }

    @Test
    fun testFullBuckets() {
        val items = 1..10
        val bucketed = makeBucket(items, 5)

        assertEquals(2, bucketed.size)
        assertTrue(bucketed.all { it.size == 5 })
    }

    @Test
    fun testPartialBucket() {
        val items = 1..10
        val bucketed = makeBucket(items, 3)
        assertEquals(4, bucketed.size)
        assertTrue(bucketed[0..2].all { it.size == 3 })
        assertEquals(1, bucketed[3].size)
    }
}