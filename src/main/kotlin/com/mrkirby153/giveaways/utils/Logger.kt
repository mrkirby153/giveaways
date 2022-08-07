package com.mrkirby153.giveaways.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ClassUtils
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
    get() = LoggerFactory.getLogger(unwrapCompanionClass(ClassUtils.getUserClass(this::class.java)))
