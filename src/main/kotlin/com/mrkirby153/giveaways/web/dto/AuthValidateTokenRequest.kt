package com.mrkirby153.giveaways.web.dto

data class AuthValidateTokenRequest(
    val token: String,
    val redirectUri: String
)