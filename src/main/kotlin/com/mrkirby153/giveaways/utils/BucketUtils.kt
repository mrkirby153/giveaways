package com.mrkirby153.giveaways.utils

/**
 * Buckets the provided [items] into buckets of [size]. The last bucket may not be a full bucket
 */
fun <T> makeBucket(items: Iterable<T>, size: Long): List<List<T>> {
    val buckets = mutableListOf<List<T>>()

    val currentBucket = mutableListOf<T>()
    items.forEach {
        if (currentBucket.size >= size) {
            buckets.add(currentBucket.toList())
            currentBucket.clear()
        }
        currentBucket.add(it)
    }
    if (currentBucket.isNotEmpty()) {
        buckets.add(currentBucket)
    }
    return buckets
}