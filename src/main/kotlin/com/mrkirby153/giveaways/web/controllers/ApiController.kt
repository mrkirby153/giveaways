package com.mrkirby153.giveaways.web.controllers

import com.mrkirby153.giveaways.web.services.OauthService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController


data class ApiInfo(
    val version: String
)

@RestController
class ApiController(
    private val oauthService: OauthService
) {
    @GetMapping("/")
    fun index(): ApiInfo {
        return ApiInfo(
            "1.0"
        )
    }
}