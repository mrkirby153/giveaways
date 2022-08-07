package com.mrkirby153.giveaways.utils

import com.mrkirby153.botcore.utils.PrerequisiteCheck

fun PrerequisiteCheck<*>.isNumeric(string: String) {
    if (string.toDoubleOrNull() == null)
        fail("`$string` is not a number")
}