package com.mrkirby153.giveaways.utils

fun pluralize(amount: Number, singular: String, plural: String, includeAmount: Boolean = true) =
    if (amount == 1) {
        if (includeAmount) "$amount $singular" else singular
    } else {
        if (includeAmount) "$amount $plural" else singular
    }