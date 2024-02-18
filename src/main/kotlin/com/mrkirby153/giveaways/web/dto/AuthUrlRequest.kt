package com.mrkirby153.giveaways.web.dto

data class AuthUrlRequest(
    val redirectUrl: String,
    val state: String?,
)