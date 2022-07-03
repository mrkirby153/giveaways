package com.mrkirby153.giveaways.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

val logCache = ConcurrentHashMap<Class<*>, Logger>()

val Any.log: Logger
    get() = logCache.computeIfAbsent(this::class.java) { LoggerFactory.getLogger(it) }