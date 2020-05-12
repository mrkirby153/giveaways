package com.mrkirby153.snowsgivingbot.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;

public class HttpExceptionUtils {

    public static HttpStatusCodeException create(HttpStatus code, String body) {
        if (code.is4xxClientError()) {
            return HttpClientErrorException
                .create(code, code.getReasonPhrase(), HttpHeaders.EMPTY, body.getBytes(), null);
        }
        if (code.is5xxServerError()) {
            return HttpServerErrorException
                .create(code, code.getReasonPhrase(), HttpHeaders.EMPTY, body.getBytes(), null);
        }
        throw new IllegalArgumentException("Invalid http code: " + code);
    }
}
