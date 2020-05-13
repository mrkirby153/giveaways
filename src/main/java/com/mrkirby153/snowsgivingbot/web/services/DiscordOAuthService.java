package com.mrkirby153.snowsgivingbot.web.services;

import com.mrkirby153.snowsgivingbot.web.dto.DiscordOAuthUser;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Service
public class DiscordOAuthService {

    private final OkHttpClient httpClient;
    @Value("${oauth.client-id}")
    private String clientId;
    @Value("${oauth.secret}")
    private String clientSecret;

    public DiscordOAuthService(OkHttpClient client) {
        this.httpClient = client;
    }


    /**
     * Exchanges an authorization code for an auth token
     *
     * @param redirectUri       The redirect URI used
     * @param authorizationCode The authorization code
     *
     * @return The auth token, or null if one could ont be retrieved
     */
    public CompletableFuture<String> getAuthorizationToken(String redirectUri,
        String authorizationCode) {
        CompletableFuture<String> cf = new CompletableFuture<>();
        RequestBody body = new FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("grant_type", "authorization_code")
            .add("code", authorizationCode)
            .add("redirect_uri", redirectUri)
            .add("scope", "identify")
            .build();

        Request req = new Request.Builder()
            .post(body)
            .url("https://discord.com/api/oauth2/token")
            .build();

        httpClient.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                cf.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    ResponseBody body = response.body();
                    System.out.println(body.string());
                    body.close();
                    cf.completeExceptionally(
                        new IllegalStateException("Received code " + response.code()));
                } else {
                    ResponseBody body = response.body();
                    if (body == null) {
                        cf.completeExceptionally(new IllegalStateException("No body returned"));
                        return;
                    }
                    JSONObject obj = new JSONObject(new JSONTokener(body.byteStream()));
                    body.close();
                    cf.complete(obj.getString("access_token"));
                }
            }
        });
        return cf;
    }

    public CompletableFuture<DiscordOAuthUser> getDiscordUser(String code) {
        CompletableFuture<DiscordOAuthUser> cf = new CompletableFuture<>();
        Request req = new Request.Builder()
            .url("https://discord.com/api/users/@me")
            .header("Authorization", String.format("Bearer %s", code))
            .build();

        httpClient.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                cf.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    cf.completeExceptionally(
                        new IllegalStateException("Received code " + response.code()));
                } else {
                    ResponseBody body = response.body();
                    if (body == null) {
                        cf.completeExceptionally(new IllegalStateException("No body returned"));
                        return;
                    }
                    JSONObject obj = new JSONObject(new JSONTokener(body.byteStream()));
                    body.close();
                    DiscordOAuthUser user = new DiscordOAuthUser(obj.getString("id"),
                        obj.getString("username"), obj.getString("discriminator"),
                        obj.optString("avatar"));
                    cf.complete(user);
                }
            }
        });
        return cf;
    }
}
