package com.mrkirby153.giveaways.utils

import net.dv8tion.jda.api.entities.User

fun pluralize(amount: Number, singular: String, plural: String, includeAmount: Boolean = true) =
    if (amount == 1) {
        if (includeAmount) "$amount $singular" else singular
    } else {
        if (includeAmount) "$amount $plural" else singular
    }

@Suppress("DEPRECATION")
val User.effectiveUsername: String
    get() {
        return if (this.discriminator != "0000") {
            "${this.name}#${this.discriminator}"
        } else {
            this.name
        }
    }