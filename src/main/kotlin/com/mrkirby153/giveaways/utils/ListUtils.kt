package com.mrkirby153.giveaways.utils

/**
 * Creates a slice of a list from the provided [range]
 */
operator fun <T> List<T>.get(range: IntRange): List<T> =
    this.subList(range.first, range.last)