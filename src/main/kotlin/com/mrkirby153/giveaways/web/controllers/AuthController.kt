package com.mrkirby153.giveaways.web.controllers

import com.mrkirby153.giveaways.web.dto.AuthUrlRequest
import com.mrkirby153.giveaways.web.services.OauthService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController



@RestController
class AuthController(
    private val oauthService: OauthService
) {
    @GetMapping(value = ["/auth/client-id"], produces = [MediaType.TEXT_PLAIN_VALUE])
    fun client(): String {
        return oauthService.getClientId()
    }

    @PostMapping(value = ["/auth/auth-url"], produces = [MediaType.TEXT_PLAIN_VALUE])
    fun authUrl(@RequestBody request: AuthUrlRequest): String {
        return oauthService.getAuthorizationUrl(request.redirectUrl, request.state)
    }

    @PostMapping("/auth/validate-token")
    fun validateToken() {

    }
}