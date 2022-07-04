package com.mrkirby153.giveaways.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.reflect.Modifier

fun <T : Any> unwrapCompanionClass(clazz: Class<T>): Class<*> {
    return clazz.enclosingClass?.let { enclosingClass ->
        try {
            enclosingClass.declaredFields.find { field ->
                field.name == clazz.simpleName && Modifier.isStatic(
                    field.modifiers
                ) && field.type == clazz
            }?.run { enclosingClass }
        } catch (e: SecurityException) {
            null
        }
    } ?: clazz
}

inline val Any.log: Logger
    get() = LoggerFactory.getLogger(unwrapCompanionClass(this::class.java))
