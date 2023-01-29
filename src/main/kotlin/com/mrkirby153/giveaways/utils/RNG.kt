package com.mrkirby153.giveaways.utils

import kotlin.random.Random


fun <T> weightedAverageRandomChoice(choices: List<Pair<T, Long>>): T? {
    val weights = mutableMapOf<Long, T>()
    var i = 0L
    choices.forEach { (item, weight) ->
        weights[i] = item
        i += weight
    }
    val winner = Random.nextLong(i)
    weights.keys.sortedDescending().forEach { key ->
        if (key <= winner) {
            return weights[key]!! // This should always be non-null
        }
    }
    return null
}