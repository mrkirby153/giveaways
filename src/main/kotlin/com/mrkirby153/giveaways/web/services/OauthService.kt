package com.mrkirby153.giveaways.web.services

import com.mrkirby153.giveaways.utils.executeAsync
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger { }

interface OauthService {

    suspend fun getAuthorizationToken(
        redirectUri: String,
        authorizationCode: String
    ): OAuthTokenResponse

    fun getClientId(): String

    fun getAuthorizationUrl(redirectUri: String, state: String?): String
}

@Serializable
data class OAuthTokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String,
    @SerialName("expires_in")
    val expiresIn: Int,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("scope")
    val scope: String
)

@Service
class OAuthManager(
    @Value("\${oauth.client-id}")
    private val clientId: String,
    @Value("\${oauth.client-secret}")
    private val clientSecret: String,
    @Value("\${oauth.scopes:identify}")
    private val scopes: Array<String>,
    private val client: OkHttpClient
) : OauthService {

    override fun getClientId(): String {
        return clientId
    }

    override fun getAuthorizationUrl(redirectUri: String, state: String?): String {
        return HttpUrl.Builder().apply {
            scheme("https")
            host("discord.com")
            addPathSegments("oauth2/authorize")
            addQueryParameter("response_type", "code")
            addQueryParameter("client_id", getClientId())
            addQueryParameter("scope", scopes.joinToString(" "))
            addQueryParameter("redirect_uri", redirectUri)
            addQueryParameter("prompt", "none")
            if (state != null)
                addQueryParameter("state", state)
        }.build().toString()
    }

    override suspend fun getAuthorizationToken(
        redirectUri: String,
        authorizationCode: String
    ): OAuthTokenResponse {
        val formBody =
            FormBody.Builder().add("client_id", clientId).add("client_secret", clientSecret)
                .add("grant_type", "authorization_code").add("code", authorizationCode)
                .add("redirect_uri", redirectUri).add("scope", "identify").build()

        val req =
            Request.Builder().post(formBody).url("https://discord.com/api/oauth2/token").build()
        val resp = client.newCall(req).executeAsync()
        if (!resp.isSuccessful) {
            resp.body.use {
                log.debug { "Received unsuccessful code: ${resp.code} ${it?.toString()}" }
                throw IllegalStateException("Bad Request")
            }
        } else {
            resp.body.use {
                if (it == null) {
                    throw IllegalStateException("No response received")
                }
                return Json.decodeFromString<OAuthTokenResponse>(it.string())
            }
        }
    }
}